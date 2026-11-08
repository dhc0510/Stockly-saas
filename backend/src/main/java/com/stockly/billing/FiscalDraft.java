package com.stockly.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

/** Datos de un comprobante listos para convertir a XML 4.4. Los montos de venta ya incluyen IVA (SPEC §7). */
public record FiscalDraft(String docType, String key, String consecutive, Instant issuedAt, String providerId,
                          String activityCode, Party issuer, Party receiver, List<Line> lines, Map<String, BigDecimal> payments,
                          Reference reference) {

    public record Party(String name, String idType, String idNumber, String email, String province, String canton,
                        String district, String otherSigns) {}

    /** Línea de venta con su total cobrado (IVA incluido) y la tarifa del catálogo (nota 8.1). */
    public record Line(String cabys, BigDecimal quantity, String unit, String detail, BigDecimal total, String rateCode) {}

    public record Reference(String docType, String key, Instant issuedAt, String reason) {}

    /** Tarifa (%) por código de la nota 8.1. Los códigos 05-07 solo existen para notas de crédito/débito. */
    static final Map<String, BigDecimal> RATES = Map.ofEntries(
        Map.entry("01", BigDecimal.ZERO), Map.entry("02", new BigDecimal("1")), Map.entry("03", new BigDecimal("2")),
        Map.entry("04", new BigDecimal("4")), Map.entry("08", new BigDecimal("13")), Map.entry("09", new BigDecimal("0.5")),
        Map.entry("10", BigDecimal.ZERO), Map.entry("11", BigDecimal.ZERO));

    /** Unidades del catálogo de Stockly en la tabla de unidades (RTC 443:2010) del XSD. */
    static final Map<String, String> UNITS = Map.of("unidad", "Unid", "kg", "Kg", "litro", "L", "metro", "M");

    /** Desglose de la línea: sin IVA = total ÷ (1 + tarifa) a céntimos; IVA = total − sin IVA (SPEC §7). */
    public record Breakdown(BigDecimal unitPrice, BigDecimal subtotal, BigDecimal rate, BigDecimal tax, BigDecimal total) {}

    public static Breakdown breakdown(Line line) {
        BigDecimal rate = RATES.get(line.rateCode());
        if (rate == null) throw new IllegalArgumentException("Tarifa de IVA no soportada: " + line.rateCode());
        BigDecimal total = line.total().setScale(2, RoundingMode.UNNECESSARY);
        BigDecimal subtotal = total.multiply(new BigDecimal(100)).divide(new BigDecimal(100).add(rate), 2, RoundingMode.HALF_UP);
        BigDecimal unitPrice = subtotal.divide(line.quantity(), 5, RoundingMode.HALF_UP);
        return new Breakdown(unitPrice, subtotal, rate, total.subtract(subtotal), total);
    }

    /** Exenta (10) y 0 % sin derecho a crédito (11) no son gravadas; el resto sí, aunque la tarifa sea 0 %. */
    static String category(String rateCode) {
        return switch (rateCode) { case "10" -> "EXENTO"; case "11" -> "NO_SUJETO"; default -> "GRAVADO"; };
    }
}
