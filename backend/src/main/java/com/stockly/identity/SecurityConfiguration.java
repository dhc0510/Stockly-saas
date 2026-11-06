package com.stockly.identity;

import com.stockly.companies.CompanyRepository;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

@Configuration @EnableMethodSecurity
public class SecurityConfiguration {
    @Bean PasswordEncoder passwordEncoder() { return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8(); }
    // No generated password, public login, or seeded admin. STK-005 supplies authentication.
    @Bean UserDetailsService noImplicitUsers() { return username -> { throw new UsernameNotFoundException("Autenticación pendiente de STK-005"); }; }
    @Bean @ConditionalOnWebApplication SecurityFilterChain security(HttpSecurity http, CompanyRepository companies) throws Exception {
        return http.authorizeHttpRequests(r -> r
                .requestMatchers("/api/companies", "/api/companies/**").hasRole("SAAS_ADMIN")
                .anyRequest().denyAll())
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req,res,ex) -> error(res,401,"UNAUTHENTICATED","Inicia sesión para continuar."))
                .accessDeniedHandler((req,res,ex) -> error(res,403,"FORBIDDEN","No tienes permiso para esta operación.")))
            .addFilterBefore(new CompanyAccessFilter(companies), AuthorizationFilter.class)
            .build();
    }
    private static void error(jakarta.servlet.http.HttpServletResponse res,int status,String code,String message) throws java.io.IOException {
        res.setStatus(status); res.setContentType("application/json");
        res.getWriter().write("{\"code\":\""+code+"\",\"message\":\""+message+"\",\"fieldErrors\":{},\"requestId\":\""+java.util.UUID.randomUUID()+"\"}");
    }
}
