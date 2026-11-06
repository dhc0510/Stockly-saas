package com.stockly.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;

@Component
@ConditionalOnProperty(name="stockly.bootstrap.saas-admin.enabled",havingValue="true")
public class InitialSaasAdminProvisioner implements ApplicationRunner {
    private final AccountRepository accounts;
    private final PasswordEncoder encoder;
    private final String fullName,username,email,password;
    public InitialSaasAdminProvisioner(AccountRepository accounts,PasswordEncoder encoder,
        @Value("${stockly.bootstrap.saas-admin.full-name:}") String fullName,
        @Value("${stockly.bootstrap.saas-admin.username:}") String username,
        @Value("${stockly.bootstrap.saas-admin.email:}") String email,
        @Value("${stockly.bootstrap.saas-admin.password:}") String password) {
        this.accounts=accounts; this.encoder=encoder; this.fullName=fullName; this.username=username; this.email=email; this.password=password;
    }
    @Override @Transactional public void run(ApplicationArguments args) {
        if(accounts.countByRole("SAAS_ADMIN")>0) return;
        if(fullName.isBlank() || !username.matches("[A-Za-z0-9._-]{3,30}") || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") || password.length()<15 || password.length()>128)
            throw new IllegalStateException("La provisión inicial de SaaS requiere STOCKLY_SAAS_ADMIN_FULL_NAME, USERNAME, EMAIL y PASSWORD válidos.");
        accounts.saveAndFlush(Account.saasAdmin(fullName.strip(),username.toLowerCase(Locale.ROOT),email.strip().toLowerCase(Locale.ROOT),encoder.encode(password)));
    }
}
