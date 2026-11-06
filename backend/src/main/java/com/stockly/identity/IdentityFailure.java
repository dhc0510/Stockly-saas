package com.stockly.identity;

import java.util.Map;

public class IdentityFailure extends RuntimeException {
    public final int status;
    public final String code;
    public final Map<String,String> fields;
    public IdentityFailure(int status, String code, String message) { this(status, code, message, Map.of()); }
    public IdentityFailure(int status, String code, String message, Map<String,String> fields) {
        super(message); this.status = status; this.code = code; this.fields = fields;
    }
}
