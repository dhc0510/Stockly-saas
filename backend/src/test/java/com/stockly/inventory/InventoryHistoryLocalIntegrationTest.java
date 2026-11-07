package com.stockly.inventory;

import com.jayway.jsonpath.JsonPath;
import com.stockly.identity.StocklyPrincipal;
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

/** Se ejecuta sobre una base con colación que distingue tildes, para probar que las búsquedas no dependen de ella. */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "STOCKLY_HISTORY_TEST_DATABASE", matches = "true")
class InventoryHistoryLocalIntegrationTest {
    private static final String BASE = "/api/inventory/history";
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

    @Test void listsEveryKindOnePerLineWithItsDataNewestFirst() throws Exception {
        var admin = account();
        String coffee = article(admin, "Café molido", null, "unidad", "CAF-1", "7460001");
        String flour = article(admin, "Harina", null, "kg", "HAR-1", "7460002");
        String receipt = id(receipt(admin, "GUÍA-8", coffee, "10", flour, "2.5"));
        String out = id(movementPost(admin, "/outflows", out(coffee, "3", flour, "0.5")));
        movementPost(admin, "/adjustments", adjust(coffee, "8", "7"));
        postVoid(admin, out);

        var page = list(admin, Map.of());
        // 2 de la entrada, 2 de la salida, 1 del ajuste y 2 de la anulación.
        page.andExpect(jsonPath("$.rows.length()").value(7)).andExpect(jsonPath("$.hasMore").value(false))
            .andExpect(jsonPath("$.rows[0].kind").value("VOID"))
            .andExpect(jsonPath("$.rows[0].originalFolio").value("SAL-0001"))
            .andExpect(jsonPath("$.rows[2].folio").value("AJU-0001"))
            .andExpect(jsonPath("$.rows[2].quantity").value(1))
            .andExpect(jsonPath("$.rows[2].balanceAfter").value(8))
            .andExpect(jsonPath("$.rows[6].folio").value("ENT-0001"));
        String body = page.andReturn().getResponse().getContentAsString();
        List<String> voided = JsonPath.read(body, "$.rows[?(@.folio=='SAL-0001')].voidFolio");
        assertThat(voided).containsOnly("ANU-0001");
        List<String> actors = JsonPath.read(body, "$.rows[*].actor");
        assertThat(actors).containsOnly("Ana Pruebas");

        for (String kind : List.of("RECEIPT", "OUT", "ADJUST", "VOID")) {
            String document = mvc.perform(get(BASE).param("type", kind).with(authentication(admin))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
            String movement = JsonPath.read(document, "$.rows[0].movementId");
            mvc.perform(get(BASE + "/movements/{id}", movement).with(authentication(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.movement.kind").value(kind));
        }
        assertThat(receipt).isNotBlank();
    }

    @Test void filtersByFolioReferenceItemTypeActorAndPeriod() throws Exception {
        var admin = account();
        String coffee = article(admin, "Café molido", null, "unidad", "CAF-2", "7460101");
        String flour = article(admin, "Harina", null, "kg", "HAR-2", "7460102");
        receipt(admin, "GUÍA-9", coffee, "10", flour, "5");
        movementPost(admin, "/outflows", out(coffee, "1", flour, "1"));
        String actor = ((StocklyPrincipal) admin.getPrincipal()).accountId();

        list(admin, Map.of("search", "ENT-0001")).andExpect(jsonPath("$.rows.length()").value(2));
        list(admin, Map.of("search", "guia-9")).andExpect(jsonPath("$.rows.length()").value(2));
        list(admin, Map.of("itemId", coffee)).andExpect(jsonPath("$.rows.length()").value(2));
        list(admin, Map.of("itemId", coffee, "type", "OUT")).andExpect(jsonPath("$.rows.length()").value(1));
        list(admin, Map.of("actorId", actor)).andExpect(jsonPath("$.rows.length()").value(4));
        list(admin, Map.of("actorId", UUID.randomUUID().toString())).andExpect(jsonPath("$.rows.length()").value(0));
        mvc.perform(get(BASE + "/actors").with(authentication(admin)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].name").value("Ana Pruebas"));

        // Costa Rica es UTC-6: el 15 de marzo local va de las 06:00 del 15 a las 06:00 del 16 en UTC.
        String entry = jdbc.queryForObject("SELECT id FROM inventory_movements WHERE company_id=? AND folio='ENT-0001'", String.class, company(admin));
        String exit = jdbc.queryForObject("SELECT id FROM inventory_movements WHERE company_id=? AND folio='SAL-0001'", String.class, company(admin));
        jdbc.update("UPDATE inventory_movements SET created_at='2026-03-15 06:00:00' WHERE company_id=? AND id=?", company(admin), entry);
        jdbc.update("UPDATE inventory_movements SET created_at='2026-03-16 05:59:59' WHERE company_id=? AND id=?", company(admin), exit);
        list(admin, Map.of("from", "2026-03-15", "to", "2026-03-15")).andExpect(jsonPath("$.rows.length()").value(4));
        list(admin, Map.of("from", "2026-03-14", "to", "2026-03-14")).andExpect(jsonPath("$.rows.length()").value(0));
        list(admin, Map.of("from", "2026-03-16", "to", "2026-03-16")).andExpect(jsonPath("$.rows.length()").value(0));
        jdbc.update("UPDATE inventory_movements SET created_at='2026-03-16 06:00:00' WHERE company_id=? AND id=?", company(admin), exit);
        list(admin, Map.of("from", "2026-03-16", "to", "2026-03-16")).andExpect(jsonPath("$.rows.length()").value(2));
        list(admin, Map.of("from", "2026-03-15", "to", "2026-03-16")).andExpect(jsonPath("$.rows.length()").value(4));

        for (var params : List.of(Map.of("from", "2099-01-01"), Map.of("to", "2099-01-01"), Map.of("from", "2026-03-16", "to", "2026-03-15"),
            Map.of("from", "ayer"), Map.of("type", "OTRO"), Map.of("itemId", "x"), Map.of("actorId", "x"))) {
            var request = get(BASE).with(authentication(admin));
            params.forEach(request::param);
            mvc.perform(request).andExpect(status().isBadRequest());
        }
    }

    @Test void paginatesAndKeepsAStableOrder() throws Exception {
        var admin = account();
        String item = article(admin, "Clavos", null, "unidad", "CLA-1", "7460201");
        for (int i = 0; i < 26; i++) movementPost(admin, "/adjustments", adjust(item, String.valueOf(i + 1), String.valueOf(i)));
        list(admin, Map.of()).andExpect(jsonPath("$.rows.length()").value(25)).andExpect(jsonPath("$.hasMore").value(true))
            .andExpect(jsonPath("$.rows[0].balanceAfter").value(26));
        list(admin, Map.of("page", "1")).andExpect(jsonPath("$.rows.length()").value(1)).andExpect(jsonPath("$.hasMore").value(false))
            .andExpect(jsonPath("$.rows[0].balanceAfter").value(1));
    }

    @Test void itemHistoryKeepsHistoricalDataAndMarksInactive() throws Exception {
        var admin = account();
        String item = article(admin, "Té verde", null, "unidad", "TEV-1", "7460301");
        receipt(admin, "", item, "10");
        movementPost(admin, "/outflows", """
            {"requestKey":"%s","reason":"EXPIRED","lines":[{"itemId":"%s","quantity":4}]}
            """.formatted(key(), item));

        mvc.perform(get(BASE + "/items/{id}", item).with(authentication(admin)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.balance").value(6)).andExpect(jsonPath("$.item.active").value(true));
        list(admin, Map.of("itemId", item)).andExpect(jsonPath("$.rows[0].balanceAfter").value(6));

        String product = jdbc.queryForObject("SELECT product_id FROM catalog_items WHERE company_id=? AND id=?", String.class, company(admin), item);
        jdbc.update("UPDATE catalog_products SET name='Infusión de té' WHERE company_id=? AND id=?", company(admin), product);
        jdbc.update("UPDATE catalog_items SET active=FALSE,sku='TEV-NUEVO' WHERE company_id=? AND id=?", company(admin), item);

        list(admin, Map.of("itemId", item))
            .andExpect(jsonPath("$.rows[0].itemName").value("Té verde"))
            .andExpect(jsonPath("$.rows[0].sku").value("TEV-1"))
            .andExpect(jsonPath("$.rows[0].itemActive").value(false));
        mvc.perform(get(BASE + "/items/{id}", item).with(authentication(admin)))
            .andExpect(jsonPath("$.item.active").value(false)).andExpect(jsonPath("$.item.name").value("Infusión de té"));
        mvc.perform(get(BASE + "/items").param("search", "infusion").with(authentication(admin)))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].active").value(false));
        String never = article(admin, "Sin movimientos", null, "unidad", "SIN-1", "7460302");
        mvc.perform(get(BASE + "/items/{id}", never).with(authentication(admin))).andExpect(jsonPath("$.balance").value(0));
    }

    @Test void permissionsAreReadOnlyAndSeparatedAndCompaniesIsolated() throws Exception {
        var admin = account();
        var other = account();
        String item = article(admin, "Clavos", null, "unidad", "CLA-3", "7460401");
        String movement = id(receipt(admin, "", item, "5"));

        for (String level : List.of("READ", "OPERATE", "ADMIN")) {
            var viewer = as(admin, "MODULE_INVENTORY_HISTORY_" + level);
            mvc.perform(get(BASE).with(authentication(viewer))).andExpect(status().isOk());
            mvc.perform(get(BASE + "/movements/{id}", movement).with(authentication(viewer))).andExpect(status().isOk());
            mvc.perform(get("/api/inventory/receipts").with(authentication(viewer))).andExpect(status().isForbidden());
            mvc.perform(get("/api/inventory/movements").with(authentication(viewer))).andExpect(status().isForbidden());
        }
        for (var denied : List.of(as(admin), as(admin, "MODULE_INVENTORY_RECEIPTS_ADMIN"), as(admin, "MODULE_INVENTORY_MOVEMENTS_ADMIN"), as(admin, "MODULE_INVENTORY_READ"))) {
            mvc.perform(get(BASE).with(authentication(denied))).andExpect(status().isForbidden());
            mvc.perform(get(BASE + "/items/{id}", item).with(authentication(denied))).andExpect(status().isForbidden());
            mvc.perform(get(BASE + "/actors").with(authentication(denied))).andExpect(status().isForbidden());
            mvc.perform(get(BASE + "/movements/{id}", movement).with(authentication(denied))).andExpect(status().isForbidden());
        }
        var viewer = as(admin, "MODULE_INVENTORY_HISTORY_ADMIN");
        assertThat(mvc.perform(post(BASE).with(authentication(viewer)).with(csrf())).andReturn().getResponse().getStatus()).isGreaterThanOrEqualTo(400);
        mvc.perform(post("/api/inventory/receipts/{id}/void", movement).with(authentication(viewer)).with(csrf())
            .contentType("application/json").content("{\"reason\":\"x\"}")).andExpect(status().isForbidden());

        mvc.perform(get(BASE + "/movements/{id}", movement).with(authentication(other))).andExpect(status().isNotFound());
        mvc.perform(get(BASE + "/items/{id}", item).with(authentication(other))).andExpect(status().isNotFound());
        list(other, Map.of("itemId", item)).andExpect(jsonPath("$.rows.length()").value(0));
        list(other, Map.of()).andExpect(jsonPath("$.rows.length()").value(0));
        mvc.perform(get(BASE + "/actors").with(authentication(other))).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get(BASE + "/items").param("search", "clavos").with(authentication(other))).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        // Los mensajes de seguridad conservan sus tildes.
        String denied = mvc.perform(get(BASE).with(authentication(as(admin)))).andExpect(status().isForbidden())
            .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(denied).contains("operación");
    }

    @Test void everySearchIgnoresAccentsAndCaseOnAnAccentSensitiveDatabase() throws Exception {
        assertThat(jdbc.queryForObject("SELECT @@collation_database", String.class)).isEqualTo("utf8mb4_0900_as_cs");
        var admin = account();
        String coffee = article(admin, "Café Molido", "Grande", "unidad", "CAFÉ-1", "7460501");
        jdbc.update("INSERT INTO catalog_categories(company_id,name) VALUES (?,'Bebidas Calientes')", company(admin));
        jdbc.update("UPDATE catalog_products SET category='Bebidas Calientes' WHERE company_id=?", company(admin));
        receipt(admin, "GUÍA-7", coffee, "5");
        movementPost(admin, "/outflows", """
            {"requestKey":"%s","reason":"DAMAGE","reference":"Revisión Técnica","lines":[{"itemId":"%s","quantity":1}]}
            """.formatted(key(), coffee));

        for (String term : List.of("cafe", "CAFÉ", "Cafe Mol")) {
            mvc.perform(get("/api/catalog").param("search", term).with(authentication(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.products.length()").value(1));
            for (String path : List.of("/api/inventory/receipts/items", "/api/inventory/movements/items", BASE + "/items"))
                mvc.perform(get(path).param("search", term).with(authentication(admin)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        }
        mvc.perform(get(BASE + "/items").param("search", "grande").with(authentication(admin))).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/inventory/receipts").param("search", "guia-7").with(authentication(admin)))
            .andExpect(jsonPath("$.entries.length()").value(1));
        mvc.perform(get("/api/inventory/movements").param("search", "revision tecnica").with(authentication(admin)))
            .andExpect(jsonPath("$.entries.length()").value(1));
        list(admin, Map.of("search", "guia-7")).andExpect(jsonPath("$.rows.length()").value(1));
        list(admin, Map.of("search", "REVISION")).andExpect(jsonPath("$.rows.length()").value(1));

        // Un código de barras exacto se sigue encontrando.
        mvc.perform(get("/api/inventory/receipts/items").param("search", "7460501").param("barcode", "true").with(authentication(admin)))
            .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/inventory/receipts/items").param("search", "74605").param("barcode", "true").with(authentication(admin)))
            .andExpect(jsonPath("$.length()").value(0));

        // Existencias de STK-007 necesita el artículo asignado a una sucursal.
        String branch = UUID.randomUUID().toString();
        jdbc.update("""
            INSERT INTO inventory_branches(company_id,id,name,code,address,phone,contact_email,manager_name,created_at,updated_at)
            VALUES (?,?,'Centro','CEN','San José','22223333','c@example.com','Ana',NOW(6),NOW(6))
            """, company(admin), branch);
        jdbc.update("INSERT INTO inventory_branch_items(company_id,branch_id,item_id,created_at,updated_at) VALUES (?,?,?,NOW(6),NOW(6))", company(admin), branch, coffee);
        for (String term : List.of("cafe", "bebidas calientes"))
            mvc.perform(get("/api/inventory/items").param("search", term).with(authentication(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }

    @Test void companySearchIgnoresAccentsOnAnAccentSensitiveDatabase() throws Exception {
        String saasId = UUID.randomUUID().toString(), company = UUID.randomUUID().toString();
        companies.add(company);
        jdbc.update("""
            INSERT INTO companies(id,commercial_name,legal_name,identification_type,identification_normalized,contact_email,status,created_at,updated_at)
            VALUES (?, 'Ferretería Ñandú', 'Ferretería Ñandú S.A.', 'LEGAL', ?, 'test@example.com', 'ACTIVE', NOW(), NOW())
            """, company, company.substring(0, 12));
        String owner = UUID.randomUUID().toString();
        jdbc.update("""
            INSERT INTO users(id,company_id,full_name,username_normalized,email_normalized,password_hash,must_change_password,role)
            VALUES (?,?,'Dueña',?,?,'unused',FALSE,'COMPANY_ADMIN')
            """, owner, company, owner.substring(0, 30), owner + "@example.com");
        jdbc.update("""
            INSERT INTO users(id,company_id,full_name,username_normalized,email_normalized,password_hash,must_change_password,role)
            VALUES (?,NULL,'SaaS',?,?,'unused',FALSE,'SAAS_ADMIN')
            """, saasId, saasId.substring(0, 30), saasId + "@example.com");
        try {
            var saas = new UsernamePasswordAuthenticationToken(new StocklyPrincipal(saasId, null, 0, 0, "SAAS_ADMIN", false), null,
                List.of(new SimpleGrantedAuthority("ROLE_SAAS_ADMIN")));
            for (String term : List.of("ferreteria nandu", "FERRETERÍA", "ñandú"))
                mvc.perform(get("/api/companies").param("search", term).with(authentication(saas)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        } finally {
            jdbc.update("DELETE FROM users WHERE id=?", saasId);
        }
    }

    private ResultActions list(UsernamePasswordAuthenticationToken actor, Map<String, String> params) throws Exception {
        var request = get(BASE).with(authentication(actor));
        params.forEach(request::param);
        return mvc.perform(request).andExpect(status().isOk());
    }

    private String movementPost(UsernamePasswordAuthenticationToken actor, String path, String body) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/inventory/movements" + path)
            .with(authentication(actor)).with(csrf()).contentType("application/json").content(body))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private void postVoid(UsernamePasswordAuthenticationToken actor, String id) throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/inventory/movements/{id}/void", id)
            .with(authentication(actor)).with(csrf()).contentType("application/json").content("{\"reason\":\"Error\"}")).andExpect(status().isOk());
    }

    private String receipt(UsernamePasswordAuthenticationToken actor, String reference, String... pairs) throws Exception {
        var lines = new StringJoiner(",");
        for (int i = 0; i < pairs.length; i += 2) lines.add("{\"itemId\":\"%s\",\"quantity\":%s}".formatted(pairs[i], pairs[i + 1]));
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/inventory/receipts")
            .with(authentication(actor)).with(csrf()).contentType("application/json")
            .content("{\"requestKey\":\"%s\",\"reason\":\"RECEIPT\",\"reference\":\"%s\",\"lines\":[%s]}".formatted(key(), reference, lines)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private static String id(String document) { return JsonPath.read(document, "$.movement.id"); }
    private static String key() { return UUID.randomUUID().toString(); }

    private static String out(String first, String firstQuantity, String second, String secondQuantity) {
        return """
            {"requestKey":"%s","reason":"DAMAGE","lines":[{"itemId":"%s","quantity":%s},{"itemId":"%s","quantity":%s}]}
            """.formatted(key(), first, firstQuantity, second, secondQuantity);
    }

    private static String adjust(String item, String counted, String expected) {
        return """
            {"requestKey":"%s","reason":"COUNT","lines":[{"itemId":"%s","countedQuantity":%s,"expectedBalance":%s}]}
            """.formatted(key(), item, counted, expected);
    }

    private String article(UsernamePasswordAuthenticationToken actor, String name, String variant, String unit, String sku, String barcode) {
        String product = UUID.randomUUID().toString(), id = UUID.randomUUID().toString(), company = company(actor);
        jdbc.update("INSERT INTO catalog_products(company_id,id,name) VALUES (?,?,?)", company, product, name);
        jdbc.update("INSERT INTO catalog_items(company_id,id,product_id,sku,unit,barcode,variant_name) VALUES (?,?,?,?,?,?,?)",
            company, id, product, sku, unit, barcode, variant);
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
            VALUES (?, 'Prueba STK-010', 'Prueba STK-010', 'LEGAL', ?, 'test@example.com', 'ACTIVE', NOW(), NOW())
            """, company, company.substring(0, 12));
        jdbc.update("""
            INSERT INTO users(id,company_id,full_name,username_normalized,email_normalized,password_hash,must_change_password,role)
            VALUES (?,?,'Ana Pruebas',?,?,'unused',FALSE,'COMPANY_ADMIN')
            """, account, company, account.substring(0, 30), account + "@example.com");
        return new UsernamePasswordAuthenticationToken(new StocklyPrincipal(account, company, 0, 0, "COMPANY_ADMIN", false),
            null, List.of(new SimpleGrantedAuthority("ROLE_COMPANY_ADMIN")));
    }
}
