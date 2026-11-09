package com.stockly.inventory;

import com.stockly.identity.StocklyPrincipal;
import java.util.List;
import java.util.Map;
import com.stockly.billing.FiscalDocumentService;
import com.stockly.billing.FiscalFiles;
import com.stockly.billing.FiscalMailer;
import com.stockly.identity.IdentityFailure;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sales")
@PreAuthorize(SaleController.READ)
public class SaleController {
    static final String READ = "hasAnyAuthority('ROLE_COMPANY_ADMIN','MODULE_SALES_READ','MODULE_SALES_OPERATE','MODULE_SALES_ADMIN')";
    static final String WRITE = "hasAnyAuthority('ROLE_COMPANY_ADMIN','MODULE_SALES_OPERATE','MODULE_SALES_ADMIN')";
    private final SaleService sales;
    private final SalePaymentService payments;
    private final FiscalDocumentService fiscal;
    private final FiscalFiles files;
    private final FiscalMailer mailer;

    SaleController(SaleService sales, SalePaymentService payments, FiscalDocumentService fiscal, FiscalFiles files, FiscalMailer mailer) {
        this.sales = sales; this.payments = payments; this.fiscal = fiscal; this.files = files; this.mailer = mailer;
    }

    @GetMapping
    SaleService.SalePage list(@AuthenticationPrincipal StocklyPrincipal actor, @RequestParam(defaultValue = "") String search,
                              @RequestParam(defaultValue = "") String status, @RequestParam(defaultValue = "") String branchId,
                              @RequestParam(defaultValue = "") String payment, @RequestParam(defaultValue = "") String fiscal,
                              @RequestParam(defaultValue = "") String fiscalType, @RequestParam(defaultValue = "0") int page) {
        return sales.list(actor, search, status, payment, fiscal, fiscalType, branchId, page);
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

    // ---------- STK-013 · Comprobante electrónico de la venta ----------

    @GetMapping("/{id}/fiscal/{documentId}/{file}")
    ResponseEntity<byte[]> fiscalFile(@AuthenticationPrincipal StocklyPrincipal actor, @PathVariable String id,
                                      @PathVariable String documentId, @PathVariable String file) {
        var sale = sales.get(actor, id);
        if (sale.fiscal().stream().noneMatch(doc -> doc.id().equals(documentId)))
            throw new IdentityFailure(404, "FISCAL_NOT_FOUND", "No se encontró el comprobante.");
        var bundle = files.bundle(actor.companyId(), documentId);
        return switch (file) {
            case "pdf" -> download(bundle.pdf(), bundle.key() + ".pdf", MediaType.APPLICATION_PDF);
            case "xml" -> download(bundle.xml(), bundle.key() + ".xml", MediaType.APPLICATION_XML);
            case "response" -> {
                if (bundle.response() == null) throw new IdentityFailure(404, "FISCAL_NO_RESPONSE", "Hacienda aún no envió respuesta.");
                yield download(bundle.response(), bundle.key() + "_respuesta.xml", MediaType.APPLICATION_XML);
            }
            default -> throw new IdentityFailure(404, "FISCAL_NOT_FOUND", "Archivo no disponible.");
        };
    }

    @PostMapping("/{id}/fiscal/{documentId}/resend")
    Map<String, Boolean> resend(@AuthenticationPrincipal StocklyPrincipal actor, @PathVariable String id, @PathVariable String documentId) {
        var sale = sales.get(actor, id);
        var doc = sale.fiscal().stream().filter(d -> d.id().equals(documentId)).findFirst()
            .orElseThrow(() -> new IdentityFailure(404, "FISCAL_NOT_FOUND", "No se encontró el comprobante."));
        if (!"ACCEPTED".equals(doc.status())) throw new IdentityFailure(409, "FISCAL_NOT_ACCEPTED", "Solo se envían comprobantes aceptados.");
        if (sale.sale().customerEmail() == null) throw new IdentityFailure(409, "SALE_NO_EMAIL", "La venta no tiene correo del cliente.");
        if (!mailer.send(actor.companyId(), documentId))
            throw new IdentityFailure(503, "MAIL_FAILED", "No se pudo enviar el correo. Intenta más tarde.");
        return Map.of("sent", true);
    }

    @PostMapping("/{id}/fiscal/retry")
    @PreAuthorize(WRITE)
    SaleService.Sale retry(@AuthenticationPrincipal StocklyPrincipal actor, @PathVariable String id) {
        var sale = sales.get(actor, id);
        fiscal.retryNow(actor.companyId(), sale.movement().id());
        return sales.get(actor, id);
    }

    @PostMapping("/{id}/fiscal/correct")
    @PreAuthorize(WRITE)
    SaleService.Sale correct(@AuthenticationPrincipal StocklyPrincipal actor, @PathVariable String id, @RequestBody Correction input) {
        var sale = sales.get(actor, id);
        fiscal.correct(actor.companyId(), sale.movement().id(), input.idType(), input.idNumber(), input.name());
        return sales.get(actor, id);
    }

    record Correction(String idType, String idNumber, String name) {}

    private static ResponseEntity<byte[]> download(byte[] body, String name, MediaType type) {
        return ResponseEntity.ok().contentType(type)
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
            .header("X-Content-Type-Options", "nosniff").body(body);
    }
}
