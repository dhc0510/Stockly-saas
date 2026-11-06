package com.stockly.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CompanyRoleRepository extends JpaRepository<CompanyRole, String> {
    List<CompanyRole> findAllByCompanyIdOrderByNameAsc(String companyId);
    Optional<CompanyRole> findByIdAndCompanyId(String id, String companyId);
    long countByCompanyId(String companyId);
    void deleteByCompanyId(String companyId);
}
