package com.stockly.inventory;

import static com.stockly.inventory.InventoryLedger.*;

import com.stockly.identity.IdentityFailure;
import com.stockly.identity.StocklyPrincipal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SaleReturnService {
    private static final Rules RULES = new Rules("RETURN_INVALID", "RETURN_CONFLICT");
    private static final Set<String> REASONS = Set.of("Producto dañado", "Producto incorrecto",
        "El cliente cambió de opinión", "Otro");
    private final JdbcTemplate jdbc;
    private final InventoryLedger ledger;
    private final SaleService sales;
    private final SalePaymentService payments;

    SaleReturnService(JdbcTemplate jdbc, InventoryLedger ledger, SaleService sales, SalePaymentService payments) {
        this.jdbc = jdbc;
        this.ledger = ledger;
        this.sales = sales;
        this.payments = payments;
    }

    @Transactional(readOnly = true)
    public Context context(StocklyPrincipal actor, String saleId) {
        return context(actor.companyId(), sales.get(actor, saleId));
    }

    private Context context(String company, SaleService.Sale sale) {
        String saleId = sale.movement().id();
        var previous = new HashMap<String, Totals>();
        jdbc.query("""
            SELECT item_id,SUM(quantity),SUM(amount) FROM sale_return_lines WHERE company_id=? AND sale_id=? GROUP BY item_id
            """, rs -> { previous.put(rs.getString(1), new Totals(rs.getBigDecimal(2), rs.getBigDecimal(3))); }, company, saleId);
        var available = sale.lines().stream().map(line -> {
            Totals prior = previous.getOrDefault(line.itemId(), new Totals(BigDecimal.ZERO, BigDecimal.ZERO));
            return new Available(line.itemId(), line.name(), line.sku(), line.unit(), line.quantity(), prior.quantity(),
                line.quantity().subtract(prior.quantity()), line.unitPrice(), line.amount(), prior.amount());
        }).toList();
        var originalFiscal = sale.fiscal().stream().filter(doc -> !"03".equals(doc.docType())).reduce((a,b) -> b);
        Fiscal fallback = originalFiscal.map(doc -> originalStatus(doc.status(), doc.reason())).orElse(new Fiscal("NONE",null,null));
        var returns = jdbc.query("""
            SELECT r.folio,r.created_at,r.amount,COALESCE((
                SELECT SUM(x.amount) FROM sale_return_refunds x WHERE x.company_id=r.company_id AND x.return_id=r.id
            ),0), f.status,f.status_reason,f.consecutive
            FROM sale_returns r LEFT JOIN fiscal_documents f ON f.company_id=r.company_id AND f.sale_return_id=r.id
            WHERE r.company_id=? AND r.sale_id=? ORDER BY r.created_at DESC,r.id
            """, (rs,row) -> new ReturnRow(rs.getString(1), rs.getTimestamp(2).toInstant(), rs.getBigDecimal(3),
            rs.getBigDecimal(4), rs.getString(5) == null ? fallback : new Fiscal(rs.getString(5),rs.getString(6),rs.getString(7))), company, saleId);
        return new Context(saleId, sale.movement().folio(), sale.sale().branchName(), sale.movement().voidId() != null,
            available, sale.payments(), returns, fallback);
    }

    @Transactional(readOnly = true)
    public Quote preview(StocklyPrincipal actor, String saleId, Selection input) {
        var sale = sales.get(actor, saleId);
        return quote(context(actor.companyId(), sale), lines(input.lines()));
    }

    @Transactional
    public Receipt confirm(StocklyPrincipal actor, String saleId, Confirmation input) {
        String company = actor.companyId();
        String key = RULES.uuid(input.requestKey(), "Identificador de devolución inválido.");
        String reason = reason(input.reason(), input.detail());
        var requested = lines(input.lines());
        var refunds = input.refunds() == null ? List.<SalePaymentService.RefundInput>of() : input.refunds();

        ledger.lockLocation(company);
        var sale = sales.get(actor, saleId);
        String originalId = sale.movement().id();
        String hash = InventoryLedger.hash(actor.accountId(), originalId, reason, requested.toString(),
            input.expectedRefund() == null ? "" : input.expectedRefund().stripTrailingZeros().toPlainString(), refunds.toString());
        var existing = jdbc.query("""
            SELECT folio,request_hash FROM sale_returns WHERE company_id=? AND request_key=?
            """, (rs,row) -> new String[]{rs.getString(1),rs.getString(2)}, company, key);
        if (!existing.isEmpty()) {
            if (!hash.equals(existing.getFirst()[1]))
                throw new IdentityFailure(409, "RETURN_KEY_CONFLICT", "La clave ya corresponde a otra devolución.");
            return receipt(company, sale, existing.getFirst()[0]);
        }

        var context = context(company, sale);
        Quote quote = quote(context, requested);
        if (input.expectedRefund() == null || quote.refund().compareTo(input.expectedRefund()) != 0)
            throw new IdentityFailure(409, "RETURN_CHANGED", "El reintegro cambió. Revisa el importe y confirma otra vez.");
        var validatedRefunds = payments.checkedRefunds(company, sale.payments(), quote.refund(), refunds, false);
        String location = jdbc.queryForObject("SELECT location_id FROM inventory_movements WHERE company_id=? AND id=?",
            String.class, company, originalId);
        var stock = new ArrayList<Line>();
        for (ReturnLine line : quote.lines()) {
            if (line.restocked().signum() == 0) continue;
            BigDecimal balance = ledger.balance(company, location, line.itemId()).add(line.restocked());
            if (balance.compareTo(MAX_BALANCE) > 0)
                throw RULES.conflict("La reposición excede el saldo máximo de " + line.name() + ".");
            stock.add(new Line(line.itemId(), line.name(), line.sku(), line.unit(), line.restocked(), balance));
        }

        String id = UUID.randomUUID().toString();
        String folio = ledger.nextFolio(company, Series.RETURN);
        String movementId = stock.isEmpty() ? null : UUID.randomUUID().toString();
        if (movementId != null) ledger.record(actor, location, movementId, "RETURN", folio, reason, sale.movement().folio(), null, null, stock);
        String actorName = jdbc.queryForObject("SELECT full_name FROM users WHERE company_id=? AND id=?",
            String.class, company, actor.accountId());
        jdbc.update("""
            INSERT INTO sale_returns(company_id,id,sale_id,folio,actor_id,actor_name,reason,movement_id,request_key,request_hash,amount)
            VALUES (?,?,?,?,?,?,?,?,?,?,?)
            """, company, id, originalId, folio, actor.accountId(), actorName, reason, movementId, key, hash, quote.amount());
        for (ReturnLine line : quote.lines()) jdbc.update("""
            INSERT INTO sale_return_lines(company_id,sale_id,return_id,item_id,quantity,restocked,amount) VALUES (?,?,?,?,?,?,?)
            """, company, originalId, id, line.itemId(), line.returned(), line.restocked(), line.amount());
        for (int i = 0; i < validatedRefunds.size(); i++) {
            var refund = validatedRefunds.get(i);
            jdbc.update("""
                INSERT INTO sale_return_refunds(company_id,return_id,position,method,amount,reference) VALUES (?,?,?,?,?,?)
                """, company, id, i, refund.method(), refund.amount(), refund.reference());
        }
        return receipt(company, sale, folio);
    }

    @Transactional(readOnly = true)
    public Receipt get(StocklyPrincipal actor, String saleId, String folio) {
        return receipt(actor.companyId(), sales.get(actor, saleId), folio);
    }

    private Receipt receipt(String company, SaleService.Sale sale, String folio) {
        String saleId = sale.movement().id();
        var header = jdbc.query("""
            SELECT id,folio,created_at,actor_name,reason,movement_id,amount
            FROM sale_returns WHERE company_id=? AND sale_id=? AND folio=?
            """, (rs,row) -> new ReturnHeader(rs.getString(1), rs.getString(2), rs.getTimestamp(3).toInstant(), rs.getString(4),
            rs.getString(5), rs.getString(6), rs.getBigDecimal(7)), company, saleId, folio).stream().findFirst()
            .orElseThrow(() -> new IdentityFailure(404, "RETURN_NOT_FOUND", "No se encontró la devolución."));
        var lines = jdbc.query("""
            SELECT l.item_id,l.item_name,l.sku,l.unit,x.quantity,x.restocked,x.amount FROM sale_return_lines x
            JOIN inventory_movement_lines l ON l.company_id=x.company_id AND l.movement_id=x.sale_id AND l.item_id=x.item_id
            WHERE x.company_id=? AND x.return_id=? ORDER BY l.item_name,l.item_id
            """, (rs,row) -> new ReturnLine(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getBigDecimal(5),
            rs.getBigDecimal(6), rs.getBigDecimal(7)), company, header.id());
        var refunds = jdbc.query("""
            SELECT method,amount,reference FROM sale_return_refunds WHERE company_id=? AND return_id=? ORDER BY position
            """, (rs,row) -> new SalePaymentService.Refund(rs.getString(1),rs.getBigDecimal(2),rs.getString(3)), company, header.id());
        return new Receipt(header.folio(), sale.movement().folio(), sale.sale().branchName(), header.date(), header.actor(),
            header.reason(), header.movementId(), header.amount(), lines, refunds, fiscal(company, saleId, header.id()));
    }

    private Fiscal fiscal(String company, String saleId, String returnId) {
        var credit = jdbc.query("""
            SELECT status,status_reason,consecutive FROM fiscal_documents WHERE company_id=? AND sale_return_id=?
            """, (rs,row) -> new Fiscal(rs.getString(1),rs.getString(2),rs.getString(3)), company, returnId).stream().findFirst();
        if (credit.isPresent()) return credit.get();
        return jdbc.query("""
            SELECT status,status_reason FROM fiscal_documents WHERE company_id=? AND sale_id=? AND doc_type<>'03'
            ORDER BY created_at DESC,id DESC LIMIT 1
            """, (rs,row) -> originalStatus(rs.getString(1), rs.getString(2)), company, saleId).stream()
            .findFirst().orElse(new Fiscal("NONE",null,null));
    }

    private static Fiscal originalStatus(String status, String reason) {
        return switch (status) {
            case "ACCEPTED" -> new Fiscal("QUEUED", null, null);
            case "REJECTED", "CANCELLED" -> new Fiscal("REVIEW", reason, null);
            default -> new Fiscal("WAITING", null, null);
        };
    }


    private static Quote quote(Context context, SortedMap<String, LineInput> requested) {
        if (context.voided()) throw RULES.conflict("La venta ya fue anulada.");
        var originals = new HashMap<String, Available>();
        context.lines().forEach(line -> originals.put(line.itemId(), line));
        var issues = new LinkedHashMap<String, String>();
        var result = new ArrayList<ReturnLine>();
        BigDecimal amount = BigDecimal.ZERO;
        for (LineInput input : requested.values()) {
            Available original = originals.get(input.itemId());
            if (original == null) {
                issues.put(input.itemId(), "El artículo no pertenece a esta venta.");
                continue;
            }
            if (input.returned().compareTo(original.remaining()) > 0) {
                issues.put(input.itemId(), "Cantidad pendiente: " + original.remaining().stripTrailingZeros().toPlainString().replace('.', ',') + ".");
                continue;
            }
            if ("unidad".equals(original.unit()) && (input.returned().scale() > 0 || input.restocked().scale() > 0)) {
                issues.put(input.itemId(), "Para unidad solo se aceptan números enteros.");
                continue;
            }
            BigDecimal accumulated = original.returned().add(input.returned());
            BigDecimal gross = accumulated.compareTo(original.sold()) == 0 ? original.amount()
                : accumulated.multiply(original.unitPrice()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal value = gross.subtract(original.returnedAmount());
            result.add(new ReturnLine(input.itemId(), original.name(), original.sku(), original.unit(), input.returned(), input.restocked(), value));
            amount = amount.add(value);
        }
        if (!issues.isEmpty()) throw new IdentityFailure(409, "RETURN_LINES", "Revisa las cantidades de la devolución.", issues);
        BigDecimal refund = context.payments().isEmpty() ? BigDecimal.ZERO : amount;
        BigDecimal paid = context.payments().stream().map(SalePaymentService.Payment::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal refunded = context.returns().stream().map(ReturnRow::refund).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (refund.add(refunded).compareTo(paid) > 0) throw RULES.conflict("El reintegro acumulado superaría lo cobrado.");
        return new Quote(amount, refund, result);
    }

    private static SortedMap<String, LineInput> lines(List<LineInput> input) {
        if (input == null || input.isEmpty()) throw RULES.invalid("Indica al menos una cantidad para devolver.");
        var result = new TreeMap<String, LineInput>();
        for (LineInput line : input) {
            if (line == null) throw RULES.invalid("Completa los artículos de la devolución.");
            String id = RULES.uuid(line.itemId(), "Artículo inválido.");
            BigDecimal returned = quantity(line.returned(), id);
            BigDecimal restocked = quantity(line.restocked(), id);
            if (returned.signum() == 0 || restocked.compareTo(returned) > 0)
                throw new IdentityFailure(400, "RETURN_LINES", "Revisa las cantidades de la devolución.",
                    Map.of(id, "La cantidad devuelta debe ser positiva y la reposición no puede superarla."));
            if (result.putIfAbsent(id, new LineInput(id, returned, restocked)) != null)
                throw RULES.invalid("El mismo artículo aparece más de una vez.");
        }
        return result;
    }

    private static BigDecimal quantity(BigDecimal value, String itemId) {
        if (value == null || value.signum() < 0 || value.stripTrailingZeros().scale() > 3 || value.compareTo(MAX_BALANCE) > 0)
            throw new IdentityFailure(400, "RETURN_LINES", "Revisa las cantidades de la devolución.",
                Map.of(itemId,"Ingresa una cantidad de hasta tres decimales."));
        return value.stripTrailingZeros();
    }

    private static String reason(String input, String detail) {
        String reason = RULES.required(input, 150, "Selecciona el motivo de la devolución.");
        if (!REASONS.contains(reason)) throw RULES.invalid("Selecciona un motivo de la lista.");
        return "Otro".equals(reason) ? reason + " · " + RULES.required(detail, 143, "Escribe el detalle del motivo.") : reason;
    }

    private record Totals(BigDecimal quantity, BigDecimal amount) {}
    private record ReturnHeader(String id, String folio, Instant date, String actor, String reason, String movementId, BigDecimal amount) {}
    public record Selection(List<LineInput> lines) {}
    public record Confirmation(String requestKey, String reason, String detail, List<LineInput> lines,
                               BigDecimal expectedRefund, List<SalePaymentService.RefundInput> refunds) {}
    public record LineInput(String itemId, BigDecimal returned, BigDecimal restocked) {}
    public record Available(String itemId, String name, String sku, String unit, BigDecimal sold, BigDecimal returned,
                            BigDecimal remaining, BigDecimal unitPrice, BigDecimal amount, BigDecimal returnedAmount) {}
    public record ReturnLine(String itemId, String name, String sku, String unit, BigDecimal returned, BigDecimal restocked, BigDecimal amount) {}
    public record Quote(BigDecimal amount, BigDecimal refund, List<ReturnLine> lines) {}
    public record ReturnRow(String folio, Instant date, BigDecimal amount, BigDecimal refund, Fiscal fiscal) {}
    public record Context(String saleId, String saleFolio, String branch, boolean voided, List<Available> lines,
                          List<SalePaymentService.Payment> payments, List<ReturnRow> returns, Fiscal fiscal) {}
    public record Fiscal(String status, String reason, String consecutive) {}
    public record Receipt(String folio, String saleFolio, String branch, Instant date, String actor, String reason,
                          String movementId, BigDecimal amount, List<ReturnLine> lines, List<SalePaymentService.Refund> refunds, Fiscal fiscal) {}
}
