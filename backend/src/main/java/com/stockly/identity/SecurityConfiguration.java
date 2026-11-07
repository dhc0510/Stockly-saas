package com.stockly.identity;

import com.stockly.companies.CompanyRepository;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.http.HttpMethod;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

@Configuration @EnableMethodSecurity
public class SecurityConfiguration {
    @Bean PasswordEncoder passwordEncoder() { return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8(); }
    @Bean SecurityContextRepository stocklySecurityContextRepository() { return new HttpSessionSecurityContextRepository(); }
    @Bean @ConditionalOnWebApplication SecurityFilterChain security(HttpSecurity http, CompanyRepository companies, AccountRepository accounts) throws Exception {
        return http.authorizeHttpRequests(r -> r
                .requestMatchers("/api/auth/csrf", "/api/auth/login", "/api/auth/device-challenges/verify",
                    "/api/auth/password-recovery", "/api/auth/password-recovery/complete").permitAll()
                .requestMatchers("/api/auth/password/first-change").hasAuthority("PASSWORD_CHANGE_ONLY")
                .requestMatchers("/api/auth/logout", "/api/auth/me").authenticated()
                .requestMatchers("/api/companies", "/api/companies/**").hasRole("SAAS_ADMIN")
                .requestMatchers("/api/users", "/api/users/**").hasAnyAuthority("ROLE_COMPANY_ADMIN", "PERM_MANAGE_USERS")
                .requestMatchers(HttpMethod.GET,"/api/roles", "/api/roles/**").hasAnyAuthority("ROLE_COMPANY_ADMIN", "PERM_MANAGE_USERS", "PERM_MANAGE_PERMISSIONS")
                .requestMatchers("/api/roles", "/api/roles/**", "/api/permissions/**").hasAnyAuthority("ROLE_COMPANY_ADMIN", "PERM_MANAGE_PERMISSIONS")
                .requestMatchers("/api/company/security/**").hasRole("COMPANY_ADMIN")
                .requestMatchers("/api/audit").hasRole("COMPANY_ADMIN")
                .requestMatchers("/api/catalog", "/api/catalog/**").hasAnyAuthority("ROLE_COMPANY_ADMIN",
                    "MODULE_CATALOG_READ", "MODULE_CATALOG_OPERATE", "MODULE_CATALOG_ADMIN")
                .requestMatchers("/api/inventory/receipts", "/api/inventory/receipts/**").hasAnyAuthority("ROLE_COMPANY_ADMIN",
                    "MODULE_INVENTORY_RECEIPTS_READ", "MODULE_INVENTORY_RECEIPTS_OPERATE", "MODULE_INVENTORY_RECEIPTS_ADMIN")
                .requestMatchers("/api/inventory/movements", "/api/inventory/movements/**").hasAnyAuthority("ROLE_COMPANY_ADMIN",
                    "MODULE_INVENTORY_MOVEMENTS_READ", "MODULE_INVENTORY_MOVEMENTS_OPERATE", "MODULE_INVENTORY_MOVEMENTS_ADMIN")
                .requestMatchers("/api/inventory/history", "/api/inventory/history/**").hasAnyAuthority("ROLE_COMPANY_ADMIN",
                    "MODULE_INVENTORY_HISTORY_READ", "MODULE_INVENTORY_HISTORY_OPERATE", "MODULE_INVENTORY_HISTORY_ADMIN")
                .requestMatchers("/api/inventory", "/api/inventory/**").hasAnyAuthority("ROLE_COMPANY_ADMIN",
                    "MODULE_INVENTORY_READ", "MODULE_INVENTORY_OPERATE", "MODULE_INVENTORY_ADMIN",
                    "MODULE_CATALOG_OPERATE", "MODULE_CATALOG_ADMIN")
                .anyRequest().denyAll())
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req,res,ex) -> error(res,401,"UNAUTHENTICATED","Inicia sesión para continuar."))
                .accessDeniedHandler((req,res,ex) -> error(res,403,"FORBIDDEN","No tienes permiso para esta operación.")))
            .requestCache(cache -> cache.disable())
            .securityContext(c -> c.securityContextRepository(stocklySecurityContextRepository()))
            .addFilterBefore(new CompanyAccessFilter(companies, accounts), AuthorizationFilter.class)
            .build();
    }
    private static void error(jakarta.servlet.http.HttpServletResponse res,int status,String code,String message) throws java.io.IOException {
        res.setStatus(status); res.setContentType("application/json"); res.setCharacterEncoding("UTF-8");
        res.getWriter().write("{\"code\":\""+code+"\",\"message\":\""+message+"\",\"fieldErrors\":{},\"requestId\":\""+java.util.UUID.randomUUID()+"\"}");
    }
}
