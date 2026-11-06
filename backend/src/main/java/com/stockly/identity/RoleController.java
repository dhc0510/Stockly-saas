package com.stockly.identity;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.List;
import static com.stockly.identity.IdentityContracts.*;

@RestController @RequestMapping("/api/roles")
public class RoleController {
    private final IdentityManagementService service;
    public RoleController(IdentityManagementService service) { this.service=service; }
    @GetMapping List<RoleView> list() { return service.listRoles(); }
    @PostMapping RoleView create(@RequestBody @Valid RoleInput input) { return service.createRole(input); }
    @PatchMapping("/{id}") RoleView update(@PathVariable String id,@RequestBody @Valid RoleInput input) { return service.updateRole(id,input); }
    @DeleteMapping("/{id}") @PreAuthorize("hasAuthority('ROLE_COMPANY_ADMIN') or (hasAuthority('PERM_MANAGE_PERMISSIONS') and hasAuthority('PERM_IRREVERSIBLE_ACTIONS'))")
    ResponseEntity<Void> delete(@PathVariable String id) { service.deleteRole(id); return ResponseEntity.noContent().build(); }
}
