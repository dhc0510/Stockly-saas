-- STK-013 (coordinado con STK-006): código CABYS (13 dígitos) y código de tarifa de IVA (nota 8.1) por variante.
-- Ambos son opcionales: un artículo sin ellos se vende, pero su comprobante queda pendiente hasta completarlos.
ALTER TABLE catalog_items
 ADD COLUMN cabys_code CHAR(13) CHARACTER SET ascii NULL,
 ADD COLUMN tax_rate_code CHAR(2) CHARACTER SET ascii NULL,
 ADD CONSTRAINT chk_catalog_item_cabys CHECK (cabys_code IS NULL OR cabys_code REGEXP '^[0-9]{13}$'),
 ADD CONSTRAINT chk_catalog_item_tax_rate CHECK (tax_rate_code IS NULL OR tax_rate_code IN ('01','02','03','04','08','09','10','11'));
