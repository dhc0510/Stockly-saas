ALTER TABLE inventory_locations ADD COLUMN return_sequence BIGINT NOT NULL DEFAULT 0;

INSERT INTO special_permission_catalog(permission_key,label) VALUES ('REGISTER_SALE_RETURNS','Registrar devoluciones');

CREATE TABLE sale_returns (
 company_id CHAR(36) NOT NULL,
 id CHAR(36) NOT NULL,
 sale_id CHAR(36) NOT NULL,
 folio VARCHAR(32) NOT NULL,
 actor_id CHAR(36) NOT NULL,
 actor_name VARCHAR(150) NOT NULL,
 reason VARCHAR(150) NOT NULL,
 movement_id CHAR(36) NULL,
 request_key CHAR(36) NOT NULL,
 request_hash CHAR(64) NOT NULL,
 amount DECIMAL(33,2) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY (company_id,id),
 UNIQUE (company_id,folio),
 UNIQUE (company_id,request_key),
 UNIQUE (company_id,movement_id),
 UNIQUE (company_id,sale_id,id),
 FOREIGN KEY (company_id,sale_id) REFERENCES sales(company_id,id) ON DELETE CASCADE,
 FOREIGN KEY (company_id,movement_id) REFERENCES inventory_movements(company_id,id) ON DELETE CASCADE,
 CHECK (amount >= 0)
);

CREATE TABLE sale_return_lines (
 company_id CHAR(36) NOT NULL,
 sale_id CHAR(36) NOT NULL,
 return_id CHAR(36) NOT NULL,
 item_id CHAR(36) NOT NULL,
 quantity DECIMAL(18,3) NOT NULL,
 restocked DECIMAL(18,3) NOT NULL,
 amount DECIMAL(33,2) NOT NULL,
 PRIMARY KEY (company_id,return_id,item_id),
 FOREIGN KEY (company_id,sale_id,return_id) REFERENCES sale_returns(company_id,sale_id,id) ON DELETE CASCADE,
 FOREIGN KEY (company_id,sale_id,item_id) REFERENCES sale_line_prices(company_id,sale_id,item_id) ON DELETE CASCADE,
 CHECK (quantity > 0 AND restocked >= 0 AND restocked <= quantity AND amount >= 0)
);

CREATE TABLE sale_return_refunds (
 company_id CHAR(36) NOT NULL,
 return_id CHAR(36) NOT NULL,
 position SMALLINT NOT NULL,
 method VARCHAR(16) NOT NULL,
 amount DECIMAL(33,2) NOT NULL,
 reference VARCHAR(60) NULL,
 PRIMARY KEY (company_id,return_id,position),
 FOREIGN KEY (company_id,return_id) REFERENCES sale_returns(company_id,id) ON DELETE CASCADE,
 CHECK (method IN ('CASH','CARD','SINPE','TRANSFER')),
 CHECK (amount > 0 AND (method<>'CASH' OR reference IS NULL))
);

ALTER TABLE fiscal_documents
 ADD COLUMN sale_return_id CHAR(36) NULL,
 ADD UNIQUE KEY uq_fiscal_sale_return (company_id,sale_return_id),
 ADD CONSTRAINT fk_fiscal_sale_return FOREIGN KEY (company_id,sale_id,sale_return_id)
   REFERENCES sale_returns(company_id,sale_id,id) ON DELETE CASCADE;
