package com.stockly.billing;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/** STK-013 sin base de datos: números, desglose de IVA, XML 4.4 firmado y validado contra el XSD oficial. */
class FiscalDocumentXmlTest {
    private static final Instant ISSUED = Instant.parse("2026-11-08T15:40:00Z");
    private final FiscalXmlBuilder builder = new FiscalXmlBuilder();
    private final FiscalSchemas schemas = new FiscalSchemas();
    private final FiscalSigner signer = new FiscalSigner();

    @Test void buildsConsecutiveAndKeyAccordingToNote3() {
        String consecutive = FiscalNumbers.consecutive("002", "01", 12);
        assertThat(consecutive).isEqualTo("00200001010000000012");
        String key = FiscalNumbers.key(ISSUED, "3101123456", consecutive, '1', "48210937");
        assertThat(key).hasSize(50).startsWith("506081126003101123456").endsWith("00200001010000000012148210937");
        assertThat(FiscalNumbers.key(ISSUED, "109870654", consecutive, '1', "00000001").substring(9, 21)).isEqualTo("000109870654");
        assertThatThrownBy(() -> FiscalNumbers.consecutive("000a", "01", 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void splitsTaxSoTheLineAddsUpToWhatTheCustomerPaid() {
        var coffee = FiscalDraft.breakdown(line("11400.00", "3", "08"));
        assertThat(coffee.subtotal()).isEqualByComparingTo("10088.50");
        assertThat(coffee.tax()).isEqualByComparingTo("1311.50");
        assertThat(coffee.subtotal().add(coffee.tax())).isEqualByComparingTo("11400.00");
        var flour = FiscalDraft.breakdown(line("1851.75", "1.5", "02"));
        assertThat(flour.subtotal()).isEqualByComparingTo("1833.42");
        assertThat(flour.tax()).isEqualByComparingTo("18.33");
        var exempt = FiscalDraft.breakdown(line("500.00", "1", "10"));
        assertThat(exempt.tax()).isEqualByComparingTo("0");
        assertThatThrownBy(() -> FiscalDraft.breakdown(line("10.00", "1", "07"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void signsAnInvoiceAndACreditNoteThatMatchTheOfficialSchema() throws Exception {
        byte[] key = getClass().getResourceAsStream("/billing/llave-prueba.p12").readAllBytes();
        var invoice = draft("01", "00100001010000000001", null);
        byte[] signed = signer.sign(builder.build(invoice), key, "123456".toCharArray());
        schemas.validate("01", signed);
        String xml = new String(signed, StandardCharsets.UTF_8);
        assertThat(xml).contains("<TotalComprobante>13251.75000</TotalComprobante>", "<TipoMedioPago>06</TipoMedioPago>",
            "SigPolicyId", FiscalSigner.POLICY_HASH, "<CodigoTarifaIVA>08</CodigoTarifaIVA>", "<UnidadMedida>Kg</UnidadMedida>");

        var credit = draft("03", "00100001030000000001",
            new FiscalDraft.Reference("01", invoice.key(), ISSUED, "Cliente desistió de la compra"));
        byte[] signedCredit = signer.sign(builder.build(credit), key, "123456".toCharArray());
        schemas.validate("03", signedCredit);
        assertThat(new String(signedCredit, StandardCharsets.UTF_8)).contains("<Codigo>01</Codigo>", invoice.key());
    }

    @Test void rejectsAnInvoiceWithoutReceiverAndAWrongPin() throws Exception {
        assertThatThrownBy(() -> builder.build(new FiscalDraft("01", "5".repeat(50), "0".repeat(20), ISSUED, "3101000000", "475203",
            issuer(), null, List.of(line("1.00", "1", "08")), new LinkedHashMap<>(), null))).isInstanceOf(IllegalArgumentException.class);
        byte[] key = getClass().getResourceAsStream("/billing/llave-prueba.p12").readAllBytes();
        assertThatThrownBy(() -> SigningKey.open(key, "000000".toCharArray())).isInstanceOf(BillingFailure.class);
        assertThat(SigningKey.open(key, "123456".toCharArray()).subject()).contains("PRUEBA");
    }

    private static FiscalDraft draft(String type, String consecutive, FiscalDraft.Reference reference) {
        var payments = new LinkedHashMap<String, BigDecimal>();
        payments.put("CASH", new BigDecimal("8251.75"));
        payments.put("SINPE", new BigDecimal("5000.00"));
        var receiver = new FiscalDraft.Party("Comercial Horizonte S.A.", "02", "3101654321", "compras@horizonte.test", null, null, null, null);
        return new FiscalDraft(type, FiscalNumbers.key(ISSUED, "3101123456", consecutive, '1', "12345678"), consecutive, ISSUED,
            "3101000000", "475203", issuer(), receiver,
            List.of(new FiscalDraft.Line("2391100000000", new BigDecimal("3"), "unidad", "Café molido 250 g", new BigDecimal("11400.00"), "08"),
                new FiscalDraft.Line("2311100000000", new BigDecimal("1.5"), "kg", "Harina a granel", new BigDecimal("1851.75"), "02")),
            payments, reference);
    }

    private static FiscalDraft.Party issuer() {
        return new FiscalDraft.Party("Ferretería de Prueba S.A.", "02", "3101123456", "facturas@ferreteria.test", "1", "15", "01",
            "200 m norte de la iglesia");
    }

    private static FiscalDraft.Line line(String total, String quantity, String rate) {
        return new FiscalDraft.Line("2391100000000", new BigDecimal(quantity), "unidad", "Producto", new BigDecimal(total), rate);
    }
}
