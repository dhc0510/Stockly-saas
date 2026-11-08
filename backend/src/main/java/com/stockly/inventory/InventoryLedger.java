package com.stockly.inventory;

import com.stockly.identity.IdentityFailure;
import com.stockly.identity.StocklyPrincipal;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Saldos, folios y movimientos compartidos por entradas, salidas y ajustes. */
@Component
public class InventoryLedger {
    static final String PRINCIPAL = "principal";
    static final int PAGE_SIZE = 25;
    static final int MAX_LINES = 200;
    /** Búsqueda sin distinguir tildes ni mayúsculas, sin depender de la colación por defecto de la base. */
    static final String ACCENT_INSENSITIVE = "COLLATE utf8mb4_0900_ai_ci";
    static final BigDecimal MAX_QUANTITY = new BigDecimal("1000000");
    static final BigDecimal MAX_BALANCE = new BigDecimal("999999999999999.999");
    static final String MOVEMENTS = """
        SELECT m.id,m.kind,m.folio,COALESCE(actor.full_name,m.actor_name) AS actor_name,m.reason,m.reference,m.created_at,COALESCE(branch.name,l.name) AS location,
               r.id AS void_id,r.folio AS void_folio,r.reason AS void_reason,
               o.id AS original_id,o.folio AS original_folio
        FROM inventory_movements m
        JOIN inventory_locations l ON l.company_id=m.company_id AND l.id=m.location_id
        LEFT JOIN inventory_branches branch ON branch.company_id=l.company_id AND branch.location_id=l.id
        LEFT JOIN users actor ON actor.company_id=m.company_id AND actor.id=m.actor_id
        LEFT JOIN inventory_movements r ON r.company_id=m.company_id AND r.reversed_id=m.id
        LEFT JOIN inventory_movements o ON o.company_id=m.company_id AND o.id=m.reversed_id
        WHERE m.company_id=?
        """;

    enum Series {
        RECEIPT("receipt_sequence", "ENT-"), VOID("void_sequence", "ANU-"),
        OUT("out_sequence", "SAL-"), ADJUST("adjust_sequence", "AJU-"), SALE("sale_sequence", "VTA-"), RETURN("return_sequence", "DEV-");
        final String column, prefix;
        Series(String column, String prefix) { this.column = column; this.prefix = prefix; }
    }

    private final JdbcTemplate jdbc;

    InventoryLedger(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    List<Article> items(String company, String location, boolean legacy, String search, boolean barcode, Rules rules) {
        String value = rules.optional(search, 160);
        if (value == null) return List.of();
        String filter = rules.term(value);
        String condition = barcode ? "i.barcode=?" : """
            (p.name COLLATE utf8mb4_0900_ai_ci LIKE ? ESCAPE '!' OR i.variant_name COLLATE utf8mb4_0900_ai_ci LIKE ? ESCAPE '!'
             OR i.sku COLLATE utf8mb4_0900_ai_ci LIKE ? ESCAPE '!' OR i.barcode COLLATE utf8mb4_0900_ai_ci LIKE ? ESCAPE '!')
            """;
        Object[] params = barcode ? new Object[]{location, company, legacy, location, value} : new Object[]{location, company, legacy, location, filter, filter, filter, filter};
        return jdbc.query("""
            SELECT i.id,i.sku,i.barcode,i.unit,p.name,i.variant_name,COALESCE(b.quantity,0) AS balance
            FROM catalog_items i JOIN catalog_products p ON p.company_id=i.company_id AND p.id=i.product_id
            LEFT JOIN inventory_balances b ON b.company_id=i.company_id AND b.item_id=i.id AND b.location_id=?
            WHERE i.company_id=? AND i.active AND p.active AND (? OR EXISTS (
                SELECT 1 FROM inventory_branch_items bi JOIN inventory_branches br
                  ON br.company_id=bi.company_id AND br.id=bi.branch_id
                WHERE bi.company_id=i.company_id AND bi.item_id=i.id AND br.location_id=?
            )) AND
            """ + condition + " ORDER BY p.name,i.variant_name,i.id LIMIT 12", (rs,row) -> article(rs), params);
    }

    void lockLocation(String company) {
        jdbc.update("""
            INSERT INTO inventory_locations(company_id,id,name) VALUES (?,'principal','Principal')
            ON DUPLICATE KEY UPDATE name=name
            """, company);
        jdbc.queryForObject("SELECT id FROM inventory_locations WHERE company_id=? AND id=? FOR UPDATE", String.class, company, PRINCIPAL);
    }

    /** Devuelve el movimiento ya registrado con esta clave; rechaza la clave si los datos son distintos. */
    Optional<String> registered(String company, String key, String hash, Rules rules) {
        var existing = jdbc.query("""
            SELECT id,request_hash FROM inventory_movements WHERE company_id=? AND request_key=?
            """, (rs,row) -> new String[]{rs.getString(1),rs.getString(2)}, company, key);
        if (existing.isEmpty()) return Optional.empty();
        if (!hash.equals(existing.getFirst()[1])) throw rules.conflict("La confirmación anterior tiene otros datos. Revisa el comprobante original.");
        return Optional.of(existing.getFirst()[0]);
    }

    String nextFolio(String company, Series series) {
        jdbc.update("UPDATE inventory_locations SET " + series.column + "=" + series.column + "+1 WHERE company_id=? AND id=?", company, PRINCIPAL);
        Long number = jdbc.queryForObject("SELECT " + series.column + " FROM inventory_locations WHERE company_id=? AND id=?",
            Long.class, company, PRINCIPAL);
        return series.prefix + String.format(Locale.ROOT, "%04d", number);
    }

    Article lockedArticle(String company, String id, Rules rules) {
        return jdbc.query("""
            SELECT i.id,i.sku,i.barcode,i.unit,p.name,i.variant_name,0 AS balance
            FROM catalog_items i JOIN catalog_products p ON p.company_id=i.company_id AND p.id=i.product_id
            WHERE i.company_id=? AND i.id=? AND i.active AND p.active FOR UPDATE
            """, (rs,row) -> article(rs), company, id).stream().findFirst()
            .orElseThrow(() -> rules.invalid("Un artículo ya no está activo o disponible en esta empresa."));
    }

    BigDecimal balance(String company, String location, String item) {
        return jdbc.queryForList("""
            SELECT quantity FROM inventory_balances WHERE company_id=? AND location_id=? AND item_id=? FOR UPDATE
            """, BigDecimal.class, company, location, item).stream().findFirst().orElse(BigDecimal.ZERO);
    }

    private void insertMovement(StocklyPrincipal actor, String location, String id, String kind, String folio, String reason,
                        String reference, String key, String hash, String reversedId) {
        String company = actor.companyId();
        String actorName = jdbc.queryForObject("SELECT full_name FROM users WHERE company_id=? AND id=?",
            String.class, company, actor.accountId());
        jdbc.update("""
            INSERT INTO inventory_movements(company_id,id,location_id,kind,folio,actor_id,actor_name,reason,reference,request_key,request_hash,reversed_id)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
            """, company, id, location, kind, folio, actor.accountId(), actorName, reason, reference, key, hash, reversedId);
    }

    private void writeLine(String company, String location, String movement, Line line) {
        jdbc.update("""
            INSERT INTO inventory_balances(company_id,location_id,item_id,quantity) VALUES (?,?,?,?) AS updated
            ON DUPLICATE KEY UPDATE quantity=updated.quantity
            """, company, location, line.itemId(), line.balanceAfter());
        jdbc.update("""
            INSERT INTO inventory_movement_lines(company_id,movement_id,item_id,item_name,sku,unit,quantity,balance_after)
            VALUES (?,?,?,?,?,?,?,?)
            """, company, movement, line.itemId(), line.name(), line.sku(), line.unit(), line.quantity(), line.balanceAfter());
    }

    /** Registra una operación confirmada: movimiento, líneas, saldos y primer movimiento de cada artículo. */
    void record(StocklyPrincipal actor, String id, String kind, Series series, String reason, String reference,
                String key, String hash, List<Line> lines) {
        record(actor, PRINCIPAL, id, kind, series, reason, reference, key, hash, lines);
    }

    void record(StocklyPrincipal actor, String location, String id, String kind, Series series, String reason,
                String reference, String key, String hash, List<Line> lines) {
        String company = actor.companyId();
        record(actor, location, id, kind, nextFolio(company, series), reason, reference, key, hash, lines);
    }

    void record(StocklyPrincipal actor, String location, String id, String kind, String folio, String reason,
                String reference, String key, String hash, List<Line> lines) {
        String company = actor.companyId();
        insertMovement(actor, location, id, kind, folio, reason, reference, key, hash, null);
        for (Line line : lines) {
            writeLine(company, location, id, line);
            jdbc.update("UPDATE catalog_items SET has_movements=TRUE WHERE company_id=? AND id=?", company, line.itemId());
        }
    }

    /** Aplica el inverso de cada línea; si alguna quedaría negativa, rechaza toda la anulación. */
    String reverse(StocklyPrincipal actor, String originalId, List<Line> original, String reason, Rules rules) {
        String company = actor.companyId();
        String location = jdbc.queryForObject("""
            SELECT location_id FROM inventory_movements WHERE company_id=? AND id=?
            """, String.class, company, originalId);
        var lines = new ArrayList<Line>();
        for (Line previous : original) {
            BigDecimal after = balance(company, location, previous.itemId()).subtract(previous.quantity());
            if (after.signum() < 0) throw rules.conflict("La anulación dejaría existencias negativas para " + previous.name() + ".");
            if (after.compareTo(MAX_BALANCE) > 0) throw rules.conflict("La anulación excede la existencia máxima permitida.");
            lines.add(new Line(previous.itemId(), previous.name(), previous.sku(), previous.unit(), previous.quantity().negate(), after));
        }
        String reversal = UUID.randomUUID().toString();
        insertMovement(actor, location, reversal, "VOID", nextFolio(company, Series.VOID), reason, null, null, null, originalId);
        for (Line line : lines) writeLine(company, location, reversal, line);
        return reversal;
    }

    /** Lee un movimiento dentro del alcance indicado; scope es SQL fijo de cada módulo, nunca datos del usuario. */
    Optional<Document> document(String company, String id, String scope, Object... access) {
        var parameters = new ArrayList<Object>(List.of(company, id));
        parameters.addAll(Arrays.asList(access));
        var movement = jdbc.query(MOVEMENTS + " AND m.id=? AND " + scope, (rs,row) -> summary(rs), parameters.toArray());
        if (movement.isEmpty()) return Optional.empty();
        List<Line> lines = jdbc.query("""
            SELECT item_id,item_name,sku,unit,quantity,balance_after FROM inventory_movement_lines
            WHERE company_id=? AND movement_id=? ORDER BY item_name,item_id
            """, (rs,row) -> new Line(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getBigDecimal(5),rs.getBigDecimal(6)), company, id);
        return Optional.of(new Document(movement.getFirst(), lines));
    }

    static Article article(ResultSet rs) throws SQLException {
        String variant = rs.getString("variant_name");
        return new Article(rs.getString("id"), rs.getString("name") + (variant == null ? "" : " · " + variant),
            rs.getString("sku"), rs.getString("barcode"), rs.getString("unit"), rs.getBigDecimal("balance"));
    }

    static Summary summary(ResultSet rs) throws SQLException {
        return new Summary(rs.getString("id"),rs.getString("kind"),rs.getString("folio"),rs.getString("actor_name"),
            rs.getString("reason"),rs.getString("reference"),rs.getTimestamp("created_at").toInstant(),rs.getString("location"),
            rs.getString("void_id"),rs.getString("void_folio"),rs.getString("void_reason"),rs.getString("original_id"),rs.getString("original_folio"));
    }

    static String hash(String... fields) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            for (String field : fields) {
                digest.update((field.length() + ":").getBytes(StandardCharsets.UTF_8));
                digest.update(field.getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    /** Validación de textos y errores, con los códigos de cada módulo. */
    record Rules(String invalidCode, String conflictCode) {
        IdentityFailure invalid(String message) { return new IdentityFailure(400, invalidCode, message); }
        IdentityFailure conflict(String message) { return new IdentityFailure(409, conflictCode, message); }

        String term(String input) {
            String value = optional(input, 160);
            return "%" + (value == null ? "" : value.replace("!","!!").replace("%","!%").replace("_","!_")) + "%";
        }

        String uuid(String input, String error) {
            try {
                if (input == null || input.length() != 36) throw invalid(error);
                return UUID.fromString(input).toString();
            } catch (IllegalArgumentException e) { throw invalid(error); }
        }

        String required(String input, int max, String message) {
            String value = optional(input, max);
            if (value == null) throw invalid(message);
            return value;
        }

        String optional(String input, int max) {
            if (input == null || input.isBlank()) return null;
            String value = input.strip();
            if (value.length() > max) throw invalid("Un campo excede el máximo de " + max + " caracteres.");
            return value;
        }
    }

    public record Article(String id,String name,String sku,String barcode,String unit,BigDecimal balance) {}
    public record Line(String itemId,String name,String sku,String unit,BigDecimal quantity,BigDecimal balanceAfter) {}
    public record Summary(String id,String kind,String folio,String actor,String reason,String reference,Instant date,String location,
                          String voidId,String voidFolio,String voidReason,String originalId,String originalFolio) {}
    public record Document(Summary movement,List<Line> lines) {}
    public record Page(List<Summary> entries,boolean hasMore) {}
}
