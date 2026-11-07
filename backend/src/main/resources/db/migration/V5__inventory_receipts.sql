INSERT INTO permission_modules(module_key,label) VALUES ('INVENTORY_RECEIPTS','Entradas de inventario');
INSERT INTO special_permission_catalog(permission_key,label) VALUES ('VOID_INVENTORY_RECEIPTS','Anular entradas de inventario');

CREATE TABLE inventory_locations (
 company_id CHAR(36) NOT NULL,
 id VARCHAR(36) NOT NULL,
 name VARCHAR(120) NOT NULL,
 receipt_sequence BIGINT NOT NULL DEFAULT 0,
 void_sequence BIGINT NOT NULL DEFAULT 0,
 PRIMARY KEY (company_id,id),
 FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE
);
INSERT INTO inventory_locations(company_id,id,name) SELECT id,'principal','Principal' FROM companies;

CREATE TABLE inventory_balances (
 company_id CHAR(36) NOT NULL,
 location_id VARCHAR(36) NOT NULL,
 item_id CHAR(36) NOT NULL,
 quantity DECIMAL(18,3) NOT NULL,
 PRIMARY KEY (company_id,location_id,item_id),
 CHECK (quantity >= 0),
 FOREIGN KEY (company_id,location_id) REFERENCES inventory_locations(company_id,id) ON DELETE CASCADE,
 FOREIGN KEY (company_id,item_id) REFERENCES catalog_items(company_id,id) ON DELETE CASCADE
);

CREATE TABLE inventory_movements (
 company_id CHAR(36) NOT NULL,
 id CHAR(36) NOT NULL,
 location_id VARCHAR(36) NOT NULL,
 kind VARCHAR(16) NOT NULL,
 folio VARCHAR(32) NOT NULL,
 actor_id CHAR(36) NOT NULL,
 actor_name VARCHAR(150) NOT NULL,
 reason VARCHAR(320) NOT NULL,
 reference VARCHAR(100),
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 request_key CHAR(36),
 request_hash CHAR(64),
 reversed_id CHAR(36),
 PRIMARY KEY (company_id,id),
 UNIQUE (company_id,folio),
 UNIQUE (company_id,request_key),
 UNIQUE (company_id,reversed_id),
 FOREIGN KEY (company_id,location_id) REFERENCES inventory_locations(company_id,id) ON DELETE CASCADE,
 FOREIGN KEY (company_id,reversed_id) REFERENCES inventory_movements(company_id,id) ON DELETE CASCADE
);

CREATE TABLE inventory_movement_lines (
 company_id CHAR(36) NOT NULL,
 movement_id CHAR(36) NOT NULL,
 item_id CHAR(36) NOT NULL,
 item_name VARCHAR(403) NOT NULL,
 sku VARCHAR(80) NOT NULL,
 unit VARCHAR(24) NOT NULL,
 quantity DECIMAL(18,3) NOT NULL,
 balance_after DECIMAL(18,3) NOT NULL,
 PRIMARY KEY (company_id,movement_id,item_id),
 FOREIGN KEY (company_id,movement_id) REFERENCES inventory_movements(company_id,id) ON DELETE CASCADE
);
