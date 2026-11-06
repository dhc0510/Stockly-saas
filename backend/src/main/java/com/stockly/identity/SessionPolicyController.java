package com.stockly.identity;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import static com.stockly.identity.IdentityContracts.*;

@RestController @RequestMapping("/api/company/security/session-policy")
public class SessionPolicyController {
    private final IdentityManagementService service;
    public SessionPolicyController(IdentityManagementService service) { this.service=service; }
    @GetMapping SessionPolicy get() { return service.sessionPolicy(); }
    @PutMapping SessionPolicy update(@RequestBody @Valid SessionPolicy policy,HttpServletRequest request) {
        if(policy.idleTimeoutMinutes()==null) request.setAttribute("STOCKLY_PERSISTENT_SESSION",Boolean.TRUE);
        else request.removeAttribute("STOCKLY_PERSISTENT_SESSION");
        if(request.getSession(false)!=null) request.changeSessionId();
        return service.updateSessionPolicy(policy);
    }
}
