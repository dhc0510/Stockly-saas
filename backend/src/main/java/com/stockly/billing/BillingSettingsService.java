package com.stockly.billing;

import com.stockly.identity.IdentityFailure;
import com.stockly.identity.StocklyPrincipal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Configuración de facturación de cada empresa (SPEC §4). Solo el administrador; los secretos nunca salen de aquí. */
@Service
public class BillingSettingsService {
    private static final Set<String> DOC_TYPES = Set.of("01", "03", "04");
    private final JdbcTemplate jdbc;
    private final BillingCrypto crypto;
    private final HaciendaClient hacienda;
    private final HaciendaEndpoints endpoints;

    BillingSettingsService(JdbcTemplate jdbc, BillingCrypto crypto, HaciendaClient hacienda, HaciendaEndpoints endpoints) {
        this.jdbc = jdbc; this.crypto = crypto; this.hacienda = hacienda; this.endpoints = endpoints;
    }

    @Transactional(readOnly = true)
    public Settings get(StocklyPrincipal actor) { return view(admin(actor)); }

    @Transactional
    public Settings saveCredentials(StocklyPrincipal actor, CredentialsInput input) {
        String company = admin(actor);
        requireEnabled();
        if (input == null) throw invalid("Completa las credenciales.");
        String username = required(input.username(), 120, "Escribe el usuario de API.");
        String password = required(input.password(), 200, "Escribe la contraseña de API.");
        String pin = required(input.pin(), 20, "Escribe el PIN de la llave.");
        byte[] keyStore;
        try { keyStore = Base64.getDecoder().decode(Objects.requireNonNullElse(input.keyStore(), "")); }
        catch (IllegalArgumentException e) { throw invalid("El archivo de la llave no es válido."); }
        SigningKey key = SigningKey.open(keyStore, pin.toCharArray());
        if (key.expiresAt().isBefore(Instant.now())) throw invalid("La llave criptográfica está vencida. Descarga una nueva en TRIBU-CR.");
        String environment = settingsRow(company).map(Row::environment).orElse("TEST");
        hacienda.verify(environment, new HaciendaClient.Credentials(username, password));
        ensureRow(company);
        jdbc.update("""
            UPDATE billing_settings SET api_username_enc=?,api_password_enc=?,key_store_enc=?,key_pin_enc=?,key_subject=?,
              key_expires_at=?,credentials_checked_at=?,updated_at=?,version=version+1 WHERE company_id=?
            """, crypto.encrypt(company, username.getBytes(StandardCharsets.UTF_8)), crypto.encrypt(company, password.getBytes(StandardCharsets.UTF_8)),
            crypto.encrypt(company, keyStore), crypto.encrypt(company, pin.getBytes(StandardCharsets.UTF_8)), key.subject(),
            Timestamp.from(key.expiresAt()), now(), now(), company);
        audit(company, actor, "BILLING_SETTINGS_UPDATED");
        return view(company);
    }

    @Transactional
    public Settings testConnection(StocklyPrincipal actor) {
        String company = admin(actor);
        var credentials = credentials(company).orElseThrow(() -> invalid("Primero guarda las credenciales de Hacienda."));
        hacienda.verify(settingsRow(company).orElseThrow().environment(), credentials.api());
        jdbc.update("UPDATE billing_settings SET credentials_checked_at=? WHERE company_id=?", now(), company);
        return view(company);
    }

    @Transactional
    public Settings saveIssuer(StocklyPrincipal actor, IssuerInput input) {
        String company = admin(actor);
        if (input == null) throw invalid("Completa los datos del emisor.");
        var errors = new LinkedHashMap<String, String>();
        String activity = clean(input.activityCode());
        if (activity == null || !activity.matches("[0-9]{6}")) errors.put("activityCode", "La actividad económica tiene 6 dígitos.");
        String province = clean(input.province()), canton = clean(input.canton()), district = clean(input.district());
        if (province == null || !province.matches("[1-7]")) errors.put("province", "Elige la provincia.");
        if (canton == null || !canton.matches("[0-9]{2}") || "00".equals(canton)) errors.put("canton", "El cantón tiene 2 dígitos.");
        if (district == null || !district.matches("[0-9]{2}") || "00".equals(district)) errors.put("district", "El distrito tiene 2 dígitos.");
        String signs = clean(input.otherSigns());
        if (signs == null || signs.length() < 5 || signs.length() > 250) errors.put("otherSigns", "Escribe otras señas (5 a 250 caracteres).");
        String email = clean(input.email());
        if (email == null || email.length() > 160 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) errors.put("email", "Escribe un correo válido.");
        if (!errors.isEmpty()) throw new BillingFailure(400, "BILLING_INVALID", "Revisa los datos del emisor.", errors);
        ensureRow(company);
        jdbc.update("""
            UPDATE billing_settings SET activity_code=?,province=?,canton=?,district=?,other_signs=?,email=?,updated_at=?,version=version+1
            WHERE company_id=?
            """, activity, province, canton, district, signs, email, now(), company);
        audit(company, actor, "BILLING_SETTINGS_UPDATED");
        return view(company);
    }

    /** Nota 3: si el comercio ya emitía con otra plataforma, mantiene su numeración. Solo antes del primer comprobante. */
    @Transactional
    public Settings setStartingNumber(StocklyPrincipal actor, String branchId, String docType, long lastUsed) {
        String company = admin(actor);
        if (!DOC_TYPES.contains(docType) || lastUsed < 0 || lastUsed > 9_999_999_998L) throw invalid("Revisa el tipo y el número.");
        String environment = settingsRow(company).map(Row::environment).orElse("TEST");
        requireBranch(company, branchId);
        Integer issued = jdbc.queryForObject("""
            SELECT COUNT(*) FROM fiscal_documents WHERE company_id=? AND branch_id=? AND environment=? AND doc_type=?
            """, Integer.class, company, branchId, environment, docType);
        if (issued != null && issued > 0) throw new BillingFailure(409, "BILLING_SEQUENCE_IN_USE", "Ya se emitieron comprobantes con esta numeración.");
        jdbc.update("""
            INSERT INTO fiscal_sequences(company_id,branch_id,environment,doc_type,last_number) VALUES (?,?,?,?,?)
            ON DUPLICATE KEY UPDATE last_number=VALUES(last_number)
            """, company, branchId, environment, docType, lastUsed);
        audit(company, actor, "BILLING_SETTINGS_UPDATED");
        return view(company);
    }

    @Transactional
    public Settings activateProduction(StocklyPrincipal actor) {
        String company = admin(actor);
        Settings current = view(company);
        if ("PROD".equals(current.environment())) return current;
        if (!current.requirements().credentials() || !current.requirements().issuer() || !current.requirements().testAccepted())
            throw new BillingFailure(409, "BILLING_NOT_READY", "Completa los requisitos antes de activar producción.");
        // Sin las direcciones de producción de Hacienda en el servidor, los comprobantes quedarían sin poder enviarse.
        if (!current.requirements().productionReady())
            throw new BillingFailure(409, "BILLING_PRODUCTION_UNAVAILABLE", "Este servidor aún no está configurado para el ambiente de producción de Hacienda.");
        jdbc.update("UPDATE billing_settings SET environment='PROD',production_since=?,updated_at=?,version=version+1 WHERE company_id=?",
            now(), now(), company);
        audit(company, actor, "BILLING_PRODUCTION_ENABLED");
        return view(company);
    }

    // ---------- Uso interno de la facturación ----------

    record Row(String environment, boolean credentials, String activity, String province, String canton, String district,
               String otherSigns, String email) {
        boolean issuerComplete() { return activity != null && province != null && canton != null && district != null && otherSigns != null && email != null; }
    }
    record Stored(HaciendaClient.Credentials api, SigningKey key) {}

    Optional<Row> settingsRow(String company) {
        return jdbc.query("""
            SELECT environment,api_username_enc IS NOT NULL AND key_store_enc IS NOT NULL,activity_code,province,canton,district,other_signs,email
            FROM billing_settings WHERE company_id=?
            """, (rs, n) -> new Row(rs.getString(1), rs.getBoolean(2), rs.getString(3), rs.getString(4), rs.getString(5),
            rs.getString(6), rs.getString(7), rs.getString(8)), company).stream().findFirst();
    }

    /** Descifra en memoria; los valores no se guardan en ningún otro lugar. */
    Optional<Stored> credentials(String company) {
        return jdbc.query("""
            SELECT api_username_enc,api_password_enc,key_store_enc,key_pin_enc FROM billing_settings
            WHERE company_id=? AND api_username_enc IS NOT NULL AND key_store_enc IS NOT NULL
            """, (rs, n) -> {
            String user = new String(crypto.decrypt(company, rs.getBytes(1)), StandardCharsets.UTF_8);
            String pass = new String(crypto.decrypt(company, rs.getBytes(2)), StandardCharsets.UTF_8);
            char[] pin = new String(crypto.decrypt(company, rs.getBytes(4)), StandardCharsets.UTF_8).toCharArray();
            return new Stored(new HaciendaClient.Credentials(user, pass), SigningKey.open(crypto.decrypt(company, rs.getBytes(3)), pin));
        }, company).stream().findFirst();
    }

    // ---------- Vista ----------

    public record Requirements(boolean serverReady, boolean credentials, boolean issuer, boolean testAccepted, boolean productionReady) {}
    public record Issuer(String name, String idType, String idNumber) {}
    public record Sequence(String docType, long lastNumber) {}
    public record BranchView(String id, String name, String fiscalCode, boolean active, List<Sequence> sequences) {}
    public record Settings(String environment, boolean billingEnabled, boolean hasCredentials, String keySubject, Instant keyExpiresAt,
                           Instant credentialsCheckedAt, Issuer issuer, String activityCode, String province, String canton,
                           String district, String otherSigns, String email, Instant productionSince,
                           List<BranchView> branches, Requirements requirements) {}
    public record CredentialsInput(String username, String password, String keyStore, String pin) {}
    public record IssuerInput(String activityCode, String province, String canton, String district, String otherSigns, String email) {}

    private Settings view(String company) {
        var issuer = jdbc.queryForObject("SELECT legal_name,identification_type,identification_normalized FROM companies WHERE id=?",
            (rs, n) -> new Issuer(rs.getString(1), FiscalNumbers.issuerType(rs.getString(2)), rs.getString(3)), company);
        record Base(String environment, boolean credentials, String subject, Instant expires, Instant checked, String activity,
                    String province, String canton, String district, String signs, String email, Instant production) {}
        Base base = jdbc.query("""
            SELECT environment,api_username_enc IS NOT NULL AND key_store_enc IS NOT NULL,key_subject,key_expires_at,credentials_checked_at,
              activity_code,province,canton,district,other_signs,email,production_since FROM billing_settings WHERE company_id=?
            """, (rs, n) -> new Base(rs.getString(1), rs.getBoolean(2), rs.getString(3), instant(rs.getTimestamp(4)),
            instant(rs.getTimestamp(5)), rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9), rs.getString(10),
            rs.getString(11), instant(rs.getTimestamp(12))), company).stream().findFirst()
            .orElse(new Base("TEST", false, null, null, null, null, null, null, null, null, null, null));
        var sequences = new HashMap<String, List<Sequence>>();
        jdbc.query("SELECT branch_id,doc_type,last_number FROM fiscal_sequences WHERE company_id=? AND environment=? ORDER BY doc_type",
            rs -> { sequences.computeIfAbsent(rs.getString(1), k -> new ArrayList<>()).add(new Sequence(rs.getString(2), rs.getLong(3))); },
            company, base.environment());
        var branches = jdbc.query("SELECT id,name,fiscal_code,active FROM inventory_branches WHERE company_id=? ORDER BY fiscal_code IS NULL,fiscal_code,name",
            (rs, n) -> new BranchView(rs.getString(1), rs.getString(2), rs.getString(3), rs.getBoolean(4),
                sequences.getOrDefault(rs.getString(1), List.of())), company);
        boolean accepted = Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM fiscal_documents WHERE company_id=? AND environment='TEST' AND status='ACCEPTED')
            """, Boolean.class, company));
        boolean issuerReady = base.activity() != null && base.province() != null && base.signs() != null && base.email() != null;
        boolean serverReady = crypto.enabled() && !endpoints.providerId().isEmpty();
        return new Settings(base.environment(), serverReady, base.credentials(), base.subject(), base.expires(), base.checked(), issuer,
            base.activity(), base.province(), base.canton(), base.district(), base.signs(), base.email(), base.production(), branches,
            new Requirements(serverReady, base.credentials() && base.checked() != null, issuerReady, accepted,
                endpoints.of("PROD").complete()));
    }

    // ---------- Apoyo ----------

    private void ensureRow(String company) {
        jdbc.update("INSERT IGNORE INTO billing_settings(company_id,environment,updated_at) VALUES (?, 'TEST', ?)", company, now());
    }

    private void requireBranch(String company, String branchId) {
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM inventory_branches WHERE company_id=? AND id=?)",
            Boolean.class, company, branchId)))
            throw new BillingFailure(404, "BRANCH_NOT_FOUND", "No se encontró la sucursal.");
    }

    private void requireEnabled() {
        if (!crypto.enabled()) throw new BillingFailure(503, "BILLING_DISABLED", "La facturación electrónica no está habilitada en este servidor.");
    }

    private void audit(String company, StocklyPrincipal actor, String action) {
        jdbc.update("""
            INSERT INTO identity_audit_events(id,company_id,actor_id,action_key,target_type,target_id,occurred_at)
            VALUES (?,?,?,?,'BILLING',?,UTC_TIMESTAMP(6))
            """, UUID.randomUUID().toString(), company, actor.accountId(), action, company);
    }

    static String admin(StocklyPrincipal actor) {
        if (actor == null || actor.companyId() == null || actor.mustChangePassword() || !"COMPANY_ADMIN".equals(actor.role()))
            throw new IdentityFailure(403, "FORBIDDEN", "Solo el administrador de la empresa configura la facturación.");
        return actor.companyId();
    }

    private static String required(String value, int max, String message) {
        String clean = clean(value);
        if (clean == null) throw invalid(message);
        if (clean.length() > max) throw invalid("Un dato excede el máximo de " + max + " caracteres.");
        return clean;
    }

    private static String clean(String value) {
        if (value == null) return null;
        String clean = value.strip();
        return clean.isEmpty() ? null : clean;
    }

    private static BillingFailure invalid(String message) { return new BillingFailure(400, "BILLING_INVALID", message); }
    private static Timestamp now() { return Timestamp.from(Instant.now()); }
    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
}
