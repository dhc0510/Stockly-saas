package com.stockly.billing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** URL de Hacienda por ambiente, tomadas solo de la configuración (nunca fijas en el código salvo la de producción documentada). */
@Component
public class HaciendaEndpoints {
    public record Endpoint(String apiUrl, String tokenUrl, String clientId) {
        public boolean complete() { return !apiUrl.isBlank() && !tokenUrl.isBlank() && !clientId.isBlank(); }
    }

    private final Endpoint test, prod;
    private final String providerId;

    HaciendaEndpoints(@Value("${stockly.billing.test.api-url:}") String testApi, @Value("${stockly.billing.test.token-url:}") String testToken,
                      @Value("${stockly.billing.test.client-id:}") String testClient,
                      @Value("${stockly.billing.prod.api-url:}") String prodApi, @Value("${stockly.billing.prod.token-url:}") String prodToken,
                      @Value("${stockly.billing.prod.client-id:}") String prodClient,
                      @Value("${stockly.billing.system-provider-id:}") String providerId) {
        this.test = new Endpoint(slash(testApi), testToken.strip(), testClient.strip());
        this.prod = new Endpoint(slash(prodApi), prodToken.strip(), prodClient.strip());
        this.providerId = providerId.strip();
    }

    public Endpoint of(String environment) { return "PROD".equals(environment) ? prod : test; }

    /** Cédula del proveedor de sistemas (campo ProveedorSistemas de la v4.4): la del titular de Stockly. */
    public String providerId() { return providerId; }

    private static String slash(String url) {
        String value = url == null ? "" : url.strip();
        return value.isEmpty() || value.endsWith("/") ? value : value + "/";
    }
}
