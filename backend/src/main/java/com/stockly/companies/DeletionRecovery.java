package com.stockly.companies;

import com.stockly.identity.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Internal recovery operation, intentionally has no HTTP endpoint. Run against an isolated restored DB. */
@Service
public class DeletionRecovery {
    private final CompanyRepository companies;
    private final AccountRepository accounts;
    private final DeletionJournal journal;
    public DeletionRecovery(CompanyRepository companies, AccountRepository accounts, DeletionJournal journal) {
        this.companies=companies; this.accounts=accounts; this.journal=journal;
    }
    @Transactional
    public int reconcile() {
        int removed=0;
        for(String id:journal.blockedCompanyIds()) {
            var company=companies.lockById(id);
            if(company.isPresent()) {
                accounts.deleteByCompanyId(id); accounts.flush();
                companies.delete(company.get()); companies.flush(); removed++;
            }
        }
        return removed;
    }
}
