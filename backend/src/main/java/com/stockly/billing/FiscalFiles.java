package com.stockly.billing;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Archivos guardados de un comprobante: PDF y XML firmado (generados juntos) y respuesta de Hacienda. */
@Component
public class FiscalFiles {
    private static final java.util.Map<String, String> TITLES = java.util.Map.of(
        "01", "Factura electrónica", "03", "Nota de crédito electrónica", "04", "Tiquete electrónico");
    private final JdbcTemplate jdbc;

    FiscalFiles(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Bundle(String key, String consecutive, String title, String environment, String email, String issuerName,
                         byte[] pdf, byte[] xml, byte[] response) {}

    public Bundle bundle(String company, String documentId) {
        var bundle = jdbc.query("""
            SELECT d.numeric_key,d.consecutive,d.doc_type,d.environment,s.customer_email,c.legal_name,d.pdf,d.signed_xml,d.response_xml
            FROM fiscal_documents d JOIN sales s ON s.company_id=d.company_id AND s.id=d.sale_id JOIN companies c ON c.id=d.company_id
            WHERE d.company_id=? AND d.id=?
            """, (rs, n) -> new Bundle(rs.getString(1), rs.getString(2), TITLES.get(rs.getString(3)), rs.getString(4), rs.getString(5),
            rs.getString(6), rs.getBytes(7), rs.getBytes(8), rs.getBytes(9)), company, documentId).stream().findFirst()
            .orElseThrow(() -> new BillingFailure(404, "FISCAL_NOT_FOUND", "No se encontró el comprobante."));
        if (bundle.xml() == null || bundle.pdf() == null)
            throw new BillingFailure(409, "FISCAL_NOT_SIGNED", "El comprobante aún no se ha generado.");
        return bundle;
    }

}
