package com.stockly.billing;

import com.stockly.identity.IdentityFailure;
import java.util.Map;

/** Error de facturación con el mismo formato de respuesta que el resto de la API. */
class BillingFailure extends IdentityFailure {
    BillingFailure(int status, String code, String message) { super(status, code, message); }
    BillingFailure(int status, String code, String message, Map<String, String> fields) { super(status, code, message, fields); }
}
