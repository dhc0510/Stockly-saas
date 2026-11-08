package com.stockly.inventory;

import com.stockly.identity.IdentityFailure;
import com.stockly.identity.StocklyPrincipal;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** STK-012: cobros, devoluciones, medios de pago y resumen sobre MySQL real. */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "STOCKLY_SALES_TEST_DATABASE", matches = "true")
class SalePaymentLocalIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired SaleService sales;
    @Autowired SalePaymentService payments;
    @Autowired SaleReturnService returns;
    @Autowired InventoryHistoryService history;
    @Autowired JdbcTemplate jdbc;
    private String item;

    @Test void collectsRefundsAndSummarizesPaymentsWithinCompanyAndBranchScope() throws Exception {
        String company = id(), other = id(), owner = id(), worker = id(), outsider = id();
        String branchA = id(), branchB = id(), product = id();
        item = id();
        company(company, "Prueba STK-012");
        company(other, "Otra STK-012");
        try {
            user(owner, company, "COMPANY_ADMIN");
            user(worker, company, "WORKER");
            user(outsider, other, "COMPANY_ADMIN");
            jdbc.update("INSERT INTO inventory_locations(company_id,id,name) VALUES (?,'principal','Principal'),(?,'loc-b','Norte')",
                company, company);
            jdbc.update("""
                INSERT INTO inventory_branches(company_id,id,name,code,address,phone,contact_email,manager_name,location_id,created_at,updated_at)
                VALUES (?,?,'Centro','CENTRO','','','','','principal',NOW(),NOW()),(?,?,'Norte','NORTE','','','','','loc-b',NOW(),NOW())
                """, company, branchA, company, branchB);
            jdbc.update("INSERT INTO inventory_branch_users(company_id,branch_id,user_id) VALUES (?,?,?)", company, branchA, worker);
            jdbc.update("INSERT INTO catalog_products(company_id,id,name) VALUES (?,?,'Martillo')", company, product);
            jdbc.update("INSERT INTO catalog_items(company_id,id,product_id,sku,unit,price) VALUES (?,?,?,'MAR-1','unidad',1000)",
                company, item, product);
            jdbc.update("""
                INSERT INTO inventory_branch_items(company_id,branch_id,item_id,created_at,updated_at)
                VALUES (?,?,?,NOW(),NOW()),(?,?,?,NOW(),NOW())
                """, company, branchA, item, company, branchB, item);
            jdbc.update("INSERT INTO inventory_balances(company_id,location_id,item_id,quantity) VALUES (?,'principal',?,20),(?,'loc-b',?,20)",
                company, item, company, item);
            var admin = new StocklyPrincipal(owner, company, 0, 0, "COMPANY_ADMIN", false);
            var seller = new StocklyPrincipal(worker, company, 0, 0, "WORKER", false);
            var stranger = new StocklyPrincipal(outsider, other, 0, 0, "COMPANY_ADMIN", false);

            // Medios de pago: los cuatro activos por omisión, al menos uno activo y cambio auditado.
            assertThat(payments.methods(admin)).allMatch(SalePaymentService.MethodState::active).hasSize(4);
            assertCode(() -> payments.updateMethods(admin, Map.of("CASH", false, "CARD", false, "SINPE", false, "TRANSFER", false)),
                "SALE_PAYMENT_INVALID");
            assertCode(() -> payments.updateMethods(seller, Map.of("CASH", true, "CARD", true, "SINPE", true, "TRANSFER", false)),
                "FORBIDDEN");
            payments.updateMethods(admin, Map.of("CASH", true, "CARD", true, "SINPE", true, "TRANSFER", false));
            assertThat(payments.methods(admin)).filteredOn(m -> !m.active()).extracting(SalePaymentService.MethodState::method)
                .containsExactly("TRANSFER");
            assertThat(payments.methods(stranger)).allMatch(SalePaymentService.MethodState::active);
            assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM identity_audit_events WHERE company_id=? AND action_key='PAYMENT_METHODS_UPDATED'
                """, Integer.class, company)).isEqualTo(1);

            // Cobro rechazado: sin pagos, con exceso, insuficiente o con un medio inactivo. No descuenta existencias.
            assertCode(() -> sales.confirm(admin, sale(id(), branchA, 3, List.of())), "SALE_PAYMENT_INVALID");
            assertCode(() -> sales.confirm(admin, sale(id(), branchA, 3, List.of(pay("CARD", "3000.01", null)))), "SALE_PAYMENT_INVALID");
            assertCode(() -> sales.confirm(admin, sale(id(), branchA, 3, List.of(pay("CASH", "2999.99", null)))), "SALE_PAYMENT_INVALID");
            assertCode(() -> sales.confirm(admin, sale(id(), branchA, 3, List.of(pay("CASH", "10.001", null)))), "SALE_PAYMENT_INVALID");
            var inactive = catchThrowableOfType(IdentityFailure.class,
                () -> sales.confirm(admin, sale(id(), branchA, 3, List.of(pay("TRANSFER", "3000", "T-1")))));
            assertThat(inactive.code).isEqualTo("SALE_PAYMENT_METHOD_INACTIVE");
            assertThat(inactive.fields).containsKey("payments.0");
            assertThat(balance(company, "principal", item)).isEqualByComparingTo("20");

            // Pago mixto: ₡1.000 tarjeta + ₡2.500 efectivo sobre ₡3.000 da ₡500 de vuelto y aplica ₡2.000 en efectivo.
            String key = id();
            var mixed = sale(key, branchA, 3, List.of(pay("CARD", "1000", " AUT 551 "), pay("CASH", "2500", null)));
            var first = sales.confirm(admin, mixed);
            assertThat(first.sale().total()).isEqualByComparingTo("3000");
            assertThat(first.payments()).extracting(SalePaymentService.Payment::method).containsExactly("CARD", "CASH");
            assertThat(first.payments().get(0).reference()).isEqualTo("AUT 551");
            assertThat(first.payments().get(1).amount()).isEqualByComparingTo("2000");
            assertThat(first.payments().get(1).received()).isEqualByComparingTo("2500");
            assertThat(balance(company, "principal", item)).isEqualByComparingTo("17");
            // Reintento idéntico: mismo folio y un solo cobro. Con otros pagos: conflicto.
            assertThat(sales.confirm(admin, mixed)).isEqualTo(first);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sale_payments WHERE company_id=?", Integer.class, company)).isEqualTo(2);
            assertCode(() -> sales.confirm(admin, sale(key, branchA, 3, List.of(pay("CASH", "3000", null)))), "SALE_CONFLICT");

            // Venta anterior a STK-012: sin cobro, se anula sin devolución.
            var legacy = sales.confirm(admin, sale(id(), branchA, 1, List.of(pay("CASH", "1000", null))));
            jdbc.update("DELETE FROM sale_payments WHERE company_id=? AND sale_id=?", company, legacy.movement().id());
            assertThat(sales.list(admin, "", "", "none", "", 0).entries()).extracting(SaleService.SaleRow::folio)
                .containsExactly(legacy.movement().folio());
            assertCode(() -> sales.reverse(admin, legacy.movement().id(), "Prueba", List.of(refund("CASH", "1000", null))),
                "SALE_PAYMENT_INVALID");
            assertThat(sales.reverse(admin, legacy.movement().id(), "Venta anterior", List.of()).refund().lines()).isEmpty();

            // Otra sucursal, solo con tarjeta.
            var north = sales.confirm(admin, sale(id(), branchB, 1, List.of(pay("CARD", "1000", null))));
            assertThat(north.payments()).hasSize(1);

            // Anulación con devolución exacta; se rechaza sin motivo, con monto distinto o con un medio no permitido.
            String saleId = first.movement().id();
            assertCode(() -> sales.reverse(admin, saleId, "Cliente desistió", List.of()), "SALE_PAYMENT_INVALID");
            assertCode(() -> sales.reverse(admin, saleId, "Cliente desistió", List.of(refund("CASH", "2999", null))), "SALE_PAYMENT_INVALID");
            assertCode(() -> sales.reverse(admin, saleId, "Cliente desistió", List.of(refund("TRANSFER", "3000", null))), "SALE_PAYMENT_INVALID");
            assertCode(() -> sales.reverse(admin, saleId, " ", List.of(refund("CASH", "3000", null))), "SALE_INVALID");
            assertThat(balance(company, "principal", item)).isEqualByComparingTo("17");
            var voided = sales.reverse(admin, saleId, "Cliente desistió",
                List.of(refund("CASH", "2000", "ignorada"), refund("SINPE", "1000", "94821733")));
            assertThat(voided.refund().lines()).extracting(SalePaymentService.Refund::method).containsExactly("CASH", "SINPE");
            assertThat(voided.refund().lines().get(0).reference()).isNull();
            assertThat(voided.refund().lines().get(1).reference()).isEqualTo("94821733");
            assertThat(voided.refund().actor()).isEqualTo("Persona " + owner.substring(0, 4));
            assertThat(balance(company, "principal", item)).isEqualByComparingTo("20");
            assertThat(sales.list(admin, first.movement().folio(), "", "voided", "", 0).entries()).hasSize(1);
            assertThat(sales.list(admin, "", "", "paid", "", 0).entries()).extracting(SaleService.SaleRow::folio)
                .containsExactly(north.movement().folio());

            // Resumen: cobros por fecha de venta y devoluciones por fecha de anulación.
            var all = payments.summary(admin, "", "", "");
            assertThat(net(all, "CASH")).isEqualByComparingTo("0");
            assertThat(collected(all, "CARD")).isEqualByComparingTo("2000");
            assertThat(net(all, "SINPE")).isEqualByComparingTo("-1000");
            assertThat(all.sales()).isEqualTo(2);
            assertThat(all.refunds()).isEqualTo(1);
            assertThat(all.days()).hasSize(1);
            assertThat(all.days().getFirst().total()).isEqualByComparingTo("1000");
            // La persona con acceso solo a Centro no ve Norte.
            var scoped = payments.summary(seller, "", "", "");
            assertThat(collected(scoped, "CARD")).isEqualByComparingTo("1000");
            assertThat(scoped.sales()).isEqualTo(1);
            assertCode(() -> payments.summary(seller, branchB, "", ""), "BRANCH_NOT_FOUND");
            assertThat(collected(payments.summary(admin, branchB, "", ""), "CARD")).isEqualByComparingTo("1000");
            assertCode(() -> payments.summary(admin, "", "2999-01-01", ""), "SALE_PAYMENT_INVALID");
            assertCode(() -> payments.summary(admin, "", "2026-01-02", "2026-01-01"), "SALE_PAYMENT_INVALID");
            // Otra empresa no ve cobros ni ventas ajenas.
            assertThat(payments.summary(stranger, "", "", "").sales()).isZero();
            assertCode(() -> payments.summary(stranger, branchA, "", ""), "BRANCH_NOT_FOUND");
            assertCode(() -> sales.get(stranger, saleId), "SALE_NOT_FOUND");

            // Permisos HTTP: solo el administrador configura; lectura de ventas consulta el resumen.
            var reader = new UsernamePasswordAuthenticationToken(seller, null, List.of(new SimpleGrantedAuthority("MODULE_SALES_READ")));
            var owners = new UsernamePasswordAuthenticationToken(admin, null, List.of(new SimpleGrantedAuthority("ROLE_COMPANY_ADMIN")));
            String body = "{\"CASH\":true,\"CARD\":true,\"SINPE\":true,\"TRANSFER\":true}";
            mvc.perform(put("/api/sales/payment-methods").with(authentication(reader)).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isForbidden());
            mvc.perform(put("/api/sales/payment-methods").with(authentication(owners)).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$[3].active").value(true));
            mvc.perform(get("/api/sales/payment-methods").with(authentication(reader))).andExpect(status().isOk());
            mvc.perform(get("/api/sales/payments/summary").with(authentication(reader)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sales").value(1));
            mvc.perform(get("/api/sales/" + first.movement().folio()).with(authentication(owners)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.payments[1].received").value(2500))
                .andExpect(jsonPath("$.refund.lines.length()").value(2));
            mvc.perform(post("/api/sales/" + north.movement().id() + "/void").with(authentication(reader)).with(csrf())
                .contentType("application/json").content("{\"reason\":\"x\",\"refunds\":[]}")).andExpect(status().isForbidden());
        } finally {
            for (String id : List.of(company, other)) {
                jdbc.update("DELETE FROM identity_audit_events WHERE company_id=?", id);
                jdbc.update("DELETE FROM users WHERE company_id=?", id);
                jdbc.update("DELETE FROM company_roles WHERE company_id=?", id);
                jdbc.update("DELETE FROM companies WHERE id=?", id);
            }
        }
    }

    @Test void returnsPartiallyRestocksAndReusesTheFolioWithoutDuplicatingTheRefund() throws Exception {
        String company = id(), foreign = id(), owner = id(), worker = id(), outsider = id(), branch = id(), product = id();
        item = id();
        company(company, "Prueba STK-015");
        company(foreign, "Otra STK-015");
        try {
            user(owner, company, "COMPANY_ADMIN");
            user(worker, company, "WORKER");
            user(outsider, foreign, "COMPANY_ADMIN");
            jdbc.update("INSERT INTO inventory_locations(company_id,id,name) VALUES (?,'principal','Principal')", company);
            jdbc.update("""
                INSERT INTO inventory_branches(company_id,id,name,code,address,phone,contact_email,manager_name,location_id,created_at,updated_at)
                VALUES (?,?,'Centro','CENTRO','','','','','principal',NOW(),NOW())
                """, company, branch);
            jdbc.update("INSERT INTO catalog_products(company_id,id,name) VALUES (?,?,'Guantes')", company, product);
            jdbc.update("INSERT INTO catalog_items(company_id,id,product_id,sku,unit,price) VALUES (?,?,?,'GUA-1','unidad',1000)",
                company, item, product);
            jdbc.update("INSERT INTO inventory_branch_items(company_id,branch_id,item_id,created_at,updated_at) VALUES (?,?,?,NOW(),NOW())",
                company, branch, item);
            jdbc.update("INSERT INTO inventory_balances(company_id,location_id,item_id,quantity) VALUES (?,'principal',?,20)", company, item);
            var admin = new StocklyPrincipal(owner, company, 0, 0, "COMPANY_ADMIN", false);
            var seller = new StocklyPrincipal(worker, company, 0, 0, "WORKER", false);
            var foreignAdmin = new StocklyPrincipal(outsider, foreign, 0, 0, "COMPANY_ADMIN", false);
            var sale = sales.confirm(admin, sale(id(), branch, 4, List.of(pay("CARD", "2000", "compra"), pay("CASH", "2000", null))));
            String saleId = sale.movement().id();
            var selection = new SaleReturnService.LineInput(item, new BigDecimal("3"), new BigDecimal("2"));
            var first = new SaleReturnService.Confirmation(id(), "Producto dañado", null, List.of(selection), new BigDecimal("3000"),
                List.of(refund("CARD", "2000", "reintegro"), refund("CASH", "1000", null)));
            assertCode(() -> returns.context(seller, saleId), "SALE_NOT_FOUND");
            assertCode(() -> returns.context(foreignAdmin, saleId), "SALE_NOT_FOUND");
            jdbc.update("INSERT INTO inventory_branch_users(company_id,branch_id,user_id) VALUES (?,?,?)", company, branch, worker);
            var reader = new UsernamePasswordAuthenticationToken(seller, null, List.of(new SimpleGrantedAuthority("MODULE_SALES_READ")));
            mvc.perform(post("/api/sales/" + saleId + "/returns").with(authentication(reader)).with(csrf())
                .contentType("application/json").content("{}")).andExpect(status().isForbidden());

            jdbc.update("UPDATE catalog_items SET price=1100 WHERE company_id=? AND id=?", company, item);
            assertThat(returns.preview(admin, saleId, new SaleReturnService.Selection(List.of(selection))).refund()).isEqualByComparingTo("3000");
            assertCode(() -> returns.confirm(admin, saleId, new SaleReturnService.Confirmation(id(), "Producto dañado", null,
                List.of(selection), new BigDecimal("3000"), List.of(refund("CASH", "1", null)))), "SALE_PAYMENT_INVALID");
            assertThat(balance(company, "principal", item)).isEqualByComparingTo("16");

            var receipt = returns.confirm(admin, saleId, first);
            assertThat(receipt.folio()).isEqualTo("DEV-0001");
            assertThat(returns.confirm(admin, saleId, first)).isEqualTo(receipt);
            assertCode(() -> returns.confirm(admin, saleId, new SaleReturnService.Confirmation(first.requestKey(), "Producto incorrecto",
                null, first.lines(), first.expectedRefund(), first.refunds())), "RETURN_KEY_CONFLICT");
            assertThat(balance(company, "principal", item)).isEqualByComparingTo("18");
            var historyRows = history.list(admin, "DEV-0001", item, "RETURN", "", "", "", 0).rows();
            assertThat(historyRows).hasSize(1);
            assertThat(historyRows.getFirst().quantity()).isEqualByComparingTo("2");
            assertThat(sales.list(admin, "", "partial", "", 0).entries()).extracting(SaleService.SaleRow::folio)
                .containsExactly(sale.movement().folio());
            assertCode(() -> sales.reverse(admin, saleId, "No repetir"), "SALE_CONFLICT");

            var last = new SaleReturnService.LineInput(item, BigDecimal.ONE, BigDecimal.ZERO);
            var refund = List.of(refund("CASH", "1000", null));
            var attempt = new SaleReturnService.Confirmation(id(), "Producto incorrecto", null, List.of(last), new BigDecimal("1000"), refund);
            var competitor = new SaleReturnService.Confirmation(id(), attempt.reason(), null, attempt.lines(), attempt.expectedRefund(), refund);
            try (var workers = java.util.concurrent.Executors.newFixedThreadPool(2)) {
                var a = workers.submit(() -> returnResult(admin, saleId, attempt));
                var b = workers.submit(() -> returnResult(admin, saleId, competitor));
                assertThat(List.of(a.get(10, java.util.concurrent.TimeUnit.SECONDS), b.get(10, java.util.concurrent.TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("DEV-0002", "RETURN_LINES");
            }
            assertThat(returns.get(admin, saleId, "DEV-0002").movementId()).isNull();
            assertThat(balance(company, "principal", item)).isEqualByComparingTo("18");
            assertThat(sales.list(admin, "", "complete", "", 0).entries()).extracting(SaleService.SaleRow::folio)
                .containsExactly(sale.movement().folio());
            assertThat(payments.summary(admin, branch, "", "").refunds()).isEqualTo(2);
            assertThat(net(payments.summary(admin, branch, "", ""), "CARD")).isEqualByComparingTo("0");
            assertThat(net(payments.summary(admin, branch, "", ""), "CASH")).isEqualByComparingTo("0");

            jdbc.update("UPDATE catalog_items SET unit='kg',price=0.01 WHERE company_id=? AND id=?", company, item);
            var fractionSale = sales.confirm(admin, new SaleService.SaleInput(id(), branch, null, null, null,
                List.of(new SaleService.LineInput(item, new BigDecimal("1.5"), new BigDecimal("0.01"))),
                List.of(pay("CASH", "0.02", null))));
            jdbc.update("DELETE FROM sale_payments WHERE company_id=? AND sale_id=?", company, fractionSale.movement().id());
            var fractional = new SaleReturnService.LineInput(item, new BigDecimal("0.5"), BigDecimal.ZERO);
            var amounts = new java.util.ArrayList<BigDecimal>();
            for (int i = 0; i < 3; i++) {
                var result = returns.confirm(admin, fractionSale.movement().id(), new SaleReturnService.Confirmation(id(),
                    "El cliente cambió de opinión", null, List.of(fractional), BigDecimal.ZERO, List.of()));
                assertThat(result.refunds()).isEmpty();
                amounts.add(result.amount());
            }
            assertThat(amounts).containsExactly(new BigDecimal("0.01"), new BigDecimal("0.00"), new BigDecimal("0.01"));
        } finally {
            for (String ownerCompany : List.of(company, foreign)) {
                jdbc.update("DELETE FROM users WHERE company_id=?", ownerCompany);
                jdbc.update("DELETE FROM company_roles WHERE company_id=?", ownerCompany);
                jdbc.update("DELETE FROM companies WHERE id=?", ownerCompany);
            }
        }
    }

    private String returnResult(StocklyPrincipal actor, String saleId, SaleReturnService.Confirmation input) {
        try { return returns.confirm(actor, saleId, input).folio(); }
        catch (IdentityFailure failure) { return failure.code; }
    }

    private void company(String id, String name) {
        jdbc.update("""
            INSERT INTO companies(id,commercial_name,legal_name,identification_type,identification_normalized,
              contact_email,status,created_at,updated_at)
            VALUES (?, ?, ?, 'LEGAL', ?, 'test@example.com', 'ACTIVE', NOW(), NOW())
            """, id, name, name, legalId());
    }

    private void user(String id, String company, String role) {
        String roleId = null;
        if ("WORKER".equals(role)) {
            roleId = id();
            jdbc.update("INSERT INTO company_roles(id,company_id,name,created_at,updated_at) VALUES (?,?,'Cajero',NOW(),NOW())", roleId, company);
        }
        jdbc.update("""
            INSERT INTO users(id,company_id,full_name,username_normalized,email_normalized,password_hash,must_change_password,role,role_id)
            VALUES (?,?,?,?,?,'unused',FALSE,?,?)
            """, id, company, "Persona " + id.substring(0, 4), id.substring(0, 30), id + "@example.com", role, roleId);
    }

    private SaleService.SaleInput sale(String key, String branch, int units, List<SalePaymentService.PaymentInput> paid) {
        return new SaleService.SaleInput(key, branch, null, null, null,
            List.of(new SaleService.LineInput(item, BigDecimal.valueOf(units), new BigDecimal("1000"))), paid);
    }

    private static SalePaymentService.PaymentInput pay(String method, String amount, String reference) {
        return new SalePaymentService.PaymentInput(method, new BigDecimal(amount), reference);
    }

    private static SalePaymentService.RefundInput refund(String method, String amount, String reference) {
        return new SalePaymentService.RefundInput(method, new BigDecimal(amount), reference);
    }

    private static BigDecimal net(SalePaymentService.Summary summary, String method) {
        return summary.methods().stream().filter(m -> m.method().equals(method)).findFirst().orElseThrow().net();
    }

    private static BigDecimal collected(SalePaymentService.Summary summary, String method) {
        return summary.methods().stream().filter(m -> m.method().equals(method)).findFirst().orElseThrow().collected();
    }

    private static void assertCode(ThrowingCallable call, String code) {
        assertThat(catchThrowableOfType(IdentityFailure.class, call)).isNotNull().extracting(failure -> failure.code).isEqualTo(code);
    }

    private BigDecimal balance(String company, String location, String item) {
        return jdbc.queryForObject("SELECT quantity FROM inventory_balances WHERE company_id=? AND location_id=? AND item_id=?",
            BigDecimal.class, company, location, item);
    }

    private static String id() { return UUID.randomUUID().toString(); }

    /** Cédula jurídica de prueba (10 dígitos), como exige STK-004 y la clave de Hacienda. */
    private static String legalId() { return "3" + String.format("%09d", java.util.concurrent.ThreadLocalRandom.current().nextLong(1_000_000_000L)); }
}
