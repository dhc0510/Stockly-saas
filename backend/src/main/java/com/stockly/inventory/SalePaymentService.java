package com.stockly.inventory;

import com.stockly.identity.IdentityFailure;
import com.stockly.identity.StocklyPrincipal;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** STK-012: registra cobros y devoluciones de ventas. Stockly no procesa pagos ni mueve dinero. */
@Service
public class SalePaymentService {
    static final List<String> METHODS = List.of("CASH", "CARD", "SINPE", "TRANSFER");
    private static final Map<String, String> LABELS = Map.of(
        "CASH", "Efectivo", "CARD", "Tarjeta", "SINPE", "SINPE Móvil", "TRANSFER", "Transferencia");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("999999999999.99");
    private static final int MAX_PAYMENTS = 20;
    private static final ZoneId COSTA_RICA = ZoneId.of("America/Costa_Rica");
    private static final String INVALID = "SALE_PAYMENT_INVALID";
    private final JdbcTemplate jdbc;

    SalePaymentService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    // ---------- Medios de pago ----------

    @Transactional(readOnly = true)
    public List<MethodState> methods(StocklyPrincipal actor) {
        var active = active(company(actor));
        return METHODS.stream().map(method -> new MethodState(method, LABELS.get(method), active.contains(method))).toList();
    }

    @Transactional
    public List<MethodState> updateMethods(StocklyPrincipal actor, Map<String, Boolean> input) {
        String company = company(actor);
        if (!InventoryScope.administrator(actor))
            throw new IdentityFailure(403, "FORBIDDEN", "Solo el administrador de la empresa configura los medios de pago.");
        if (input == null || !input.keySet().equals(new HashSet<>(METHODS)) || input.values().stream().anyMatch(Objects::isNull))
            throw invalid("Indica el estado de los cuatro medios de pago.");
        if (input.values().stream().noneMatch(Boolean::booleanValue))
            throw invalid("Debe quedar al menos un medio de pago activo.");
        jdbc.queryForList("SELECT id FROM companies WHERE id=? FOR UPDATE", String.class, company);
        for (String method : METHODS)
            jdbc.update("""
                INSERT INTO company_payment_methods(company_id,method,active,updated_at) VALUES (?,?,?,UTC_TIMESTAMP(6))
                ON DUPLICATE KEY UPDATE active=VALUES(active),updated_at=VALUES(updated_at)
                """, company, method, input.get(method));
        jdbc.update("""
            INSERT INTO identity_audit_events(id,company_id,actor_id,action_key,target_type,target_id,occurred_at)
            VALUES (?,?,?,'PAYMENT_METHODS_UPDATED','PAYMENT_METHODS',?,UTC_TIMESTAMP(6))
            """, UUID.randomUUID().toString(), company, actor.accountId(), company);
        return methods(actor);
    }

    Set<String> active(String company) {
        var configured = new HashMap<String, Boolean>();
        jdbc.query("SELECT method,active FROM company_payment_methods WHERE company_id=?",
            rs -> { configured.put(rs.getString(1), rs.getBoolean(2)); }, company);
        var result = new LinkedHashSet<String>();
        for (String method : METHODS) if (configured.getOrDefault(method, true)) result.add(method);
        return result;
    }

    // ---------- Cobro al confirmar ----------

    /** Valida la forma de los pagos y devuelve una huella estable para el reintento. */
    static List<PaymentInput> normalize(List<PaymentInput> payments) {
        if (payments == null || payments.isEmpty()) return List.of();
        if (payments.size() > MAX_PAYMENTS) throw invalid("Registra como máximo " + MAX_PAYMENTS + " pagos por venta.");
        var result = new ArrayList<PaymentInput>();
        for (int i = 0; i < payments.size(); i++) {
            PaymentInput payment = payments.get(i);
            if (payment == null || payment.method() == null || !METHODS.contains(payment.method()))
                throw invalid("Elige un medio de pago válido.", "payments." + i);
            BigDecimal amount = money(payment.amount(), "payments." + i);
            String reference = "CASH".equals(payment.method()) ? null : reference(payment.reference(), "payments." + i);
            result.add(new PaymentInput(payment.method(), amount, reference));
        }
        return result;
    }

    static String fingerprint(List<PaymentInput> payments) {
        var text = new StringBuilder();
        for (PaymentInput payment : payments)
            text.append(payment.method()).append('|').append(payment.amount().toPlainString()).append('|')
                .append(Objects.toString(payment.reference(), "")).append(';');
        return text.toString();
    }

    /** Comprueba medios activos y que el cobro cubra el total. Devuelve los montos aplicados a guardar. */
    List<Payment> apply(String company, BigDecimal total, List<PaymentInput> payments) {
        if (payments.isEmpty()) throw invalid("Registra el cobro de la venta antes de confirmar.");
        var active = active(company);
        var inactive = new LinkedHashMap<String, String>();
        for (int i = 0; i < payments.size(); i++)
            if (!active.contains(payments.get(i).method()))
                inactive.put("payments." + i, LABELS.get(payments.get(i).method()) + " ya no está activo. Quita ese pago y elige otro medio.");
        if (!inactive.isEmpty())
            throw new IdentityFailure(409, "SALE_PAYMENT_METHOD_INACTIVE", "Un medio de pago fue desactivado. No se registró la venta.", inactive);

        BigDecimal cash = BigDecimal.ZERO, other = BigDecimal.ZERO;
        for (PaymentInput payment : payments)
            if ("CASH".equals(payment.method())) cash = cash.add(payment.amount()); else other = other.add(payment.amount());
        if (other.compareTo(total) > 0)
            throw invalid("Tarjeta, SINPE Móvil y transferencia no pueden superar el total de la venta. Solo el efectivo da vuelto.");
        if (cash.add(other).compareTo(total) < 0)
            throw invalid("El cobro no cubre el total de la venta. Registra el pendiente.");

        // El vuelto se descuenta del efectivo, empezando por el último pago en efectivo.
        BigDecimal change = cash.add(other).subtract(total);
        var result = new Payment[payments.size()];
        for (int i = payments.size() - 1; i >= 0; i--) {
            PaymentInput payment = payments.get(i);
            if (!"CASH".equals(payment.method())) {
                result[i] = new Payment(payment.method(), payment.amount(), null, payment.reference());
                continue;
            }
            BigDecimal discount = change.min(payment.amount());
            change = change.subtract(discount);
            result[i] = new Payment("CASH", payment.amount().subtract(discount), payment.amount(), null);
        }
        return List.of(result);
    }

    void record(String company, String saleId, List<Payment> payments) {
        for (int i = 0; i < payments.size(); i++) {
            Payment payment = payments.get(i);
            jdbc.update("""
                INSERT INTO sale_payments(company_id,sale_id,position,method,amount,received,reference) VALUES (?,?,?,?,?,?,?)
                """, company, saleId, i, payment.method(), payment.amount(), payment.received(), payment.reference());
        }
    }

    // ---------- Devolución al anular ----------

    void refund(String company, String saleId, String voidId, List<RefundInput> input) {
        var payments = payments(company, saleId);
        var refunds = input == null ? List.<RefundInput>of() : input;
        if (payments.isEmpty()) {
            if (!refunds.isEmpty()) throw invalid("La venta no tiene cobro registrado; no hay dinero que devolver.");
            return;
        }
        if (refunds.isEmpty()) throw invalid("Registra cómo se devolvió el dinero.");
        if (refunds.size() > MAX_PAYMENTS) throw invalid("Registra como máximo " + MAX_PAYMENTS + " medios de devolución.");
        var allowed = new HashSet<>(active(company));
        payments.forEach(payment -> allowed.add(payment.method()));
        BigDecimal paid = payments.stream().map(Payment::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sum = BigDecimal.ZERO;
        var clean = new ArrayList<Refund>();
        for (int i = 0; i < refunds.size(); i++) {
            RefundInput refund = refunds.get(i);
            if (refund == null || refund.method() == null || !allowed.contains(refund.method()))
                throw invalid("Elige un medio de devolución válido.", "refunds." + i);
            BigDecimal amount = money(refund.amount(), "refunds." + i);
            String reference = "CASH".equals(refund.method()) ? null : reference(refund.reference(), "refunds." + i);
            sum = sum.add(amount);
            clean.add(new Refund(refund.method(), amount, reference));
        }
        if (sum.compareTo(paid) != 0)
            throw invalid("La devolución debe sumar exactamente lo cobrado (₡" + paid.toPlainString() + ").");
        for (int i = 0; i < clean.size(); i++) {
            Refund refund = clean.get(i);
            jdbc.update("""
                INSERT INTO sale_refunds(company_id,sale_id,void_id,position,method,amount,reference) VALUES (?,?,?,?,?,?,?)
                """, company, saleId, voidId, i, refund.method(), refund.amount(), refund.reference());
        }
    }

    // ---------- Consultas ----------

    List<Payment> payments(String company, String saleId) {
        return jdbc.query("""
            SELECT method,amount,received,reference FROM sale_payments WHERE company_id=? AND sale_id=? ORDER BY position
            """, (rs, row) -> new Payment(rs.getString(1), rs.getBigDecimal(2), rs.getBigDecimal(3), rs.getString(4)), company, saleId);
    }

    List<Refund> refunds(String company, String saleId) {
        return jdbc.query("""
            SELECT method,amount,reference FROM sale_refunds WHERE company_id=? AND sale_id=? ORDER BY position
            """, (rs, row) -> new Refund(rs.getString(1), rs.getBigDecimal(2), rs.getString(3)), company, saleId);
    }

    @Transactional(readOnly = true)
    public Summary summary(StocklyPrincipal actor, String branchId, String from, String to) {
        String company = company(actor);
        LocalDate today = LocalDate.now(COSTA_RICA);
        LocalDate start = day(from, today), end = day(to, today);
        if (start.isAfter(today) || end.isAfter(today)) throw invalid("No se aceptan fechas futuras.");
        if (start.isAfter(end)) throw invalid("La fecha inicial no puede ser posterior a la final.");
        String branch = branchId == null ? "" : branchId.strip();
        if (!branch.isEmpty() && (!branchExists(company, branch) || !InventoryScope.administrator(actor) && !visibleBranch(actor, branch)))
            throw new IdentityFailure(404, "BRANCH_NOT_FOUND", "No se encontró la sucursal.");
        Timestamp lower = Timestamp.from(start.atStartOfDay(COSTA_RICA).toInstant());
        Timestamp upper = Timestamp.from(end.plusDays(1).atStartOfDay(COSTA_RICA).toInstant());

        // Cobros por fecha de venta y devoluciones por fecha de anulación, solo en sucursales visibles.
        String visible = """
            JOIN sales s ON s.company_id=x.company_id AND s.id=x.sale_id
            JOIN inventory_branches b ON b.company_id=s.company_id AND b.id=s.branch_id
            WHERE x.company_id=? AND m.created_at>=? AND m.created_at<? AND (?='' OR s.branch_id=?)
              AND (? OR EXISTS (SELECT 1 FROM inventory_branch_users u
                   WHERE u.company_id=b.company_id AND u.branch_id=b.id AND u.user_id=?))
            """;
        Object[] params = { company, lower, upper, branch, branch, InventoryScope.administrator(actor), actor.accountId() };
        var collected = new ArrayList<Amount>();
        jdbc.query("""
            SELECT m.created_at,x.method,x.amount FROM sale_payments x
            JOIN inventory_movements m ON m.company_id=x.company_id AND m.id=x.sale_id
            """ + visible, rs -> { collected.add(new Amount(rs.getTimestamp(1).toInstant(), rs.getString(2), rs.getBigDecimal(3))); }, params);
        var refunded = new ArrayList<Amount>();
        jdbc.query("""
            SELECT m.created_at,x.method,x.amount FROM sale_refunds x
            JOIN inventory_movements m ON m.company_id=x.company_id AND m.id=x.void_id
            """ + visible, rs -> { refunded.add(new Amount(rs.getTimestamp(1).toInstant(), rs.getString(2), rs.getBigDecimal(3))); }, params);
        Integer sales = jdbc.queryForObject("""
            SELECT COUNT(DISTINCT x.sale_id) FROM sale_payments x
            JOIN inventory_movements m ON m.company_id=x.company_id AND m.id=x.sale_id
            """ + visible, Integer.class, params);
        Integer voids = jdbc.queryForObject("""
            SELECT COUNT(DISTINCT x.sale_id) FROM sale_refunds x
            JOIN inventory_movements m ON m.company_id=x.company_id AND m.id=x.void_id
            """ + visible, Integer.class, params);

        var totals = new LinkedHashMap<String, MethodTotal>();
        for (String method : METHODS) totals.put(method, MethodTotal.of(method, LABELS.get(method), BigDecimal.ZERO, BigDecimal.ZERO));
        var days = new TreeMap<LocalDate, Map<String, BigDecimal>>(Comparator.reverseOrder());
        for (Amount value : collected) {
            totals.computeIfPresent(value.method(), (k, t) -> MethodTotal.of(k, t.label(), t.collected().add(value.amount()), t.refunded()));
            days.computeIfAbsent(value.day(), d -> new LinkedHashMap<>()).merge(value.method(), value.amount(), BigDecimal::add);
        }
        for (Amount value : refunded) {
            totals.computeIfPresent(value.method(), (k, t) -> MethodTotal.of(k, t.label(), t.collected(), t.refunded().add(value.amount())));
            days.computeIfAbsent(value.day(), d -> new LinkedHashMap<>()).merge(value.method(), value.amount().negate(), BigDecimal::add);
        }
        var dayRows = days.entrySet().stream().map(entry -> new DayTotal(entry.getKey(),
            METHODS.stream().map(method -> entry.getValue().getOrDefault(method, BigDecimal.ZERO)).toList(),
            entry.getValue().values().stream().reduce(BigDecimal.ZERO, BigDecimal::add))).toList();
        return new Summary(start, end, List.copyOf(totals.values()), dayRows,
            sales == null ? 0 : sales, voids == null ? 0 : voids);
    }

    private boolean visibleBranch(StocklyPrincipal actor, String branch) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM inventory_branch_users WHERE company_id=? AND branch_id=? AND user_id=?)
            """, Boolean.class, actor.companyId(), branch, actor.accountId()));
    }

    private boolean branchExists(String company, String branch) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM inventory_branches WHERE company_id=? AND id=?)",
            Boolean.class, company, branch));
    }

    // ---------- Validaciones comunes ----------

    private static BigDecimal money(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0 || value.stripTrailingZeros().scale() > 2 || value.compareTo(MAX_AMOUNT) > 0)
            throw invalid("Ingresa un monto positivo de hasta dos decimales.", field);
        return value.setScale(2);
    }

    private static String reference(String value, String field) {
        if (value == null) return null;
        String clean = value.strip();
        if (clean.isEmpty()) return null;
        if (clean.length() > 60) throw invalid("La referencia admite hasta 60 caracteres.", field);
        return clean;
    }

    private static LocalDate day(String value, LocalDate fallback) {
        if (value == null || value.isBlank()) return fallback;
        try { return LocalDate.parse(value.strip()); }
        catch (DateTimeParseException error) { throw invalid("Revisa las fechas del período."); }
    }

    private static IdentityFailure invalid(String message) { return new IdentityFailure(400, INVALID, message); }

    private static IdentityFailure invalid(String message, String field) {
        return new IdentityFailure(400, INVALID, message, Map.of(field, message));
    }

    private static String company(StocklyPrincipal actor) {
        if (actor == null || actor.companyId() == null || actor.mustChangePassword())
            throw new IdentityFailure(403, "FORBIDDEN", "No tienes acceso a cobros.");
        return actor.companyId();
    }

    static String label(String method) { return LABELS.get(method); }

    private record Amount(Instant date, String method, BigDecimal amount) {
        LocalDate day() { return LocalDate.ofInstant(date, COSTA_RICA); }
    }
    public record MethodState(String method, String label, boolean active) {}
    public record PaymentInput(String method, BigDecimal amount, String reference) {}
    public record RefundInput(String method, BigDecimal amount, String reference) {}
    public record Payment(String method, BigDecimal amount, BigDecimal received, String reference) {}
    public record Refund(String method, BigDecimal amount, String reference) {}
    public record MethodTotal(String method, String label, BigDecimal collected, BigDecimal refunded, BigDecimal net) {
        static MethodTotal of(String method, String label, BigDecimal collected, BigDecimal refunded) {
            return new MethodTotal(method, label, collected, refunded, collected.subtract(refunded));
        }
    }
    public record DayTotal(LocalDate day, List<BigDecimal> methods, BigDecimal total) {}
    public record Summary(LocalDate from, LocalDate to, List<MethodTotal> methods, List<DayTotal> days, int sales, int refunds) {}
}
