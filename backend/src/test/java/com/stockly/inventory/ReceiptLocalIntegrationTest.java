package com.stockly.inventory;

import com.jayway.jsonpath.JsonPath;
import com.stockly.identity.StocklyPrincipal;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "STOCKLY_RECEIPTS_TEST_DATABASE", matches = "true")
class ReceiptLocalIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    private final List<String> companies = new ArrayList<>();

    @AfterEach void clean() {
        for (String company : companies) {
            jdbc.update("DELETE FROM users WHERE company_id=?", company);
            jdbc.update("DELETE FROM company_roles WHERE company_id=?", company);
            jdbc.update("DELETE FROM companies WHERE id=?", company);
        }
    }

    @Test void concurrentRetriesRegisterOneMovementAndKeepHistoricalBalance() throws Exception {
        var admin = account();
        String unit = article(admin, "unidad", "7440001");
        String kg = article(admin, "kg", "7440002");
        String key = UUID.randomUUID().toString();
        String payload = body(key, unit, "5", kg, "1.25");
        var gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<String> call = () -> { gate.await(); return confirm(admin, payload); };
            var first = pool.submit(call);
            var second = pool.submit(call);
            gate.countDown();
            assertThat((String) JsonPath.read(first.get(), "$.movement.id"))
                .isEqualTo((String) JsonPath.read(second.get(), "$.movement.id"));
        }
        postReceipt(admin, body(key, unit, "6", kg, "1.25")).andExpect(status().isConflict());
        assertThat(balance(admin, unit)).isEqualByComparingTo("5");
        assertThat(balance(admin, kg)).isEqualByComparingTo("1.250");
        assertThat(jdbc.queryForObject("SELECT has_movements FROM catalog_items WHERE company_id=? AND id=?",
            Boolean.class, company(admin), unit)).isTrue();

        String next = confirm(admin, body(UUID.randomUUID().toString(), unit, "2", kg, "1"));
        assertThat((String) JsonPath.read(next, "$.movement.folio")).isEqualTo("ENT-0002");
        mvc.perform(get("/api/inventory/receipts").param("search","guia-8").with(authentication(admin)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.entries.length()").value(2));
        mvc.perform(get("/api/inventory/receipts/items").param("search","7440002").param("barcode","true").with(authentication(admin)))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].unit").value("kg"));
        String original = confirm(admin, payload);
        assertThat((String) JsonPath.read(original, "$.movement.folio")).isEqualTo("ENT-0001");
        List<Number> historical = JsonPath.read(original, "$.lines[*].balanceAfter");
        assertThat(historical.stream().map(Number::doubleValue).toList()).containsExactlyInAnyOrder(5.0, 1.25);
    }

    @Test void voidIsLinkedOnceAndInsufficientBalanceLeavesEveryLineIntact() throws Exception {
        var admin = account();
        String unit = article(admin, "unidad", "7440101");
        String kg = article(admin, "kg", "7440102");
        String original = confirm(admin, body(UUID.randomUUID().toString(), unit, "5", kg, "2.5"));
        String id = JsonPath.read(original, "$.movement.id");
        jdbc.update("UPDATE inventory_balances SET quantity=1 WHERE company_id=? AND item_id=?", company(admin), kg);
        reverse(admin, id).andExpect(status().isConflict());
        assertThat(balance(admin, unit)).isEqualByComparingTo("5");
        assertThat(balance(admin, kg)).isEqualByComparingTo("1");
        jdbc.update("UPDATE inventory_balances SET quantity=2.5 WHERE company_id=? AND item_id=?", company(admin), kg);
        String result;
        var gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<org.springframework.mock.web.MockHttpServletResponse> call = () -> {
                gate.await();
                return reverse(admin, id).andReturn().getResponse();
            };
            var first = pool.submit(call);
            var second = pool.submit(call);
            gate.countDown();
            var a = first.get();
            var b = second.get();
            assertThat(List.of(a.getStatus(), b.getStatus())).containsExactlyInAnyOrder(200, 409);
            result = (a.getStatus() == 200 ? a : b).getContentAsString();
        }
        assertThat((String) JsonPath.read(result, "$.movement.voidFolio")).isEqualTo("ANU-0001");
        String reversal = JsonPath.read(result, "$.movement.voidId");
        mvc.perform(get("/api/inventory/receipts/{id}", reversal).with(authentication(admin)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.movement.originalId").value(id));
        reverse(admin, id).andExpect(status().isConflict());
        assertThat(balance(admin, unit)).isEqualByComparingTo("0");
        assertThat(balance(admin, kg)).isEqualByComparingTo("0");
    }

    @Test void receiptAndAuditTimesMatchUtcWhileSessionPolicyKeepsItsMinutes() throws Exception {
        var admin = account();
        String first = article(admin, "unidad", "7440301"), second = article(admin, "kg", "7440302");
        Instant before = Instant.now().minusSeconds(2);
        String response = confirm(admin, body(UUID.randomUUID().toString(), first, "1", second, "2"));
        Instant movement = Instant.parse(JsonPath.read(response, "$.movement.date"));
        assertThat(movement).isBetween(before, Instant.now().plusSeconds(2));
        assertThat(jdbc.queryForObject("SELECT @@session.time_zone", String.class)).isEqualTo("+00:00");

        mvc.perform(put("/api/company/security/session-policy").with(authentication(admin)).with(csrf())
            .contentType("application/json").content("{\"idleTimeoutMinutes\":90}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.idleTimeoutMinutes").value(90));
        mvc.perform(get("/api/company/security/session-policy").with(authentication(admin)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.idleTimeoutMinutes").value(90));
        String audit = mvc.perform(get("/api/audit").with(authentication(admin)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Instant event = Instant.parse(JsonPath.read(audit, "$.items[0].occurredAt"));
        assertThat(event).isBetween(before, Instant.now().plusSeconds(2));
    }

    @Test void invalidLinesAndForeignArticlesLeaveNoChanges() throws Exception {
        var admin = account();
        var other = account();
        String unit = article(admin, "unidad", "7440201");
        String kg = article(admin, "kg", "7440202");
        String foreign = article(other, "unidad", "7440203");
        var reader = new UsernamePasswordAuthenticationToken(admin.getPrincipal(), null,
            List.of(new SimpleGrantedAuthority("MODULE_INVENTORY_RECEIPTS_READ")));
        var invalid = body(UUID.randomUUID().toString(), unit, "1.25", kg, "2");
        postReceipt(admin, invalid).andExpect(status().isBadRequest());
        postReceipt(admin, body(UUID.randomUUID().toString(), unit, "1", foreign, "2")).andExpect(status().isBadRequest());
        postReceipt(admin, body(UUID.randomUUID().toString(), unit, "1000001", kg, "2")).andExpect(status().isBadRequest());
        postReceipt(reader, body(UUID.randomUUID().toString(), unit, "1", kg, "2")).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_movements WHERE company_id=?", Integer.class, company(admin))).isZero();
        String id = JsonPath.read(confirm(admin, body(UUID.randomUUID().toString(), unit, "1", kg, "2")), "$.movement.id");
        mvc.perform(get("/api/inventory/receipts/{id}", id).with(authentication(other))).andExpect(status().isNotFound());
        reverse(reader, id).andExpect(status().isForbidden());
    }

    private String confirm(UsernamePasswordAuthenticationToken actor, String payload) throws Exception {
        return postReceipt(actor, payload).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private org.springframework.test.web.servlet.ResultActions postReceipt(UsernamePasswordAuthenticationToken actor, String payload) throws Exception {
        return mvc.perform(post("/api/inventory/receipts").with(authentication(actor)).with(csrf())
            .contentType("application/json").content(payload));
    }

    private org.springframework.test.web.servlet.ResultActions reverse(UsernamePasswordAuthenticationToken actor, String id) throws Exception {
        return mvc.perform(post("/api/inventory/receipts/{id}/void", id).with(authentication(actor)).with(csrf())
            .contentType("application/json").content("{\"reason\":\"Recepción duplicada\"}"));
    }

    private static String body(String key, String first, String firstQuantity, String second, String secondQuantity) {
        return """
            {"requestKey":"%s","reason":"RECEIPT","reference":"GUÍA-8","lines":[{"itemId":"%s","quantity":%s},{"itemId":"%s","quantity":%s}]}
            """.formatted(key, first, firstQuantity, second, secondQuantity);
    }

    private BigDecimal balance(UsernamePasswordAuthenticationToken actor, String item) {
        return jdbc.queryForObject("SELECT quantity FROM inventory_balances WHERE company_id=? AND item_id=?",
            BigDecimal.class, company(actor), item);
    }

    private String article(UsernamePasswordAuthenticationToken actor, String unit, String barcode) {
        String product = UUID.randomUUID().toString(), id = UUID.randomUUID().toString(), company = company(actor);
        jdbc.update("INSERT INTO catalog_products(company_id,id,name) VALUES (?,?,?)", company, product, "Prueba " + unit);
        jdbc.update("INSERT INTO catalog_items(company_id,id,product_id,sku,unit,barcode) VALUES (?,?,?,?,?,?)",
            company, id, product, barcode, unit, barcode);
        return id;
    }

    private static String company(UsernamePasswordAuthenticationToken actor) {
        return ((StocklyPrincipal) actor.getPrincipal()).companyId();
    }

    private UsernamePasswordAuthenticationToken account() {
        String company = UUID.randomUUID().toString(), account = UUID.randomUUID().toString();
        companies.add(company);
        jdbc.update("""
            INSERT INTO companies(id,commercial_name,legal_name,identification_type,identification_normalized,
              contact_email,status,created_at,updated_at)
            VALUES (?, 'Prueba STK-008', 'Prueba STK-008', 'LEGAL', ?, 'test@example.com', 'ACTIVE', NOW(), NOW())
            """, company, company.substring(0,12));
        jdbc.update("""
            INSERT INTO users(id,company_id,full_name,username_normalized,email_normalized,password_hash,must_change_password,role)
            VALUES (?,?,'Prueba',?,?,'unused',FALSE,'COMPANY_ADMIN')
            """, account, company, account.substring(0,30), account+"@example.com");
        return new UsernamePasswordAuthenticationToken(new StocklyPrincipal(account, company, 0, 0, "COMPANY_ADMIN", false),
            null, List.of(new SimpleGrantedAuthority("ROLE_COMPANY_ADMIN")));
    }
}
