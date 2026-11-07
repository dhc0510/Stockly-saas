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
public class ReceiptService {
    private static final Rules RULES = new Rules("RECEIPT_INVALID", "RECEIPT_CONFLICT");
    /** Este módulo solo lee entradas y sus anulaciones; salidas y ajustes tienen permisos propios. */
    private static final String SCOPE = "(m.kind='RECEIPT' OR (m.kind='VOID' AND o.kind='RECEIPT'))";
    private final JdbcTemplate jdbc;
    private final InventoryLedger ledger;

    ReceiptService(JdbcTemplate jdbc, InventoryLedger ledger) { this.jdbc = jdbc; this.ledger = ledger; }

    @Transactional(readOnly = true)
    public Page list(StocklyPrincipal actor, String search, String status, int page) {
        String company = company(actor);
        if (!Set.of("", "confirmed", "voided").contains(status) || page < 0)
            throw RULES.invalid("Revisa el estado y la página.");
        String filter = RULES.term(search);
        var entries = jdbc.query(MOVEMENTS + """
            AND m.kind='RECEIPT'
            AND (m.folio LIKE ? ESCAPE '!' OR COALESCE(m.reference,'') LIKE ? ESCAPE '!')
            AND (?='' OR (?='voided' AND r.id IS NOT NULL) OR (?='confirmed' AND r.id IS NULL))
            ORDER BY m.created_at DESC,m.id DESC LIMIT ? OFFSET ?
            """, (rs, row) -> summary(rs), company, filter, filter, status, status, status, PAGE_SIZE + 1, (long) page * PAGE_SIZE);
        return new Page(entries.stream().limit(PAGE_SIZE).toList(), entries.size() > PAGE_SIZE);
    }

    public List<Article> items(StocklyPrincipal actor, String search, boolean barcode) {
        return ledger.items(company(actor), search, barcode, RULES);
    }

    @Transactional(readOnly = true)
    public Document get(StocklyPrincipal actor, String id) { return receipt(company(actor), id); }

    @Transactional
    public Document confirm(StocklyPrincipal actor, ReceiptInput input) {
        String company = company(actor);
        String key = RULES.uuid(input.requestKey(), "Identificador de confirmación inválido.");
        String reason = switch (input.reason() == null ? "" : input.reason()) {
            case "RECEIPT" -> "Recepción de mercancía";
            case "INITIAL" -> "Carga inicial";
            case "OTHER" -> "Otro · " + RULES.required(input.detail(), 150, "Escribe el detalle del motivo Otro.");
            default -> throw RULES.invalid("Selecciona el motivo de la entrada.");
        };
        String reference = RULES.optional(input.reference(), 100);
        var quantities = validateLines(input.lines());
        String hash = InventoryLedger.hash(actor.accountId(), reason, reference == null ? "" : reference, quantities.toString());
        ledger.lockLocation(company);
        var existing = ledger.registered(company, key, hash, RULES);
        if (existing.isPresent()) return receipt(company, existing.get());

        var lines = new ArrayList<Line>();
        for (var entry : quantities.entrySet()) {
            Article article = ledger.lockedArticle(company, entry.getKey(), RULES);
            BigDecimal quantity = entry.getValue();
            if (article.unit().equals("unidad") && quantity.scale() > 0)
                throw RULES.invalid(article.name() + ": para unidad solo se aceptan números enteros.");
            BigDecimal balance = ledger.balance(company, article.id()).add(quantity);
            if (balance.compareTo(MAX_BALANCE) > 0) throw RULES.conflict("La entrada excede la existencia máxima permitida.");
            lines.add(new Line(article.id(), article.name(), article.sku(), article.unit(), quantity, balance));
        }
        String id = UUID.randomUUID().toString();
        ledger.record(actor, id, "RECEIPT", Series.RECEIPT, reason, reference, key, hash, lines);
        return receipt(company, id);
    }

    @Transactional
    public Document reverse(StocklyPrincipal actor, String id, String input) {
        String company = company(actor);
        String reason = RULES.required(input, 150, "Escribe el motivo de la anulación.");
        ledger.lockLocation(company);
        Document original = receipt(company, id);
        if (!original.movement().kind().equals("RECEIPT")) throw RULES.invalid("Solo se puede anular una entrada.");
        if (original.movement().voidId() != null) throw RULES.conflict("Esta entrada ya fue anulada.");
        ledger.reverse(actor, id, original.lines(), reason, RULES);
        return receipt(company, id);
    }

    private Document receipt(String company, String id) {
        return ledger.document(company, id, SCOPE)
            .orElseThrow(() -> new IdentityFailure(404, "RECEIPT_NOT_FOUND", "No se encontró el movimiento."));
    }

    private static SortedMap<String,BigDecimal> validateLines(List<LineInput> lines) {
        if (lines == null || lines.isEmpty() || lines.size() > MAX_LINES)
            throw RULES.invalid("La entrada debe tener entre 1 y 200 líneas.");
        var result = new TreeMap<String,BigDecimal>();
        for (LineInput line : lines) {
            if (line == null || line.quantity() == null) throw RULES.invalid("Completa los artículos y sus cantidades.");
            String id = RULES.uuid(line.itemId(), "Identificador de artículo inválido.");
            BigDecimal quantity = line.quantity().stripTrailingZeros();
            if (quantity.signum() <= 0 || quantity.scale() > 3 || quantity.compareTo(MAX_QUANTITY) > 0)
                throw RULES.invalid("Cada cantidad debe ser positiva, de hasta tres decimales y como máximo 1.000.000.");
            if (result.putIfAbsent(id, quantity) != null) throw RULES.invalid("El mismo artículo aparece en más de una línea.");
        }
        return result;
    }

    private static String company(StocklyPrincipal actor) {
        if (actor == null || actor.companyId() == null || actor.mustChangePassword())
            throw new IdentityFailure(403,"FORBIDDEN","No tienes acceso a las entradas.");
        return actor.companyId();
    }

    public record ReceiptInput(String requestKey, String reason, String detail, String reference, List<LineInput> lines) {}
    public record LineInput(String itemId, BigDecimal quantity) {}
}
