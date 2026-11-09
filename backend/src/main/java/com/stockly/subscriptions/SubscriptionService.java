package com.stockly.subscriptions;

import com.stockly.identity.IdentityFailure;
import com.stockly.identity.StocklyPrincipal;
import com.stockly.subscriptions.SubscriptionRules.Snapshot;
import com.stockly.subscriptions.SubscriptionRules.State;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * STK-018 · Planes, configuración, suscripciones y pagos. El estado se calcula siempre con las fechas (SubscriptionRules);
 * la columna «state» solo sirve para listar y filtrar. Stockly no procesa dinero: los pagos se registran a mano.
 */
@Service
public class SubscriptionService {
    static final Set<String> METHODS = Set.of("SINPE", "TRANSFER", "CASH");
    private final JdbcTemplate jdbc;
    private Clock clock = Clock.system(SubscriptionRules.COSTA_RICA);

    public SubscriptionService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Solo para pruebas: fija «hoy». */
    void clock(Clock clock) { this.clock = clock; }
    LocalDate today() { return LocalDate.now(clock); }

    // ---------- Configuración ----------

    public record Settings(int trialDays, int graceDays, int noticeDays, int trialProducts, int trialUsers, int trialBranches,
                           String paymentInstructions, long version) {}

    @Transactional(readOnly = true)
    public Settings settings() {
        return jdbc.queryForObject("""
            SELECT trial_days,grace_days,notice_days,trial_products,trial_users,trial_branches,payment_instructions,version FROM saas_settings WHERE id=1
            """, (rs, n) -> new Settings(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getInt(5), rs.getInt(6), rs.getString(7), rs.getLong(8)));
    }

    @Transactional
    public Settings saveSettings(StocklyPrincipal actor, Settings input) {
        if (input == null) throw invalid("request", "Completa la configuración.");
        var errors = new LinkedHashMap<String, String>();
        range(errors, "trialDays", input.trialDays(), 1, 90, "Los días de prueba van de 1 a 90.");
        range(errors, "graceDays", input.graceDays(), 0, 60, "Los días de gracia van de 0 a 60.");
        range(errors, "noticeDays", input.noticeDays(), 1, 30, "El aviso va de 1 a 30 días antes.");
        range(errors, "trialProducts", input.trialProducts(), 1, 100000, "Indica un límite de productos válido.");
        range(errors, "trialUsers", input.trialUsers(), 1, 1000, "Indica un límite de usuarios válido.");
        range(errors, "trialBranches", input.trialBranches(), 1, 1000, "Indica un límite de sucursales válido.");
        String instructions = clean(input.paymentInstructions());
        if (instructions != null && instructions.length() > 2000) errors.put("paymentInstructions", "Las instrucciones admiten hasta 2000 caracteres.");
        if (!errors.isEmpty()) throw new IdentityFailure(400, "SUBSCRIPTION_INVALID", "Revisa la configuración.", errors);
        int changed = jdbc.update("""
            UPDATE saas_settings SET trial_days=?,grace_days=?,notice_days=?,trial_products=?,trial_users=?,trial_branches=?,payment_instructions=?,
              updated_at=?,version=version+1 WHERE id=1 AND version=?
            """, input.trialDays(), input.graceDays(), input.noticeDays(), input.trialProducts(), input.trialUsers(), input.trialBranches(),
            instructions, now(), input.version());
        if (changed == 0) throw stale();
        audit(actor, "SAAS_SETTINGS_UPDATED", null, null, null);
        return settings();
    }

    // ---------- Planes ----------

    public record Plan(String id, String name, BigDecimal monthlyPrice, BigDecimal annualPrice, String description, boolean active,
                       int companies, boolean hasPayments, long version) {}
    public record PlanInput(String name, BigDecimal monthlyPrice, BigDecimal annualPrice, String description, Boolean active, Long version) {}

    @Transactional(readOnly = true)
    public List<Plan> plans() {
        return jdbc.query("""
            SELECT p.id,p.name,p.monthly_price,p.annual_price,p.description,p.active,
              (SELECT COUNT(*) FROM company_subscriptions s WHERE s.plan_id=p.id),
              EXISTS(SELECT 1 FROM subscription_payments x WHERE x.plan_id=p.id),p.version
            FROM subscription_plans p ORDER BY p.active DESC,p.name
            """, (rs, n) -> new Plan(rs.getString(1), rs.getString(2), rs.getBigDecimal(3), rs.getBigDecimal(4), rs.getString(5),
            rs.getBoolean(6), rs.getInt(7), rs.getBoolean(8), rs.getLong(9)));
    }

    @Transactional
    public Plan savePlan(StocklyPrincipal actor, String id, PlanInput input) {
        if (input == null) throw invalid("request", "Completa el plan.");
        var errors = new LinkedHashMap<String, String>();
        String name = clean(input.name());
        if (name == null || name.length() < 2 || name.length() > 80) errors.put("name", "Escribe el nombre del plan (2 a 80 caracteres).");
        BigDecimal monthly = money(input.monthlyPrice(), "monthlyPrice", true, errors), annual = money(input.annualPrice(), "annualPrice", false, errors);
        String description = clean(input.description());
        if (description != null && description.length() > 500) errors.put("description", "La descripción admite hasta 500 caracteres.");
        if (name != null && Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM subscription_plans WHERE name=? AND id<>?)",
            Boolean.class, name, id == null ? "" : id))) errors.put("name", "Ya existe un plan con ese nombre.");
        if (!errors.isEmpty()) throw new IdentityFailure(400, "SUBSCRIPTION_INVALID", "Revisa el plan.", errors);
        boolean active = input.active() == null || input.active();
        if (id == null) {
            id = UUID.randomUUID().toString();
            jdbc.update("""
                INSERT INTO subscription_plans(id,name,monthly_price,annual_price,description,active,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?)
                """, id, name, monthly, annual, description, active, now(), now());
            audit(actor, "SAAS_PLAN_CREATED", null, id, name);
        } else {
            if (input.version() == null) throw invalid("version", "Versión requerida.");
            int changed = jdbc.update("""
                UPDATE subscription_plans SET name=?,monthly_price=?,annual_price=?,description=?,active=?,updated_at=?,version=version+1
                WHERE id=? AND version=?
                """, name, monthly, annual, description, active, now(), id, input.version());
            if (changed == 0) {
                if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM subscription_plans WHERE id=?)", Boolean.class, id)))
                    throw new IdentityFailure(404, "PLAN_NOT_FOUND", "No se encontró el plan.");
                throw stale();
            }
            audit(actor, "SAAS_PLAN_UPDATED", null, id, name);
        }
        String planId = id;
        return plans().stream().filter(p -> p.id().equals(planId)).findFirst().orElseThrow();
    }

    // ---------- Suscripción de una empresa ----------

    public record Payment(String id, String planName, String cycle, int periods, BigDecimal amount, LocalDate paidOn, String method,
                          String reference, LocalDate coversFrom, LocalDate coversUntil, String createdBy, Instant createdAt,
                          Instant voidedAt, String voidedBy, String voidReason) {}
    public record Subscription(String companyId, String companyName, String legalName, String state, String planId, String planName,
                               String cycle, BigDecimal price, LocalDate trialEndsOn, LocalDate paidUntil, LocalDate expiresOn,
                               LocalDate suspendsOn, boolean limited, Instant cancelledAt, String cancelReason, Instant createdAt,
                               List<Payment> payments, List<LocalDate> noticesSent, long version) {}

    @Transactional(readOnly = true)
    public Subscription subscription(String companyId) {
        var settings = settings();
        record Row(String name, String legal, String planId, String plan, String cycle, BigDecimal monthly, BigDecimal annual, LocalDate trial,
                   LocalDate paid, Instant cancelled, String reason, Instant created, long version) {}
        Row row = jdbc.query("""
            SELECT c.commercial_name,c.legal_name,s.plan_id,p.name,s.cycle,p.monthly_price,p.annual_price,s.trial_ends_on,s.paid_until,
              s.cancelled_at,s.cancel_reason,c.created_at,s.version
            FROM company_subscriptions s JOIN companies c ON c.id=s.company_id LEFT JOIN subscription_plans p ON p.id=s.plan_id WHERE s.company_id=?
            """, (rs, n) -> new Row(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getBigDecimal(6),
            rs.getBigDecimal(7), rs.getDate(8).toLocalDate(), date(rs.getDate(9)), instant(rs.getTimestamp(10)), rs.getString(11),
            instant(rs.getTimestamp(12)), rs.getLong(13)), companyId).stream().findFirst()
            .orElseThrow(() -> new IdentityFailure(404, "COMPANY_NOT_FOUND", "No se encontró la empresa."));
        var snapshot = new Snapshot(row.trial(), row.paid(), row.cancelled() != null);
        var payments = jdbc.query("""
            SELECT id,plan_name,cycle,periods,amount,paid_on,method,reference,covers_from,covers_until,created_by_name,created_at,voided_at,
              voided_by_name,void_reason FROM subscription_payments WHERE company_id=? ORDER BY created_at DESC,id
            """, (rs, n) -> new Payment(rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getBigDecimal(5),
            rs.getDate(6).toLocalDate(), rs.getString(7), rs.getString(8), rs.getDate(9).toLocalDate(), rs.getDate(10).toLocalDate(),
            rs.getString(11), rs.getTimestamp(12).toInstant(), instant(rs.getTimestamp(13)), rs.getString(14), rs.getString(15)), companyId);
        var notices = jdbc.queryForList("SELECT DATE(sent_at) FROM subscription_notices WHERE company_id=? ORDER BY sent_at", Date.class, companyId)
            .stream().map(Date::toLocalDate).toList();
        BigDecimal price = row.planId() == null ? null : "ANNUAL".equals(row.cycle()) ? row.annual() : row.monthly();
        return new Subscription(companyId, row.name(), row.legal(), SubscriptionRules.state(snapshot, settings.graceDays(), today()).name(),
            row.planId(), row.plan(), row.cycle(), price, row.trial(), row.paid(), snapshot.expiresOn(),
            SubscriptionRules.suspendsOn(snapshot, settings.graceDays()), snapshot.limited(), row.cancelled(), row.reason(), row.created(),
            payments, notices, row.version());
    }

    public record Summary(String companyId, String state, String planName, String cycle, LocalDate expiresOn, LocalDate suspendsOn,
                          LocalDate lastPayment, boolean limited) {}

    /** Datos de suscripción para la lista de empresas (STK-004). */
    @Transactional(readOnly = true)
    public List<Summary> summaries(Collection<String> companyIds) {
        if (companyIds.isEmpty()) return List.of();
        var settings = settings(); LocalDate today = today();
        String marks = String.join(",", Collections.nCopies(companyIds.size(), "?"));
        return jdbc.query("""
            SELECT s.company_id,p.name,s.cycle,s.trial_ends_on,s.paid_until,s.cancelled_at IS NOT NULL,
              (SELECT MAX(x.paid_on) FROM subscription_payments x WHERE x.company_id=s.company_id AND x.voided_at IS NULL)
            FROM company_subscriptions s LEFT JOIN subscription_plans p ON p.id=s.plan_id WHERE s.company_id IN (""" + marks + ")",
            (rs, n) -> {
                var snap = new Snapshot(rs.getDate(4).toLocalDate(), date(rs.getDate(5)), rs.getBoolean(6));
                return new Summary(rs.getString(1), SubscriptionRules.state(snap, settings.graceDays(), today).name(), rs.getString(2),
                    rs.getString(3), snap.expiresOn(), SubscriptionRules.suspendsOn(snap, settings.graceDays()), date(rs.getDate(7)), snap.limited());
            }, companyIds.toArray());
    }

    /** Empresas cuyo último estado calculado coincide (para el filtro de la lista de empresas). */
    @Transactional
    public List<String> companiesInState(String state) {
        if (Arrays.stream(State.values()).noneMatch(s -> s.name().equals(state))) throw invalid("subscription", "Estado de suscripción inválido.");
        refreshStates();
        return jdbc.queryForList("SELECT company_id FROM company_subscriptions WHERE state=?", String.class, state);
    }

    /** STK-004: una empresa nueva empieza en prueba. Llamar en la misma transacción que crea la empresa. */
    public void startTrial(String companyId) {
        var settings = settings();
        jdbc.update("""
            INSERT INTO company_subscriptions(company_id,state,trial_ends_on,created_at,updated_at) VALUES (?,'TRIAL',?,?,?)
            """, companyId, Date.valueOf(today().plusDays(settings.trialDays())), now(), now());
    }

    // ---------- Pagos ----------

    public record PaymentInput(String requestKey, String planId, String cycle, Integer periods, BigDecimal amount, String paidOn,
                               String method, String reference) {}

    @Transactional
    public Subscription registerPayment(StocklyPrincipal actor, String companyId, PaymentInput input) {
        if (input == null) throw invalid("request", "Completa el pago.");
        String requestKey = clean(input.requestKey());
        if (requestKey == null || !requestKey.matches("[0-9a-fA-F-]{36}")) throw invalid("requestKey", "Solicitud inválida.");
        Snapshot snap = lock(companyId);
        // Un reintento con la misma clave devuelve el resultado ya registrado, sin duplicar.
        if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM subscription_payments WHERE company_id=? AND request_key=?)",
            Boolean.class, companyId, requestKey))) return subscription(companyId);
        var errors = new LinkedHashMap<String, String>();
        String currentPlan = jdbc.queryForObject("SELECT plan_id FROM company_subscriptions WHERE company_id=?", String.class, companyId);
        var plan = jdbc.query("SELECT id,name,monthly_price,annual_price,active FROM subscription_plans WHERE id=?",
            (rs, n) -> new Object[]{rs.getString(1), rs.getString(2), rs.getBigDecimal(3), rs.getBigDecimal(4), rs.getBoolean(5)},
            Objects.requireNonNullElse(input.planId(), "")).stream().findFirst().orElse(null);
        if (plan == null || !(Boolean) plan[4] && !plan[0].equals(currentPlan)) errors.put("planId", "Elige un plan disponible.");
        String cycle = input.cycle();
        if (!"MONTHLY".equals(cycle) && !"ANNUAL".equals(cycle)) errors.put("cycle", "Elige el ciclo.");
        else if (plan != null && "ANNUAL".equals(cycle) && plan[3] == null) errors.put("cycle", "Este plan no tiene precio anual.");
        int periods = input.periods() == null ? 0 : input.periods();
        if (periods < 1 || periods > 12) errors.put("periods", "Los períodos van de 1 a 12.");
        LocalDate paidOn = null;
        try { paidOn = input.paidOn() == null ? null : LocalDate.parse(input.paidOn()); } catch (DateTimeParseException ignored) {}
        if (paidOn == null) errors.put("paidOn", "Indica la fecha del pago.");
        else if (paidOn.isAfter(today())) errors.put("paidOn", "La fecha del pago no puede ser futura.");
        String method = input.method();
        if (!METHODS.contains(method)) errors.put("method", "Elige el medio de pago.");
        String reference = clean(input.reference());
        if (reference != null && reference.length() > 60) errors.put("reference", "La referencia admite hasta 60 caracteres.");
        if (METHODS.contains(method) && !"CASH".equals(method) && reference == null)
            errors.put("reference", "La referencia es obligatoria para SINPE Móvil y transferencia.");
        BigDecimal expected = null;
        if (plan != null && !errors.containsKey("cycle") && periods >= 1 && periods <= 12)
            expected = SubscriptionRules.expectedAmount((BigDecimal) ("ANNUAL".equals(cycle) ? plan[3] : plan[2]), periods);
        if (input.amount() == null || input.amount().signum() <= 0 || input.amount().stripTrailingZeros().scale() > 2)
            errors.put("amount", "Escribe el monto recibido.");
        else if (expected != null && input.amount().compareTo(expected) != 0)
            errors.put("amount", "El monto debe ser igual al precio del plan (₡" + expected.stripTrailingZeros().toPlainString() + ").");
        if (!errors.isEmpty()) throw new IdentityFailure(400, "SUBSCRIPTION_INVALID", "Revisa los datos del pago.", errors);

        var settings = settings();
        LocalDate from = SubscriptionRules.periodStart(snap, settings.graceDays(), today(), paidOn);
        LocalDate until = SubscriptionRules.periodEnd(from, cycle, periods);
        String id = UUID.randomUUID().toString();
        jdbc.update("""
            INSERT INTO subscription_payments(id,company_id,plan_id,plan_name,cycle,periods,amount,paid_on,method,reference,covers_from,covers_until,
              request_key,created_by,created_by_name,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """, id, companyId, plan[0], plan[1], cycle, periods, expected, Date.valueOf(paidOn), method, reference,
            Date.valueOf(from), Date.valueOf(until), requestKey, actor.accountId(), actorName(actor), now());
        jdbc.update("""
            UPDATE company_subscriptions SET plan_id=?,cycle=?,paid_until=GREATEST(COALESCE(paid_until,?),?),cancelled_at=NULL,cancelled_by=NULL,
              cancel_reason=NULL,updated_at=?,version=version+1 WHERE company_id=?
            """, plan[0], cycle, Date.valueOf(until), Date.valueOf(until), now(), companyId);
        persistState(companyId);
        audit(actor, "SUBSCRIPTION_PAYMENT_REGISTERED", companyId, id, plan[1] + " · ₡" + expected.toPlainString());
        return subscription(companyId);
    }

    @Transactional
    public Subscription voidPayment(StocklyPrincipal actor, String companyId, String paymentId, String reason) {
        lock(companyId);
        String clean = clean(reason);
        if (clean == null || clean.length() > 150) throw invalid("reason", "Escribe el motivo (hasta 150 caracteres).");
        int changed = jdbc.update("""
            UPDATE subscription_payments SET voided_at=?,voided_by_name=?,void_reason=? WHERE company_id=? AND id=? AND voided_at IS NULL
            """, now(), actorName(actor), clean, companyId, paymentId);
        if (changed == 0) throw new IdentityFailure(404, "PAYMENT_NOT_FOUND", "No se encontró el pago o ya estaba anulado.");
        // «Pagado hasta», plan y ciclo salen de los pagos vigentes; sin ninguno, la empresa vuelve a depender de la prueba.
        var latest = jdbc.query("""
            SELECT plan_id,cycle FROM subscription_payments WHERE company_id=? AND voided_at IS NULL ORDER BY created_at DESC,id LIMIT 1
            """, (rs, n) -> new String[]{rs.getString(1), rs.getString(2)}, companyId).stream().findFirst().orElse(new String[2]);
        jdbc.update("""
            UPDATE company_subscriptions SET paid_until=(SELECT MAX(covers_until) FROM subscription_payments WHERE company_id=? AND voided_at IS NULL),
              plan_id=?,cycle=?,updated_at=?,version=version+1 WHERE company_id=?
            """, companyId, latest[0], latest[1], now(), companyId);
        persistState(companyId);
        audit(actor, "SUBSCRIPTION_PAYMENT_VOIDED", companyId, paymentId, clean);
        return subscription(companyId);
    }

    @Transactional
    public Subscription cancel(StocklyPrincipal actor, String companyId, String reason) {
        lock(companyId);
        String clean = clean(reason);
        if (clean == null || clean.length() > 150) throw invalid("reason", "Escribe el motivo (hasta 150 caracteres).");
        int changed = jdbc.update("""
            UPDATE company_subscriptions SET cancelled_at=?,cancelled_by=?,cancel_reason=?,updated_at=?,version=version+1
            WHERE company_id=? AND cancelled_at IS NULL
            """, now(), actor.accountId(), clean, now(), companyId);
        if (changed == 0) throw new IdentityFailure(409, "SUBSCRIPTION_ALREADY_CANCELLED", "La suscripción ya estaba cancelada.");
        persistState(companyId);
        audit(actor, "SUBSCRIPTION_CANCELLED", companyId, companyId, clean);
        return subscription(companyId);
    }

    // ---------- Panel ----------

    public record Attention(String companyId, String companyName, String state, String planName, LocalDate date, String reason) {}
    public record Panel(Map<String, Integer> states, BigDecimal monthTotal, int monthPayments, int monthVoided, List<Attention> attention) {}

    @Transactional
    public Panel panel() {
        refreshStates();
        var states = new LinkedHashMap<String, Integer>();
        for (State s : State.values()) states.put(s.name(), 0);
        jdbc.query("SELECT state,COUNT(*) FROM company_subscriptions GROUP BY state", rs -> { states.put(rs.getString(1), rs.getInt(2)); });
        LocalDate today = today(), first = today.withDayOfMonth(1);
        var month = jdbc.queryForObject("""
            SELECT COALESCE(SUM(IF(voided_at IS NULL,amount,0)),0),COALESCE(SUM(voided_at IS NULL),0),COALESCE(SUM(voided_at IS NOT NULL),0)
            FROM subscription_payments WHERE paid_on>=? AND paid_on<?
            """, (rs, n) -> new Object[]{rs.getBigDecimal(1), rs.getInt(2), rs.getInt(3)}, Date.valueOf(first), Date.valueOf(first.plusMonths(1)));
        var settings = settings();
        var attention = new ArrayList<Attention>();
        jdbc.query("""
            SELECT s.company_id,c.commercial_name,p.name,s.trial_ends_on,s.paid_until,s.cancelled_at IS NOT NULL FROM company_subscriptions s
            JOIN companies c ON c.id=s.company_id LEFT JOIN subscription_plans p ON p.id=s.plan_id WHERE s.state IN ('TRIAL','ACTIVE','GRACE','CANCELLED')
            """, rs -> {
            var snap = new Snapshot(rs.getDate(4).toLocalDate(), date(rs.getDate(5)), rs.getBoolean(6));
            State state = SubscriptionRules.state(snap, settings.graceDays(), today);
            String plan = rs.getString(3) == null ? "Prueba" : rs.getString(3);
            if (state == State.GRACE)
                attention.add(new Attention(rs.getString(1), rs.getString(2), state.name(), plan, SubscriptionRules.suspendsOn(snap, settings.graceDays()), "Se suspende si no paga"));
            else if (state != State.CANCELLED && !snap.expiresOn().isAfter(today.plusDays(settings.noticeDays())))
                attention.add(new Attention(rs.getString(1), rs.getString(2), state.name(), plan, snap.expiresOn(),
                    state == State.TRIAL ? "Termina la prueba" : "Vence"));
        });
        jdbc.query("""
            SELECT d.company_id,COALESCE(c.commercial_name,d.receiver_name),d.created_at FROM saas_fiscal_documents d LEFT JOIN companies c ON c.id=d.company_id
            WHERE d.status='REJECTED' AND d.replaced_by IS NULL ORDER BY d.created_at DESC LIMIT 20
            """, rs -> { attention.add(new Attention(rs.getString(1), rs.getString(2), "INVOICE_REJECTED", null,
                LocalDate.ofInstant(rs.getTimestamp(3).toInstant(), SubscriptionRules.COSTA_RICA), "Factura rechazada por Hacienda")); });
        attention.sort(Comparator.comparing(Attention::date));
        return new Panel(states, (BigDecimal) month[0], (Integer) month[1], (Integer) month[2], attention);
    }

    // ---------- Vista del comercio ----------

    public record Usage(int products, int users, int branches) {}
    public record Mine(String state, String planName, String cycle, BigDecimal price, LocalDate trialEndsOn, LocalDate paidUntil,
                       LocalDate expiresOn, LocalDate suspendsOn, boolean limited, Usage usage, Usage limits, String paymentInstructions,
                       List<Plan> plans, List<Payment> payments, int noticeDays) {}

    /** «Mi suscripción» y franjas: el administrador ve todo; los demás usuarios solo el estado y los límites. */
    @Transactional(readOnly = true)
    public Mine mine(StocklyPrincipal actor) {
        if (actor == null || actor.companyId() == null) throw new IdentityFailure(403, "FORBIDDEN", "No tienes acceso.");
        String company = actor.companyId();
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM company_subscriptions WHERE company_id=?)", Boolean.class, company)))
            return null;
        var s = subscription(company); var settings = settings();
        boolean admin = "COMPANY_ADMIN".equals(actor.role());
        var plans = plans().stream().filter(Plan::active).map(p -> new Plan(p.id(), p.name(), p.monthlyPrice(), p.annualPrice(), p.description(),
            true, 0, false, 0)).toList();
        var payments = admin ? s.payments().stream().filter(p -> p.voidedAt() == null).toList() : List.<Payment>of();
        return new Mine(s.state(), s.planName(), s.cycle(), s.price(), s.trialEndsOn(), s.paidUntil(), s.expiresOn(), s.suspendsOn(), s.limited(),
            usage(company), new Usage(settings.trialProducts(), settings.trialUsers(), settings.trialBranches()), settings.paymentInstructions(),
            plans, payments, settings.noticeDays());
    }

    Usage usage(String company) {
        return new Usage(count("SELECT COUNT(*) FROM catalog_products WHERE company_id=? AND active=TRUE", company),
            count("SELECT COUNT(*) FROM users WHERE company_id=? AND active=TRUE", company),
            count("SELECT COUNT(*) FROM inventory_branches WHERE company_id=? AND active=TRUE", company));
    }

    // ---------- Estados ----------

    /** Recalcula y guarda el estado de todas las suscripciones; devuelve las que acaban de suspenderse. */
    @Transactional
    public List<String> refreshStates() {
        var settings = settings(); LocalDate today = today();
        var suspended = new ArrayList<String>();
        jdbc.query("SELECT company_id,state,trial_ends_on,paid_until,cancelled_at IS NOT NULL FROM company_subscriptions", rs -> {
            var snap = new Snapshot(rs.getDate(3).toLocalDate(), date(rs.getDate(4)), rs.getBoolean(5));
            String state = SubscriptionRules.state(snap, settings.graceDays(), today).name();
            if (state.equals(rs.getString(2))) return;
            jdbc.update("UPDATE company_subscriptions SET state=?,updated_at=? WHERE company_id=?", state, now(), rs.getString(1));
            if (State.SUSPENDED.name().equals(state)) suspended.add(rs.getString(1));
        });
        for (String company : suspended) {
            // Cierra las sesiones abiertas como la suspensión de STK-004; el acceso ya se bloquea por fechas.
            jdbc.update("UPDATE companies SET access_version=access_version+1 WHERE id=?", company);
            audit(null, "SUBSCRIPTION_SUSPENDED", company, company, "Falta de pago");
        }
        return suspended;
    }

    private void persistState(String companyId) {
        var settings = settings();
        var snap = lockedSnapshot(companyId);
        String state = SubscriptionRules.state(snap, settings.graceDays(), today()).name();
        String before = jdbc.queryForObject("SELECT state FROM company_subscriptions WHERE company_id=?", String.class, companyId);
        jdbc.update("UPDATE company_subscriptions SET state=? WHERE company_id=?", state, companyId);
        if (State.SUSPENDED.name().equals(state) && !state.equals(before))
            jdbc.update("UPDATE companies SET access_version=access_version+1 WHERE id=?", companyId);
    }

    // ---------- Apoyo ----------

    private Snapshot lock(String companyId) {
        var rows = jdbc.query("SELECT trial_ends_on,paid_until,cancelled_at IS NOT NULL FROM company_subscriptions WHERE company_id=? FOR UPDATE",
            (rs, n) -> new Snapshot(rs.getDate(1).toLocalDate(), date(rs.getDate(2)), rs.getBoolean(3)), companyId);
        if (rows.isEmpty()) throw new IdentityFailure(404, "COMPANY_NOT_FOUND", "No se encontró la empresa.");
        return rows.getFirst();
    }

    private Snapshot lockedSnapshot(String companyId) {
        return jdbc.queryForObject("SELECT trial_ends_on,paid_until,cancelled_at IS NOT NULL FROM company_subscriptions WHERE company_id=?",
            (rs, n) -> new Snapshot(rs.getDate(1).toLocalDate(), date(rs.getDate(2)), rs.getBoolean(3)), companyId);
    }

    void audit(StocklyPrincipal actor, String action, String company, String target, String detail) {
        jdbc.update("""
            INSERT INTO saas_audit_events(id,actor_id,actor_name,action_key,company_id,target_id,detail,occurred_at) VALUES (?,?,?,?,?,?,?,UTC_TIMESTAMP(6))
            """, UUID.randomUUID().toString(), actor == null ? null : actor.accountId(), actor == null ? "Sistema" : actorName(actor), action, company,
            target, detail == null ? null : detail.length() > 300 ? detail.substring(0, 300) : detail);
    }

    private String actorName(StocklyPrincipal actor) {
        return jdbc.queryForList("SELECT full_name FROM users WHERE id=?", String.class, actor.accountId()).stream().findFirst().orElse("Administrador de Stockly");
    }

    private int count(String sql, String company) { Integer n = jdbc.queryForObject(sql, Integer.class, company); return n == null ? 0 : n; }

    private static BigDecimal money(BigDecimal value, String field, boolean required, Map<String, String> errors) {
        if (value == null) { if (required) errors.put(field, "Escribe el precio."); return null; }
        if (value.signum() <= 0 || value.stripTrailingZeros().scale() > 2 || value.compareTo(new BigDecimal("9999999999999")) > 0) {
            errors.put(field, "Escribe un precio positivo de hasta dos decimales."); return null;
        }
        return value.setScale(2);
    }
    private static void range(Map<String, String> errors, String field, int value, int min, int max, String message) {
        if (value < min || value > max) errors.put(field, message);
    }
    static String clean(String value) { if (value == null) return null; String c = value.strip(); return c.isEmpty() ? null : c; }
    private static IdentityFailure invalid(String field, String message) {
        return new IdentityFailure(400, "SUBSCRIPTION_INVALID", message, Map.of(field, message));
    }
    private static IdentityFailure stale() { return new IdentityFailure(409, "STALE_VERSION", "Otra persona cambió estos datos. Recarga e inténtalo de nuevo."); }
    private static Timestamp now() { return Timestamp.from(Instant.now()); }
    private static LocalDate date(Date value) { return value == null ? null : value.toLocalDate(); }
    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
}
