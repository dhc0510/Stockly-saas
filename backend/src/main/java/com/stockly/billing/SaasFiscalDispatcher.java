package com.stockly.billing;

import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * STK-018 · Facturas de Stockly por los pagos de suscripción. Mismo ciclo que {@link FiscalDispatcher}: reserva con lease,
 * consulta la clave antes de reenviar, firma una sola vez y guarda XML, PDF y respuesta. Nunca registra XML ni credenciales.
 */
@Component
public class SaasFiscalDispatcher {
    private static final Logger log = LoggerFactory.getLogger(SaasFiscalDispatcher.class);
    private static final Duration LEASE = Duration.ofMinutes(5);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final BillingCrypto crypto;
    private final SaasBillingService billing;
    private final FiscalXmlBuilder builder;
    private final FiscalSigner signer;
    private final FiscalSchemas schemas;
    private final HaciendaClient hacienda;
    private final HaciendaEndpoints endpoints;
    private final FiscalPdf pdf;
    private final ObjectProvider<JavaMailSender> senders;
    private final String from;
    private final boolean enabled;

    SaasFiscalDispatcher(JdbcTemplate jdbc, TransactionTemplate tx, BillingCrypto crypto, SaasBillingService billing, FiscalXmlBuilder builder,
                         FiscalSigner signer, FiscalSchemas schemas, HaciendaClient hacienda, HaciendaEndpoints endpoints, FiscalPdf pdf,
                         ObjectProvider<JavaMailSender> senders, @Value("${stockly.mail-from:no-reply@stockly.local}") String from,
                         @Value("${stockly.billing.dispatch-enabled:true}") boolean enabled) {
        this.jdbc = jdbc; this.tx = tx; this.crypto = crypto; this.billing = billing; this.builder = builder; this.signer = signer;
        this.schemas = schemas; this.hacienda = hacienda; this.endpoints = endpoints; this.pdf = pdf; this.senders = senders;
        this.from = from; this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${stockly.billing.dispatch-delay-ms:15000}", initialDelay = 25000)
    void scheduled() {
        if (enabled && crypto.enabled()) runOnce(20);
    }

    public int runOnce(int limit) {
        enqueue(limit);
        int handled = 0;
        for (var doc : claim(limit)) {
            try { process(doc); }
            catch (RuntimeException e) {
                log.warn("Comprobante de Stockly {} no se pudo procesar: {}", doc.id(), e.getClass().getSimpleName());
                reschedule(doc, "Error inesperado al procesar. Se reintentará.", 1);
            }
            handled++;
        }
        sendPendingMail(limit);
        return handled;
    }

    /** Factura por cada pago vigente que aún no la tiene, y nota de crédito por cada pago anulado cuya factura fue aceptada. */
    private void enqueue(int limit) {
        tx.executeWithoutResult(s -> {
            for (String payment : jdbc.queryForList("""
                SELECT p.id FROM subscription_payments p WHERE p.voided_at IS NULL
                  AND NOT EXISTS(SELECT 1 FROM saas_fiscal_documents d WHERE d.payment_id=p.id AND d.doc_type='01')
                ORDER BY p.created_at LIMIT ?
                """, String.class, limit))
                billing.createDocument(payment, "01", null);
            jdbc.query("""
                SELECT d.payment_id,d.id FROM saas_fiscal_documents d JOIN subscription_payments p ON p.id=d.payment_id
                WHERE d.doc_type='01' AND d.status='ACCEPTED' AND d.replaced_by IS NULL AND p.voided_at IS NOT NULL
                  AND NOT EXISTS(SELECT 1 FROM saas_fiscal_documents c WHERE c.reference_id=d.id)
                ORDER BY p.voided_at LIMIT ?
                """, rs -> { billing.createDocument(rs.getString(1), "03", rs.getString(2)); }, limit);
        });
    }

    private record Claimed(String id, String paymentId, String docType, String environment, String consecutive, String key, Instant issuedAt,
                           String referenceId, String status, int attempts, boolean signed) {}

    private List<Claimed> claim(int limit) {
        return tx.execute(status -> {
            var rows = jdbc.query("""
                SELECT id,payment_id,doc_type,environment,consecutive,numeric_key,issued_at,reference_id,status,attempts,signed_xml IS NOT NULL
                FROM saas_fiscal_documents WHERE status IN ('PENDING','PROCESSING') AND next_attempt_at<=?
                ORDER BY next_attempt_at LIMIT ? FOR UPDATE SKIP LOCKED
                """, (rs, n) -> new Claimed(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6),
                rs.getTimestamp(7).toInstant(), rs.getString(8), rs.getString(9), rs.getInt(10), rs.getBoolean(11)), now(), limit);
            for (var row : rows)
                jdbc.update("UPDATE saas_fiscal_documents SET next_attempt_at=? WHERE id=?", Timestamp.from(Instant.now().plus(LEASE)), row.id());
            return rows;
        });
    }

    private void process(Claimed doc) {
        var stored = billing.credentials();
        if (stored.isEmpty()) { reschedule(doc, "Faltan las credenciales de Hacienda de Stockly en Configuración.", 0); return; }
        var credentials = stored.get().api();
        if ("PROCESSING".equals(doc.status()) || doc.attempts() > 0) {
            var answer = hacienda.status(doc.environment(), credentials, doc.key());
            switch (answer.outcome()) {
                case ACCEPTED -> { accept(doc, answer.responseXml()); return; }
                case REJECTED -> { reject(doc, answer.detail(), answer.responseXml()); return; }
                case PROCESSING -> { later(doc, Duration.ofSeconds(30)); return; }
                case RETRY -> { reschedule(doc, answer.detail(), 1); return; }
                default -> { }
            }
        }
        FiscalDraft draft = draft(doc);
        if (draft == null) { reschedule(doc, "Faltan datos del emisor de Stockly (actividad, CABYS, ubicación o correo).", 0); return; }
        if (endpoints.providerId().isEmpty()) { reschedule(doc, "El servidor no tiene configurada la cédula del proveedor de sistemas.", 0); return; }
        byte[] signed = doc.signed() ? jdbc.queryForObject("SELECT signed_xml FROM saas_fiscal_documents WHERE id=?", byte[].class, doc.id()) : null;
        if (signed == null) {
            signed = signer.sign(builder.build(draft), stored.get().keyStore(), stored.get().pin());
            if (schemas.available(doc.docType())) schemas.validate(doc.docType(), signed);
            byte[] toStore = signed, graphic = pdf.render(draft, doc.environment());
            tx.executeWithoutResult(s -> jdbc.update("UPDATE saas_fiscal_documents SET signed_xml=?,pdf=? WHERE id=? AND signed_xml IS NULL",
                toStore, graphic, doc.id()));
        }
        var result = hacienda.submit(doc.environment(), credentials, new HaciendaClient.Submission(doc.key(), doc.issuedAt(),
            draft.issuer().idType(), draft.issuer().idNumber(), draft.receiver().idType(), draft.receiver().idNumber(), signed));
        switch (result.outcome()) {
            case RECEIVED, ALREADY_RECEIVED -> tx.executeWithoutResult(s -> jdbc.update("""
                UPDATE saas_fiscal_documents SET status='PROCESSING',status_reason=NULL,attempts=attempts+1,sent_at=COALESCE(sent_at,?),next_attempt_at=?
                WHERE id=?
                """, now(), Timestamp.from(Instant.now().plusSeconds(10)), doc.id()));
            case REJECTED -> reject(doc, result.detail(), null);
            default -> reschedule(doc, result.detail(), 1);
        }
    }

    /** Emisor (Configuración), receptor copiado al crear el comprobante y una línea de servicio con IVA 13 % incluido. */
    private FiscalDraft draft(Claimed doc) {
        var issuer = jdbc.query("""
            SELECT legal_name,id_type,id_number,email,province,canton,district,other_signs,activity_code,cabys_code FROM saas_billing_settings
            WHERE id=1 AND legal_name IS NOT NULL AND activity_code IS NOT NULL AND cabys_code IS NOT NULL AND province IS NOT NULL
              AND other_signs IS NOT NULL AND email IS NOT NULL
            """, (rs, n) -> new String[]{rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6),
            rs.getString(7), rs.getString(8), rs.getString(9), rs.getString(10)}).stream().findFirst().orElse(null);
        if (issuer == null) return null;
        record Row(String name, String type, String number, String email, String detail, BigDecimal amount, String reason) {}
        Row row = jdbc.queryForObject("""
            SELECT receiver_name,receiver_id_type,receiver_id_number,receiver_email,detail,amount,reason FROM saas_fiscal_documents WHERE id=?
            """, (rs, n) -> new Row(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getBigDecimal(6),
            rs.getString(7)), doc.id());
        var payments = new LinkedHashMap<String, BigDecimal>();
        FiscalDraft.Reference reference = null;
        if ("03".equals(doc.docType())) {
            reference = jdbc.queryForObject("SELECT numeric_key,issued_at FROM saas_fiscal_documents WHERE id=?",
                (rs, n) -> new FiscalDraft.Reference("01", rs.getString(1), rs.getTimestamp(2).toInstant(), row.reason()), doc.referenceId());
        } else {
            payments.put(jdbc.queryForObject("SELECT method FROM subscription_payments WHERE id=?", String.class, doc.paymentId()), row.amount());
        }
        var issuerParty = new FiscalDraft.Party(issuer[0], issuer[1], issuer[2], issuer[3], issuer[4], issuer[5], issuer[6], issuer[7]);
        var receiver = new FiscalDraft.Party(row.name(), row.type(), row.number(), row.email(), null, null, null, null);
        var line = new FiscalDraft.Line(issuer[9], BigDecimal.ONE, FiscalDraft.SERVICE, row.detail(), row.amount(), "08");
        return new FiscalDraft(doc.docType(), doc.key(), doc.consecutive(), doc.issuedAt(), endpoints.providerId(), issuer[8], issuerParty,
            receiver, List.of(line), payments, reference);
    }

    private void accept(Claimed doc, byte[] response) {
        tx.executeWithoutResult(s -> jdbc.update("""
            UPDATE saas_fiscal_documents SET status='ACCEPTED',status_reason=NULL,response_xml=?,resolved_at=?,next_attempt_at=NULL,
              mail_status=IF(receiver_email IS NULL,'NONE','PENDING') WHERE id=?
            """, response, now(), doc.id()));
    }

    private void reject(Claimed doc, String reason, byte[] response) {
        tx.executeWithoutResult(s -> jdbc.update("""
            UPDATE saas_fiscal_documents SET status='REJECTED',status_reason=?,response_xml=COALESCE(?,response_xml),resolved_at=?,next_attempt_at=NULL,
              attempts=attempts+1 WHERE id=?
            """, reason == null ? "Hacienda rechazó el comprobante." : reason, response, now(), doc.id()));
    }

    private void later(Claimed doc, Duration wait) {
        tx.executeWithoutResult(s -> jdbc.update("UPDATE saas_fiscal_documents SET status='PROCESSING',next_attempt_at=? WHERE id=?",
            Timestamp.from(Instant.now().plus(wait)), doc.id()));
    }

    /** Espera creciente: 30 s, 1 min, 2 min… hasta 1 h. Los datos faltantes se revisan cada 10 minutos. */
    private void reschedule(Claimed doc, String reason, int countAttempt) {
        long seconds = countAttempt == 0 ? 600 : Math.min(3600, 30L << Math.min(doc.attempts(), 7));
        tx.executeWithoutResult(s -> jdbc.update("UPDATE saas_fiscal_documents SET status_reason=?,attempts=attempts+?,next_attempt_at=? WHERE id=?",
            reason, countAttempt, Timestamp.from(Instant.now().plusSeconds(seconds)), doc.id()));
    }

    /** Correo al comercio con PDF, XML firmado y respuesta de Hacienda; un fallo no cambia el estado fiscal. */
    private void sendPendingMail(int limit) {
        var sender = senders.getIfAvailable();
        if (sender == null) return;
        jdbc.query("""
            SELECT id,doc_type,consecutive,numeric_key,environment,receiver_email,pdf,signed_xml,response_xml FROM saas_fiscal_documents
            WHERE mail_status='PENDING' AND status='ACCEPTED' ORDER BY resolved_at LIMIT ?
            """, rs -> {
            String id = rs.getString(1), key = rs.getString(4);
            boolean sent;
            try {
                MimeMessage message = sender.createMimeMessage();
                var helper = new MimeMessageHelper(message, true, "UTF-8");
                String title = "03".equals(rs.getString(2)) ? "Nota de crédito electrónica" : "Factura electrónica";
                helper.setFrom(from);
                helper.setTo(rs.getString(6));
                helper.setSubject(title + " " + rs.getString(3) + " · Stockly");
                helper.setText("Adjuntamos su " + title.toLowerCase() + " de Stockly aceptada por el Ministerio de Hacienda."
                    + ("TEST".equals(rs.getString(5)) ? "\n\nAmbiente de pruebas: este comprobante no tiene validez fiscal." : ""));
                helper.addAttachment(key + ".pdf", new ByteArrayResource(rs.getBytes(7)), "application/pdf");
                helper.addAttachment(key + ".xml", new ByteArrayResource(rs.getBytes(8)), "application/xml");
                byte[] response = rs.getBytes(9);
                if (response != null) helper.addAttachment(key + "_respuesta.xml", new ByteArrayResource(response), "application/xml");
                sender.send(message);
                sent = true;
            } catch (Exception e) {
                log.warn("No se pudo enviar el comprobante de Stockly {} por correo: {}", id, e.getClass().getSimpleName());
                sent = false;
            }
            jdbc.update("UPDATE saas_fiscal_documents SET mail_status=? WHERE id=?", sent ? "SENT" : "FAILED", id);
        }, limit);
    }

    private static Timestamp now() { return Timestamp.from(Instant.now()); }
}
