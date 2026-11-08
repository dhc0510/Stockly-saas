ALTER TABLE inventory_branches
 ADD COLUMN location_id VARCHAR(36) NULL,
 ADD CONSTRAINT uq_inventory_branch_location UNIQUE (company_id,location_id),
 ADD CONSTRAINT fk_inventory_branch_location FOREIGN KEY (company_id,location_id)
   REFERENCES inventory_locations(company_id,id) ON DELETE CASCADE;

ALTER TABLE inventory_locations ADD COLUMN sale_sequence BIGINT NOT NULL DEFAULT 0;

INSERT INTO permission_modules(module_key,label) VALUES ('SALES','Ventas');
INSERT INTO special_permission_catalog(permission_key,label) VALUES ('VOID_SALES','Anular ventas');

-- La venta comparte su identificador y folio con el movimiento de inventario.
CREATE TABLE sales (
 company_id CHAR(36) NOT NULL,
 id CHAR(36) NOT NULL,
 branch_id CHAR(36) NOT NULL,
 branch_name VARCHAR(120) NOT NULL,
 customer_name VARCHAR(150),
 total DECIMAL(33,2) NOT NULL,
 PRIMARY KEY (company_id,id),
 FOREIGN KEY (company_id,id) REFERENCES inventory_movements(company_id,id) ON DELETE CASCADE,
 FOREIGN KEY (company_id,branch_id) REFERENCES inventory_branches(company_id,id) ON DELETE CASCADE,
 CHECK (total >= 0)
);

CREATE TABLE sale_line_prices (
 company_id CHAR(36) NOT NULL,
 sale_id CHAR(36) NOT NULL,
 item_id CHAR(36) NOT NULL,
 unit_price DECIMAL(15,2) NOT NULL,
 amount DECIMAL(33,2) NOT NULL,
 PRIMARY KEY (company_id,sale_id,item_id),
 FOREIGN KEY (company_id,sale_id) REFERENCES sales(company_id,id) ON DELETE CASCADE,
 FOREIGN KEY (company_id,sale_id,item_id)
   REFERENCES inventory_movement_lines(company_id,movement_id,item_id) ON DELETE CASCADE,
 CHECK (unit_price >= 0 AND amount >= 0)
);
