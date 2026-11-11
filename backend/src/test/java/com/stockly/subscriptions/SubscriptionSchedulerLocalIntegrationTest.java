package com.stockly.subscriptions;

import com.stockly.identity.EmailDelivery;
import java.sql.Date;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * STK-018 · Avisos de suscripción sobre MySQL real: llegan al correo del administrador una sola vez por vencimiento, y si el
 * envío falla el aviso se libera para reintentarlo. Antes del 2026-11-11 la consulta usaba una columna inexistente y el aviso
 * quedaba registrado sin enviarse.
 */
@SpringBootTest(properties = "stockly.subscriptions.scheduler-enabled=false")
@EnabledIfEnvironmentVariable(named = "STOCKLY_SUBSCRIPTIONS_TEST_DATABASE", matches = "true")
class SubscriptionSchedulerLocalIntegrationTest {
    @Autowired SubscriptionScheduler scheduler;
    @Autowired SubscriptionService subscriptions;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean EmailDelivery email;

    @Test void sendsEachNoticeOnceToTheCompanyAdministrator() {
        String company = id(), admin = id(), mail = admin + "@example.com";
        LocalDate today = subscriptions.today();
        jdbc.update("""
            INSERT INTO companies(id,commercial_name,legal_name,identification_type,identification_normalized,contact_email,status,created_at,updated_at)
            VALUES (?,'Avisos STK-018','Avisos STK-018 S.A.','LEGAL',?,'empresa@example.com','ACTIVE',NOW(),NOW())
            """, company, "3" + String.format("%09d", java.util.concurrent.ThreadLocalRandom.current().nextLong(1_000_000_000L)));
        try {
            jdbc.update("""
                INSERT INTO users(id,company_id,full_name,username_normalized,email_normalized,password_hash,must_change_password,role)
                VALUES (?,?,'Ana Admin',?,?,'unused',FALSE,'COMPANY_ADMIN')
                """, admin, company, admin.substring(0, 30), mail);
            jdbc.update("INSERT INTO company_subscriptions(company_id,state,trial_ends_on,created_at,updated_at) VALUES (?,'TRIAL',?,NOW(),NOW())",
                company, Date.valueOf(today.plusDays(3)));

            // Si el correo falla, el aviso no queda marcado como enviado.
            doThrow(new IllegalStateException("SMTP caído")).when(email).send(eq(mail), anyString(), anyString());
            scheduler.runOnce();
            assertThat(notices(company)).isZero();

            // Al volver el correo se envía una sola vez, al correo del administrador, con el aviso de la prueba.
            reset(email);
            scheduler.runOnce();
            verify(email, times(1)).send(eq(mail), contains("Tu prueba de Stockly termina el"), contains("Hola, Ana Admin"));
            assertThat(notices(company)).isEqualTo(1);
            scheduler.runOnce();
            verify(email, times(1)).send(eq(mail), anyString(), anyString());

            // Al vencer (gracia) y al suspenderse sale un aviso nuevo de cada tipo.
            jdbc.update("UPDATE company_subscriptions SET trial_ends_on=? WHERE company_id=?", Date.valueOf(today.minusDays(1)), company);
            scheduler.runOnce();
            verify(email).send(eq(mail), contains("venció"), anyString());
            int grace = subscriptions.settings().graceDays();
            jdbc.update("UPDATE company_subscriptions SET trial_ends_on=? WHERE company_id=?", Date.valueOf(today.minusDays(grace + 1L)), company);
            scheduler.runOnce();
            verify(email).send(eq(mail), contains("suspendido"), anyString());
            verify(email, times(3)).send(eq(mail), anyString(), anyString());
        } finally {
            jdbc.update("DELETE FROM subscription_notices WHERE company_id=?", company);
            jdbc.update("DELETE FROM saas_audit_events WHERE company_id=?", company);
            jdbc.update("DELETE FROM company_subscriptions WHERE company_id=?", company);
            jdbc.update("DELETE FROM users WHERE company_id=?", company);
            jdbc.update("DELETE FROM companies WHERE id=?", company);
        }
    }

    private int notices(String company) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM subscription_notices WHERE company_id=?", Integer.class, company);
    }

    private static String id() { return UUID.randomUUID().toString(); }
}
