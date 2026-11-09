package com.stockly.inventory;

import com.stockly.identity.IdentityFailure;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Escritura compartida de CSV y Excel (STK-016 y STK-017). Los números van como números y los textos peligrosos se neutralizan. */
final class TabularExport {
    static final String CSV = "text/csv; charset=UTF-8";
    static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** Hoja: encabezados, filas (String, BigDecimal o Number) y formatos numéricos por columna. */
    record Sheet(String name, List<String> headers, List<List<Object>> rows, Map<Integer, String> formats) {}

    private TabularExport() {}

    static String format(String value) {
        String kind = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
        if (!kind.equals("csv") && !kind.equals("xlsx")) throw new IdentityFailure(400, "INVENTORY_INVALID", "Elige Excel o CSV para exportar.");
        return kind;
    }

    /** CSV RFC 4180 en UTF-8 con BOM, coma y punto decimal. */
    static byte[] csv(List<String> headers, List<List<Object>> rows) {
        StringBuilder out = new StringBuilder("﻿");
        line(out, headers.stream().map(TabularExport::text).toList());
        for (List<Object> row : rows) line(out, row.stream().map(TabularExport::cell).toList());
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    static byte[] xlsx(List<Sheet> sheets) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            Workbook book = new Workbook(out, "Stockly", "1.0");
            for (Sheet s : sheets) {
                Worksheet sheet = book.newWorksheet(s.name());
                for (int c = 0; c < s.headers().size(); c++) sheet.value(0, c, s.headers().get(c));
                sheet.range(0, 0, 0, s.headers().size() - 1).style().bold().set();
                int r = 1;
                for (List<Object> row : s.rows()) {
                    for (int c = 0; c < row.size(); c++) {
                        Object v = row.get(c);
                        if (v instanceof Number n) sheet.value(r, c, n);
                        else if (v != null) sheet.value(r, c, v.toString());
                    }
                    r++;
                }
                int last = Math.max(1, r - 1);
                s.formats().forEach((col, pattern) -> sheet.range(1, col, last, col).style().format(pattern).set());
            }
            book.finish();
        } catch (IOException e) { throw new IdentityFailure(500, "EXPORT_FAILED", "No pudimos generar el archivo. Intenta de nuevo."); }
        return out.toByteArray();
    }

    private static String cell(Object value) {
        if (value == null) return "";
        if (value instanceof BigDecimal n) return n.toPlainString();
        if (value instanceof Number n) return n.toString();
        return text(value.toString());
    }

    private static void line(StringBuilder out, List<String> cells) { out.append(String.join(",", cells)).append("\r\n"); }

    /** Texto entre comillas; si empieza con = + - @ o un control, se antepone ' para que la hoja no lo ejecute como fórmula. */
    static String text(String value) {
        String v = value == null ? "" : value;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) v = "'" + v;
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    static String slug(String value) {
        String plain = plain(value);
        String s = plain.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return s.isEmpty() ? "sucursal" : s;
    }

    /** Minúsculas sin tildes, para búsquedas y nombres de archivo. */
    static String plain(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
