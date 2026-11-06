package com.stockly.companies;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "companies")
public class Company {
    @Id @Column(length = 36) String id;
    @Column(nullable = false, length = 150) String commercialName;
    @Column(nullable = false, length = 150) String legalName;
    @Column(nullable = false, length = 12) String identificationType;
    @Column(nullable = false, length = 12) String identificationNormalized;
    @Column(nullable = false, length = 254) String contactEmail;
    @Column(length = 30) String phone;
    @Column(length = 500) String address;
    @Column(nullable = false, length = 12) String status;
    @Column(nullable = false) Instant createdAt;
    @Column(nullable = false) Instant updatedAt;
    @Version long version;
    @Column(nullable = false) long accessVersion;
    @Column(name="idle_timeout_minutes") Long idleTimeoutMinutes = 120L;

    protected Company() {}
    static Company create(CompanyContracts.CompanyInput input) {
        var company = new Company();
        company.id = UUID.randomUUID().toString();
        company.createdAt = Instant.now();
        company.status = "ACTIVE";
        company.update(input);
        return company;
    }
    void update(CompanyContracts.CompanyInput input) {
        commercialName = input.commercialName(); legalName = input.legalName();
        identificationType = input.identificationType(); identificationNormalized = input.identification();
        contactEmail = input.contactEmail(); phone = input.phone(); address = input.address();
        updatedAt = Instant.now();
    }
    public String id() { return id; }
    public String status() { return status; }
    public long accessVersion() { return accessVersion; }
    public Long idleTimeoutMinutes() { return idleTimeoutMinutes; }
    public void idleTimeoutMinutes(Long value) { idleTimeoutMinutes = value; }
}
