package com.stockly.inventory;

import com.stockly.identity.IdentityFailure;
import com.stockly.identity.StocklyPrincipal;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

import static com.stockly.inventory.InventoryContracts.*;

@Service
public class InventoryService {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final BigDecimal DEFAULT_LOW = new BigDecimal("2.00");
    private static final BigDecimal DEFAULT_RECOVERY = new BigDecimal("1.00");
    private final JdbcTemplate jdbc;

    private final InventoryLedger ledger;
    private final com.stockly.billing.FiscalDocumentService fiscal;
    private final com.stockly.identity.AuditEventService audit;

    public InventoryService(JdbcTemplate jdbc, InventoryLedger ledger, com.stockly.billing.FiscalDocumentService fiscal,
                            com.stockly.identity.AuditEventService audit) {
        this.jdbc = jdbc;
        this.ledger = ledger;
        this.fiscal = fiscal;
        this.audit = audit;
    }

    public record LocationSetup(List<Branch> branches, boolean needsPrincipal, boolean pending) {}

    @Transactional(readOnly = true)
    public LocationSetup locationSetup(StocklyPrincipal actor) {
        String company = requireAdmin(actor);
        boolean linked = Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM inventory_branches WHERE company_id=? AND location_id='principal')
            """, Boolean.class, company));
        boolean history = Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM inventory_movements WHERE company_id=? AND location_id='principal')
                OR EXISTS(SELECT 1 FROM inventory_balances WHERE company_id=? AND location_id='principal' AND quantity>0)
            """, Boolean.class, company, company));
        boolean pending = Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM inventory_branches WHERE company_id=? AND active AND location_id IS NULL)
            """, Boolean.class, company));
        return new LocationSetup(branches(actor, false), history && !linked, pending);
    }

    @Transactional
    public LocationSetup configureLocations(StocklyPrincipal actor, String branchId) {
        String company = requireAdmin(actor);
        ledger.lockLocation(company);
        LocationSetup setup = locationSetup(actor);
        if (setup.needsPrincipal()) {
            if (branchId == null || setup.branches().stream().noneMatch(branch -> branch.id().equals(branchId)))
                throw invalid("Selecciona la sucursal que corresponde al inventario de Principal.");
            jdbc.update("UPDATE inventory_branches SET location_id='principal' WHERE company_id=? AND id=?",
                company, branchId);
        }
        jdbc.update("""
            INSERT INTO inventory_locations(company_id,id,name)
            SELECT company_id,id,name FROM inventory_branches WHERE company_id=? AND active AND location_id IS NULL
            """, company);
        jdbc.update("""
            UPDATE inventory_branches SET location_id=id WHERE company_id=? AND active AND location_id IS NULL
            """, company);
        return locationSetup(actor);
    }


    @Transactional(readOnly = true)
    public List<Branch> branches(StocklyPrincipal actor, boolean includeInactive) {
        String company = company(actor);
        if (admin(actor)) return jdbc.query("""
            SELECT id,name,code,address,phone,contact_email,manager_name,active
            FROM inventory_branches WHERE company_id=? AND (? OR active=TRUE) ORDER BY active DESC,name,id
            """, (rs, row) -> branch(rs), company, includeInactive);
        return jdbc.query("""
            SELECT b.id,b.name,b.code,b.address,b.phone,b.contact_email,b.manager_name,b.active
            FROM inventory_branches b JOIN inventory_branch_users a
              ON a.company_id=b.company_id AND a.branch_id=b.id
            WHERE b.company_id=? AND a.user_id=? AND (? OR b.active=TRUE)
            ORDER BY b.active DESC,b.name,b.id
            """, (rs, row) -> branch(rs), company, actor.accountId(), includeInactive);
    }

    @Transactional
    public Branch createBranch(StocklyPrincipal actor, BranchInput input) {
        String company = requireAdmin(actor);
        BranchInput value = validBranch(input);
        String id = UUID.randomUUID().toString(); Instant now = Instant.now();
        try {
            jdbc.update("""
                INSERT INTO inventory_branches(company_id,id,name,code,address,phone,contact_email,manager_name,active,created_at,updated_at)
                VALUES(?,?,?,?,?,?,?,?,TRUE,?,?)
                """, company, id, value.name(), value.code(), value.address(), value.phone(), value.contactEmail(),
                value.managerName(), Timestamp.from(now), Timestamp.from(now));
            jdbc.update("INSERT INTO inventory_branch_users(company_id,branch_id,user_id) VALUES(?,?,?)",
                company, id, actor.accountId());
        } catch (DuplicateKeyException e) { throw conflict("Ya existe una sucursal con ese código."); }
        fiscal.branchCode(company, id); // STK-013: código de establecimiento de Hacienda (001, 002…).
        return getBranch(company, id);
    }

    @Transactional
    public Branch editBranch(StocklyPrincipal actor, String branchId, BranchInput input) {
        String company = requireAdmin(actor); BranchInput value = validBranch(input); requireBranchExists(company, branchId);
        try {
            jdbc.update("""
                UPDATE inventory_branches SET name=?,code=?,address=?,phone=?,contact_email=?,manager_name=?,updated_at=?
                WHERE company_id=? AND id=?
                """, value.name(), value.code(), value.address(), value.phone(), value.contactEmail(), value.managerName(),
                Timestamp.from(Instant.now()), company, branchId);
        } catch (DuplicateKeyException e) { throw conflict("Ya existe una sucursal con ese código."); }
        return getBranch(company, branchId);
    }

    @Transactional
    public void deactivateBranch(StocklyPrincipal actor, String branchId) {
        String company = requireAdmin(actor);
        ledger.lockLocation(company);
        requireBranchExists(company, branchId);
        Integer stock = jdbc.queryForObject("""
            SELECT EXISTS (
                SELECT 1 FROM inventory_balances s JOIN inventory_branches b
                  ON b.company_id=s.company_id AND b.location_id=s.location_id
                WHERE b.company_id=? AND b.id=? AND s.quantity>0
            )
            """, Integer.class, company, branchId);
        if (stock != null && stock != 0)
            throw conflict("Reasigna las existencias antes de desactivar esta sucursal.");
        Long assignments = jdbc.queryForObject("""
            SELECT COUNT(*) FROM inventory_branch_items current_item
            WHERE current_item.company_id=? AND current_item.branch_id=?
              AND NOT EXISTS (SELECT 1 FROM inventory_branch_items other_item
                JOIN inventory_branches other_branch ON other_branch.company_id=other_item.company_id
                  AND other_branch.id=other_item.branch_id AND other_branch.active=TRUE
                WHERE other_item.company_id=current_item.company_id AND other_item.item_id=current_item.item_id
                  AND other_item.branch_id<>current_item.branch_id)
            """, Long.class, company, branchId);
        if (assignments != null && assignments > 0)
            throw conflict("Asigna los productos a otra sucursal activa antes de desactivar esta sucursal.");
        jdbc.update("UPDATE inventory_branches SET active=FALSE,updated_at=? WHERE company_id=? AND id=?",
            Timestamp.from(Instant.now()), company, branchId);
        jdbc.update("DELETE FROM inventory_branch_users WHERE company_id=? AND branch_id=?", company, branchId);
    }

    @Transactional
    public void activateBranch(StocklyPrincipal actor, String branchId) {
        String company = requireAdmin(actor); requireBranchExists(company, branchId);
        jdbc.update("UPDATE inventory_branches SET active=TRUE,updated_at=? WHERE company_id=? AND id=?",
            Timestamp.from(Instant.now()), company, branchId);
        jdbc.update("INSERT IGNORE INTO inventory_branch_users(company_id,branch_id,user_id) VALUES(?,?,?)",
            company, branchId, actor.accountId());
    }

    @Transactional
    public void deleteBranch(StocklyPrincipal actor, String branchId) {
        String company = requireAdmin(actor);
        ledger.lockLocation(company);
        requireBranchExists(company, branchId);
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM inventory_branch_items WHERE company_id=? AND branch_id=?",
            Long.class, company, branchId);
        if (count != null && count > 0) throw conflict("No se puede borrar una sucursal con productos asignados. Desactívala y conserva su historial.");
        Integer activity = jdbc.queryForObject("""
            SELECT EXISTS (
                SELECT 1 FROM inventory_movements m JOIN inventory_branches b
                  ON b.company_id=m.company_id AND b.location_id=m.location_id
                WHERE b.company_id=? AND b.id=?
            ) OR EXISTS (
                SELECT 1 FROM inventory_balances s JOIN inventory_branches b
                  ON b.company_id=s.company_id AND b.location_id=s.location_id
                WHERE b.company_id=? AND b.id=? AND s.quantity>0
            )
            """, Integer.class, company, branchId, company, branchId);
        if (activity != null && activity != 0)
            throw conflict("No se puede borrar una sucursal con existencias o historial.");
        jdbc.update("DELETE FROM inventory_branches WHERE company_id=? AND id=?", company, branchId);
    }

    @Transactional(readOnly = true)
    public List<BranchAccess> branchAccess(StocklyPrincipal actor, String branchId) {
        String company = requireAdmin(actor); requireBranchExists(company, branchId);
        return jdbc.query("""
            SELECT u.id,u.full_name,u.email_normalized,u.role,
              EXISTS(SELECT 1 FROM inventory_branch_users a WHERE a.company_id=u.company_id AND a.branch_id=? AND a.user_id=u.id)
            FROM users u WHERE u.company_id=? AND u.active=TRUE ORDER BY u.role,u.full_name,u.id
            """, (rs, row) -> new BranchAccess(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getBoolean(5)),
            branchId, company);
    }

    @Transactional
    public void setBranchAccess(StocklyPrincipal actor, String branchId, AccessInput input) {
        String company = requireAdmin(actor); requireBranchExists(company, branchId);
        List<String> userIds = input == null || input.userIds() == null ? List.of() : input.userIds().stream().distinct().toList();
        for (String userId : userIds) {
            Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE company_id=? AND id=? AND active=TRUE",
                Integer.class, company, userId);
            if (exists == null || exists != 1) throw invalid("La asignación incluye una cuenta inactiva o ajena a la empresa.");
        }
        jdbc.update("DELETE FROM inventory_branch_users WHERE company_id=? AND branch_id=?", company, branchId);
        for (String userId : userIds)
            jdbc.update("INSERT INTO inventory_branch_users(company_id,branch_id,user_id) VALUES(?,?,?)", company, branchId, userId);
        refreshAlerts(company, branchId);
    }

    @Transactional(readOnly = true)
    public List<StockItem> items(StocklyPrincipal actor, String branchId, String search, String category,
                                 String status, String sort) {
        String company = company(actor); Set<String> allowed = accessibleBranchIds(actor, company);
        if (branchId != null && !allowed.contains(branchId)) throw missing();
        if (branchId == null && allowed.isEmpty()) return List.of();
        String term = search == null ? "" : search.strip().replace("!", "!!").replace("%", "!%").replace("_", "!_");
        String selectedCategory = category == null ? "" : category.strip();
        List<Object> params = new ArrayList<>(); params.add(company);
        StringBuilder sql = new StringBuilder("""
            SELECT p.id,p.name,p.category,i.id,i.variant_name,i.sku,i.barcode,i.unit,b.id,b.name,
              bi.minimum_quantity,bi.low_threshold,bi.recovery_margin,bi.exhausted_basis,COALESCE(stock.quantity,0),bi.target_quantity,bi.restock_paused
            FROM inventory_branch_items bi
            JOIN inventory_branches b ON b.company_id=bi.company_id AND b.id=bi.branch_id AND b.active=TRUE
            JOIN catalog_items i ON i.company_id=bi.company_id AND i.id=bi.item_id AND i.active=TRUE
            JOIN catalog_products p ON p.company_id=i.company_id AND p.id=i.product_id AND p.active=TRUE
            LEFT JOIN inventory_balances stock ON stock.company_id=b.company_id AND stock.location_id=b.location_id AND stock.item_id=i.id
            WHERE bi.company_id=?
            """);
        if (branchId != null) { sql.append(" AND b.id=?"); params.add(branchId); }
        else { sql.append(" AND b.id IN (").append(String.join(",", Collections.nCopies(allowed.size(), "?"))).append(")"); params.addAll(allowed); }
        if (!term.isEmpty()) {
            sql.append(" AND (p.name COLLATE utf8mb4_0900_ai_ci LIKE ? ESCAPE '!' OR i.sku COLLATE utf8mb4_0900_ai_ci LIKE ? ESCAPE '!' OR i.barcode COLLATE utf8mb4_0900_ai_ci LIKE ? ESCAPE '!' OR p.category COLLATE utf8mb4_0900_ai_ci LIKE ? ESCAPE '!')");
            String like = "%" + term + "%"; params.addAll(List.of(like, like, like, like));
        }
        if (!selectedCategory.isEmpty()) { sql.append(" AND p.category=?"); params.add(selectedCategory); }
        List<StockItem> rows = jdbc.query(sql.append(" ORDER BY p.name,i.variant_name,b.name,p.id,i.id").toString(),
            (rs, row) -> stockItem(rs), params.toArray());
        List<StockItem> filtered = rows.stream().map(this::withState)
            .filter(item -> matchesStatus(item, status)).sorted(sortComparator(sort)).toList();
        return filtered;
    }

    @Transactional(readOnly=true)
    public List<PendingItem> pendingItems(StocklyPrincipal actor) {
        String company=requireAdmin(actor);
        return jdbc.query("""
            SELECT p.id,p.name,p.category,i.id,i.variant_name,i.sku,i.unit
            FROM catalog_items i JOIN catalog_products p ON p.company_id=i.company_id AND p.id=i.product_id
            WHERE i.company_id=? AND i.active=TRUE AND p.active=TRUE
              AND NOT EXISTS(SELECT 1 FROM inventory_branch_items bi JOIN inventory_branches b
                ON b.company_id=bi.company_id AND b.id=bi.branch_id AND b.active=TRUE
                WHERE bi.company_id=i.company_id AND bi.item_id=i.id)
            ORDER BY p.name,i.variant_name,i.id
            """,(rs,row)->new PendingItem(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7)),company);
    }

    @Transactional
    public void assignItem(StocklyPrincipal actor, String branchId, ItemAssignmentInput input) {
        String company = company(actor); requireBranchAccess(actor, company, branchId, false);
        if (input == null || input.itemId() == null) throw invalid("Selecciona un producto del catálogo.");
        Integer branchExists = jdbc.queryForObject("SELECT COUNT(*) FROM inventory_branches WHERE company_id=? AND id=? AND active=TRUE",
            Integer.class, company, branchId);
        Integer itemExists = jdbc.queryForObject("SELECT COUNT(*) FROM catalog_items WHERE company_id=? AND id=? AND active=TRUE",
            Integer.class, company, input.itemId());
        if (branchExists == null || branchExists != 1 || itemExists == null || itemExists != 1) throw missing();
        ThresholdInput thresholds = new ThresholdInput(input.minimumQuantity(), input.lowThreshold(), input.recoveryMargin(), input.exhaustedBasis());
        ThresholdInput clean = validThresholds(thresholds, true);
        Instant now = Instant.now();
        try {
            jdbc.update("""
                INSERT INTO inventory_branch_items(company_id,branch_id,item_id,minimum_quantity,low_threshold,recovery_margin,exhausted_basis,created_at,updated_at)
                VALUES(?,?,?,?,?,?,?,?,?)
                """, company, branchId, input.itemId(), clean.minimumQuantity(), clean.lowThreshold(), clean.recoveryMargin(),
                clean.exhaustedBasis(), Timestamp.from(now), Timestamp.from(now));
        } catch (DuplicateKeyException e) { throw conflict("El producto ya está asignado a esta sucursal."); }
        syncItemAlerts(company, branchId, input.itemId(), withState(loadOne(company, branchId, input.itemId())));
    }

    @Transactional
    public void updateThresholds(StocklyPrincipal actor, String branchId, String itemId, ThresholdInput input) {
        String company = company(actor); requireBranchAccess(actor, company, branchId, false);
        ThresholdInput clean = validThresholds(input, false);
        validTarget(input.targetQuantity(), clean.minimumQuantity());
        Boolean wasPaused = jdbc.query("SELECT restock_paused FROM inventory_branch_items WHERE company_id=? AND branch_id=? AND item_id=? FOR UPDATE",
            (rs,row)->rs.getBoolean(1), company, branchId, itemId).stream().findFirst().orElseThrow(InventoryService::missing);
        boolean paused = input.restockPaused() == null ? wasPaused : input.restockPaused();
        Instant now = Instant.now();
        jdbc.update("""
            UPDATE inventory_branch_items SET minimum_quantity=?,low_threshold=?,recovery_margin=?,exhausted_basis=?,target_quantity=?,updated_at=?
            WHERE company_id=? AND branch_id=? AND item_id=?
            """, clean.minimumQuantity(), clean.lowThreshold(), clean.recoveryMargin(), clean.exhaustedBasis(), input.targetQuantity(),
            Timestamp.from(now), company, branchId, itemId);
        if (paused != wasPaused) {
            jdbc.update("UPDATE inventory_branch_items SET restock_paused=?,restock_paused_by=?,restock_paused_at=? WHERE company_id=? AND branch_id=? AND item_id=?",
                paused, paused ? actor.accountId() : null, paused ? Timestamp.from(now) : null, company, branchId, itemId);
            audit.record(paused ? "RESTOCK_PAUSED" : "RESTOCK_RESUMED", "INVENTORY_ITEM", itemId);
        }
        jdbc.update("UPDATE inventory_alerts SET active=FALSE,dismissed_at=NULL,updated_at=? WHERE company_id=? AND branch_id=? AND item_id=?",
            Timestamp.from(Instant.now()),company,branchId,itemId);
        syncItemAlerts(company, branchId, itemId, withState(loadOne(company, branchId, itemId)));
    }

    @Transactional
    public void unassignItem(StocklyPrincipal actor, String branchId, String itemId) {
        String company = requireAdmin(actor); requireBranchExists(company, branchId);
        Integer other = jdbc.queryForObject("""
            SELECT COUNT(*) FROM inventory_branch_items WHERE company_id=? AND item_id=? AND branch_id<>?
            """, Integer.class, company, itemId, branchId);
        if (other == null || other == 0) throw conflict("Cada producto debe permanecer asignado al menos a una sucursal.");
        jdbc.update("DELETE FROM inventory_branch_items WHERE company_id=? AND branch_id=? AND item_id=?", company, branchId, itemId);
    }

    @Transactional
    public List<Alert> alerts(StocklyPrincipal actor) {
        String company = company(actor); Set<String> assigned = assignedBranchIds(actor, company);
        if (assigned.isEmpty()) return List.of();
        List<StockItem> rows = loadAssignedItems(company, assigned);
        for (StockItem raw : rows) syncItemAlerts(company, raw.branchId(), raw.itemId(), withState(raw));
        Instant now = Instant.now();
        List<Map<String,Object>> due = jdbc.queryForList("""
            SELECT id FROM inventory_alerts WHERE company_id=? AND recipient_id=? AND active=TRUE
              AND (last_notified_at IS NULL OR last_notified_at<=?)
            """, company, actor.accountId(), Timestamp.from(now.minusSeconds(24 * 60 * 60L)));
        for (Map<String,Object> row : due) jdbc.update("UPDATE inventory_alerts SET last_notified_at=?,dismissed_at=NULL,updated_at=? WHERE id=?",
            Timestamp.from(now), Timestamp.from(now), row.get("id"));
        return jdbc.query("""
            SELECT a.id,a.alert_type,p.id,p.name,i.variant_name,i.sku,i.unit,b.id,b.name,
              COALESCE(stock.quantity,0),
              bi.minimum_quantity,a.last_notified_at
            FROM inventory_alerts a
            JOIN inventory_branch_items bi ON bi.company_id=a.company_id AND bi.branch_id=a.branch_id AND bi.item_id=a.item_id
            JOIN inventory_branches b ON b.company_id=a.company_id AND b.id=a.branch_id AND b.active=TRUE
            JOIN catalog_items i ON i.company_id=a.company_id AND i.id=a.item_id
            JOIN catalog_products p ON p.company_id=i.company_id AND p.id=i.product_id
            LEFT JOIN inventory_balances stock ON stock.company_id=b.company_id AND stock.location_id=b.location_id AND stock.item_id=i.id
            WHERE a.company_id=? AND a.recipient_id=? AND a.active=TRUE
              AND (a.dismissed_at IS NULL OR a.last_notified_at>=?)
            ORDER BY CASE a.alert_type WHEN 'EXHAUSTED' THEN 0 ELSE 1 END,b.name,p.name,i.variant_name
            """, (rs,row)->new Alert(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),
                rs.getString(7),rs.getString(8),rs.getString(9),ZERO,rs.getBigDecimal(11),instant(rs.getTimestamp(12))), company, actor.accountId(),
            Timestamp.from(now.minusSeconds(24 * 60 * 60L)));
    }

    @Transactional
    public void dismissAlert(StocklyPrincipal actor, String alertId) {
        String company = company(actor);
        int count = jdbc.update("UPDATE inventory_alerts SET dismissed_at=?,updated_at=? WHERE company_id=? AND recipient_id=? AND id=? AND active=TRUE",
            Timestamp.from(Instant.now()), Timestamp.from(Instant.now()), company, actor.accountId(), alertId);
        if (count == 0) throw missing();
    }

    private void syncItemAlerts(String company, String branchId, String itemId, StockItem item) {
        Instant now = Instant.now();
        boolean wasMinimum = Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM inventory_alerts WHERE company_id=? AND branch_id=? AND item_id=? AND alert_type='MINIMUM' AND active=TRUE)
            """,Boolean.class,company,branchId,itemId));
        boolean minimum = !item.restockPaused() && item.minimumQuantity() != null && (item.available().compareTo(item.minimumQuantity()) <= 0 ||
            wasMinimum && item.available().compareTo(item.minimumQuantity().add(item.recoveryMargin())) < 0);
        boolean exhausted = !item.restockPaused() && ("ON_HAND".equals(item.exhaustedBasis()) ? item.onHand() : item.available()).signum() <= 0;
        syncAlertType(company, branchId, itemId, "MINIMUM", minimum, now);
        syncAlertType(company, branchId, itemId, "EXHAUSTED", exhausted, now);
        List<String> recipients = jdbc.query("""
            SELECT DISTINCT u.id FROM users u
            JOIN inventory_branch_users a ON a.company_id=u.company_id AND a.user_id=u.id AND a.branch_id=?
            LEFT JOIN role_module_permissions inv ON inv.role_id=u.role_id AND inv.module_key='INVENTORY'
            LEFT JOIN role_module_permissions cat ON cat.role_id=u.role_id AND cat.module_key='CATALOG'
            WHERE u.company_id=? AND u.active=TRUE AND
              (u.role='COMPANY_ADMIN' OR inv.access_level IN ('READ','OPERATE','ADMIN') OR cat.access_level IN ('OPERATE','ADMIN'))
            """, (rs,row)->rs.getString(1), branchId, company);
        for (String type : List.of("MINIMUM", "EXHAUSTED")) {
            boolean active = type.equals("MINIMUM") ? minimum : exhausted;
            if (!active) { jdbc.update("UPDATE inventory_alerts SET active=FALSE,dismissed_at=NULL,updated_at=? WHERE company_id=? AND branch_id=? AND item_id=? AND alert_type=?",
                Timestamp.from(now), company, branchId, itemId, type); continue; }
            for (String recipient : recipients) jdbc.update("""
                INSERT INTO inventory_alerts(id,company_id,recipient_id,branch_id,item_id,alert_type,active,created_at,updated_at)
                VALUES(?,?,?,?,?,?,TRUE,?,?) ON DUPLICATE KEY UPDATE active=TRUE,updated_at=VALUES(updated_at)
                """, UUID.randomUUID().toString(), company, recipient, branchId, itemId, type, Timestamp.from(now), Timestamp.from(now));
            if (!recipients.isEmpty()) jdbc.update("""
                UPDATE inventory_alerts SET active=FALSE,dismissed_at=NULL,updated_at=?
                WHERE company_id=? AND branch_id=? AND item_id=? AND alert_type=? AND recipient_id NOT IN ("""
                    + String.join(",", Collections.nCopies(recipients.size(), "?")) + ")",
                combine(new Object[]{Timestamp.from(now), company, branchId, itemId, type}, recipients.toArray()));
        }
    }

    private void syncAlertType(String company, String branchId, String itemId, String type, boolean active, Instant now) {
        if (!active) jdbc.update("UPDATE inventory_alerts SET active=FALSE,dismissed_at=NULL,updated_at=? WHERE company_id=? AND branch_id=? AND item_id=? AND alert_type=?",
            Timestamp.from(now), company, branchId, itemId, type);
    }

    private void refreshAlerts(String company, String branchId) {
        for (StockItem item : loadAssignedItems(company, Set.of(branchId))) syncItemAlerts(company, branchId, item.itemId(), withState(item));
    }

    private List<StockItem> loadAssignedItems(String company, Set<String> branches) {
        if (branches.isEmpty()) return List.of();
        return jdbc.query("""
            SELECT p.id,p.name,p.category,i.id,i.variant_name,i.sku,i.barcode,i.unit,b.id,b.name,
              bi.minimum_quantity,bi.low_threshold,bi.recovery_margin,bi.exhausted_basis,COALESCE(stock.quantity,0),bi.target_quantity,bi.restock_paused
            FROM inventory_branch_items bi JOIN inventory_branches b ON b.company_id=bi.company_id AND b.id=bi.branch_id AND b.active=TRUE
            JOIN catalog_items i ON i.company_id=bi.company_id AND i.id=bi.item_id AND i.active=TRUE
            JOIN catalog_products p ON p.company_id=i.company_id AND p.id=i.product_id AND p.active=TRUE
            LEFT JOIN inventory_balances stock ON stock.company_id=b.company_id AND stock.location_id=b.location_id AND stock.item_id=i.id
            WHERE bi.company_id=? AND b.id IN (""" + String.join(",", Collections.nCopies(branches.size(), "?")) + ")",
            (rs,row)->stockItem(rs), combine(new Object[]{company},branches.toArray()));
    }

    private StockItem loadOne(String company, String branchId, String itemId) {
        return jdbc.query("""
            SELECT p.id,p.name,p.category,i.id,i.variant_name,i.sku,i.barcode,i.unit,b.id,b.name,
              bi.minimum_quantity,bi.low_threshold,bi.recovery_margin,bi.exhausted_basis,COALESCE(stock.quantity,0),bi.target_quantity,bi.restock_paused
            FROM inventory_branch_items bi JOIN inventory_branches b ON b.company_id=bi.company_id AND b.id=bi.branch_id
            JOIN catalog_items i ON i.company_id=bi.company_id AND i.id=bi.item_id JOIN catalog_products p ON p.company_id=i.company_id AND p.id=i.product_id
            LEFT JOIN inventory_balances stock ON stock.company_id=b.company_id AND stock.location_id=b.location_id AND stock.item_id=i.id
            WHERE bi.company_id=? AND bi.branch_id=? AND bi.item_id=?
            """, (rs,row)->stockItem(rs), company, branchId, itemId).stream().findFirst().orElseThrow(InventoryService::missing);
    }

    private StockItem stockItem(java.sql.ResultSet rs) throws java.sql.SQLException {
        BigDecimal onHand = rs.getBigDecimal(15);
        return new StockItem(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7),rs.getString(8),
            rs.getString(9),rs.getString(10),onHand,ZERO,onHand,rs.getBigDecimal(11),rs.getBigDecimal(12),rs.getBigDecimal(13),rs.getString(14),"",
            rs.getBigDecimal(16),rs.getBoolean(17));
    }

    private StockItem withState(StockItem item) {
        String state;
        BigDecimal exhaustedValue = "ON_HAND".equals(item.exhaustedBasis()) ? item.onHand() : item.available();
        if (exhaustedValue.signum() <= 0) state = "EXHAUSTED";
        else if (item.minimumQuantity() != null && item.available().compareTo(item.minimumQuantity()) <= 0) state = "MINIMUM";
        else if (item.minimumQuantity() != null && item.available().compareTo(item.minimumQuantity().add(item.lowThreshold())) <= 0) state = "LOW";
        else state = "AVAILABLE";
        return new StockItem(item.productId(),item.productName(),item.category(),item.itemId(),item.variantName(),item.sku(),item.barcode(),item.unit(),
            item.branchId(),item.branchName(),item.onHand(),item.reserved(),item.available(),item.minimumQuantity(),item.lowThreshold(),item.recoveryMargin(),item.exhaustedBasis(),state,
            item.targetQuantity(),item.restockPaused());
    }

    private boolean matchesStatus(StockItem item, String filter) {
        if (filter == null || filter.isBlank() || "ALL".equalsIgnoreCase(filter)) return true;
        return switch (filter.toUpperCase(Locale.ROOT)) {
            case "AVAILABLE" -> item.available().signum() > 0;
            case "LOW" -> "LOW".equals(item.state());
            case "MINIMUM" -> "MINIMUM".equals(item.state());
            case "EXHAUSTED" -> "EXHAUSTED".equals(item.state());
            default -> throw invalid("El filtro de existencias no es válido.");
        };
    }

    private Comparator<StockItem> sortComparator(String sort) {
        Comparator<StockItem> name = Comparator.comparing(StockItem::productName,String.CASE_INSENSITIVE_ORDER)
            .thenComparing(i->Objects.toString(i.variantName(),""),String.CASE_INSENSITIVE_ORDER).thenComparing(StockItem::branchName,String.CASE_INSENSITIVE_ORDER);
        if ("AVAILABLE_DESC".equalsIgnoreCase(sort)) return Comparator.comparing(StockItem::available).reversed().thenComparing(name);
        if ("AVAILABLE_ASC".equalsIgnoreCase(sort)) return Comparator.comparing(StockItem::available).thenComparing(name);
        if ("ALERTS".equalsIgnoreCase(sort)) return Comparator.comparingInt((StockItem i)->switch(i.state()){case "EXHAUSTED"->0;case "MINIMUM"->1;case "LOW"->2;default->3;}).thenComparing(name);
        if (sort != null && !sort.isBlank() && !"NAME".equalsIgnoreCase(sort)) throw invalid("El orden de existencias no es válido.");
        return name;
    }

    private Set<String> accessibleBranchIds(StocklyPrincipal actor, String company) {
        if (admin(actor)) return new LinkedHashSet<>(jdbc.queryForList("SELECT id FROM inventory_branches WHERE company_id=? AND active=TRUE ORDER BY name,id",String.class,company));
        return new LinkedHashSet<>(jdbc.queryForList("""
            SELECT b.id FROM inventory_branches b JOIN inventory_branch_users a ON a.company_id=b.company_id AND a.branch_id=b.id
            WHERE b.company_id=? AND b.active=TRUE AND a.user_id=? ORDER BY b.name,b.id
            """,String.class,company,actor.accountId()));
    }

    private Set<String> assignedBranchIds(StocklyPrincipal actor, String company) {
        return new LinkedHashSet<>(jdbc.queryForList("""
            SELECT b.id FROM inventory_branches b JOIN inventory_branch_users a ON a.company_id=b.company_id AND a.branch_id=b.id
            WHERE b.company_id=? AND b.active=TRUE AND a.user_id=? ORDER BY b.id
            """,String.class,company,actor.accountId()));
    }

    private void requireBranchAccess(StocklyPrincipal actor, String company, String branchId, boolean includeInactive) {
        if (admin(actor)) {
            Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM inventory_branches WHERE company_id=? AND id=? AND (? OR active=TRUE)",Integer.class,company,branchId,includeInactive);
            if(count==null||count!=1) throw missing();
            return;
        }
        Integer count=jdbc.queryForObject("""
            SELECT COUNT(*) FROM inventory_branch_users a JOIN inventory_branches b ON b.company_id=a.company_id AND b.id=a.branch_id
            WHERE a.company_id=? AND a.branch_id=? AND a.user_id=? AND (? OR b.active=TRUE)
            """,Integer.class,company,branchId,actor.accountId(),includeInactive);
        if(count==null||count!=1) throw missing();
    }

    private void requireBranchExists(String company,String id) {
        Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM inventory_branches WHERE company_id=? AND id=?",Integer.class,company,id);
        if(count==null||count!=1) throw missing();
    }
    private Branch getBranch(String company,String id) {
        return jdbc.query("SELECT id,name,code,address,phone,contact_email,manager_name,active FROM inventory_branches WHERE company_id=? AND id=?",
            (rs,row)->branch(rs),company,id).stream().findFirst().orElseThrow(InventoryService::missing);
    }
    private static Branch branch(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Branch(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7),rs.getBoolean(8));
    }

    private BranchInput validBranch(BranchInput input) {
        if (input==null) throw invalid("Completa los datos de la sucursal.");
        String name=required(input.name(),120,"El nombre de la sucursal es obligatorio.");
        String code=required(input.code(),40,"El código de la sucursal es obligatorio.").toUpperCase(Locale.ROOT);
        if(!code.matches("[A-Z0-9][A-Z0-9._-]*")) throw invalid("El código admite letras sin tilde, números, punto, guion y guion bajo.");
        String phone=optional(input.phone(),30),email=optional(input.contactEmail(),254);
        if(!phone.isEmpty()&&!phone.matches("[+0-9() -]+")) throw invalid("El teléfono contiene caracteres no admitidos.");
        if(!email.isEmpty()&&!email.matches("(?i)^[A-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?(?:\\.[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?)+$"))
            throw invalid("El correo de contacto no tiene un formato válido.");
        return new BranchInput(name,code,optional(input.address(),500),phone,email,optional(input.managerName(),150));
    }
    private ThresholdInput validThresholds(ThresholdInput input,boolean defaults) {
        if(input==null) throw invalid("Revisa los umbrales de existencias.");
        BigDecimal low=input.lowThreshold()==null&&defaults?DEFAULT_LOW:input.lowThreshold();
        BigDecimal recovery=input.recoveryMargin()==null&&defaults?DEFAULT_RECOVERY:input.recoveryMargin();
        String basis=input.exhaustedBasis()==null&&defaults?"AVAILABLE":input.exhaustedBasis();
        decimal(input.minimumQuantity(),true,"El mínimo debe ser una cantidad igual o mayor que cero.");
        decimal(low,false,"El umbral de stock bajo debe ser igual o mayor que cero.");
        decimal(recovery,false,"El margen de recuperación debe ser al menos 1.00.");
        if(recovery.compareTo(BigDecimal.ONE)<0) throw invalid("El margen de recuperación debe ser al menos 1.00.");
        if(!Set.of("AVAILABLE","ON_HAND").contains(basis)) throw invalid("Selecciona un criterio de agotado válido.");
        return new ThresholdInput(input.minimumQuantity(),low,recovery,basis);
    }
    /** STK-016: nivel deseado opcional, mayor que el mínimo (o que 0 sin mínimo), con hasta 2 decimales. */
    private void validTarget(BigDecimal target,BigDecimal minimum) {
        if(target==null) return;
        if(target.scale()>2||target.signum()<=0||target.compareTo(new BigDecimal("9999999999999.99"))>0)
            throw invalidField("targetQuantity","El nivel deseado debe ser una cantidad mayor que cero, con hasta 2 decimales.");
        if(minimum!=null&&target.compareTo(minimum)<=0)
            throw invalidField("targetQuantity","El nivel deseado debe ser mayor que el mínimo ("+minimum.stripTrailingZeros().toPlainString()+").");
    }
    private void decimal(BigDecimal value,boolean nullable,String message) {
        if(value==null&&nullable)return;
        if(value==null||value.signum()<0||value.scale()>2||value.compareTo(new BigDecimal("9999999999999.99"))>0) throw invalid(message);
    }
    private static String required(String input,int max,String message) {
        String value=optional(input,max); if(value.isEmpty()) throw invalid(message); return value;
    }
    private static String optional(String input,int max) {
        String value=input==null?"":input.strip(); if(value.length()>max) throw invalid("Un campo supera el máximo de "+max+" caracteres."); return value;
    }
    private static String company(StocklyPrincipal actor) {
        if(actor.companyId()==null||actor.mustChangePassword()) throw forbidden("La operación requiere una cuenta de empresa activa.");
        return actor.companyId();
    }
    private static String requireAdmin(StocklyPrincipal actor) {
        String company=company(actor); if(!admin(actor)) throw forbidden("Solo el administrador de empresa puede gestionar sucursales."); return company;
    }
    private static boolean admin(StocklyPrincipal actor){return "COMPANY_ADMIN".equals(actor.role());}
    private static Object[] combine(Object[] first,Object[] second){Object[] result=Arrays.copyOf(first,first.length+second.length);System.arraycopy(second,0,result,first.length,second.length);return result;}
    private static Instant instant(Timestamp value){return value==null?null:value.toInstant();}
    private static IdentityFailure missing(){return new IdentityFailure(404,"INVENTORY_NOT_FOUND","No se encontró la sucursal, el producto o la alerta.");}
    private static IdentityFailure invalid(String message){return new IdentityFailure(400,"INVENTORY_INVALID",message);}
    private static IdentityFailure invalidField(String field,String message){return new IdentityFailure(400,"INVENTORY_INVALID",message,Map.of(field,message));}
    private static IdentityFailure conflict(String message){return new IdentityFailure(409,"INVENTORY_CONFLICT",message);}
    private static IdentityFailure forbidden(String message){return new IdentityFailure(403,"FORBIDDEN",message);}
}
