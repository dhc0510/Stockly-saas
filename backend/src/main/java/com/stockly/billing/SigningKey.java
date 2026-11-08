package com.stockly.billing;

import java.io.ByteArrayInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Collections;

/** Llave criptográfica .p12 de una empresa, abierta en memoria con su PIN. Nunca se escribe en disco ni en registros. */
record SigningKey(PrivateKey privateKey, X509Certificate certificate) {
    static final int MAX_BYTES = 64 * 1024;

    static SigningKey open(byte[] keyStore, char[] pin) {
        if (keyStore == null || keyStore.length == 0 || keyStore.length > MAX_BYTES)
            throw new BillingFailure(400, "BILLING_KEY_INVALID", "Sube la llave criptográfica .p12 descargada de TRIBU-CR.");
        try {
            KeyStore store = KeyStore.getInstance("PKCS12");
            store.load(new ByteArrayInputStream(keyStore), pin);
            for (String alias : Collections.list(store.aliases())) {
                if (!store.isKeyEntry(alias)) continue;
                var key = (PrivateKey) store.getKey(alias, pin);
                var cert = (X509Certificate) store.getCertificate(alias);
                if (key != null && cert != null) return new SigningKey(key, cert);
            }
        } catch (Exception e) {
            // El detalle de la excepción puede describir el contenido de la llave: no se propaga.
            throw new BillingFailure(400, "BILLING_KEY_INVALID", "No se pudo abrir la llave. Revisa el archivo .p12 y su PIN.");
        }
        throw new BillingFailure(400, "BILLING_KEY_INVALID", "El archivo no contiene una llave de firma.");
    }

    Instant expiresAt() { return certificate.getNotAfter().toInstant(); }

    String subject() {
        String subject = certificate.getSubjectX500Principal().getName();
        return subject.length() > 300 ? subject.substring(0, 300) : subject;
    }
}
