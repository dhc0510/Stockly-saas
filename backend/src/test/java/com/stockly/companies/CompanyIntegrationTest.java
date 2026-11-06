package com.stockly.companies;

import com.stockly.identity.StocklyPrincipal;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import com.jayway.jsonpath.JsonPath;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers
@WithMockUser(roles="SAAS_ADMIN")
class CompanyIntegrationTest {
    @Container static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4.8");
    static final Path JOURNAL;
    static { try { JOURNAL=Files.createTempDirectory("stockly-deletions-test-"); } catch(Exception e) { throw new ExceptionInInitializerError(e); } }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", MYSQL::getJdbcUrl);
        r.add("spring.datasource.username", MYSQL::getUsername);
        r.add("spring.datasource.password", MYSQL::getPassword);
        r.add("stockly.deletion-journal-directory", JOURNAL::toString);
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired CompanyService service;
    @Autowired DeletionRecovery recovery;
    @BeforeEach void clean() { jdbc.update("DELETE FROM users"); jdbc.update("DELETE FROM companies"); }
    private String payload(String identification,String username,String email) {
        return """
            {"company":{"commercialName":" Ferretería El Sol ","legalName":"Ferretería El Sol S.A.","identificationType":"LEGAL","identification":"%s","contactEmail":" Contacto@example.com ","phone":"+506 2222-3344","address":"San José"},
             "administrator":{"fullName":"Andrés Varela","username":"%s","email":"%s","temporaryPassword":" temporal segura de prueba "}}
            """.formatted(identification,username,email);
    }
    private Map<String,Object> create() throws Exception {
        String json=mvc.perform(post("/api/companies").with(csrf()).contentType("application/json").content(payload("3-101-765432","AVarela","ADMIN@example.com")))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.administrator.username").value("avarela"))
            .andExpect(jsonPath("$.contactEmail").value("contacto@example.com"))
            .andExpect(jsonPath("$.administrator.passwordHash").doesNotExist())
            .andExpect(jsonPath("$.administrator.temporaryPassword").doesNotExist())
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json,"$");
    }
    @Test void createsAtomicAccountWithHashAndRequiredPasswordChange() throws Exception {
        var company=create();
        assertThat(company.get("commercialName")).isEqualTo("Ferretería El Sol");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE company_id = ? AND must_change_password = true",Integer.class,company.get("id"))).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT password_hash FROM users",String.class)).startsWith("$argon2id$").doesNotContain("temporal segura");
    }
    @Test void duplicateAdministratorRollsBackTheCompany() throws Exception {
        create();
        mvc.perform(post("/api/companies").with(csrf()).contentType("application/json").content(payload("3101765433","OtherUser","admin@example.com")))
            .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM companies",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users",Integer.class)).isEqualTo(1);
    }
    @Test void rejectsNormalizedDuplicateIdentificationAndInvalidFields() throws Exception {
        create();
        mvc.perform(post("/api/companies").with(csrf()).contentType("application/json").content(payload("3101765432","other","other@example.com"))).andExpect(status().isConflict());
        mvc.perform(post("/api/companies").with(csrf()).contentType("application/json").content(payload("123","newuser","new@example.com")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.identification").exists());
        mvc.perform(post("/api/companies").with(csrf()).contentType("application/json").content(payload("3101765433","bad user","new@example.com")))
            .andExpect(status().isBadRequest());
    }
    @Test void searchesFiltersAndUsesCostaRicaDayBoundaries() throws Exception {
        var c=create();
        jdbc.update("UPDATE companies SET created_at = '2026-11-06 05:59:59' WHERE id = ?",c.get("id"));
        mvc.perform(get("/api/companies").param("search","el sol").param("status","ACTIVE").param("registeredFrom","2026-11-05").param("registeredTo","2026-11-05"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/companies").param("search","3-101-765432")).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/companies").param("search","%" )).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/companies").param("registeredFrom","2026-11-06")).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/companies").param("size","101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/companies").param("registeredFrom","2026-11-06").param("registeredTo","2026-11-05")).andExpect(status().isBadRequest());
    }
    @Test void editsAndRejectsStaleUpdates() throws Exception {
        var c=create(); String id=(String)c.get("id");
        String body="""
          {"version":0,"company":{"commercialName":"Nuevo nombre","legalName":"El Sol S.A.","identificationType":"LEGAL","identification":"3101765432","contactEmail":"contacto@example.com"}}
          """;
        mvc.perform(patch("/api/companies/"+id).with(csrf()).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.commercialName").value("Nuevo nombre"));
        mvc.perform(patch("/api/companies/"+id).with(csrf()).contentType("application/json").content(body)).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT username_normalized FROM users",String.class)).isEqualTo("avarela");
    }
    @Test void suspendInvalidatesOldBusinessPrincipalEvenAfterReactivation() throws Exception {
        var c=create(); String id=(String)c.get("id");
        var oldAuth=new UsernamePasswordAuthenticationToken(new StocklyPrincipal("account",id,0),null,List.of(new SimpleGrantedAuthority("ROLE_COMPANY_ADMIN")));
        mvc.perform(post("/api/companies/"+id+"/suspend").with(csrf()).contentType("application/json").content("{\"version\":0}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUSPENDED"));
        mvc.perform(get("/api/companies/"+id).with(authentication(oldAuth))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/companies/"+id+"/reactivate").with(csrf()).contentType("application/json").content("{\"version\":1}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(get("/api/companies/"+id).with(authentication(oldAuth))).andExpect(status().isUnauthorized());
    }
    @Test void deleteRequiresExactNameAndRemovesOnlySelectedCompany() throws Exception {
        var c=create(); String id=(String)c.get("id");
        mvc.perform(post("/api/companies").with(csrf()).contentType("application/json").content(payload("3101765433","other","other@example.com"))).andExpect(status().isCreated());
        mvc.perform(post("/api/companies/"+id+"/delete").with(csrf()).contentType("application/json").content("{\"version\":0,\"confirmationName\":\"incorrecto\"}"))
            .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM companies",Integer.class)).isEqualTo(2);
        mvc.perform(post("/api/companies/"+id+"/delete").with(csrf()).contentType("application/json").content("{\"version\":0,\"confirmationName\":\" Ferretería El Sol \"}"))
            .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM companies",Integer.class)).isEqualTo(1);
        assertThat(Files.readString(JOURNAL.resolve(id+".deleted"))).contains(id).doesNotContain("Ferretería");
        mvc.perform(get("/api/companies/"+id)).andExpect(status().isNotFound());
    }
    @Test void recoveryReappliesDeletionBeforeRestoredDataCanBeEnabled() throws Exception {
        var c=create(); String id=(String)c.get("id");
        mvc.perform(post("/api/companies/"+id+"/delete").with(csrf()).contentType("application/json").content("{\"version\":0,\"confirmationName\":\"Ferretería El Sol\"}"))
            .andExpect(status().isNoContent());
        // Simulate an isolated pre-deletion backup being restored. Only test data is affected.
        jdbc.update("INSERT INTO companies(id,commercial_name,legal_name,identification_type,identification_normalized,contact_email,status,created_at,updated_at,version,access_version) VALUES(?,?,?,?,?,?,?,NOW(),NOW(),0,0)",
            id,"Old backup","Old backup SA","LEGAL","3101765432","old@example.com","ACTIVE");
        assertThat(recovery.reconcile()).isEqualTo(1);
        assertThat(companiesCount()).isZero();
        assertThat(recovery.reconcile()).isZero();
    }
    private int companiesCount() { return jdbc.queryForObject("SELECT COUNT(*) FROM companies",Integer.class); }
    @Test void failedDeletionIsNotReappliedDuringRecovery() throws Exception {
        var c=create(); String id=(String)c.get("id");
        jdbc.execute("CREATE TABLE deletion_test_reference (company_id CHAR(36), FOREIGN KEY (company_id) REFERENCES companies(id))");
        try {
            jdbc.update("INSERT INTO deletion_test_reference(company_id) VALUES (?)",id);
            mvc.perform(post("/api/companies/"+id+"/delete").with(csrf()).contentType("application/json").content("{\"version\":0,\"confirmationName\":\"Ferretería El Sol\"}"))
                .andExpect(status().isConflict());
            assertThat(companiesCount()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users",Integer.class)).isEqualTo(1);
            assertThat(Files.readString(JOURNAL.resolve(id+".deleted"))).contains("ROLLED_BACK");
            assertThat(recovery.reconcile()).isZero();
        } finally { jdbc.execute("DROP TABLE deletion_test_reference"); }
    }
    @Test @WithMockUser(roles="COMPANY_ADMIN") void businessCannotReadOrMutateGlobalCompanyManagement() throws Exception {
        mvc.perform(get("/api/companies")).andExpect(status().isForbidden());
        mvc.perform(get("/api/companies/another-company")).andExpect(status().isForbidden());
        mvc.perform(post("/api/companies").with(csrf()).contentType("application/json").content(payload("3101765432","user","test@example.com"))).andExpect(status().isForbidden());
    }
    @Test void requiresCsrfOnAllMutations() throws Exception {
        mvc.perform(post("/api/companies").contentType("application/json").content(payload("3101765432","user","test@example.com"))).andExpect(status().isForbidden());
    }
    @Test void anonymousHasNoGeneratedAdminOrPublicApi() throws Exception {
        mvc.perform(get("/api/companies").with(anonymous())).andExpect(status().isUnauthorized());
    }
    @Test void concurrentCreatesProduceOneCompanyAndAccount() throws Exception {
        var barrier=new CyclicBarrier(2);
        var executor=Executors.newFixedThreadPool(2);
        Callable<Boolean> attempt=() -> {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("saas-admin",null,List.of(new SimpleGrantedAuthority("ROLE_SAAS_ADMIN"))));
            try {
                barrier.await(10,TimeUnit.SECONDS);
                service.create(new CompanyContracts.CreateCompany(
                    new CompanyContracts.CompanyInput("Empresa","Empresa S.A.","LEGAL","3101765432","contact@example.com",null,null),
                    new CompanyContracts.AdministratorInput("Admin Inicial","admin","admin@example.com","contraseña temporal segura")));
                return true;
            } catch(org.springframework.dao.DataIntegrityViolationException ex) { return false; }
            finally { SecurityContextHolder.clearContext(); }
        };
        try {
            var a=executor.submit(attempt); var b=executor.submit(attempt);
            assertThat(List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM companies",Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users",Integer.class)).isEqualTo(1);
        } finally { executor.shutdownNow(); }
    }
}
