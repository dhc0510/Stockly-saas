ALTER TABLE catalog_products
 DROP FOREIGN KEY catalog_products_ibfk_2,
 ADD CONSTRAINT fk_catalog_product_category
 FOREIGN KEY (company_id,category) REFERENCES catalog_categories(company_id,name)
 ON UPDATE CASCADE ON DELETE CASCADE;
