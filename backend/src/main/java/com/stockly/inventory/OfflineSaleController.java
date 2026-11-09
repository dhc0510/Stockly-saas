package com.stockly.inventory;

import com.stockly.identity.StocklyPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sales/offline")
public class OfflineSaleController {
    private static final String REVIEW = "hasAnyAuthority('ROLE_COMPANY_ADMIN','MODULE_SALES_ADMIN')";
    private final OfflineSaleService service;

    OfflineSaleController(OfflineSaleService service) { this.service = service; }

    @PostMapping("/snapshot")
    @PreAuthorize(SaleController.WRITE)
    OfflineSaleService.Snapshot prepare(@AuthenticationPrincipal StocklyPrincipal actor, @RequestParam String branchId) {
        return service.prepare(actor, branchId);
    }

    @PostMapping("/queue")
    @PreAuthorize(SaleController.WRITE)
    OfflineSaleService.Entry sync(@AuthenticationPrincipal StocklyPrincipal actor, @RequestBody OfflineSaleService.Input input) {
        return service.sync(actor, input);
    }

    @GetMapping("/queue/{id}")
    @PreAuthorize(SaleController.READ)
    OfflineSaleService.Entry status(@AuthenticationPrincipal StocklyPrincipal actor, @PathVariable String id) {
        return service.status(actor, id);
    }

    @GetMapping("/review")
    @PreAuthorize(REVIEW)
    java.util.List<OfflineSaleService.Entry> review(@AuthenticationPrincipal StocklyPrincipal actor) {
        return service.review(actor);
    }

    @PostMapping("/review/{id}/retry")
    @PreAuthorize(REVIEW)
    OfflineSaleService.Entry retry(@AuthenticationPrincipal StocklyPrincipal actor, @PathVariable String id) {
        return service.retry(actor, id);
    }

    @PostMapping("/review/{id}/close")
    @PreAuthorize(REVIEW)
    OfflineSaleService.Entry close(@AuthenticationPrincipal StocklyPrincipal actor, @PathVariable String id,
                                   @RequestBody OfflineSaleService.Closure closure) {
        return service.close(actor, id, closure);
    }
}
