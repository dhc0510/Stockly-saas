-- STK-013: facturación electrónica directa con Hacienda (Anexos y Estructuras v4.4).
-- Los datos fiscales del catálogo (CABYS y tarifa) van en V13, coordinada con STK-006.

-- Identificación del cliente para factura electrónica (nota 4: 01 física, 02 jurídica, 03 DIMEX, 04 NITE).
ALTER TABLE sales
 ADD COLUMN customer_id_type CHAR(2) NULL,
 ADD COLUMN customer_id_number VARCHAR(12) CHARACTER SET ascii NULL,
 ADD CONSTRAINT chk_sale_customer_id CHECK (
   (customer_id_type IS NULL AND customer_id_number IS NULL)
   OR (customer_id_type = '01' AND customer_id_number REGEXP '^[0-9]{9}$')
   OR (customer_id_type = '02' AND customer_id_number REGEXP '^[0-9]{10}$')
   OR (customer_id_type = '03' AND customer_id_number REGEXP '^[0-9]{11,12}$')
   OR (customer_id_type = '04' AND customer_id_number REGEXP '^[0-9]{10}$'));

-- Código de establecimiento de Hacienda por sucursal (nota 3): 001, 002… asignado en orden y sin cambios.
-- Las sucursales nuevas lo reciben, a más tardar, al emitir su primer comprobante.
ALTER TABLE inventory_branches ADD COLUMN fiscal_code CHAR(3) CHARACTER SET ascii NULL;
UPDATE inventory_branches b
JOIN (SELECT company_id, id, ROW_NUMBER() OVER (PARTITION BY company_id ORDER BY created_at, id) AS n
      FROM inventory_branches) ordered ON ordered.company_id = b.company_id AND ordered.id = b.id
SET b.fiscal_code = LPAD(ordered.n, 3, '0');
ALTER TABLE inventory_branches
 ADD CONSTRAINT uq_inventory_branch_fiscal_code UNIQUE (company_id, fiscal_code),
 ADD CONSTRAINT chk_inventory_branch_fiscal_code CHECK (fiscal_code IS NULL OR (fiscal_code REGEXP '^[0-9]{3}$' AND fiscal_code <> '000'));

-- Configuración por empresa. Los secretos se guardan cifrados con AES-GCM (ADR-007), nunca en claro.
CREATE TABLE billing_settings (
 company_id CHAR(36) NOT NULL PRIMARY KEY,
 environment VARCHAR(10) NOT NULL DEFAULT 'TEST',
 api_username_enc VARBINARY(1024) NULL,
 api_password_enc VARBINARY(1024) NULL,
 key_store_enc MEDIUMBLOB NULL,
 key_pin_enc VARBINARY(256) NULL,
 key_subject VARCHAR(300) NULL,
 key_expires_at DATETIME(6) NULL,
 credentials_checked_at DATETIME(6) NULL,
 activity_code CHAR(6) CHARACTER SET ascii NULL,
 province CHAR(1) CHARACTER SET ascii NULL,
 canton CHAR(2) CHARACTER SET ascii NULL,
 district CHAR(2) CHARACTER SET ascii NULL,
 other_signs VARCHAR(250) NULL,
 email VARCHAR(160) NULL,
 production_since DATETIME(6) NULL,
 updated_at DATETIME(6) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE,
 CHECK (environment IN ('TEST', 'PROD')),
 CHECK (activity_code IS NULL OR activity_code REGEXP '^[0-9]{6}$'),
 CHECK (environment = 'TEST' OR production_since IS NOT NULL)
);

-- Último consecutivo por sucursal, ambiente y tipo (01 factura, 03 nota de crédito, 04 tiquete).
CREATE TABLE fiscal_sequences (
 company_id CHAR(36) NOT NULL,
 branch_id CHAR(36) NOT NULL,
 environment VARCHAR(10) NOT NULL,
 doc_type CHAR(2) NOT NULL,
 last_number BIGINT NOT NULL DEFAULT 0,
 PRIMARY KEY (company_id, branch_id, environment, doc_type),
 FOREIGN KEY (company_id, branch_id) REFERENCES inventory_branches(company_id, id) ON DELETE CASCADE,
 CHECK (environment IN ('TEST', 'PROD')),
 CHECK (doc_type IN ('01', '03', '04')),
 CHECK (last_number BETWEEN 0 AND 9999999999)
);

-- Cada comprobante emitido o por emitir. El XML firmado, su PDF y la respuesta de Hacienda no se modifican.
-- La conservación ante el borrado de empresas (STK-004) se define antes de producción (SPEC §12); por ahora sigue a la venta.
CREATE TABLE fiscal_documents (
 company_id CHAR(36) NOT NULL,
 id CHAR(36) NOT NULL,
 sale_id CHAR(36) NOT NULL,
 doc_type CHAR(2) NOT NULL,
 environment VARCHAR(10) NOT NULL,
 branch_id CHAR(36) NOT NULL,
 consecutive CHAR(20) CHARACTER SET ascii NOT NULL,
 numeric_key CHAR(50) CHARACTER SET ascii NOT NULL,
 issued_at DATETIME(6) NOT NULL,
 reference_id CHAR(36) NULL,
 replaces_id CHAR(36) NULL,
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
 PRIMARY KEY (company_id, id),
 UNIQUE KEY uq_fiscal_key (company_id, numeric_key),
 UNIQUE KEY uq_fiscal_consecutive (company_id, environment, consecutive),
 KEY ix_fiscal_sale (company_id, sale_id, created_at),
 KEY ix_fiscal_dispatch (status, next_attempt_at),
 FOREIGN KEY (company_id, sale_id) REFERENCES sales(company_id, id) ON DELETE CASCADE,
 FOREIGN KEY (company_id, branch_id) REFERENCES inventory_branches(company_id, id) ON DELETE CASCADE,
 FOREIGN KEY (company_id, reference_id) REFERENCES fiscal_documents(company_id, id),
 FOREIGN KEY (company_id, replaces_id) REFERENCES fiscal_documents(company_id, id),
 CHECK (doc_type IN ('01', '03', '04')),
 CHECK (environment IN ('TEST', 'PROD')),
 CHECK (status IN ('PENDING', 'PROCESSING', 'ACCEPTED', 'REJECTED', 'CANCELLED')),
 CHECK (mail_status IN ('NONE', 'PENDING', 'SENT', 'FAILED')),
 CHECK (consecutive REGEXP '^[0-9]{20}$' AND numeric_key REGEXP '^506[0-9]{47}$'),
 CHECK ((doc_type = '03') = (reference_id IS NOT NULL))
);
