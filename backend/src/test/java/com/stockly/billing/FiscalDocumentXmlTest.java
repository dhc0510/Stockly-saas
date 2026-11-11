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

    @Test void offlineKeyUsesSituation3AndTwoBusinessDayDeadline() {
        String consecutive = FiscalNumbers.consecutive("001", "04", 7);
        String key = FiscalNumbers.key(ISSUED, "3101123456", consecutive, '3', "12345678");
        assertThat(FiscalNumbers.situation(key)).isEqualTo('3');
        assertThat(FiscalNumbers.situation(FiscalNumbers.key(ISSUED, "3101123456", consecutive, '1', "12345678"))).isEqualTo('1');
        // Viernes 13/11 en Costa Rica (UTC-6): los dos días hábiles son lunes 16 y martes 17.
        Instant friday = Instant.parse("2026-11-13T16:00:00Z");
        assertThat(FiscalNumbers.lateOfflineSubmission(friday, Instant.parse("2026-11-14T18:00:00Z"))).isFalse();
        assertThat(FiscalNumbers.lateOfflineSubmission(friday, Instant.parse("2026-11-18T05:59:00Z"))).isFalse(); // martes 23:59
        assertThat(FiscalNumbers.lateOfflineSubmission(friday, Instant.parse("2026-11-18T06:00:00Z"))).isTrue();  // miércoles 00:00
        // Miércoles 11/11: jueves 12 y viernes 13; el sábado ya es tarde.
        Instant wednesday = Instant.parse("2026-11-11T20:00:00Z");
        assertThat(FiscalNumbers.lateOfflineSubmission(wednesday, Instant.parse("2026-11-14T05:00:00Z"))).isFalse(); // viernes 23:00
        assertThat(FiscalNumbers.lateOfflineSubmission(wednesday, Instant.parse("2026-11-14T15:00:00Z"))).isTrue();  // sábado
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

    @Test void receiverNameMustHaveThreeToHundredCharactersAndTheSchemaFailureIsExplained() throws Exception {
        // Servidor (SPEC §6): la venta con factura exige un nombre de 3 a 100 caracteres.
        assertThatThrownBy(() -> FiscalDocumentService.customer("01", "109870654", "a"))
            .isInstanceOf(com.stockly.identity.IdentityFailure.class).hasMessageContaining("de 3 a 100");
        assertThatThrownBy(() -> FiscalDocumentService.customer("01", "109870654", "x".repeat(101)))
            .isInstanceOf(com.stockly.identity.IdentityFailure.class);
        assertThat(FiscalDocumentService.customer("01", "1-0987-0654", "  Ana  ").name()).isEqualTo("Ana");
        assertThat(FiscalDocumentService.customer(null, null, "a").name()).isEqualTo("a"); // tiquete: el nombre es libre

        // Un XML armado con nombre «a» no cumple el esquema (por eso quedaba reintentándose); el despachador lo explica.
        byte[] key = getClass().getResourceAsStream("/billing/llave-prueba.p12").readAllBytes();
        var base = draft("01", "00100001010000000002", null);
        var shortName = new FiscalDraft(base.docType(), base.key(), base.consecutive(), base.issuedAt(), base.providerId(), base.activityCode(),
            base.issuer(), new FiscalDraft.Party("a", "01", "109870654", null, null, null, null, null), base.lines(), base.payments(), null);
        byte[] signed = signer.sign(builder.build(shortName), key, "123456".toCharArray());
        assertThatThrownBy(() -> schemas.validate("01", signed)).isInstanceOf(IllegalArgumentException.class)
            .satisfies(e -> assertThat(FiscalDispatcher.schemaReason(e.getMessage())).contains("nombre del cliente debe tener de 3 a 100"));
        assertThat(FiscalDispatcher.schemaReason("El XML no cumple el esquema v4.4: cvc-x: algo")).startsWith("No se envió a Hacienda")
            .contains("cvc-x: algo").endsWith("Corrige los datos del cliente.");
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
