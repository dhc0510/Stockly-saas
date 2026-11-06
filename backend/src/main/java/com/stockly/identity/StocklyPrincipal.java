package com.stockly.identity;

import java.io.Serializable;
import java.security.Principal;

/** Contract for STK-005: only the authentication module may establish these identities. */
public record StocklyPrincipal(String accountId, String companyId, long accessVersion, long accountAccessVersion,
                               String role, boolean mustChangePassword) implements Principal, Serializable {
    public StocklyPrincipal(String accountId, String companyId, long accessVersion) {
        this(accountId, companyId, accessVersion, 0, "SAAS_ADMIN", false);
    }
    @Override public String getName() { return accountId; }
}
