-- ============================================================================
-- V3_1_0: Advanced Performance Optimization - Additional Database Indexes
-- ============================================================================
-- Description: Adds advanced database indexes based on performance report analysis
-- Author: Performance Optimization - January 2026
-- Date: 2026-01-26
-- ============================================================================
-- Based on performance_report_20260126_215920.json analysis:
-- - /sales/history: up to 49 queries, N+1 problem detected
-- - /account/profit-summary: 5284ms, 14 queries
-- - /account/cash-flow-summary: 4640ms, 6 queries
-- ============================================================================

-- ============================================================================
-- SALE_PAYMENT TABLE INDEXES
-- ============================================================================
-- Optimize payment lookups by sale_id (N+1 problem fix)
-- Query: "select p?_?.sale_id,p?_?.id,p?_?.amount,p?_?.payment_method from sale_payment"
CREATE INDEX IF NOT EXISTS idx_sale_payment_sale_id ON sale_payment(sale_id);

-- ============================================================================
-- TRANSACTION TABLE - ADVANCED FILTERS
-- ============================================================================
-- Optimize profit calculations with affects_profit flag
CREATE INDEX IF NOT EXISTS idx_transaction_affects_profit ON transaction(affects_profit, date) WHERE affects_profit = true;

-- Optimize cash flow calculations with affects_cash flag
CREATE INDEX IF NOT EXISTS idx_transaction_affects_cash ON transaction(affects_cash, date) WHERE affects_cash = true;

-- Optimize wallet-specific queries
CREATE INDEX IF NOT EXISTS idx_transaction_wallet_destination ON transaction(wallet_destination, date);

-- Composite index for account-based queries with ledger flags
CREATE INDEX IF NOT EXISTS idx_transaction_account_ledger ON transaction(account_id, affects_profit, affects_cash, date);

-- ============================================================================
-- PRODUCT VARIANT TABLE INDEXES
-- ============================================================================
-- Optimize product variant lookups in sale items
CREATE INDEX IF NOT EXISTS idx_product_variant_product_id ON product_variant(product_id);

-- ============================================================================
-- INVENTORY_ITEM TABLE INDEXES
-- ============================================================================
-- Optimize inventory lookups by variant
CREATE INDEX IF NOT EXISTS idx_inventory_item_variant ON inventory_item(inventory_id, product_variant_id);

-- ============================================================================
-- CATEGORY TABLE INDEXES
-- ============================================================================
-- Optimize category lookups by inventory
-- Query: "select c?_?.id,c?_?.inventory_id,c?_?.name from categories"
CREATE INDEX IF NOT EXISTS idx_category_inventory_id ON categories(inventory_id);

-- ============================================================================
-- USERS_ROLES TABLE INDEXES
-- ============================================================================
-- Optimize role lookups by user
-- Query: "select r?_?.user_id,r?_?.id,r?_?.name from users_roles"
CREATE INDEX IF NOT EXISTS idx_users_roles_user_id ON users_roles(user_id);

-- ============================================================================
-- ACCOUNT TABLE INDEXES
-- ============================================================================
-- Optimize account lookups by user and type
CREATE INDEX IF NOT EXISTS idx_account_user_type ON account(user_id, type);

-- ============================================================================
-- COVERING INDEXES FOR COMMON QUERIES
-- ============================================================================
-- Covering index for sale history queries (includes most frequently accessed columns)
CREATE INDEX IF NOT EXISTS idx_sale_history_covering
ON sale(inventory_id, date, id, total_amount, total_profit)
WHERE inventory_id IS NOT NULL;

-- Covering index for transaction summary queries
CREATE INDEX IF NOT EXISTS idx_transaction_summary_covering
ON transaction(account_id, affects_profit, affects_cash, date, amount)
WHERE account_id IS NOT NULL;

-- ============================================================================
-- STATISTICS UPDATE
-- ============================================================================
-- Update table statistics to help query planner make better decisions
ANALYZE sale;
ANALYZE sale_item;
ANALYZE sale_payment;
ANALYZE transaction;
ANALYZE product_variant;
ANALYZE inventory_item;
