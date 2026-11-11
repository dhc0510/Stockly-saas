-- STK-013 · ajuste del 2026-11-11: comprobante de una venta sin internet sincronizada después de dos días hábiles.
ALTER TABLE fiscal_documents ADD COLUMN late_submission BOOLEAN NOT NULL DEFAULT FALSE;
