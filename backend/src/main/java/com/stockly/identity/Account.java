package com.stockly.identity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "users")
public class Account {
    @Id @Column(length = 36) private String id;
    @Column(length = 36) private String companyId;
    @Column(nullable = false, length = 150) private String fullName;
    @Column(nullable = false, length = 30) private String usernameNormalized;
    @Column(nullable = false, length = 254) private String emailNormalized;
    @Column(nullable = false, length = 255) private String passwordHash;
    @Column(nullable = false) private boolean mustChangePassword;
    @Column(nullable = false, length = 30) private String role;
    @Column(nullable = false) private boolean active = true;
    @Column(nullable = false) private long accessVersion;
    @Column(length = 36) private String roleId;
    private Instant emailVerifiedAt;
    private Instant temporaryPasswordExpiresAt;
    @Column(nullable = false) private int failedPasswordAttempts;
    private Instant passwordLockedUntil;
    @Column(nullable = false) private int failedDeviceAttempts;
    private Instant deviceLockedUntil;

    protected Account() {}
    public static Account initial(String companyId, String fullName, String username, String email, String hash) {
        var account = new Account();
        account.id = UUID.randomUUID().toString(); account.companyId = companyId;
        account.fullName = fullName; account.usernameNormalized = username;
        account.emailNormalized = email; account.passwordHash = hash;
        account.mustChangePassword = true; account.role = "COMPANY_ADMIN";
        account.temporaryPasswordExpiresAt = Instant.now().plusSeconds(24 * 60 * 60);
        return account;
    }
    public static Account worker(String companyId, String fullName, String username, String email, String hash, String roleId) {
        var account = new Account();
        account.id = UUID.randomUUID().toString(); account.companyId = companyId;
        account.fullName = fullName; account.usernameNormalized = username; account.emailNormalized = email;
        account.passwordHash = hash; account.mustChangePassword = true; account.role = "WORKER";
        account.roleId = roleId; account.temporaryPasswordExpiresAt = Instant.now().plusSeconds(24 * 60 * 60);
        return account;
    }
    public static Account saasAdmin(String fullName,String username,String email,String hash) {
        var account=new Account(); account.id=UUID.randomUUID().toString(); account.companyId=null;
        account.fullName=fullName; account.usernameNormalized=username; account.emailNormalized=email;
        account.passwordHash=hash; account.mustChangePassword=false; account.role="SAAS_ADMIN";
        account.emailVerifiedAt=Instant.now(); return account;
    }
    public String id() { return id; }
    public String companyId() { return companyId; }
    public String fullName() { return fullName; }
    public String username() { return usernameNormalized; }
    public String email() { return emailNormalized; }
    public String role() { return role; }
    public String roleId() { return roleId; }
    public boolean active() { return active; }
    public boolean mustChangePassword() { return mustChangePassword; }
    public long accessVersion() { return accessVersion; }
    public Instant temporaryPasswordExpiresAt() { return temporaryPasswordExpiresAt; }
    public Instant passwordLockedUntil() { return passwordLockedUntil; }
    public Instant deviceLockedUntil() { return deviceLockedUntil; }
    public String passwordHash() { return passwordHash; }
    public boolean emailVerified() { return emailVerifiedAt != null; }
    public void verifyEmail() { if(emailVerifiedAt==null) emailVerifiedAt=Instant.now(); }
    public void passwordFailed(Instant now) {
        if (passwordLockedUntil != null && passwordLockedUntil.isAfter(now)) return;
        failedPasswordAttempts++;
        if (failedPasswordAttempts >= 5) { passwordLockedUntil = now.plusSeconds(15 * 60); failedPasswordAttempts = 0; }
    }
    public void passwordSucceeded() { failedPasswordAttempts = 0; passwordLockedUntil = null; }
    public void deviceFailed(Instant now) {
        if (deviceLockedUntil != null && deviceLockedUntil.isAfter(now)) return;
        failedDeviceAttempts++;
        if (failedDeviceAttempts >= 5) { deviceLockedUntil = now.plusSeconds(15 * 60); failedDeviceAttempts = 0; }
    }
    public void deviceSucceeded() { failedDeviceAttempts = 0; deviceLockedUntil = null; }
    public void changePassword(String encoded, Instant now) {
        passwordHash = encoded; mustChangePassword = false; temporaryPasswordExpiresAt = null;
        passwordSucceeded(); accessVersion++;
    }
    public void issueTemporaryPassword(String encoded,Instant now) {
        passwordHash=encoded; mustChangePassword=true; temporaryPasswordExpiresAt=now.plusSeconds(24*60*60);
        passwordSucceeded(); accessVersion++;
    }
    public void deactivate() { active = false; accessVersion++; }
    public void assignRole(String id) { roleId = id; role = "WORKER"; accessVersion++; }
}
