-- =========================================================
-- Migration V3.0.0: Migrate Products to Inventory Scope
-- =========================================================
-- This migration transforms the product system from global to inventory-scoped.
-- Each product will now belong to a specific inventory instead of being shared globally.
--
-- SUMMARY OF CHANGES:
-- 1. Add inventory_id column to product table
-- 2. Populate inventory_id for existing products based on their usage in inventory_item
-- 3. Handle products used in multiple inventories by creating duplicates
-- 4. Update all foreign key relationships and constraints
-- 5. Remove product_data table (no longer needed - salePrice moves to product)
-- 6. Add salePrice column directly to product table
-- 7. Update Category to be inventory-scoped as well
-- =========================================================

-- Step 1: Add new columns to product table (nullable for now)
ALTER TABLE product ADD COLUMN inventory_id BIGINT;
ALTER TABLE product ADD COLUMN sale_price DECIMAL(10, 2);

-- Step 2: Add inventory_id to categories table
ALTER TABLE categories ADD COLUMN inventory_id BIGINT;

-- Step 3: Create a temporary table to track product-inventory relationships
CREATE TEMPORARY TABLE temp_product_inventory_mapping (
    old_product_id BIGINT,
    new_product_id BIGINT,
    inventory_id BIGINT,
    is_original BOOLEAN,
    PRIMARY KEY (old_product_id, inventory_id)
);

-- Step 4: Identify all products and their associated inventories through inventory_item
-- For products in a single inventory, map directly
INSERT INTO temp_product_inventory_mapping (old_product_id, new_product_id, inventory_id, is_original)
SELECT DISTINCT 
    p.id as old_product_id,
    p.id as new_product_id,
    ii.inventory_id,
    true as is_original
FROM product p
INNER JOIN product_variant pv ON pv.product_id = p.id
INNER JOIN inventory_item ii ON ii.product_variant_id = pv.id
WHERE p.id IN (
    -- Products that exist in only one inventory
    SELECT p2.id
    FROM product p2
    INNER JOIN product_variant pv2 ON pv2.product_id = p2.id
    INNER JOIN inventory_item ii2 ON ii2.product_variant_id = pv2.id
    GROUP BY p2.id
    HAVING COUNT(DISTINCT ii2.inventory_id) = 1
);

-- Step 5: For products in multiple inventories, we need to duplicate them
-- First, identify these products
CREATE TEMPORARY TABLE temp_multi_inventory_products AS
SELECT DISTINCT p.id as product_id
FROM product p
INNER JOIN product_variant pv ON pv.product_id = p.id
INNER JOIN inventory_item ii ON ii.product_variant_id = pv.id
GROUP BY p.id
HAVING COUNT(DISTINCT ii.inventory_id) > 1;

-- Ensure product_variant sequence exists
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_class WHERE relname = 'product_variant_seq') THEN
        CREATE SEQUENCE product_variant_seq;
        -- Set sequence to max existing id + 1
        PERFORM setval('product_variant_seq', (SELECT COALESCE(MAX(id), 0) + 1 FROM product_variant));
    END IF;
END $$;

-- Step 6: Duplicate products for each inventory they appear in
-- We'll keep the original for the first inventory and create copies for others
DO $$
DECLARE
    product_record RECORD;
    inventory_record RECORD;
    new_product_id BIGINT;
    variant_record RECORD;
    new_variant_id BIGINT;
    is_first BOOLEAN;
BEGIN
    -- For each product that exists in multiple inventories
    FOR product_record IN 
        SELECT p.* FROM product p
        INNER JOIN temp_multi_inventory_products tmp ON tmp.product_id = p.id
    LOOP
        is_first := true;
        
        -- For each inventory this product is used in
        FOR inventory_record IN
            SELECT DISTINCT ii.inventory_id
            FROM product_variant pv
            INNER JOIN inventory_item ii ON ii.product_variant_id = pv.id
            WHERE pv.product_id = product_record.id
        LOOP
            IF is_first THEN
                -- Use the original product for the first inventory
                new_product_id := product_record.id;
                is_first := false;
                
                -- Map the original product to first inventory
                INSERT INTO temp_product_inventory_mapping (old_product_id, new_product_id, inventory_id, is_original)
                VALUES (product_record.id, new_product_id, inventory_record.inventory_id, true);
            ELSE
                -- Create a duplicate product for other inventories
                INSERT INTO product (model, color, img_url, category_id, inventory_id)
                VALUES (product_record.model, product_record.color, product_record.img_url, product_record.category_id, inventory_record.inventory_id)
                RETURNING id INTO new_product_id;
                
                -- Map the duplicate
                INSERT INTO temp_product_inventory_mapping (old_product_id, new_product_id, inventory_id, is_original)
                VALUES (product_record.id, new_product_id, inventory_record.inventory_id, false);
                
                -- Duplicate all variants for this product
                FOR variant_record IN
                    SELECT * FROM product_variant WHERE product_id = product_record.id
                LOOP
                    -- Insert new variant using nextval for ID generation
                    INSERT INTO product_variant (id, sku, size, product_id)
                    VALUES (nextval('product_variant_seq'), variant_record.sku, variant_record.size, new_product_id)
                    RETURNING id INTO new_variant_id;
                    
                    -- Update inventory_item references for this inventory
                    UPDATE inventory_item
                    SET product_variant_id = new_variant_id
                    WHERE product_variant_id = variant_record.id
                    AND inventory_id = inventory_record.inventory_id;
                    
                    -- Update purchase_order_item references
                    UPDATE purchase_order_item poi
                    SET product_variant_id = new_variant_id
                    FROM purchase_order po
                    WHERE poi.purchase_order_id = po.id
                    AND poi.product_variant_id = variant_record.id
                    AND po.inventory_id = inventory_record.inventory_id;
                    
                    -- Update sale_item references
                    UPDATE sale_item si
                    SET product_variant_id = new_variant_id
                    FROM sale s
                    WHERE si.sale_id = s.id
                    AND si.product_variant_id = variant_record.id
                    AND s.inventory_id = inventory_record.inventory_id;
                END LOOP;
            END IF;
        END LOOP;
    END LOOP;
END $$;

-- Step 7: Update inventory_id for all products based on the mapping
UPDATE product p
SET inventory_id = m.inventory_id
FROM temp_product_inventory_mapping m
WHERE p.id = m.new_product_id;

-- Step 8: Migrate sale_price from product_data to product table (if table exists)
DO $$
BEGIN
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'product_data') THEN
        UPDATE product p
        SET sale_price = pd.sale_price
        FROM product_data pd
        INNER JOIN temp_product_inventory_mapping m ON m.old_product_id = pd.product_id AND m.inventory_id = pd.inventory_id
        WHERE p.id = m.new_product_id;
    END IF;
END $$;

-- Step 9: Handle products without inventory_item (orphaned products)
-- Assign them to the first available inventory or delete them
DO $$
DECLARE
    first_inventory_id BIGINT;
BEGIN
    -- Get the first inventory
    SELECT id INTO first_inventory_id FROM inventory ORDER BY id LIMIT 1;
    
    -- Assign orphaned products to first inventory
    UPDATE product
    SET inventory_id = first_inventory_id
    WHERE inventory_id IS NULL AND first_inventory_id IS NOT NULL;
    
    -- Delete orphaned products if no inventory exists
    DELETE FROM product WHERE inventory_id IS NULL;
END $$;

-- Step 10: Remove old unique constraint on category name (if exists)
-- This constraint prevents duplicating categories with same name for different inventories
DO $$
DECLARE
    constraint_name TEXT;
BEGIN
    -- Find the unique constraint on categories.name
    SELECT conname INTO constraint_name
    FROM pg_constraint
    WHERE conrelid = 'categories'::regclass
    AND contype = 'u'
    AND array_length(conkey, 1) = 1
    AND conkey[1] = (SELECT attnum FROM pg_attribute WHERE attrelid = 'categories'::regclass AND attname = 'name');
    
    -- Drop it if found
    IF constraint_name IS NOT NULL THEN
        EXECUTE 'ALTER TABLE categories DROP CONSTRAINT ' || constraint_name;
    END IF;
END $$;

-- Step 11: Update categories to be inventory-scoped
-- First, duplicate categories that are used across multiple inventories
DO $$
DECLARE
    category_record RECORD;
    inventory_record RECORD;
    new_category_id BIGINT;
    is_first BOOLEAN;
    original_category_id BIGINT;
BEGIN
    FOR category_record IN 
        SELECT DISTINCT c.id, c.name
        FROM categories c
        INNER JOIN product p ON p.category_id = c.id
        WHERE p.inventory_id IS NOT NULL
    LOOP
        is_first := true;
        original_category_id := category_record.id;
        
        FOR inventory_record IN
            SELECT DISTINCT p.inventory_id
            FROM product p
            WHERE p.category_id = category_record.id
        LOOP
            IF is_first THEN
                -- Update the original category for the first inventory
                UPDATE categories
                SET inventory_id = inventory_record.inventory_id
                WHERE id = category_record.id;
                
                is_first := false;
            ELSE
                -- Create duplicate category for other inventories
                INSERT INTO categories (name, inventory_id)
                VALUES (category_record.name, inventory_record.inventory_id)
                RETURNING id INTO new_category_id;
                
                -- Update products to point to new category
                UPDATE product
                SET category_id = new_category_id
                WHERE category_id = original_category_id
                AND inventory_id = inventory_record.inventory_id;
            END IF;
        END LOOP;
    END LOOP;
    
    -- Handle categories without products
    UPDATE categories
    SET inventory_id = (SELECT id FROM inventory ORDER BY id LIMIT 1)
    WHERE inventory_id IS NULL;
END $$;

-- Step 12: Drop product_data table (no longer needed)
DROP TABLE IF EXISTS product_data;

-- Step 13: Make inventory_id NOT NULL now that all products have been assigned
ALTER TABLE product ALTER COLUMN inventory_id SET NOT NULL;
ALTER TABLE categories ALTER COLUMN inventory_id SET NOT NULL;

-- Step 14: Add foreign key constraints
ALTER TABLE product 
    ADD CONSTRAINT fk_product_inventory 
    FOREIGN KEY (inventory_id) 
    REFERENCES inventory(id) 
    ON DELETE CASCADE;

ALTER TABLE categories
    ADD CONSTRAINT fk_categories_inventory
    FOREIGN KEY (inventory_id)
    REFERENCES inventory(id)
    ON DELETE CASCADE;

-- Step 15: Add indexes for performance (skip if already exist)
CREATE INDEX IF NOT EXISTS idx_product_inventory ON product(inventory_id);
CREATE INDEX IF NOT EXISTS idx_product_inventory_category ON product(inventory_id, category_id);
CREATE INDEX IF NOT EXISTS idx_categories_inventory ON categories(inventory_id);

-- Step 16: Add new unique constraint on categories (name + inventory_id)
-- This ensures category names are unique within each inventory but can be reused across inventories
CREATE UNIQUE INDEX IF NOT EXISTS idx_categories_name_inventory ON categories(name, inventory_id);

-- Step 17: Clean up temporary tables
DROP TABLE IF EXISTS temp_product_inventory_mapping;
DROP TABLE IF EXISTS temp_multi_inventory_products;

-- =========================================================
-- Migration Complete!
-- =========================================================
-- Summary of changes:
-- - Products are now scoped to inventories
-- - Categories are now scoped to inventories
-- - Products used in multiple inventories have been duplicated
-- - Sale prices are now stored directly in the product table
-- - ProductData table has been removed
-- - All foreign key relationships have been updated
-- =========================================================
