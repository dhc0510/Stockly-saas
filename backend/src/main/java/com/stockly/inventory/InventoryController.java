package com.stockly.inventory;

import com.stockly.identity.StocklyPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.stockly.inventory.InventoryContracts.*;

@RestController
@RequestMapping("/api/inventory")
@PreAuthorize("hasAnyAuthority('ROLE_COMPANY_ADMIN','MODULE_INVENTORY_READ','MODULE_INVENTORY_OPERATE','MODULE_INVENTORY_ADMIN','MODULE_CATALOG_OPERATE','MODULE_CATALOG_ADMIN')")
public class InventoryController {
    private static final String READERS = "hasAnyAuthority('ROLE_COMPANY_ADMIN','MODULE_INVENTORY_READ','MODULE_INVENTORY_OPERATE','MODULE_INVENTORY_ADMIN')";
    private final InventoryService inventory;
    private final RestockService restock;
    public InventoryController(InventoryService inventory, RestockService restock) { this.inventory = inventory; this.restock = restock; }

    // STK-016: Por reponer, contador del menú y exportación, con los mismos permisos y sucursales que Existencias.
    @GetMapping("/restock")
    @PreAuthorize(READERS)
    RestockService.Restock restock(Authentication auth,@RequestParam(required=false) String branchId,@RequestParam(defaultValue="") String search,
                                   @RequestParam(defaultValue="") String category,@RequestParam(defaultValue="false") boolean includeLow) {
        return restock.list(principal(auth),branchId,search,category,includeLow);
    }

    @GetMapping("/restock/count")
    @PreAuthorize(READERS)
    java.util.Map<String,Integer> restockCount(Authentication auth) { return java.util.Map.of("count",restock.count(principal(auth))); }

    @GetMapping("/restock/export")
    @PreAuthorize(READERS)
    org.springframework.http.ResponseEntity<byte[]> restockExport(Authentication auth,@RequestParam String format,@RequestParam(required=false) String branchId,
                                       @RequestParam(defaultValue="") String search,@RequestParam(defaultValue="") String category,
                                       @RequestParam(defaultValue="false") boolean includeLow) {
        RestockService.Export file = restock.export(principal(auth),format,branchId,search,category,includeLow);
        return org.springframework.http.ResponseEntity.ok()
            .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, org.springframework.http.ContentDisposition.attachment().filename(file.fileName()).build().toString())
            .header(org.springframework.http.HttpHeaders.CACHE_CONTROL, "no-store")
            .contentType(org.springframework.http.MediaType.parseMediaType(file.contentType())).body(file.content());
    }

    @GetMapping("/location-setup")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    InventoryService.LocationSetup locationSetup(Authentication auth) {
        return inventory.locationSetup(principal(auth));
    }

    @PostMapping("/location-setup")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    InventoryService.LocationSetup configureLocations(Authentication auth, @RequestBody LocationInput input) {
        return inventory.configureLocations(principal(auth), input.branchId());
    }

    record LocationInput(String branchId) {}


    @GetMapping("/branches")
    @PreAuthorize(READERS)
    List<Branch> branches(Authentication auth,@RequestParam(defaultValue="false") boolean includeInactive) {
        return inventory.branches(principal(auth),includeInactive);
    }

    @PostMapping("/branches")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    Branch createBranch(Authentication auth,@RequestBody BranchInput input) { return inventory.createBranch(principal(auth),input); }

    @PutMapping("/branches/{branchId}")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    Branch editBranch(Authentication auth,@PathVariable String branchId,@RequestBody BranchInput input) {
        return inventory.editBranch(principal(auth),branchId,input);
    }

    @PostMapping("/branches/{branchId}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    void deactivate(Authentication auth,@PathVariable String branchId) { inventory.deactivateBranch(principal(auth),branchId); }

    @PostMapping("/branches/{branchId}/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    void activate(Authentication auth,@PathVariable String branchId) { inventory.activateBranch(principal(auth),branchId); }

    @DeleteMapping("/branches/{branchId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    void deleteBranch(Authentication auth,@PathVariable String branchId) { inventory.deleteBranch(principal(auth),branchId); }

    @GetMapping("/branches/{branchId}/access")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    List<BranchAccess> branchAccess(Authentication auth,@PathVariable String branchId) { return inventory.branchAccess(principal(auth),branchId); }

    @PutMapping("/branches/{branchId}/access")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    void setBranchAccess(Authentication auth,@PathVariable String branchId,@RequestBody AccessInput input) {
        inventory.setBranchAccess(principal(auth),branchId,input);
    }

    @GetMapping("/items")
    @PreAuthorize(READERS)
    List<StockItem> items(Authentication auth,@RequestParam(required=false) String branchId,
                          @RequestParam(defaultValue="") String search,@RequestParam(defaultValue="") String category,
                          @RequestParam(defaultValue="ALL") String status,@RequestParam(defaultValue="NAME") String sort) {
        return inventory.items(principal(auth),branchId,search,category,status,sort);
    }

    @GetMapping("/pending-items")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    List<PendingItem> pendingItems(Authentication auth) { return inventory.pendingItems(principal(auth)); }

    @PostMapping("/branches/{branchId}/items")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('ROLE_COMPANY_ADMIN','MODULE_CATALOG_OPERATE','MODULE_CATALOG_ADMIN')")
    void assignItem(Authentication auth,@PathVariable String branchId,@RequestBody ItemAssignmentInput input) {
        inventory.assignItem(principal(auth),branchId,input);
    }

    @PutMapping("/branches/{branchId}/items/{itemId}/thresholds")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyAuthority('ROLE_COMPANY_ADMIN','MODULE_CATALOG_OPERATE','MODULE_CATALOG_ADMIN')")
    void updateThresholds(Authentication auth,@PathVariable String branchId,@PathVariable String itemId,@RequestBody ThresholdInput input) {
        inventory.updateThresholds(principal(auth),branchId,itemId,input);
    }

    @DeleteMapping("/branches/{branchId}/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    void unassignItem(Authentication auth,@PathVariable String branchId,@PathVariable String itemId) {
        inventory.unassignItem(principal(auth),branchId,itemId);
    }

    @GetMapping("/alerts")
    @PreAuthorize(READERS)
    List<Alert> alerts(Authentication auth) { return inventory.alerts(principal(auth)); }

    @PostMapping("/alerts/{alertId}/dismiss")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(READERS)
    void dismiss(Authentication auth,@PathVariable String alertId) { inventory.dismissAlert(principal(auth),alertId); }

    private StocklyPrincipal principal(Authentication auth) { return (StocklyPrincipal)auth.getPrincipal(); }
}
