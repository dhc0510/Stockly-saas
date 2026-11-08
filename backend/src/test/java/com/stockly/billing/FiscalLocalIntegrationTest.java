package com.stockly.billing;

import com.stockly.identity.IdentityFailure;
import com.stockly.identity.StocklyPrincipal;
import com.stockly.inventory.SalePaymentService;
import com.stockly.inventory.SaleService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** STK-013 sobre MySQL real con Hacienda simulada: emisión, envío sin duplicar, rechazo, nota de crédito y seguridad. */
@SpringBootTest(properties = {
    "stockly.billing.key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
    "stockly.billing.system-provider-id=3101000000",
    "stockly.billing.dispatch-enabled=false"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "STOCKLY_SALES_TEST_DATABASE", matches = "true")
class FiscalLocalIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired SaleService sales;
    @Autowired BillingSettingsService settings;
    @Autowired FiscalDispatcher dispatcher;
    @Autowired FiscalDocumentService documents;
    @Autowired FiscalArchive archive;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean HaciendaClient hacienda;

    @Test void issuesSendsWithoutDuplicatingCorrectsAndCreditsWithinCompanyScope() throws Exception {
        String company = id(), owner = id(), worker = id(), branch = id(), product = id(), item = id(), bare = id();
        jdbc.update("""
            INSERT INTO companies(id,commercial_name,legal_name,identification_type,identification_normalized,contact_email,status,created_at,updated_at)
            VALUES (?, 'Ferretería STK-013', 'Ferretería STK-013 S.A.', 'LEGAL', ?, 'test@example.com', 'ACTIVE', NOW(), NOW())
            """, company, "31" + String.format("%08d", Math.abs(company.hashCode() % 100_000_000)));
        try {
            jdbc.update("INSERT INTO users(id,company_id,full_name,username_normalized,email_normalized,password_hash,must_change_password,role) VALUES (?,?,'Ana',?,?,'x',FALSE,'COMPANY_ADMIN')",
                owner, company, owner.substring(0, 30), owner + "@example.com");
            String role = id();
            jdbc.update("INSERT INTO company_roles(id,company_id,name,created_at,updated_at) VALUES (?,?,'Cajero',NOW(),NOW())", role, company);
            jdbc.update("INSERT INTO users(id,company_id,full_name,username_normalized,email_normalized,password_hash,must_change_password,role,role_id) VALUES (?,?,'Luis',?,?,'x',FALSE,'WORKER',?)",
                worker, company, worker.substring(0, 30), worker + "@example.com", role);
            jdbc.update("INSERT INTO inventory_locations(company_id,id,name) VALUES (?,'principal','Principal')", company);
            jdbc.update("""
                INSERT INTO inventory_branches(company_id,id,name,code,address,phone,contact_email,manager_name,location_id,created_at,updated_at)
                VALUES (?,?,'Central','CENTRAL','','','','','principal',NOW(),NOW())
                """, company, branch);
            jdbc.update("INSERT INTO inventory_branch_users(company_id,branch_id,user_id) VALUES (?,?,?)", company, branch, worker);
            jdbc.update("INSERT INTO catalog_products(company_id,id,name) VALUES (?,?,'Café molido')", company, product);
            jdbc.update("INSERT INTO catalog_items(company_id,id,product_id,sku,unit,price,cabys_code,tax_rate_code) VALUES (?,?,?,'CAF','unidad',3800,'2391100000000','08')",
                company, item, product);
            jdbc.update("INSERT INTO catalog_items(company_id,id,product_id,variant_name,sku,unit,price) VALUES (?,?,?,'Sin CABYS','VAS','unidad',4500)",
                company, bare, product);
            jdbc.update("INSERT INTO inventory_branch_items(company_id,branch_id,item_id,created_at,updated_at) VALUES (?,?,?,NOW(),NOW()),(?,?,?,NOW(),NOW())",
                company, branch, item, company, branch, bare);
            jdbc.update("INSERT INTO inventory_balances(company_id,location_id,item_id,quantity) VALUES (?,'principal',?,50),(?,'principal',?,50)",
                company, item, company, bare);
            var admin = new StocklyPrincipal(owner, company, 0, 0, "COMPANY_ADMIN", false);
            var seller = new StocklyPrincipal(worker, company, 0, 0, "WORKER", false);

            // Configuración: solo el administrador; las credenciales se cifran y nunca se devuelven.
            byte[] key = getClass().getResourceAsStream("/billing/llave-prueba.p12").readAllBytes();
            assertThatThrownBy(() -> settings.get(seller)).isInstanceOf(IdentityFailure.class);
            assertThatThrownBy(() -> settings.saveCredentials(admin, new BillingSettingsService.CredentialsInput("cpj-api", "secreta", Base64.getEncoder().encodeToString(key), "999999")))
                .hasMessageContaining("PIN");
            var saved = settings.saveCredentials(admin, new BillingSettingsService.CredentialsInput("cpj-api", "secreta", Base64.getEncoder().encodeToString(key), "123456"));
            assertThat(saved.hasCredentials()).isTrue();
            assertThat(saved.keySubject()).contains("PRUEBA");
            byte[] stored = jdbc.queryForObject("SELECT api_password_enc FROM billing_settings WHERE company_id=?", byte[].class, company);
            assertThat(new String(stored, StandardCharsets.ISO_8859_1)).doesNotContain("secreta");
            settings.saveIssuer(admin, new BillingSettingsService.IssuerInput("475203", "1", "15", "01", "200 m norte de la iglesia", "facturas@ferreteria.test"));
            assertThat(settings.get(admin).branches().getFirst().fiscalCode()).isNull(); // Sucursal creada sin pasar por InventoryService.
            assertThatThrownBy(() -> settings.activateProduction(admin)).hasMessageContaining("requisitos");
            var owners = new UsernamePasswordAuthenticationToken(admin, null, List.of(new SimpleGrantedAuthority("ROLE_COMPANY_ADMIN")));
            var reader = new UsernamePasswordAuthenticationToken(seller, null, List.of(new SimpleGrantedAuthority("MODULE_SALES_READ")));
            mvc.perform(get("/api/billing/settings").with(authentication(owners))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secreta"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("cpj-api"))));
            mvc.perform(get("/api/billing/settings").with(authentication(reader))).andExpect(status().isForbidden());

            // Factura (cliente identificado) y tiquete (público general). La identificación se valida.
            assertThatThrownBy(() -> sales.confirm(admin, sale(branch, item, "02", "123", "Horizonte"))).hasMessageContaining("identificación");
            var invoiceSale = sales.confirm(admin, sale(branch, item, "02", "3-101-654321", "Comercial Horizonte S.A."));
            var ticketSale = sales.confirm(admin, sale(branch, item, null, null, null));
            assertThat(invoiceSale.fiscal()).singleElement().satisfies(doc -> {
                assertThat(doc.docType()).isEqualTo("01");
                assertThat(doc.status()).isEqualTo("PENDING");
                assertThat(doc.consecutive()).isEqualTo("00100001010000000001");
                assertThat(doc.key()).hasSize(50).startsWith("506");
            });
            assertThat(ticketSale.fiscal().getFirst().consecutive()).isEqualTo("00100001040000000001");
            assertThat(settings.get(admin).branches().getFirst().fiscalCode()).isEqualTo("001");

            // Envío: Hacienda recibe y luego acepta. La segunda consulta usa la misma clave.
            when(hacienda.submit(any(), any(), any())).thenReturn(new HaciendaClient.Result(HaciendaClient.Outcome.RECEIVED, null, null));
            when(hacienda.status(any(), any(), any())).thenReturn(new HaciendaClient.Result(HaciendaClient.Outcome.ACCEPTED, null,
                "<MensajeHacienda><Mensaje>1</Mensaje></MensajeHacienda>".getBytes(StandardCharsets.UTF_8)));
            dispatcher.runOnce(10);
            assertThat(fiscalStatus(invoiceSale)).isEqualTo("PROCESSING");
            due(company);
            dispatcher.runOnce(10);
            assertThat(fiscalStatus(invoiceSale)).isEqualTo("ACCEPTED");
            assertThat(fiscalStatus(ticketSale)).isEqualTo("ACCEPTED");
            verify(hacienda, times(2)).submit(any(), any(), any());
            String invoiceId = sales.get(admin, invoiceSale.movement().id()).fiscal().getFirst().id();
            mvc.perform(get("/api/sales/" + invoiceSale.movement().folio() + "/fiscal/" + invoiceId + "/xml").with(authentication(reader)))
                .andExpect(status().isOk()).andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(".xml")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("SigPolicyId")));
            mvc.perform(get("/api/sales/" + invoiceSale.movement().folio() + "/fiscal/" + invoiceId + "/pdf").with(authentication(reader)))
                .andExpect(status().isOk()).andExpect(content().contentType("application/pdf"));
            assertThat(sales.list(admin, "", "", "", "ACCEPTED", "01", "", 0).entries()).extracting(SaleService.SaleRow::folio)
                .containsExactly(invoiceSale.movement().folio());

            // Sin respuesta: no se duplica. El siguiente ciclo consulta la clave y, si no existe, reenvía la misma.
            reset(hacienda);
            var lost = sales.confirm(admin, sale(branch, item, null, null, null));
            when(hacienda.submit(any(), any(), any())).thenReturn(new HaciendaClient.Result(HaciendaClient.Outcome.RETRY, "Hacienda no respondió.", null));
            dispatcher.runOnce(10);
            assertThat(reason(lost)).contains("no respondió");
            due(company);
            when(hacienda.status(any(), any(), any())).thenReturn(new HaciendaClient.Result(HaciendaClient.Outcome.NOT_FOUND, null, null));
            when(hacienda.submit(any(), any(), any())).thenReturn(new HaciendaClient.Result(HaciendaClient.Outcome.RECEIVED, null, null));
            dispatcher.runOnce(10);
            var keys = org.mockito.ArgumentCaptor.forClass(HaciendaClient.Submission.class);
            verify(hacienda, times(2)).submit(any(), any(), keys.capture());
            assertThat(keys.getAllValues()).extracting(HaciendaClient.Submission::key).containsOnly(key(lost));

            // Falta CABYS: queda pendiente con el motivo, sin enviar.
            reset(hacienda);
            var missing = sales.confirm(admin, sale(branch, bare, "4500", null, null, null));
            dispatcher.runOnce(10);
            assertThat(fiscalStatus(missing)).isEqualTo("PENDING");
            assertThat(reason(missing)).contains("CABYS");
            verify(hacienda, never()).submit(any(), any(), any());

            // Rechazo y corrección: comprobante nuevo con otro consecutivo; el rechazado se conserva.
            var rejected = sales.confirm(admin, sale(branch, item, "01", "109870654", "María Rojas"));
            when(hacienda.submit(any(), any(), any())).thenReturn(new HaciendaClient.Result(HaciendaClient.Outcome.RECEIVED, null, null));
            when(hacienda.status(any(), any(), any())).thenReturn(new HaciendaClient.Result(HaciendaClient.Outcome.REJECTED,
                "La identificación del receptor no está inscrita.", null));
            dispatcher.runOnce(10);
            due(company);
            dispatcher.runOnce(10);
            assertThat(fiscalStatus(rejected)).isEqualTo("REJECTED");
            mvc.perform(post("/api/sales/" + rejected.movement().folio() + "/fiscal/correct").with(authentication(reader)).with(csrf())
                .contentType("application/json").content("{}")).andExpect(status().isForbidden());
            var corrected = sales.get(admin, rejected.movement().id());
            documents.correct(company, rejected.movement().id(), "02", "3101654321", "Comercial Horizonte S.A.");
            var docs = sales.get(admin, rejected.movement().id()).fiscal();
            assertThat(docs).hasSize(2);
            assertThat(docs.get(1).replacesId()).isEqualTo(docs.get(0).id());
            assertThat(docs.get(1).docType()).isEqualTo("01");
            assertThat(docs.get(1).consecutive()).isNotEqualTo(corrected.fiscal().getFirst().consecutive());

            // Anular una venta aceptada emite nota de crédito; anular una no enviada la cancela.
            reset(hacienda);
            when(hacienda.submit(any(), any(), any())).thenReturn(new HaciendaClient.Result(HaciendaClient.Outcome.RECEIVED, null, null));
            when(hacienda.status(any(), any(), any())).thenReturn(new HaciendaClient.Result(HaciendaClient.Outcome.ACCEPTED, null, null));
            sales.reverse(admin, invoiceSale.movement().id(), "Cliente desistió",
                List.of(new SalePaymentService.RefundInput("CASH", new BigDecimal("3800.00"), null)));
            var withCredit = sales.get(admin, invoiceSale.movement().id()).fiscal();
            assertThat(withCredit).hasSize(2);
            assertThat(withCredit.get(1).docType()).isEqualTo("03");
            assertThat(withCredit.get(1).referenceId()).isEqualTo(withCredit.get(0).id());
            dispatcher.runOnce(10);
            due(company);
            dispatcher.runOnce(10);
            assertThat(sales.get(admin, invoiceSale.movement().id()).fiscal().get(1).status()).isEqualTo("ACCEPTED");
            String credit = jdbc.queryForObject("SELECT signed_xml FROM fiscal_documents WHERE company_id=? AND id=?", String.class,
                company, withCredit.get(1).id());
            assertThat(credit).contains("NotaCreditoElectronica", "<Codigo>01</Codigo>", withCredit.get(0).key());

            var notSent = sales.confirm(admin, sale(branch, item, null, null, null));
            sales.reverse(admin, notSent.movement().id(), "Error de digitación",
                List.of(new SalePaymentService.RefundInput("CASH", new BigDecimal("3800.00"), null)));
            assertThat(fiscalStatus(notSent)).isEqualTo("CANCELLED");
            assertThat(sales.get(admin, notSent.movement().id()).fiscal()).hasSize(1);

            // ADR-008: al borrar la empresa se archivan aceptados y rechazados por 5 años; pendientes y cancelados no.
            int archived = new org.springframework.transaction.support.TransactionTemplate(transactions).execute(s -> archive.archiveCompany(company));
            Integer expected = jdbc.queryForObject("SELECT COUNT(*) FROM fiscal_documents WHERE company_id=? AND status IN ('ACCEPTED','REJECTED') AND signed_xml IS NOT NULL",
                Integer.class, company);
            assertThat(archived).isEqualTo(expected).isGreaterThanOrEqualTo(5);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM fiscal_archive WHERE company_id=? AND retain_until = issued_at + INTERVAL 5 YEAR AND status NOT IN ('ACCEPTED','REJECTED')",
                Integer.class, company)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM fiscal_archive WHERE company_id=? AND retain_until <> issued_at + INTERVAL 5 YEAR", Integer.class, company)).isZero();
            jdbc.update("UPDATE fiscal_archive SET retain_until = UTC_TIMESTAMP(6) - INTERVAL 1 DAY WHERE company_id=? LIMIT 1", company);
            assertThat(archive.purgeExpired()).isEqualTo(1);
        } finally {
            for (String table : List.of("identity_audit_events", "users", "company_roles")) jdbc.update("DELETE FROM " + table + " WHERE company_id=?", company);
            jdbc.update("DELETE FROM fiscal_documents WHERE company_id=? AND reference_id IS NOT NULL", company);
            jdbc.update("DELETE FROM fiscal_documents WHERE company_id=? AND replaces_id IS NOT NULL", company);
            jdbc.update("DELETE FROM fiscal_documents WHERE company_id=?", company);
            jdbc.update("DELETE FROM companies WHERE id=?", company);
            jdbc.update("DELETE FROM fiscal_archive WHERE company_id=?", company);
        }
    }


    private static SaleService.SaleInput sale(String branch, String item, String idType, String idNumber, String name) {
        return sale(branch, item, "3800", idType, idNumber, name);
    }

    private static SaleService.SaleInput sale(String branch, String item, String price, String idType, String idNumber, String name) {
        return new SaleService.SaleInput(id(), branch, name, null, idType == null ? null : "cliente@example.test",
            List.of(new SaleService.LineInput(item, BigDecimal.ONE, new BigDecimal(price))),
            List.of(new SalePaymentService.PaymentInput("CASH", new BigDecimal("5000"), null)), idType, idNumber);
    }

    private void due(String company) {
        jdbc.update("UPDATE fiscal_documents SET next_attempt_at=NOW(6) - INTERVAL 1 SECOND WHERE company_id=? AND status IN ('PENDING','PROCESSING')", company);
    }

    private String fiscalStatus(SaleService.Sale sale) { return field(sale, "status"); }
    private String reason(SaleService.Sale sale) { return field(sale, "status_reason"); }
    private String key(SaleService.Sale sale) { return field(sale, "numeric_key"); }

    private String field(SaleService.Sale sale, String column) {
        return jdbc.queryForObject("SELECT " + column + " FROM fiscal_documents WHERE sale_id=? AND doc_type<>'03' ORDER BY created_at DESC LIMIT 1",
            String.class, sale.movement().id());
    }

    private static String id() { return UUID.randomUUID().toString(); }
}
