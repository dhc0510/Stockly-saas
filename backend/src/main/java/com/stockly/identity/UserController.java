package com.stockly.identity;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static com.stockly.identity.IdentityContracts.*;

@RestController @RequestMapping("/api/users")
public class UserController {
    private final IdentityManagementService service;
    public UserController(IdentityManagementService service) { this.service=service; }
    @GetMapping List<WorkerView> list() { return service.listWorkers(); }
    @PostMapping CreatedWorker create(@RequestBody @Valid WorkerInput input) { return service.createWorker(input); }
    @PostMapping("/{id}/temporary-password") CreatedWorker temporaryPassword(@PathVariable String id) { return service.issueTemporaryPassword(id); }
    @PatchMapping("/{id}/role") WorkerView role(@PathVariable String id,@RequestBody @Valid AssignRoleRequest input) { return service.assignRole(id,input.roleId()); }
    @PostMapping("/{id}/deactivate") void deactivate(@PathVariable String id) { service.deactivateWorker(id); }
}
