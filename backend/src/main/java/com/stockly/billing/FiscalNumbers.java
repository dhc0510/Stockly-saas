package com.stockly.billing;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Consecutivo (20 dígitos) y clave numérica (50 dígitos) según la nota 3 de los Anexos v4.4. */
public final class FiscalNumbers {
    public static final ZoneId COSTA_RICA = ZoneId.of("America/Costa_Rica");
    static final String POINT_OF_SALE = "00001";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("ddMMyy");

    private FiscalNumbers() {}

    /** Establecimiento (3) + punto de venta (5) + tipo (2) + número (10). */
    public static String consecutive(String branchCode, String docType, long number) {
        if (!branchCode.matches("[0-9]{3}") || !docType.matches("[0-9]{2}") || number < 1 || number > 9_999_999_999L)
            throw new IllegalArgumentException("Datos de consecutivo inválidos.");
        return branchCode + POINT_OF_SALE + docType + String.format("%010d", number);
    }

    /** 506 + DDMMAA + cédula a 12 dígitos (nota 4.1) + consecutivo + situación + código de seguridad de 8 dígitos. */
    public static String key(Instant issuedAt, String issuerNumber, String consecutive, char situation, String securityCode) {
        if (!issuerNumber.matches("[0-9]{9,12}") || !consecutive.matches("[0-9]{20}") || "123".indexOf(situation) < 0
            || !securityCode.matches("[0-9]{8}"))
            throw new IllegalArgumentException("Datos de clave inválidos.");
        String key = "506" + DAY.format(issuedAt.atZone(COSTA_RICA)) + "0".repeat(12 - issuerNumber.length()) + issuerNumber
            + consecutive + situation + securityCode;
        if (key.length() != 50) throw new IllegalStateException("La clave debe tener 50 dígitos.");
        return key;
    }

    static String securityCode() { return String.format("%08d", RANDOM.nextInt(100_000_000)); }

    /** Situación de la clave (posición 42): 1 normal, 2 contingencia, 3 sin internet. */
    public static char situation(String key) { return key.charAt(41); }

    /**
     * Reglamento 44739-H, art. 23: el comprobante «sin internet» se envía a más tardar dos días hábiles después de generarse.
     * Cuenta lunes a viernes en hora de Costa Rica, sin feriados (SPEC §13): true si el envío ocurre después del segundo día hábil.
     */
    public static boolean lateOfflineSubmission(Instant generatedAt, Instant submittedAt) {
        var deadline = generatedAt.atZone(COSTA_RICA).toLocalDate();
        for (int business = 0; business < 2; ) {
            deadline = deadline.plusDays(1);
            if (deadline.getDayOfWeek().getValue() <= 5) business++;
        }
        return submittedAt.atZone(COSTA_RICA).toLocalDate().isAfter(deadline);
    }

    /** Tipo de identificación de Hacienda (nota 4) para el tipo guardado en la empresa (STK-004). */
    static String issuerType(String companyType) {
        return switch (companyType) {
            case "PHYSICAL" -> "01";
            case "LEGAL" -> "02";
            case "DIMEX" -> "03";
            case "NITE" -> "04";
            default -> throw new IllegalArgumentException("Tipo de identificación no soportado.");
        };
    }
}
