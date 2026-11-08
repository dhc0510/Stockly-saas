package com.stockly.billing;

import com.stockly.identity.StocklyPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/billing/settings")
@PreAuthorize("hasRole('COMPANY_ADMIN')")
public class BillingSettingsController {
    private final BillingSettingsService settings;

    BillingSettingsController(BillingSettingsService settings) { this.settings = settings; }

    @GetMapping
    BillingSettingsService.Settings get(@AuthenticationPrincipal StocklyPrincipal actor) { return settings.get(actor); }

    @PutMapping("/credentials")
    BillingSettingsService.Settings credentials(@AuthenticationPrincipal StocklyPrincipal actor,
                                                @RequestBody BillingSettingsService.CredentialsInput input) {
        return settings.saveCredentials(actor, input);
    }

    @PostMapping("/test")
    BillingSettingsService.Settings test(@AuthenticationPrincipal StocklyPrincipal actor) { return settings.testConnection(actor); }

    @PutMapping("/issuer")
    BillingSettingsService.Settings issuer(@AuthenticationPrincipal StocklyPrincipal actor, @RequestBody BillingSettingsService.IssuerInput input) {
        return settings.saveIssuer(actor, input);
    }

    @PutMapping("/branches/{branchId}/sequences/{docType}")
    BillingSettingsService.Settings sequence(@AuthenticationPrincipal StocklyPrincipal actor, @PathVariable String branchId,
                                             @PathVariable String docType, @RequestBody StartingNumber input) {
        return settings.setStartingNumber(actor, branchId, docType, input == null ? -1 : input.lastUsed());
    }

    @PostMapping("/production")
    BillingSettingsService.Settings production(@AuthenticationPrincipal StocklyPrincipal actor) { return settings.activateProduction(actor); }

    record StartingNumber(long lastUsed) {}
}
