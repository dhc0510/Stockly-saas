package com.stockly.subscriptions;

import com.stockly.billing.SaasBillingService;
import com.stockly.identity.IdentityFailure;
import com.stockly.identity.StocklyPrincipal;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** STK-018 · «Mi suscripción» y franjas del comercio. Facturas y pagos solo para el administrador de la empresa. */
@RestController
@RequestMapping("/api/subscription")
public class SubscriptionController {
    private final SubscriptionService subscriptions;
    private final SaasBillingService billing;

    SubscriptionController(SubscriptionService subscriptions, SaasBillingService billing) { this.subscriptions = subscriptions; this.billing = billing; }

    public record View(SubscriptionService.Mine subscription, List<SaasBillingService.Document> documents) {}

    @GetMapping
    ResponseEntity<View> mine(@AuthenticationPrincipal StocklyPrincipal actor) {
        var mine = subscriptions.mine(actor);
        if (mine == null) return ResponseEntity.noContent().build();
        boolean admin = "COMPANY_ADMIN".equals(actor.role());
        return ResponseEntity.ok(new View(mine, admin ? billing.documents(actor.companyId()).stream()
            .filter(d -> "ACCEPTED".equals(d.status())).toList() : List.of()));
    }

    @GetMapping("/documents/{documentId}/{kind}")
    ResponseEntity<byte[]> file(@AuthenticationPrincipal StocklyPrincipal actor, @PathVariable String documentId, @PathVariable String kind) {
        if (actor == null || !"COMPANY_ADMIN".equals(actor.role()))
            throw new IdentityFailure(403, "FORBIDDEN", "Solo el administrador de la empresa descarga las facturas.");
        return SaasController.download(billing.file(documentId, actor.companyId(), kind));
    }
}
