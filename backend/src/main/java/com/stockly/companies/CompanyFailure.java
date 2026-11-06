package com.stockly.companies;

import java.util.Map;

public class CompanyFailure extends RuntimeException {
    final int status;
    final String code;
    final Map<String,String> fields;
    public CompanyFailure(int status, String code, String message) { this(status, code, message, Map.of()); }
    public CompanyFailure(int status, String code, String message, Map<String,String> fields) {
        super(message); this.status=status; this.code=code; this.fields=fields;
    }
}
