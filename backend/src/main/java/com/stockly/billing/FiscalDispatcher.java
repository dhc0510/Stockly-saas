package com.stockly.billing;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Envía los comprobantes pendientes y consulta su resultado (SPEC §8). Cada comprobante se reserva con un "lease"
 * (FOR UPDATE SKIP LOCKED) para que dos procesos nunca lo envíen a la vez. Antes de reenviar siempre consulta la clave.
 */
@Component
public class FiscalDispatcher {
    private static final Logger log = LoggerFactory.getLogger(FiscalDispatcher.class);
    private static final Duration LEASE = Duration.ofMinutes(5);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final BillingCrypto crypto;
    private final BillingSettingsService settings;
    private final FiscalAssembler assembler;
    private final FiscalXmlBuilder builder;
    private final FiscalSigner signer;
    private final FiscalSchemas schemas;
    private final HaciendaClient hacienda;
    private final FiscalDocumentService documents;
    private final FiscalMailer mailer;
    private final FiscalPdf pdf;
    private final boolean enabled;

    FiscalDispatcher(JdbcTemplate jdbc, TransactionTemplate tx, BillingCrypto crypto, BillingSettingsService settings,
                     FiscalAssembler assembler, FiscalXmlBuilder builder, FiscalSigner signer, FiscalSchemas schemas,
                     HaciendaClient hacienda, FiscalDocumentService documents, FiscalMailer mailer, FiscalPdf pdf,
                     @Value("${stockly.billing.dispatch-enabled:true}") boolean enabled) {
        this.jdbc = jdbc; this.tx = tx; this.crypto = crypto; this.settings = settings; this.assembler = assembler;
        this.builder = builder; this.signer = signer; this.schemas = schemas; this.hacienda = hacienda;
        this.documents = documents; this.mailer = mailer; this.pdf = pdf; this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${stockly.billing.dispatch-delay-ms:15000}", initialDelay = 20000)
    void scheduled() {
        if (enabled && crypto.enabled()) runOnce(20);
    }

    /** Procesa hasta {@code limit} comprobantes vencidos y los correos pendientes. Devuelve cuántos atendió. */
    public int runOnce(int limit) {
        enqueueReturnCredits(limit);
        int handled = 0;
        for (var doc : claim(limit)) {
            try { process(doc); }
            catch (RuntimeException e) {
                // Nunca se registra el XML ni credenciales; solo el comprobante y el tipo de error.
                log.warn("Comprobante {} no se pudo procesar: {}", doc.id(), e.getClass().getSimpleName());
                reschedule(doc, "Error inesperado al procesar. Se reintentará.", 1);
            }
            handled++;
        }
        mailer.sendPending(limit);
        return handled;
    }

    private void enqueueReturnCredits(int limit) {
        var pending = jdbc.query("""
            SELECT r.company_id,r.id FROM sale_returns r
            JOIN fiscal_documents f ON f.company_id=r.company_id AND f.id=(
              SELECT d.id FROM fiscal_documents d WHERE d.company_id=r.company_id AND d.sale_id=r.sale_id AND d.doc_type<>'03'
              ORDER BY d.created_at DESC,d.id DESC LIMIT 1)
            WHERE f.status='ACCEPTED'
              AND NOT EXISTS(SELECT 1 FROM fiscal_documents c WHERE c.company_id=r.company_id AND c.sale_return_id=r.id)
            ORDER BY r.created_at,r.id LIMIT ?
            """, (rs,row) -> new String[]{rs.getString(1),rs.getString(2)}, limit);
        for (var operation : pending) {
            try { documents.issueForReturn(operation[0], operation[1]); }
            catch (RuntimeException failure) {
                log.warn("No se pudo encolar la nota de devolución {}: {}", operation[1], failure.getClass().getSimpleName());
            }
        }
    }

    private record Claimed(FiscalAssembler.Doc doc, String status, int attempts, boolean signed) {
        String id() { return doc.id(); }
        String company() { return doc.company(); }
    }

    private List<Claimed> claim(int limit) {
        return tx.execute(status -> {
            var rows = jdbc.query("""
                SELECT company_id,id,sale_id,doc_type,environment,consecutive,numeric_key,issued_at,reference_id,status,attempts,
                  signed_xml IS NOT NULL
                FROM fiscal_documents WHERE status IN ('PENDING','PROCESSING') AND next_attempt_at<=?
                ORDER BY next_attempt_at LIMIT ? FOR UPDATE SKIP LOCKED
                """, (rs, n) -> new Claimed(new FiscalAssembler.Doc(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getString(6), rs.getString(7), rs.getTimestamp(8).toInstant(), rs.getString(9)),
                rs.getString(10), rs.getInt(11), rs.getBoolean(12)), now(), limit);
            for (var row : rows)
                jdbc.update("UPDATE fiscal_documents SET next_attempt_at=? WHERE company_id=? AND id=?",
                    Timestamp.from(Instant.now().plus(LEASE)), row.company(), row.id());
            return rows;
        });
    }

    private void process(Claimed claimed) {
        var doc = claimed.doc();
        var stored = settings.credentials(doc.company());
        if (stored.isEmpty()) { reschedule(claimed, "Faltan las credenciales de Hacienda en Facturación.", 0); return; }
        var credentials = stored.get().api();

        // Antes de reenviar, consultar si Hacienda ya lo tiene (SPEC §8): evita duplicados tras una respuesta perdida.
        if ("PROCESSING".equals(claimed.status()) || claimed.attempts() > 0) {
            var answer = hacienda.status(doc.environment(), credentials, doc.key());
            switch (answer.outcome()) {
                case ACCEPTED -> { accept(claimed, answer.responseXml()); return; }
                case REJECTED -> { reject(claimed, answer.detail(), answer.responseXml()); return; }
                case PROCESSING -> { processing(claimed, Duration.ofSeconds(30)); return; }
                case RETRY -> { reschedule(claimed, answer.detail(), 1); return; }
                default -> { if (cancelIfVoided(claimed)) return; }
            }
        }

        byte[] signed = claimed.signed() ? jdbc.queryForObject("SELECT signed_xml FROM fiscal_documents WHERE company_id=? AND id=?",
            byte[].class, doc.company(), doc.id()) : null;
        if (signed == null) {
            var assembly = assembler.assemble(doc);
            if (assembly.missing() != null) { reschedule(claimed, assembly.missing(), 0); return; }
            var key = jdbc.queryForObject("SELECT key_store_enc,key_pin_enc FROM billing_settings WHERE company_id=?",
                (rs, n) -> new byte[][]{rs.getBytes(1), rs.getBytes(2)}, doc.company());
            signed = signer.sign(builder.build(assembly.draft()), crypto.decrypt(doc.company(), key[0]),
                new String(crypto.decrypt(doc.company(), key[1]), java.nio.charset.StandardCharsets.UTF_8).toCharArray());
            if (schemas.available(doc.docType())) {
                try { schemas.validate(doc.docType(), signed); }
                catch (IllegalArgumentException invalid) {
                    // SPEC §9: lo que no cumple el esquema no se envía ni se reintenta; queda rechazado y se corrige con un comprobante nuevo.
                    reject(claimed, schemaReason(invalid.getMessage()), null);
                    return;
                }
            }
            // La representación gráfica se genera junto con la firma, con los mismos datos del XML.
            byte[] toStore = signed, graphic = pdf.render(assembly.draft(), doc.environment());
            tx.executeWithoutResult(s -> jdbc.update("UPDATE fiscal_documents SET signed_xml=?,pdf=? WHERE company_id=? AND id=? AND signed_xml IS NULL",
                toStore, graphic, doc.company(), doc.id()));
        }

        var draftParties = jdbc.queryForObject("""
            SELECT c.identification_type,c.identification_normalized,s.customer_id_type,s.customer_id_number
            FROM companies c JOIN sales s ON s.company_id=c.id WHERE c.id=? AND s.id=?
            """, (rs, n) -> new String[]{FiscalNumbers.issuerType(rs.getString(1)), rs.getString(2), rs.getString(3), rs.getString(4)},
            doc.company(), doc.saleId());
        boolean withReceiver = !FiscalDocumentService.TICKET.equals(doc.docType()) && draftParties[3] != null;
        var result = hacienda.submit(doc.environment(), credentials, new HaciendaClient.Submission(doc.key(), doc.issuedAt(),
            draftParties[0], draftParties[1], withReceiver ? draftParties[2] : null, withReceiver ? draftParties[3] : null, signed));
        switch (result.outcome()) {
            case RECEIVED, ALREADY_RECEIVED -> {
                tx.executeWithoutResult(s -> jdbc.update("""
                    UPDATE fiscal_documents SET status='PROCESSING',status_reason=NULL,attempts=attempts+1,sent_at=COALESCE(sent_at,?),
                      next_attempt_at=? WHERE company_id=? AND id=?
                    """, now(), Timestamp.from(Instant.now().plusSeconds(10)), doc.company(), doc.id()));
            }
            case REJECTED -> reject(claimed, result.detail(), null);
            default -> reschedule(claimed, result.detail(), 1);
        }
    }

    private void accept(Claimed claimed, byte[] response) {
        var doc = claimed.doc();
        tx.executeWithoutResult(s -> {
            boolean hasEmail = Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT customer_email IS NOT NULL FROM sales WHERE company_id=? AND id=?
                """, Boolean.class, doc.company(), doc.saleId()));
            jdbc.update("""
                UPDATE fiscal_documents SET status='ACCEPTED',status_reason=NULL,response_xml=?,resolved_at=?,next_attempt_at=NULL,
                  mail_status=? WHERE company_id=? AND id=?
                """, response, now(), hasEmail ? "PENDING" : "NONE", doc.company(), doc.id());
            if (!FiscalDocumentService.CREDIT_NOTE.equals(doc.docType())) documents.creditNoteIfVoided(doc.company(), doc.saleId(), doc.id());
        });
    }

    /** Motivo legible para un XML que no cumple el esquema; el detalle técnico no incluye datos del XML firmado ni credenciales. */
    static String schemaReason(String detail) {
        String text = detail == null ? "" : detail;
        if (text.contains("Nombre")) return "No se envió a Hacienda: el nombre del cliente debe tener de 3 a 100 caracteres. Corrige los datos del cliente.";
        String clean = text.replaceFirst("^El XML no cumple el esquema v4\\.4: ", "").replaceAll("\\s+", " ").strip();
        if (clean.length() > 160) clean = clean.substring(0, 160) + "…";
        return "No se envió a Hacienda: los datos no cumplen el formato v4.4" + (clean.isEmpty() ? "." : " (" + clean + ").") + " Corrige los datos del cliente.";
    }

    private void reject(Claimed claimed, String reason, byte[] response) {
        var doc = claimed.doc();
        tx.executeWithoutResult(s -> jdbc.update("""
            UPDATE fiscal_documents SET status='REJECTED',status_reason=?,response_xml=COALESCE(?,response_xml),resolved_at=?,
              next_attempt_at=NULL,attempts=attempts+1 WHERE company_id=? AND id=?
            """, reason == null ? "Hacienda rechazó el comprobante." : reason, response, now(), doc.company(), doc.id()));
    }

    private void processing(Claimed claimed, Duration wait) {
        tx.executeWithoutResult(s -> jdbc.update("UPDATE fiscal_documents SET status='PROCESSING',next_attempt_at=? WHERE company_id=? AND id=?",
            Timestamp.from(Instant.now().plus(wait)), claimed.company(), claimed.id()));
    }

    /** Si la venta se anuló y Hacienda confirmó que no tiene el comprobante, se cancela en vez de enviarlo. */
    private boolean cancelIfVoided(Claimed claimed) {
        var doc = claimed.doc();
        if (FiscalDocumentService.CREDIT_NOTE.equals(doc.docType())) return false;
        Boolean voided = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM inventory_movements WHERE company_id=? AND reversed_id=?)",
            Boolean.class, doc.company(), doc.saleId());
        if (!Boolean.TRUE.equals(voided)) return false;
        tx.executeWithoutResult(s -> jdbc.update("""
            UPDATE fiscal_documents SET status='CANCELLED',status_reason='La venta se anuló y Hacienda no había recibido el comprobante.',
              next_attempt_at=NULL,resolved_at=? WHERE company_id=? AND id=?
            """, now(), doc.company(), doc.id()));
        return true;
    }

    /** Espera creciente: 30 s, 1 min, 2 min… hasta 1 h. Los datos faltantes se revisan cada 10 minutos. */
    private void reschedule(Claimed claimed, String reason, int countAttempt) {
        long seconds = countAttempt == 0 ? 600 : Math.min(3600, 30L << Math.min(claimed.attempts(), 7));
        tx.executeWithoutResult(s -> jdbc.update("""
            UPDATE fiscal_documents SET status_reason=?,attempts=attempts+?,next_attempt_at=? WHERE company_id=? AND id=?
            """, reason, countAttempt, Timestamp.from(Instant.now().plusSeconds(seconds)), claimed.company(), claimed.id()));
    }

    private static Timestamp now() { return Timestamp.from(Instant.now()); }
}
