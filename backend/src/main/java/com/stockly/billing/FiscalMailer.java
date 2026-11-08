package com.stockly.billing;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/** Entrega del comprobante aceptado (SPEC §11): PDF, XML firmado y respuesta de Hacienda con los nombres de la nota 3. */
@Component
public class FiscalMailer {
    private static final Logger log = LoggerFactory.getLogger(FiscalMailer.class);
    private final JdbcTemplate jdbc;
    private final ObjectProvider<JavaMailSender> senders;
    private final FiscalFiles files;
    private final String from;

    FiscalMailer(JdbcTemplate jdbc, ObjectProvider<JavaMailSender> senders, FiscalFiles files,
                 @Value("${stockly.mail-from:no-reply@stockly.local}") String from) {
        this.jdbc = jdbc; this.senders = senders; this.files = files; this.from = from;
    }

    /** Envía los correos pendientes; un fallo no cambia el estado fiscal y se puede reenviar. */
    void sendPending(int limit) {
        var pending = jdbc.query("""
            SELECT company_id,id FROM fiscal_documents WHERE mail_status='PENDING' AND status='ACCEPTED' ORDER BY resolved_at LIMIT ?
            """, (rs, n) -> new String[]{rs.getString(1), rs.getString(2)}, limit);
        for (var row : pending) {
            boolean sent = send(row[0], row[1]);
            jdbc.update("UPDATE fiscal_documents SET mail_status=? WHERE company_id=? AND id=?", sent ? "SENT" : "FAILED", row[0], row[1]);
        }
    }

    /** Envía (o reenvía) un comprobante aceptado al correo de la venta. */
    public boolean send(String company, String documentId) {
        var sender = senders.getIfAvailable();
        if (sender == null) return false;
        try {
            var bundle = files.bundle(company, documentId);
            if (bundle.email() == null) return false;
            MimeMessage message = sender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(bundle.email());
            helper.setSubject(bundle.title() + " " + bundle.consecutive() + " · " + bundle.issuerName());
            helper.setText("Adjuntamos su " + bundle.title().toLowerCase() + " aceptado por el Ministerio de Hacienda."
                + ("TEST".equals(bundle.environment()) ? "\n\nAmbiente de pruebas: este comprobante no tiene validez fiscal." : ""));
            helper.addAttachment(bundle.key() + ".pdf", new ByteArrayResource(bundle.pdf()), "application/pdf");
            helper.addAttachment(bundle.key() + ".xml", new ByteArrayResource(bundle.xml()), "application/xml");
            if (bundle.response() != null)
                helper.addAttachment(bundle.key() + "_respuesta.xml", new ByteArrayResource(bundle.response()), "application/xml");
            sender.send(message);
            return true;
        } catch (Exception e) {
            log.warn("No se pudo enviar el comprobante {} por correo: {}", documentId, e.getClass().getSimpleName());
            return false;
        }
    }
}
