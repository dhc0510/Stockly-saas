package com.stockly.inventory;

import com.stockly.identity.IdentityFailure;
import com.stockly.identity.StocklyPrincipal;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "STOCKLY_SALES_TEST_DATABASE", matches = "true")
class SaleLocalIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired SaleService sales;
    @Autowired OfflineSaleService offlineSales;
    @Autowired InventoryService inventory;
    @Autowired ReceiptService receipts;
    @Autowired StockMovementService movements;
    @Autowired InventoryHistoryService history;
    @Autowired JdbcTemplate jdbc;

    @Test void rejectsPriceChangeThenConfirmsRetriesAndReversesInOriginalBranch() throws Exception {
        String company = id(), owner = id(), branch = id(), principalBranch = id(), product = id(), item = id();
        jdbc.update("""
            INSERT INTO companies(id,commercial_name,legal_name,identification_type,identification_normalized,
              contact_email,status,created_at,updated_at)
            VALUES (?, 'Prueba STK-011', 'Prueba STK-011', 'LEGAL', ?, 'test@example.com', 'ACTIVE', NOW(), NOW())
            """, company, legalId());
        try {
            jdbc.update("""
                INSERT INTO users(id,company_id,full_name,username_normalized,email_normalized,password_hash,must_change_password,role)
                VALUES (?,?,'Ana Pruebas',?,?,'unused',FALSE,'COMPANY_ADMIN')
                """, owner, company, owner.substring(0, 30), owner + "@example.com");

            jdbc.update("""
                INSERT INTO inventory_branches(company_id,id,name,code,address,phone,contact_email,manager_name,
                  created_at,updated_at)
                VALUES (?,?,'Norte','NORTE','','','','',NOW(),NOW()),
                       (?,?,'Centro','CENTRO','','','','',NOW(),NOW())
                """, company, branch, company, principalBranch);
            jdbc.update("INSERT INTO catalog_products(company_id,id,name) VALUES (?,?,'Clavos')", company, product);
            jdbc.update("""
                INSERT INTO catalog_items(company_id,id,product_id,sku,unit,price) VALUES (?,?,?,'CLAVOS','kg',1234.50)
                """, company, item, product);
            jdbc.update("""
                INSERT INTO inventory_branch_items(company_id,branch_id,item_id,created_at,updated_at)
                VALUES (?,?,?,NOW(),NOW())
                """, company, branch, item);
            jdbc.update("INSERT INTO inventory_locations(company_id,id,name) VALUES (?,'principal','Principal')", company);
            jdbc.update("INSERT INTO inventory_balances(company_id,location_id,item_id,quantity) VALUES (?,'principal',?,10)",
                company, item);
            var actor = new StocklyPrincipal(owner, company, 0, 0, "COMPANY_ADMIN", false);
            assertThat(inventory.locationSetup(actor).needsPrincipal()).isTrue();
            assertThatThrownBy(() -> inventory.configureLocations(actor, null))
                .isInstanceOf(IdentityFailure.class).hasMessageContaining("Selecciona la sucursal");
            inventory.configureLocations(actor, principalBranch);
            assertThat(balance(company, InventoryLedger.PRINCIPAL, item)).isEqualByComparingTo("10");
            var received = List.of(new ReceiptService.LineInput(item, new BigDecimal("1.250")));
            assertThatThrownBy(() -> receipts.confirm(actor, new ReceiptService.ReceiptInput(id(), null, "RECEIPT", null, null, received)))
                .isInstanceOf(IdentityFailure.class).hasMessageContaining("Selecciona una sucursal");
            var receipt = receipts.confirm(actor, new ReceiptService.ReceiptInput(id(), branch, "RECEIPT", null, null, received));
            assertThat(receipt.movement().location()).isEqualTo("Norte");
            assertThat(balance(company, branch, item)).isEqualByComparingTo("1.250");
            var outflow = movements.outflow(actor, new StockMovementService.OutflowInput(
                id(), branch, "DAMAGE", null, null, List.of(new StockMovementService.OutflowLine(item, new BigDecimal("0.100")))));
            assertThat(balance(company, branch, item)).isEqualByComparingTo("1.150");
            movements.reverse(actor, outflow.movement().id(), "Corregir salida");
            var adjustment = movements.adjustment(actor, new StockMovementService.AdjustmentInput(
                id(), branch, "COUNT", null, null,
                List.of(new StockMovementService.AdjustmentLine(item, new BigDecimal("1.300"), new BigDecimal("1.250")))));
            assertThat(balance(company, branch, item)).isEqualByComparingTo("1.300");
            movements.reverse(actor, adjustment.movement().id(), "Corregir ajuste");
            assertThat(balance(company, branch, item)).isEqualByComparingTo("1.250");
            assertThat(inventory.items(actor, branch, "", "", "ALL", "NAME").getFirst().available())
                .isEqualByComparingTo("1.250");
            assertThatThrownBy(() -> inventory.deactivateBranch(actor, branch))
                .isInstanceOf(IdentityFailure.class).hasMessageContaining("Reasigna las existencias");
            String key = id();
            assertThatThrownBy(() -> sales.confirm(actor, input(key, branch, item, "1000")))
                .isInstanceOf(IdentityFailure.class).hasMessageContaining("Hay precios nuevos");
            assertThat(balance(company, branch, item)).isEqualByComparingTo("1.250");

            var input = input(key, branch, item, "1234.50");
            var sale = sales.confirm(actor, input);
            assertThat(sale.movement().folio()).isEqualTo("VTA-0001");
            assertThat(sale.sale().total()).isEqualByComparingTo("154.31");
            assertThat(sale.sale().customerPhone()).isEqualTo("+506 2222 2222");
            assertThat(sale.sale().customerEmail()).isEqualTo("ana@example.invalid");
            assertThat(sales.get(actor, "VTA-0001")).isEqualTo(sale);
            assertThat(sales.list(actor, "VTA-", "confirmed", branch, 0).entries()).hasSize(1);
            var historyRows = history.list(actor, "VTA-0001", item, "SALE", "", "", "", 0).rows();
            assertThat(historyRows).hasSize(1);
            assertThat(historyRows.getFirst().balanceAfter()).isEqualByComparingTo("1.125");
            var admin = new UsernamePasswordAuthenticationToken(actor, null, List.of(new SimpleGrantedAuthority("ROLE_COMPANY_ADMIN")));
            var restricted = new StocklyPrincipal(owner, company, 0, 0, "WORKER", false);
            var reader = new UsernamePasswordAuthenticationToken(restricted, null, List.of(new SimpleGrantedAuthority("MODULE_SALES_READ")));
            mvc.perform(get("/api/sales/branches").with(authentication(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.branches.length()").value(2));
            mvc.perform(get("/api/sales/VTA-0001").with(authentication(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sale.branchName").value("Norte"));
            mvc.perform(get("/api/sales/VTA-0001").with(authentication(reader))).andExpect(status().isNotFound());
            mvc.perform(post("/api/sales").with(authentication(reader)).with(csrf()).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
            assertThat(balance(company, branch, item)).isEqualByComparingTo("1.125");

            jdbc.update("UPDATE catalog_items SET price=1500 WHERE company_id=? AND id=?", company, item);
            assertThat(sales.confirm(actor, input)).isEqualTo(sale);
            assertThat(balance(company, branch, item)).isEqualByComparingTo("1.125");

            var reversed = sales.reverse(actor, sale.movement().id(), "Error al cobrar",
                List.of(new SalePaymentService.RefundInput("CASH", new BigDecimal("154.31"), null)));
            assertThat(reversed.movement().voidReason()).isEqualTo("Error al cobrar");
            assertThat(balance(company, InventoryLedger.PRINCIPAL, item)).isEqualByComparingTo("10");
            assertThat(balance(company, branch, item)).isEqualByComparingTo("1.250");
            assertThatThrownBy(() -> sales.reverse(actor, sale.movement().id(), "Segundo intento"))
                .isInstanceOf(IdentityFailure.class).hasMessageContaining("ya fue anulada");
            try (var workers = Executors.newFixedThreadPool(2)) {
                var first = workers.submit(() -> confirmLastUnit(actor, branch, item));
                var second = workers.submit(() -> confirmLastUnit(actor, branch, item));
                assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("VTA-0002", "SALE_LINE_ERROR");
            }
            assertThat(balance(company, branch, item)).isEqualByComparingTo("0.250");

            var snapshot = offlineSales.prepare(actor, branch);
            var savedItem = snapshot.items().getFirst();
            var proofs = java.util.Map.of(item, savedItem.proof());
            var soldAt = java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
            jdbc.update("UPDATE catalog_items SET price=1700 WHERE company_id=? AND id=?", company, item);

            var pending = new OfflineSaleService.Input(snapshot.id(), soldAt,
                input(id(), branch, item, "1500"), proofs);
            var synced = offlineSales.sync(actor, pending);
            assertThat(synced.state()).isEqualTo("SYNCED");
            var offlineSale = sales.get(actor, synced.folio());
            assertThat(offlineSale.sale().total()).isEqualByComparingTo("187.50");
            assertThat(offlineSale.sale().offlineSoldAt()).isEqualTo(soldAt);
            assertThat(offlineSale.sale().offlineReference()).isEqualTo(synced.reference());
            // STK-013 §13: al sincronizar se emite el tiquete con situación 3 («sin internet») y la hora real de la venta.
            assertThat(offlineSale.fiscal()).hasSize(1);
            var offlineDoc = offlineSale.fiscal().getFirst();
            assertThat(offlineDoc.docType()).isEqualTo("04");
            assertThat(offlineDoc.situation()).isEqualTo("3");
            assertThat(offlineDoc.issuedAt()).isEqualTo(soldAt);
            assertThat(offlineDoc.key().substring(3, 9))
                .isEqualTo(java.time.format.DateTimeFormatter.ofPattern("ddMMyy").format(soldAt.atZone(java.time.ZoneId.of("America/Costa_Rica"))));
            assertThat(offlineDoc.lateSubmission()).isFalse();
            assertThat(offlineDoc.status()).isEqualTo("PENDING");
            assertThat(offlineSales.sync(actor, pending).folio()).isEqualTo(synced.folio());
            assertThat(balance(company, branch, item)).isEqualByComparingTo("0.125");

            var last = new OfflineSaleService.Input(snapshot.id(), soldAt, input(id(), branch, item, "1500"), proofs);
            assertThat(offlineSales.sync(actor, last).state()).isEqualTo("SYNCED");
            var excess = new OfflineSaleService.Input(snapshot.id(), soldAt, input(id(), branch, item, "1500"), proofs);
            var conflict = offlineSales.sync(actor, excess);
            assertThat(conflict.state()).isEqualTo("REVIEW");
            assertThat(conflict.issue().fields()).containsKey(item);
            assertThat(offlineSales.retry(actor, conflict.id()).state()).isEqualTo("REVIEW");
            var closed = offlineSales.close(actor, conflict.id(), new OfflineSaleService.Closure("Sin existencias", "Efectivo"));
            assertThat(closed.state()).isEqualTo("CLOSED");
            assertThat(closed.refundDetail()).isEqualTo("Efectivo");
            // En revisión o cerrada, la venta sin internet no emite: solo las dos sincronizadas tienen comprobante.
            assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM fiscal_documents f JOIN sales s ON s.company_id=f.company_id AND s.id=f.sale_id
                WHERE f.company_id=? AND s.offline_reference IS NOT NULL""", Integer.class, company)).isEqualTo(2);
            assertThat(balance(company, branch, item)).isEqualByComparingTo("0");
            jdbc.update("DELETE FROM inventory_branch_items WHERE company_id=? AND branch_id=?", company, branch);
            assertThatThrownBy(() -> inventory.deleteBranch(actor, branch))
                .isInstanceOf(IdentityFailure.class).hasMessageContaining("existencias o historial");
        } finally {
            jdbc.update("DELETE FROM users WHERE company_id=?", company);
            jdbc.update("DELETE FROM company_roles WHERE company_id=?", company);
            jdbc.update("DELETE FROM companies WHERE id=?", company);
        }
    }

    private String confirmLastUnit(StocklyPrincipal actor, String branch, String item) {
        var line = new SaleService.LineInput(item, BigDecimal.ONE, new BigDecimal("1500"));
        try {
            return sales.confirm(actor, new SaleService.SaleInput(id(), branch, null, null, null, List.of(line),
                List.of(new SalePaymentService.PaymentInput("CASH", new BigDecimal("1500"), null)))).movement().folio();
        } catch (IdentityFailure failure) {
            return failure.code;
        }
    }


    private static SaleService.SaleInput input(String key, String branch, String item, String price) {
        var line = new SaleService.LineInput(item, new BigDecimal("0.125"), new BigDecimal(price));
        return new SaleService.SaleInput(key, branch, null, "+506 2222 2222", "ana@example.invalid", List.of(line),
            List.of(new SalePaymentService.PaymentInput("CASH", new BigDecimal("200"), null)));
    }

    private BigDecimal balance(String company, String branch, String item) {
        return jdbc.queryForObject("SELECT quantity FROM inventory_balances WHERE company_id=? AND location_id=? AND item_id=?",
            BigDecimal.class, company, branch, item);
    }

    private static String id() { return UUID.randomUUID().toString(); }

    /** Cédula jurídica de prueba (10 dígitos), como exige STK-004 y la clave de Hacienda. */
    private static String legalId() { return "3" + String.format("%09d", java.util.concurrent.ThreadLocalRandom.current().nextLong(1_000_000_000L)); }
}
