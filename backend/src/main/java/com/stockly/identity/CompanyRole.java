package com.stockly.identity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="company_roles", uniqueConstraints=@UniqueConstraint(columnNames={"company_id","name"}))
public class CompanyRole {
    @Id @Column(length=36) private String id;
    @Column(name="company_id", nullable=false, length=36) private String companyId;
    @Column(nullable=false, length=80) private String name;
    @Column(name="created_at", nullable=false) private Instant createdAt;
    @Column(name="updated_at", nullable=false) private Instant updatedAt;
    @Version private long version;

    protected CompanyRole() {}
    public static CompanyRole create(String companyId, String name) {
        var role = new CompanyRole();
        role.id = UUID.randomUUID().toString(); role.companyId = companyId; role.name = name;
        role.createdAt = Instant.now(); role.updatedAt = role.createdAt; return role;
    }
    public void rename(String name) { this.name = name; updatedAt = Instant.now(); }
    public String id() { return id; }
    public String companyId() { return companyId; }
    public String name() { return name; }
}
