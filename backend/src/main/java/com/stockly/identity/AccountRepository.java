package com.stockly.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, String> {
    Optional<Account> findFirstByCompanyIdOrderByIdAsc(String companyId);
    void deleteByCompanyId(String companyId);
}
