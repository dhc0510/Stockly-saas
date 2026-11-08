package com.stockly.billing;

import java.math.BigDecimal;
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
        String receiverDocType = doc.docType();
        if (FiscalDocumentService.CREDIT_NOTE.equals(doc.docType())) {
            var original = jdbc.queryForObject("SELECT doc_type,numeric_key,issued_at FROM fiscal_documents WHERE company_id=? AND id=?",
                (rs, n) -> new String[]{rs.getString(1), rs.getString(2), rs.getTimestamp(3).toInstant().toString()}, doc.company(), doc.referenceId());
            String reason = jdbc.query("SELECT reason FROM inventory_movements WHERE company_id=? AND reversed_id=?",
                (rs, n) -> rs.getString(1), doc.company(), doc.saleId()).stream().findFirst().orElse("Anulación de la venta");
            reference = new FiscalDraft.Reference(original[0], original[1], Instant.parse(original[2]), reason);
            receiverDocType = original[0];
        }
        FiscalDraft.Party receiver = FiscalDocumentService.INVOICE.equals(receiverDocType) && sale[3] != null
            ? new FiscalDraft.Party(sale[0], sale[2], sale[3], sale[1], null, null, null, null) : null;

        var missing = new ArrayList<String>();
        var lines = jdbc.query("""
            SELECT l.item_name,-l.quantity,l.unit,p.amount,i.cabys_code,i.tax_rate_code
            FROM inventory_movement_lines l
            JOIN sale_line_prices p ON p.company_id=l.company_id AND p.sale_id=l.movement_id AND p.item_id=l.item_id
            JOIN catalog_items i ON i.company_id=l.company_id AND i.id=l.item_id
            WHERE l.company_id=? AND l.movement_id=? ORDER BY l.item_name,l.item_id
            """, (rs, n) -> {
            if (rs.getString(5) == null || rs.getString(6) == null) missing.add(rs.getString(1));
            return new FiscalDraft.Line(rs.getString(5), rs.getBigDecimal(2), rs.getString(3), rs.getString(1), rs.getBigDecimal(4), rs.getString(6));
        }, doc.company(), doc.saleId());
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
}
