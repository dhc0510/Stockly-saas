package com.stockly.identity;

import jakarta.persistence.*;
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

    protected Account() {}
    public static Account initial(String companyId, String fullName, String username, String email, String hash) {
        var account = new Account();
        account.id = UUID.randomUUID().toString(); account.companyId = companyId;
        account.fullName = fullName; account.usernameNormalized = username;
        account.emailNormalized = email; account.passwordHash = hash;
        account.mustChangePassword = true; account.role = "COMPANY_ADMIN";
        return account;
    }
    public String id() { return id; }
    public String fullName() { return fullName; }
    public String username() { return usernameNormalized; }
    public String email() { return emailNormalized; }
}
