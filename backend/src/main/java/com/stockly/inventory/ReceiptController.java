package com.stockly.inventory;

import com.stockly.identity.StocklyPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory/receipts")
@PreAuthorize(ReceiptController.READ)
public class ReceiptController {
    static final String READ = "hasAnyAuthority('ROLE_COMPANY_ADMIN','MODULE_INVENTORY_RECEIPTS_READ','MODULE_INVENTORY_RECEIPTS_OPERATE','MODULE_INVENTORY_RECEIPTS_ADMIN')";
    static final String WRITE = "hasAnyAuthority('ROLE_COMPANY_ADMIN','MODULE_INVENTORY_RECEIPTS_OPERATE','MODULE_INVENTORY_RECEIPTS_ADMIN')";
    private final ReceiptService service;

    ReceiptController(ReceiptService service) { this.service = service; }

    @GetMapping
    InventoryLedger.Page list(@AuthenticationPrincipal StocklyPrincipal actor, @RequestParam(defaultValue = "") String search,
                              @RequestParam(defaultValue = "") String status, @RequestParam(defaultValue = "0") int page) {
        return service.list(actor, search, status, page);
    }

    @GetMapping("/items")
    java.util.List<InventoryLedger.Article> items(@AuthenticationPrincipal StocklyPrincipal actor,
                                                 @RequestParam String search, @RequestParam(defaultValue = "false") boolean barcode) {
        return service.items(actor, search, barcode);
    }

    @GetMapping("/{id}")
    InventoryLedger.Document get(@AuthenticationPrincipal StocklyPrincipal actor, @PathVariable String id) {
        return service.get(actor, id);
    }

    @PostMapping
    @PreAuthorize(WRITE)
    InventoryLedger.Document confirm(@AuthenticationPrincipal StocklyPrincipal actor, @RequestBody ReceiptService.ReceiptInput input) {
        return service.confirm(actor, input);
    }

    @PostMapping("/{id}/void")
    @PreAuthorize("hasRole('COMPANY_ADMIN') or (hasAuthority('PERM_VOID_INVENTORY_RECEIPTS') and " + READ + ")")
    InventoryLedger.Document reverse(@AuthenticationPrincipal StocklyPrincipal actor, @PathVariable String id,
                                     @RequestBody VoidInput input) {
        return service.reverse(actor, id, input.reason());
    }

    record VoidInput(String reason) {}
}
