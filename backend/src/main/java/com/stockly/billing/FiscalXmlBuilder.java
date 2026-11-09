package com.stockly.billing;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.springframework.stereotype.Component;

/** Arma el XML sin firmar de factura (01), nota de crédito (03) y tiquete (04) siguiendo el orden de los XSD v4.4. */
@Component
public class FiscalXmlBuilder {
    static final String BASE = "https://cdn.comprobanteselectronicos.go.cr/xml-schemas/v4.4/";
    static final Map<String, String[]> ROOTS = Map.of(
        "01", new String[]{"FacturaElectronica", BASE + "facturaElectronica"},
        "03", new String[]{"NotaCreditoElectronica", BASE + "notaCreditoElectronica"},
        "04", new String[]{"TiqueteElectronico", BASE + "tiqueteElectronico"});
    /** Medios de pago de STK-012 en la nota 6. */
    static final Map<String, String> PAYMENT_CODES = Map.of("CASH", "01", "CARD", "02", "TRANSFER", "04", "SINPE", "06");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    public byte[] build(FiscalDraft draft) {
        String[] root = ROOTS.get(draft.docType());
        if (root == null) throw new IllegalArgumentException("Tipo de comprobante no soportado.");
        if ("01".equals(draft.docType()) && draft.receiver() == null) throw new IllegalArgumentException("La factura requiere receptor.");
        if ("03".equals(draft.docType()) && draft.reference() == null) throw new IllegalArgumentException("La nota de crédito requiere referencia.");
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            Document doc = factory.newDocumentBuilder().newDocument();
            var w = new Writer(doc, root[1]);
            Element top = doc.createElementNS(root[1], root[0]);
            doc.appendChild(top);
            w.text(top, "Clave", draft.key());
            w.text(top, "ProveedorSistemas", draft.providerId());
            w.text(top, "CodigoActividadEmisor", draft.activityCode());
            w.text(top, "NumeroConsecutivo", draft.consecutive());
            w.text(top, "FechaEmision", DATE.format(draft.issuedAt().atZone(FiscalNumbers.COSTA_RICA).toOffsetDateTime().withNano(0)));
            party(w, top, "Emisor", draft.issuer(), true);
            if (draft.receiver() != null) party(w, top, "Receptor", draft.receiver(), false);
            w.text(top, "CondicionVenta", "01");
            Element detail = w.child(top, "DetalleServicio");

            var totals = new Totals();
            int number = 1;
            for (FiscalDraft.Line line : draft.lines()) {
                var b = FiscalDraft.breakdown(line);
                Element el = w.child(detail, "LineaDetalle");
                w.text(el, "NumeroLinea", String.valueOf(number++));
                w.text(el, "CodigoCABYS", line.cabys());
                w.text(el, "Cantidad", plain(line.quantity().setScale(3, RoundingMode.UNNECESSARY)));
                w.text(el, "UnidadMedida", Objects.requireNonNull(FiscalDraft.UNITS.get(line.unit()), "Unidad sin código"));
                w.text(el, "Detalle", clip(line.detail(), 200));
                w.text(el, "PrecioUnitario", money5(b.unitPrice()));
                w.text(el, "MontoTotal", money5(b.subtotal()));
                w.text(el, "SubTotal", money5(b.subtotal()));
                w.text(el, "BaseImponible", money5(b.subtotal()));
                Element tax = w.child(el, "Impuesto");
                w.text(tax, "Codigo", "01");
                w.text(tax, "CodigoTarifaIVA", line.rateCode());
                w.text(tax, "Tarifa", b.rate().setScale(2, RoundingMode.UNNECESSARY).toPlainString());
                w.text(tax, "Monto", money5(b.tax()));
                w.text(el, "ImpuestoAsumidoEmisorFabrica", money5(BigDecimal.ZERO));
                w.text(el, "ImpuestoNeto", money5(b.tax()));
                w.text(el, "MontoTotalLinea", money5(b.total()));
                totals.add(line.rateCode(), b, FiscalDraft.SERVICE.equals(line.unit()));
            }

            Element summary = w.child(top, "ResumenFactura");
            Element currency = w.child(summary, "CodigoTipoMoneda");
            w.text(currency, "CodigoMoneda", "CRC");
            w.text(currency, "TipoCambio", money5(BigDecimal.ONE));
            // Servicios (STK-018) solo si hay líneas de servicio: el XML de las ventas de mercancías no cambia.
            if (totals.services) {
                w.text(summary, "TotalServGravados", money5(totals.servGravado));
                w.text(summary, "TotalServExentos", money5(totals.servExento));
                w.text(summary, "TotalServNoSujeto", money5(totals.servNoSujeto));
            }
            w.text(summary, "TotalMercanciasGravadas", money5(totals.gravado.subtract(totals.servGravado)));
            w.text(summary, "TotalMercanciasExentas", money5(totals.exento.subtract(totals.servExento)));
            w.text(summary, "TotalMercNoSujeta", money5(totals.noSujeto.subtract(totals.servNoSujeto)));
            w.text(summary, "TotalGravado", money5(totals.gravado));
            w.text(summary, "TotalExento", money5(totals.exento));
            w.text(summary, "TotalNoSujeto", money5(totals.noSujeto));
            BigDecimal sale = totals.gravado.add(totals.exento).add(totals.noSujeto);
            w.text(summary, "TotalVenta", money5(sale));
            w.text(summary, "TotalVentaNeta", money5(sale));
            for (var entry : totals.byRate.entrySet()) {
                Element breakdown = w.child(summary, "TotalDesgloseImpuesto");
                w.text(breakdown, "Codigo", "01");
                w.text(breakdown, "CodigoTarifaIVA", entry.getKey());
                w.text(breakdown, "TotalMontoImpuesto", money5(entry.getValue()));
            }
            w.text(summary, "TotalImpuesto", money5(totals.tax));
            for (var payment : draft.payments().entrySet()) {
                Element pay = w.child(summary, "MedioPago");
                w.text(pay, "TipoMedioPago", Objects.requireNonNull(PAYMENT_CODES.get(payment.getKey()), "Medio sin código"));
                w.text(pay, "TotalMedioPago", money5(payment.getValue()));
            }
            w.text(summary, "TotalComprobante", money5(sale.add(totals.tax)));

            if (draft.reference() != null) {
                Element ref = w.child(top, "InformacionReferencia");
                w.text(ref, "TipoDocIR", draft.reference().docType());
                w.text(ref, "Numero", draft.reference().key());
                w.text(ref, "FechaEmisionIR", DATE.format(draft.reference().issuedAt().atZone(FiscalNumbers.COSTA_RICA).toOffsetDateTime().withNano(0)));
                w.text(ref, "Codigo", draft.reference().code());
                w.text(ref, "Razon", clip(draft.reference().reason(), 180));
            }
            var out = new ByteArrayOutputStream();
            var transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.transform(new DOMSource(doc), new StreamResult(out));
            return out.toByteArray();
        } catch (IllegalArgumentException | NullPointerException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el XML del comprobante.", e);
        }
    }

    private static void party(Writer w, Element parent, String name, FiscalDraft.Party party, boolean issuer) {
        Element el = w.child(parent, name);
        w.text(el, "Nombre", clip(party.name(), 100));
        Element id = w.child(el, "Identificacion");
        w.text(id, "Tipo", party.idType());
        w.text(id, "Numero", party.idNumber());
        if (issuer) {
            Element place = w.child(el, "Ubicacion");
            w.text(place, "Provincia", party.province());
            w.text(place, "Canton", party.canton());
            w.text(place, "Distrito", party.district());
            w.text(place, "OtrasSenas", clip(party.otherSigns(), 250));
        }
        if (party.email() != null && !party.email().isBlank()) w.text(el, "CorreoElectronico", party.email());
    }

    private static final class Totals {
        BigDecimal gravado = BigDecimal.ZERO, exento = BigDecimal.ZERO, noSujeto = BigDecimal.ZERO, tax = BigDecimal.ZERO;
        final SortedMap<String, BigDecimal> byRate = new TreeMap<>();
        BigDecimal servGravado = BigDecimal.ZERO, servExento = BigDecimal.ZERO, servNoSujeto = BigDecimal.ZERO;
        boolean services;
        void add(String rateCode, FiscalDraft.Breakdown b, boolean service) {
            switch (FiscalDraft.category(rateCode)) {
                case "EXENTO" -> { exento = exento.add(b.subtotal()); if (service) servExento = servExento.add(b.subtotal()); }
                case "NO_SUJETO" -> { noSujeto = noSujeto.add(b.subtotal()); if (service) servNoSujeto = servNoSujeto.add(b.subtotal()); }
                default -> { gravado = gravado.add(b.subtotal()); if (service) servGravado = servGravado.add(b.subtotal()); }
            }
            services |= service;
            tax = tax.add(b.tax());
            byRate.merge(rateCode, b.tax(), BigDecimal::add);
        }
    }

    private record Writer(Document doc, String ns) {
        Element child(Element parent, String name) {
            Element el = doc.createElementNS(ns, name);
            parent.appendChild(el);
            return el;
        }
        void text(Element parent, String name, String value) { child(parent, name).setTextContent(value); }
    }

    private static String money5(BigDecimal value) { return value.setScale(5, RoundingMode.HALF_UP).toPlainString(); }
    private static String plain(BigDecimal value) { return value.toPlainString(); }
    private static String clip(String value, int max) {
        String clean = value == null ? "" : value.strip().replaceAll("\\s+", " ");
        return clean.length() > max ? clean.substring(0, max) : clean;
    }
}
