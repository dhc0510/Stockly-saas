package com.stockly.inventory;

import static com.stockly.inventory.InventoryLedger.*;

import com.stockly.identity.IdentityFailure;
import com.stockly.identity.StocklyPrincipal;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockMovementService {
    private static final Rules RULES = new Rules("MOVEMENT_INVALID", "MOVEMENT_CONFLICT");
    /** Este módulo solo lee salidas, ajustes y sus anulaciones; las entradas tienen permisos propios. */
    private static final String SCOPE = "(m.kind IN ('OUT','ADJUST') OR (m.kind='VOID' AND o.kind IN ('OUT','ADJUST')))";
    private final JdbcTemplate jdbc;
    private final InventoryLedger ledger;
    private final InventoryScope scope;

    StockMovementService(JdbcTemplate jdbc, InventoryLedger ledger, InventoryScope scope) { this.jdbc = jdbc; this.ledger = ledger; this.scope = scope; }

    @Transactional(readOnly = true)
    public Page list(StocklyPrincipal actor, String search, String type, String status, int page) {
        String company = company(actor);
        if (!Set.of("", "OUT", "ADJUST").contains(type) || !Set.of("", "confirmed", "voided").contains(status) || page < 0)
            throw RULES.invalid("Revisa el tipo, el estado y la página.");
        String filter = RULES.term(search);
        var entries = jdbc.query(MOVEMENTS + " AND " + InventoryScope.VISIBLE_MOVEMENT + """
            AND m.kind IN ('OUT','ADJUST')
            AND (m.folio COLLATE utf8mb4_0900_ai_ci LIKE ? ESCAPE '!' OR COALESCE(m.reference,'') COLLATE utf8mb4_0900_ai_ci LIKE ? ESCAPE '!')
            AND (?='' OR m.kind=?)
            AND (?='' OR (?='voided' AND r.id IS NOT NULL) OR (?='confirmed' AND r.id IS NULL))
            ORDER BY m.created_at DESC,m.id DESC LIMIT ? OFFSET ?
            """, (rs, row) -> summary(rs), company, InventoryScope.administrator(actor), actor.accountId(), filter, filter, type, type, status, status, status, PAGE_SIZE + 1, (long) page * PAGE_SIZE);
        return new Page(entries.stream().limit(PAGE_SIZE).toList(), entries.size() > PAGE_SIZE);
    }

    public List<Article> items(StocklyPrincipal actor, String search, boolean barcode, String branchId) {
        String company = company(actor);
        return ledger.items(company, scope.location(actor, branchId), scope.isLegacy(company), search, barcode, RULES);
    }

    public InventoryScope.Choices branches(StocklyPrincipal actor) {
        company(actor);
        return scope.choices(actor);
    }

    @Transactional(readOnly = true)
    public Document get(StocklyPrincipal actor, String id) { return movement(actor, id); }

    @Transactional
    public Document outflow(StocklyPrincipal actor, OutflowInput input) {
        String company = company(actor);
        String key = RULES.uuid(input.requestKey(), "Identificador de confirmación inválido.");
        String reason = switch (input.reason() == null ? "" : input.reason()) {
            case "DAMAGE" -> "Daño o rotura";
            case "EXPIRED" -> "Vencimiento";
            case "THEFT" -> "Robo o faltante";
            case "INTERNAL" -> "Consumo interno";
            case "OTHER" -> "Otro · " + RULES.required(input.detail(), 150, "Escribe el detalle del motivo Otro.");
            default -> throw RULES.invalid("Selecciona el motivo de la salida.");
        };
        String reference = RULES.optional(input.reference(), 100);
        var quantities = new TreeMap<String,BigDecimal>();
        for (OutflowLine line : lines(input.lines())) {
            if (line == null || line.quantity() == null) throw RULES.invalid("Completa los artículos y sus cantidades.");
            BigDecimal quantity = line.quantity().stripTrailingZeros();
            if (quantity.signum() <= 0 || quantity.scale() > 3 || quantity.compareTo(MAX_QUANTITY) > 0)
                throw RULES.invalid("Cada cantidad debe ser positiva, de hasta tres decimales y como máximo 1.000.000.");
            if (quantities.putIfAbsent(RULES.uuid(line.itemId(), "Identificador de artículo inválido."), quantity) != null)
                throw RULES.invalid("El mismo artículo aparece en más de una línea.");
        }
        String hash = InventoryLedger.hash("OUT", actor.accountId(), reason, reference == null ? "" : reference, quantities.toString());
        ledger.lockLocation(company);
        if (input.branchId() != null) hash = InventoryLedger.hash(hash, input.branchId());
        var existing = ledger.registered(company, key, hash, RULES);
        if (existing.isPresent()) return movement(actor, existing.get());
        String location = scope.location(actor, input.branchId());

        var result = new ArrayList<Line>();
        var fields = new LinkedHashMap<String,String>();
        var names = new ArrayList<String>();
        for (var entry : quantities.entrySet()) {
            scope.requireAssigned(company, location, entry.getKey());
            Article article = checked(company, entry.getKey(), entry.getValue());
            BigDecimal balance = ledger.balance(company, location, article.id());
            BigDecimal after = balance.subtract(entry.getValue());
            if (after.signum() < 0) {
                fields.put(article.id(), balance.toPlainString());
                names.add(article.name() + " (existencia: " + display(balance, article.unit()) + ")");
            }
            result.add(new Line(article.id(), article.name(), article.sku(), article.unit(), entry.getValue().negate(), after));
        }
        if (!fields.isEmpty()) throw new IdentityFailure(409, "INSUFFICIENT_BALANCE",
            "No se registró la salida porque dejaría existencias negativas: " + String.join(", ", names) + ".", fields);
        String id = UUID.randomUUID().toString();
        ledger.record(actor, location, id, "OUT", Series.OUT, reason, reference, key, hash, result);
        return movement(actor, id);
    }

    @Transactional
    public Document adjustment(StocklyPrincipal actor, AdjustmentInput input) {
        String company = company(actor);
        String key = RULES.uuid(input.requestKey(), "Identificador de confirmación inválido.");
        String reason = switch (input.reason() == null ? "" : input.reason()) {
            case "COUNT" -> "Conteo físico";
            case "CORRECTION" -> "Corrección de error";
            case "OTHER" -> "Otro · " + RULES.required(input.detail(), 150, "Escribe el detalle del motivo Otro.");
            default -> throw RULES.invalid("Selecciona el motivo del ajuste.");
        };
        String reference = RULES.optional(input.reference(), 100);
        var counted = new TreeMap<String,BigDecimal>();
        var expected = new TreeMap<String,BigDecimal>();
        for (AdjustmentLine line : lines(input.lines())) {
            if (line == null || line.countedQuantity() == null || line.expectedBalance() == null)
                throw RULES.invalid("Completa los artículos y lo contado.");
            BigDecimal quantity = line.countedQuantity().stripTrailingZeros();
            if (quantity.signum() < 0 || quantity.scale() > 3 || quantity.compareTo(MAX_QUANTITY) > 0)
                throw RULES.invalid("Lo contado no puede ser negativo, admite hasta tres decimales y como máximo 1.000.000.");
            String id = RULES.uuid(line.itemId(), "Identificador de artículo inválido.");
            if (counted.putIfAbsent(id, quantity) != null) throw RULES.invalid("El mismo artículo aparece en más de una línea.");
            expected.put(id, line.expectedBalance().stripTrailingZeros());
        }
        String hash = InventoryLedger.hash("ADJUST", actor.accountId(), reason, reference == null ? "" : reference, counted.toString(), expected.toString());
        ledger.lockLocation(company);
        if (input.branchId() != null) hash = InventoryLedger.hash(hash, input.branchId());
        var existing = ledger.registered(company, key, hash, RULES);
        if (existing.isPresent()) return movement(actor, existing.get());
        String location = scope.location(actor, input.branchId());

        var result = new ArrayList<Line>();
        var fields = new LinkedHashMap<String,String>();
        for (var entry : counted.entrySet()) {
            scope.requireAssigned(company, location, entry.getKey());
            Article article = checked(company, entry.getKey(), entry.getValue());
            BigDecimal balance = ledger.balance(company, location, article.id());
            if (balance.compareTo(expected.get(article.id())) != 0) fields.put(article.id(), balance.toPlainString());
            result.add(new Line(article.id(), article.name(), article.sku(), article.unit(), entry.getValue().subtract(balance), entry.getValue()));
        }
        if (!fields.isEmpty()) throw new IdentityFailure(409, "BALANCE_CHANGED",
            "El saldo cambió y no se aplicó el ajuste. Revisa la nueva diferencia y confirma otra vez.", fields);
        String id = UUID.randomUUID().toString();
        ledger.record(actor, location, id, "ADJUST", Series.ADJUST, reason, reference, key, hash, result);
        return movement(actor, id);
    }

    @Transactional
    public Document reverse(StocklyPrincipal actor, String id, String input) {
        String company = company(actor);
        String reason = RULES.required(input, 150, "Escribe el motivo de la anulación.");
        ledger.lockLocation(company);
        Document original = movement(actor, id);
        if (original.movement().kind().equals("VOID")) throw RULES.invalid("Solo se puede anular una salida o un ajuste.");
        if (original.movement().voidId() != null) throw RULES.conflict("Este movimiento ya fue anulado.");
        ledger.reverse(actor, id, original.lines(), reason, RULES);
        return movement(actor, id);
    }

    private Article checked(String company, String id, BigDecimal quantity) {
        Article article = ledger.lockedArticle(company, id, RULES);
        if (article.unit().equals("unidad") && quantity.scale() > 0)
            throw RULES.invalid(article.name() + ": para unidad solo se aceptan números enteros.");
        return article;
    }

    private Document movement(StocklyPrincipal actor, String id) {
        return ledger.document(company(actor), id, SCOPE + " AND " + InventoryScope.VISIBLE_MOVEMENT, InventoryScope.administrator(actor), actor.accountId())
            .orElseThrow(() -> new IdentityFailure(404, "MOVEMENT_NOT_FOUND", "No se encontró el movimiento."));
    }

    private static <T> List<T> lines(List<T> lines) {
        if (lines == null || lines.isEmpty() || lines.size() > MAX_LINES)
            throw RULES.invalid("La operación debe tener entre 1 y 200 líneas.");
        return lines;
    }

    private static String display(BigDecimal value, String unit) {
        String number = value.stripTrailingZeros().toPlainString().replace('.', ',');
        return number + " " + (unit.equals("unidad") && value.compareTo(BigDecimal.ONE) != 0 ? "unidades" : unit);
    }

    private static String company(StocklyPrincipal actor) {
        if (actor == null || actor.companyId() == null || actor.mustChangePassword())
            throw new IdentityFailure(403,"FORBIDDEN","No tienes acceso a las salidas y los ajustes.");
        return actor.companyId();
    }

    public record OutflowInput(String requestKey, String branchId, String reason, String detail, String reference, List<OutflowLine> lines) {}
    public record OutflowLine(String itemId, BigDecimal quantity) {}
    public record AdjustmentInput(String requestKey, String branchId, String reason, String detail, String reference, List<AdjustmentLine> lines) {}
    public record AdjustmentLine(String itemId, BigDecimal countedQuantity, BigDecimal expectedBalance) {}
}
