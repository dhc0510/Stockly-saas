package com.stockly.identity;

import java.io.Serializable;
import java.security.Principal;

/** Contract for STK-005: only the authentication module may establish these identities. */
public record StocklyPrincipal(String accountId, String companyId, long accessVersion) implements Principal, Serializable {
    @Override public String getName() { return accountId; }
}
