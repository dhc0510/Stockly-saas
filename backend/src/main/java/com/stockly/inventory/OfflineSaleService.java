package com.stockly.inventory;

import com.stockly.identity.IdentityFailure;
import com.stockly.identity.StocklyPrincipal;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class OfflineSaleService {
    private static final InventoryLedger.Rules RULES = new InventoryLedger.Rules("OFFLINE_INVALID", "OFFLINE_CONFLICT");
    private static final Set<String> REVIEWABLE = Set.of("SALE_LINE_ERROR", "SALE_PAYMENT_METHOD_INACTIVE", "SALE_PAYMENT_INVALID");
    private final SecureRandom random = new SecureRandom();
    private final JsonMapper json = JsonMapper.builder().build();
    private final JdbcTemplate jdbc;
    private final InventoryScope scope;
    private final InventoryLedger ledger;
    private final SaleService sales;
    private final SalePaymentService payments;

    OfflineSaleService(JdbcTemplate jdbc, InventoryScope scope, InventoryLedger ledger, SaleService sales, SalePaymentService payments) {
        this.jdbc = jdbc;
        this.scope = scope;
        this.ledger = ledger;
        this.sales = sales;
        this.payments = payments;
    }

    @Transactional
    public Snapshot prepare(StocklyPrincipal actor, String branch) {
        String company = SaleService.company(actor);
        String location = scope.location(actor, RULES.uuid(branch, "Selecciona una sucursal."));
        String id = UUID.randomUUID().toString();
        Instant issued = Instant.now().truncatedTo(ChronoUnit.MICROS);
        byte[] key = new byte[32];
        random.nextBytes(key);
        jdbc.update("""
            INSERT INTO offline_sale_snapshots(company_id,id,account_id,branch_id,issued_at,proof_key) VALUES (?,?,?,?,?,?)
            """, company, id, actor.accountId(), branch, Timestamp.from(issued), key);
        var items = jdbc.query("""
            SELECT i.id,i.sku,i.barcode,i.unit,i.price,p.name,i.variant_name,COALESCE(b.quantity,0) AS balance
            FROM inventory_branch_items bi
            JOIN catalog_items i ON i.company_id=bi.company_id AND i.id=bi.item_id AND i.active
            JOIN catalog_products p ON p.company_id=i.company_id AND p.id=i.product_id AND p.active
            LEFT JOIN inventory_balances b ON b.company_id=i.company_id AND b.item_id=i.id AND b.location_id=?
            WHERE bi.company_id=? AND bi.branch_id=? AND i.price IS NOT NULL
            ORDER BY p.name,i.variant_name,i.id
            """, (rs, row) -> {
                var article = InventoryLedger.article(rs);
                var price = rs.getBigDecimal("price");
                return new Item(article, price, proof(key, article.id(), price));
            }, location, company, branch);
        return new Snapshot(id, issued, issued.plus(24, ChronoUnit.HOURS), items, payments.methods(actor));
    }

    @Transactional
    public Entry sync(StocklyPrincipal actor, Input input) {
        String company = SaleService.company(actor);
        if (input == null || input.sale() == null || input.soldAt() == null || input.proofs() == null)
            throw RULES.invalid("La venta sin internet está incompleta.");
        var sale = input.sale();
        String id = RULES.uuid(sale.requestKey(), "Identificador de venta inválido.");
        String branch = RULES.uuid(sale.branchId(), "Selecciona una sucursal.");
        ledger.lockLocation(company);
        scope.location(actor, branch);
        String payload = json.writeValueAsString(sale);
        String hash = InventoryLedger.hash(payload, input.soldAt().toString());
        Entry existing = find(company, id);
        if (existing != null) {
            if (!actor.accountId().equals(existing.accountId()) || !branch.equals(existing.sale().branchId()))
                throw missing();
            if (!hash.equals(existing.requestHash())) throw RULES.conflict("El intento ya fue recibido con otros datos.");
            return existing;
        }
        verifySnapshot(actor, company, branch, input);
        jdbc.update("""
            INSERT INTO offline_sales(company_id,id,account_id,branch_id,sold_at,payload,request_hash,state)
            VALUES (?,?,?,?,?,?,?,'REVIEW')
            """, company, id, actor.accountId(), branch, Timestamp.from(input.soldAt()), payload, hash);
        return attempt(actor, company, find(company, id));
    }

    private void verifySnapshot(StocklyPrincipal actor, String company, String branch, Input input) {
        var grant = jdbc.query("""
            SELECT issued_at,proof_key FROM offline_sale_snapshots
            WHERE company_id=? AND id=? AND account_id=? AND branch_id=?
            """, (rs, row) -> new Grant(rs.getTimestamp(1).toInstant(), rs.getBytes(2)), company,
            RULES.uuid(input.snapshotId(), "Identificador de preparación inválido."), actor.accountId(), branch)
            .stream().findFirst().orElseThrow(() -> RULES.invalid("No se encontró la preparación de esta venta."));
        if (input.soldAt().isBefore(grant.issued().truncatedTo(ChronoUnit.MILLIS))
            || !input.soldAt().isBefore(grant.issued().plus(24, ChronoUnit.HOURS))
            || input.soldAt().isAfter(Instant.now().plusSeconds(60)))
            throw RULES.invalid("La fecha de venta está fuera del período de acceso sin internet.");
        for (var line : SaleService.validateLines(input.sale().lines()).values()) {
            String supplied = input.proofs().get(line.itemId());
            if (line.expectedPrice() == null || supplied == null || !MessageDigest.isEqual(
                proof(grant.key(), line.itemId(), line.expectedPrice()).getBytes(StandardCharsets.US_ASCII),
                supplied.getBytes(StandardCharsets.US_ASCII)))
                throw RULES.invalid("Un precio no coincide con los datos guardados para esta sucursal.");
        }
    }

    @Transactional(readOnly = true)
    public Entry status(StocklyPrincipal actor, String id) {
        Entry entry = authorized(actor, SaleService.company(actor), id);
        if (!actor.accountId().equals(entry.accountId())) throw missing();
        return entry;
    }

    @Transactional(readOnly = true)
    public List<Entry> review(StocklyPrincipal actor) {
        String company = SaleService.company(actor);
        return scope.options(actor).stream().flatMap(branch -> jdbc.query(SELECT + """
            WHERE o.company_id=? AND o.branch_id=? AND o.state IN ('REVIEW','CLOSED')
            ORDER BY o.sold_at DESC
            """, this::entry, company, branch.id()).stream()).toList();
    }

    @Transactional
    public Entry retry(StocklyPrincipal actor, String id) {
        String company = SaleService.company(actor);
        ledger.lockLocation(company);
        Entry current = authorized(actor, company, id);
        return "REVIEW".equals(current.state()) ? attempt(actor, company, current) : current;
    }

    @Transactional
    public Entry close(StocklyPrincipal actor, String id, Closure closure) {
        String company = SaleService.company(actor);
        ledger.lockLocation(company);
        Entry current = authorized(actor, company, id);
        if (!"REVIEW".equals(current.state())) return current;
        if (closure == null) throw RULES.invalid("Escribe el motivo del cierre.");
        String reason = RULES.required(closure.reason(), 150, "Escribe el motivo del cierre.");
        String refund = RULES.optional(closure.refundDetail(), 300);
        jdbc.update("""
            UPDATE offline_sales SET state='CLOSED',resolved_by=?,resolved_at=CURRENT_TIMESTAMP(6),close_reason=?,refund_detail=?
            WHERE company_id=? AND id=?
            """, actor.accountId(), reason, refund, company, id);
        return find(company, id);
    }

    private Entry attempt(StocklyPrincipal actor, String company, Entry entry) {
        SaleService.Sale sale;
        try {
            sale = sales.confirmOffline(actor, entry.sale(), entry.reference(), entry.soldAt());
        } catch (IdentityFailure failure) {
            if (!REVIEWABLE.contains(failure.code)) throw failure;
            jdbc.update("UPDATE offline_sales SET issue=? WHERE company_id=? AND id=?",
                json.writeValueAsString(new Issue(failure.getMessage(), failure.fields)), company, entry.id());
            return find(company, entry.id());
        }
        jdbc.update("""
            UPDATE offline_sales SET state='SYNCED',sale_id=?,issue=NULL,resolved_by=?,resolved_at=CURRENT_TIMESTAMP(6)
            WHERE company_id=? AND id=?
            """, sale.movement().id(), actor.accountId(), company, entry.id());
        return find(company, entry.id());
    }

    private Entry authorized(StocklyPrincipal actor, String company, String id) {
        Entry entry = find(company, RULES.uuid(id, "Identificador de venta inválido."));
        if (entry == null) throw missing();
        scope.location(actor, entry.sale().branchId());
        return entry;
    }

    private static IdentityFailure missing() { return new IdentityFailure(404, "OFFLINE_NOT_FOUND", "No se encontró la venta."); }

    private static String proof(byte[] key, String item, BigDecimal price) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                mac.doFinal((item + ":" + price.stripTrailingZeros().toPlainString()).getBytes(StandardCharsets.US_ASCII)));
        } catch (java.security.GeneralSecurityException e) { throw new IllegalStateException(e); }
    }

    private static final String SELECT = """
        SELECT o.*,m.folio,b.name AS branch_name,u.full_name AS resolver

        FROM offline_sales o
        JOIN inventory_branches b ON b.company_id=o.company_id AND b.id=o.branch_id
        LEFT JOIN inventory_movements m ON m.company_id=o.company_id AND m.id=o.sale_id
        LEFT JOIN users u ON u.company_id=o.company_id AND u.id=o.resolved_by
        """;

    private Entry find(String company, String id) {
        return jdbc.query(SELECT + " WHERE o.company_id=? AND o.id=?", this::entry, company, id).stream().findFirst().orElse(null);
    }

    private Entry entry(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        String id = rs.getString("id");
        String issue = rs.getString("issue");

        return new Entry(id, "LOCAL-" + id.toUpperCase(Locale.ROOT), rs.getString("state"), rs.getString("folio"),
            rs.getTimestamp("sold_at").toInstant(), rs.getString("branch_name"), rs.getString("account_id"),
            rs.getString("request_hash"), json.readValue(rs.getString("payload"), SaleService.SaleInput.class),
            issue == null ? null : json.readValue(issue, Issue.class), rs.getString("resolver"),
            rs.getString("close_reason"), rs.getString("refund_detail"));
    }

    private record Grant(Instant issued, byte[] key) {}
    public record Snapshot(String id, Instant issuedAt, Instant expiresAt, List<Item> items, List<SalePaymentService.MethodState> methods) {}
    public record Item(InventoryLedger.Article article, BigDecimal price, String proof) {}
    public record Input(String snapshotId, Instant soldAt, SaleService.SaleInput sale, Map<String, String> proofs) {}
    public record Issue(String message, Map<String, String> fields) {}
    public record Closure(String reason, String refundDetail) {}
    public record Entry(String id, String reference, String state, String folio, Instant soldAt, String branchName,
                        @com.fasterxml.jackson.annotation.JsonIgnore String accountId,
                        @com.fasterxml.jackson.annotation.JsonIgnore String requestHash,
                        SaleService.SaleInput sale, Issue issue, String resolver, String closeReason, String refundDetail) {}
}
