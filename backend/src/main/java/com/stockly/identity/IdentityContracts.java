package com.stockly.identity;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class IdentityContracts {
    private IdentityContracts() {}
    public record LoginRequest(@NotBlank @Size(max=254) String identifier, @NotBlank @Size(min=15,max=128) String password) {
        @Override public String toString() { return "LoginRequest[identifier=REDACTED,password=REDACTED]"; }
    }
    public record LoginResult(String status, String challengeId, String message) {}
    public record VerifyCodeRequest(@NotBlank String challengeId, @NotBlank @Pattern(regexp="[0-9]{6}") String code, boolean rememberDevice) {
        @Override public String toString() { return "VerifyCodeRequest[challengeId=REDACTED,code=REDACTED]"; }
    }
    public record ChangePasswordRequest(@NotBlank @Size(min=15,max=128) String newPassword) {
        @Override public String toString() { return "ChangePasswordRequest[REDACTED]"; }
    }
    public record RecoveryRequest(@NotBlank @Email @Size(max=254) String email) {}
    public record CompleteRecoveryRequest(@NotBlank String token, @NotBlank @Size(min=15,max=128) String newPassword) {
        @Override public String toString() { return "CompleteRecoveryRequest[REDACTED]"; }
    }
    public record SessionUser(String id, String fullName, String username, String email, String role,
                              boolean mustChangePassword, String companyId, List<ModuleGrant> modules, Set<String> specialPermissions) {}
    public record ModuleGrant(String moduleKey, String accessLevel) {}
    public record WorkerInput(@NotBlank @Size(min=2,max=150) String fullName,
        @NotBlank @Pattern(regexp="[A-Za-z0-9._-]{3,30}") String username,
        @NotBlank @Email @Size(max=254) String email, @NotBlank String roleId) {}
    public record AssignRoleRequest(@NotBlank String roleId) {}
    public record WorkerView(String id, String fullName, String username, String email, boolean active, String roleId, String roleName) {}
    public record CreatedWorker(WorkerView worker, String temporaryPassword, Instant temporaryPasswordExpiresAt) {
        @Override public String toString() { return "CreatedWorker[worker="+worker+", temporaryPassword=REDACTED]"; }
    }
    public record RoleInput(@NotBlank @Size(min=2,max=80) String name,
                            Map<@NotBlank String,@NotBlank String> modules,
                            Set<@NotBlank String> specialPermissions) {}
    public record RoleView(String id, String name, Map<String,String> modules, Set<String> specialPermissions, long assignedWorkers) {}
    public record PermissionModule(String key, String label) {}
    public record SessionPolicy(Long idleTimeoutMinutes) {}
}
