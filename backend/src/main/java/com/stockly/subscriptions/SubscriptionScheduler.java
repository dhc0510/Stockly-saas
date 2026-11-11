package com.stockly.subscriptions;

import com.stockly.identity.EmailDelivery;
import com.stockly.subscriptions.SubscriptionRules.Snapshot;
import com.stockly.subscriptions.SubscriptionRules.State;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * STK-018 · Cada hora: recalcula estados (suspende y cierra sesiones al terminar la gracia) y envía los avisos al
 * administrador de cada empresa. Cada aviso se registra antes de enviarse para no repetirlo; si el correo falla se libera.
 */
@Component
public class SubscriptionScheduler {
    private static final Logger log = LoggerFactory.getLogger(SubscriptionScheduler.class);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final JdbcTemplate jdbc;
    private final SubscriptionService subscriptions;
    private final EmailDelivery email;
    private final boolean enabled;

    SubscriptionScheduler(JdbcTemplate jdbc, SubscriptionService subscriptions, EmailDelivery email,
                          @Value("${stockly.subscriptions.scheduler-enabled:true}") boolean enabled) {
        this.jdbc = jdbc; this.subscriptions = subscriptions; this.email = email; this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${stockly.subscriptions.delay-ms:3600000}", initialDelay = 30000)
    void scheduled() {
        if (!enabled) return;
        try { runOnce(); } catch (RuntimeException e) { log.warn("No se pudieron revisar las suscripciones: {}", e.getClass().getSimpleName()); }
    }

    /** Devuelve cuántos avisos envió. */
    public int runOnce() {
        subscriptions.refreshStates();
        var settings = subscriptions.settings();
        LocalDate today = subscriptions.today();
        record Due(String company, String name, Snapshot snap, String plan, java.math.BigDecimal price) {}
        var due = new ArrayList<Due>();
        jdbc.query("""
            SELECT s.company_id,c.commercial_name,s.trial_ends_on,s.paid_until,s.cancelled_at IS NOT NULL,p.name,
              IF(s.cycle='ANNUAL',p.annual_price,p.monthly_price)
            FROM company_subscriptions s JOIN companies c ON c.id=s.company_id LEFT JOIN subscription_plans p ON p.id=s.plan_id
            WHERE c.status='ACTIVE'
            """, rs -> { due.add(new Due(rs.getString(1), rs.getString(2), new Snapshot(rs.getDate(3).toLocalDate(),
                rs.getDate(4) == null ? null : rs.getDate(4).toLocalDate(), rs.getBoolean(5)), rs.getString(6), rs.getBigDecimal(7))); });
        int sent = 0;
        for (Due d : due) {
            if (d.snap().cancelled()) continue;   // Cancelada: no se envían más avisos de cobro.
            State state = SubscriptionRules.state(d.snap(), settings.graceDays(), today);
            LocalDate expires = d.snap().expiresOn(), suspends = SubscriptionRules.suspendsOn(d.snap(), settings.graceDays());
            String kind = null;
            if ((state == State.TRIAL || state == State.ACTIVE) && !today.isBefore(expires.minusDays(settings.noticeDays()))) kind = "BEFORE";
            else if (state == State.GRACE) kind = "EXPIRED";
            else if (state == State.SUSPENDED) kind = "SUSPENDED";
            if (kind == null) continue;
            // Se buscan los destinatarios antes de registrar el aviso: si la consulta falla, el aviso no queda marcado como enviado.
            List<String[]> admins = jdbc.query("SELECT email_normalized,full_name FROM users WHERE company_id=? AND role='COMPANY_ADMIN' AND active=TRUE",
                (rs, n) -> new String[]{rs.getString(1), rs.getString(2)}, d.company());
            if (admins.isEmpty()) continue;
            if (jdbc.update("INSERT IGNORE INTO subscription_notices(company_id,kind,due_on,sent_at) VALUES (?,?,?,?)",
                d.company(), kind, Date.valueOf(expires), Timestamp.from(Instant.now())) == 0) continue;
            boolean trial = d.snap().paidUntil() == null;
            String what = trial ? "La prueba de Stockly de " + d.name() : "La suscripción de Stockly de " + d.name()
                + (d.plan() == null ? "" : " (plan " + d.plan() + (d.price() == null ? "" : ", ₡" + d.price().stripTrailingZeros().toPlainString()) + ")");
            String subject, body;
            switch (kind) {
                case "BEFORE" -> {
                    subject = (trial ? "Tu prueba de Stockly termina el " : "Tu suscripción de Stockly vence el ") + DAY.format(expires);
                    body = what + (trial ? " termina el " : " vence el ") + DAY.format(expires) + ". Paga a tiempo para seguir sin interrupciones.";
                }
                case "EXPIRED" -> {
                    subject = "Tu suscripción de Stockly venció · tienes hasta el " + DAY.format(suspends);
                    body = what + (trial ? " terminó el " : " venció el ") + DAY.format(expires) + ". Mantienes el acceso completo hasta el "
                        + DAY.format(suspends) + ". Si para entonces no se registra el pago, el acceso se suspende; tus datos se conservan.";
                }
                default -> {
                    subject = "Tu acceso a Stockly está suspendido";
                    body = "El acceso de " + d.name() + " se suspendió el " + DAY.format(suspends) + " por falta de pago. Tus datos están guardados y no se borran."
                        + " Al registrar el pago se reactiva el acceso.";
                }
            }
            String instructions = settings.paymentInstructions() == null ? "" : "\n\nCómo pagar:\n" + settings.paymentInstructions();
            try {
                for (String[] admin : admins)
                    email.send(admin[0], subject, "Hola, " + admin[1] + ":\n\n" + body + instructions
                        + "\n\nRecibes este correo porque administras " + d.name() + " en Stockly.");
                sent++;
            } catch (RuntimeException e) {
                // Se libera el aviso para reintentarlo en la próxima revisión.
                jdbc.update("DELETE FROM subscription_notices WHERE company_id=? AND kind=? AND due_on=?", d.company(), kind, Date.valueOf(expires));
                log.warn("No se pudo enviar el aviso de suscripción de {}: {}", d.company(), e.getClass().getSimpleName());
            }
        }
        return sent;
    }
}
