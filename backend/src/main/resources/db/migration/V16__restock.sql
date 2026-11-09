-- STK-016: nivel deseado y «No reponer» por producto/variante y sucursal.
-- restock_paused_by guarda la cuenta sin FK, como el responsable de ventas y movimientos, para no bloquear borrados de cuentas.
ALTER TABLE inventory_branch_items
  ADD COLUMN target_quantity DECIMAL(15,2) NULL,
  ADD COLUMN restock_paused BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN restock_paused_by CHAR(36) NULL,
  ADD COLUMN restock_paused_at DATETIME(6) NULL,
  ADD CONSTRAINT chk_inventory_target_quantity CHECK (target_quantity IS NULL OR target_quantity > 0),
  ADD CONSTRAINT chk_inventory_restock_paused CHECK (restock_paused = FALSE OR restock_paused_at IS NOT NULL);
