package com.stockly.billing;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cifra las credenciales de Hacienda con AES-256-GCM (ADR-007). La clave vive solo en STOCKLY_BILLING_KEY:
 * nunca en la base de datos ni en el código. Cada valor lleva su propio vector de inicialización y la empresa
 * como dato asociado, así un valor copiado a otra empresa no se puede descifrar.
 */
@Component
public class BillingCrypto {
    private static final int IV_BYTES = 12, TAG_BITS = 128;
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    BillingCrypto(@Value("${stockly.billing.key:}") String encoded) {
        SecretKeySpec parsed = null;
        if (encoded != null && !encoded.isBlank()) {
            byte[] bytes = Base64.getDecoder().decode(encoded.strip());
            if (bytes.length != 32) throw new IllegalStateException("STOCKLY_BILLING_KEY debe tener 32 bytes en Base64.");
            parsed = new SecretKeySpec(bytes, "AES");
        }
        this.key = parsed;
    }

    public boolean enabled() { return key != null; }

    byte[] encrypt(String company, byte[] plain) {
        requireKey();
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(company.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] sealed = cipher.doFinal(plain);
            return ByteBuffer.allocate(iv.length + sealed.length).put(iv).put(sealed).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo cifrar la credencial.", e);
        }
    }

    byte[] decrypt(String company, byte[] stored) {
        requireKey();
        if (stored == null || stored.length <= IV_BYTES) throw new IllegalStateException("Credencial ausente.");
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, Arrays.copyOf(stored, IV_BYTES)));
            cipher.updateAAD(company.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return cipher.doFinal(stored, IV_BYTES, stored.length - IV_BYTES);
        } catch (GeneralSecurityException e) {
            // No se incluye el detalle: podría revelar información de la credencial o de la clave.
            throw new IllegalStateException("No se pudo descifrar la credencial.");
        }
    }

    private void requireKey() {
        if (key == null) throw new IllegalStateException("La facturación electrónica no está habilitada en este servidor.");
    }
}
