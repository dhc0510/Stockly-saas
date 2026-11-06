package com.stockly.identity;

import org.springframework.web.bind.annotation.*;
import java.util.List;
import static com.stockly.identity.IdentityContracts.*;

@RestController @RequestMapping("/api/permissions")
public class PermissionController {
    private final IdentityManagementService service;
    public PermissionController(IdentityManagementService service) { this.service=service; }
    @GetMapping("/modules") List<PermissionModule> modules() { return service.modules(); }
    @GetMapping("/special") List<PermissionModule> special() { return service.specialPermissions(); }
}
