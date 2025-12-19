-- ============================================================================
-- V2_2_0: Performance Optimization - Database Indexes
-- ============================================================================
-- Description: Adds database indexes to optimize query performance for high-volume data
-- Author: Performance Optimization Team
-- Date: 2025-12-18
-- ============================================================================

-- ============================================================================
-- SALE TABLE INDEXES
-- ============================================================================
-- Optimize queries that filter/sort sales by date
CREATE INDEX IF NOT EXISTS idx_sale_date ON sale(date);

-- Optimize queries that filter sales by inventory and sort by date
CREATE INDEX IF NOT EXISTS idx_sale_inventory_date ON sale(inventory_id, date);

-- ============================================================================
-- SALE_ITEM TABLE INDEXES
-- ============================================================================
-- Optimize lookups of items by sale (used in JOINs)
CREATE INDEX IF NOT EXISTS idx_sale_item_sale_id ON sale_item(sale_id);

-- Optimize lookups of items by product variant (used in inventory queries)
CREATE INDEX IF NOT EXISTS idx_sale_item_variant_id ON sale_item(product_variant_id);

-- ============================================================================
-- PURCHASE_ORDER TABLE INDEXES
-- ============================================================================
-- Optimize queries that filter/sort purchase orders by date
CREATE INDEX IF NOT EXISTS idx_purchase_order_date ON purchase_order(date);

-- Optimize queries that filter purchase orders by inventory and sort by date
CREATE INDEX IF NOT EXISTS idx_purchase_order_inventory_date ON purchase_order(inventory_id, date);

-- Optimize queries that search by order number
CREATE INDEX IF NOT EXISTS idx_purchase_order_number ON purchase_order(order_number);

-- ============================================================================
-- TRANSACTION TABLE INDEXES
-- ============================================================================
-- Optimize queries that filter/sort transactions by date
CREATE INDEX IF NOT EXISTS idx_transaction_date ON transaction(date);

-- Optimize queries that filter transactions by account and sort by date
CREATE INDEX IF NOT EXISTS idx_transaction_account_date ON transaction(account_id, date);

-- Optimize queries that search by reference (e.g., SALE#123)
CREATE INDEX IF NOT EXISTS idx_transaction_reference ON transaction(reference);

-- Optimize profit calculation queries (affectsProfit flag + date filtering)
CREATE INDEX IF NOT EXISTS idx_transaction_affects_profit ON transaction(affects_profit, date);

-- Optimize wallet balance queries (walletDestination + date filtering)
CREATE INDEX IF NOT EXISTS idx_transaction_wallet_dest ON transaction(wallet_destination, date);

-- Optimize queries that filter by transaction type and detail
CREATE INDEX IF NOT EXISTS idx_transaction_type_detail ON transaction(type, detail);

-- ============================================================================
-- PRODUCT TABLE INDEXES
-- ============================================================================
-- Optimize queries that search/sort products by model and color
CREATE INDEX IF NOT EXISTS idx_product_model_color ON product(model, color);

-- Optimize queries that filter products by category
CREATE INDEX IF NOT EXISTS idx_product_category ON product(category_id);

-- ============================================================================
-- INVENTORY_ITEM TABLE INDEXES
-- ============================================================================
-- Optimize lookups by product variant
CREATE INDEX IF NOT EXISTS idx_inventory_item_variant ON inventory_item(product_variant_id);

-- Optimize queries that search for specific inventory items (composite unique constraint)
-- This also prevents duplicate entries for the same variant in the same inventory
CREATE UNIQUE INDEX IF NOT EXISTS idx_inventory_item_inventory_variant 
    ON inventory_item(inventory_id, product_variant_id);

-- ============================================================================
-- USER TABLE INDEXES
-- ============================================================================
-- Optimize user lookups by email (most common query)
CREATE UNIQUE INDEX IF NOT EXISTS idx_user_email ON app_user(email);

-- Optimize reverse lookups from inventory to user
CREATE INDEX IF NOT EXISTS idx_user_inventory ON app_user(inventory_id);

-- ============================================================================
-- NOTES
-- ============================================================================
-- 1. These indexes significantly improve query performance for:
--    - Sales history queries (by date range, inventory)
--    - Transaction reports (profit, cash flow, wallet balances)
--    - Product/inventory lookups
--    - Financial calculations across large datasets
--
-- 2. Indexes are created with "IF NOT EXISTS" to prevent errors on re-run
--
-- 3. Monitor index usage in production with:
--    SELECT * FROM pg_stat_user_indexes WHERE schemaname = 'public';
--
-- 4. Consider periodic index maintenance:
--    REINDEX TABLE <table_name>;
--
-- ============================================================================
