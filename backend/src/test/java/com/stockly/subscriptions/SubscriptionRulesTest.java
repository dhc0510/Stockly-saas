package com.stockly.subscriptions;

import com.stockly.subscriptions.SubscriptionRules.Snapshot;
import com.stockly.subscriptions.SubscriptionRules.State;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** STK-018 · Estados por fechas, inicio y fin del período pagado y monto esperado. */
class SubscriptionRulesTest {
    private static final LocalDate TRIAL_END = LocalDate.of(2026, 11, 23), PAID = LocalDate.of(2026, 11, 5);

    @Test void trialGraceAndSuspension() {
        var trial = new Snapshot(TRIAL_END, null, false);
        assertThat(SubscriptionRules.state(trial, 7, LocalDate.of(2026, 11, 22))).isEqualTo(State.TRIAL);
        assertThat(SubscriptionRules.state(trial, 7, TRIAL_END)).isEqualTo(State.GRACE);
        assertThat(SubscriptionRules.state(trial, 7, TRIAL_END.plusDays(6))).isEqualTo(State.GRACE);
        assertThat(SubscriptionRules.state(trial, 7, TRIAL_END.plusDays(7))).isEqualTo(State.SUSPENDED);
        assertThat(SubscriptionRules.suspendsOn(trial, 7)).isEqualTo(TRIAL_END.plusDays(7));
        assertThat(trial.limited()).isTrue();
        assertThat(SubscriptionRules.state(trial, 0, TRIAL_END)).isEqualTo(State.SUSPENDED);
    }

    @Test void paidActiveThenGraceThenSuspended() {
        var paid = new Snapshot(TRIAL_END, PAID, false);
        assertThat(paid.limited()).isFalse();
        assertThat(SubscriptionRules.state(paid, 7, PAID.minusDays(1))).isEqualTo(State.ACTIVE);
        assertThat(SubscriptionRules.state(paid, 7, PAID)).isEqualTo(State.GRACE);
        assertThat(SubscriptionRules.state(paid, 7, PAID.plusDays(7))).isEqualTo(State.SUSPENDED);
    }

    @Test void cancelledKeepsAccessUntilPaidThenSuspendsWithoutGrace() {
        var cancelled = new Snapshot(TRIAL_END, PAID, true);
        assertThat(SubscriptionRules.state(cancelled, 7, PAID.minusDays(1))).isEqualTo(State.CANCELLED);
        assertThat(SubscriptionRules.state(cancelled, 7, PAID)).isEqualTo(State.SUSPENDED);
        assertThat(SubscriptionRules.suspendsOn(cancelled, 7)).isEqualTo(PAID);
    }

    @Test void periodContinuesWhenActiveOrInGraceAndStartsOnPaymentOtherwise() {
        LocalDate paidOn = LocalDate.of(2026, 11, 9);
        // En gracia: sigue desde «pagado hasta» (pagar tarde no regala días).
        assertThat(SubscriptionRules.periodStart(new Snapshot(TRIAL_END, PAID, false), 7, paidOn, paidOn)).isEqualTo(PAID);
        // Activa: renovación anticipada, también desde «pagado hasta».
        assertThat(SubscriptionRules.periodStart(new Snapshot(TRIAL_END, PAID, false), 7, PAID.minusDays(3), PAID.minusDays(3))).isEqualTo(PAID);
        // Prueba o suspendida: desde el día del pago.
        assertThat(SubscriptionRules.periodStart(new Snapshot(TRIAL_END, null, false), 7, paidOn, paidOn)).isEqualTo(paidOn);
        LocalDate late = PAID.plusDays(20);
        assertThat(SubscriptionRules.periodStart(new Snapshot(TRIAL_END, PAID, false), 7, late, late)).isEqualTo(late);
    }

    @Test void periodEndAndExpectedAmount() {
        assertThat(SubscriptionRules.periodEnd(LocalDate.of(2026, 1, 31), "MONTHLY", 1)).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(SubscriptionRules.periodEnd(LocalDate.of(2026, 11, 5), "MONTHLY", 3)).isEqualTo(LocalDate.of(2027, 2, 5));
        assertThat(SubscriptionRules.periodEnd(LocalDate.of(2026, 11, 5), "ANNUAL", 1)).isEqualTo(LocalDate.of(2027, 11, 5));
        assertThat(SubscriptionRules.expectedAmount(new BigDecimal("15000"), 3)).isEqualByComparingTo("45000.00");
    }
}
