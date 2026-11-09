package com.stockly.subscriptions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * STK-018 · Reglas puras de la suscripción (días de Costa Rica). La fecha de vencimiento E es «pagado hasta» o, si nunca
 * pagó, el fin de la prueba. Antes de E: Prueba o Activa; de E a E + gracia: Vencida en gracia; después: Suspendida.
 * Cancelada conserva el acceso hasta E y luego queda suspendida sin gracia.
 */
public final class SubscriptionRules {
    public static final ZoneId COSTA_RICA = ZoneId.of("America/Costa_Rica");
    public enum State { TRIAL, ACTIVE, GRACE, SUSPENDED, CANCELLED }

    private SubscriptionRules() {}

    public record Snapshot(LocalDate trialEndsOn, LocalDate paidUntil, boolean cancelled) {
        public LocalDate expiresOn() { return paidUntil != null ? paidUntil : trialEndsOn; }
        /** Los límites aplican mientras la empresa nunca haya pagado. */
        public boolean limited() { return paidUntil == null; }
    }

    public static State state(Snapshot s, int graceDays, LocalDate today) {
        LocalDate expires = s.expiresOn();
        if (s.cancelled()) return today.isBefore(expires) ? State.CANCELLED : State.SUSPENDED;
        if (today.isBefore(expires)) return s.paidUntil() == null ? State.TRIAL : State.ACTIVE;
        if (today.isBefore(expires.plusDays(graceDays))) return State.GRACE;
        return State.SUSPENDED;
    }

    /** Fin de la gracia (día en que se suspende). */
    public static LocalDate suspendsOn(Snapshot s, int graceDays) {
        return s.cancelled() ? s.expiresOn() : s.expiresOn().plusDays(graceDays);
    }

    /**
     * Inicio del período pagado: si la empresa ya había pagado y sigue activa, en gracia o cancelada sin vencer, continúa desde
     * «pagado hasta» (pagar tarde no regala días); si viene de la prueba o de una suspensión, empieza el día del pago.
     */
    public static LocalDate periodStart(Snapshot s, int graceDays, LocalDate today, LocalDate paidOn) {
        State state = state(s, graceDays, today);
        boolean continues = s.paidUntil() != null && (state == State.ACTIVE || state == State.GRACE || state == State.CANCELLED);
        return continues ? s.paidUntil() : paidOn;
    }

    public static LocalDate periodEnd(LocalDate start, String cycle, int periods) {
        return "ANNUAL".equals(cycle) ? start.plusYears(periods) : start.plusMonths(periods);
    }

    public static BigDecimal expectedAmount(BigDecimal price, int periods) {
        return price.multiply(BigDecimal.valueOf(periods)).setScale(2);
    }
}
