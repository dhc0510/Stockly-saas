CREATE TABLE companies (
 id CHAR(36) NOT NULL PRIMARY KEY,
 commercial_name VARCHAR(150) NOT NULL,
 legal_name VARCHAR(150) NOT NULL,
 identification_type VARCHAR(12) NOT NULL,
 identification_normalized VARCHAR(12) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 contact_email VARCHAR(254) NOT NULL,
 phone VARCHAR(30), address VARCHAR(500),
 status VARCHAR(12) NOT NULL,
 created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0, access_version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT uq_company_identification UNIQUE (identification_normalized),
 CONSTRAINT chk_company_status CHECK (status IN ('ACTIVE','SUSPENDED')),
 CONSTRAINT chk_company_id_type CHECK (identification_type IN ('PHYSICAL','LEGAL','DIMEX','NITE')),
 INDEX ix_company_registration (created_at, id)
);
CREATE TABLE users (
 id CHAR(36) NOT NULL PRIMARY KEY,
 company_id CHAR(36),
 full_name VARCHAR(150) NOT NULL,
 username_normalized VARCHAR(30) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 email_normalized VARCHAR(254) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
 password_hash VARCHAR(255) NOT NULL,
 must_change_password BOOLEAN NOT NULL,
 role VARCHAR(30) NOT NULL,
 CONSTRAINT uq_user_username UNIQUE (username_normalized),
 CONSTRAINT uq_user_email UNIQUE (email_normalized),
 CONSTRAINT fk_user_company FOREIGN KEY (company_id) REFERENCES companies(id),
 CONSTRAINT chk_user_scope CHECK ((role = 'SAAS_ADMIN' AND company_id IS NULL) OR (role = 'COMPANY_ADMIN' AND company_id IS NOT NULL))
);
