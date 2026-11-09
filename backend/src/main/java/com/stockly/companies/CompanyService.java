package com.stockly.companies;

import com.stockly.identity.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import java.time.*;
import java.util.*;
import static com.stockly.companies.CompanyContracts.*;

@Service @PreAuthorize("hasRole('SAAS_ADMIN')")
public class CompanyService {
    private final CompanyRepository companies;
    private final AccountRepository accounts;
    private final CompanyRoleRepository roles;
    private final CompanyValidation validation;
    private final PasswordEncoder encoder;
    private final DeletionJournal journal;
    private final com.stockly.billing.FiscalArchive archive;
    private final JdbcTemplate jdbc;
    private final com.stockly.subscriptions.SubscriptionService subscriptions;
    public CompanyService(CompanyRepository companies,AccountRepository accounts,CompanyRoleRepository roles,CompanyValidation validation,PasswordEncoder encoder,DeletionJournal journal,JdbcTemplate jdbc,com.stockly.billing.FiscalArchive archive,
                          com.stockly.subscriptions.SubscriptionService subscriptions) {
        this.companies=companies; this.accounts=accounts; this.roles=roles; this.validation=validation; this.encoder=encoder; this.journal=journal; this.jdbc=jdbc;
        this.archive=archive; this.subscriptions=subscriptions;
    }
    @Transactional
    public CompanyView create(CreateCompany request) {
        if(request==null) throw CompanyValidation.invalid("request","Solicitud incompleta.");
        var input=validation.normalize(request.company());
        var admin=validation.normalize(request.administrator());
        var company=companies.saveAndFlush(Company.create(input));
        jdbc.update("INSERT INTO inventory_locations(company_id,id,name) VALUES (?,'principal','Principal')", company.id);
        subscriptions.startTrial(company.id); // STK-018: la empresa nueva empieza en prueba
        var account=accounts.saveAndFlush(Account.initial(company.id,admin.fullName(),admin.username(),admin.email(),encoder.encode(admin.temporaryPassword())));
        return CompanyView.of(company,account);
    }
    @Transactional(readOnly=true)
    public CompanyView get(String id) { return view(companies.findById(id).orElseThrow(CompanyService::missing)); }
    @Transactional(readOnly=true)
    public CompanyPage list(String search,String status,LocalDate from,LocalDate to,int page,int size) { return list(search,status,null,from,to,page,size); }
    @Transactional
    public CompanyPage list(String search,String status,String subscription,LocalDate from,LocalDate to,int page,int size) {
        // STK-018: filtro por estado de la suscripción (último estado calculado).
        List<String> subscribed=subscription==null || subscription.isBlank() ? null : subscriptions.companiesInState(subscription);
        if(page<0 || size<1 || size>100 || (search!=null && search.length()>150))
            throw CompanyValidation.invalid("filters","Revisa la búsqueda y la paginación.");
        if(status!=null && !Set.of("ACTIVE","SUSPENDED").contains(status)) throw CompanyValidation.invalid("status","Estado inválido.");
        if(from!=null && to!=null && from.isAfter(to)) throw CompanyValidation.invalid("registeredFrom","El inicio no puede ser posterior al fin.");
        var zone=ZoneId.of("America/Costa_Rica");
        Specification<Company> spec=(root,query,cb) -> {
            var filters=new ArrayList<Predicate>();
            if(search!=null && !search.isBlank()) {
                String text=search.strip().toLowerCase(Locale.ROOT).replace("\\","\\\\").replace("%","\\%").replace("_","\\_");
                var names=cb.or(cb.like(cb.lower(root.get("commercialName")),"%"+text+"%",'\\'),cb.like(cb.lower(root.get("legalName")),"%"+text+"%",'\\'));
                var id=search.replaceAll("[\\s-]","");
                filters.add(id.matches("[0-9]+") ? cb.or(names,cb.like(root.get("identificationNormalized"),"%"+id+"%")) : names);
            }
            if(status!=null) filters.add(cb.equal(root.get("status"),status));
            if(subscribed!=null) filters.add(subscribed.isEmpty() ? cb.disjunction() : root.get("id").in(subscribed));
            if(from!=null) filters.add(cb.greaterThanOrEqualTo(root.get("createdAt"),from.atStartOfDay(zone).toInstant()));
            if(to!=null) filters.add(cb.lessThan(root.get("createdAt"),to.plusDays(1).atStartOfDay(zone).toInstant()));
            return cb.and(filters.toArray(Predicate[]::new));
        };
        var result=companies.findAll(spec,PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"createdAt","id")));
        return new CompanyPage(result.stream().map(this::view).toList(),result.getTotalElements(),page,size);
    }
    @Transactional
    public CompanyView update(String id,UpdateCompany request) {
        if(request==null || request.version()==null || request.version()<0) throw CompanyValidation.invalid("version","Versión requerida.");
        var company=locked(id,request.version());
        company.update(validation.normalize(request.company()));
        companies.flush(); return view(company);
    }
    @Transactional
    public CompanyView state(String id,StateChange request,String target) {
        validation.check(request);
        var company=companies.lockById(id).orElseThrow(CompanyService::missing);
        if(company.status.equals(target)) return view(company);
        version(company,request.version());
        company.status=target; company.updatedAt=Instant.now();
        if(target.equals("SUSPENDED")) company.accessVersion++;
        companies.flush(); return view(company);
    }
    @Transactional
    public void delete(String id,DeleteCompany request) {
        validation.check(request);
        var company=locked(id,request.version());
        if(!company.commercialName.equals(request.confirmationName().strip()))
            throw CompanyValidation.invalid("confirmationName","Escribe el nombre exacto de la empresa.");
        journal.prepare(company.id);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { journal.committed(company.id); }
            @Override public void afterCompletion(int status) {
                if(status==STATUS_ROLLED_BACK) journal.rolledBack(company.id);
            }
        });
        // ADR-008: los comprobantes enviados a Hacienda se conservan 5 años aunque se borre la empresa.
        archive.archiveCompany(company.id);
        accounts.deleteByCompanyId(company.id);
        accounts.flush();
        roles.deleteByCompanyId(company.id);
        roles.flush();
        companies.delete(company); companies.flush();
    }
    private Company locked(String id,long version) {
        var company=companies.lockById(id).orElseThrow(CompanyService::missing);
        version(company,version); return company;
    }
    private static void version(Company company,long expected) {
        if(company.version!=expected) throw new CompanyFailure(409,"STALE_VERSION","La empresa cambió. Actualiza la información e intenta de nuevo.");
    }
    private CompanyView view(Company company) {
        return CompanyView.of(company,accounts.findFirstByCompanyIdOrderByIdAsc(company.id).orElseThrow(() -> new IllegalStateException("Empresa sin administrador inicial")));
    }
    private static CompanyFailure missing() { return new CompanyFailure(404,"NOT_FOUND","La empresa no está disponible."); }
}
