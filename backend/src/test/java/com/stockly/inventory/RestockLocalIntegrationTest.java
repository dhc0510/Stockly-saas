package com.stockly.inventory;

import com.jayway.jsonpath.JsonPath;
import com.stockly.identity.StocklyPrincipal;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
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

/** STK-016 · Por reponer: cálculo, «No reponer» y alertas, exportación, permisos y aislamiento. */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "STOCKLY_RESTOCK_TEST_DATABASE", matches = "true")
class RestockLocalIntegrationTest {
    private static final String BASE = "/api/inventory";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    private final List<String> companies = new ArrayList<>();

    @AfterEach void clean() {
        for (String company : companies) {
            jdbc.update("DELETE FROM inventory_alerts WHERE company_id=?", company);
            jdbc.update("DELETE FROM inventory_branch_items WHERE company_id=?", company);
            jdbc.update("DELETE FROM inventory_branch_users WHERE company_id=?", company);
            jdbc.update("DELETE FROM inventory_balances WHERE company_id=?", company);
            jdbc.update("UPDATE inventory_branches SET location_id=NULL WHERE company_id=?", company);
            jdbc.update("DELETE FROM inventory_locations WHERE company_id=?", company);
            jdbc.update("DELETE FROM identity_audit_events WHERE company_id=?", company);
            jdbc.update("DELETE FROM users WHERE company_id=?", company);
            jdbc.update("DELETE FROM company_roles WHERE company_id=?", company);
            jdbc.update("DELETE FROM companies WHERE id=?", company);
        }
    }

    @Test void listsSuggestsPausesAndExports() throws Exception {
        var admin = account("Ferretería Uno");
        String centro = branch(admin, "Centro"), norte = branch(admin, "Norte");
        String cable = item(admin, "Cable eléctrico", "CAB", "metro", "=HYPERLINK(\"x\")");
        String harina = item(admin, "Harina", "HAR", "kg", "Granos");
        String cafe = item(admin, "Café", "CAF", "unidad", "Bebidas");
        assign(admin, centro, cable, "50", null, "40");          // mínimo, nivel deseado 200 se configura abajo
        assign(admin, centro, harina, "5", null, "6.125");       // stock bajo: solo con el filtro
        assign(admin, norte, harina, "5", null, "0");            // agotado, hasta salir de alerta: 5 + 1 − 0
        assign(admin, norte, cafe, null, null, "0");             // agotado sin datos: sin sugerencia

        thresholds(admin, centro, cable, "50", "200", null).andExpect(status().isNoContent());
        thresholds(admin, centro, cable, "50", "50", null).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors.targetQuantity").value("El nivel deseado debe ser mayor que el mínimo (50)."));
        thresholds(admin, centro, cable, "50", "10.555", null).andExpect(status().isBadRequest());

        String list = ok(mvc.perform(get(BASE + "/restock").with(authentication(admin))));
        assertThat((List<String>) JsonPath.read(list, "$.rows[*].sku")).containsExactly("CAF", "HAR", "CAB");
        assertThat(JsonPath.<Integer>read(list, "$.totals.exhausted")).isEqualTo(2);
        assertThat(JsonPath.<Integer>read(list, "$.totals.minimum")).isEqualTo(1);
        assertThat(JsonPath.<String>read(list, "$.companyName")).isEqualTo("Ferretería Uno");
        assertThat(row(list, "CAB", "toOrder")).isEqualTo(160.0);
        assertThat(JsonPath.<List<String>>read(list, "$.rows[?(@.sku=='CAB')].basis")).containsExactly("TARGET");
        assertThat(row(list, "HAR", "toOrder")).isEqualTo(6.0);
        assertThat(JsonPath.<List<String>>read(list, "$.rows[?(@.sku=='HAR')].basis")).containsExactly("RECOVERY");
        assertThat(JsonPath.<List<Object>>read(list, "$.rows[?(@.sku=='CAF')].toOrder")).containsExactly((Object) null);
        assertThat(ok(mvc.perform(get(BASE + "/restock/count").with(authentication(admin))))).contains("\"count\":3");

        // Exacto con los 3 decimales del saldo, búsqueda sin tildes y filtro de stock bajo.
        jdbc.update("UPDATE inventory_branch_items SET target_quantity=20 WHERE company_id=? AND branch_id=? AND item_id=?", company(admin), centro, harina);
        String low = ok(mvc.perform(get(BASE + "/restock").param("includeLow", "true").param("branchId", centro).with(authentication(admin))));
        assertThat(row(low, "HAR", "toOrder")).isEqualTo(13.875);
        String found = ok(mvc.perform(get(BASE + "/restock").param("search", "cafe").with(authentication(admin))));
        assertThat((List<String>) JsonPath.read(found, "$.rows[*].sku")).containsExactly("CAF");

        // «No reponer»: sin alertas, fuera de la lista y del contador, con autor y auditoría; al desmarcar se reevalúa.
        ok(mvc.perform(get(BASE + "/alerts").with(authentication(admin))));
        assertThat(activeAlerts(admin, norte, harina)).isPositive();
        thresholds(admin, norte, harina, "5", null, true).andExpect(status().isNoContent());
        assertThat(activeAlerts(admin, norte, harina)).isZero();
        String paused = ok(mvc.perform(get(BASE + "/restock").with(authentication(admin))));
        assertThat((List<String>) JsonPath.read(paused, "$.rows[*].sku")).doesNotContain("HAR");
        assertThat(JsonPath.<List<String>>read(paused, "$.paused[*].pausedBy")).containsExactly("Ana Pruebas");
        assertThat(ok(mvc.perform(get(BASE + "/restock/count").with(authentication(admin))))).contains("\"count\":2");
        String alerts = ok(mvc.perform(get(BASE + "/alerts").with(authentication(admin))));
        assertThat(alerts).doesNotContain("HAR");
        assertThat(JsonPath.<List<Boolean>>read(ok(mvc.perform(get(BASE + "/items").param("branchId", norte).with(authentication(admin)))),
            "$[?(@.sku=='HAR')].restockPaused")).containsExactly(true);
        thresholds(admin, norte, harina, "5", null, false).andExpect(status().isNoContent());
        ok(mvc.perform(get(BASE + "/alerts").with(authentication(admin))));
        assertThat(activeAlerts(admin, norte, harina)).isPositive();
        assertThat(jdbc.queryForList("SELECT action_key FROM identity_audit_events WHERE company_id=? ORDER BY occurred_at", String.class, company(admin)))
            .containsSubsequence("RESTOCK_PAUSED", "RESTOCK_RESUMED");

        // Exportación: mismas filas, fórmulas neutralizadas y Excel con números.
        var csv = mvc.perform(get(BASE + "/restock/export").param("format", "csv").with(authentication(admin))).andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("por-reponer-todas-"))).andReturn().getResponse();
        String text = new String(csv.getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(text.split("\r\n")).hasSize(4);
        assertThat(text).contains("\"'=HYPERLINK(\"\"x\"\")\"").contains("\"Cable eléctrico\"").contains(",160.00,");
        byte[] xlsx = mvc.perform(get(BASE + "/restock/export").param("format", "xlsx").param("branchId", centro).with(authentication(admin)))
            .andExpect(status().isOk()).andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("por-reponer-centro-")))
            .andReturn().getResponse().getContentAsByteArray();
        assertThat(zipEntry(xlsx, "xl/worksheets/sheet1.xml")).contains("<v>160").doesNotContain("<f>");
        mvc.perform(get(BASE + "/restock/export").param("format", "pdf").with(authentication(admin))).andExpect(status().isBadRequest());
    }

    @Test void respectsPermissionsBranchesAndCompanies() throws Exception {
        var admin = account("Ferretería Dos");
        String centro = branch(admin, "Centro"), norte = branch(admin, "Norte");
        String cable = item(admin, "Cable", "CAB2", "metro", null);
        assign(admin, centro, cable, "10", null, "0");
        assign(admin, norte, cable, "10", null, "0");
        String worker = worker(admin, centro);
        var reader = as(admin, worker, "MODULE_INVENTORY_READ");
        var editor = as(admin, worker, "MODULE_INVENTORY_READ", "MODULE_CATALOG_OPERATE");

        String seen = ok(mvc.perform(get(BASE + "/restock").with(authentication(reader))));
        assertThat((List<String>) JsonPath.read(seen, "$.rows[*].branchName")).containsExactly("Centro");
        mvc.perform(get(BASE + "/restock").param("branchId", norte).with(authentication(reader))).andExpect(status().isNotFound());
        mvc.perform(get(BASE + "/restock/export").param("format", "csv").param("branchId", norte).with(authentication(reader))).andExpect(status().isNotFound());
        assertThat(ok(mvc.perform(get(BASE + "/restock/count").with(authentication(reader))))).contains("\"count\":1");
        thresholds(reader, centro, cable, "10", "40", true).andExpect(status().isForbidden());
        thresholds(editor, norte, cable, "10", "40", true).andExpect(status().isNotFound());
        thresholds(editor, centro, cable, "10", "40", null).andExpect(status().isNoContent());

        var none = as(admin, worker, "MODULE_SALES_READ");
        mvc.perform(get(BASE + "/restock").with(authentication(none))).andExpect(status().isForbidden());
        var other = account("Otra empresa");
        String foreign = ok(mvc.perform(get(BASE + "/restock").with(authentication(other))));
        assertThat(JsonPath.<List<Object>>read(foreign, "$.rows")).isEmpty();
        mvc.perform(get(BASE + "/restock").param("branchId", centro).with(authentication(other))).andExpect(status().isNotFound());
        thresholds(other, centro, cable, "10", "40", true).andExpect(status().isNotFound());
        mvc.perform(get(BASE + "/restock")).andExpect(status().isUnauthorized());
    }

    @Test void showsPreciseAlertsUntilRecoveryAndDismissesOnlyForTheRecipient() throws Exception {
        var admin = account("Ferretería alertas");
        String centro = branch(admin, "Centro"), norte = branch(admin, "Norte");
        String cable = item(admin, "Cable eléctrico", "CAB-007", "metro", "Electricidad");
        assign(admin, centro, cable, "5", null, "4.875");
        assign(admin, norte, cable, "5", null, "0");
        String worker = worker(admin, centro);
        jdbc.update("""
            INSERT INTO role_module_permissions(role_id,module_key,access_level)
            SELECT role_id,'INVENTORY','READ' FROM users WHERE id=?
            """, worker);
        jdbc.update("INSERT INTO inventory_branch_users(company_id,branch_id,user_id) VALUES (?,?,?)", company(admin), norte, worker);
        var reader = as(admin, worker, "MODULE_INVENTORY_READ");
        var catalogOnly = as(admin, worker, "MODULE_CATALOG_OPERATE");
        for (String path : List.of("/branches", "/items", "/alerts", "/restock"))
            mvc.perform(get(BASE + path).with(authentication(catalogOnly))).andExpect(status().isForbidden());

        String adminAlerts = ok(mvc.perform(get(BASE + "/alerts").with(authentication(admin))));
        assertThat(JsonPath.<List<Number>>read(adminAlerts, "$[?(@.branchName=='Centro')].available")).containsExactly(4.875);
        String userAlerts = ok(mvc.perform(get(BASE + "/alerts").with(authentication(reader))));
        String alert = JsonPath.<List<String>>read(userAlerts, "$[?(@.branchName=='Centro')].id").getFirst();
        mvc.perform(post(BASE + "/alerts/{id}/dismiss", alert).with(authentication(admin)).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(post(BASE + "/alerts/{id}/dismiss", alert).with(authentication(reader)).with(csrf())).andExpect(status().isNoContent());
        assertThat(ok(mvc.perform(get(BASE + "/alerts").with(authentication(reader))))).doesNotContain(alert);
        assertThat(ok(mvc.perform(get(BASE + "/alerts").with(authentication(admin))))).contains("Centro");

        thresholds(admin, centro, cable, "5", "12", null).andExpect(status().isNoContent());
        assertThat(ok(mvc.perform(get(BASE + "/alerts").with(authentication(reader))))).doesNotContain(alert);
        jdbc.update("UPDATE inventory_alerts SET last_notified_at=UTC_TIMESTAMP(6)-INTERVAL 25 HOUR WHERE id=?", alert);
        assertThat(ok(mvc.perform(get(BASE + "/alerts").with(authentication(reader))))).contains(alert);

        jdbc.update("UPDATE inventory_balances SET quantity=5.999 WHERE company_id=? AND location_id=? AND item_id=?", company(admin), centro, cable);
        assertThat(ok(mvc.perform(get(BASE + "/alerts").with(authentication(reader))))).contains(alert);
        jdbc.update("UPDATE inventory_balances SET quantity=6 WHERE company_id=? AND location_id=? AND item_id=?", company(admin), centro, cable);
        assertThat(ok(mvc.perform(get(BASE + "/alerts").with(authentication(reader))))).doesNotContain(alert);

        String northAlert = JsonPath.<List<String>>read(userAlerts, "$[?(@.branchName=='Norte')].id").getFirst();
        jdbc.update("DELETE FROM inventory_branch_users WHERE company_id=? AND branch_id=? AND user_id=?", company(admin), norte, worker);
        mvc.perform(get(BASE + "/items").param("branchId", norte).with(authentication(reader))).andExpect(status().isNotFound());
        assertThat(ok(mvc.perform(get(BASE + "/alerts").with(authentication(reader))))).doesNotContain(northAlert);
        mvc.perform(post(BASE + "/alerts/{id}/dismiss", northAlert).with(authentication(reader)).with(csrf())).andExpect(status().isNotFound());
    }

    @Test void managesBranchLifecycleAndFiltersAssignedStock() throws Exception {
        var admin = account("Ferretería existencias");
        String branchBody = """
            {"name":"Centro","code":"C-007","address":"San José","phone":"","contactEmail":"","managerName":""}
            """;
        String created = mvc.perform(post(BASE + "/branches").with(authentication(admin)).with(csrf())
            .contentType("application/json").content(branchBody)).andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String centro = JsonPath.read(created, "$.id");
        mvc.perform(post(BASE + "/branches").with(authentication(admin)).with(csrf())
            .contentType("application/json").content(branchBody)).andExpect(status().isConflict());
        mvc.perform(put(BASE + "/branches/{id}", centro).with(authentication(admin)).with(csrf())
            .contentType("application/json").content(branchBody.replace("Centro", "Centro nuevo")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Centro nuevo"));
        mvc.perform(post(BASE + "/branches/{id}/deactivate", centro).with(authentication(admin)).with(csrf())).andExpect(status().isNoContent());
        assertThat(ok(mvc.perform(get(BASE + "/branches").with(authentication(admin))))).isEqualTo("[]");
        mvc.perform(get(BASE + "/branches").param("includeInactive", "true").with(authentication(admin)))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].active").value(false));
        mvc.perform(post(BASE + "/branches/{id}/activate", centro).with(authentication(admin)).with(csrf())).andExpect(status().isNoContent());
        String worker = worker(admin, centro);
        var reader = as(admin, worker, "MODULE_INVENTORY_READ");
        mvc.perform(post(BASE + "/branches").with(authentication(reader)).with(csrf())
            .contentType("application/json").content(branchBody)).andExpect(status().isForbidden());
        mvc.perform(put(BASE + "/branches/{id}/access", centro).with(authentication(reader)).with(csrf())
            .contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(put(BASE + "/branches/{id}/access", centro).with(authentication(admin)).with(csrf())
            .contentType("application/json").content("{\"userIds\":[\"" + worker + "\"]}")).andExpect(status().isNoContent());
        mvc.perform(get(BASE + "/branches/{id}/access", centro).with(authentication(admin)))
            .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.selected==true)].userId").value(org.hamcrest.Matchers.contains(worker)));
        mvc.perform(delete(BASE + "/branches/{id}", centro).with(authentication(admin)).with(csrf())).andExpect(status().isNoContent());

        String norte = branch(admin, "Norte"), sur = branch(admin, "Sur");
        String cable = item(admin, "Cable eléctrico", "CAB-123", "metro", "Electricidad");
        jdbc.update("UPDATE catalog_items SET barcode='123456789' WHERE company_id=? AND id=?", company(admin), cable);
        assertThat(ok(mvc.perform(get(BASE + "/pending-items").with(authentication(admin))))).contains("CAB-123");
        jdbc.update("INSERT INTO inventory_branch_users(company_id,branch_id,user_id) VALUES (?,?,?)", company(admin), norte, worker);
        var editor = as(admin, worker, "MODULE_INVENTORY_READ", "MODULE_CATALOG_OPERATE");
        mvc.perform(post(BASE + "/branches/{id}/items", sur).with(authentication(editor)).with(csrf())
            .contentType("application/json").content("{}")).andExpect(status().isNotFound());
        mvc.perform(post(BASE + "/branches/{id}/items", norte).with(authentication(editor)).with(csrf())
            .contentType("application/json").content("{\"itemId\":\"" + cable + "\",\"minimumQuantity\":5}"))
            .andExpect(status().isCreated());
        jdbc.update("INSERT INTO inventory_balances(company_id,location_id,item_id,quantity) VALUES (?,?,?,6.125)", company(admin), norte, cable);
        assign(admin, sur, cable, "5", null, "0");
        assertThat(ok(mvc.perform(get(BASE + "/pending-items").with(authentication(admin))))).doesNotContain("CAB-123");
        for (String term : List.of("electrico", "CAB-123", "123456789", "Electricidad"))
            mvc.perform(get(BASE + "/items").param("search", term).param("category", "Electricidad")
                .param("status", "AVAILABLE").with(authentication(admin))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].available").value(6.125)).andExpect(jsonPath("$[0].reserved").value(0));
        mvc.perform(get(BASE + "/items").param("status", "LOW").with(authentication(admin)))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].branchId").value(norte));
        mvc.perform(get(BASE + "/items").param("status", "EXHAUSTED").with(authentication(admin)))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].branchId").value(sur));
        mvc.perform(delete(BASE + "/branches/{id}", norte).with(authentication(admin)).with(csrf())).andExpect(status().isConflict());
    }

    private ResultActions thresholds(UsernamePasswordAuthenticationToken actor, String branch, String item, String minimum, String target, Boolean paused) throws Exception {
        String body = "{\"minimumQuantity\":%s,\"lowThreshold\":2,\"recoveryMargin\":1,\"exhaustedBasis\":\"AVAILABLE\",\"targetQuantity\":%s,\"restockPaused\":%s}"
            .formatted(minimum, target, paused);
        return mvc.perform(put(BASE + "/branches/{b}/items/{i}/thresholds", branch, item).with(authentication(actor)).with(csrf())
            .contentType("application/json").content(body));
    }

    private int activeAlerts(UsernamePasswordAuthenticationToken actor, String branch, String item) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM inventory_alerts WHERE company_id=? AND branch_id=? AND item_id=? AND active=TRUE",
            Integer.class, company(actor), branch, item);
    }

    private static double row(String json, String sku, String field) {
        List<Number> values = JsonPath.read(json, "$.rows[?(@.sku=='" + sku + "')]." + field);
        return values.getFirst().doubleValue();
    }

    private static String zipEntry(byte[] zip, String name) throws Exception {
        var file = java.nio.file.Files.createTempFile("por-reponer", ".xlsx");
        try {
            java.nio.file.Files.write(file, zip);
            try (var archive = new java.util.zip.ZipFile(file.toFile())) {
                var entry = archive.getEntry(name);
                return entry == null ? "" : new String(archive.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
            }
        } finally { java.nio.file.Files.deleteIfExists(file); }
    }

    private static String ok(ResultActions result) throws Exception {
        return result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String branch(UsernamePasswordAuthenticationToken actor, String name) {
        String company = company(actor), id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO inventory_locations(company_id,id,name) VALUES (?,?,?)", company, id, name);
        jdbc.update("""
            INSERT INTO inventory_branches(company_id,id,name,code,address,phone,contact_email,manager_name,location_id,created_at,updated_at)
            VALUES (?,?,?,?,'','','','',?,NOW(),NOW())
            """, company, id, name, name.toUpperCase(Locale.ROOT), id);
        jdbc.update("INSERT INTO inventory_branch_users(company_id,branch_id,user_id) VALUES (?,?,?)", company, id,
            ((StocklyPrincipal) actor.getPrincipal()).accountId());
        return id;
    }

    private String item(UsernamePasswordAuthenticationToken actor, String name, String sku, String unit, String category) {
        String company = company(actor), product = UUID.randomUUID().toString(), id = UUID.randomUUID().toString();
        if (category != null) jdbc.update("INSERT IGNORE INTO catalog_categories(company_id,name) VALUES (?,?)", company, category);
        jdbc.update("INSERT INTO catalog_products(company_id,id,name,category) VALUES (?,?,?,?)", company, product, name, category);
        jdbc.update("INSERT INTO catalog_items(company_id,id,product_id,sku,unit) VALUES (?,?,?,?,?)", company, id, product, sku, unit);
        return id;
    }

    private void assign(UsernamePasswordAuthenticationToken actor, String branch, String item, String minimum, String target, String stock) {
        String company = company(actor);
        jdbc.update("""
            INSERT INTO inventory_branch_items(company_id,branch_id,item_id,minimum_quantity,target_quantity,created_at,updated_at)
            VALUES (?,?,?,?,?,NOW(),NOW())
            """, company, branch, item, minimum == null ? null : new BigDecimal(minimum), target == null ? null : new BigDecimal(target));
        jdbc.update("INSERT INTO inventory_balances(company_id,location_id,item_id,quantity) VALUES (?,?,?,?)", company, branch, item, new BigDecimal(stock));
    }

    private String worker(UsernamePasswordAuthenticationToken admin, String branch) {
        String company = company(admin), id = UUID.randomUUID().toString(), role = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO company_roles(id,company_id,name,created_at,updated_at) VALUES (?,?,'Bodega',NOW(),NOW())", role, company);
        jdbc.update("""
            INSERT INTO users(id,company_id,full_name,username_normalized,email_normalized,password_hash,must_change_password,role,role_id)
            VALUES (?,?,'Trabajador',?,?,'unused',FALSE,'WORKER',?)
            """, id, company, id.substring(0, 30), id + "@example.com", role);
        jdbc.update("INSERT INTO inventory_branch_users(company_id,branch_id,user_id) VALUES (?,?,?)", company, branch, id);
        return id;
    }

    private static UsernamePasswordAuthenticationToken as(UsernamePasswordAuthenticationToken base, String account, String... authorities) {
        return new UsernamePasswordAuthenticationToken(new StocklyPrincipal(account, company(base), 0, 0, "WORKER", false), null,
            Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
    }

    private static String company(UsernamePasswordAuthenticationToken actor) { return ((StocklyPrincipal) actor.getPrincipal()).companyId(); }

    private UsernamePasswordAuthenticationToken account(String name) {
        String company = UUID.randomUUID().toString(), account = UUID.randomUUID().toString();
        companies.add(company);
        jdbc.update("""
            INSERT INTO companies(id,commercial_name,legal_name,identification_type,identification_normalized,contact_email,status,created_at,updated_at)
            VALUES (?,?,?,'LEGAL',?,'test@example.com','ACTIVE',NOW(),NOW())
            """, company, name, name, "3" + String.format("%09d", java.util.concurrent.ThreadLocalRandom.current().nextLong(1_000_000_000L)));
        jdbc.update("""
            INSERT INTO users(id,company_id,full_name,username_normalized,email_normalized,password_hash,must_change_password,role)
            VALUES (?,?,'Ana Pruebas',?,?,'unused',FALSE,'COMPANY_ADMIN')
            """, account, company, account.substring(0, 30), account + "@example.com");
        return new UsernamePasswordAuthenticationToken(new StocklyPrincipal(account, company, 0, 0, "COMPANY_ADMIN", false),
            null, List.of(new SimpleGrantedAuthority("ROLE_COMPANY_ADMIN")));
    }
}
