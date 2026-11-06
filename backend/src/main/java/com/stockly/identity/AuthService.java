package com.stockly.identity;

import com.stockly.companies.CompanyRepository;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import static com.stockly.identity.IdentityContracts.*;

@Service
public class AuthService {
    private static final SecureRandom RANDOM=new SecureRandom();
    private static final String DEVICE_COOKIE="STOCKLY_DEVICE";
    private final AccountRepository accounts;
    private final CompanyRepository companies;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final EmailDelivery email;
    private final SessionRevoker revoker;
    private final SecurityContextRepository contexts;
    private final String baseUrl;

    public AuthService(AccountRepository accounts, CompanyRepository companies, JdbcTemplate jdbc, PasswordEncoder encoder,
                       EmailDelivery email, SessionRevoker revoker, SecurityContextRepository contexts,
                       @Value("${stockly.public-base-url:http://localhost:5173}") String baseUrl) {
        this.accounts=accounts; this.companies=companies; this.jdbc=jdbc; this.encoder=encoder;
        this.email=email; this.revoker=revoker; this.contexts=contexts; this.baseUrl=baseUrl.replaceAll("/$","");
    }

    @Transactional(noRollbackFor=IdentityFailure.class)
    public LoginResult login(LoginRequest input,HttpServletRequest request,HttpServletResponse response) {
        String identifier=input.identifier().strip().toLowerCase(Locale.ROOT);
        var account=accounts.findByUsernameNormalizedOrEmailNormalized(identifier,identifier).orElse(null);
        if(account==null || !account.active() || account.passwordLockedUntil()!=null && account.passwordLockedUntil().isAfter(Instant.now())
            || !validCompany(account) || !encoder.matches(input.password(),account.passwordHash())) {
            if(account!=null && account.active() && validCompany(account)) { account.passwordFailed(Instant.now()); accounts.save(account); }
            throw new IdentityFailure(401,"INVALID_CREDENTIALS","No se pudo iniciar sesión. Revisa las credenciales o recupera el acceso.");
        }
        if(account.mustChangePassword() && (account.temporaryPasswordExpiresAt()==null || account.temporaryPasswordExpiresAt().isBefore(Instant.now())))
            throw new IdentityFailure(401,"INVALID_CREDENTIALS","No se pudo iniciar sesión. Revisa las credenciales o recupera el acceso.");
        account.passwordSucceeded();
        if(isTrusted(account.id(),deviceCookie(request))) {
            establish(account,request,response); return new LoginResult(account.mustChangePassword()?"PASSWORD_CHANGE_REQUIRED":"AUTHENTICATED",null,"Acceso iniciado.");
        }
        if(account.deviceLockedUntil()!=null && account.deviceLockedUntil().isAfter(Instant.now()))
            throw new IdentityFailure(429,"CODE_PAUSED","Se alcanzó el límite. Intenta de nuevo en 15 minutos.");
        String challengeId=sendDeviceCode(account);
        return new LoginResult("DEVICE_CODE_REQUIRED",challengeId,"Te enviamos un código al correo registrado.");
    }

    @Transactional(noRollbackFor=IdentityFailure.class)
    public void verifyCode(VerifyCodeRequest input,HttpServletRequest request,HttpServletResponse response) {
        var rows=jdbc.query("SELECT id,user_id,code_hash,expires_at,attempts,locked_until,consumed_at FROM auth_challenges WHERE id=? FOR UPDATE",
            (rs,row)->new Object[]{rs.getString(1),rs.getString(2),rs.getString(3),rs.getTimestamp(4).toInstant(),rs.getInt(5),rs.getTimestamp(6)==null?null:rs.getTimestamp(6).toInstant(),rs.getTimestamp(7)==null?null:rs.getTimestamp(7).toInstant()},input.challengeId());
        if(rows.isEmpty()) throw invalidCode();
        Object[] row=rows.getFirst(); String challengeId=(String)row[0], userId=(String)row[1]; Instant now=Instant.now();
        var account=accounts.findById(userId).orElseThrow(AuthService::invalidCode);
        if(!account.active() || !validCompany(account)
            || account.mustChangePassword() && (account.temporaryPasswordExpiresAt()==null || !account.temporaryPasswordExpiresAt().isAfter(now)))
            throw invalidCode();
        Instant locked=(Instant)row[5], consumed=(Instant)row[6], expires=(Instant)row[3];
        if(consumed!=null || !expires.isAfter(now) || locked!=null && locked.isAfter(now)
            || account.deviceLockedUntil()!=null && account.deviceLockedUntil().isAfter(now)) throw invalidCode();
        if(!encoder.matches(input.code(),(String)row[2])) {
            int attempts=(Integer)row[4]+1;
            jdbc.update("UPDATE auth_challenges SET attempts=? WHERE id=?",attempts,challengeId);
            account.deviceFailed(now); accounts.save(account);
            if(account.deviceLockedUntil()!=null && account.deviceLockedUntil().isAfter(now))
                throw new IdentityFailure(429,"CODE_PAUSED","Se alcanzó el límite. Intenta de nuevo en 15 minutos.");
            throw new IdentityFailure(400,"INVALID_CODE","El código no es válido. Revisa el correo e inténtalo de nuevo.");
        }
        jdbc.update("UPDATE auth_challenges SET consumed_at=? WHERE id=?",now,challengeId);
        account.deviceSucceeded(); account.verifyEmail(); accounts.save(account);
        String deviceToken=null;
        if(input.rememberDevice()) { deviceToken=createTrustedDevice(account); }
        establish(account,request,response);
        if(deviceToken!=null) setDeviceCookie(response,deviceToken,30*24*60*60);
    }

    @Transactional(noRollbackFor=IdentityFailure.class)
    public void firstChange(String newPassword,HttpServletRequest request,HttpServletResponse response) {
        var principal=principal();
        var account=accounts.findById(principal.accountId()).orElseThrow(AuthService::invalidCredentials);
        if(!account.mustChangePassword()) throw new IdentityFailure(409,"PASSWORD_ALREADY_CHANGED","La contraseña temporal ya fue reemplazada.");
        account.changePassword(encoder.encode(newPassword),Instant.now()); accounts.saveAndFlush(account);
        establish(account,request,response);
    }

    @Transactional(noRollbackFor=IdentityFailure.class)
    public void requestRecovery(String emailAddress) {
        String normalized=emailAddress.strip().toLowerCase(Locale.ROOT);
        var account=accounts.findByEmailNormalized(normalized).orElse(null);
        if(account==null || !account.active() || !account.emailVerified() || !validCompany(account)) return;
        Instant now=Instant.now();
        if(!withinSendLimit(account.id(),"RECOVERY",now)) return;
        String id=UUID.randomUUID().toString(), secret=randomToken(32);
        jdbc.update("INSERT INTO password_reset_tokens(id,user_id,token_hash,expires_at,created_at) VALUES(?,?,?,?,?)",
            id,account.id(),encoder.encode(secret),now.plusSeconds(30*60),now);
        recordSend(account.id(),"RECOVERY",now);
        String link=baseUrl+"/reset-password?token="+id+"."+secret;
        try { email.send(account.email(),"Recupera tu acceso a Stockly","Usa este enlace una sola vez y dentro de 30 minutos para cambiar tu contraseña:\n\n"+link); }
        catch(RuntimeException deliveryFailure) { org.slf4j.LoggerFactory.getLogger(AuthService.class).warn("No se pudo enviar un enlace de recuperación."); }
    }

    @Transactional(noRollbackFor=IdentityFailure.class)
    public void completeRecovery(String token,String newPassword) {
        String[] parts=token.split("\\.",2);
        if(parts.length!=2) throw invalidRecovery();
        var rows=jdbc.query("SELECT user_id,token_hash,expires_at,consumed_at FROM password_reset_tokens WHERE id=? FOR UPDATE",
            (rs,row)->new Object[]{rs.getString(1),rs.getString(2),rs.getTimestamp(3).toInstant(),rs.getTimestamp(4)==null?null:rs.getTimestamp(4).toInstant()},parts[0]);
        if(rows.isEmpty()) throw invalidRecovery();
        Object[] row=rows.getFirst(); Instant now=Instant.now();
        if(row[3]!=null || !((Instant)row[2]).isAfter(now) || !encoder.matches(parts[1],(String)row[1])) throw invalidRecovery();
        String userId=(String)row[0]; var account=accounts.findById(userId).orElseThrow(AuthService::invalidRecovery);
        if(!account.active() || !account.emailVerified() || !validCompany(account)) throw invalidRecovery();
        jdbc.update("UPDATE password_reset_tokens SET consumed_at=? WHERE id=?",now,parts[0]);
        account.changePassword(encoder.encode(newPassword),now); accounts.save(account); revoker.revokeSessions(userId);
    }

    @Transactional(readOnly=true)
    public SessionUser me() {
        var p=principal(); var account=accounts.findById(p.accountId()).orElseThrow(AuthService::invalidCredentials);
        return sessionUser(account);
    }

    public void logout(HttpServletRequest request,HttpServletResponse response) {
        if(request.getSession(false)!=null) request.getSession(false).invalidate();
        SecurityContextHolder.clearContext();
    }

    private String sendDeviceCode(Account account) {
        Instant now=Instant.now();
        if(!withinSendLimit(account.id(),"DEVICE_CODE",now)) throw new IdentityFailure(429,"EMAIL_LIMIT","Espera antes de solicitar otro código.");
        jdbc.update("DELETE FROM auth_challenges WHERE expires_at<?",now.minusSeconds(24*60*60));
        jdbc.update("DELETE FROM password_reset_tokens WHERE expires_at<?",now.minusSeconds(24*60*60));
        jdbc.update("DELETE FROM trusted_devices WHERE expires_at<?",now);
        jdbc.update("UPDATE auth_challenges SET consumed_at=? WHERE user_id=? AND consumed_at IS NULL",now,account.id());
        String code=String.format(Locale.ROOT,"%06d",RANDOM.nextInt(1_000_000)); String id=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO auth_challenges(id,user_id,code_hash,expires_at,last_sent_at,created_at) VALUES(?,?,?,?,?,?)",
            id,account.id(),encoder.encode(code),now.plusSeconds(10*60),now,now);
        recordSend(account.id(),"DEVICE_CODE",now);
        email.send(account.email(),"Código de acceso a Stockly","Tu código de acceso es "+code+". Vence en 10 minutos. Si no lo solicitaste, ignora este correo.");
        return id;
    }

    private boolean withinSendLimit(String userId,String purpose,Instant now) {
        jdbc.update("DELETE FROM auth_email_sends WHERE created_at<?",now.minusSeconds(24*60*60));
        Integer minute=jdbc.queryForObject("SELECT COUNT(*) FROM auth_email_sends WHERE user_id=? AND purpose=? AND created_at>=?",Integer.class,userId,purpose,now.minusSeconds(60));
        Integer hour=jdbc.queryForObject("SELECT COUNT(*) FROM auth_email_sends WHERE user_id=? AND purpose=? AND created_at>=?",Integer.class,userId,purpose,now.minusSeconds(60*60));
        return (minute==null || minute<1) && (hour==null || hour<5);
    }
    private void recordSend(String userId,String purpose,Instant now) {
        jdbc.update("INSERT INTO auth_email_sends(id,user_id,purpose,created_at) VALUES(?,?,?,?)",UUID.randomUUID().toString(),userId,purpose,now);
    }
    private boolean isTrusted(String userId,String raw) {
        if(raw==null || raw.isBlank()) return false;
        String[] parts=raw.split("\\.",2);
        if(parts.length!=2) return false;
        List<String> hashes=jdbc.query("SELECT token_hash FROM trusted_devices WHERE id=? AND user_id=? AND revoked_at IS NULL AND expires_at>?",(rs,row)->rs.getString(1),parts[0],userId,Instant.now());
        return !hashes.isEmpty() && encoder.matches(parts[1],hashes.getFirst());
    }
    private String createTrustedDevice(Account account) {
        String id=UUID.randomUUID().toString(), secret=randomToken(32); Instant now=Instant.now();
        jdbc.update("INSERT INTO trusted_devices(id,user_id,token_hash,expires_at,created_at) VALUES(?,?,?,?,?)",
            id,account.id(),encoder.encode(secret),now.plusSeconds(30L*24*60*60),now);
        return id+"."+secret;
    }
    private void establish(Account account,HttpServletRequest request,HttpServletResponse response) {
        var context=SecurityContextHolder.createEmptyContext();
        var authorities=new ArrayList<SimpleGrantedAuthority>();
        if(account.mustChangePassword()) authorities.add(new SimpleGrantedAuthority("PASSWORD_CHANGE_ONLY"));
        else {
            authorities.add(new SimpleGrantedAuthority("ROLE_"+account.role()));
            if(account.role().equals("WORKER")) {
                jdbc.query("SELECT module_key,access_level FROM role_module_permissions WHERE role_id=?",rs->{
                    authorities.add(new SimpleGrantedAuthority("MODULE_"+rs.getString(1)+"_"+rs.getString(2)));
                },account.roleId());
                jdbc.query("SELECT permission_key FROM role_special_permissions WHERE role_id=?",rs->{
                    authorities.add(new SimpleGrantedAuthority("PERM_"+rs.getString(1)));
                },account.roleId());
            }
        }
        var principal=new StocklyPrincipal(account.id(),account.companyId(),companyVersion(account),account.accessVersion(),account.role(),account.mustChangePassword());
        var authentication=new UsernamePasswordAuthenticationToken(principal,null,authorities);
        context.setAuthentication(authentication);
        if(request.getSession(false)==null) request.getSession(true);
        request.changeSessionId();
        var session=request.getSession(true); session.setAttribute("STOCKLY_LAST_ACTIVITY",System.currentTimeMillis());
        session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,account.id());
        Long timeout=idleTimeout(account);
        if(timeout==null) request.setAttribute("STOCKLY_PERSISTENT_SESSION",Boolean.TRUE);
        session.setMaxInactiveInterval(timeout==null || timeout>Integer.MAX_VALUE/60 ? -1 : timeout.intValue()*60);
        SecurityContextHolder.setContext(context); contexts.saveContext(context,request,response);
    }

    private SessionUser sessionUser(Account account) {
        List<ModuleGrant> modules=jdbc.query("SELECT module_key,access_level FROM role_module_permissions WHERE role_id=? ORDER BY module_key",(rs,row)->new ModuleGrant(rs.getString(1),rs.getString(2)),account.roleId());
        Set<String> special=new TreeSet<>(jdbc.query("SELECT permission_key FROM role_special_permissions WHERE role_id=?",(rs,row)->rs.getString(1),account.roleId()));
        return new SessionUser(account.id(),account.fullName(),account.username(),account.email(),account.role(),account.mustChangePassword(),account.companyId(),modules,special);
    }
    private Long idleTimeout(Account account) {
        if(account.companyId()==null) return 120L;
        return companies.findById(account.companyId()).map(c->c.idleTimeoutMinutes()).orElse(120L);
    }
    private long companyVersion(Account account) { return account.companyId()==null?0:companies.findById(account.companyId()).map(c->c.accessVersion()).orElse(-1L); }
    private boolean validCompany(Account account) {
        return account.companyId()==null || companies.findById(account.companyId()).map(c->c.status().equals("ACTIVE")).orElse(false);
    }
    private static StocklyPrincipal principal() {
        Object value=SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if(!(value instanceof StocklyPrincipal p)) throw invalidCredentials(); return p;
    }
    private static String deviceCookie(HttpServletRequest request) {
        if(request.getCookies()==null) return null;
        return Arrays.stream(request.getCookies()).filter(c->DEVICE_COOKIE.equals(c.getName())).map(Cookie::getValue).findFirst().orElse(null);
    }
    private static String randomToken(int bytes) {
        byte[] value=new byte[bytes]; RANDOM.nextBytes(value); return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
    private static void setDeviceCookie(HttpServletResponse response,String value,int seconds) {
        String header=DEVICE_COOKIE+"="+value+"; Path=/api/auth; Max-Age="+seconds+"; HttpOnly; Secure; SameSite=Lax";
        response.addHeader("Set-Cookie",header);
    }
    private static IdentityFailure invalidCode() { return new IdentityFailure(400,"INVALID_CODE","El código no es válido, venció o ya fue utilizado."); }
    private static IdentityFailure invalidRecovery() { return new IdentityFailure(400,"INVALID_RECOVERY","El enlace no es válido, venció o ya fue utilizado. Solicita uno nuevo."); }
    private static IdentityFailure invalidCredentials() { return new IdentityFailure(401,"UNAUTHENTICATED","Inicia sesión para continuar."); }
}
