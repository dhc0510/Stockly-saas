package com.stockly.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, String> {
    Optional<Account> findFirstByCompanyIdOrderByIdAsc(String companyId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Account> findByUsernameNormalizedOrEmailNormalized(String username, String email);
    Optional<Account> findByIdAndCompanyId(String id, String companyId);
    java.util.List<Account> findAllByCompanyIdOrderByFullNameAsc(String companyId);
    long countByCompanyIdAndRoleAndActiveTrue(String companyId, String role);
    long countByCompanyIdAndRoleId(String companyId, String roleId);
    long countByRole(String role);
    Optional<Account> findByEmailNormalized(String email);
    void deleteByCompanyId(String companyId);
}
