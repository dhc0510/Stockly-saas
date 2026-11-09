package com.stockly.inventory;

import com.stockly.identity.IdentityFailure;
import com.stockly.identity.StocklyPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static com.stockly.inventory.InventoryContracts.StockItem;

/**
 * STK-017: reportes operativos de solo lectura. Las fechas son días de Costa Rica; las devoluciones y las anulaciones de
 * ventas de otro día restan en su propia fecha; las anuladas el mismo día no suman. Los cobros reutilizan el resumen de STK-012.
 */
@Service
public class ReportService {
    static final ZoneId COSTA_RICA = ZoneId.of("America/Costa_Rica");
    static final int MAX_DAYS = 366, DAILY_LIMIT = 62;
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final String NO_CATEGORY = "Sin categoría";

    public record Branch(String id, String name) {}
    public record Context(String companyName, String branchId, String branchName, LocalDate from, LocalDate to,
                          LocalDate previousFrom, LocalDate previousTo, String grouping, List<Branch> branches) {}
    public record Kpis(BigDecimal gross, BigDecimal returns, BigDecimal laterVoids, BigDecimal net, int sales, BigDecimal average,
                       int sameDayVoids, BigDecimal sameDayVoidAmount) {}
    public record Bucket(LocalDate start, int sales, BigDecimal gross, BigDecimal returns, BigDecimal voids, BigDecimal net) {}
    public record ProductRow(String itemId, String productName, String variantName, String sku, String category, String unit,
                             BigDecimal quantity, BigDecimal amount, BigDecimal returned, BigDecimal share) {}
    public record CategoryRow(String category, int products, BigDecimal amount, BigDecimal share) {}
    public record Summary(Context context, Kpis current, Kpis previous, List<Bucket> series, List<ProductRow> top) {}
    public record Products(Context context, BigDecimal gross, BigDecimal returned, List<CategoryRow> categories,
                           List<ProductRow> products, List<String> categoryOptions) {}
    public record PaymentRow(String method, String label, int payments, BigDecimal collected, BigDecimal refunded, BigDecimal net) {}
    public record Payments(Context context, List<PaymentRow> methods, PaymentRow total, int unpaidSales, BigDecimal unpaidAmount,
                           BigDecimal netSales) {}
    public record SellerRow(String sellerId, String name, int sales, BigDecimal gross, BigDecimal returns, BigDecimal voids,
                            BigDecimal net, BigDecimal average) {}
    public record Sellers(Context context, List<SellerRow> rows, SellerRow total) {}
    public record IdleRow(String productId, String productName, String variantName, String sku, String category, String unit,
                          String branchId, String branchName, BigDecimal available, Instant lastSale) {}
    public record Idle(Context context, List<IdleRow> rows) {}
    public record Filters(String from, String to, String branchId, String search, String category, String sort, boolean onlyStock) {}
    public record Export(String fileName, String contentType, byte[] content) {}

    private final JdbcTemplate jdbc;
    private final InventoryScope scope;
    private final InventoryService inventory;
    private final SalePaymentService payments;

    public ReportService(JdbcTemplate jdbc, InventoryScope scope, InventoryService inventory, SalePaymentService payments) {
        this.jdbc = jdbc; this.scope = scope; this.inventory = inventory; this.payments = payments;
    }

    // ---------- Reportes ----------

    @Transactional(readOnly = true)
    public Summary summary(StocklyPrincipal actor, Filters f) {
        Scope s = scope(actor, f);
        Facts facts = facts(s, s.previousLower(), s.upper(), true);
        Kpis current = kpis(facts, s.lower(), s.upper()), previous = kpis(facts, s.previousLower(), s.lower());
        List<ProductRow> top = products(facts, s, current.gross()).stream()
            .filter(p -> p.amount().signum() > 0).sorted(order("AMOUNT")).limit(3).toList();
        return new Summary(s.context(), current, previous, series(facts, s), top);
    }

    @Transactional(readOnly = true)
    public Products products(StocklyPrincipal actor, Filters f) {
        Scope s = scope(actor, f);
        Facts facts = facts(s, s.lower(), s.upper(), true);
        BigDecimal gross = kpis(facts, s.lower(), s.upper()).gross();
        List<ProductRow> all = products(facts, s, gross);
        Map<String, CategoryRow> cats = new LinkedHashMap<>();
        for (ProductRow p : all) cats.merge(p.category(), new CategoryRow(p.category(), p.quantity().signum() > 0 ? 1 : 0, p.amount(), ZERO),
            (a, b) -> new CategoryRow(a.category(), a.products() + b.products(), a.amount().add(b.amount()), ZERO));
        List<CategoryRow> categories = cats.values().stream().filter(c -> c.amount().signum() > 0)
            .map(c -> new CategoryRow(c.category(), c.products(), c.amount(), share(c.amount(), gross)))
            .sorted(Comparator.comparing(CategoryRow::amount).reversed().thenComparing(CategoryRow::category)).toList();
        List<String> options = all.stream().map(ProductRow::category).distinct().sorted().toList();
        String term = TabularExport.plain(f.search()).strip(), category = f.category() == null ? "" : f.category().strip();
        Map<String, String> haystack = facts.searchText();
        List<ProductRow> rows = all.stream()
            .filter(p -> category.isEmpty() || category.equals(p.category()))
            .filter(p -> term.isEmpty() || haystack.getOrDefault(p.itemId(), "").contains(term))
            .sorted(order(f.sort())).toList();
        BigDecimal returned = all.stream().map(ProductRow::returned).reduce(ZERO, BigDecimal::add);
        return new Products(s.context(), gross, returned, categories, rows, options);
    }

    @Transactional(readOnly = true)
    public Payments payments(StocklyPrincipal actor, Filters f) {
        Scope s = scope(actor, f);
        var summary = payments.summary(actor, s.branch(), s.context().from().toString(), s.context().to().toString());
        Map<String, Integer> counts = new HashMap<>();
        jdbc.query("""
            SELECT x.method,COUNT(*) FROM sale_payments x
            JOIN sales s ON s.company_id=x.company_id AND s.id=x.sale_id
            JOIN inventory_movements m ON m.company_id=s.company_id AND m.id=s.id
            WHERE x.company_id=? AND m.created_at>=? AND m.created_at<? AND""" + VISIBLE + " GROUP BY x.method",
            rs -> { counts.put(rs.getString(1), rs.getInt(2)); }, s.params(s.lower(), s.upper()));
        List<PaymentRow> rows = summary.methods().stream().map(m -> new PaymentRow(m.method(), m.label(),
            counts.getOrDefault(m.method(), 0), m.collected(), m.refunded(), m.net())).toList();
        PaymentRow total = new PaymentRow("TOTAL", "Total", rows.stream().mapToInt(PaymentRow::payments).sum(),
            sum(rows, PaymentRow::collected), sum(rows, PaymentRow::refunded), sum(rows, PaymentRow::net));
        Facts facts = facts(s, s.lower(), s.upper(), false);
        Set<String> paid = new HashSet<>(jdbc.queryForList("""
            SELECT DISTINCT x.sale_id FROM sale_payments x
            JOIN sales s ON s.company_id=x.company_id AND s.id=x.sale_id
            JOIN inventory_movements m ON m.company_id=s.company_id AND m.id=s.id
            WHERE x.company_id=? AND m.created_at>=? AND m.created_at<? AND""" + VISIBLE, String.class, s.params(s.lower(), s.upper())));
        List<Sale> unpaid = facts.sales().stream().filter(x -> x.in(s.lower(), s.upper()) && !x.sameDayVoid() && !paid.contains(x.id())).toList();
        return new Payments(s.context(), rows, total, unpaid.size(), unpaid.stream().map(Sale::total).reduce(ZERO, BigDecimal::add),
            kpis(facts, s.lower(), s.upper()).net());
    }

    @Transactional(readOnly = true)
    public Sellers sellers(StocklyPrincipal actor, Filters f) {
        Scope s = scope(actor, f);
        Facts facts = facts(s, s.lower(), s.upper(), false);
        Map<String, SellerAcc> acc = new LinkedHashMap<>();
        for (Sale x : facts.sales()) {
            if (x.in(s.lower(), s.upper()) && !x.sameDayVoid()) acc.computeIfAbsent(x.sellerId(), k -> new SellerAcc(x.sellerName())).sale(x.total());
            if (x.voidIn(s.lower(), s.upper()) && !x.sameDayVoid()) acc.computeIfAbsent(x.sellerId(), k -> new SellerAcc(x.sellerName())).voids(x.total());
        }
        for (Return r : facts.returns()) acc.computeIfAbsent(r.sellerId(), k -> new SellerAcc(r.sellerName())).returned(r.amount());
        List<SellerRow> rows = acc.entrySet().stream().map(e -> e.getValue().row(e.getKey()))
            .sorted(Comparator.comparing(SellerRow::net).reversed().thenComparing(SellerRow::name)).toList();
        SellerAcc all = new SellerAcc("Total");
        for (SellerRow r : rows) { all.count += r.sales(); all.gross = all.gross.add(r.gross()); all.returns = all.returns.add(r.returns()); all.voids = all.voids.add(r.voids()); }
        return new Sellers(s.context(), rows, all.row("TOTAL"));
    }

    @Transactional(readOnly = true)
    public Idle idle(StocklyPrincipal actor, Filters f) {
        Scope s = scope(actor, f);
        Facts facts = facts(s, s.lower(), s.upper(), true);
        Set<String> sold = new HashSet<>();
        for (Line l : facts.lines()) { Sale x = facts.sale(l.saleId()); if (x.in(s.lower(), s.upper()) && !x.sameDayVoid()) sold.add(x.branchId() + "/" + l.itemId()); }
        Map<String, Instant> last = new HashMap<>();
        jdbc.query("""
            SELECT s.branch_id,l.item_id,MAX(m.created_at) FROM inventory_movement_lines l
            JOIN sales s ON s.company_id=l.company_id AND s.id=l.movement_id
            JOIN inventory_movements m ON m.company_id=s.company_id AND m.id=s.id
            LEFT JOIN inventory_movements v ON v.company_id=m.company_id AND v.reversed_id=m.id
            WHERE l.company_id=? AND (v.id IS NULL OR DATE(DATE_SUB(v.created_at,INTERVAL 6 HOUR))<>DATE(DATE_SUB(m.created_at,INTERVAL 6 HOUR)))
              AND""" + VISIBLE + " GROUP BY s.branch_id,l.item_id",
            rs -> { last.put(rs.getString(1) + "/" + rs.getString(2), rs.getTimestamp(3).toInstant()); }, s.visibleParams());
        List<StockItem> items = inventory.items(actor, s.branch().isEmpty() ? null : s.branch(), f.search(), "", "ALL", "NAME");
        List<IdleRow> rows = items.stream()
            .filter(i -> i.branchId() != null && !sold.contains(i.branchId() + "/" + i.itemId()))
            .filter(i -> !f.onlyStock() || i.available().signum() > 0)
            .map(i -> new IdleRow(i.productId(), i.productName(), i.variantName(), i.sku(), i.category() == null ? NO_CATEGORY : i.category(),
                i.unit(), i.branchId(), i.branchName(), i.available(), last.get(i.branchId() + "/" + i.itemId())))
            .sorted(Comparator.comparing((IdleRow r) -> r.lastSale() == null ? Instant.MIN : r.lastSale())
                .thenComparing(IdleRow::productName).thenComparing(IdleRow::branchName)).toList();
        return new Idle(s.context(), rows);
    }

    // ---------- Exportación ----------

    private static final Map<String, String> SLUG = Map.of("summary", "resumen", "products", "productos", "payments", "cobros",
        "sellers", "vendedores", "idle", "sin-ventas");
    private static final String MONEY = "#,##0.00", QTY = "#,##0.###", PERCENT = "0.0%";

    @Transactional(readOnly = true)
    public Export export(StocklyPrincipal actor, String format, String report, Filters f) {
        String kind = TabularExport.format(format);
        String key = report == null ? "" : report.strip();
        if (kind.equals("csv") && !SLUG.containsKey(key)) throw new IdentityFailure(400, "INVENTORY_INVALID", "Elige el reporte que quieres exportar.");
        Summary summary = summary(actor, f);
        Context c = summary.context();
        String base = "reportes-" + (c.branchId() == null ? "todas" : TabularExport.slug(c.branchName())) + "-" + c.from() + "-a-" + c.to();
        if (kind.equals("csv")) {
            TabularExport.Sheet sheet = switch (key) {
                case "summary" -> summarySheet(summary);
                case "products" -> productSheet(products(actor, f));
                case "payments" -> paymentSheet(payments(actor, f));
                case "sellers" -> sellerSheet(sellers(actor, f));
                default -> idleSheet(idle(actor, f));
            };
            return new Export(base + "-" + SLUG.get(key) + ".csv", TabularExport.CSV, TabularExport.csv(sheet.headers(), sheet.rows()));
        }
        Products products = products(actor, f);
        List<TabularExport.Sheet> sheets = List.of(summarySheet(summary), seriesSheet(summary), productSheet(products), categorySheet(products),
            paymentSheet(payments(actor, f)), sellerSheet(sellers(actor, f)), idleSheet(idle(actor, f)));
        return new Export(base + ".xlsx", TabularExport.XLSX, TabularExport.xlsx(sheets));
    }

    private static TabularExport.Sheet summarySheet(Summary s) {
        Kpis a = s.current(), b = s.previous();
        List<List<Object>> rows = List.of(
            row("Ventas brutas", a.gross(), b.gross()), row("Devoluciones", a.returns(), b.returns()),
            row("Anulaciones de ventas anteriores", a.laterVoids(), b.laterVoids()), row("Ventas netas", a.net(), b.net()),
            row("Ventas realizadas", a.sales(), b.sales()), row("Venta promedio", a.average(), b.average()),
            row("Anuladas el mismo día (cantidad)", a.sameDayVoids(), b.sameDayVoids()),
            row("Anuladas el mismo día (monto)", a.sameDayVoidAmount(), b.sameDayVoidAmount()));
        Context c = s.context();
        return new TabularExport.Sheet("Resumen", List.of("Indicador", "Período " + c.from() + " a " + c.to(),
            "Período anterior " + c.previousFrom() + " a " + c.previousTo()), rows, Map.of(1, MONEY, 2, MONEY));
    }

    private static TabularExport.Sheet seriesSheet(Summary s) {
        List<List<Object>> rows = s.series().stream().map(b -> Arrays.<Object>asList(
            "MONTH".equals(s.context().grouping()) ? b.start().toString().substring(0, 7) : b.start().toString(),
            b.sales(), b.gross(), b.returns(), b.voids(), b.net())).toList();
        return new TabularExport.Sheet("Por " + ("MONTH".equals(s.context().grouping()) ? "mes" : "día"),
            List.of("MONTH".equals(s.context().grouping()) ? "Mes" : "Día", "Ventas", "Brutas", "Devoluciones", "Anulaciones de ventas anteriores", "Netas"),
            rows, Map.of(2, MONEY, 3, MONEY, 4, MONEY, 5, MONEY));
    }

    private static TabularExport.Sheet productSheet(Products p) {
        List<List<Object>> rows = new ArrayList<>(); int n = 1;
        for (ProductRow r : p.products()) rows.add(Arrays.asList(n++, r.productName(), r.variantName(), r.sku(), r.category(),
            r.quantity(), r.unit(), r.amount(), r.returned(), r.share()));
        return new TabularExport.Sheet("Productos", List.of("#", "Producto", "Variante", "SKU", "Categoría", "Cantidad", "Unidad",
            "Monto bruto", "Devuelto", "% del total"), rows, Map.of(5, QTY, 7, MONEY, 8, MONEY, 9, PERCENT));
    }

    private static TabularExport.Sheet categorySheet(Products p) {
        List<List<Object>> rows = p.categories().stream().map(c -> Arrays.<Object>asList(c.category(), c.products(), c.amount(), c.share())).toList();
        return new TabularExport.Sheet("Categorías", List.of("Categoría", "Productos vendidos", "Monto", "% del total"), rows, Map.of(2, MONEY, 3, PERCENT));
    }

    private static TabularExport.Sheet paymentSheet(Payments p) {
        List<List<Object>> rows = new ArrayList<>();
        for (PaymentRow r : p.methods()) rows.add(Arrays.asList(r.label(), r.payments(), r.collected(), r.refunded(), r.net()));
        rows.add(Arrays.asList("Total", p.total().payments(), p.total().collected(), p.total().refunded(), p.total().net()));
        if (p.unpaidSales() > 0) rows.add(Arrays.asList("Ventas sin cobro registrado (monto vendido)", p.unpaidSales(), p.unpaidAmount(), null, null));
        return new TabularExport.Sheet("Cobros", List.of("Medio de pago", "Pagos", "Cobrado", "Reintegrado", "Neto"), rows, Map.of(2, MONEY, 3, MONEY, 4, MONEY));
    }

    private static TabularExport.Sheet sellerSheet(Sellers s) {
        List<List<Object>> rows = new ArrayList<>();
        for (SellerRow r : s.rows()) rows.add(Arrays.asList(r.name(), r.sales(), r.gross(), r.returns(), r.voids(), r.net(), r.average()));
        SellerRow t = s.total();
        rows.add(Arrays.asList("Total", t.sales(), t.gross(), t.returns(), t.voids(), t.net(), t.average()));
        return new TabularExport.Sheet("Vendedores", List.of("Vendedor", "Ventas", "Brutas", "Devoluciones", "Anulaciones de ventas anteriores",
            "Netas", "Venta promedio"), rows, Map.of(2, MONEY, 3, MONEY, 4, MONEY, 5, MONEY, 6, MONEY));
    }

    private static TabularExport.Sheet idleSheet(Idle i) {
        List<List<Object>> rows = i.rows().stream().map(r -> Arrays.<Object>asList(r.branchName(), r.productName(), r.variantName(), r.sku(),
            r.category(), r.available(), r.unit(), r.lastSale() == null ? "Nunca vendido" : LocalDate.ofInstant(r.lastSale(), COSTA_RICA).toString())).toList();
        return new TabularExport.Sheet("Sin ventas", List.of("Sucursal", "Producto", "Variante", "SKU", "Categoría", "Disponible hoy", "Unidad", "Última venta"),
            rows, Map.of(5, QTY));
    }

    private static List<Object> row(String label, Object current, Object previous) { return Arrays.asList(label, current, previous); }

    // ---------- Cálculo ----------

    private static Kpis kpis(Facts facts, Instant lower, Instant upper) {
        BigDecimal gross = ZERO, laterVoids = ZERO, voidAmount = ZERO, returns = ZERO; int count = 0, voids = 0;
        for (Sale x : facts.sales()) {
            if (x.in(lower, upper)) {
                if (x.sameDayVoid()) { voids++; voidAmount = voidAmount.add(x.total()); }
                else { count++; gross = gross.add(x.total()); }
            }
            if (x.voidIn(lower, upper) && !x.sameDayVoid()) laterVoids = laterVoids.add(x.total());
        }
        for (Return r : facts.returns()) if (!r.at().isBefore(lower) && r.at().isBefore(upper)) returns = returns.add(r.amount());
        return new Kpis(gross, returns, laterVoids, gross.subtract(returns).subtract(laterVoids), count, average(gross, count), voids, voidAmount);
    }

    private static List<Bucket> series(Facts facts, Scope s) {
        boolean monthly = "MONTH".equals(s.context().grouping());
        TreeMap<LocalDate, BucketAcc> buckets = new TreeMap<>();
        for (LocalDate d = s.context().from(); !d.isAfter(s.context().to()); d = d.plusDays(1)) buckets.putIfAbsent(key(d, monthly, s), new BucketAcc());
        for (Sale x : facts.sales()) {
            if (x.in(s.lower(), s.upper()) && !x.sameDayVoid()) { BucketAcc b = buckets.get(key(day(x.at()), monthly, s)); b.sales++; b.gross = b.gross.add(x.total()); }
            if (x.voidIn(s.lower(), s.upper()) && !x.sameDayVoid()) { BucketAcc b = buckets.get(key(day(x.voidAt()), monthly, s)); b.voids = b.voids.add(x.total()); }
        }
        for (Return r : facts.returns()) if (!r.at().isBefore(s.lower()) && r.at().isBefore(s.upper())) {
            BucketAcc b = buckets.get(key(day(r.at()), monthly, s)); b.returns = b.returns.add(r.amount()); }
        return buckets.entrySet().stream().map(e -> new Bucket(e.getKey(), e.getValue().sales, e.getValue().gross, e.getValue().returns,
            e.getValue().voids, e.getValue().gross.subtract(e.getValue().returns).subtract(e.getValue().voids))).toList();
    }

    private static LocalDate key(LocalDate d, boolean monthly, Scope s) {
        if (!monthly) return d;
        LocalDate first = d.withDayOfMonth(1);
        return first.isBefore(s.context().from()) ? s.context().from() : first;
    }

    private static List<ProductRow> products(Facts facts, Scope s, BigDecimal gross) {
        Map<String, ProductAcc> acc = new LinkedHashMap<>();
        for (Line l : facts.lines()) {
            Sale x = facts.sale(l.saleId());
            if (x.in(s.lower(), s.upper()) && !x.sameDayVoid()) acc.computeIfAbsent(l.itemId(), k -> new ProductAcc(l)).sold(l);
            if (x.voidIn(s.lower(), s.upper()) && !x.sameDayVoid()) acc.computeIfAbsent(l.itemId(), k -> new ProductAcc(l)).returned(l.amount());
        }
        for (ReturnLine l : facts.returnLines()) if (!l.at().isBefore(s.lower()) && l.at().isBefore(s.upper())) acc.computeIfAbsent(l.item().itemId(), k -> new ProductAcc(l.item())).returned(l.amount());
        return acc.values().stream().map(p -> new ProductRow(p.info.itemId(), p.info.productName(), p.info.variantName(), p.info.sku(),
            p.info.category(), p.info.unit(), p.quantity, p.amount, p.returned, share(p.amount, gross))).toList();
    }

    private static Comparator<ProductRow> order(String sort) {
        Comparator<ProductRow> byName = Comparator.comparing(ProductRow::productName).thenComparing(p -> p.variantName() == null ? "" : p.variantName());
        return switch (sort == null ? "" : sort) {
            case "QUANTITY" -> Comparator.comparing(ProductRow::quantity).reversed().thenComparing(byName);
            case "AMOUNT_ASC" -> Comparator.comparing(ProductRow::amount).thenComparing(byName);
            default -> Comparator.comparing(ProductRow::amount).reversed().thenComparing(byName);
        };
    }

    private static BigDecimal share(BigDecimal part, BigDecimal whole) {
        return whole.signum() == 0 ? BigDecimal.ZERO.setScale(4) : part.divide(whole, 4, RoundingMode.HALF_UP);
    }

    private static BigDecimal average(BigDecimal gross, int count) {
        return count == 0 ? ZERO : gross.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    private static <T> BigDecimal sum(List<T> rows, java.util.function.Function<T, BigDecimal> f) {
        return rows.stream().map(f).reduce(ZERO, BigDecimal::add);
    }

    static LocalDate day(Instant at) { return LocalDate.ofInstant(at, COSTA_RICA); }

    // ---------- Datos ----------

    /** Ventas visibles cuya venta o anulación cae en el período (o en el anterior), devoluciones del rango y sus líneas. */
    private Facts facts(Scope s, Instant lower, Instant upper, boolean withLines) {
        Object[] range = s.params(lower, upper, lower, upper);
        Map<String, Sale> sales = new LinkedHashMap<>();
        jdbc.query("""
            SELECT s.id,s.branch_id,m.actor_id,COALESCE(u.full_name,m.actor_name),m.created_at,s.total,v.created_at
            FROM sales s
            JOIN inventory_movements m ON m.company_id=s.company_id AND m.id=s.id
            LEFT JOIN inventory_movements v ON v.company_id=m.company_id AND v.reversed_id=m.id
            LEFT JOIN users u ON u.company_id=m.company_id AND u.id=m.actor_id
            WHERE s.company_id=? AND ((m.created_at>=? AND m.created_at<?) OR (v.created_at>=? AND v.created_at<?)) AND""" + VISIBLE,
            rs -> { Timestamp v = rs.getTimestamp(7);
                sales.put(rs.getString(1), new Sale(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                    rs.getTimestamp(5).toInstant(), rs.getBigDecimal(6).setScale(2), v == null ? null : v.toInstant())); }, range);
        List<Return> returns = jdbc.query("""
            SELECT r.id,r.amount,r.created_at,m.actor_id,COALESCE(u.full_name,m.actor_name)
            FROM sale_returns r
            JOIN sales s ON s.company_id=r.company_id AND s.id=r.sale_id
            JOIN inventory_movements m ON m.company_id=s.company_id AND m.id=s.id
            LEFT JOIN users u ON u.company_id=m.company_id AND u.id=m.actor_id
            WHERE r.company_id=? AND r.created_at>=? AND r.created_at<? AND""" + VISIBLE,
            (rs, n) -> new Return(rs.getString(1), rs.getBigDecimal(2).setScale(2), rs.getTimestamp(3).toInstant(), rs.getString(4), rs.getString(5)),
            s.params(lower, upper));
        List<Line> lines = new ArrayList<>(); List<ReturnLine> returnLines = new ArrayList<>(); Map<String, String> search = new HashMap<>();
        if (withLines) {
            jdbc.query("""
                SELECT l.movement_id,l.item_id,l.item_name,l.sku,l.unit,l.quantity,p.amount,cp.name,ci.variant_name,ci.sku,cp.category,ci.barcode,ci.unit
                FROM inventory_movement_lines l
                JOIN sale_line_prices p ON p.company_id=l.company_id AND p.sale_id=l.movement_id AND p.item_id=l.item_id
                JOIN sales s ON s.company_id=l.company_id AND s.id=l.movement_id
                JOIN inventory_movements m ON m.company_id=s.company_id AND m.id=s.id
                LEFT JOIN inventory_movements v ON v.company_id=m.company_id AND v.reversed_id=m.id
                LEFT JOIN catalog_items ci ON ci.company_id=l.company_id AND ci.id=l.item_id
                LEFT JOIN catalog_products cp ON cp.company_id=ci.company_id AND cp.id=ci.product_id
                WHERE l.company_id=? AND ((m.created_at>=? AND m.created_at<?) OR (v.created_at>=? AND v.created_at<?)) AND""" + VISIBLE,
                rs -> { Item item = item(rs, 2, search);
                    // La venta es una salida: la línea guarda la cantidad en negativo.
                    lines.add(new Line(rs.getString(1), item.itemId(), item, rs.getBigDecimal(6).abs(), rs.getBigDecimal(7).setScale(2))); }, range);
            jdbc.query("""
                SELECT r.created_at,rl.item_id,ml.item_name,ml.sku,ml.unit,rl.quantity,rl.amount,cp.name,ci.variant_name,ci.sku,cp.category,ci.barcode,ci.unit
                FROM sale_return_lines rl
                JOIN sale_returns r ON r.company_id=rl.company_id AND r.id=rl.return_id
                JOIN sales s ON s.company_id=r.company_id AND s.id=r.sale_id
                JOIN inventory_movement_lines ml ON ml.company_id=rl.company_id AND ml.movement_id=rl.sale_id AND ml.item_id=rl.item_id
                LEFT JOIN catalog_items ci ON ci.company_id=rl.company_id AND ci.id=rl.item_id
                LEFT JOIN catalog_products cp ON cp.company_id=ci.company_id AND cp.id=ci.product_id
                WHERE rl.company_id=? AND r.created_at>=? AND r.created_at<? AND""" + VISIBLE,
                rs -> { Item item = item(rs, 2, search); returnLines.add(new ReturnLine(item, rs.getBigDecimal(7).setScale(2), rs.getTimestamp(1).toInstant())); },
                s.params(lower, upper));
        }
        return new Facts(sales, returns, lines, returnLines, search);
    }

    /** Datos actuales del catálogo; si el artículo ya no existe, los guardados en la línea. */
    private static Item item(java.sql.ResultSet rs, int col, Map<String, String> search) throws java.sql.SQLException {
        String id = rs.getString(col), name = rs.getString(8), variant = rs.getString(9), sku = rs.getString(10), category = rs.getString(11), unit = rs.getString(13);
        Item item = name == null
            ? new Item(id, rs.getString(3), null, rs.getString(4), NO_CATEGORY, rs.getString(5))
            : new Item(id, name, variant, sku, category == null ? NO_CATEGORY : category, unit);
        search.putIfAbsent(id, TabularExport.plain(String.join(" ", item.productName(), Objects.toString(item.variantName(), ""),
            Objects.toString(item.sku(), ""), Objects.toString(rs.getString(12), ""))));
        return item;
    }

    // ---------- Período y alcance ----------

    /** Sucursal elegida (vacía = todas las autorizadas) y permisos, como el resumen de Cobros. */
    private static final String VISIBLE = """
         (?='' OR s.branch_id=?) AND (? OR EXISTS (SELECT 1 FROM inventory_branch_users bu
             WHERE bu.company_id=s.company_id AND bu.branch_id=s.branch_id AND bu.user_id=?))
        """;

    private Scope scope(StocklyPrincipal actor, Filters f) {
        if (actor == null || actor.companyId() == null || actor.mustChangePassword())
            throw new IdentityFailure(403, "FORBIDDEN", "No tienes acceso a reportes.");
        String company = actor.companyId();
        boolean admin = InventoryScope.administrator(actor);
        LocalDate today = LocalDate.now(COSTA_RICA);
        LocalDate to = date(f.to(), today), from = date(f.from(), to.minusDays(6));
        if (from.isAfter(today) || to.isAfter(today)) throw invalid("No se aceptan fechas futuras.");
        if (from.isAfter(to)) throw invalid("La fecha «Desde» debe ser igual o anterior a «Hasta».");
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days > MAX_DAYS) throw invalid("El período puede ser de hasta 366 días.");
        String branch = f.branchId() == null ? "" : f.branchId().strip(), branchName = null;
        if (!branch.isEmpty()) {
            branchName = jdbc.queryForList("SELECT name FROM inventory_branches WHERE company_id=? AND id=?", String.class, company, branch)
                .stream().findFirst().orElse(null);
            if (branchName == null || !admin && !Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM inventory_branch_users WHERE company_id=? AND branch_id=? AND user_id=?)
                """, Boolean.class, company, branch, actor.accountId())))
                throw new IdentityFailure(404, "BRANCH_NOT_FOUND", "No se encontró la sucursal.");
        }
        String companyName = jdbc.queryForList("SELECT commercial_name FROM companies WHERE id=?", String.class, company).stream().findFirst().orElse("");
        List<Branch> branches = scope.options(actor).stream().map(o -> new Branch(o.id(), o.name())).toList();
        LocalDate previousTo = from.minusDays(1), previousFrom = from.minusDays(days);
        Context context = new Context(companyName, branch.isEmpty() ? null : branch, branchName, from, to, previousFrom, previousTo,
            days > DAILY_LIMIT ? "MONTH" : "DAY", branches);
        return new Scope(company, branch, admin, actor.accountId(), context, start(from), start(to.plusDays(1)), start(previousFrom));
    }

    private static Instant start(LocalDate d) { return d.atStartOfDay(COSTA_RICA).toInstant(); }

    private static LocalDate date(String value, LocalDate fallback) {
        if (value == null || value.isBlank()) return fallback;
        try { return LocalDate.parse(value.strip()); }
        catch (DateTimeParseException e) { throw invalid("Revisa las fechas del período."); }
    }

    private static IdentityFailure invalid(String message) {
        return new IdentityFailure(400, "REPORT_INVALID", message, Map.of("period", message));
    }

    private record Scope(String company, String branch, boolean admin, String account, Context context,
                         Instant lower, Instant upper, Instant previousLower) {
        Object[] params(Object... range) {
            List<Object> p = new ArrayList<>(); p.add(company); for (Object r : range) p.add(r instanceof Instant i ? Timestamp.from(i) : r); p.addAll(Arrays.asList(visible())); return p.toArray();
        }
        Object[] visibleParams() { List<Object> p = new ArrayList<>(); p.add(company); p.addAll(Arrays.asList(visible())); return p.toArray(); }
        private Object[] visible() { return new Object[]{branch, branch, admin, account}; }
    }

    private record Sale(String id, String branchId, String sellerId, String sellerName, Instant at, BigDecimal total, Instant voidAt) {
        boolean sameDayVoid() { return voidAt != null && day(voidAt).equals(day(at)); }
        boolean in(Instant lower, Instant upper) { return !at.isBefore(lower) && at.isBefore(upper); }
        boolean voidIn(Instant lower, Instant upper) { return voidAt != null && !voidAt.isBefore(lower) && voidAt.isBefore(upper); }
    }
    private record Return(String id, BigDecimal amount, Instant at, String sellerId, String sellerName) {}
    private record Item(String itemId, String productName, String variantName, String sku, String category, String unit) {}
    private record Line(String saleId, String itemId, Item item, BigDecimal quantity, BigDecimal amount) {}
    private record ReturnLine(Item item, BigDecimal amount, Instant at) {}
    private record Facts(Map<String, Sale> byId, List<Return> returns, List<Line> lines, List<ReturnLine> returnLines, Map<String, String> searchText) {
        Collection<Sale> sales() { return byId.values(); }
        Sale sale(String id) { return byId.get(id); }
    }

    private static final class BucketAcc { int sales; BigDecimal gross = ZERO, returns = ZERO, voids = ZERO; }

    private static final class ProductAcc {
        final Item info; BigDecimal quantity = BigDecimal.ZERO.setScale(3), amount = ZERO, returned = ZERO;
        ProductAcc(Line l) { this(l.item()); }
        ProductAcc(Item info) { this.info = info; }
        void sold(Line l) { quantity = quantity.add(l.quantity()); amount = amount.add(l.amount()); }
        void returned(BigDecimal value) { returned = returned.add(value); }
    }

    private static final class SellerAcc {
        final String name; int count; BigDecimal gross = ZERO, returns = ZERO, voids = ZERO;
        SellerAcc(String name) { this.name = name; }
        void sale(BigDecimal total) { count++; gross = gross.add(total); }
        void voids(BigDecimal total) { voids = voids.add(total); }
        void returned(BigDecimal amount) { returns = returns.add(amount); }
        SellerRow row(String id) { return new SellerRow(id, name, count, gross, returns, voids, gross.subtract(returns).subtract(voids), average(gross, count)); }
    }
}
