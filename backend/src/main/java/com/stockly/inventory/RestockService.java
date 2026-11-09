package com.stockly.inventory;

import com.stockly.identity.IdentityFailure;
import com.stockly.identity.StocklyPrincipal;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import static com.stockly.inventory.InventoryContracts.StockItem;

/** STK-016: lista «Por reponer», cantidad sugerida y exportación. Reutiliza la consulta y los permisos de Existencias (STK-007). */
@Service
public class RestockService {
    static final ZoneId COSTA_RICA = ZoneId.of("America/Costa_Rica");
    private static final Map<String,String> STATE_LABEL = Map.of("EXHAUSTED","Agotado","MINIMUM","Stock mínimo","LOW","Stock bajo");
    private static final Map<String,String> BASIS_LABEL = Map.of("TARGET","Nivel deseado","RECOVERY","Mínimo + margen de recuperación","NONE","Sin dato para sugerir");

    public record RestockRow(String productId, String productName, String category, String itemId, String variantName, String sku,
                             String unit, String branchId, String branchName, String state, BigDecimal available,
                             BigDecimal minimumQuantity, BigDecimal recoveryMargin, BigDecimal targetQuantity,
                             BigDecimal toOrder, String basis) {}
    public record PausedRow(String productId, String productName, String itemId, String variantName, String sku, String unit,
                            String branchId, String branchName, BigDecimal available, String pausedBy, Instant pausedAt) {}
    public record Totals(int exhausted, int minimum, int low) {}
    public record Restock(String companyName, List<RestockRow> rows, Totals totals, List<PausedRow> paused) {}
    public record Export(String fileName, String contentType, byte[] content) {}

    private final InventoryService inventory;
    private final JdbcTemplate jdbc;

    public RestockService(InventoryService inventory, JdbcTemplate jdbc) { this.inventory = inventory; this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public Restock list(StocklyPrincipal actor, String branchId, String search, String category, boolean includeLow) {
        List<StockItem> items = inventory.items(actor, blank(branchId), search, category, "ALL", "ALERTS");
        List<StockItem> active = items.stream().filter(i -> !i.restockPaused()).toList();
        Totals totals = new Totals(count(active, "EXHAUSTED"), count(active, "MINIMUM"), count(active, "LOW"));
        List<RestockRow> rows = active.stream()
            .filter(i -> "EXHAUSTED".equals(i.state()) || "MINIMUM".equals(i.state()) || includeLow && "LOW".equals(i.state()))
            .map(RestockService::row).toList();
        String company = jdbc.queryForList("SELECT commercial_name FROM companies WHERE id=?", String.class, actor.companyId()).stream().findFirst().orElse("");
        return new Restock(company, rows, totals, paused(actor, items.stream().filter(StockItem::restockPaused).toList()));
    }

    /** Contador del menú: agotados y en mínimo de todas las sucursales autorizadas, sin los marcados «No reponer». */
    @Transactional(readOnly = true)
    public int count(StocklyPrincipal actor) {
        return (int) inventory.items(actor, null, "", "", "ALL", "NAME").stream()
            .filter(i -> !i.restockPaused() && ("EXHAUSTED".equals(i.state()) || "MINIMUM".equals(i.state()))).count();
    }

    @Transactional(readOnly = true)
    public Export export(StocklyPrincipal actor, String format, String branchId, String search, String category, boolean includeLow) {
        String kind = format == null ? "" : format.strip().toLowerCase(Locale.ROOT);
        if (!kind.equals("csv") && !kind.equals("xlsx")) throw new IdentityFailure(400, "INVENTORY_INVALID", "Elige Excel o CSV para exportar.");
        List<RestockRow> rows = list(actor, branchId, search, category, includeLow).rows();
        String place = blank(branchId) == null ? "todas" : slug(rows.isEmpty() ? branchName(actor, branchId) : rows.get(0).branchName());
        String name = "por-reponer-" + place + "-" + LocalDate.now(COSTA_RICA) + "." + kind;
        return kind.equals("csv")
            ? new Export(name, "text/csv; charset=UTF-8", csv(rows))
            : new Export(name, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx(rows));
    }

    /** Cantidad sugerida exacta, sin redondear (el saldo admite 3 decimales desde STK-008): nivel deseado − disponible; si no hay, mínimo + margen − disponible. */
    static RestockRow row(StockItem i) {
        BigDecimal toOrder; String basis;
        if (i.targetQuantity() != null) { toOrder = i.targetQuantity().subtract(i.available()); basis = "TARGET"; }
        else if (i.minimumQuantity() != null) { toOrder = i.minimumQuantity().add(i.recoveryMargin()).subtract(i.available()); basis = "RECOVERY"; }
        else { toOrder = null; basis = "NONE"; }
        if (toOrder != null) toOrder = toOrder.signum() <= 0 ? BigDecimal.ZERO.setScale(2) : toOrder.setScale(Math.max(2, toOrder.stripTrailingZeros().scale()), RoundingMode.UNNECESSARY);
        return new RestockRow(i.productId(), i.productName(), i.category(), i.itemId(), i.variantName(), i.sku(), i.unit(),
            i.branchId(), i.branchName(), i.state(), i.available(), i.minimumQuantity(), i.recoveryMargin(), i.targetQuantity(), toOrder, basis);
    }

    private List<PausedRow> paused(StocklyPrincipal actor, List<StockItem> items) {
        if (items.isEmpty()) return List.of();
        Map<String,Object[]> who = new HashMap<>();
        jdbc.query("""
            SELECT bi.branch_id,bi.item_id,u.full_name,bi.restock_paused_at FROM inventory_branch_items bi
            LEFT JOIN users u ON u.company_id=bi.company_id AND u.id=bi.restock_paused_by
            WHERE bi.company_id=? AND bi.restock_paused=TRUE
            """, rs -> { Timestamp at = rs.getTimestamp(4);
                who.put(rs.getString(1) + "/" + rs.getString(2), new Object[]{rs.getString(3), at == null ? null : at.toInstant()}); },
            actor.companyId());
        return items.stream().map(i -> { Object[] w = who.getOrDefault(i.branchId() + "/" + i.itemId(), new Object[]{null, null});
            return new PausedRow(i.productId(), i.productName(), i.itemId(), i.variantName(), i.sku(), i.unit(), i.branchId(), i.branchName(),
                i.available(), (String) w[0], (Instant) w[1]); }).toList();
    }

    private String branchName(StocklyPrincipal actor, String branchId) {
        return jdbc.queryForList("SELECT name FROM inventory_branches WHERE company_id=? AND id=?", String.class, actor.companyId(), branchId)
            .stream().findFirst().orElse("sucursal");
    }

    private static final String[] HEADERS = {"Sucursal","Producto","Variante","SKU","Categoría","Estado","Disponible","Unidad","Mínimo","Nivel deseado","A pedir","Base del cálculo"};

    static byte[] csv(List<RestockRow> rows) {
        StringBuilder out = new StringBuilder("﻿");
        line(out, Arrays.stream(HEADERS).map(RestockService::text).toList());
        for (RestockRow r : rows) line(out, List.of(text(r.branchName()), text(r.productName()), text(r.variantName()), text(r.sku()),
            text(r.category()), text(STATE_LABEL.get(r.state())), num(r.available()), text(r.unit()), num(r.minimumQuantity()),
            num(r.targetQuantity()), num(r.toOrder()), text(BASIS_LABEL.get(r.basis()))));
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    static byte[] xlsx(List<RestockRow> rows) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            Workbook book = new Workbook(out, "Stockly", "1.0");
            Worksheet sheet = book.newWorksheet("Por reponer");
            for (int c = 0; c < HEADERS.length; c++) sheet.value(0, c, HEADERS[c]);
            sheet.range(0, 0, 0, HEADERS.length - 1).style().bold().set();
            int r = 1;
            for (RestockRow row : rows) {
                Object[] cells = {row.branchName(), row.productName(), row.variantName(), row.sku(), row.category(), STATE_LABEL.get(row.state()),
                    row.available(), row.unit(), row.minimumQuantity(), row.targetQuantity(), row.toOrder(), BASIS_LABEL.get(row.basis())};
                for (int c = 0; c < cells.length; c++) {
                    if (cells[c] instanceof BigDecimal n) sheet.value(r, c, n);
                    else if (cells[c] != null) sheet.value(r, c, (String) cells[c]);
                }
                r++;
            }
            sheet.range(1, 6, Math.max(1, r - 1), 10).style().format("#,##0.00").set();
            book.finish();
        } catch (IOException e) { throw new IdentityFailure(500, "EXPORT_FAILED", "No pudimos generar el archivo. Intenta de nuevo."); }
        return out.toByteArray();
    }

    private static void line(StringBuilder out, List<String> cells) { out.append(String.join(",", cells)).append("\r\n"); }
    private static String num(BigDecimal value) { return value == null ? "" : value.toPlainString(); }
    /** Texto entre comillas; si empieza con = + - @ o un control, se antepone ' para que la hoja no lo ejecute como fórmula. */
    static String text(String value) {
        String v = value == null ? "" : value;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) v = "'" + v;
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }
    static String slug(String value) {
        String plain = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        String s = plain.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return s.isEmpty() ? "sucursal" : s;
    }
    private static int count(List<StockItem> items, String state) { return (int) items.stream().filter(i -> state.equals(i.state())).count(); }
    private static String blank(String value) { return value == null || value.isBlank() ? null : value; }
}
