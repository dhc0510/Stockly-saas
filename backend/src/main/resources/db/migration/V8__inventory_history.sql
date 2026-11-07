INSERT INTO permission_modules(module_key,label) VALUES ('INVENTORY_HISTORY','Historial');

CREATE INDEX ix_inventory_movement_date ON inventory_movements(company_id,created_at);
CREATE INDEX ix_inventory_movement_actor ON inventory_movements(company_id,actor_id,created_at);
CREATE INDEX ix_inventory_movement_line_item ON inventory_movement_lines(company_id,item_id);

-- La búsqueda de empresas usa Criteria; la colación se fija en la columna para no depender de la base.
ALTER TABLE companies
 MODIFY commercial_name VARCHAR(150) NOT NULL COLLATE utf8mb4_0900_ai_ci,
 MODIFY legal_name VARCHAR(150) NOT NULL COLLATE utf8mb4_0900_ai_ci;
