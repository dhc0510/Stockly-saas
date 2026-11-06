package com.stockly.companies;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import com.stockly.identity.Account;

public final class CompanyContracts {
    private CompanyContracts() {}
    public record CompanyInput(
        @NotBlank @Size(min=2,max=150) String commercialName,
        @NotBlank @Size(min=2,max=150) String legalName,
        @NotNull @Pattern(regexp="PHYSICAL|LEGAL|DIMEX|NITE") String identificationType,
        @NotBlank String identification,
        @NotBlank @Email @Size(max=254) String contactEmail,
        @Size(max=30) String phone, @Size(max=500) String address) {}
    public record AdministratorInput(
        @NotBlank @Size(min=2,max=150) String fullName,
        @NotBlank @Pattern(regexp="[a-zA-Z0-9._-]{3,30}") String username,
        @NotBlank @Email @Size(max=254) String email,
        @NotNull @Size(min=15,max=128) String temporaryPassword) {
        @Override public String toString() { return "AdministratorInput[REDACTED]"; }
    }
    public record CreateCompany(@NotNull @Valid CompanyInput company, @NotNull @Valid AdministratorInput administrator) {
        @Override public String toString() { return "CreateCompany[REDACTED]"; }
    }
    public record UpdateCompany(@NotNull @Valid CompanyInput company, @NotNull @PositiveOrZero Long version) {}
    public record StateChange(@NotNull @PositiveOrZero Long version) {}
    public record DeleteCompany(@NotBlank String confirmationName, @NotNull @PositiveOrZero Long version) {}
    public record AdministratorView(String id, String fullName, String username, String email) {
        static AdministratorView of(Account account) {
            return new AdministratorView(account.id(), account.fullName(), account.username(), account.email());
        }
    }
    public record CompanyView(String id, String commercialName, String legalName,
        String identificationType, String identification, String contactEmail, String phone,
        String address, String status, Instant createdAt, long version, AdministratorView administrator) {
        static CompanyView of(Company c, Account a) {
            return new CompanyView(c.id,c.commercialName,c.legalName,c.identificationType,c.identificationNormalized,
                c.contactEmail,c.phone,c.address,c.status,c.createdAt,c.version,AdministratorView.of(a));
        }
    }
    public record CompanyPage(List<CompanyView> items, long total, int page, int size) {}
}
