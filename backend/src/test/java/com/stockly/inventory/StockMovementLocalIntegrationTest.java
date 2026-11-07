package com.stockly.inventory;

import com.jayway.jsonpath.JsonPath;
import com.stockly.identity.StocklyPrincipal;
import java.math.BigDecimal;
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
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "STOCKLY_MOVEMENTS_TEST_DATABASE", matches = "true")
class StockMovementLocalIntegrationTest {
    private static final String BASE = "/api/inventory/movements";
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

    @Test void outflowIsAtomicAndValidatesReasonUnitAndBalance() throws Exception {
        var admin = account();
        String unit = stocked(admin, "unidad", "7450001", "10");
        String kg = stocked(admin, "kg", "7450002", "5");

        for (String reason : List.of("DAMAGE", "EXPIRED", "THEFT", "INTERNAL")) {
            outflow(admin, out(key(), reason, "", unit, "1", kg, "0.25")).andExpect(status().isOk());
        }
        assertThat(balance(admin, unit)).isEqualByComparingTo("6");
        assertThat(balance(admin, kg)).isEqualByComparingTo("4");

        outflow(admin, out(key(), "", "", unit, "1", kg, "1")).andExpect(status().isBadRequest());
        outflow(admin, out(key(), "OTHER", "", unit, "1", kg, "1")).andExpect(status().isBadRequest());
        outflow(admin, out(key(), "OTHER", "Muestra", unit, "1.25", kg, "1")).andExpect(status().isBadRequest());
        outflow(admin, out(key(), "DAMAGE", "", unit, "0", kg, "1")).andExpect(status().isBadRequest());
        outflow(admin, out(key(), "DAMAGE", "", unit, "1000001", kg, "1")).andExpect(status().isBadRequest());

        outflow(admin, out(key(), "DAMAGE", "", unit, "1", kg, "99"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("INSUFFICIENT_BALANCE"))
            .andExpect(jsonPath("$.fieldErrors['" + kg + "']").value("4.000"));
        assertThat(balance(admin, unit)).isEqualByComparingTo("6");
        assertThat(balance(admin, kg)).isEqualByComparingTo("4");

        String document = confirm(outflow(admin, out(key(), "OTHER", "Muestra", unit, "6", kg, "1.25")));
        assertThat((String) JsonPath.read(document, "$.movement.folio")).isEqualTo("SAL-0005");
        assertThat((String) JsonPath.read(document, "$.movement.kind")).isEqualTo("OUT");
        assertThat((String) JsonPath.read(document, "$.movement.reason")).isEqualTo("Otro · Muestra");
        assertThat(balance(admin, unit)).isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject("SELECT has_movements FROM catalog_items WHERE company_id=? AND id=?",
            Boolean.class, company(admin), unit)).isTrue();
    }

    @Test void concurrentRetriesRegisterOneOutflowAndDifferentDataIsRejected() throws Exception {
        var admin = account();
        String unit = stocked(admin, "unidad", "7450101", "10");
        String kg = stocked(admin, "kg", "7450102", "5");
        String key = key();
        String payload = out(key, "DAMAGE", "", unit, "2", kg, "1.5");
        var gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<String> call = () -> { gate.await(); return confirm(outflow(admin, payload)); };
            var first = pool.submit(call);
            var second = pool.submit(call);
            gate.countDown();
            assertThat((String) JsonPath.read(first.get(), "$.movement.id"))
                .isEqualTo((String) JsonPath.read(second.get(), "$.movement.id"));
        }
        outflow(admin, out(key, "DAMAGE", "", unit, "3", kg, "1.5")).andExpect(status().isConflict());
        assertThat(balance(admin, unit)).isEqualByComparingTo("8");
        assertThat(balance(admin, kg)).isEqualByComparingTo("3.5");
    }

    @Test void concurrentOutflowsNeverGoNegative() throws Exception {
        var admin = account();
        String unit = stocked(admin, "unidad", "7450151", "3");
        String kg = stocked(admin, "kg", "7450152", "3");
        var gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Integer> call = () -> {
                gate.await();
                return outflow(admin, out(key(), "THEFT", "", unit, "2", kg, "1")).andReturn().getResponse().getStatus();
            };
            var first = pool.submit(call);
            var second = pool.submit(call);
            gate.countDown();
            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(200, 409);
        }
        assertThat(balance(admin, unit)).isEqualByComparingTo("1");
        assertThat(balance(admin, kg)).isEqualByComparingTo("2");
    }

    @Test void adjustmentLeavesCountedBalanceAndShowsDifference() throws Exception {
        var admin = account();
        String up = stocked(admin, "unidad", "7450201", "10");
        String down = stocked(admin, "kg", "7450202", "5");
        String zero = stocked(admin, "unidad", "7450203", "4");
        String same = stocked(admin, "unidad", "7450204", "7");
        String never = article(admin, "unidad", "7450205");

        String payload = adjust(key(), "COUNT", "", line(up, "12", "10"), line(down, "4.5", "5"),
            line(zero, "0", "4"), line(same, "7", "7"), line(never, "2", "0"));
        String document = confirm(adjustment(admin, payload));
        assertThat((String) JsonPath.read(document, "$.movement.folio")).isEqualTo("AJU-0001");
        assertThat((String) JsonPath.read(document, "$.movement.reason")).isEqualTo("Conteo físico");
        assertThat(balance(admin, up)).isEqualByComparingTo("12");
        assertThat(balance(admin, down)).isEqualByComparingTo("4.5");
        assertThat(balance(admin, zero)).isEqualByComparingTo("0");
        assertThat(balance(admin, same)).isEqualByComparingTo("7");
        assertThat(balance(admin, never)).isEqualByComparingTo("2");
        assertThat(lineValue(document, up, "quantity")).isEqualTo(2.0);
        assertThat(lineValue(document, down, "quantity")).isEqualTo(-0.5);
        assertThat(lineValue(document, same, "quantity")).isEqualTo(0.0);
        assertThat(lineValue(document, same, "balanceAfter")).isEqualTo(7.0);
        assertThat(jdbc.queryForObject("SELECT has_movements FROM catalog_items WHERE company_id=? AND id=?",
            Boolean.class, company(admin), same)).isTrue();

        adjustment(admin, adjust(key(), "", "", line(up, "1", "12"))).andExpect(status().isBadRequest());
        adjustment(admin, adjust(key(), "OTHER", "", line(up, "1", "12"))).andExpect(status().isBadRequest());
        adjustment(admin, adjust(key(), "CORRECTION", "", line(up, "1.5", "12"))).andExpect(status().isBadRequest());
        adjustment(admin, adjust(key(), "CORRECTION", "", line(up, "-1", "12"))).andExpect(status().isBadRequest());
        assertThat(balance(admin, up)).isEqualByComparingTo("12");
    }

    @Test void changedBalanceAppliesNothingAndCanBeConfirmedAgain() throws Exception {
        var admin = account();
        String first = stocked(admin, "unidad", "7450301", "10");
        String second = stocked(admin, "kg", "7450302", "5");
        String payload = adjust(key(), "COUNT", "", line(first, "9", "10"), line(second, "6", "5"));

        outflow(admin, out(key(), "DAMAGE", "", first, "2", second, "0")).andExpect(status().isBadRequest());
        String taken = out(key(), "DAMAGE", "", first, "2", second, "0.5");
        confirm(outflow(admin, taken));
        adjustment(admin, payload)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("BALANCE_CHANGED"))
            .andExpect(jsonPath("$.fieldErrors['" + first + "']").value("8.000"))
            .andExpect(jsonPath("$.fieldErrors['" + second + "']").value("4.500"));
        assertThat(balance(admin, first)).isEqualByComparingTo("8");
        assertThat(balance(admin, second)).isEqualByComparingTo("4.5");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_movements WHERE company_id=? AND kind='ADJUST'",
            Integer.class, company(admin))).isZero();

        String key = key();
        String retried = adjust(key, "COUNT", "", line(first, "9", "8"), line(second, "6", "4.5"));
        String document = confirm(adjustment(admin, retried));
        assertThat(lineValue(document, first, "quantity")).isEqualTo(1.0);
        assertThat(balance(admin, first)).isEqualByComparingTo("9");

        // Un reintento de lo ya registrado devuelve el comprobante aunque el saldo haya cambiado después.
        confirm(outflow(admin, out(key(), "THEFT", "", first, "1", second, "1")));
        String again = confirm(adjustment(admin, retried));
        assertThat((String) JsonPath.read(again, "$.movement.id")).isEqualTo((String) JsonPath.read(document, "$.movement.id"));
        assertThat(balance(admin, first)).isEqualByComparingTo("8");
    }

    @Test void voidRevertsOnceSharesSeriesAndNegativeLeavesEverythingIntact() throws Exception {
        var admin = account();
        String unit = stocked(admin, "unidad", "7450401", "10");
        String kg = stocked(admin, "kg", "7450402", "5");
        String receipt = confirm(mvc.perform(post("/api/inventory/receipts").with(authentication(admin)).with(csrf())
            .contentType("application/json").content("""
            {"requestKey":"%s","reason":"RECEIPT","lines":[{"itemId":"%s","quantity":1}]}
            """.formatted(key(), unit))));
        String receiptVoid = confirm(mvc.perform(post("/api/inventory/receipts/{id}/void", (String) JsonPath.read(receipt, "$.movement.id"))
            .with(authentication(admin)).with(csrf()).contentType("application/json").content("{\"reason\":\"Duplicada\"}")));
        assertThat((String) JsonPath.read(receiptVoid, "$.movement.voidFolio")).isEqualTo("ANU-0001");

        String out = confirm(outflow(admin, out(key(), "DAMAGE", "", unit, "3", kg, "1")));
        String outId = JsonPath.read(out, "$.movement.id");
        reverse(admin, outId, " ").andExpect(status().isBadRequest());
        String voided = confirm(reverse(admin, outId, "Se encontró en bodega"));
        assertThat((String) JsonPath.read(voided, "$.movement.voidFolio")).isEqualTo("ANU-0002");
        assertThat(balance(admin, unit)).isEqualByComparingTo("10");
        assertThat(balance(admin, kg)).isEqualByComparingTo("5");
        reverse(admin, outId, "Otra vez").andExpect(status().isConflict());
        String reversal = JsonPath.read(voided, "$.movement.voidId");
        mvc.perform(get(BASE + "/{id}", reversal).with(authentication(admin)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.movement.originalId").value(outId));
        reverse(admin, reversal, "No aplica").andExpect(status().isBadRequest());

        String adjusted = confirm(adjustment(admin, adjust(key(), "COUNT", "", line(unit, "15", "10"), line(kg, "4", "5"))));
        String adjustedId = JsonPath.read(adjusted, "$.movement.id");
        confirm(outflow(admin, out(key(), "THEFT", "", unit, "12", kg, "1")));
        reverse(admin, adjustedId, "Conteo equivocado").andExpect(status().isConflict());
        assertThat(balance(admin, unit)).isEqualByComparingTo("3");
        assertThat(balance(admin, kg)).isEqualByComparingTo("3");
        mvc.perform(get(BASE + "/{id}", adjustedId).with(authentication(admin)))
            .andExpect(jsonPath("$.movement.voidId").doesNotExist());

        confirm(adjustment(admin, adjust(key(), "CORRECTION", "", line(unit, "20", "3"), line(kg, "3", "3"))));
        confirm(reverse(admin, adjustedId, "Conteo equivocado"));
        assertThat(balance(admin, unit)).isEqualByComparingTo("15");
        assertThat(balance(admin, kg)).isEqualByComparingTo("4");

        // Lo histórico no cambia con movimientos posteriores.
        String stored = mvc.perform(get(BASE + "/{id}", adjustedId).with(authentication(admin)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(lineValue(stored, unit, "balanceAfter")).isEqualTo(15.0);
        assertThat(lineValue(stored, unit, "quantity")).isEqualTo(5.0);
    }

    @Test void listFiltersAndKeepsModulesApart() throws Exception {
        var admin = account();
        String unit = stocked(admin, "unidad", "7450501", "10");
        String kg = stocked(admin, "kg", "7450502", "5");
        String outDoc = confirm(outflow(admin, """
            {"requestKey":"%s","reason":"DAMAGE","reference":"REP-77","lines":[{"itemId":"%s","quantity":1}]}
            """.formatted(key(), unit)));
        String outId = JsonPath.read(outDoc, "$.movement.id");
        String adjustDoc = confirm(adjustment(admin, adjust(key(), "COUNT", "", line(kg, "5", "5"))));

        list(admin, "", "", "").andExpect(jsonPath("$.entries.length()").value(2));
        list(admin, "", "OUT", "").andExpect(jsonPath("$.entries.length()").value(1)).andExpect(jsonPath("$.entries[0].folio").value("SAL-0001"));
        list(admin, "", "ADJUST", "").andExpect(jsonPath("$.entries[0].folio").value("AJU-0001"));
        list(admin, "rep-77", "", "").andExpect(jsonPath("$.entries.length()").value(1));
        list(admin, "AJU-0001", "", "").andExpect(jsonPath("$.entries.length()").value(1));
        list(admin, "", "", "voided").andExpect(jsonPath("$.entries.length()").value(0));
        confirm(reverse(admin, outId, "Error"));
        list(admin, "", "", "voided").andExpect(jsonPath("$.entries[0].folio").value("SAL-0001"));
        list(admin, "", "", "confirmed").andExpect(jsonPath("$.entries[0].folio").value("AJU-0001"));
        mvc.perform(get(BASE).param("type", "RECEIPT").with(authentication(admin))).andExpect(status().isBadRequest());
        mvc.perform(get(BASE + "/items").param("search", "7450502").param("barcode", "true").with(authentication(admin)))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].unit").value("kg"));

        // Entradas no muestran ni leen salidas y ajustes, ni tampoco al revés.
        mvc.perform(get("/api/inventory/receipts").with(authentication(admin)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.entries.length()").value(2));
        mvc.perform(get("/api/inventory/receipts/{id}", outId).with(authentication(admin))).andExpect(status().isNotFound());
        mvc.perform(get("/api/inventory/receipts/{id}", (String) JsonPath.read(adjustDoc, "$.movement.id")).with(authentication(admin))).andExpect(status().isNotFound());
        String voidId = JsonPath.read(mvc.perform(get(BASE + "/{id}", outId).with(authentication(admin))).andReturn().getResponse().getContentAsString(), "$.movement.voidId");
        mvc.perform(get("/api/inventory/receipts/{id}", voidId).with(authentication(admin))).andExpect(status().isNotFound());
        mvc.perform(post("/api/inventory/receipts/{id}/void", (String) JsonPath.read(adjustDoc, "$.movement.id"))
            .with(authentication(admin)).with(csrf()).contentType("application/json").content("{\"reason\":\"x\"}")).andExpect(status().isNotFound());
        String initial = jdbc.queryForObject("SELECT id FROM inventory_movements WHERE company_id=? AND kind='RECEIPT' LIMIT 1", String.class, company(admin));
        mvc.perform(get(BASE + "/{id}", initial).with(authentication(admin))).andExpect(status().isNotFound());
        reverse(admin, initial, "No corresponde").andExpect(status().isNotFound());
    }

    @Test void permissionsFollowTheAccessTableAndCompaniesStayIsolated() throws Exception {
        var admin = account();
        var other = account();
        String unit = stocked(admin, "unidad", "7450601", "50");
        String kg = stocked(admin, "kg", "7450602", "50");
        String foreign = stocked(other, "unidad", "7450603", "5");

        var none = as(admin);
        var receiptsOnly = as(admin, "MODULE_INVENTORY_RECEIPTS_ADMIN", "PERM_VOID_INVENTORY_RECEIPTS");
        var reader = as(admin, "MODULE_INVENTORY_MOVEMENTS_READ");
        var operator = as(admin, "MODULE_INVENTORY_MOVEMENTS_OPERATE");
        var moduleAdmin = as(admin, "MODULE_INVENTORY_MOVEMENTS_ADMIN");
        var adjuster = as(admin, "MODULE_INVENTORY_MOVEMENTS_OPERATE", "PERM_ADJUST_INVENTORY");
        var readerWithAdjust = as(admin, "MODULE_INVENTORY_MOVEMENTS_READ", "PERM_ADJUST_INVENTORY");
        var adjustOnly = as(admin, "PERM_ADJUST_INVENTORY");
        var voider = as(admin, "MODULE_INVENTORY_MOVEMENTS_READ", "PERM_VOID_INVENTORY_MOVEMENTS");
        var voidOnly = as(admin, "PERM_VOID_INVENTORY_MOVEMENTS");
        var movementsOnly = as(admin, "MODULE_INVENTORY_MOVEMENTS_ADMIN", "PERM_VOID_INVENTORY_MOVEMENTS");

        String id = JsonPath.read(confirm(outflow(admin, out(key(), "DAMAGE", "", unit, "1", kg, "1"))), "$.movement.id");

        for (var denied : List.of(none, receiptsOnly, adjustOnly, voidOnly)) {
            mvc.perform(get(BASE).with(authentication(denied))).andExpect(status().isForbidden());
            mvc.perform(get(BASE + "/{id}", id).with(authentication(denied))).andExpect(status().isForbidden());
        }
        for (var allowed : List.of(reader, operator, moduleAdmin, voider)) {
            mvc.perform(get(BASE + "/{id}", id).with(authentication(allowed))).andExpect(status().isOk());
        }
        mvc.perform(get("/api/inventory/receipts").with(authentication(movementsOnly))).andExpect(status().isForbidden());
        mvc.perform(get("/api/inventory/receipts/{id}", id).with(authentication(movementsOnly))).andExpect(status().isForbidden());

        outflow(reader, out(key(), "DAMAGE", "", unit, "1", kg, "1")).andExpect(status().isForbidden());
        confirm(outflow(operator, out(key(), "DAMAGE", "", unit, "1", kg, "1")));
        confirm(outflow(moduleAdmin, out(key(), "DAMAGE", "", unit, "1", kg, "1")));

        String adjustment = adjust(key(), "COUNT", "", line(kg, "47", "47"));
        adjustment(operator, adjustment).andExpect(status().isForbidden());
        adjustment(moduleAdmin, adjustment).andExpect(status().isForbidden());
        adjustment(readerWithAdjust, adjustment).andExpect(status().isForbidden());
        adjustment(adjustOnly, adjustment).andExpect(status().isForbidden());
        confirm(adjustment(adjuster, adjustment));

        reverse(reader, id, "x").andExpect(status().isForbidden());
        reverse(operator, id, "x").andExpect(status().isForbidden());
        reverse(voidOnly, id, "x").andExpect(status().isForbidden());
        reverse(receiptsOnly, id, "x").andExpect(status().isForbidden());
        confirm(reverse(voider, id, "Autorizado"));

        // Otra empresa no ve ni usa estos datos, y sus folios son independientes.
        mvc.perform(get(BASE + "/{id}", id).with(authentication(other))).andExpect(status().isNotFound());
        reverse(other, id, "x").andExpect(status().isNotFound());
        list(other, "", "", "").andExpect(jsonPath("$.entries.length()").value(0));
        outflow(admin, out(key(), "DAMAGE", "", unit, "1", foreign, "1")).andExpect(status().isBadRequest());
        assertThat(balance(other, foreign)).isEqualByComparingTo("5");
        String first = confirm(outflow(other, """
            {"requestKey":"%s","reason":"DAMAGE","lines":[{"itemId":"%s","quantity":1}]}
            """.formatted(key(), foreign)));
        assertThat((String) JsonPath.read(first, "$.movement.folio")).isEqualTo("SAL-0001");

        var locked = new UsernamePasswordAuthenticationToken(new StocklyPrincipal(
            ((StocklyPrincipal) admin.getPrincipal()).accountId(), company(admin), 0, 0, "COMPANY_ADMIN", true),
            null, List.of(new SimpleGrantedAuthority("ROLE_COMPANY_ADMIN")));
        mvc.perform(get(BASE).with(authentication(locked))).andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/outflows").with(csrf()).contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
    }

    @Test void catalogAndPermissionsAreAvailableAfterMigration() {
        assertThat(jdbc.queryForObject("SELECT label FROM permission_modules WHERE module_key='INVENTORY_MOVEMENTS'", String.class)).isEqualTo("Salidas y ajustes");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM special_permission_catalog WHERE permission_key IN ('ADJUST_INVENTORY','VOID_INVENTORY_MOVEMENTS')", Integer.class)).isEqualTo(2);
    }

    private ResultActions outflow(UsernamePasswordAuthenticationToken actor, String payload) throws Exception {
        return mvc.perform(post(BASE + "/outflows").with(authentication(actor)).with(csrf()).contentType("application/json").content(payload));
    }

    private ResultActions adjustment(UsernamePasswordAuthenticationToken actor, String payload) throws Exception {
        return mvc.perform(post(BASE + "/adjustments").with(authentication(actor)).with(csrf()).contentType("application/json").content(payload));
    }

    private ResultActions reverse(UsernamePasswordAuthenticationToken actor, String id, String reason) throws Exception {
        return mvc.perform(post(BASE + "/{id}/void", id).with(authentication(actor)).with(csrf())
            .contentType("application/json").content("{\"reason\":\"" + reason + "\"}"));
    }

    private ResultActions list(UsernamePasswordAuthenticationToken actor, String search, String type, String state) throws Exception {
        return mvc.perform(get(BASE).param("search", search).param("type", type).param("status", state).with(authentication(actor)))
            .andExpect(status().isOk());
    }

    private static String confirm(ResultActions result) throws Exception {
        return result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private static String key() { return UUID.randomUUID().toString(); }

    private static String out(String key, String reason, String detail, String first, String firstQuantity, String second, String secondQuantity) {
        return """
            {"requestKey":"%s","reason":"%s","detail":"%s","lines":[{"itemId":"%s","quantity":%s},{"itemId":"%s","quantity":%s}]}
            """.formatted(key, reason, detail, first, firstQuantity, second, secondQuantity);
    }

    private static String line(String item, String counted, String expected) {
        return "{\"itemId\":\"%s\",\"countedQuantity\":%s,\"expectedBalance\":%s}".formatted(item, counted, expected);
    }

    private static String adjust(String key, String reason, String detail, String... lines) {
        return "{\"requestKey\":\"%s\",\"reason\":\"%s\",\"detail\":\"%s\",\"lines\":[%s]}".formatted(key, reason, detail, String.join(",", lines));
    }

    private static double lineValue(String document, String item, String field) {
        List<Number> values = JsonPath.read(document, "$.lines[?(@.itemId=='" + item + "')]." + field);
        return values.getFirst().doubleValue();
    }

    private BigDecimal balance(UsernamePasswordAuthenticationToken actor, String item) {
        return jdbc.queryForObject("SELECT quantity FROM inventory_balances WHERE company_id=? AND item_id=?",
            BigDecimal.class, company(actor), item);
    }

    /** Crea el artículo y le da saldo con una entrada real, como ocurriría en la operación. */
    private String stocked(UsernamePasswordAuthenticationToken actor, String unit, String barcode, String quantity) throws Exception {
        String id = article(actor, unit, barcode);
        confirm(mvc.perform(post("/api/inventory/receipts").with(authentication(actor)).with(csrf())
            .contentType("application/json").content("""
            {"requestKey":"%s","reason":"INITIAL","lines":[{"itemId":"%s","quantity":%s}]}
            """.formatted(key(), id, quantity))));
        return id;
    }

    private String article(UsernamePasswordAuthenticationToken actor, String unit, String barcode) {
        String product = UUID.randomUUID().toString(), id = UUID.randomUUID().toString(), company = company(actor);
        jdbc.update("INSERT INTO catalog_products(company_id,id,name) VALUES (?,?,?)", company, product, "Prueba " + barcode);
        jdbc.update("INSERT INTO catalog_items(company_id,id,product_id,sku,unit,barcode) VALUES (?,?,?,?,?,?)",
            company, id, product, barcode, unit, barcode);
        return id;
    }

    private static String company(UsernamePasswordAuthenticationToken actor) {
        return ((StocklyPrincipal) actor.getPrincipal()).companyId();
    }

    private static UsernamePasswordAuthenticationToken as(UsernamePasswordAuthenticationToken base, String... authorities) {
        return new UsernamePasswordAuthenticationToken(new StocklyPrincipal(
            ((StocklyPrincipal) base.getPrincipal()).accountId(), company(base), 0, 0, "WORKER", false), null,
            Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
    }

    private UsernamePasswordAuthenticationToken account() {
        String company = UUID.randomUUID().toString(), account = UUID.randomUUID().toString();
        companies.add(company);
        jdbc.update("""
            INSERT INTO companies(id,commercial_name,legal_name,identification_type,identification_normalized,
              contact_email,status,created_at,updated_at)
            VALUES (?, 'Prueba STK-009', 'Prueba STK-009', 'LEGAL', ?, 'test@example.com', 'ACTIVE', NOW(), NOW())
            """, company, company.substring(0,12));
        jdbc.update("""
            INSERT INTO users(id,company_id,full_name,username_normalized,email_normalized,password_hash,must_change_password,role)
            VALUES (?,?,'Prueba',?,?,'unused',FALSE,'COMPANY_ADMIN')
            """, account, company, account.substring(0,30), account+"@example.com");
        return new UsernamePasswordAuthenticationToken(new StocklyPrincipal(account, company, 0, 0, "COMPANY_ADMIN", false),
            null, List.of(new SimpleGrantedAuthority("ROLE_COMPANY_ADMIN")));
    }
}
