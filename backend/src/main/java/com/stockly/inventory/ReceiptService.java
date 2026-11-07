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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReceiptService {
    private static final String PRINCIPAL = "principal";
    private static final int PAGE_SIZE = 25;
    private static final int MAX_LINES = 200;
    private static final BigDecimal MAX_QUANTITY = new BigDecimal("1000000");
    private static final BigDecimal MAX_BALANCE = new BigDecimal("999999999999999.999");
    private static final String MOVEMENTS = """
        SELECT m.id,m.kind,m.folio,COALESCE(actor.full_name,m.actor_name) AS actor_name,m.reason,m.reference,m.created_at,l.name AS location,
               r.id AS void_id,r.folio AS void_folio,r.reason AS void_reason,
               o.id AS original_id,o.folio AS original_folio
        FROM inventory_movements m
        JOIN inventory_locations l ON l.company_id=m.company_id AND l.id=m.location_id
        LEFT JOIN users actor ON actor.company_id=m.company_id AND actor.id=m.actor_id
        LEFT JOIN inventory_movements r ON r.company_id=m.company_id AND r.reversed_id=m.id
        LEFT JOIN inventory_movements o ON o.company_id=m.company_id AND o.id=m.reversed_id
        WHERE m.company_id=?
        """;
    private final JdbcTemplate jdbc;

    ReceiptService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public Page list(StocklyPrincipal actor, String search, String status, int page) {
        String company = company(actor);
        if (!Set.of("", "confirmed", "voided").contains(status) || page < 0)
            throw invalid("Revisa el estado y la página.");
        String filter = term(search);
        var entries = jdbc.query(MOVEMENTS + """
            AND m.kind='RECEIPT'
            AND (m.folio LIKE ? ESCAPE '!' OR COALESCE(m.reference,'') LIKE ? ESCAPE '!')
            AND (?='' OR (?='voided' AND r.id IS NOT NULL) OR (?='confirmed' AND r.id IS NULL))
            ORDER BY m.created_at DESC,m.id DESC LIMIT ? OFFSET ?
            """, (rs, row) -> summary(rs), company, filter, filter, status, status, status, PAGE_SIZE + 1, (long) page * PAGE_SIZE);
        return new Page(entries.stream().limit(PAGE_SIZE).toList(), entries.size() > PAGE_SIZE);
    }

    public List<Article> items(StocklyPrincipal actor, String search, boolean barcode) {
        String company = company(actor);
        String value = optional(search, 160);
        if (value == null) return List.of();
        String filter = term(value);
        String condition = barcode ? "i.barcode=?" : """
            (p.name LIKE ? ESCAPE '!' OR i.variant_name LIKE ? ESCAPE '!'
             OR i.sku LIKE ? ESCAPE '!' OR i.barcode LIKE ? ESCAPE '!')
            """;
        Object[] params = barcode ? new Object[]{company, value} : new Object[]{company, filter, filter, filter, filter};
        return jdbc.query("""
            SELECT i.id,i.sku,i.barcode,i.unit,p.name,i.variant_name,COALESCE(b.quantity,0) AS balance
            FROM catalog_items i JOIN catalog_products p ON p.company_id=i.company_id AND p.id=i.product_id
            LEFT JOIN inventory_balances b ON b.company_id=i.company_id AND b.item_id=i.id AND b.location_id='principal'
            WHERE i.company_id=? AND i.active AND p.active AND
            """ + condition + " ORDER BY p.name,i.variant_name,i.id LIMIT 12", (rs,row) -> article(rs), params);
    }

    @Transactional(readOnly = true)
    public Receipt get(StocklyPrincipal actor, String id) { return receipt(company(actor), id); }

    @Transactional
    public Receipt confirm(StocklyPrincipal actor, ReceiptInput input) {
        String company = company(actor);
        String key = uuid(input.requestKey(), "Identificador de confirmación inválido.");
        String reason = switch (input.reason() == null ? "" : input.reason()) {
            case "RECEIPT" -> "Recepción de mercancía";
            case "INITIAL" -> "Carga inicial";
            case "OTHER" -> "Otro · " + required(input.detail(), 150, "Escribe el detalle del motivo Otro.");
            default -> throw invalid("Selecciona el motivo de la entrada.");
        };
        String reference = optional(input.reference(), 100);
        var quantities = validateLines(input.lines());
        String hash = hash(actor.accountId(), reason, reference, quantities);
        lockLocation(company);
        var existing = jdbc.query("""
            SELECT id,request_hash FROM inventory_movements WHERE company_id=? AND request_key=?
            """, (rs,row) -> new String[]{rs.getString(1),rs.getString(2)}, company, key);
        if (!existing.isEmpty()) {
            if (!hash.equals(existing.getFirst()[1])) throw conflict("La confirmación anterior tiene otros datos. Revisa el comprobante original.");
            return receipt(company, existing.getFirst()[0]);
        }

        var lines = new ArrayList<Line>();
        for (var entry : quantities.entrySet()) {
            Article article = lockedArticle(company, entry.getKey());
            BigDecimal quantity = entry.getValue();
            if (article.unit().equals("unidad") && quantity.scale() > 0)
                throw invalid(article.name() + ": para unidad solo se aceptan números enteros.");
            BigDecimal balance = balance(company, article.id()).add(quantity);
            if (balance.compareTo(MAX_BALANCE) > 0) throw conflict("La entrada excede la existencia máxima permitida.");
            lines.add(new Line(article.id(), article.name(), article.sku(), article.unit(), quantity, balance));
        }
        String id = UUID.randomUUID().toString();
        insertMovement(actor, id, "RECEIPT", nextFolio(company, false), reason, reference, key, hash, null);
        for (Line line : lines) {
            writeLine(company, id, line);
            jdbc.update("UPDATE catalog_items SET has_movements=TRUE WHERE company_id=? AND id=?", company, line.itemId());
        }
        return receipt(company, id);
    }

    @Transactional
    public Receipt reverse(StocklyPrincipal actor, String id, String input) {
        String company = company(actor);
        String reason = required(input, 150, "Escribe el motivo de la anulación.");
        lockLocation(company);
        Receipt original = receipt(company, id);
        if (!original.movement().kind().equals("RECEIPT")) throw invalid("Solo se puede anular una entrada.");
        if (original.movement().voidId() != null) throw conflict("Esta entrada ya fue anulada.");
        var lines = new ArrayList<Line>();
        for (Line previous : original.lines()) {
            BigDecimal after = balance(company, previous.itemId()).subtract(previous.quantity());
            if (after.signum() < 0) throw conflict("La anulación dejaría existencias negativas para " + previous.name() + ".");
            lines.add(new Line(previous.itemId(), previous.name(), previous.sku(), previous.unit(), previous.quantity().negate(), after));
        }
        String reversal = UUID.randomUUID().toString();
        insertMovement(actor, reversal, "VOID", nextFolio(company, true), reason, null, null, null, id);
        for (Line line : lines) writeLine(company, reversal, line);
        return receipt(company, id);
    }

    private void lockLocation(String company) {
        jdbc.update("""
            INSERT INTO inventory_locations(company_id,id,name) VALUES (?,'principal','Principal')
            ON DUPLICATE KEY UPDATE name=name
            """, company);
        jdbc.queryForObject("SELECT id FROM inventory_locations WHERE company_id=? AND id=? FOR UPDATE", String.class, company, PRINCIPAL);
    }

    private String nextFolio(String company, boolean reverse) {
        String sequence = reverse ? "void_sequence" : "receipt_sequence";
        jdbc.update("UPDATE inventory_locations SET " + sequence + "=" + sequence + "+1 WHERE company_id=? AND id=?", company, PRINCIPAL);
        Long number = jdbc.queryForObject("SELECT " + sequence + " FROM inventory_locations WHERE company_id=? AND id=?",
            Long.class, company, PRINCIPAL);
        return (reverse ? "ANU-" : "ENT-") + String.format(Locale.ROOT, "%04d", number);
    }

    private Article lockedArticle(String company, String id) {
        return jdbc.query("""
            SELECT i.id,i.sku,i.barcode,i.unit,p.name,i.variant_name,0 AS balance
            FROM catalog_items i JOIN catalog_products p ON p.company_id=i.company_id AND p.id=i.product_id
            WHERE i.company_id=? AND i.id=? AND i.active AND p.active FOR UPDATE
            """, (rs,row) -> article(rs), company, id).stream().findFirst()
            .orElseThrow(() -> invalid("Un artículo ya no está activo o disponible en esta empresa."));
    }

    private BigDecimal balance(String company, String item) {
        return jdbc.queryForList("""
            SELECT quantity FROM inventory_balances WHERE company_id=? AND location_id=? AND item_id=? FOR UPDATE
            """, BigDecimal.class, company, PRINCIPAL, item).stream().findFirst().orElse(BigDecimal.ZERO);
    }

    private void insertMovement(StocklyPrincipal actor, String id, String kind, String folio, String reason,
                                String reference, String key, String hash, String reversedId) {
        String company = company(actor);
        String actorName = jdbc.queryForObject("SELECT full_name FROM users WHERE company_id=? AND id=?",
            String.class, company, actor.accountId());
        jdbc.update("""
            INSERT INTO inventory_movements(company_id,id,location_id,kind,folio,actor_id,actor_name,reason,reference,request_key,request_hash,reversed_id)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
            """, company, id, PRINCIPAL, kind, folio, actor.accountId(), actorName, reason, reference, key, hash, reversedId);
    }

    private void writeLine(String company, String movement, Line line) {
        jdbc.update("""
            INSERT INTO inventory_balances(company_id,location_id,item_id,quantity) VALUES (?,?,?,?) AS updated
            ON DUPLICATE KEY UPDATE quantity=updated.quantity
            """, company, PRINCIPAL, line.itemId(), line.balanceAfter());
        jdbc.update("""
            INSERT INTO inventory_movement_lines(company_id,movement_id,item_id,item_name,sku,unit,quantity,balance_after)
            VALUES (?,?,?,?,?,?,?,?)
            """, company, movement, line.itemId(), line.name(), line.sku(), line.unit(), line.quantity(), line.balanceAfter());
    }

    private Receipt receipt(String company, String id) {
        Summary movement = jdbc.query(MOVEMENTS + " AND m.id=?", (rs,row) -> summary(rs), company, id)
            .stream().findFirst().orElseThrow(() -> new IdentityFailure(404, "RECEIPT_NOT_FOUND", "No se encontró el movimiento."));
        List<Line> lines = jdbc.query("""
            SELECT item_id,item_name,sku,unit,quantity,balance_after FROM inventory_movement_lines
            WHERE company_id=? AND movement_id=? ORDER BY item_name,item_id
            """, (rs,row) -> new Line(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getBigDecimal(5),rs.getBigDecimal(6)), company, id);
        return new Receipt(movement, lines);
    }

    private static Article article(ResultSet rs) throws SQLException {
        String variant = rs.getString("variant_name");
        return new Article(rs.getString("id"), rs.getString("name") + (variant == null ? "" : " · " + variant),
            rs.getString("sku"), rs.getString("barcode"), rs.getString("unit"), rs.getBigDecimal("balance"));
    }

    private static Summary summary(ResultSet rs) throws SQLException {
        return new Summary(rs.getString("id"),rs.getString("kind"),rs.getString("folio"),rs.getString("actor_name"),
            rs.getString("reason"),rs.getString("reference"),rs.getTimestamp("created_at").toInstant(),rs.getString("location"),
            rs.getString("void_id"),rs.getString("void_folio"),rs.getString("void_reason"),rs.getString("original_id"),rs.getString("original_folio"));
    }

    private static SortedMap<String,BigDecimal> validateLines(List<LineInput> lines) {
        if (lines == null || lines.isEmpty() || lines.size() > MAX_LINES)
            throw invalid("La entrada debe tener entre 1 y 200 líneas.");
        var result = new TreeMap<String,BigDecimal>();
        for (LineInput line : lines) {
            if (line == null || line.quantity() == null) throw invalid("Completa los artículos y sus cantidades.");
            String id = uuid(line.itemId(), "Identificador de artículo inválido.");
            BigDecimal quantity = line.quantity().stripTrailingZeros();
            if (quantity.signum() <= 0 || quantity.scale() > 3 || quantity.compareTo(MAX_QUANTITY) > 0)
                throw invalid("Cada cantidad debe ser positiva, de hasta tres decimales y como máximo 1.000.000.");
            if (result.putIfAbsent(id, quantity) != null) throw invalid("El mismo artículo aparece en más de una línea.");
        }
        return result;
    }

    private static String hash(String actor, String reason, String reference, SortedMap<String,BigDecimal> quantities) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            for (String field : List.of(actor, reason, reference == null ? "" : reference, quantities.toString())) {
                digest.update((field.length() + ":").getBytes(StandardCharsets.UTF_8));
                digest.update(field.getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private static String term(String input) {
        String value = optional(input, 160);
        return "%" + (value == null ? "" : value.replace("!","!!").replace("%","!%").replace("_","!_")) + "%";
    }

    private static String uuid(String input, String error) {
        try {
            if (input == null || input.length() != 36) throw invalid(error);
            return UUID.fromString(input).toString();
        } catch (IllegalArgumentException e) { throw invalid(error); }
    }

    private static String required(String input, int max, String message) {
        String value = optional(input, max);
        if (value == null) throw invalid(message);
        return value;
    }

    private static String optional(String input, int max) {
        if (input == null || input.isBlank()) return null;
        String value = input.strip();
        if (value.length() > max) throw invalid("Un campo excede el máximo de " + max + " caracteres.");
        return value;
    }

    private static String company(StocklyPrincipal actor) {
        if (actor == null || actor.companyId() == null || actor.mustChangePassword())
            throw new IdentityFailure(403,"FORBIDDEN","No tienes acceso a las entradas.");
        return actor.companyId();
    }

    private static IdentityFailure invalid(String message) { return new IdentityFailure(400,"RECEIPT_INVALID",message); }
    private static IdentityFailure conflict(String message) { return new IdentityFailure(409,"RECEIPT_CONFLICT",message); }

    public record ReceiptInput(String requestKey, String reason, String detail, String reference, List<LineInput> lines) {}
    public record LineInput(String itemId, BigDecimal quantity) {}
    public record Article(String id,String name,String sku,String barcode,String unit,BigDecimal balance) {}
    public record Line(String itemId,String name,String sku,String unit,BigDecimal quantity,BigDecimal balanceAfter) {}
    public record Summary(String id,String kind,String folio,String actor,String reason,String reference,Instant date,String location,
                          String voidId,String voidFolio,String voidReason,String originalId,String originalFolio) {}
    public record Receipt(Summary movement,List<Line> lines) {}
    public record Page(List<Summary> entries,boolean hasMore) {}
}
