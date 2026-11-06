package com.stockly.catalog;

import com.stockly.identity.StocklyPrincipal;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/catalog")
@PreAuthorize("hasAnyAuthority('ROLE_COMPANY_ADMIN','MODULE_CATALOG_READ','MODULE_CATALOG_OPERATE','MODULE_CATALOG_ADMIN')")
public class CatalogController {
    private final CatalogService catalog;

    CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    CatalogService.Page list(Authentication auth, @RequestParam(defaultValue = "") String search,
                             @RequestParam(defaultValue = "") String category,
                             @RequestParam(defaultValue = "true") boolean active,
                             @RequestParam(defaultValue = "0") int page) {
        return catalog.list(principal(auth), search, category, active, page);
    }

    @PostMapping("/categories")
    @PreAuthorize("hasAnyAuthority('ROLE_COMPANY_ADMIN','MODULE_CATALOG_OPERATE','MODULE_CATALOG_ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    CategoryInput createCategory(Authentication auth, @RequestBody CategoryInput input) {
        return new CategoryInput(catalog.createCategory(principal(auth), input.name()));
    }

    @PutMapping("/categories")
    @PreAuthorize("hasAnyAuthority('ROLE_COMPANY_ADMIN','MODULE_CATALOG_OPERATE','MODULE_CATALOG_ADMIN')")
    CategoryInput renameCategory(Authentication auth, @RequestBody CategoryRename input) {
        return new CategoryInput(catalog.renameCategory(principal(auth), input.previousName(), input.name()));
    }

    @DeleteMapping("/categories")
    @PreAuthorize("hasAnyAuthority('ROLE_COMPANY_ADMIN','MODULE_CATALOG_OPERATE','MODULE_CATALOG_ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteCategory(Authentication auth, @RequestParam String name) {
        catalog.deleteCategory(principal(auth), name);
    }

    @PostMapping("/products")
    @PreAuthorize("hasAnyAuthority('ROLE_COMPANY_ADMIN','MODULE_CATALOG_OPERATE','MODULE_CATALOG_ADMIN')")
    CatalogService.Product create(Authentication auth, @RequestBody CatalogService.ProductInput input) {
        return catalog.saveProduct(principal(auth), null, input, canPrice(auth));
    }

    @PutMapping("/products/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_COMPANY_ADMIN','MODULE_CATALOG_OPERATE','MODULE_CATALOG_ADMIN')")
    CatalogService.Product edit(Authentication auth, @PathVariable String id,
                                @RequestBody CatalogService.ProductInput input) {
        return catalog.saveProduct(principal(auth), id, input, canPrice(auth));
    }

    @PutMapping("/items/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_COMPANY_ADMIN','MODULE_CATALOG_OPERATE','MODULE_CATALOG_ADMIN')")
    CatalogService.Item editItem(Authentication auth, @PathVariable String id,
                                 @RequestBody CatalogService.ItemInput input) {
        return catalog.saveItem(principal(auth), id, input, canPrice(auth));
    }

    @PutMapping("/{kind}/{id}/status")
    @PreAuthorize("hasRole('COMPANY_ADMIN') or (hasAuthority('PERM_CATALOG_ARCHIVE') and hasAnyAuthority('MODULE_CATALOG_READ','MODULE_CATALOG_OPERATE','MODULE_CATALOG_ADMIN'))")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void status(Authentication auth, @PathVariable String kind, @PathVariable String id,
                @RequestBody StatusInput input) {
        catalog.status(principal(auth), kind, id, input.active());
    }

    @PutMapping("/items/{id}/price")
    @PreAuthorize("hasRole('COMPANY_ADMIN') or (hasAuthority('PERM_CATALOG_PRICE') and hasAnyAuthority('MODULE_CATALOG_READ','MODULE_CATALOG_OPERATE','MODULE_CATALOG_ADMIN'))")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void price(Authentication auth, @PathVariable String id, @RequestBody PriceInput input) {
        catalog.price(principal(auth), id, input.price());
    }

    @GetMapping("/products/{id}/image")
    ResponseEntity<byte[]> image(Authentication auth, @PathVariable String id) {
        var image = catalog.image(principal(auth), id);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .header("X-Content-Type-Options", "nosniff")
            .contentType(MediaType.parseMediaType(image.type())).body(image.bytes());
    }

    private static StocklyPrincipal principal(Authentication auth) {
        return (StocklyPrincipal) auth.getPrincipal();
    }

    private static boolean canPrice(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a ->
            a.getAuthority().equals("ROLE_COMPANY_ADMIN") || a.getAuthority().equals("PERM_CATALOG_PRICE"));
    }

    record CategoryRename(String previousName, String name) {}
    record CategoryInput(String name) {}
    record StatusInput(boolean active) {}
    record PriceInput(java.math.BigDecimal price) {}
}
