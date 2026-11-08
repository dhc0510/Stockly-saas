package com.stockly.inventory;

import com.stockly.identity.StocklyPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sales/{id}/returns")
@PreAuthorize(SaleController.READ)
public class SaleReturnController {
    private static final String REGISTER = "hasRole('COMPANY_ADMIN') or (hasAuthority('PERM_REGISTER_SALE_RETURNS') and " + SaleController.READ + ")";
    private final SaleReturnService returns;

    SaleReturnController(SaleReturnService returns) { this.returns = returns; }

    @GetMapping
    SaleReturnService.Context context(@AuthenticationPrincipal StocklyPrincipal actor, @PathVariable String id) {
        return returns.context(actor, id);
    }

    @PostMapping("/preview")
    @PreAuthorize(REGISTER)
    SaleReturnService.Quote preview(@AuthenticationPrincipal StocklyPrincipal actor, @PathVariable String id,
                                    @RequestBody SaleReturnService.Selection input) {
        return returns.preview(actor, id, input);
    }

    @PostMapping
    @PreAuthorize(REGISTER)
    SaleReturnService.Receipt confirm(@AuthenticationPrincipal StocklyPrincipal actor, @PathVariable String id,
                                      @RequestBody SaleReturnService.Confirmation input) {
        return returns.confirm(actor, id, input);
    }

    @GetMapping("/{folio}")
    SaleReturnService.Receipt get(@AuthenticationPrincipal StocklyPrincipal actor, @PathVariable String id, @PathVariable String folio) {
        return returns.get(actor, id, folio);
    }
}
