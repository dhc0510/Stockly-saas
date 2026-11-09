package com.stockly.inventory;

import com.stockly.identity.StocklyPrincipal;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** STK-017: reportes de solo lectura; cualquier nivel del módulo REPORTS distinto de «Sin acceso» permite consultarlos. */
@RestController
@RequestMapping("/api/reports")
@PreAuthorize(ReportController.READ)
public class ReportController {
    static final String READ = "hasAnyAuthority('ROLE_COMPANY_ADMIN','MODULE_REPORTS_READ','MODULE_REPORTS_OPERATE','MODULE_REPORTS_ADMIN')";
    private final ReportService reports;

    ReportController(ReportService reports) { this.reports = reports; }

    @GetMapping("/summary")
    ReportService.Summary summary(@AuthenticationPrincipal StocklyPrincipal actor, @ModelAttribute Params p) { return reports.summary(actor, p.filters()); }

    @GetMapping("/products")
    ReportService.Products products(@AuthenticationPrincipal StocklyPrincipal actor, @ModelAttribute Params p) { return reports.products(actor, p.filters()); }

    @GetMapping("/payments")
    ReportService.Payments payments(@AuthenticationPrincipal StocklyPrincipal actor, @ModelAttribute Params p) { return reports.payments(actor, p.filters()); }

    @GetMapping("/sellers")
    ReportService.Sellers sellers(@AuthenticationPrincipal StocklyPrincipal actor, @ModelAttribute Params p) { return reports.sellers(actor, p.filters()); }

    @GetMapping("/idle")
    ReportService.Idle idle(@AuthenticationPrincipal StocklyPrincipal actor, @ModelAttribute Params p) { return reports.idle(actor, p.filters()); }

    @GetMapping("/export")
    ResponseEntity<byte[]> export(@AuthenticationPrincipal StocklyPrincipal actor, @RequestParam String format,
                                  @RequestParam(defaultValue = "") String report, @ModelAttribute Params p) {
        ReportService.Export file = reports.export(actor, format, report, p.filters());
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.fileName()).build().toString())
            .header(HttpHeaders.CONTENT_TYPE, file.contentType())
            .body(file.content());
    }

    /** Filtros comunes: período (AAAA-MM-DD), sucursal y los propios de Productos y «Sin ventas». */
    public record Params(String from, String to, String branchId, String search, String category, String sort, Boolean onlyStock) {
        ReportService.Filters filters() {
            return new ReportService.Filters(from, to, branchId, search, category, sort, onlyStock == null || onlyStock);
        }
    }
}
