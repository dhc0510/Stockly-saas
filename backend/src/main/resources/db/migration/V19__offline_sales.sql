ALTER TABLE sales
 ADD COLUMN offline_reference VARCHAR(42) NULL,
 ADD COLUMN offline_sold_at TIMESTAMP(6) NULL,
 ADD UNIQUE KEY uq_sale_offline_reference (company_id,offline_reference);

CREATE TABLE offline_sale_snapshots (
 company_id CHAR(36) NOT NULL,
 id CHAR(36) NOT NULL,
 account_id CHAR(36) NOT NULL,
 branch_id CHAR(36) NOT NULL,
 issued_at TIMESTAMP(6) NOT NULL,
 proof_key VARBINARY(32) NOT NULL,
 PRIMARY KEY (company_id,id),
 FOREIGN KEY (company_id,branch_id) REFERENCES inventory_branches(company_id,id) ON DELETE CASCADE
);

CREATE TABLE offline_sales (
 company_id CHAR(36) NOT NULL,
 id CHAR(36) NOT NULL,
 account_id CHAR(36) NOT NULL,
 branch_id CHAR(36) NOT NULL,
 sold_at TIMESTAMP(6) NOT NULL,
 payload JSON NOT NULL,
 request_hash CHAR(64) NOT NULL,
 state VARCHAR(12) NOT NULL,
 issue JSON NULL,
 sale_id CHAR(36) NULL,
 resolved_by CHAR(36) NULL,
 resolved_at TIMESTAMP(6) NULL,
 close_reason VARCHAR(150) NULL,
 refund_detail VARCHAR(300) NULL,
 PRIMARY KEY (company_id,id),
 INDEX ix_offline_sales_branch (company_id,branch_id,state,sold_at),
 FOREIGN KEY (company_id,branch_id) REFERENCES inventory_branches(company_id,id) ON DELETE CASCADE,
 FOREIGN KEY (company_id,sale_id) REFERENCES sales(company_id,id) ON DELETE CASCADE,
 CHECK (state IN ('REVIEW','SYNCED','CLOSED'))
);
