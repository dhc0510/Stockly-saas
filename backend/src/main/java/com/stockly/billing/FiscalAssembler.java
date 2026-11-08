package com.stockly.billing;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Reúne desde MySQL los datos de un comprobante: emisor, receptor, líneas con CABYS, medios de pago y referencia. */
@Component
class FiscalAssembler {
    private final JdbcTemplate jdbc;
    private final HaciendaEndpoints endpoints;

    FiscalAssembler(JdbcTemplate jdbc, HaciendaEndpoints endpoints) { this.jdbc = jdbc; this.endpoints = endpoints; }

    record Doc(String company, String id, String saleId, String docType, String environment, String consecutive, String key,
               Instant issuedAt, String referenceId) {}

    /** Datos faltantes que impiden emitir; vacío si el comprobante está completo. */
    record Assembly(FiscalDraft draft, String missing) {}

    Assembly assemble(Doc doc) {
        var settings = jdbc.query("""
            SELECT activity_code,province,canton,district,other_signs,email FROM billing_settings WHERE company_id=?
            """, (rs, n) -> new String[]{rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6)},
            doc.company()).stream().findFirst().orElse(new String[6]);
        if (Arrays.stream(settings).anyMatch(Objects::isNull))
            return new Assembly(null, "Faltan datos del emisor en Facturación (actividad económica, ubicación o correo).");
        if (endpoints.providerId().isEmpty())
            return new Assembly(null, "El servidor no tiene configurada la cédula del proveedor de sistemas.");
        var issuer = jdbc.queryForObject("SELECT legal_name,identification_type,identification_normalized FROM companies WHERE id=?",
            (rs, n) -> new FiscalDraft.Party(rs.getString(1), FiscalNumbers.issuerType(rs.getString(2)), rs.getString(3), settings[5],
                settings[1], settings[2], settings[3], settings[4]), doc.company());

        // El receptor y las líneas salen de la venta; la nota de crédito repite los del comprobante original.
        var sale = jdbc.queryForObject("SELECT customer_name,customer_email,customer_id_type,customer_id_number FROM sales WHERE company_id=? AND id=?",
            (rs, n) -> new String[]{rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)}, doc.company(), doc.saleId());
        FiscalDraft.Reference reference = null;
        String returnId = null;
        String receiverDocType = doc.docType();
        if (FiscalDocumentService.CREDIT_NOTE.equals(doc.docType())) {
            var original = jdbc.queryForObject("SELECT doc_type,numeric_key,issued_at FROM fiscal_documents WHERE company_id=? AND id=?",
                (rs, n) -> new String[]{rs.getString(1), rs.getString(2), rs.getTimestamp(3).toInstant().toString()}, doc.company(), doc.referenceId());
            returnId = jdbc.queryForObject("SELECT sale_return_id FROM fiscal_documents WHERE company_id=? AND id=?",
                String.class, doc.company(), doc.id());
            String reason = returnId == null ? jdbc.query("SELECT reason FROM inventory_movements WHERE company_id=? AND reversed_id=?",
                (rs, n) -> rs.getString(1), doc.company(), doc.saleId()).stream().findFirst().orElse("Anulación de la venta")
                : jdbc.queryForObject("SELECT reason FROM sale_returns WHERE company_id=? AND id=?", String.class, doc.company(), returnId);
            reference = new FiscalDraft.Reference(original[0], original[1], Instant.parse(original[2]), reason, returnId == null ? "01" : "06");
            receiverDocType = original[0];
        }
        FiscalDraft.Party receiver = FiscalDocumentService.INVOICE.equals(receiverDocType) && sale[3] != null
            ? new FiscalDraft.Party(sale[0], sale[2], sale[3], sale[1], null, null, null, null) : null;

        var missing = new ArrayList<String>();
        var lines = returnId != null ? returnLines(doc, returnId) : jdbc.query("""
            SELECT l.item_name,-l.quantity,l.unit,p.amount,i.cabys_code,i.tax_rate_code
            FROM inventory_movement_lines l
            JOIN sale_line_prices p ON p.company_id=l.company_id AND p.sale_id=l.movement_id AND p.item_id=l.item_id
            JOIN catalog_items i ON i.company_id=l.company_id AND i.id=l.item_id
            WHERE l.company_id=? AND l.movement_id=? ORDER BY l.item_name,l.item_id
            """, (rs, n) -> {
            if (rs.getString(5) == null || rs.getString(6) == null) missing.add(rs.getString(1));
            return new FiscalDraft.Line(rs.getString(5), rs.getBigDecimal(2), rs.getString(3), rs.getString(1), rs.getBigDecimal(4), rs.getString(6));
        }, doc.company(), doc.saleId());
        if (lines == null) return new Assembly(null, "No se pudieron leer las líneas del comprobante electrónico original.");
        if (!missing.isEmpty())
            return new Assembly(null, "Falta el código CABYS o la tarifa de IVA en el catálogo: " + String.join(", ", missing) + ".");

        // Medios de pago de STK-012 agrupados (máximo 4 en el XSD). Las notas de crédito no los llevan.
        var payments = new LinkedHashMap<String, BigDecimal>();
        if (!FiscalDocumentService.CREDIT_NOTE.equals(doc.docType()))
            jdbc.query("SELECT method,SUM(amount) FROM sale_payments WHERE company_id=? AND sale_id=? GROUP BY method ORDER BY MIN(position)",
                rs -> { payments.put(rs.getString(1), rs.getBigDecimal(2)); }, doc.company(), doc.saleId());
        return new Assembly(new FiscalDraft(doc.docType(), doc.key(), doc.consecutive(), doc.issuedAt(), endpoints.providerId(),
            settings[0], issuer, receiver, lines, payments, reference), null);
    }

    private List<FiscalDraft.Line> returnLines(Doc doc, String returnId) {
        byte[] signed = jdbc.queryForObject("SELECT signed_xml FROM fiscal_documents WHERE company_id=? AND id=?",
            byte[].class, doc.company(), doc.referenceId());
        if (signed == null) return null;
        var selected = jdbc.query("""
            SELECT ordered.number,ordered.unit,ordered.item_name,r.quantity,r.amount FROM (
              SELECT item_id,unit,item_name,ROW_NUMBER() OVER (ORDER BY item_name,item_id) AS number
              FROM inventory_movement_lines WHERE company_id=? AND movement_id=?
            ) ordered JOIN sale_return_lines r ON r.item_id=ordered.item_id
            WHERE r.company_id=? AND r.return_id=? ORDER BY ordered.number
            """, (rs,row) -> new Returned(rs.getInt(1),rs.getString(2),rs.getString(3),rs.getBigDecimal(4),rs.getBigDecimal(5)),
            doc.company(), doc.saleId(), doc.company(), returnId);
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            Element root = factory.newDocumentBuilder().parse(new ByteArrayInputStream(signed)).getDocumentElement();
            String namespace = root.getNamespaceURI();
            var originals = root.getElementsByTagNameNS(namespace, "LineaDetalle");
            var result = new ArrayList<FiscalDraft.Line>();
            for (Returned line : selected) {
                if (!(originals.item(line.number() - 1) instanceof Element original)) return null;
                String cabys = text(original, namespace, "CodigoCABYS");
                String rate = text(original, namespace, "CodigoTarifaIVA");
                if (cabys == null || rate == null || !FiscalDraft.RATES.containsKey(rate)) return null;
                result.add(new FiscalDraft.Line(cabys, line.quantity(), line.unit(), line.name(), line.amount(), rate));
            }
            return result.isEmpty() ? null : result;
        } catch (ParserConfigurationException | SAXException | IOException invalidXml) {
            return null;
        }
    }

    private static String text(Element element, String namespace, String name) {
        var node = element.getElementsByTagNameNS(namespace, name).item(0);
        return node == null ? null : node.getTextContent();
    }

    private record Returned(int number, String unit, String name, BigDecimal quantity, BigDecimal amount) {}
}
