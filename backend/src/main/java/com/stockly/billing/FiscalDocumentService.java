package com.stockly.billing;

import com.stockly.identity.IdentityFailure;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ciclo de vida de los comprobantes de una venta (SPEC §8-§10). Crear un comprobante solo reserva su consecutivo
 * y su clave dentro de la transacción de la venta; el envío ocurre después en {@link FiscalDispatcher}.
 */
@Service
public class FiscalDocumentService {
    public static final String INVOICE = "01", CREDIT_NOTE = "03", TICKET = "04";
    private final JdbcTemplate jdbc;

    FiscalDocumentService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Al confirmar la venta: factura si el cliente está identificado, tiquete si no. Debe correr en la transacción de la venta. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void issueForSale(String company, String saleId) {
        var sale = sale(company, saleId);
        create(company, sale, sale.customerIdNumber() == null ? TICKET : INVOICE, null, null);
    }

    /** Al anular la venta (SPEC §10): nota de crédito si el comprobante fue aceptado; cancela lo que nunca llegó a Hacienda. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void onSaleVoided(String company, String saleId) {
        var current = current(company, saleId);
        if (current.isEmpty()) return;
        var doc = current.get();
        switch (doc.status()) {
            case "ACCEPTED" -> creditNote(company, saleId, doc);
            case "PENDING" -> {
                // Si nunca se envió, se cancela. Si hubo intentos, el despachador consulta la clave primero y decide.
                if (doc.attempts() == 0)
                    jdbc.update("""
                        UPDATE fiscal_documents SET status='CANCELLED',status_reason='La venta se anuló antes de enviar el comprobante.',
                          next_attempt_at=NULL,resolved_at=? WHERE company_id=? AND id=?
                        """, now(), company, doc.id());
            }
            default -> { /* PROCESSING: al aceptarse, el despachador emite la nota de crédito. REJECTED: no requiere. */ }
        }
    }

    /** El despachador llama esto cuando un comprobante de una venta anulada resulta aceptado. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void creditNoteIfVoided(String company, String saleId, String documentId) {
        boolean voided = Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM inventory_movements WHERE company_id=? AND reversed_id=?)
            """, Boolean.class, company, saleId));
        boolean credited = Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM fiscal_documents WHERE company_id=? AND reference_id=?)
            """, Boolean.class, company, documentId));
        if (voided && !credited) creditNote(company, saleId, current(company, saleId).filter(d -> d.id().equals(documentId)).orElseThrow());
    }

    /** SPEC §9: corrige los datos del cliente de una venta con comprobante rechazado y emite uno nuevo. */
    @Transactional
    public void correct(String company, String saleId, String idType, String idNumber, String name) {
        var doc = current(company, saleId).orElseThrow(() -> new BillingFailure(404, "FISCAL_NOT_FOUND", "La venta no tiene comprobante."));
        if (!"REJECTED".equals(doc.status()))
            throw new BillingFailure(409, "FISCAL_NOT_REJECTED", "Solo se corrige un comprobante rechazado.");
        if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM inventory_movements WHERE company_id=? AND reversed_id=?)",
            Boolean.class, company, saleId)))
            throw new BillingFailure(409, "FISCAL_SALE_VOIDED", "La venta está anulada.");
        Customer customer = customer(idType, idNumber, name);
        jdbc.queryForList("SELECT id FROM sales WHERE company_id=? AND id=? FOR UPDATE", String.class, company, saleId);
        jdbc.update("UPDATE sales SET customer_id_type=?,customer_id_number=?,customer_name=COALESCE(?,customer_name) WHERE company_id=? AND id=?",
            customer.type(), customer.number(), customer.name(), company, saleId);
        create(company, sale(company, saleId), customer.number() == null ? TICKET : INVOICE, null, doc.id());
    }

    /** Registra una nota pendiente por devolución aceptada para despacho asíncrono. */
    @Transactional
    public void issueForReturn(String company, String returnId) {
        var ids = jdbc.queryForList("SELECT sale_id FROM sale_returns WHERE company_id=? AND id=? FOR UPDATE",
            String.class, company, returnId);
        if (ids.isEmpty()) return;
        boolean alreadyIssued = Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM fiscal_documents WHERE company_id=? AND sale_return_id=?)
            """, Boolean.class, company, returnId));
        if (alreadyIssued) return;
        String saleId = ids.getFirst();
        current(company, saleId).filter(doc -> "ACCEPTED".equals(doc.status())).ifPresent(doc ->
            create(company, sale(company, saleId), CREDIT_NOTE, doc.id(), null, returnId, doc.environment()));
    }

    /** Pide un nuevo intento inmediato de un comprobante pendiente. */
    @Transactional
    public void retryNow(String company, String saleId) {
        var doc = current(company, saleId).orElseThrow(() -> new BillingFailure(404, "FISCAL_NOT_FOUND", "La venta no tiene comprobante."));
        jdbc.update("UPDATE fiscal_documents SET next_attempt_at=? WHERE company_id=? AND id=? AND status IN ('PENDING','PROCESSING')",
            now(), company, doc.id());
        jdbc.update("UPDATE fiscal_documents SET next_attempt_at=? WHERE company_id=? AND reference_id=? AND status IN ('PENDING','PROCESSING')",
            now(), company, doc.id());
    }

    /** Valida tipo y número de identificación (nota 4) y devuelve los datos normalizados. */
    public static Customer customer(String idType, String idNumber, String name) {
        String type = idType == null || idType.isBlank() ? null : idType.strip();
        String digits = idNumber == null ? "" : idNumber.replaceAll("\\D", "");
        String cleanName = name == null || name.isBlank() ? null : name.strip();
        if (type == null) {
            if (!digits.isEmpty()) throw new IdentityFailure(400, "SALE_INVALID", "Elige el tipo de identificación del cliente.");
            return new Customer(null, null, cleanName);
        }
        boolean valid = switch (type) {
            case "01" -> digits.length() == 9;
            case "02", "04" -> digits.length() == 10;
            case "03" -> digits.length() == 11 || digits.length() == 12;
            default -> false;
        };
        if (!valid) throw new IdentityFailure(400, "SALE_INVALID", "Revisa el número de identificación del cliente.",
            Map.of("customerIdNumber", "El número no corresponde al tipo de identificación."));
        if (cleanName == null) throw new IdentityFailure(400, "SALE_INVALID", "La factura necesita el nombre del cliente.",
            Map.of("customer", "Escribe el nombre del cliente."));
        if (cleanName.length() > 100) throw new IdentityFailure(400, "SALE_INVALID", "El nombre para la factura admite hasta 100 caracteres.");
        return new Customer(type, digits, cleanName);
    }

    // ---------- Consulta ----------

    public record Document(String id, String docType, String environment, String consecutive, String key, Instant issuedAt,
                           String status, String reason, int attempts, Instant sentAt, Instant resolvedAt, String referenceId,
                           String replacesId, String mailStatus, String branchCode, String saleReturnId) {}

    /** Todos los comprobantes de la venta, del más antiguo al más reciente. */
    @Transactional(readOnly = true)
    public List<Document> documents(String company, String saleId) {
        return jdbc.query("""
            SELECT d.id,d.doc_type,d.environment,d.consecutive,d.numeric_key,d.issued_at,d.status,d.status_reason,d.attempts,
              d.sent_at,d.resolved_at,d.reference_id,d.replaces_id,d.mail_status,b.fiscal_code,d.sale_return_id
            FROM fiscal_documents d JOIN inventory_branches b ON b.company_id=d.company_id AND b.id=d.branch_id
            WHERE d.company_id=? AND d.sale_id=? ORDER BY d.created_at,d.id
            """, (rs, n) -> new Document(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
            rs.getTimestamp(6).toInstant(), rs.getString(7), rs.getString(8), rs.getInt(9), instant(rs.getTimestamp(10)),
            instant(rs.getTimestamp(11)), rs.getString(12), rs.getString(13), rs.getString(14), rs.getString(15), rs.getString(16)), company, saleId);
    }

    /** Comprobante vigente de la venta: el último que no es nota de crédito. */
    Optional<Document> current(String company, String saleId) {
        return documents(company, saleId).stream().filter(d -> !CREDIT_NOTE.equals(d.docType())).reduce((a, b) -> b);
    }

    // ---------- Creación ----------

    record Sale(String id, String branchId, String customerIdType, String customerIdNumber) {}
    public record Customer(String type, String number, String name) {}

    private Sale sale(String company, String saleId) {
        return jdbc.queryForObject("SELECT id,branch_id,customer_id_type,customer_id_number FROM sales WHERE company_id=? AND id=?",
            (rs, n) -> new Sale(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)), company, saleId);
    }

    private void creditNote(String company, String saleId, Document original) {
        create(company, sale(company, saleId), CREDIT_NOTE, original.id(), null);
    }

    private void create(String company, Sale sale, String docType, String referenceId, String replacesId) {
        create(company, sale, docType, referenceId, replacesId, null, null);
    }

    private void create(String company, Sale sale, String docType, String referenceId, String replacesId,
                        String returnId, String originalEnvironment) {
        String environment = originalEnvironment != null ? originalEnvironment : jdbc.query("SELECT environment FROM billing_settings WHERE company_id=?",
            (rs, n) -> rs.getString(1), company).stream().findFirst().orElse("TEST");
        String branchCode = branchCode(company, sale.branchId());
        jdbc.update("""
            INSERT IGNORE INTO fiscal_sequences(company_id,branch_id,environment,doc_type,last_number) VALUES (?,?,?,?,0)
            """, company, sale.branchId(), environment, docType);
        long number = jdbc.queryForObject("""
            SELECT last_number FROM fiscal_sequences WHERE company_id=? AND branch_id=? AND environment=? AND doc_type=? FOR UPDATE
            """, Long.class, company, sale.branchId(), environment, docType) + 1;
        if (number > 9_999_999_999L) number = 1; // Nota 3: al llegar al tope puede volver a empezar.
        jdbc.update("UPDATE fiscal_sequences SET last_number=? WHERE company_id=? AND branch_id=? AND environment=? AND doc_type=?",
            number, company, sale.branchId(), environment, docType);
        String issuerNumber = jdbc.queryForObject("SELECT identification_normalized FROM companies WHERE id=?", String.class, company);
        Instant issuedAt = Instant.now();
        String consecutive = FiscalNumbers.consecutive(branchCode, docType, number);
        String key = FiscalNumbers.key(issuedAt, issuerNumber, consecutive, '1', FiscalNumbers.securityCode());
        jdbc.update("""
            INSERT INTO fiscal_documents(company_id,id,sale_id,doc_type,environment,branch_id,consecutive,numeric_key,issued_at,
              reference_id,replaces_id,sale_return_id,status,next_attempt_at,created_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,'PENDING',?,?)
            """, company, UUID.randomUUID().toString(), sale.id(), docType, environment, sale.branchId(), consecutive, key,
            Timestamp.from(issuedAt), referenceId, replacesId, returnId, Timestamp.from(issuedAt), Timestamp.from(issuedAt));
    }

    /** Asigna el siguiente código de establecimiento (001, 002…) si la sucursal aún no tiene, bajo bloqueo de la empresa. */
    public String branchCode(String company, String branchId) {
        String code = jdbc.queryForObject("SELECT fiscal_code FROM inventory_branches WHERE company_id=? AND id=?", String.class, company, branchId);
        if (code != null) return code;
        jdbc.queryForList("SELECT id FROM companies WHERE id=? FOR UPDATE", String.class, company);
        code = jdbc.queryForObject("SELECT fiscal_code FROM inventory_branches WHERE company_id=? AND id=?", String.class, company, branchId);
        if (code != null) return code;
        Integer max = jdbc.queryForObject("SELECT COALESCE(MAX(CAST(fiscal_code AS UNSIGNED)),0) FROM inventory_branches WHERE company_id=?",
            Integer.class, company);
        code = String.format("%03d", (max == null ? 0 : max) + 1);
        jdbc.update("UPDATE inventory_branches SET fiscal_code=? WHERE company_id=? AND id=?", code, company, branchId);
        return code;
    }

    private static Timestamp now() { return Timestamp.from(Instant.now()); }
    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }

}
