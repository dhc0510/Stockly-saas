package com.stockly.inventory;

import com.stockly.identity.StocklyPrincipal;
import java.util.List;
import java.util.Map;
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
    private final SalePaymentService payments;

    SaleController(SaleService sales, SalePaymentService payments) { this.sales = sales; this.payments = payments; }

    @GetMapping
    SaleService.SalePage list(@AuthenticationPrincipal StocklyPrincipal actor, @RequestParam(defaultValue = "") String search,
                              @RequestParam(defaultValue = "") String status, @RequestParam(defaultValue = "") String branchId,
                              @RequestParam(defaultValue = "") String payment, @RequestParam(defaultValue = "0") int page) {
        return sales.list(actor, search, status, payment, branchId, page);
    }

    @GetMapping("/payment-methods")
    List<SalePaymentService.MethodState> paymentMethods(@AuthenticationPrincipal StocklyPrincipal actor) {
        return payments.methods(actor);
    }

    @PutMapping("/payment-methods")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    List<SalePaymentService.MethodState> updatePaymentMethods(@AuthenticationPrincipal StocklyPrincipal actor,
                                                              @RequestBody Map<String, Boolean> input) {
        return payments.updateMethods(actor, input);
    }

    @GetMapping("/payments/summary")
    SalePaymentService.Summary paymentSummary(@AuthenticationPrincipal StocklyPrincipal actor,
                                              @RequestParam(defaultValue = "") String branchId,
                                              @RequestParam(defaultValue = "") String from, @RequestParam(defaultValue = "") String to) {
        return payments.summary(actor, branchId, from, to);
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
        return sales.reverse(actor, id, input.reason(), input.refunds() == null ? List.of() : input.refunds());
    }

    record VoidInput(String reason, List<SalePaymentService.RefundInput> refunds) {}
}
