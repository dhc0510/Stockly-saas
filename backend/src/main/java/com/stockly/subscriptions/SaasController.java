package com.stockly.subscriptions;

import com.stockly.billing.SaasBillingService;
import com.stockly.identity.StocklyPrincipal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** STK-018 · API del administrador de Stockly: panel, planes, configuración, facturación de Stockly y suscripciones. */
@RestController
@RequestMapping("/api/saas")
@PreAuthorize("hasRole('SAAS_ADMIN')")
public class SaasController {
    private final SubscriptionService subscriptions;
    private final SaasBillingService billing;

    SaasController(SubscriptionService subscriptions, SaasBillingService billing) { this.subscriptions = subscriptions; this.billing = billing; }

    public record PanelView(SubscriptionService.Panel panel, SaasBillingService.Counters invoices) {}

    @GetMapping("/panel")
    PanelView panel() {
        LocalDate first = subscriptions.today().withDayOfMonth(1);
        return new PanelView(subscriptions.panel(), billing.counters(first.atStartOfDay(SubscriptionRules.COSTA_RICA).toInstant(),
            first.plusMonths(1).atStartOfDay(SubscriptionRules.COSTA_RICA).toInstant()));
    }

    @GetMapping("/settings") SubscriptionService.Settings settings() { return subscriptions.settings(); }
    @PutMapping("/settings") SubscriptionService.Settings saveSettings(@AuthenticationPrincipal StocklyPrincipal actor, @RequestBody SubscriptionService.Settings input) {
        return subscriptions.saveSettings(actor, input);
    }

    @GetMapping("/plans") List<SubscriptionService.Plan> plans() { return subscriptions.plans(); }
    @PostMapping("/plans") SubscriptionService.Plan createPlan(@AuthenticationPrincipal StocklyPrincipal actor, @RequestBody SubscriptionService.PlanInput input) {
        return subscriptions.savePlan(actor, null, input);
    }
    @PutMapping("/plans/{id}") SubscriptionService.Plan updatePlan(@AuthenticationPrincipal StocklyPrincipal actor, @PathVariable String id,
                                                                  @RequestBody SubscriptionService.PlanInput input) {
        return subscriptions.savePlan(actor, id, input);
    }

    @GetMapping("/billing") SaasBillingService.Settings billing() { return billing.settings(); }
    @PutMapping("/billing/issuer") SaasBillingService.Settings issuer(@AuthenticationPrincipal StocklyPrincipal actor, @RequestBody SaasBillingService.IssuerInput input) {
        var result = billing.saveIssuer(input); subscriptions.audit(actor, "SAAS_BILLING_UPDATED", null, null, "Datos del emisor"); return result;
    }
    @PutMapping("/billing/credentials") SaasBillingService.Settings credentials(@AuthenticationPrincipal StocklyPrincipal actor,
                                                                               @RequestBody SaasBillingService.CredentialsInput input) {
        var result = billing.saveCredentials(input); subscriptions.audit(actor, "SAAS_BILLING_UPDATED", null, null, "Credenciales de Hacienda"); return result;
    }
    @PostMapping("/billing/test") SaasBillingService.Settings test() { return billing.testConnection(); }
    @PutMapping("/billing/environment") SaasBillingService.Settings environment(@AuthenticationPrincipal StocklyPrincipal actor, @RequestBody Map<String, String> input) {
        var result = billing.environment(input == null ? null : input.get("environment"));
        subscriptions.audit(actor, "SAAS_BILLING_UPDATED", null, null, "Ambiente " + result.environment()); return result;
    }

    @GetMapping("/subscriptions") List<SubscriptionService.Summary> summaries(@RequestParam(defaultValue = "") String companyIds) {
        var ids = Arrays.stream(companyIds.split(",")).map(String::strip).filter(s -> !s.isEmpty()).limit(200).toList();
        return subscriptions.summaries(ids);
    }

    public record Detail(SubscriptionService.Subscription subscription, List<SaasBillingService.Document> documents) {}

    @GetMapping("/companies/{id}/subscription")
    Detail subscription(@PathVariable String id) { return new Detail(subscriptions.subscription(id), billing.documents(id)); }

    @PostMapping("/companies/{id}/payments")
    Detail pay(@AuthenticationPrincipal StocklyPrincipal actor, @PathVariable String id, @RequestBody SubscriptionService.PaymentInput input) {
        return new Detail(subscriptions.registerPayment(actor, id, input), billing.documents(id));
    }

    @PostMapping("/companies/{id}/payments/{paymentId}/void")
    Detail voidPayment(@AuthenticationPrincipal StocklyPrincipal actor, @PathVariable String id, @PathVariable String paymentId,
                       @RequestBody Map<String, String> input) {
        return new Detail(subscriptions.voidPayment(actor, id, paymentId, input == null ? null : input.get("reason")), billing.documents(id));
    }

    @PostMapping("/companies/{id}/cancel")
    Detail cancel(@AuthenticationPrincipal StocklyPrincipal actor, @PathVariable String id, @RequestBody Map<String, String> input) {
        return new Detail(subscriptions.cancel(actor, id, input == null ? null : input.get("reason")), billing.documents(id));
    }

    @GetMapping("/documents/{documentId}/{kind}")
    ResponseEntity<byte[]> file(@PathVariable String documentId, @PathVariable String kind) { return download(billing.file(documentId, null, kind)); }

    @PostMapping("/documents/{documentId}/retry")
    ResponseEntity<Void> retry(@AuthenticationPrincipal StocklyPrincipal actor, @PathVariable String documentId) {
        billing.retry(documentId); subscriptions.audit(actor, "SAAS_INVOICE_RETRIED", null, documentId, null);
        return ResponseEntity.noContent().build();
    }

    static ResponseEntity<byte[]> download(SaasBillingService.File file) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.name()).build().toString())
            .header(HttpHeaders.CONTENT_TYPE, file.contentType()).body(file.content());
    }
}
