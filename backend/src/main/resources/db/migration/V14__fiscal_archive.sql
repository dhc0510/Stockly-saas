-- STK-013 · ADR-008: al borrar una empresa (STK-004), sus comprobantes enviados a Hacienda se conservan aquí
-- durante 5 años desde su emisión. Sin claves foráneas: debe sobrevivir al borrado de la empresa y sus ventas.
CREATE TABLE fiscal_archive (
 company_id CHAR(36) NOT NULL,
 document_id CHAR(36) NOT NULL,
 issuer_legal_name VARCHAR(150) NOT NULL,
 issuer_id_type VARCHAR(12) NOT NULL,
 issuer_id_number VARCHAR(12) NOT NULL,
 doc_type CHAR(2) NOT NULL,
 environment VARCHAR(10) NOT NULL,
 consecutive CHAR(20) CHARACTER SET ascii NOT NULL,
 numeric_key CHAR(50) CHARACTER SET ascii NOT NULL,
 issued_at DATETIME(6) NOT NULL,
 status VARCHAR(12) NOT NULL,
 signed_xml MEDIUMBLOB NOT NULL,
 pdf MEDIUMBLOB NULL,
 response_xml MEDIUMBLOB NULL,
 archived_at DATETIME(6) NOT NULL,
 retain_until DATETIME(6) NOT NULL,
 PRIMARY KEY (company_id, document_id),
 UNIQUE KEY uq_fiscal_archive_key (numeric_key),
 KEY ix_fiscal_archive_retention (retain_until),
 KEY ix_fiscal_archive_issuer (issuer_id_number, issued_at)
);
