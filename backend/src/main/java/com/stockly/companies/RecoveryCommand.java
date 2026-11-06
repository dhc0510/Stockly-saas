package com.stockly.companies;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

@Configuration
@ConditionalOnProperty(name="stockly.reconcile-deletions",havingValue="true")
public class RecoveryCommand {
    @Bean ApplicationRunner reconcileDeletedCompanies(DeletionRecovery recovery, Environment environment) {
        return args -> {
            if(!"none".equals(environment.getProperty("spring.main.web-application-type")))
                throw new IllegalStateException("La recuperación requiere un proceso sin servidor web y una base aislada.");
            recovery.reconcile();
        };
    }
}
