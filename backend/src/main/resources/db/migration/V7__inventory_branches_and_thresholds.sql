INSERT INTO permission_modules(module_key,label) VALUES ('INVENTORY','Existencias y sucursales');

ALTER TABLE users ADD CONSTRAINT uq_users_company_id UNIQUE (company_id,id);

CREATE TABLE inventory_branches (
 company_id CHAR(36) NOT NULL,
 id CHAR(36) NOT NULL,
 name VARCHAR(120) NOT NULL,
 code VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 address VARCHAR(500) NOT NULL,
 phone VARCHAR(30) NOT NULL,
 contact_email VARCHAR(254) NOT NULL,
 manager_name VARCHAR(150) NOT NULL,
 active BOOLEAN NOT NULL DEFAULT TRUE,
 created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 PRIMARY KEY (company_id,id),
 CONSTRAINT uq_inventory_branch_code UNIQUE (company_id,code),
 CONSTRAINT fk_inventory_branch_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE
);

CREATE TABLE inventory_branch_users (
 company_id CHAR(36) NOT NULL,
 branch_id CHAR(36) NOT NULL,
 user_id CHAR(36) NOT NULL,
 PRIMARY KEY (company_id,branch_id,user_id),
 CONSTRAINT fk_inventory_branch_user_branch FOREIGN KEY (company_id,branch_id)
   REFERENCES inventory_branches(company_id,id) ON DELETE CASCADE,
 CONSTRAINT fk_inventory_branch_user_user FOREIGN KEY (company_id,user_id)
   REFERENCES users(company_id,id) ON DELETE CASCADE
);

CREATE TABLE inventory_branch_items (
 company_id CHAR(36) NOT NULL,
 branch_id CHAR(36) NOT NULL,
 item_id CHAR(36) NOT NULL,
 minimum_quantity DECIMAL(15,2) NULL,
 low_threshold DECIMAL(15,2) NOT NULL DEFAULT 2.00,
 recovery_margin DECIMAL(15,2) NOT NULL DEFAULT 1.00,
 exhausted_basis VARCHAR(12) NOT NULL DEFAULT 'AVAILABLE',
 created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 PRIMARY KEY (company_id,branch_id,item_id),
 CONSTRAINT chk_inventory_low_threshold CHECK (low_threshold >= 0),
 CONSTRAINT chk_inventory_recovery_margin CHECK (recovery_margin >= 1),
 CONSTRAINT chk_inventory_exhausted_basis CHECK (exhausted_basis IN ('AVAILABLE','ON_HAND')),
 CONSTRAINT fk_inventory_branch_item_branch FOREIGN KEY (company_id,branch_id)
   REFERENCES inventory_branches(company_id,id) ON DELETE CASCADE,
 CONSTRAINT fk_inventory_branch_item_catalog FOREIGN KEY (company_id,item_id)
   REFERENCES catalog_items(company_id,id) ON DELETE CASCADE
);

CREATE TABLE inventory_alerts (
 id CHAR(36) NOT NULL PRIMARY KEY,
 company_id CHAR(36) NOT NULL,
 recipient_id CHAR(36) NOT NULL,
 branch_id CHAR(36) NOT NULL,
 item_id CHAR(36) NOT NULL,
 alert_type VARCHAR(12) NOT NULL,
 active BOOLEAN NOT NULL DEFAULT TRUE,
 last_notified_at DATETIME(6),
 dismissed_at DATETIME(6),
 created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 CONSTRAINT uq_inventory_alert_recipient UNIQUE (company_id,recipient_id,branch_id,item_id,alert_type),
 CONSTRAINT chk_inventory_alert_type CHECK (alert_type IN ('MINIMUM','EXHAUSTED')),
 CONSTRAINT fk_inventory_alert_branch FOREIGN KEY (company_id,branch_id)
   REFERENCES inventory_branches(company_id,id) ON DELETE CASCADE,
 CONSTRAINT fk_inventory_alert_item FOREIGN KEY (company_id,branch_id,item_id)
   REFERENCES inventory_branch_items(company_id,branch_id,item_id) ON DELETE CASCADE,
 CONSTRAINT fk_inventory_alert_recipient FOREIGN KEY (company_id,recipient_id)
   REFERENCES users(company_id,id) ON DELETE CASCADE,
 INDEX ix_inventory_alert_inbox (company_id,recipient_id,active,last_notified_at)
);
