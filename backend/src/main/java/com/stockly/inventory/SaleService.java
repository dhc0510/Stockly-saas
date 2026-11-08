package com.stockly.inventory;

import static com.stockly.inventory.InventoryLedger.*;

import com.stockly.identity.IdentityFailure;
import com.stockly.identity.StocklyPrincipal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import com.stockly.billing.FiscalDocumentService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SaleService {
    private static final Rules RULES = new Rules("SALE_INVALID", "SALE_CONFLICT");
    private final JdbcTemplate jdbc;
    private final InventoryLedger ledger;
    private final InventoryScope scope;
    private final SalePaymentService payments;
    private final FiscalDocumentService fiscal;

    SaleService(JdbcTemplate jdbc, InventoryLedger ledger, InventoryScope scope, SalePaymentService payments, FiscalDocumentService fiscal) {
        this.jdbc = jdbc;
        this.ledger = ledger;
        this.scope = scope;
        this.payments = payments;
        this.fiscal = fiscal;
    }

    public InventoryScope.Choices branches(StocklyPrincipal actor) {
        company(actor);
        return scope.choices(actor);
    }

    @Transactional(readOnly = true)
    public List<SaleArticle> items(StocklyPrincipal actor, String branchId, String search) {
        String company = company(actor);
        String location = scope.location(actor, branchId);
        String term = RULES.optional(search, 160);
        if (term == null) return List.of();
        return ledger.items(company, location, false, term, false, RULES).stream().map(article -> {
            BigDecimal price = jdbc.queryForObject("SELECT price FROM catalog_items WHERE company_id=? AND id=?",
                BigDecimal.class, company, article.id());
            return new SaleArticle(article, price);
        }).toList();
    }

    @Transactional(readOnly = true)
    public SalePage list(StocklyPrincipal actor, String search, String status, String branchId, int page) {
        return list(actor, search, status, "", branchId, page);
    }

    public SalePage list(StocklyPrincipal actor, String search, String status, String payment, String branchId, int page) {
        return list(actor, search, status, payment, "", "", branchId, page);
    }

    /** fiscal: estado del comprobante vigente (PENDING, PROCESSING, ACCEPTED, REJECTED, CANCELLED o NONE); fiscalType: 01 o 04. */
    @Transactional(readOnly = true)
    public SalePage list(StocklyPrincipal actor, String search, String status, String payment, String fiscal, String fiscalType,
                         String branchId, int page) {
        String company = company(actor);
        if (page < 0 || !Set.of("", "confirmed", "voided", "partial", "complete").contains(status) || !Set.of("", "paid", "voided", "none").contains(payment)
            || !Set.of("", "PENDING", "PROCESSING", "ACCEPTED", "REJECTED", "CANCELLED", "NONE").contains(fiscal)
            || !Set.of("", "01", "04").contains(fiscalType))
            throw RULES.invalid("Revisa el estado y la página.");
        if (!branchId.isEmpty()) branch(actor, branchId);
        String term = RULES.term(search);
        // Estado del cobro: sin filas en sale_payments la venta es anterior a STK-012.
        var rows = jdbc.query("""
            SELECT * FROM (
            SELECT s.id,m.folio,m.created_at,s.branch_name,s.customer_name,s.total,r.id IS NOT NULL AS voided,
              CASE WHEN NOT EXISTS (SELECT 1 FROM sale_payments p WHERE p.company_id=s.company_id AND p.sale_id=s.id) THEN 'none'
                   WHEN r.id IS NOT NULL THEN 'voided' ELSE 'paid' END AS payment,
              f.doc_type AS fiscal_type, COALESCE(f.status,'NONE') AS fiscal_status,
              (SELECT c.status FROM fiscal_documents c WHERE c.company_id=f.company_id AND c.reference_id=f.id ORDER BY c.created_at DESC LIMIT 1) AS credit_status,
              s.customer_id_number,
              CASE WHEN NOT EXISTS(SELECT 1 FROM sale_returns sr WHERE sr.company_id=s.company_id AND sr.sale_id=s.id) THEN NULL
                   WHEN EXISTS(
                     SELECT 1 FROM inventory_movement_lines sl WHERE sl.company_id=s.company_id AND sl.movement_id=s.id
                       AND -sl.quantity > COALESCE((
                         SELECT SUM(rl.quantity) FROM sale_return_lines rl
                         WHERE rl.company_id=s.company_id AND rl.sale_id=s.id AND rl.item_id=sl.item_id
                       ),0)
                   ) THEN 'partial' ELSE 'complete' END AS return_status
            FROM sales s JOIN inventory_movements m ON m.company_id=s.company_id AND m.id=s.id
            LEFT JOIN inventory_movements r ON r.company_id=m.company_id AND r.reversed_id=m.id
            LEFT JOIN fiscal_documents f ON f.company_id=s.company_id AND f.id=(
              SELECT x.id FROM fiscal_documents x WHERE x.company_id=s.company_id AND x.sale_id=s.id AND x.doc_type<>'03'
              ORDER BY x.created_at DESC,x.id DESC LIMIT 1)
            WHERE s.company_id=? AND
            """ + InventoryScope.VISIBLE_MOVEMENT + """
            AND (?='' OR s.branch_id=?)
            AND (m.folio COLLATE utf8mb4_0900_ai_ci LIKE ? ESCAPE '!' OR COALESCE(s.customer_name,'') COLLATE utf8mb4_0900_ai_ci LIKE ? ESCAPE '!'
              OR COALESCE(s.customer_id_number,'') LIKE ? ESCAPE '!')

            ) sale WHERE (?='' OR (?='confirmed' AND NOT sale.voided AND sale.return_status IS NULL)
              OR (?='voided' AND sale.voided) OR sale.return_status=?)
              AND (?='' OR sale.payment=?) AND (?='' OR sale.fiscal_status=?) AND (?='' OR sale.fiscal_type=?)
            ORDER BY sale.created_at DESC,sale.id DESC LIMIT ? OFFSET ?
            """, (rs, row) -> new SaleRow(rs.getString(1), rs.getString(2), rs.getTimestamp(3).toInstant(),
            rs.getString(4), rs.getString(5), rs.getBigDecimal(6), rs.getBoolean(7), rs.getString(8), rs.getString(9), rs.getString(10),
            rs.getString(11), rs.getString(12), rs.getString(13)),
            company, InventoryScope.administrator(actor), actor.accountId(), branchId, branchId, term, term, term, status, status,
            status, status, payment, payment, fiscal, fiscal, fiscalType, fiscalType, PAGE_SIZE + 1, (long) page * PAGE_SIZE);
        return new SalePage(rows.stream().limit(PAGE_SIZE).toList(), rows.size() > PAGE_SIZE);
    }


    @Transactional
    public Sale confirm(StocklyPrincipal actor, SaleInput input) {
        String company = company(actor);
        String request = RULES.uuid(input.requestKey(), "Identificador de confirmación inválido.");
        String branchId = RULES.uuid(input.branchId(), "Selecciona una sucursal.");
        String customer = RULES.optional(input.customer(), 150);
        String customerPhone = RULES.optional(input.customerPhone(), 30);
        String customerEmail = RULES.optional(input.customerEmail(), 254);
        if (customerEmail != null && !customerEmail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
            throw RULES.invalid("Ingresa un correo electrónico válido.");
        // STK-013: con tipo y número de identificación se emite factura; sin ellos, tiquete.
        var fiscalCustomer = FiscalDocumentService.customer(input.customerIdType(), input.customerIdNumber(), customer);
        var requested = validateLines(input.lines());
        String hash = InventoryLedger.hash("SALE", actor.accountId(), branchId,
            Objects.toString(customer, ""), requested.toString());
        // Conserva la huella de los intentos creados antes de añadir el contacto.
        if (customerPhone != null || customerEmail != null)
            hash = InventoryLedger.hash(hash, Objects.toString(customerPhone, ""), Objects.toString(customerEmail, ""));
        // Los pagos forman parte del intento: la misma clave con otros pagos es un conflicto (STK-012).
        if (fiscalCustomer.number() != null) hash = InventoryLedger.hash(hash, "ID", fiscalCustomer.type(), fiscalCustomer.number());
        var requestedPayments = SalePaymentService.normalize(input.payments());
        if (!requestedPayments.isEmpty())
            hash = InventoryLedger.hash(hash, "PAYMENTS", SalePaymentService.fingerprint(requestedPayments));

        ledger.lockLocation(company);
        var existing = ledger.registered(company, request, hash, RULES);
        if (existing.isPresent()) return get(actor, existing.get());
        if (requestedPayments.isEmpty()) throw new IdentityFailure(400, "SALE_PAYMENT_INVALID", "Registra el cobro de la venta antes de confirmar.");

        Branch branch = branch(actor, branchId);
        if (!branch.active() || branch.location() == null)
            throw new IdentityFailure(409, "SALE_NOT_READY", "La sucursal no está habilitada para vender.");

        var lines = new ArrayList<Line>();
        var prices = new ArrayList<Price>();
        var errors = new LinkedHashMap<String, String>();
        var changedPrices = new LinkedHashMap<String, String>();
        BigDecimal total = BigDecimal.ZERO;

        for (var entry : requested.entrySet()) {
            String itemId = entry.getKey();
            BigDecimal quantity = entry.getValue().quantity();
            var product = jdbc.query("""
                SELECT i.id,i.sku,i.barcode,i.unit,i.price,p.name,i.variant_name,0 AS balance
                FROM inventory_branch_items bi
                JOIN catalog_items i ON i.company_id=bi.company_id AND i.id=bi.item_id AND i.active
                JOIN catalog_products p ON p.company_id=i.company_id AND p.id=i.product_id AND p.active
                WHERE bi.company_id=? AND bi.branch_id=? AND bi.item_id=? FOR UPDATE
                """, (rs, row) -> new Product(article(rs), rs.getBigDecimal("price")), company, branchId, itemId)
                .stream().findFirst();
            if (product.isEmpty()) {
                errors.put(itemId, "El artículo ya no está disponible en esta sucursal.");
                continue;
            }

            Article article = product.get().article();
            BigDecimal price = product.get().price();
            if ("unidad".equals(article.unit()) && quantity.scale() > 0) {
                errors.put(itemId, "Para unidad solo se aceptan números enteros.");
                continue;
            }
            if (price == null) {
                errors.put(itemId, "Configura el precio del artículo en el catálogo.");
                continue;
            }
            BigDecimal available = ledger.balance(company, branch.location(), itemId);
            if (available.compareTo(quantity) < 0) {
                errors.put(itemId, "Existencia disponible: " + available.stripTrailingZeros().toPlainString().replace('.', ',')
                    + " " + article.unit() + ".");
                continue;
            }
            BigDecimal expected = entry.getValue().expectedPrice();
            if (expected == null || price.compareTo(expected) != 0)
                changedPrices.put(itemId, price.toPlainString());

            BigDecimal amount = quantity.multiply(price).setScale(2, RoundingMode.HALF_UP);
            lines.add(new Line(itemId, article.name(), article.sku(), article.unit(), quantity.negate(), available.subtract(quantity)));
            prices.add(new Price(itemId, price, amount));
            total = total.add(amount);
        }

        if (!errors.isEmpty())
            throw new IdentityFailure(409, "SALE_LINE_ERROR", "Revisa los artículos marcados. No se registró la venta.", errors);
        if (!changedPrices.isEmpty())
            throw new IdentityFailure(409, "SALE_PRICE_CHANGED", "Hay precios nuevos. Revisa el total y confirma otra vez.", changedPrices);
        var applied = payments.apply(company, total, requestedPayments);

        String id = UUID.randomUUID().toString();
        ledger.record(actor, branch.location(), id, "SALE", Series.SALE, "Venta", null, request, hash, lines);
        jdbc.update("""
            INSERT INTO sales(company_id,id,branch_id,branch_name,customer_name,customer_phone,customer_email,total,customer_id_type,customer_id_number)
            VALUES (?,?,?,?,?,?,?,?,?,?)
            """,
            company, id, branchId, branch.name(), customer, customerPhone, customerEmail, total, fiscalCustomer.type(), fiscalCustomer.number());
        for (Price price : prices)
            jdbc.update("INSERT INTO sale_line_prices(company_id,sale_id,item_id,unit_price,amount) VALUES (?,?,?,?,?)",
                company, id, price.itemId(), price.unitPrice(), price.amount());
        payments.record(company, id, applied);
        fiscal.issueForSale(company, id);
        return get(actor, id);
    }

    @Transactional(readOnly = true)
    public Sale get(StocklyPrincipal actor, String id) {
        String company = company(actor);
        String saleId = id.matches("VTA-[0-9]{4,19}") ? jdbc.queryForList("""
            SELECT id FROM inventory_movements WHERE company_id=? AND folio=? AND kind='SALE'
            """, String.class, company, id).stream().findFirst().orElseThrow(SaleService::missing)
            : RULES.uuid(id, "Identificador de venta inválido.");
        var header = jdbc.query("""
            SELECT branch_id,branch_name,customer_name,customer_phone,customer_email,total,customer_id_type,customer_id_number FROM sales WHERE company_id=? AND id=?
            """, (rs, row) -> new SaleHeader(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getBigDecimal(6),
            rs.getString(7), rs.getString(8)), company, saleId)
            .stream().findFirst().orElseThrow(SaleService::missing);
        branch(actor, header.branchId());
        var movement = ledger.document(company, saleId, "m.kind='SALE'").orElseThrow(SaleService::missing);
        var prices = new HashMap<String, Price>();
        jdbc.query("SELECT item_id,unit_price,amount FROM sale_line_prices WHERE company_id=? AND sale_id=?", rs -> {
            String item = rs.getString(1);
            prices.put(item, new Price(item, rs.getBigDecimal(2), rs.getBigDecimal(3)));
        }, company, saleId);
        var lines = movement.lines().stream().map(line -> {
            Price price = prices.get(line.itemId());
            return new SaleLine(line.itemId(), line.name(), line.sku(), line.unit(), line.quantity().negate(),
                price.unitPrice(), price.amount());
        }).toList();
        Refunded refund = null;
        if (movement.movement().voidId() != null) {
            refund = jdbc.query("""
                SELECT r.created_at,COALESCE(u.full_name,r.actor_name) FROM inventory_movements r
                LEFT JOIN users u ON u.company_id=r.company_id AND u.id=r.actor_id
                WHERE r.company_id=? AND r.id=?
                """, (rs, row) -> new Refunded(rs.getTimestamp(1).toInstant(), rs.getString(2),
                payments.refunds(company, saleId)), company, movement.movement().voidId()).stream().findFirst().orElse(null);
        }
        return new Sale(header, movement.movement(), lines, payments.payments(company, saleId), refund, fiscal.documents(company, saleId));
    }

    public Sale reverse(StocklyPrincipal actor, String id, String input) {
        return reverse(actor, id, input, List.of());
    }

    @Transactional
    public Sale reverse(StocklyPrincipal actor, String id, String input, List<SalePaymentService.RefundInput> refunds) {
        String company = company(actor);
        String reason = RULES.required(input, 150, "Escribe el motivo de la anulación.");
        ledger.lockLocation(company);
        Sale original = get(actor, id);
        if (original.movement().voidId() != null) throw RULES.conflict("Esta venta ya fue anulada.");
        String originalId = original.movement().id();
        if (Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM sale_returns WHERE company_id=? AND sale_id=?)", Boolean.class, company, originalId)))
            throw RULES.conflict("Esta venta tiene devoluciones. Usa Devolver lo restante.");
        var movement = ledger.document(company, originalId, "m.kind='SALE'").orElseThrow(SaleService::missing);
        String reversal = ledger.reverse(actor, originalId, movement.lines(), reason, RULES);
        payments.refund(company, originalId, reversal, refunds);
        fiscal.onSaleVoided(company, originalId);
        return get(actor, id);
    }

    private Branch branch(StocklyPrincipal actor, String id) {
        return jdbc.query("""
            SELECT b.name,b.location_id,b.active
            FROM inventory_branches b
            WHERE b.company_id=? AND b.id=? AND (? OR EXISTS (
                SELECT 1 FROM inventory_branch_users u
                WHERE u.company_id=b.company_id AND u.branch_id=b.id AND u.user_id=?
            ))
            """, (rs, row) -> new Branch(rs.getString(1), rs.getString(2), rs.getBoolean(3)),
            company(actor), id, "COMPANY_ADMIN".equals(actor.role()), actor.accountId())
            .stream().findFirst().orElseThrow(SaleService::missing);
    }

    private static SortedMap<String, LineInput> validateLines(List<LineInput> lines) {
        if (lines == null || lines.isEmpty()) throw RULES.invalid("Agrega al menos un artículo.");
        var result = new TreeMap<String, LineInput>();
        for (LineInput line : lines) {
            if (line == null || line.quantity() == null) throw RULES.invalid("Completa los artículos y sus cantidades.");
            String id = RULES.uuid(line.itemId(), "Identificador de artículo inválido.");
            BigDecimal quantity = line.quantity().stripTrailingZeros();
            if (quantity.signum() <= 0 || quantity.scale() > 3 || quantity.compareTo(MAX_BALANCE) > 0)
                throw RULES.invalid("Cada cantidad debe ser positiva, de hasta tres decimales y dentro del límite de existencias.");
            BigDecimal expected = line.expectedPrice() == null ? null : line.expectedPrice().stripTrailingZeros();
            if (result.putIfAbsent(id, new LineInput(id, quantity, expected)) != null)
                throw RULES.invalid("El mismo artículo aparece en más de una línea.");
        }
        return result;
    }

    private static String company(StocklyPrincipal actor) {
        if (actor == null || actor.companyId() == null || actor.mustChangePassword())
            throw new IdentityFailure(403, "FORBIDDEN", "No tienes acceso a ventas.");
        return actor.companyId();
    }

    private static IdentityFailure missing() {
        return new IdentityFailure(404, "SALE_NOT_FOUND", "No se encontró la venta o la sucursal.");
    }

    private record Branch(String name, String location, boolean active) {}
    private record Product(Article article, BigDecimal price) {}
    private record Price(String itemId, BigDecimal unitPrice, BigDecimal amount) {}
    public record SaleArticle(Article article, BigDecimal price) {}
    public record SaleRow(String id, String folio, java.time.Instant date, String branchName, String customer,
                          BigDecimal total, boolean voided, String payment, String fiscalType, String fiscalStatus,
                          String creditStatus, String customerIdNumber, String returnStatus) {}
    public record SalePage(List<SaleRow> entries, boolean hasMore) {}
    public record SaleInput(String requestKey, String branchId, String customer, String customerPhone,
                            String customerEmail, List<LineInput> lines, List<SalePaymentService.PaymentInput> payments,
                            String customerIdType, String customerIdNumber) {
        public SaleInput(String requestKey, String branchId, String customer, String customerPhone,
                         String customerEmail, List<LineInput> lines) {
            this(requestKey, branchId, customer, customerPhone, customerEmail, lines, null, null, null);
        }
        public SaleInput(String requestKey, String branchId, String customer, String customerPhone,
                         String customerEmail, List<LineInput> lines, List<SalePaymentService.PaymentInput> payments) {
            this(requestKey, branchId, customer, customerPhone, customerEmail, lines, payments, null, null);
        }
    }
    public record Refunded(java.time.Instant date, String actor, List<SalePaymentService.Refund> lines) {}
    public record LineInput(String itemId, BigDecimal quantity, BigDecimal expectedPrice) {}
    public record SaleHeader(String branchId, String branchName, String customer, String customerPhone,
                             String customerEmail, BigDecimal total, String customerIdType, String customerIdNumber) {}
    public record SaleLine(String itemId, String name, String sku, String unit, BigDecimal quantity,
                           BigDecimal unitPrice, BigDecimal amount) {}
    public record Sale(SaleHeader sale, Summary movement, List<SaleLine> lines,
                       List<SalePaymentService.Payment> payments, Refunded refund, List<FiscalDocumentService.Document> fiscal) {}
}
