-- STK-012: Stockly solo registra cobros y devoluciones; no procesa pagos.

-- Sin filas para una empresa, los cuatro medios se consideran activos.
CREATE TABLE company_payment_methods (
 company_id CHAR(36) NOT NULL,
 method VARCHAR(16) NOT NULL,
 active BOOLEAN NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 PRIMARY KEY (company_id,method),
 FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE,
 CHECK (method IN ('CASH','CARD','SINPE','TRANSFER'))
);

-- Una venta sin filas aquí se registró antes de STK-012: "Sin cobro registrado".
CREATE TABLE sale_payments (
 company_id CHAR(36) NOT NULL,
 sale_id CHAR(36) NOT NULL,
 position SMALLINT NOT NULL,
 method VARCHAR(16) NOT NULL,
 amount DECIMAL(33,2) NOT NULL,
 received DECIMAL(33,2) NULL,
 reference VARCHAR(60) NULL,
 PRIMARY KEY (company_id,sale_id,position),
 FOREIGN KEY (company_id,sale_id) REFERENCES sales(company_id,id) ON DELETE CASCADE,
 CHECK (method IN ('CASH','CARD','SINPE','TRANSFER')),
 CHECK (amount >= 0),
 CHECK ((method='CASH' AND received IS NOT NULL AND received > 0 AND received >= amount AND reference IS NULL)
     OR (method<>'CASH' AND received IS NULL AND amount > 0))
);

CREATE TABLE sale_refunds (
 company_id CHAR(36) NOT NULL,
 sale_id CHAR(36) NOT NULL,
 void_id CHAR(36) NOT NULL,
 position SMALLINT NOT NULL,
 method VARCHAR(16) NOT NULL,
 amount DECIMAL(33,2) NOT NULL,
 reference VARCHAR(60) NULL,
 PRIMARY KEY (company_id,sale_id,position),
 FOREIGN KEY (company_id,sale_id) REFERENCES sales(company_id,id) ON DELETE CASCADE,
 FOREIGN KEY (company_id,void_id) REFERENCES inventory_movements(company_id,id) ON DELETE CASCADE,
 INDEX ix_sale_refunds_void (company_id,void_id),
 CHECK (method IN ('CASH','CARD','SINPE','TRANSFER')),
 CHECK (amount > 0),
 CHECK (method<>'CASH' OR reference IS NULL)
);

-- El resumen filtra los movimientos de venta y anulación por empresa y fecha.
CREATE INDEX ix_inventory_movements_company_kind_time ON inventory_movements(company_id,kind,created_at);
