-- STK-018: suscripciones del SaaS. Stockly no procesa dinero: los pagos se registran a mano y cada uno genera
-- una factura electrónica de Stockly (emisor propio, mismo motor que STK-013). Todo en colones con IVA incluido.

CREATE TABLE saas_settings (
 id TINYINT NOT NULL PRIMARY KEY,
 trial_days INT NOT NULL,
 grace_days INT NOT NULL,
 notice_days INT NOT NULL,
 trial_products INT NOT NULL,
 trial_users INT NOT NULL,
 trial_branches INT NOT NULL,
 payment_instructions VARCHAR(2000) NULL,
 updated_at DATETIME(6) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 CHECK (id = 1),
 CHECK (trial_days BETWEEN 1 AND 90 AND grace_days BETWEEN 0 AND 60 AND notice_days BETWEEN 1 AND 30),
 CHECK (trial_products >= 1 AND trial_users >= 1 AND trial_branches >= 1)
);
INSERT INTO saas_settings(id,trial_days,grace_days,notice_days,trial_products,trial_users,trial_branches,updated_at)
VALUES (1,14,7,7,50,2,1,UTC_TIMESTAMP(6));

-- Emisor de Stockly para facturar las suscripciones. Credenciales y llave cifradas (ADR-007, clave STOCKLY_BILLING_KEY).
CREATE TABLE saas_billing_settings (
 id TINYINT NOT NULL PRIMARY KEY,
 legal_name VARCHAR(100) NULL,
 id_type CHAR(2) CHARACTER SET ascii NULL,
 id_number VARCHAR(12) CHARACTER SET ascii NULL,
 email VARCHAR(160) NULL,
 activity_code CHAR(6) CHARACTER SET ascii NULL,
 cabys_code CHAR(13) CHARACTER SET ascii NULL,
 province CHAR(1) CHARACTER SET ascii NULL,
 canton CHAR(2) CHARACTER SET ascii NULL,
 district CHAR(2) CHARACTER SET ascii NULL,
 other_signs VARCHAR(250) NULL,
 environment VARCHAR(10) NOT NULL DEFAULT 'TEST',
 api_username_enc VARBINARY(1024) NULL,
 api_password_enc VARBINARY(1024) NULL,
 key_store_enc MEDIUMBLOB NULL,
 key_pin_enc VARBINARY(256) NULL,
 key_subject VARCHAR(300) NULL,
 key_expires_at DATETIME(6) NULL,
 credentials_checked_at DATETIME(6) NULL,
 updated_at DATETIME(6) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 CHECK (id = 1),
 CHECK (environment IN ('TEST','PROD')),
 CHECK (activity_code IS NULL OR activity_code REGEXP '^[0-9]{6}$'),
 CHECK (cabys_code IS NULL OR cabys_code REGEXP '^[0-9]{13}$')
);
INSERT INTO saas_billing_settings(id,updated_at) VALUES (1,UTC_TIMESTAMP(6));

CREATE TABLE saas_fiscal_sequences (
 environment VARCHAR(10) NOT NULL,
 doc_type CHAR(2) NOT NULL,
 last_number BIGINT NOT NULL DEFAULT 0,
 PRIMARY KEY (environment, doc_type),
 CHECK (doc_type IN ('01','03') AND last_number BETWEEN 0 AND 9999999999)
);

CREATE TABLE subscription_plans (
 id CHAR(36) NOT NULL PRIMARY KEY,
 name VARCHAR(80) NOT NULL,
 monthly_price DECIMAL(15,2) NOT NULL,
 annual_price DECIMAL(15,2) NULL,
 description VARCHAR(500) NULL,
 active BOOLEAN NOT NULL DEFAULT TRUE,
 created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 UNIQUE KEY uq_subscription_plan_name (name),
 CHECK (monthly_price > 0 AND (annual_price IS NULL OR annual_price > 0))
);

-- Una suscripción por empresa. «state» es el último estado calculado (para listar y filtrar); el acceso siempre
-- se recalcula con las fechas para no depender de la tarea programada.
CREATE TABLE company_subscriptions (
 company_id CHAR(36) NOT NULL PRIMARY KEY,
 state VARCHAR(12) NOT NULL,
 plan_id CHAR(36) NULL,
 cycle VARCHAR(8) NULL,
 trial_ends_on DATE NOT NULL,
 paid_until DATE NULL,
 cancelled_at DATETIME(6) NULL,
 cancelled_by CHAR(36) NULL,
 cancel_reason VARCHAR(150) NULL,
 created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE,
 FOREIGN KEY (plan_id) REFERENCES subscription_plans(id),
 INDEX ix_company_subscriptions_state (state),
 CHECK (state IN ('TRIAL','ACTIVE','GRACE','SUSPENDED','CANCELLED')),
 CHECK (cycle IS NULL OR cycle IN ('MONTHLY','ANNUAL'))
);

CREATE TABLE subscription_payments (
 id CHAR(36) NOT NULL PRIMARY KEY,
 company_id CHAR(36) NOT NULL,
 plan_id CHAR(36) NOT NULL,
 plan_name VARCHAR(80) NOT NULL,
 cycle VARCHAR(8) NOT NULL,
 periods INT NOT NULL,
 amount DECIMAL(15,2) NOT NULL,
 paid_on DATE NOT NULL,
 method VARCHAR(16) NOT NULL,
 reference VARCHAR(60) NULL,
 covers_from DATE NOT NULL,
 covers_until DATE NOT NULL,
 request_key CHAR(36) NOT NULL,
 created_by CHAR(36) NOT NULL,
 created_by_name VARCHAR(150) NOT NULL,
 created_at DATETIME(6) NOT NULL,
 voided_at DATETIME(6) NULL,
 voided_by_name VARCHAR(150) NULL,
 void_reason VARCHAR(150) NULL,
 UNIQUE KEY uq_subscription_payment_request (company_id, request_key),
 INDEX ix_subscription_payments_company (company_id, created_at),
 INDEX ix_subscription_payments_paid (paid_on),
 FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE,
 FOREIGN KEY (plan_id) REFERENCES subscription_plans(id),
 CHECK (cycle IN ('MONTHLY','ANNUAL')),
 CHECK (periods BETWEEN 1 AND 12 AND amount > 0 AND covers_until > covers_from),
 CHECK (method IN ('SINPE','TRANSFER','CASH')),
 CHECK (method = 'CASH' OR reference IS NOT NULL),
 CHECK ((voided_at IS NULL) = (void_reason IS NULL))
);

-- Avisos enviados: uno por tipo y vencimiento (SPEC «Avisos»).
CREATE TABLE subscription_notices (
 company_id CHAR(36) NOT NULL,
 kind VARCHAR(12) NOT NULL,
 due_on DATE NOT NULL,
 sent_at DATETIME(6) NOT NULL,
 PRIMARY KEY (company_id, kind, due_on),
 FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE,
 CHECK (kind IN ('BEFORE','EXPIRED','SUSPENDED'))
);

-- Facturas y notas de crédito de Stockly. Sin FK a la empresa ni al pago: se conservan por obligación legal
-- aunque la empresa se elimine (STK-004). El receptor queda copiado.
CREATE TABLE saas_fiscal_documents (
 id CHAR(36) NOT NULL PRIMARY KEY,
 company_id CHAR(36) NOT NULL,
 payment_id CHAR(36) NOT NULL,
 doc_type CHAR(2) NOT NULL,
 environment VARCHAR(10) NOT NULL,
 consecutive CHAR(20) CHARACTER SET ascii NOT NULL,
 numeric_key CHAR(50) CHARACTER SET ascii NOT NULL,
 issued_at DATETIME(6) NOT NULL,
 reference_id CHAR(36) NULL,
 replaced_by CHAR(36) NULL,
 receiver_name VARCHAR(150) NOT NULL,
 receiver_id_type CHAR(2) CHARACTER SET ascii NOT NULL,
 receiver_id_number VARCHAR(12) CHARACTER SET ascii NOT NULL,
 receiver_email VARCHAR(254) NULL,
 detail VARCHAR(200) NOT NULL,
 amount DECIMAL(15,2) NOT NULL,
 reason VARCHAR(180) NULL,
 status VARCHAR(12) NOT NULL,
 status_reason VARCHAR(1000) NULL,
 attempts INT NOT NULL DEFAULT 0,
 next_attempt_at DATETIME(6) NULL,
 sent_at DATETIME(6) NULL,
 resolved_at DATETIME(6) NULL,
 signed_xml MEDIUMBLOB NULL,
 pdf MEDIUMBLOB NULL,
 response_xml MEDIUMBLOB NULL,
 mail_status VARCHAR(10) NOT NULL DEFAULT 'NONE',
 created_at DATETIME(6) NOT NULL,
 UNIQUE KEY uq_saas_fiscal_key (numeric_key),
 UNIQUE KEY uq_saas_fiscal_consecutive (environment, consecutive),
 INDEX ix_saas_fiscal_payment (payment_id, doc_type),
 INDEX ix_saas_fiscal_company (company_id, created_at),
 INDEX ix_saas_fiscal_dispatch (status, next_attempt_at),
 FOREIGN KEY (reference_id) REFERENCES saas_fiscal_documents(id),
 CHECK (doc_type IN ('01','03')),
 CHECK (environment IN ('TEST','PROD')),
 CHECK (status IN ('PENDING','PROCESSING','ACCEPTED','REJECTED')),
 CHECK (mail_status IN ('NONE','PENDING','SENT','FAILED')),
 CHECK ((doc_type = '03') = (reference_id IS NOT NULL))
);

-- Actividad del administrador de Stockly (la de empresas exige company_id).
CREATE TABLE saas_audit_events (
 id CHAR(36) NOT NULL PRIMARY KEY,
 actor_id CHAR(36) NULL,
 actor_name VARCHAR(150) NOT NULL,
 action_key VARCHAR(48) NOT NULL,
 company_id CHAR(36) NULL,
 target_id CHAR(36) NULL,
 detail VARCHAR(300) NULL,
 occurred_at DATETIME(6) NOT NULL,
 INDEX ix_saas_audit_time (occurred_at),
 INDEX ix_saas_audit_company (company_id, occurred_at)
);

-- Empresas existentes: empiezan en prueba desde la puesta en marcha (SPEC, regla aprobada el 2026-11-09).
INSERT INTO company_subscriptions(company_id,state,trial_ends_on,created_at,updated_at)
SELECT id,'TRIAL',DATE(DATE_SUB(UTC_TIMESTAMP(), INTERVAL 6 HOUR)) + INTERVAL 14 DAY,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6) FROM companies;
