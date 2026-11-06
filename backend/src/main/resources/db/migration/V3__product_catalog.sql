INSERT INTO permission_modules(module_key,label) VALUES ('CATALOG','Catálogo');
INSERT INTO special_permission_catalog(permission_key,label) VALUES
 ('CATALOG_PRICE','Cambiar precios del catálogo'),
 ('CATALOG_ARCHIVE','Archivar y reactivar productos');

CREATE TABLE catalog_categories (
 company_id CHAR(36) NOT NULL,
 name VARCHAR(120) NOT NULL,
 PRIMARY KEY (company_id,name),
 FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE
);

CREATE TABLE catalog_products (
 company_id CHAR(36) NOT NULL,
 id CHAR(36) NOT NULL,
 name VARCHAR(160) NOT NULL,
 category VARCHAR(120),
 description VARCHAR(2000),
 image_type VARCHAR(32),
 image_data MEDIUMBLOB,
 active BOOLEAN NOT NULL DEFAULT TRUE,
 PRIMARY KEY (company_id,id),
 FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE,
 FOREIGN KEY (company_id,category) REFERENCES catalog_categories(company_id,name) ON DELETE CASCADE
);

CREATE TABLE catalog_items (
 company_id CHAR(36) NOT NULL,
 id CHAR(36) NOT NULL,
 product_id CHAR(36) NOT NULL,
 variant_name VARCHAR(240),
 sku VARCHAR(80) NOT NULL,
 barcode VARCHAR(80),
 unit VARCHAR(24) NOT NULL,
 price DECIMAL(15,2),
 active BOOLEAN NOT NULL DEFAULT TRUE,
 has_movements BOOLEAN NOT NULL DEFAULT FALSE,
 PRIMARY KEY (company_id,id),
 UNIQUE (company_id,barcode),
 UNIQUE (company_id,product_id,variant_name),
 FOREIGN KEY (company_id,product_id) REFERENCES catalog_products(company_id,id) ON DELETE CASCADE
);

CREATE TABLE catalog_skus (
 company_id CHAR(36) NOT NULL,
 sku VARCHAR(80) NOT NULL,
 item_id CHAR(36) NOT NULL,
 PRIMARY KEY (company_id,sku),
 FOREIGN KEY (company_id,item_id) REFERENCES catalog_items(company_id,id) ON DELETE CASCADE
);

CREATE TABLE catalog_sku_history (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 company_id CHAR(36) NOT NULL,
 item_id CHAR(36) NOT NULL,
 actor_id CHAR(36) NOT NULL,
 previous_sku VARCHAR(80) NOT NULL,
 new_sku VARCHAR(80) NOT NULL,
 changed_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY (company_id,item_id) REFERENCES catalog_items(company_id,id) ON DELETE CASCADE
);
