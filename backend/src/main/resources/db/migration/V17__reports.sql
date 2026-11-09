-- STK-017: módulo de permiso «Reportes» (solo lectura) e índice para filtrar devoluciones por fecha.
INSERT INTO permission_modules(module_key,label) VALUES ('REPORTS','Reportes');

CREATE INDEX ix_sale_returns_company_time ON sale_returns(company_id,created_at);
