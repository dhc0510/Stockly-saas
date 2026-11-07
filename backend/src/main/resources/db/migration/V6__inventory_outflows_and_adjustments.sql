INSERT INTO permission_modules(module_key,label) VALUES ('INVENTORY_MOVEMENTS','Salidas y ajustes');
INSERT INTO special_permission_catalog(permission_key,label) VALUES
 ('ADJUST_INVENTORY','Registrar ajustes de inventario'),
 ('VOID_INVENTORY_MOVEMENTS','Anular salidas y ajustes');

ALTER TABLE inventory_locations
 ADD COLUMN out_sequence BIGINT NOT NULL DEFAULT 0,
 ADD COLUMN adjust_sequence BIGINT NOT NULL DEFAULT 0;
