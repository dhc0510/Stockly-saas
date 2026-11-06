package com.stockly.catalog;

import com.stockly.identity.StocklyPrincipal;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
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
@EnabledIfEnvironmentVariable(named = "STOCKLY_CATALOG_TEST_DATABASE", matches = "true")
class CatalogLocalIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    private final List<String> companies = new ArrayList<>();

    @AfterEach void cleanFixtures() {
        for (String company : companies) {
            jdbc.update("DELETE FROM users WHERE company_id=?", company);
            jdbc.update("DELETE FROM company_roles WHERE company_id=?", company);
            jdbc.update("DELETE FROM companies WHERE id=?", company);
        }
    }

    @Test
    void keepsIdentityOnConversionAndProtectsReservedCodesAndImages() throws Exception {
        var admin = account("COMPANY_ADMIN", "ROLE_COMPANY_ADMIN");
        var outsider = account("COMPANY_ADMIN", "ROLE_COMPANY_ADMIN");
        mvc.perform(post("/api/catalog/categories").with(authentication(admin)).with(csrf())
            .contentType("application/json").content("{\"name\":\"Bebidas\"}")).andExpect(status().isCreated());
        String image = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a8X8AAAAASUVORK5CYII=";
        String created = mvc.perform(post("/api/catalog/products").with(authentication(admin)).with(csrf())
            .contentType("application/json").content("""
                {"name":"Café","category":"Bebidas","image":"%s","item":{"sku":"CAF-1","barcode":"1234"}}
                """.formatted(image)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].unit").value("unidad"))
            .andReturn().getResponse().getContentAsString();
        String product = JsonPath.read(created, "$.id");
        String item = JsonPath.read(created, "$.items[0].id");

        mvc.perform(get("/api/catalog/products/{id}/image", product).with(authentication(admin)))
            .andExpect(status().isOk()).andExpect(content().contentType("image/png"));
        mvc.perform(get("/api/catalog/products/{id}/image", product).with(authentication(outsider)))
            .andExpect(status().isNotFound());
        mvc.perform(put("/api/catalog/items/{id}", item).with(authentication(admin)).with(csrf())
            .contentType("application/json").content("""
                {"sku":"CAF-2","barcode":"1234","unit":"unidad"}
                """)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT previous_sku FROM catalog_sku_history WHERE item_id=?", String.class, item))
            .isEqualTo("CAF-1");
        mvc.perform(post("/api/catalog/products").with(authentication(admin)).with(csrf())
            .contentType("application/json").content("""
                {"name":"Duplicado","item":{"sku":"CAF-1"}}
                """)).andExpect(status().isConflict());
        mvc.perform(post("/api/catalog/products").with(authentication(outsider)).with(csrf())
            .contentType("application/json").content("""
                {"name":"Otro catálogo","item":{"sku":"CAF-1","barcode":"1234"}}
                """)).andExpect(status().isOk());

        jdbc.update("UPDATE catalog_items SET has_movements=TRUE WHERE id=?", item);
        mvc.perform(put("/api/catalog/products/{id}", product).with(authentication(admin)).with(csrf())
            .contentType("application/json").content("""
                {"name":"Café","category":"Bebidas","variants":["Tamaño: Grande","Tamaño: Mediano"],
                 "keepVariant":"Tamaño: Grande"}
                """)).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(item))
            .andExpect(jsonPath("$.items[0].sku").value("CAF-2"))
            .andExpect(jsonPath("$.items[1].sku").isNotEmpty());
        mvc.perform(put("/api/catalog/items/{id}", item).with(authentication(admin)).with(csrf())
            .contentType("application/json").content("""
                {"sku":"CAF-2","barcode":"1234","unit":"kg"}
                """)).andExpect(status().isConflict());
        mvc.perform(get("/api/catalog").param("search", "1234").with(authentication(admin)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.products[0].id").value(product));
        mvc.perform(put("/api/catalog/products/{id}/status", product).with(authentication(admin)).with(csrf())
            .contentType("application/json").content("{\"active\":false}")).andExpect(status().isNoContent());
        mvc.perform(get("/api/catalog").with(authentication(admin)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.products").isEmpty());
        mvc.perform(get("/api/catalog").param("active", "false").with(authentication(admin)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.products[0].id").value(product));
    }

    @Test
    void separatesReadEditAndPricePermissions() throws Exception {
        var reader = account("WORKER", "ROLE_WORKER", "MODULE_CATALOG_READ");
        var editor = account("WORKER", "ROLE_WORKER", "MODULE_CATALOG_OPERATE");
        mvc.perform(get("/api/catalog").with(authentication(reader))).andExpect(status().isOk());
        mvc.perform(post("/api/catalog/products").with(authentication(reader)).with(csrf())
            .contentType("application/json").content("{\"name\":\"Artículo\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/catalog/products").with(authentication(editor)).with(csrf())
            .contentType("application/json").content("""
                {"name":"Artículo","item":{"price":10,"setPrice":true}}
                """)).andExpect(status().isForbidden());
        var principal = (StocklyPrincipal) editor.getPrincipal();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM catalog_products WHERE company_id=?",
            Integer.class, principal.companyId())).isZero();
    }

    @Test
    void concurrentRequestsCannotClaimTheSameSku() throws Exception {
        var admin = account("COMPANY_ADMIN", "ROLE_COMPANY_ADMIN");
        var gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Integer> create = () -> {
                gate.await();
                return mvc.perform(post("/api/catalog/products").with(authentication(admin)).with(csrf())
                    .contentType("application/json").content("""
                        {"name":"En paralelo","item":{"sku":"SOLO-UNO"}}
                        """)).andReturn().getResponse().getStatus();
            };
            var first = pool.submit(create);
            var second = pool.submit(create);
            gate.countDown();
            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(200, 409);
        }
        var principal = (StocklyPrincipal) admin.getPrincipal();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM catalog_products WHERE company_id=?",
            Integer.class, principal.companyId())).isEqualTo(1);
    }

    private UsernamePasswordAuthenticationToken account(String role, String... authorities) {
        String company = UUID.randomUUID().toString();
        companies.add(company);
        String account = UUID.randomUUID().toString();
        String roleId = null;
        jdbc.update("""
            INSERT INTO companies(id,commercial_name,legal_name,identification_type,identification_normalized,
              contact_email,status,created_at,updated_at)
            VALUES (?, 'Pruebas STK-006', 'Pruebas STK-006', 'LEGAL', ?, 'test@example.com', 'ACTIVE', NOW(), NOW())
            """, company, company.substring(0, 12));
        if (role.equals("WORKER")) {
            roleId = UUID.randomUUID().toString();
            jdbc.update("INSERT INTO company_roles(id,company_id,name,created_at,updated_at) VALUES (?,?,'Prueba',NOW(),NOW())",
                roleId, company);
        }
        jdbc.update("""
            INSERT INTO users(id,company_id,full_name,username_normalized,email_normalized,password_hash,
                must_change_password,role,role_id)
            VALUES (?,?,'Prueba',?,?,'unused',FALSE,?,?)
            """, account, company, account.substring(0, 30), account + "@example.com", role, roleId);
        return new UsernamePasswordAuthenticationToken(new StocklyPrincipal(account, company, 0, 0, role, false), null,
            Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
    }
}
