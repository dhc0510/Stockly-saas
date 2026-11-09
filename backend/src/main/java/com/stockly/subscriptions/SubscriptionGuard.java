package com.stockly.subscriptions;

import com.stockly.identity.IdentityFailure;
import com.stockly.subscriptions.SubscriptionRules.Snapshot;
import com.stockly.subscriptions.SubscriptionRules.State;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * STK-018 · Controles que usan otros módulos: límites de la prueba al crear o reactivar, y bloqueo de acceso por
 * suscripción suspendida. Una empresa sin fila de suscripción (datos anteriores a V18 en pruebas) no tiene límites ni bloqueo.
 */
@Component
public class SubscriptionGuard {
    public enum Limit { PRODUCTS, USERS, BRANCHES }
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final JdbcTemplate jdbc;
    private Clock clock = Clock.system(SubscriptionRules.COSTA_RICA);

    public SubscriptionGuard(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    void clock(Clock clock) { this.clock = clock; }

    /**
     * Llamar dentro de la transacción que crea o reactiva. Bloquea la fila de la suscripción para que dos altas simultáneas
     * no superen el límite. Lanza 409 TRIAL_LIMIT si la empresa nunca pagó y ya llegó al tope.
     */
    public void requireCapacity(String companyId, Limit limit) {
        var row = jdbc.query("SELECT paid_until IS NULL FROM company_subscriptions WHERE company_id=? FOR UPDATE",
            (rs, n) -> rs.getBoolean(1), companyId).stream().findFirst();
        if (row.isEmpty() || !row.get()) return;
        var settings = jdbc.queryForObject("SELECT trial_products,trial_users,trial_branches FROM saas_settings WHERE id=1",
            (rs, n) -> new int[]{rs.getInt(1), rs.getInt(2), rs.getInt(3)});
        int max; String sql, label;
        switch (limit) {
            case PRODUCTS -> { max = settings[0]; sql = "SELECT COUNT(*) FROM catalog_products WHERE company_id=? AND active=TRUE"; label = "productos"; }
            case USERS -> { max = settings[1]; sql = "SELECT COUNT(*) FROM users WHERE company_id=? AND active=TRUE"; label = "usuarios"; }
            default -> { max = settings[2]; sql = "SELECT COUNT(*) FROM inventory_branches WHERE company_id=? AND active=TRUE"; label = "sucursales"; }
        }
        Integer used = jdbc.queryForObject(sql, Integer.class, companyId);
        if (used != null && used >= max)
            throw new IdentityFailure(409, "TRIAL_LIMIT", "Llegaste al límite de la prueba: hasta " + max + " " + label + ". Lo que ya tienes se conserva; con un plan pagado no hay límites.",
                Map.of("limit", limit.name(), "max", String.valueOf(max)));
    }

    public record Suspension(String companyName, LocalDate since, String instructions) {}

    /** Datos de la suspensión por falta de pago, si la suscripción está suspendida hoy (calculado con las fechas). */
    public Optional<Suspension> suspension(String companyId) {
        if (companyId == null) return Optional.empty();
        var row = jdbc.query("""
            SELECT s.trial_ends_on,s.paid_until,s.cancelled_at IS NOT NULL,c.commercial_name,t.grace_days,t.payment_instructions
            FROM company_subscriptions s JOIN companies c ON c.id=s.company_id JOIN saas_settings t ON t.id=1 WHERE s.company_id=?
            """, (rs, n) -> new Object[]{new Snapshot(rs.getDate(1).toLocalDate(), date(rs.getDate(2)), rs.getBoolean(3)), rs.getString(4),
            rs.getInt(5), rs.getString(6)}, companyId).stream().findFirst();
        if (row.isEmpty()) return Optional.empty();
        var snap = (Snapshot) row.get()[0]; int grace = (Integer) row.get()[2];
        if (SubscriptionRules.state(snap, grace, LocalDate.now(clock)) != State.SUSPENDED) return Optional.empty();
        return Optional.of(new Suspension((String) row.get()[1], SubscriptionRules.suspendsOn(snap, grace), (String) row.get()[3]));
    }

    public boolean suspended(String companyId) { return suspension(companyId).isPresent(); }

    /**
     * Error de ingreso con la suscripción suspendida. El administrador ve las instrucciones de pago; los demás usuarios,
     * que deben comunicarse con su administrador (SPEC «Estados y fechas»).
     */
    public IdentityFailure loginFailure(Suspension s, boolean administrator) {
        String message = administrator
            ? "La suscripción de " + s.companyName() + " está suspendida desde el " + DAY.format(s.since()) + " por falta de pago. Tus datos están guardados y no se borran."
            : "El acceso de tu empresa está suspendido. Comunícate con el administrador de tu empresa.";
        var details = new java.util.LinkedHashMap<String, String>();
        details.put("role", administrator ? "ADMIN" : "USER");
        if (administrator) {
            details.put("companyName", s.companyName());
            details.put("since", s.since().toString());
            if (s.instructions() != null) details.put("instructions", s.instructions());
        }
        return new IdentityFailure(403, "SUBSCRIPTION_SUSPENDED", message, details);
    }

    private static LocalDate date(Date value) { return value == null ? null : value.toLocalDate(); }
}
