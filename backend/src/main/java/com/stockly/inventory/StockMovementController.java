package com.stockly.inventory;

import com.stockly.identity.StocklyPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory/movements")
@PreAuthorize(StockMovementController.READ)
public class StockMovementController {
    static final String READ = "hasAnyAuthority('ROLE_COMPANY_ADMIN','MODULE_INVENTORY_MOVEMENTS_READ','MODULE_INVENTORY_MOVEMENTS_OPERATE','MODULE_INVENTORY_MOVEMENTS_ADMIN')";
    static final String WRITE = "hasAnyAuthority('ROLE_COMPANY_ADMIN','MODULE_INVENTORY_MOVEMENTS_OPERATE','MODULE_INVENTORY_MOVEMENTS_ADMIN')";
    private final StockMovementService service;

    StockMovementController(StockMovementService service) { this.service = service; }

    @GetMapping
    InventoryLedger.Page list(@AuthenticationPrincipal StocklyPrincipal actor, @RequestParam(defaultValue = "") String search,
                              @RequestParam(defaultValue = "") String type, @RequestParam(defaultValue = "") String status,
                              @RequestParam(defaultValue = "0") int page) {
        return service.list(actor, search, type, status, page);
    }

    @GetMapping("/items")
    java.util.List<InventoryLedger.Article> items(@AuthenticationPrincipal StocklyPrincipal actor,
                                                 @RequestParam String search, @RequestParam(defaultValue = "false") boolean barcode,
                                                 @RequestParam(required = false) String branchId) {
        return service.items(actor, search, barcode, branchId);
    }

    @GetMapping("/branches")
    InventoryScope.Choices branches(@AuthenticationPrincipal StocklyPrincipal actor) {
        return service.branches(actor);
    }

    @GetMapping("/{id}")
    InventoryLedger.Document get(@AuthenticationPrincipal StocklyPrincipal actor, @PathVariable String id) {
        return service.get(actor, id);
    }

    @PostMapping("/outflows")
    @PreAuthorize(WRITE)
    InventoryLedger.Document outflow(@AuthenticationPrincipal StocklyPrincipal actor, @RequestBody StockMovementService.OutflowInput input) {
        return service.outflow(actor, input);
    }

    @PostMapping("/adjustments")
    @PreAuthorize("hasRole('COMPANY_ADMIN') or (hasAuthority('PERM_ADJUST_INVENTORY') and " + WRITE + ")")
    InventoryLedger.Document adjustment(@AuthenticationPrincipal StocklyPrincipal actor, @RequestBody StockMovementService.AdjustmentInput input) {
        return service.adjustment(actor, input);
    }

    @PostMapping("/{id}/void")
    @PreAuthorize("hasRole('COMPANY_ADMIN') or (hasAuthority('PERM_VOID_INVENTORY_MOVEMENTS') and " + READ + ")")
    InventoryLedger.Document reverse(@AuthenticationPrincipal StocklyPrincipal actor, @PathVariable String id,
                                     @RequestBody VoidInput input) {
        return service.reverse(actor, id, input.reason());
    }

    record VoidInput(String reason) {}
}
