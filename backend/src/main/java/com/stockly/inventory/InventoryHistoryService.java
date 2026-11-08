package com.stockly.inventory;

import static com.stockly.inventory.InventoryLedger.*;

import com.stockly.identity.IdentityFailure;
import com.stockly.identity.StocklyPrincipal;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consulta de solo lectura de todos los movimientos de inventario de la empresa. */
@Service
public class InventoryHistoryService {
    private static final Rules RULES = new Rules("HISTORY_INVALID", "HISTORY_CONFLICT");
    private static final ZoneId COSTA_RICA = ZoneId.of("America/Costa_Rica");
    private static final Set<String> KINDS = Set.of("RECEIPT", "OUT", "ADJUST", "SALE", "RETURN", "VOID");
    private static final String ROWS = """
        SELECT m.id,m.kind,m.folio,m.created_at,COALESCE(actor.full_name,m.actor_name) AS actor_name,m.reason,m.reference,
               r.folio AS void_folio,o.folio AS original_folio,
               l.item_id,l.item_name,l.sku,l.unit,l.quantity,l.balance_after,
               COALESCE(i.active AND p.active,FALSE) AS item_active
        FROM inventory_movement_lines l
        JOIN inventory_movements m ON m.company_id=l.company_id AND m.id=l.movement_id
        LEFT JOIN users actor ON actor.company_id=m.company_id AND actor.id=m.actor_id
        LEFT JOIN inventory_movements r ON r.company_id=m.company_id AND r.reversed_id=m.id
        LEFT JOIN inventory_movements o ON o.company_id=m.company_id AND o.id=m.reversed_id
        LEFT JOIN catalog_items i ON i.company_id=l.company_id AND i.id=l.item_id
        LEFT JOIN catalog_products p ON p.company_id=i.company_id AND p.id=i.product_id
        WHERE l.company_id=?
        """;
    private final JdbcTemplate jdbc;
    private final InventoryLedger ledger;

    InventoryHistoryService(JdbcTemplate jdbc, InventoryLedger ledger) { this.jdbc = jdbc; this.ledger = ledger; }

    @Transactional(readOnly = true)
    public RowPage list(StocklyPrincipal actor, String search, String itemId, String type, String actorId,
                        String from, String to, int page) {
        String company = company(actor);
        if (page < 0) throw RULES.invalid("Revisa la página.");
        if (!type.isEmpty() && !KINDS.contains(type)) throw RULES.invalid("Revisa el tipo de movimiento.");
        LocalDate start = day(from), end = day(to), today = LocalDate.now(COSTA_RICA);
        if ((start != null && start.isAfter(today)) || (end != null && end.isAfter(today)))
            throw RULES.invalid("No se aceptan fechas futuras.");
        if (start != null && end != null && start.isAfter(end))
            throw RULES.invalid("La fecha inicial no puede ser posterior a la final.");

        var sql = new StringBuilder(ROWS).append(" AND ").append(InventoryScope.VISIBLE_MOVEMENT);
        var params = new ArrayList<Object>(List.of(company, InventoryScope.administrator(actor), actor.accountId()));
        String term = RULES.optional(search, 160);
        if (term != null) {
            String filter = RULES.term(term);
            sql.append(" AND (m.folio ").append(ACCENT_INSENSITIVE).append(" LIKE ? ESCAPE '!' OR COALESCE(m.reference,'') ")
                .append(ACCENT_INSENSITIVE).append(" LIKE ? ESCAPE '!')");
            params.add(filter); params.add(filter);
        }
        if (!itemId.isEmpty()) {
            sql.append(" AND l.item_id=?");
            params.add(RULES.uuid(itemId, "Identificador de artículo inválido."));
        }
        if (!type.isEmpty()) { sql.append(" AND m.kind=?"); params.add(type); }
        if (!actorId.isEmpty()) {
            sql.append(" AND m.actor_id=?");
            params.add(RULES.uuid(actorId, "Identificador de responsable inválido."));
        }
        if (start != null) { sql.append(" AND m.created_at>=?"); params.add(Timestamp.from(startOf(start))); }
        if (end != null) { sql.append(" AND m.created_at<?"); params.add(Timestamp.from(startOf(end.plusDays(1)))); }
        sql.append(" ORDER BY m.created_at DESC,m.id DESC,l.item_name,l.item_id LIMIT ? OFFSET ?");
        params.add(PAGE_SIZE + 1); params.add((long) page * PAGE_SIZE);

        var rows = jdbc.query(sql.toString(), (rs, row) -> new Row(rs.getString("id"), rs.getString("kind"), rs.getString("folio"),
            rs.getTimestamp("created_at").toInstant(), rs.getString("actor_name"), rs.getString("reason"), rs.getString("reference"),
            rs.getString("void_folio"), rs.getString("original_folio"), rs.getString("item_id"), rs.getString("item_name"),
            rs.getString("sku"), rs.getString("unit"), rs.getBigDecimal("quantity"), rs.getBigDecimal("balance_after"),
            rs.getBoolean("item_active")), params.toArray());
        return new RowPage(rows.stream().limit(PAGE_SIZE).toList(), rows.size() > PAGE_SIZE);
    }

    public List<ItemOption> items(StocklyPrincipal actor, String search) {
        String company = company(actor);
        String value = RULES.optional(search, 160);
        if (value == null) return List.of();
        String filter = RULES.term(value);
        return jdbc.query("""
            SELECT i.id,p.name,i.variant_name,i.sku,i.barcode,i.unit,(i.active AND p.active) AS item_active
            FROM catalog_items i JOIN catalog_products p ON p.company_id=i.company_id AND p.id=i.product_id
            WHERE i.company_id=? AND (p.name %1$s LIKE ? ESCAPE '!' OR i.variant_name %1$s LIKE ? ESCAPE '!'
              OR i.sku %1$s LIKE ? ESCAPE '!' OR i.barcode %1$s LIKE ? ESCAPE '!')
            ORDER BY p.name,i.variant_name,i.id LIMIT 12
            """.formatted(ACCENT_INSENSITIVE), (rs, row) -> option(rs), company, filter, filter, filter, filter);
    }

    public List<ActorOption> actors(StocklyPrincipal actor) {
        return jdbc.query("""
            SELECT m.actor_id,MAX(COALESCE(u.full_name,m.actor_name)) AS actor_name
            FROM inventory_movements m LEFT JOIN users u ON u.company_id=m.company_id AND u.id=m.actor_id
            WHERE m.company_id=? AND
            """ + InventoryScope.VISIBLE_MOVEMENT + """
            GROUP BY m.actor_id ORDER BY actor_name,m.actor_id
            """, (rs, row) -> new ActorOption(rs.getString(1), rs.getString(2)), company(actor), InventoryScope.administrator(actor), actor.accountId());
    }

    @Transactional(readOnly = true)
    public ItemSummary item(StocklyPrincipal actor, String id) {
        String company = company(actor);
        String item = RULES.uuid(id, "Identificador de artículo inválido.");
        var option = jdbc.query("""
            SELECT i.id,p.name,i.variant_name,i.sku,i.barcode,i.unit,(i.active AND p.active) AS item_active
            FROM catalog_items i JOIN catalog_products p ON p.company_id=i.company_id AND p.id=i.product_id
            WHERE i.company_id=? AND i.id=?
            """, (rs, row) -> option(rs), company, item).stream().findFirst().orElseThrow(StockMissing::item);
        BigDecimal balance = jdbc.queryForList("""
            SELECT COALESCE(SUM(m.quantity),0) FROM inventory_balances m WHERE m.company_id=? AND m.item_id=? AND
            """ + InventoryScope.VISIBLE_MOVEMENT + """

            """, BigDecimal.class, company, item, InventoryScope.administrator(actor), actor.accountId()).stream().findFirst().orElse(BigDecimal.ZERO);
        return new ItemSummary(option, balance);
    }

    @Transactional(readOnly = true)
    public Document movement(StocklyPrincipal actor, String id) {
        return ledger.document(company(actor), RULES.uuid(id, "Identificador de movimiento inválido."),
            InventoryScope.VISIBLE_MOVEMENT, InventoryScope.administrator(actor), actor.accountId())
            .orElseThrow(StockMissing::movement);
    }

    private static ItemOption option(java.sql.ResultSet rs) throws java.sql.SQLException {
        String variant = rs.getString("variant_name");
        return new ItemOption(rs.getString("id"), rs.getString("name") + (variant == null ? "" : " · " + variant),
            rs.getString("sku"), rs.getString("barcode"), rs.getString("unit"), rs.getBoolean("item_active"));
    }

    private static LocalDate day(String value) {
        if (value == null || value.isBlank()) return null;
        try { return LocalDate.parse(value.strip()); }
        catch (DateTimeParseException error) { throw RULES.invalid("Revisa las fechas del periodo."); }
    }

    private static Instant startOf(LocalDate day) { return day.atStartOfDay(COSTA_RICA).toInstant(); }

    private static String company(StocklyPrincipal actor) {
        if (actor == null || actor.companyId() == null || actor.mustChangePassword())
            throw new IdentityFailure(403, "FORBIDDEN", "No tienes acceso al historial.");
        return actor.companyId();
    }

    private static final class StockMissing {
        static IdentityFailure item() { return new IdentityFailure(404, "HISTORY_ITEM_NOT_FOUND", "No se encontró el artículo."); }
        static IdentityFailure movement() { return new IdentityFailure(404, "HISTORY_MOVEMENT_NOT_FOUND", "No se encontró el movimiento."); }
    }

    public record Row(String movementId, String kind, String folio, Instant date, String actor, String reason, String reference,
                      String voidFolio, String originalFolio, String itemId, String itemName, String sku, String unit,
                      BigDecimal quantity, BigDecimal balanceAfter, boolean itemActive) {}
    public record RowPage(List<Row> rows, boolean hasMore) {}
    public record ItemOption(String id, String name, String sku, String barcode, String unit, boolean active) {}
    public record ItemSummary(ItemOption item, BigDecimal balance) {}
    public record ActorOption(String id, String name) {}
}
