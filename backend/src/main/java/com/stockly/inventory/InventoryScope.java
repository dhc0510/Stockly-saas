package com.stockly.inventory;

import com.stockly.identity.IdentityFailure;
import com.stockly.identity.StocklyPrincipal;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class InventoryScope {
    private final JdbcTemplate jdbc;

    InventoryScope(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    List<Option> options(StocklyPrincipal actor) {
        return jdbc.query("""
            SELECT b.id,b.name,b.location_id FROM inventory_branches b
            WHERE b.company_id=? AND b.active AND (? OR EXISTS (
                SELECT 1 FROM inventory_branch_users u
                WHERE u.company_id=b.company_id AND u.branch_id=b.id AND u.user_id=?
            )) ORDER BY b.name,b.id
            """, (rs, row) -> new Option(rs.getString(1), rs.getString(2), rs.getString(3)),
            actor.companyId(), "COMPANY_ADMIN".equals(actor.role()), actor.accountId());
    }

    String location(StocklyPrincipal actor, String branchId) {
        var choices = options(actor);
        if (branchId == null && choices.size() == 1) branchId = choices.getFirst().id();
        if (branchId == null && choices.isEmpty() && isLegacy(actor.companyId())) return InventoryLedger.PRINCIPAL;
        if (branchId == null)
            throw new IdentityFailure(400, "BRANCH_REQUIRED", choices.isEmpty()
                ? "No tienes acceso a una sucursal activa." : "Selecciona una sucursal.");
        for (Option choice : choices) {
            if (!choice.id().equals(branchId)) continue;
            if (choice.location() == null)
                throw new IdentityFailure(409, "BRANCH_UNCONFIGURED", "El administrador debe configurar el inventario de esta sucursal.");
            return choice.location();
        }
        throw new IdentityFailure(404, "BRANCH_NOT_FOUND", "No se encontró la sucursal.");
    }

    boolean isLegacy(String company) {
        return !Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM inventory_branches WHERE company_id=?)
            """, Boolean.class, company));
    }

    void requireAssigned(String company, String location, String item) {
        if (InventoryLedger.PRINCIPAL.equals(location) && isLegacy(company)) return;
        if (!Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM inventory_branch_items bi JOIN inventory_branches b
              ON b.company_id=bi.company_id AND b.id=bi.branch_id
              WHERE b.company_id=? AND b.location_id=? AND bi.item_id=?)
            """, Boolean.class, company, location, item)))
            throw new IdentityFailure(400, "ITEM_NOT_ASSIGNED", "Un artículo no está asignado a la sucursal seleccionada.");
    }

    static final String VISIBLE_MOVEMENT = """
        (? OR EXISTS (
            SELECT 1 FROM inventory_branches b JOIN inventory_branch_users u
              ON u.company_id=b.company_id AND u.branch_id=b.id
            WHERE b.company_id=m.company_id AND b.location_id=m.location_id AND b.active AND u.user_id=?
        ) OR (m.location_id='principal' AND NOT EXISTS (
            SELECT 1 FROM inventory_branches b WHERE b.company_id=m.company_id
        )))
        """;

    static boolean administrator(StocklyPrincipal actor) { return "COMPANY_ADMIN".equals(actor.role()); }

    record Option(String id, String name, String location) {}
    record Choices(List<Option> branches, boolean legacy) {}

    Choices choices(StocklyPrincipal actor) {
        return new Choices(options(actor), isLegacy(actor.companyId()));
    }
}
