package com.stockly.billing;

import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.enumerations.SignaturePackaging;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.Policy;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.token.KSPrivateKeyEntry;
import eu.europa.esig.dss.token.Pkcs12SignatureToken;
import eu.europa.esig.dss.xades.XAdESSignatureParameters;
import eu.europa.esig.dss.xades.signature.XAdESService;
import java.io.ByteArrayOutputStream;
import java.security.KeyStore;
import java.util.Base64;
import javax.xml.crypto.dsig.CanonicalizationMethod;
import org.springframework.stereotype.Component;

/**
 * Firma XAdES-EPES enveloped (Anexos v4.4): XAdES v1.3.2, C14N 20010315, SHA-256 y la política oficial de Hacienda.
 * La llave se abre solo en memoria para cada firma.
 */
@Component
public class FiscalSigner {
    /** Identificador y hash SHA-256 de la política publicados en el ejemplo de firma de los Anexos v4.4. */
    static final String POLICY_ID = "https://cdn.comprobanteselectronicos.go.cr/xml-schemas/"
        + "Resoluci%C3%B3n_General_sobre_disposiciones_t%C3%A9cnicas_comprobantes_electr%C3%B3nicos_para_efectos_tributarios.pdf";
    static final String POLICY_HASH = "DWxin1xWOeI8OuWQXazh4VjLWAaCLAA954em7DMh0h8=";

    private final XAdESService service = new XAdESService(new CommonCertificateVerifier());

    public byte[] sign(byte[] xml, byte[] keyStore, char[] pin) {
        try (var token = new Pkcs12SignatureToken(keyStore, new KeyStore.PasswordProtection(pin))) {
            KSPrivateKeyEntry entry = (KSPrivateKeyEntry) token.getKeys().stream().findFirst()
                .orElseThrow(() -> new BillingFailure(400, "BILLING_KEY_INVALID", "La llave no contiene un certificado de firma."));
            var parameters = new XAdESSignatureParameters();
            parameters.setSignatureLevel(SignatureLevel.XAdES_BASELINE_B);
            parameters.setSignaturePackaging(SignaturePackaging.ENVELOPED);
            parameters.setDigestAlgorithm(DigestAlgorithm.SHA256);
            parameters.setSigningCertificate(entry.getCertificate());
            parameters.setCertificateChain(entry.getCertificateChain());
            parameters.setEn319132(false);
            parameters.setSignedInfoCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE);
            parameters.setSignedPropertiesCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE);
            var policy = new Policy();
            policy.setId(POLICY_ID);
            policy.setDigestAlgorithm(DigestAlgorithm.SHA256);
            policy.setDigestValue(Base64.getDecoder().decode(POLICY_HASH));
            parameters.bLevel().setSignaturePolicy(policy);

            DSSDocument document = new InMemoryDocument(xml);
            var toSign = service.getDataToSign(document, parameters);
            var value = token.sign(toSign, parameters.getDigestAlgorithm(), entry);
            DSSDocument signed = service.signDocument(document, parameters, value);
            var out = new ByteArrayOutputStream();
            signed.writeTo(out);
            return out.toByteArray();
        } catch (BillingFailure e) {
            throw e;
        } catch (Exception e) {
            throw new BillingFailure(400, "BILLING_SIGN_FAILED", "No se pudo firmar el comprobante con la llave guardada.");
        }
    }

}
