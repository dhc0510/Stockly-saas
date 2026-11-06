ALTER TABLE companies
 ADD COLUMN idle_timeout_minutes BIGINT NULL DEFAULT 120;

ALTER TABLE users
 ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE,
 ADD COLUMN access_version BIGINT NOT NULL DEFAULT 0,
 ADD COLUMN role_id CHAR(36) NULL,
 ADD COLUMN email_verified_at DATETIME(6) NULL,
 ADD COLUMN temporary_password_expires_at DATETIME(6) NULL,
 ADD COLUMN failed_password_attempts INT NOT NULL DEFAULT 0,
 ADD COLUMN password_locked_until DATETIME(6) NULL,
 ADD COLUMN failed_device_attempts INT NOT NULL DEFAULT 0,
 ADD COLUMN device_locked_until DATETIME(6) NULL,
 DROP CHECK chk_user_scope,
 ADD CONSTRAINT chk_user_scope CHECK (
   (role = 'SAAS_ADMIN' AND company_id IS NULL AND role_id IS NULL)
   OR (role = 'COMPANY_ADMIN' AND company_id IS NOT NULL AND role_id IS NULL)
   OR (role = 'WORKER' AND company_id IS NOT NULL AND role_id IS NOT NULL)
 );

CREATE TABLE company_roles (
 id CHAR(36) NOT NULL PRIMARY KEY,
 company_id CHAR(36) NOT NULL,
 name VARCHAR(80) NOT NULL,
 created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT uq_company_role_name UNIQUE (company_id, name),
 CONSTRAINT uq_company_role_id UNIQUE (company_id, id),
 CONSTRAINT fk_role_company FOREIGN KEY (company_id) REFERENCES companies(id)
);

CREATE TABLE permission_modules (
 module_key VARCHAR(80) NOT NULL PRIMARY KEY,
 label VARCHAR(120) NOT NULL
);

CREATE TABLE special_permission_catalog (
 permission_key VARCHAR(80) NOT NULL PRIMARY KEY,
 label VARCHAR(120) NOT NULL
);
INSERT INTO special_permission_catalog(permission_key,label) VALUES
 ('MANAGE_USERS','Gestionar usuarios'),
 ('MANAGE_PERMISSIONS','Gestionar roles y permisos'),
 ('IRREVERSIBLE_ACTIONS','Realizar acciones irreversibles');

ALTER TABLE users
 ADD CONSTRAINT fk_user_role_company FOREIGN KEY (company_id, role_id) REFERENCES company_roles(company_id, id);

CREATE TABLE role_module_permissions (
 role_id CHAR(36) NOT NULL,
 module_key VARCHAR(80) NOT NULL,
 access_level VARCHAR(16) NOT NULL,
 PRIMARY KEY (role_id, module_key),
 CONSTRAINT fk_module_permission_role FOREIGN KEY (role_id) REFERENCES company_roles(id) ON DELETE CASCADE,
 CONSTRAINT fk_module_permission_catalog FOREIGN KEY (module_key) REFERENCES permission_modules(module_key),
 CONSTRAINT chk_module_access_level CHECK (access_level IN ('NONE','READ','OPERATE','ADMIN'))
);

CREATE TABLE role_special_permissions (
 role_id CHAR(36) NOT NULL,
 permission_key VARCHAR(80) NOT NULL,
 PRIMARY KEY (role_id, permission_key),
 CONSTRAINT fk_special_permission_role FOREIGN KEY (role_id) REFERENCES company_roles(id) ON DELETE CASCADE,
 CONSTRAINT fk_special_permission_catalog FOREIGN KEY (permission_key) REFERENCES special_permission_catalog(permission_key)
);

CREATE TABLE auth_challenges (
 id CHAR(36) NOT NULL PRIMARY KEY,
 user_id CHAR(36) NOT NULL,
 code_hash VARCHAR(255) NOT NULL,
 expires_at DATETIME(6) NOT NULL,
 attempts INT NOT NULL DEFAULT 0,
 locked_until DATETIME(6) NULL,
 consumed_at DATETIME(6) NULL,
 last_sent_at DATETIME(6) NOT NULL,
 created_at DATETIME(6) NOT NULL,
 CONSTRAINT fk_auth_challenge_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
 INDEX ix_auth_challenge_user (user_id, created_at)
);

CREATE TABLE password_reset_tokens (
 id CHAR(36) NOT NULL PRIMARY KEY,
 user_id CHAR(36) NOT NULL,
 token_hash VARCHAR(255) NOT NULL,
 expires_at DATETIME(6) NOT NULL,
 consumed_at DATETIME(6) NULL,
 created_at DATETIME(6) NOT NULL,
 CONSTRAINT fk_password_reset_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
 INDEX ix_password_reset_user (user_id, created_at)
);

CREATE TABLE trusted_devices (
 id CHAR(36) NOT NULL PRIMARY KEY,
 user_id CHAR(36) NOT NULL,
 token_hash VARCHAR(255) NOT NULL,
 expires_at DATETIME(6) NOT NULL,
 revoked_at DATETIME(6) NULL,
 created_at DATETIME(6) NOT NULL,
 CONSTRAINT fk_trusted_device_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
 INDEX ix_trusted_device_user (user_id, expires_at)
);

CREATE TABLE auth_email_sends (
 id CHAR(36) NOT NULL PRIMARY KEY,
 user_id CHAR(36) NOT NULL,
 purpose VARCHAR(24) NOT NULL,
 created_at DATETIME(6) NOT NULL,
 CONSTRAINT fk_auth_email_send_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
 CONSTRAINT chk_auth_email_send_purpose CHECK (purpose IN ('DEVICE_CODE','RECOVERY')),
 INDEX ix_auth_email_send_rate (user_id, purpose, created_at)
);

CREATE TABLE identity_audit_events (
 id CHAR(36) NOT NULL PRIMARY KEY,
 company_id CHAR(36) NOT NULL,
 actor_id CHAR(36) NOT NULL,
 action_key VARCHAR(48) NOT NULL,
 target_type VARCHAR(32) NOT NULL,
 target_id CHAR(36) NOT NULL,
 occurred_at DATETIME(6) NOT NULL,
 CONSTRAINT fk_identity_audit_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE,
 INDEX ix_identity_audit_company_time (company_id, occurred_at, id),
 INDEX ix_identity_audit_company_action_time (company_id, action_key, occurred_at)
);
