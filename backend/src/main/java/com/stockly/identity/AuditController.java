package com.stockly.identity;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import static com.stockly.identity.AuditContracts.*;

@RestController @RequestMapping("/api/audit") @PreAuthorize("hasAuthority('ROLE_COMPANY_ADMIN')")
public class AuditController {
    private final AuditEventService service;
    public AuditController(AuditEventService service) { this.service=service; }
    @GetMapping AuditPage list(@RequestParam(required=false) String action,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return service.list(action,page,size);
    }
}
