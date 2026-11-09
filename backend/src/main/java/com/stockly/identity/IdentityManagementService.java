package com.stockly.identity;

import com.stockly.companies.Company;
import com.stockly.companies.CompanyRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import static com.stockly.identity.IdentityContracts.*;

@Service
public class IdentityManagementService {
    private static final Set<String> LEVELS = Set.of("NONE", "READ", "OPERATE", "ADMIN");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789-_".toCharArray();
    private final AccountRepository accounts;
    private final CompanyRoleRepository roles;
    private final CompanyRepository companies;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final SessionRevoker revoker;
    private final AuditEventService audit;

    public IdentityManagementService(AccountRepository accounts, CompanyRoleRepository roles, CompanyRepository companies,
                                    JdbcTemplate jdbc, PasswordEncoder encoder, SessionRevoker revoker,AuditEventService audit,
                                    com.stockly.subscriptions.SubscriptionGuard guard) {
        this.accounts=accounts; this.roles=roles; this.companies=companies; this.jdbc=jdbc; this.encoder=encoder; this.revoker=revoker; this.audit=audit;
        this.guard=guard;
    }
    private final com.stockly.subscriptions.SubscriptionGuard guard;

    @Transactional(readOnly=true)
    public List<WorkerView> listWorkers() {
        String companyId=companyId();
        return accounts.findAllByCompanyIdOrderByFullNameAsc(companyId).stream()
            .filter(a -> a.role().equals("WORKER"))
            .map(a -> new WorkerView(a.id(),a.fullName(),a.username(),a.email(),a.active(),a.roleId(),
                roles.findByIdAndCompanyId(a.roleId(),companyId).map(CompanyRole::name).orElse(""))).toList();
    }

    @Transactional
    public CreatedWorker createWorker(WorkerInput input) {
        String companyId=companyId();
        if(input==null) throw invalid("request","Solicitud incompleta.");
        if(input.fullName()==null || input.username()==null || input.email()==null || input.roleId()==null) throw invalid("request","Completa todos los datos del trabajador.");
        String fullName=input.fullName().strip();
        String username=input.username().strip().toLowerCase(Locale.ROOT);
        String email=input.email().strip().toLowerCase(Locale.ROOT);
        if(fullName.length()<2 || !username.matches("[a-z0-9._-]{3,30}") || email.length()>254 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
            throw invalid("request","Revisa el nombre, usuario y correo.");
        var role=roles.findByIdAndCompanyId(input.roleId(),companyId).orElseThrow(() -> new IdentityFailure(404,"ROLE_NOT_FOUND","El rol no está disponible."));
        guard.requireCapacity(companyId,com.stockly.subscriptions.SubscriptionGuard.Limit.USERS); // STK-018: límite de la prueba
        String temporaryPassword=temporaryPassword();
        var account=Account.worker(companyId,fullName,username,email,encoder.encode(temporaryPassword),role.id());
        accounts.saveAndFlush(account);
        audit.record("USER_CREATED","USER",account.id());
        return new CreatedWorker(new WorkerView(account.id(),fullName,username,email,true,role.id(),role.name()),temporaryPassword,account.temporaryPasswordExpiresAt());
    }

    @Transactional
    public void deactivateWorker(String id) {
        var account=accounts.findByIdAndCompanyId(id,companyId()).orElseThrow(() -> new IdentityFailure(404,"USER_NOT_FOUND","El usuario no está disponible."));
        if(!account.role().equals("WORKER")) throw new IdentityFailure(403,"FIXED_ADMIN","Solo se pueden desactivar trabajadores.");
        if(!account.active()) return;
        account.deactivate();
        jdbc.update("UPDATE users SET active=false, access_version=access_version+1 WHERE id=?",account.id());
        revoker.revokeUser(account.id());
        audit.record("USER_DEACTIVATED","USER",account.id());
    }

    @Transactional
    public CreatedWorker issueTemporaryPassword(String id) {
        String companyId=companyId();
        var account=accounts.findByIdAndCompanyId(id,companyId).orElseThrow(() -> new IdentityFailure(404,"USER_NOT_FOUND","El usuario no está disponible."));
        if(!account.role().equals("WORKER") || !account.active()) throw new IdentityFailure(404,"USER_NOT_FOUND","El usuario no está disponible.");
        String password=temporaryPassword(); Instant now=Instant.now();
        account.issueTemporaryPassword(encoder.encode(password),now); accounts.save(account);
        revoker.revokeUser(account.id());
        audit.record("TEMP_PASSWORD_ISSUED","USER",account.id());
        String roleName=roles.findByIdAndCompanyId(account.roleId(),companyId).map(CompanyRole::name).orElse("");
        var view=new WorkerView(account.id(),account.fullName(),account.username(),account.email(),true,account.roleId(),roleName);
        return new CreatedWorker(view,password,account.temporaryPasswordExpiresAt());
    }

    @Transactional
    public WorkerView assignRole(String id,String roleId) {
        String companyId=companyId();
        var account=accounts.findByIdAndCompanyId(id,companyId).orElseThrow(() -> new IdentityFailure(404,"USER_NOT_FOUND","El usuario no está disponible."));
        if(!account.role().equals("WORKER")) throw new IdentityFailure(403,"FIXED_ADMIN","No se puede cambiar el rol del administrador.");
        var role=roles.findByIdAndCompanyId(roleId,companyId).orElseThrow(() -> new IdentityFailure(404,"ROLE_NOT_FOUND","El rol no está disponible."));
        account.assignRole(role.id()); accounts.save(account); revoker.revokeSessions(account.id());
        audit.record("USER_ROLE_ASSIGNED","USER",account.id());
        return new WorkerView(account.id(),account.fullName(),account.username(),account.email(),account.active(),role.id(),role.name());
    }

    @Transactional(readOnly=true)
    public List<RoleView> listRoles() { return roles.findAllByCompanyIdOrderByNameAsc(companyId()).stream().map(this::roleView).toList(); }

    @Transactional
    public RoleView createRole(RoleInput input) {
        if(input==null) throw invalid("request","Solicitud incompleta.");
        String companyId=companyId();
        String name=normalizedRoleName(input.name());
        var role=roles.saveAndFlush(CompanyRole.create(companyId,name));
        replaceGrants(role,input);
        audit.record("ROLE_CREATED","ROLE",role.id());
        return roleView(role);
    }

    @Transactional
    public RoleView updateRole(String id,RoleInput input) {
        if(input==null) throw invalid("request","Solicitud incompleta.");
        String companyId=companyId();
        var role=roles.findByIdAndCompanyId(id,companyId).orElseThrow(() -> new IdentityFailure(404,"ROLE_NOT_FOUND","El rol no está disponible."));
        role.rename(normalizedRoleName(input.name()));
        replaceGrants(role,input);
        List<String> assigned=jdbc.query("SELECT id FROM users WHERE role_id=?",(rs,row)->rs.getString(1),role.id());
        for(String userId:assigned) {
            jdbc.update("UPDATE users SET access_version=access_version+1 WHERE id=?",userId);
            revoker.revokeSessions(userId);
        }
        audit.record("ROLE_UPDATED","ROLE",role.id());
        return roleView(role);
    }

    @Transactional
    public void deleteRole(String id) {
        String companyId=companyId();
        var role=roles.findByIdAndCompanyId(id,companyId).orElseThrow(() -> new IdentityFailure(404,"ROLE_NOT_FOUND","El rol no está disponible."));
        if(accounts.countByCompanyIdAndRoleId(companyId,role.id())>0) throw new IdentityFailure(409,"ROLE_ASSIGNED","Reasigna los trabajadores antes de eliminar este rol.");
        roles.delete(role); audit.record("ROLE_DELETED","ROLE",role.id());
    }

    @Transactional(readOnly=true)
    public List<PermissionModule> modules() {
        return jdbc.query("SELECT module_key,label FROM permission_modules ORDER BY label",(rs,row)->new PermissionModule(rs.getString(1),rs.getString(2)));
    }

    @Transactional(readOnly=true)
    public List<PermissionModule> specialPermissions() {
        return jdbc.query("SELECT permission_key,label FROM special_permission_catalog ORDER BY label",(rs,row)->new PermissionModule(rs.getString(1),rs.getString(2)));
    }

    @Transactional(readOnly=true)
    public SessionPolicy sessionPolicy() { return new SessionPolicy(company().idleTimeoutMinutes()); }

    @Transactional
    public SessionPolicy updateSessionPolicy(SessionPolicy policy) {
        if(policy==null) throw invalid("request","Solicitud incompleta.");
        Long value=policy.idleTimeoutMinutes();
        if(value!=null && (value<30 || value%30!=0)) throw invalid("idleTimeoutMinutes","Usa múltiplos de 30 minutos o selecciona Nunca.");
        var company=company(); Long previous=company.idleTimeoutMinutes(); company.idleTimeoutMinutes(value);
        for(Account account:accounts.findAllByCompanyIdOrderByFullNameAsc(company.id()))
            revoker.updateIdleTimeout(account.id(),value);
        if(!Objects.equals(previous,value)) audit.record("SESSION_POLICY_UPDATED","SESSION_POLICY",company.id());
        return new SessionPolicy(company.idleTimeoutMinutes());
    }

    private void replaceGrants(CompanyRole role,RoleInput input) {
        Map<String,String> moduleInput=input.modules()==null ? Map.of() : input.modules();
        Set<String> validModules=new HashSet<>(jdbc.query("SELECT module_key FROM permission_modules",(rs,row)->rs.getString(1)));
        for(var entry:moduleInput.entrySet()) {
            if(!validModules.contains(entry.getKey()) || !LEVELS.contains(entry.getValue())) throw invalid("modules","El módulo o nivel de acceso no es válido.");
        }
        Set<String> special=input.specialPermissions()==null ? Set.of() : input.specialPermissions();
        Set<String> validSpecial=new HashSet<>(jdbc.query("SELECT permission_key FROM special_permission_catalog",(rs,row)->rs.getString(1)));
        if(!validSpecial.containsAll(special)) throw invalid("specialPermissions","Hay permisos especiales no válidos.");
        jdbc.update("DELETE FROM role_module_permissions WHERE role_id=?",role.id());
        jdbc.update("DELETE FROM role_special_permissions WHERE role_id=?",role.id());
        moduleInput.forEach((module,level)->jdbc.update("INSERT INTO role_module_permissions(role_id,module_key,access_level) VALUES(?,?,?)",role.id(),module,level));
        special.forEach(permission->jdbc.update("INSERT INTO role_special_permissions(role_id,permission_key) VALUES(?,?)",role.id(),permission));
    }

    private RoleView roleView(CompanyRole role) {
        Map<String,String> modules=new LinkedHashMap<>();
        jdbc.query("SELECT module_key,access_level FROM role_module_permissions WHERE role_id=? ORDER BY module_key",rs->{modules.put(rs.getString(1),rs.getString(2));},role.id());
        Set<String> special=new TreeSet<>(jdbc.query("SELECT permission_key FROM role_special_permissions WHERE role_id=?",(rs,row)->rs.getString(1),role.id()));
        return new RoleView(role.id(),role.name(),modules,special,accounts.countByCompanyIdAndRoleId(companyId(),role.id()));
    }
    private Company company() { return companies.findById(companyId()).orElseThrow(() -> new IdentityFailure(404,"COMPANY_NOT_FOUND","La empresa no está disponible.")); }
    private static String companyId() {
        Object principal=SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if(!(principal instanceof StocklyPrincipal p) || p.companyId()==null) throw new IdentityFailure(403,"COMPANY_REQUIRED","La operación requiere una cuenta de empresa.");
        return p.companyId();
    }
    private static String temporaryPassword() {
        char[] value=new char[24];
        for(int i=0;i<value.length;i++) value[i]=PASSWORD_CHARS[RANDOM.nextInt(PASSWORD_CHARS.length)];
        return new String(value);
    }
    private static IdentityFailure invalid(String field,String message) { return new IdentityFailure(400,"VALIDATION",message,Map.of(field,message)); }
    private static String normalizedRoleName(String input) {
        if(input==null) throw invalid("name","Ingresa el nombre del rol.");
        String name=input.strip();
        if(name.length()<2 || name.length()>80) throw invalid("name","El nombre del rol debe tener entre 2 y 80 caracteres.");
        return name;
    }
}
