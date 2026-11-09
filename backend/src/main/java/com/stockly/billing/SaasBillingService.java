package com.stockly.billing;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * STK-018 · Emisor de Stockly para facturar las suscripciones. Mismas validaciones, cifrado (ADR-007) y verificación con
 * Hacienda que la facturación de cada comercio (STK-013); los secretos nunca salen de este servicio.
 */
@Service
public class SaasBillingService {
    /** Dato asociado del cifrado: un valor de una empresa no se puede descifrar como de Stockly ni al revés. */
    static final String AAD = "stockly-saas";
    private static final Map<String, String> ID_PATTERNS = Map.of("01", "[1-9][0-9]{8}", "02", "[1-9][0-9]{9}", "03", "[1-9][0-9]{10,11}", "04", "[0-9]{10}");
    private final JdbcTemplate jdbc;
    private final BillingCrypto crypto;
    private final HaciendaClient hacienda;
    private final HaciendaEndpoints endpoints;

    SaasBillingService(JdbcTemplate jdbc, BillingCrypto crypto, HaciendaClient hacienda, HaciendaEndpoints endpoints) {
        this.jdbc = jdbc; this.crypto = crypto; this.hacienda = hacienda; this.endpoints = endpoints;
    }

    public record Settings(String legalName, String idType, String idNumber, String email, String activityCode, String cabysCode,
                           String province, String canton, String district, String otherSigns, String environment,
                           boolean hasCredentials, String keySubject, Instant keyExpiresAt, Instant credentialsCheckedAt,
                           boolean serverReady, boolean issuerComplete, boolean productionReady, long version) {}
    public record IssuerInput(String legalName, String idType, String idNumber, String email, String activityCode, String cabysCode,
                              String province, String canton, String district, String otherSigns) {}
    public record CredentialsInput(String username, String password, String keyStore, String pin) {}

    @Transactional(readOnly = true)
    public Settings settings() {
        return jdbc.queryForObject("""
            SELECT legal_name,id_type,id_number,email,activity_code,cabys_code,province,canton,district,other_signs,environment,
              api_username_enc IS NOT NULL AND key_store_enc IS NOT NULL,key_subject,key_expires_at,credentials_checked_at,version
            FROM saas_billing_settings WHERE id=1
            """, (rs, n) -> {
            boolean issuer = rs.getString(1) != null && rs.getString(2) != null && rs.getString(3) != null && rs.getString(4) != null
                && rs.getString(5) != null && rs.getString(6) != null && rs.getString(7) != null && rs.getString(10) != null;
            return new Settings(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6),
                rs.getString(7), rs.getString(8), rs.getString(9), rs.getString(10), rs.getString(11), rs.getBoolean(12), rs.getString(13),
                instant(rs.getTimestamp(14)), instant(rs.getTimestamp(15)), crypto.enabled() && !endpoints.providerId().isEmpty(),
                issuer, endpoints.of("PROD").complete(), rs.getLong(16));
        });
    }

    @Transactional
    public Settings saveIssuer(IssuerInput input) {
        if (input == null) throw invalid("Completa los datos del emisor.");
        var errors = new LinkedHashMap<String, String>();
        String name = clean(input.legalName());
        if (name == null || name.length() < 2 || name.length() > 100) errors.put("legalName", "Escribe la razón social (2 a 100 caracteres).");
        String type = clean(input.idType()), number = digits(input.idNumber());
        if (type == null || !ID_PATTERNS.containsKey(type)) errors.put("idType", "Elige el tipo de identificación.");
        else if (number == null || !number.matches(ID_PATTERNS.get(type))) errors.put("idNumber", "Revisa el número de identificación.");
        String email = clean(input.email());
        if (email == null || email.length() > 160 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) errors.put("email", "Escribe un correo válido.");
        String activity = clean(input.activityCode());
        if (activity == null || !activity.matches("[0-9]{6}")) errors.put("activityCode", "La actividad económica tiene 6 dígitos.");
        String cabys = clean(input.cabysCode());
        if (cabys == null || !cabys.matches("[0-9]{13}")) errors.put("cabysCode", "El código CABYS tiene 13 dígitos.");
        String province = clean(input.province()), canton = clean(input.canton()), district = clean(input.district());
        if (province == null || !province.matches("[1-7]")) errors.put("province", "Elige la provincia.");
        if (canton == null || !canton.matches("[0-9]{2}") || "00".equals(canton)) errors.put("canton", "El cantón tiene 2 dígitos.");
        if (district == null || !district.matches("[0-9]{2}") || "00".equals(district)) errors.put("district", "El distrito tiene 2 dígitos.");
        String signs = clean(input.otherSigns());
        if (signs == null || signs.length() < 5 || signs.length() > 250) errors.put("otherSigns", "Escribe otras señas (5 a 250 caracteres).");
        if (!errors.isEmpty()) throw new BillingFailure(400, "BILLING_INVALID", "Revisa los datos del emisor.", errors);
        jdbc.update("""
            UPDATE saas_billing_settings SET legal_name=?,id_type=?,id_number=?,email=?,activity_code=?,cabys_code=?,province=?,canton=?,
              district=?,other_signs=?,updated_at=?,version=version+1 WHERE id=1
            """, name, type, number, email, activity, cabys, province, canton, district, signs, now());
        return settings();
    }

    @Transactional
    public Settings saveCredentials(CredentialsInput input) {
        if (!crypto.enabled()) throw new BillingFailure(503, "BILLING_DISABLED", "La facturación electrónica no está habilitada en este servidor.");
        if (input == null) throw invalid("Completa las credenciales.");
        String username = required(input.username(), 120, "Escribe el usuario de API.");
        String password = required(input.password(), 200, "Escribe la contraseña de API.");
        String pin = required(input.pin(), 20, "Escribe el PIN de la llave.");
        byte[] keyStore;
        try { keyStore = Base64.getDecoder().decode(Objects.requireNonNullElse(input.keyStore(), "")); }
        catch (IllegalArgumentException e) { throw invalid("El archivo de la llave no es válido."); }
        SigningKey key = SigningKey.open(keyStore, pin.toCharArray());
        if (key.expiresAt().isBefore(Instant.now())) throw invalid("La llave criptográfica está vencida. Descarga una nueva en TRIBU-CR.");
        hacienda.verify(settings().environment(), new HaciendaClient.Credentials(username, password));
        jdbc.update("""
            UPDATE saas_billing_settings SET api_username_enc=?,api_password_enc=?,key_store_enc=?,key_pin_enc=?,key_subject=?,key_expires_at=?,
              credentials_checked_at=?,updated_at=?,version=version+1 WHERE id=1
            """, crypto.encrypt(AAD, username.getBytes(StandardCharsets.UTF_8)), crypto.encrypt(AAD, password.getBytes(StandardCharsets.UTF_8)),
            crypto.encrypt(AAD, keyStore), crypto.encrypt(AAD, pin.getBytes(StandardCharsets.UTF_8)), key.subject(),
            Timestamp.from(key.expiresAt()), now(), now());
        return settings();
    }

    @Transactional
    public Settings testConnection() {
        var stored = credentials().orElseThrow(() -> invalid("Primero guarda las credenciales de Hacienda."));
        hacienda.verify(settings().environment(), stored.api());
        jdbc.update("UPDATE saas_billing_settings SET credentials_checked_at=? WHERE id=1", now());
        return settings();
    }

    /** Cambia el ambiente. Producción solo si el servidor la tiene configurada y hay credenciales verificadas. */
    @Transactional
    public Settings environment(String environment) {
        if (!"TEST".equals(environment) && !"PROD".equals(environment)) throw invalid("Elige el ambiente.");
        Settings current = settings();
        if ("PROD".equals(environment) && (!current.productionReady() || !current.hasCredentials() || current.credentialsCheckedAt() == null))
            throw new BillingFailure(409, "BILLING_NOT_READY", "Verifica las credenciales y la configuración de producción antes de activarla.");
        jdbc.update("UPDATE saas_billing_settings SET environment=?,updated_at=?,version=version+1 WHERE id=1", environment, now());
        return settings();
    }

    // ---------- Comprobantes ----------

    public record Document(String id, String paymentId, String docType, String environment, String consecutive, String key, Instant issuedAt,
                           String status, String statusReason, String mailStatus) {}

    @Transactional(readOnly = true)
    public List<Document> documents(String companyId) {
        return jdbc.query("""
            SELECT id,payment_id,doc_type,environment,consecutive,numeric_key,issued_at,status,status_reason,mail_status
            FROM saas_fiscal_documents WHERE company_id=? ORDER BY created_at,id
            """, (rs, n) -> new Document(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6),
            rs.getTimestamp(7).toInstant(), rs.getString(8), rs.getString(9), rs.getString(10)), companyId);
    }

    public record Counters(int accepted, int problems, int pending) {}

    @Transactional(readOnly = true)
    public Counters counters(Instant from, Instant to) {
        return jdbc.queryForObject("""
            SELECT COALESCE(SUM(status='ACCEPTED'),0),COALESCE(SUM(status='REJECTED'),0),COALESCE(SUM(status IN ('PENDING','PROCESSING')),0)
            FROM saas_fiscal_documents WHERE created_at>=? AND created_at<?
            """, (rs, n) -> new Counters(rs.getInt(1), rs.getInt(2), rs.getInt(3)), Timestamp.from(from), Timestamp.from(to));
    }

    public record File(String name, String contentType, byte[] content) {}

    /** PDF o XML de un comprobante de Stockly; {@code companyId} limita la consulta al comercio que lo pide. */
    @Transactional(readOnly = true)
    public File file(String documentId, String companyId, String kind) {
        var row = jdbc.query("SELECT numeric_key,pdf,signed_xml,company_id FROM saas_fiscal_documents WHERE id=?",
            (rs, n) -> new Object[]{rs.getString(1), rs.getBytes(2), rs.getBytes(3), rs.getString(4)}, documentId).stream().findFirst()
            .filter(r -> companyId == null || companyId.equals(r[3]))
            .orElseThrow(() -> new BillingFailure(404, "FISCAL_NOT_FOUND", "No se encontró el comprobante."));
        byte[] content = "xml".equals(kind) ? (byte[]) row[2] : (byte[]) row[1];
        if (content == null) throw new BillingFailure(409, "FISCAL_NOT_SIGNED", "El comprobante aún no se ha generado.");
        return "xml".equals(kind) ? new File(row[0] + ".xml", "application/xml", content) : new File(row[0] + ".pdf", "application/pdf", content);
    }

    /**
     * Reintenta ya un comprobante pendiente. Uno rechazado no se reenvía con la misma clave: se emite uno nuevo (otro número)
     * con los datos actuales del emisor y del comercio, y el rechazado queda reemplazado, como en STK-013.
     */
    @Transactional
    public void retry(String documentId) {
        var row = jdbc.query("SELECT payment_id,doc_type,reference_id,status,replaced_by FROM saas_fiscal_documents WHERE id=? FOR UPDATE",
            (rs, n) -> new String[]{rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5)}, documentId)
            .stream().findFirst().orElseThrow(() -> new BillingFailure(404, "FISCAL_NOT_FOUND", "No se encontró el comprobante."));
        if ("PENDING".equals(row[3]) || "PROCESSING".equals(row[3])) {
            jdbc.update("UPDATE saas_fiscal_documents SET next_attempt_at=? WHERE id=?", now(), documentId);
            return;
        }
        if (!"REJECTED".equals(row[3]) || row[4] != null) throw new BillingFailure(409, "FISCAL_NOT_RETRYABLE", "Este comprobante no se puede reintentar.");
        String replacement = createDocument(row[0], row[1], row[2]);
        if (replacement == null) throw new BillingFailure(409, "BILLING_NOT_READY", "Completa los datos del emisor de Stockly antes de reintentar.");
        jdbc.update("UPDATE saas_fiscal_documents SET replaced_by=? WHERE id=?", replacement, documentId);
    }

    /**
     * Crea una factura (01) o nota de crédito (03) pendiente para un pago, con su consecutivo y clave. Devuelve null si faltan
     * los datos del emisor: el despachador vuelve a intentarlo cuando se completen. Llamar dentro de una transacción.
     */
    String createDocument(String paymentId, String docType, String referenceId) {
        var issuer = jdbc.query("SELECT id_number,environment FROM saas_billing_settings WHERE id=1 AND legal_name IS NOT NULL AND id_number IS NOT NULL"
            + " AND activity_code IS NOT NULL AND cabys_code IS NOT NULL AND province IS NOT NULL AND other_signs IS NOT NULL AND email IS NOT NULL",
            (rs, n) -> new String[]{rs.getString(1), rs.getString(2)}).stream().findFirst().orElse(null);
        if (issuer == null) return null;
        record Payment(String company, String plan, String cycle, int periods, java.math.BigDecimal amount, java.time.LocalDate from,
                       java.time.LocalDate until, String reason) {}
        Payment p = jdbc.queryForObject("""
            SELECT company_id,plan_name,cycle,periods,amount,covers_from,covers_until,void_reason FROM subscription_payments WHERE id=?
            """, (rs, n) -> new Payment(rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getBigDecimal(5),
            rs.getDate(6).toLocalDate(), rs.getDate(7).toLocalDate(), rs.getString(8)), paymentId);
        var receiver = jdbc.query("SELECT legal_name,identification_type,identification_normalized,contact_email FROM companies WHERE id=?",
            (rs, n) -> new String[]{rs.getString(1), FiscalNumbers.issuerType(rs.getString(2)), rs.getString(3), rs.getString(4)}, p.company())
            .stream().findFirst().orElse(null);
        if (receiver == null) return null;
        var day = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String detail = "Suscripción Stockly · plan " + p.plan() + " · " + ("ANNUAL".equals(p.cycle()) ? "anual" : "mensual") + " × " + p.periods()
            + " (" + day.format(p.from()) + " al " + day.format(p.until()) + ")";
        // La nota de crédito va en el mismo ambiente que la factura que anula.
        String environment = referenceId == null ? issuer[1]
            : jdbc.queryForObject("SELECT environment FROM saas_fiscal_documents WHERE id=?", String.class, referenceId);
        jdbc.update("INSERT IGNORE INTO saas_fiscal_sequences(environment,doc_type,last_number) VALUES (?,?,0)", environment, docType);
        long number = jdbc.queryForObject("SELECT last_number FROM saas_fiscal_sequences WHERE environment=? AND doc_type=? FOR UPDATE",
            Long.class, environment, docType) + 1;
        jdbc.update("UPDATE saas_fiscal_sequences SET last_number=? WHERE environment=? AND doc_type=?", number, environment, docType);
        Instant issuedAt = Instant.now();
        String consecutive = FiscalNumbers.consecutive("001", docType, number);
        String key = FiscalNumbers.key(issuedAt, issuer[0], consecutive, '1', FiscalNumbers.securityCode());
        String id = UUID.randomUUID().toString();
        jdbc.update("""
            INSERT INTO saas_fiscal_documents(id,company_id,payment_id,doc_type,environment,consecutive,numeric_key,issued_at,reference_id,
              receiver_name,receiver_id_type,receiver_id_number,receiver_email,detail,amount,reason,status,next_attempt_at,created_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'PENDING',?,?)
            """, id, p.company(), paymentId, docType, environment, consecutive, key, Timestamp.from(issuedAt), referenceId,
            receiver[0], receiver[1], receiver[2], receiver[3], detail.length() > 200 ? detail.substring(0, 200) : detail, p.amount(),
            "03".equals(docType) ? Objects.requireNonNullElse(p.reason(), "Anulación del pago de suscripción") : null, now(), now());
        return id;
    }

    // ---------- Uso interno ----------

    record Stored(HaciendaClient.Credentials api, byte[] keyStore, char[] pin) {}

    Optional<Stored> credentials() {
        return jdbc.query("""
            SELECT api_username_enc,api_password_enc,key_store_enc,key_pin_enc FROM saas_billing_settings
            WHERE id=1 AND api_username_enc IS NOT NULL AND key_store_enc IS NOT NULL
            """, (rs, n) -> new Stored(new HaciendaClient.Credentials(new String(crypto.decrypt(AAD, rs.getBytes(1)), StandardCharsets.UTF_8),
                new String(crypto.decrypt(AAD, rs.getBytes(2)), StandardCharsets.UTF_8)), crypto.decrypt(AAD, rs.getBytes(3)),
            new String(crypto.decrypt(AAD, rs.getBytes(4)), StandardCharsets.UTF_8).toCharArray())).stream().findFirst();
    }

    private static BillingFailure invalid(String message) { return new BillingFailure(400, "BILLING_INVALID", message); }
    private static String required(String value, int max, String message) {
        String clean = clean(value);
        if (clean == null) throw invalid(message);
        if (clean.length() > max) throw invalid("Un dato excede el máximo de " + max + " caracteres.");
        return clean;
    }
    private static String clean(String value) { if (value == null) return null; String c = value.strip(); return c.isEmpty() ? null : c; }
    private static String digits(String value) { String c = clean(value); return c == null ? null : c.replaceAll("[\\s-]", ""); }
    private static Timestamp now() { return Timestamp.from(Instant.now()); }
    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
}
