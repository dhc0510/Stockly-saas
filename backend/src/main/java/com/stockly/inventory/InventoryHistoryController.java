package com.stockly.inventory;

import com.stockly.identity.StocklyPrincipal;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory/history")
@PreAuthorize("hasAnyAuthority('ROLE_COMPANY_ADMIN','MODULE_INVENTORY_HISTORY_READ','MODULE_INVENTORY_HISTORY_OPERATE','MODULE_INVENTORY_HISTORY_ADMIN')")
public class InventoryHistoryController {
    private final InventoryHistoryService service;

    InventoryHistoryController(InventoryHistoryService service) { this.service = service; }

    @GetMapping
    InventoryHistoryService.RowPage list(@AuthenticationPrincipal StocklyPrincipal actor,
                                         @RequestParam(defaultValue = "") String search, @RequestParam(defaultValue = "") String itemId,
                                         @RequestParam(defaultValue = "") String type, @RequestParam(defaultValue = "") String actorId,
                                         @RequestParam(defaultValue = "") String from, @RequestParam(defaultValue = "") String to,
                                         @RequestParam(defaultValue = "0") int page) {
        return service.list(actor, search, itemId, type, actorId, from, to, page);
    }

    @GetMapping("/items")
    List<InventoryHistoryService.ItemOption> items(@AuthenticationPrincipal StocklyPrincipal actor, @RequestParam String search) {
        return service.items(actor, search);
    }

    @GetMapping("/items/{id}")
    InventoryHistoryService.ItemSummary item(@AuthenticationPrincipal StocklyPrincipal actor, @PathVariable String id) {
        return service.item(actor, id);
    }

    @GetMapping("/actors")
    List<InventoryHistoryService.ActorOption> actors(@AuthenticationPrincipal StocklyPrincipal actor) {
        return service.actors(actor);
    }

    @GetMapping("/movements/{id}")
    InventoryLedger.Document movement(@AuthenticationPrincipal StocklyPrincipal actor, @PathVariable String id) {
        return service.movement(actor, id);
    }
}
