package com.stockly.catalog;

import com.stockly.identity.IdentityFailure;
import com.stockly.identity.StocklyPrincipal;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {
    private static final int PAGE_SIZE = 25;
    private static final int IMAGE_LIMIT = 2 * 1024 * 1024;
    private static final Set<String> UNITS = Set.of("unidad", "kg", "litro", "metro");
    private final JdbcTemplate jdbc;

    CatalogService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Page list(StocklyPrincipal actor, String search, String category, boolean active, int page) {
        String company = company(actor);
        String term = "%" + search.strip().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        var products = jdbc.query("""
            SELECT p.id,p.name,p.category,p.description,p.active,p.image_type IS NOT NULL AS has_image FROM catalog_products p
            WHERE p.company_id=? AND (?='' OR p.category=?)
              AND EXISTS (SELECT 1 FROM catalog_items i
                WHERE i.company_id=p.company_id AND i.product_id=p.id AND (p.active AND i.active)=?
                  AND (p.name COLLATE utf8mb4_0900_ai_ci LIKE ? ESCAPE '!' OR i.sku COLLATE utf8mb4_0900_ai_ci LIKE ? ESCAPE '!' OR i.barcode COLLATE utf8mb4_0900_ai_ci LIKE ? ESCAPE '!'))
            ORDER BY p.name,p.id LIMIT ? OFFSET ?
            """, (rs, row) -> product(company, rs),
            company, category, category, active, term, term, term, PAGE_SIZE + 1, Math.max(0L, page) * PAGE_SIZE);
        var categories = jdbc.queryForList(
            "SELECT name FROM catalog_categories WHERE company_id=? ORDER BY name", String.class, company);
        return new Page(products.stream().limit(PAGE_SIZE).toList(), categories, products.size() > PAGE_SIZE);
    }

    @Transactional
    public String createCategory(StocklyPrincipal actor, String input) {
        String name = required(input, 120, "Escribe el nombre de la categoría.");
        try {
            jdbc.update("INSERT INTO catalog_categories(company_id,name) VALUES (?,?)", company(actor), name);
        } catch (DuplicateKeyException e) {
            throw conflict("Ya existe una categoría con ese nombre.");
        }
        return name;
    }

    @Transactional
    public String renameCategory(StocklyPrincipal actor, String previousName, String input) {
        String company = company(actor);
        String previous = required(previousName, 120, "Selecciona una categoría.");
        String name = required(input, 120, "Escribe el nombre de la categoría.");
        try {
            if (jdbc.update("UPDATE catalog_categories SET name=? WHERE company_id=? AND name=?",
                name, company, previous) == 0) throw missing();
        } catch (DuplicateKeyException e) {
            throw conflict("Ya existe una categoría con ese nombre.");
        }
        return name;
    }

    @Transactional
    public void deleteCategory(StocklyPrincipal actor, String input) {
        String company = company(actor);
        String name = required(input, 120, "Selecciona una categoría.");
        if (jdbc.queryForList("SELECT name FROM catalog_categories WHERE company_id=? AND name=? FOR UPDATE",
            String.class, company, name).isEmpty()) throw missing();
        jdbc.update("UPDATE catalog_products SET category=NULL WHERE company_id=? AND category=?", company, name);
        jdbc.update("DELETE FROM catalog_categories WHERE company_id=? AND name=?", company, name);
    }

    @Transactional
    public Product saveProduct(StocklyPrincipal actor, String id, ProductInput input, boolean canPrice) {
        String company = company(actor);
        String name = required(input.name(), 160, "Escribe el nombre del producto.");
        String category = clean(input.category(), 120);
        String description = clean(input.description(), 2000);
        boolean creating = id == null;
        if (category != null) {
            category = jdbc.queryForList("SELECT name FROM catalog_categories WHERE company_id=? AND name=? FOR SHARE",
                String.class, company, category).stream().findFirst()
                .orElseThrow(() -> invalid("La categoría ya no está disponible. Actualiza el catálogo y elige otra."));
        }
        if (creating) id = uuid();
        else lockProduct(company, id);
        if (creating) {
            jdbc.update("INSERT INTO catalog_products(company_id,id,name,category,description) VALUES (?,?,?,?,?)",
                company, id, name, category, description);
        } else {
            jdbc.update("UPDATE catalog_products SET name=?,category=?,description=? WHERE company_id=? AND id=?",
                name, category, description, company, id);
        }
        if (Boolean.TRUE.equals(input.removeImage()) || input.image() != null) {
            Image image = Boolean.TRUE.equals(input.removeImage()) ? null : decode(input.image());
            jdbc.update("UPDATE catalog_products SET image_type=?,image_data=? WHERE company_id=? AND id=?",
                image == null ? null : image.type(), image == null ? null : image.bytes(), company, id);
        }
        var items = jdbc.query("SELECT * FROM catalog_items WHERE company_id=? AND product_id=? FOR UPDATE",
            (rs, row) -> item(rs), company, id);
        Item simple = items.size() == 1 && items.getFirst().variantName() == null ? items.getFirst() : null;
        var variants = input.variants() == null ? List.<String>of() : input.variants();
        if (!variants.isEmpty()) {
            if (variants.size() > 100) throw invalid("Selecciona hasta 100 variantes por operación.");
            if (simple != null && !variants.contains(input.keepVariant())) throw invalid("Elige la variante que conserva el artículo original.");
            var unique = new HashSet<String>();
            for (String value : variants) {
                String variant = required(value, 240, "Completa la combinación de variante.");
                if (!unique.add(variant.toLowerCase(Locale.ROOT))) throw invalid("Hay combinaciones repetidas.");
                if (simple != null && value.equals(input.keepVariant())) {
                    jdbc.update("UPDATE catalog_items SET variant_name=? WHERE company_id=? AND id=?", variant, company, simple.id());
                } else {
                    var original = creating && value.equals(variants.getFirst()) ? input.item() : null;
                    // Las variantes nuevas heredan la unidad y los datos fiscales del artículo existente.
                    Item base = items.isEmpty() ? null : items.getFirst();
                    insertItem(actor, id, variant, original == null ? new ItemInput(null, null,
                        base != null ? base.unit() : input.item() == null ? null : input.item().unit(), null, false,
                        base != null ? base.cabys() : input.item() == null ? null : input.item().cabys(),
                        base != null ? base.taxRate() : input.item() == null ? null : input.item().taxRate()) : original, canPrice);
                }
            }
        } else if (creating) {
            insertItem(actor, id, null, input.item() == null ? new ItemInput(null, null, null, null, false) : input.item(), canPrice);
        } else if (simple != null && input.item() != null) {
            updateItem(actor, simple, input.item(), canPrice);
        }
        return get(company, id);
    }

    @Transactional
    public Item saveItem(StocklyPrincipal actor, String id, ItemInput input, boolean canPrice) {
        String company = company(actor);
        Item current = jdbc.query("""
            SELECT * FROM catalog_items WHERE company_id=? AND id=? FOR UPDATE
            """, (rs, row) -> item(rs), company, id).stream().findFirst().orElseThrow(CatalogService::missing);
        updateItem(actor, current, input, canPrice);
        return jdbc.query("SELECT * FROM catalog_items WHERE company_id=? AND id=?",
            (rs, row) -> item(rs), company, id).getFirst();
    }

    @Transactional
    public void status(StocklyPrincipal actor, String kind, String id, boolean active) {
        String company = company(actor);
        String table = switch (kind) {
            case "products" -> "catalog_products";
            case "items" -> "catalog_items";
            default -> throw missing();
        };
        if (jdbc.update("UPDATE " + table + " SET active=? WHERE company_id=? AND id=?", active, company, id) == 0)
            throw missing();
    }

    @Transactional
    public void price(StocklyPrincipal actor, String id, BigDecimal price) {
        if (jdbc.update("UPDATE catalog_items SET price=? WHERE company_id=? AND id=?",
            validPrice(price), company(actor), id) == 0) throw missing();
    }

    public Image image(StocklyPrincipal actor, String id) {
        return jdbc.query("""
            SELECT image_type,image_data FROM catalog_products WHERE company_id=? AND id=? AND image_type IS NOT NULL
            """, (rs, row) -> new Image(rs.getString(1), rs.getBytes(2)), company(actor), id)
            .stream().findFirst().orElseThrow(CatalogService::missing);
    }

    private void insertItem(StocklyPrincipal actor, String product, String variant, ItemInput input, boolean canPrice) {
        String company = company(actor);
        String id = uuid();
        String sku = clean(input.sku(), 80);
        boolean automatic = sku == null;
        if (automatic) sku = automaticSku();
        BigDecimal price = authorizedPrice(input, null, canPrice);
        try {
            jdbc.update("""
                INSERT INTO catalog_items(company_id,id,product_id,variant_name,sku,barcode,unit,price,cabys_code,tax_rate_code)
                VALUES (?,?,?,?,?,?,?,?,?,?)
                """, company, id, product, variant, sku, clean(input.barcode(), 80), unit(input.unit()), price,
                cabys(input.cabys(), null), taxRate(input.taxRate(), null));
        } catch (DuplicateKeyException e) {
            throw conflict("El código de barras o la combinación ya existe.");
        }
        while (!reserve(company, id, sku)) {
            if (!automatic) throw conflict("Ese SKU está asignado o reservado.");
            sku = automaticSku();
            jdbc.update("UPDATE catalog_items SET sku=? WHERE company_id=? AND id=?", sku, company, id);
        }
    }

    private void updateItem(StocklyPrincipal actor, Item item, ItemInput input, boolean canPrice) {
        String company = company(actor);
        String sku = clean(input.sku(), 80);
        if (sku == null) sku = item.sku();
        String unit = unit(input.unit());
        if (item.hasMovements() && !unit.equals(item.unit())) throw conflict("La unidad ya tiene movimientos de inventario.");
        BigDecimal price = authorizedPrice(input, item.price(), canPrice);
        if (!sku.equals(item.sku())) {
            if (!reserve(company, item.id(), sku)) throw conflict("Ese SKU está asignado o reservado.");
            jdbc.update("""
                INSERT INTO catalog_sku_history(company_id,item_id,actor_id,previous_sku,new_sku)
                VALUES (?,?,?,?,?)
                """, company, item.id(), actor.accountId(), item.sku(), sku);
        }
        try {
            jdbc.update("UPDATE catalog_items SET sku=?,barcode=?,unit=?,price=?,cabys_code=?,tax_rate_code=? WHERE company_id=? AND id=?",
                sku, clean(input.barcode(), 80), unit, price, cabys(input.cabys(), item.cabys()), taxRate(input.taxRate(), item.taxRate()),
                company, item.id());
        } catch (DuplicateKeyException e) {
            throw conflict("Ese código de barras ya está asignado.");
        }
    }

    private boolean reserve(String company, String id, String sku) {
        try {
            jdbc.update("INSERT INTO catalog_skus(company_id,sku,item_id) VALUES (?,?,?)", company, sku, id);
            return true;
        } catch (DuplicateKeyException e) {
            return id.equals(jdbc.queryForObject("""
                SELECT item_id FROM catalog_skus WHERE company_id=? AND sku=? FOR UPDATE
                """, String.class, company, sku));
        }
    }

    private void lockProduct(String company, String id) {
        if (jdbc.queryForList("SELECT id FROM catalog_products WHERE company_id=? AND id=? FOR UPDATE",
            String.class, company, id).isEmpty()) throw missing();
    }

    private Product get(String company, String id) {
        return jdbc.query("""
            SELECT id,name,category,description,active,image_type IS NOT NULL AS has_image
            FROM catalog_products WHERE company_id=? AND id=?
            """, (rs, row) -> product(company, rs), company, id).stream().findFirst().orElseThrow(CatalogService::missing);
    }

    private Product product(String company, ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        return new Product(id, rs.getString("name"), rs.getString("category"), rs.getString("description"),
            rs.getBoolean("active"), rs.getBoolean("has_image"), items(company, id));
    }

    private List<Item> items(String company, String product) {
        return jdbc.query("SELECT * FROM catalog_items WHERE company_id=? AND product_id=? ORDER BY variant_name,id",
            (rs, row) -> item(rs), company, product);
    }

    private static Item item(ResultSet rs) throws SQLException {
        return new Item(rs.getString("id"), rs.getString("variant_name"), rs.getString("sku"),
            rs.getString("barcode"), rs.getString("unit"), rs.getBigDecimal("price"),
            rs.getBoolean("active"), rs.getBoolean("has_movements"), rs.getString("cabys_code"), rs.getString("tax_rate_code"));
    }

    private static String company(StocklyPrincipal actor) {
        if (actor.companyId() == null || actor.mustChangePassword())
            throw new IdentityFailure(403, "FORBIDDEN", "No tienes acceso al catálogo.");
        return actor.companyId();
    }

    private static String required(String value, int limit, String message) {
        String result = clean(value, limit);
        if (result == null) throw invalid(message);
        return result;
    }

    private static String clean(String value, int limit) {
        if (value == null || value.isBlank()) return null;
        value = value.strip();
        if (value.length() > limit) throw invalid("Un campo excede el máximo de " + limit + " caracteres.");
        return value;
    }

    private static String unit(String input) {
        if (input == null) return "unidad";
        if (!UNITS.contains(input)) throw invalid("Selecciona una unidad base válida.");
        return input;
    }

    private static final Set<String> TAX_RATES = Set.of("01", "02", "03", "04", "08", "09", "10", "11");

    private static String cabys(String input, String previous) {
        if (input == null) return previous;
        String value = input.strip();
        if (value.isEmpty()) return null;
        if (!value.matches("[0-9]{13}")) throw invalid("El código CABYS debe tener 13 dígitos.");
        return value;
    }

    private static String taxRate(String input, String previous) {
        if (input == null) return previous;
        String value = input.strip();
        if (value.isEmpty()) return null;
        if (!TAX_RATES.contains(value)) throw invalid("Selecciona una tarifa de IVA válida.");
        return value;
    }

    private static BigDecimal authorizedPrice(ItemInput input, BigDecimal previous, boolean allowed) {
        if (!Boolean.TRUE.equals(input.setPrice())) return previous;
        if (!allowed) throw new IdentityFailure(403, "FORBIDDEN", "No tienes permiso para cambiar precios.");
        return validPrice(input.price());
    }

    private static BigDecimal validPrice(BigDecimal price) {
        if (price != null && (price.signum() < 0 || price.stripTrailingZeros().scale() > 2 ||
            price.compareTo(new BigDecimal("9999999999999.99")) > 0)) throw invalid("El precio de venta no es válido.");
        return price;
    }

    private static Image decode(String input) {
        if (input.length() > 4 * ((IMAGE_LIMIT + 2) / 3)) throw invalid("La imagen debe pesar hasta 2 MB.");
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(input);
        } catch (IllegalArgumentException e) {
            throw invalid("El archivo de imagen no es válido.");
        }
        if (bytes.length > IMAGE_LIMIT) throw invalid("La imagen debe pesar hasta 2 MB.");
        if (starts(bytes, 0, 0xff, 0xd8, 0xff)) return new Image("image/jpeg", bytes);
        if (starts(bytes, 0, 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)) return new Image("image/png", bytes);
        if (starts(bytes, 0, 'R', 'I', 'F', 'F') && starts(bytes, 8, 'W', 'E', 'B', 'P'))
            return new Image("image/webp", bytes);
        throw invalid("Selecciona una imagen JPG, PNG o WebP.");
    }

    private static boolean starts(byte[] bytes, int offset, int... signature) {
        if (bytes.length < offset + signature.length) return false;
        for (int i = 0; i < signature.length; i++)
            if (Byte.toUnsignedInt(bytes[offset + i]) != signature[i]) return false;
        return true;
    }

    private static String uuid() { return UUID.randomUUID().toString(); }
    private static String automaticSku() { return "STK-" + uuid().substring(0, 12).toUpperCase(Locale.ROOT); }
    private static IdentityFailure missing() { return new IdentityFailure(404, "CATALOG_NOT_FOUND", "No se encontró el artículo."); }
    private static IdentityFailure invalid(String message) { return new IdentityFailure(400, "CATALOG_INVALID", message); }
    private static IdentityFailure conflict(String message) { return new IdentityFailure(409, "CATALOG_CONFLICT", message); }

    public record ProductInput(String name, String category, String description, ItemInput item,
                               List<String> variants, String keepVariant, String image, Boolean removeImage) {}
    /** cabys y taxRate (STK-013): null conserva el valor; texto vacío lo borra. */
    public record ItemInput(String sku, String barcode, String unit, BigDecimal price, Boolean setPrice, String cabys, String taxRate) {
        public ItemInput(String sku, String barcode, String unit, BigDecimal price, Boolean setPrice) {
            this(sku, barcode, unit, price, setPrice, null, null);
        }
    }
    public record Item(String id, String variantName, String sku, String barcode, String unit,
                       BigDecimal price, boolean active, boolean hasMovements, String cabys, String taxRate) {}
    public record Product(String id, String name, String category, String description,
                          boolean active, boolean hasImage, List<Item> items) {}
    public record Page(List<Product> products, List<String> categories, boolean hasMore) {}
    public record Image(String type, byte[] bytes) {}
}
