-- =========================================================
-- Migration V999.0.0: Fix Inventory-User Relationship
-- =========================================================
-- Problem: The database has 'owner_id' column in inventory table,
-- but JPA expects 'inventory_id' in app_user table.
-- This migration fixes the schema to match JPA mappings.
-- =========================================================

-- Step 1: Check if owner_id column exists in inventory table
DO $$ 
BEGIN
    -- If owner_id exists, we need to migrate the relationship
    IF EXISTS (
        SELECT 1 
        FROM information_schema.columns 
        WHERE table_name = 'inventory' 
        AND column_name = 'owner_id'
    ) THEN
        
        -- Step 2: Add inventory_id to app_user if it doesn't exist
        IF NOT EXISTS (
            SELECT 1 
            FROM information_schema.columns 
            WHERE table_name = 'app_user' 
            AND column_name = 'inventory_id'
        ) THEN
            ALTER TABLE app_user 
            ADD COLUMN inventory_id BIGINT UNIQUE;
            
            -- Create the foreign key
            ALTER TABLE app_user 
            ADD CONSTRAINT fk_user_inventory 
            FOREIGN KEY (inventory_id) REFERENCES inventory(id);
            
            -- Create index
            CREATE INDEX idx_user_inventory ON app_user(inventory_id);
        END IF;
        
        -- Step 3: Migrate data from inventory.owner_id to app_user.inventory_id
        UPDATE app_user u
        SET inventory_id = i.id
        FROM inventory i
        WHERE i.owner_id = u.id
        AND u.inventory_id IS NULL;
        
        -- Step 4: Drop the old owner_id column and its constraints
        ALTER TABLE inventory DROP CONSTRAINT IF EXISTS fk_inventory_owner;
        ALTER TABLE inventory DROP COLUMN IF EXISTS owner_id;
        
        RAISE NOTICE 'Successfully migrated inventory-user relationship from owner_id to inventory_id';
    ELSE
        RAISE NOTICE 'No migration needed - schema already correct';
    END IF;
END $$;
