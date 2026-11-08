package com.stockly.inventory;

import com.stockly.identity.StocklyPrincipal;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sales")
@PreAuthorize(SaleController.READ)
public class SaleController {
    static final String READ = "hasAnyAuthority('ROLE_COMPANY_ADMIN','MODULE_SALES_READ','MODULE_SALES_OPERATE','MODULE_SALES_ADMIN')";
    private static final String WRITE = "hasAnyAuthority('ROLE_COMPANY_ADMIN','MODULE_SALES_OPERATE','MODULE_SALES_ADMIN')";
    private final SaleService sales;

    SaleController(SaleService sales) { this.sales = sales; }

    @GetMapping
    SaleService.SalePage list(@AuthenticationPrincipal StocklyPrincipal actor, @RequestParam(defaultValue = "") String search,
                              @RequestParam(defaultValue = "") String status, @RequestParam(defaultValue = "") String branchId,
                              @RequestParam(defaultValue = "0") int page) {
        return sales.list(actor, search, status, branchId, page);
    }

    @GetMapping("/branches")
    InventoryScope.Choices branches(@AuthenticationPrincipal StocklyPrincipal actor) { return sales.branches(actor); }

    @GetMapping("/items")
    List<SaleService.SaleArticle> items(@AuthenticationPrincipal StocklyPrincipal actor, @RequestParam String branchId,
                                        @RequestParam String search) {
        return sales.items(actor, branchId, search);
    }

    @GetMapping("/{id}")
    SaleService.Sale get(@AuthenticationPrincipal StocklyPrincipal actor, @PathVariable String id) { return sales.get(actor, id); }

    @PostMapping
    @PreAuthorize(WRITE)
    SaleService.Sale confirm(@AuthenticationPrincipal StocklyPrincipal actor, @RequestBody SaleService.SaleInput input) {
        return sales.confirm(actor, input);
    }

    @PostMapping("/{id}/void")
    @PreAuthorize("hasRole('COMPANY_ADMIN') or (hasAuthority('PERM_VOID_SALES') and " + READ + ")")
    SaleService.Sale reverse(@AuthenticationPrincipal StocklyPrincipal actor, @PathVariable String id, @RequestBody VoidInput input) {
        return sales.reverse(actor, id, input.reason());
    }

    record VoidInput(String reason) {}
}
