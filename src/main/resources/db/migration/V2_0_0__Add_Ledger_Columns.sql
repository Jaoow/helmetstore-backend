-- ================================================================================
-- Migration V2.0.0: Add Double-Entry Ledger Support
-- ================================================================================
-- Description: Adds columns to support professional accounting principles:
--   1. Freeze cost at the moment of sale (Historical Accuracy)
--   2. Add ledger flags to Transaction table for profit vs cash tracking
--   3. Migrate existing data to maintain backward compatibility
--
-- IMPORTANT: This migration does NOT create historical transactions.
--            Historical sales use Sale.totalProfit column.
--            Only NEW sales (after this migration) will use full ledger system.
-- ================================================================================

-- ================================================================================
-- PART 1: Add Historical Cost Snapshot to Sale Items
-- ================================================================================
-- Purpose: Store the exact cost at the moment of sale to prevent retroactive
-- profit calculation errors when future stock purchases change the average cost.
-- ================================================================================

ALTER TABLE sale_item
ADD COLUMN cost_basis_at_sale NUMERIC(10,2);

COMMENT ON COLUMN sale_item.cost_basis_at_sale IS
'Snapshot of the product average cost at the exact moment of sale. Used for accurate historical profit calculation.';

-- ================================================================================
-- PART 2: Add Ledger Flags to Transaction Table
-- ================================================================================
-- Purpose: Enable clean separation of Cash Flow vs Profitability reports without
-- complex JOINs. Each transaction explicitly declares its financial impact.
-- ================================================================================

-- Flag: Does this transaction affect Net Profit calculation?
-- TRUE: Revenue, COGS, Operational Expenses
-- FALSE: Owner Investments, Internal Transfers, Stock Purchases
ALTER TABLE transaction
ADD COLUMN affects_profit BOOLEAN DEFAULT FALSE NOT NULL;

COMMENT ON COLUMN transaction.affects_profit IS
'TRUE if this transaction should be included in Net Profit calculations (Revenue - COGS - Expenses). FALSE for cash-neutral events like stock purchases or owner capital.';

-- Flag: Does this transaction affect Cash/Bank balance?
-- TRUE: Sales Revenue, Bill Payments, Stock Purchases
-- FALSE: Cost of Goods Sold (accounting entry only)
ALTER TABLE transaction
ADD COLUMN affects_cash BOOLEAN DEFAULT FALSE NOT NULL;

COMMENT ON COLUMN transaction.affects_cash IS
'TRUE if this transaction physically moved money in/out of Cash or Bank. FALSE for non-cash accounting entries like COGS.';

-- Destination: Which wallet did the money go to/from?
-- 'CASH': Physical cash drawer
-- 'BANK': Bank account (PIX/Card)
-- NULL: Non-cash transactions (COGS, accruals)
ALTER TABLE transaction
ADD COLUMN wallet_destination VARCHAR(20);

COMMENT ON COLUMN transaction.wallet_destination IS
'Target account for cash-affecting transactions. Values: CASH (physical), BANK (digital), or NULL (non-cash entries like COGS).';

-- ================================================================================
-- PART 3: Initial Data Migration (Backward Compatibility)
-- ================================================================================
-- Purpose: Classify existing transactions so reports work immediately after upgrade
-- ================================================================================

-- 3.1: Update existing SALES (Revenue) - They affect both Cash and Profit
UPDATE transaction
SET affects_profit = TRUE,
    affects_cash = TRUE,
    wallet_destination = CASE
        WHEN payment_method = 'CASH' THEN 'CASH'
        ELSE 'BANK'
    END
WHERE detail = 'SALE' AND type = 'INCOME';

-- 3.2: Update existing EXPENSES - They affect both Cash and Profit
-- (excluding COST_OF_GOODS_SOLD and OWNER_INVESTMENT which are handled separately)
UPDATE transaction
SET affects_profit = TRUE,
    affects_cash = TRUE,
    wallet_destination = CASE
        WHEN payment_method = 'CASH' THEN 'CASH'
        ELSE 'BANK'
    END
WHERE type = 'EXPENSE'
  AND detail NOT IN ('COST_OF_GOODS_SOLD', 'OWNER_INVESTMENT', 'INTERNAL_TRANSFER_OUT');

-- 3.3: Update OWNER_INVESTMENT - Affects Cash but NOT Profit
UPDATE transaction
SET affects_profit = FALSE,
    affects_cash = TRUE,
    wallet_destination = CASE
        WHEN payment_method = 'CASH' THEN 'CASH'
        ELSE 'BANK'
    END
WHERE detail = 'OWNER_INVESTMENT';

-- 3.4: Update INTERNAL_TRANSFERS - Affect Cash but NOT Profit
-- CRITICAL FIX: payment_method indicates the wallet being AFFECTED by the transaction
-- INTERNAL_TRANSFER_OUT with payment_method='CASH' means money is LEAVING CASH wallet -> wallet_destination='CASH'
-- INTERNAL_TRANSFER_IN with payment_method='CASH' means money is ENTERING CASH wallet -> wallet_destination='CASH'
UPDATE transaction
SET affects_profit = FALSE,
    affects_cash = TRUE,
    wallet_destination = CASE
        WHEN detail = 'INTERNAL_TRANSFER_OUT' THEN
            CASE WHEN payment_method = 'CASH' THEN 'CASH' ELSE 'BANK' END
        WHEN detail = 'INTERNAL_TRANSFER_IN' THEN
            CASE WHEN payment_method = 'CASH' THEN 'CASH' ELSE 'BANK' END
    END
WHERE detail IN ('INTERNAL_TRANSFER_IN', 'INTERNAL_TRANSFER_OUT');

-- 3.5: Update COST_OF_GOODS_SOLD transactions (if any exist from Purchase Orders)
-- COGS affects Profit but NOT Cash (cost was already paid when purchasing inventory)
UPDATE transaction
SET affects_profit = TRUE,
    affects_cash = FALSE,
    wallet_destination = NULL  -- COGS doesn't affect any wallet
WHERE detail = 'COST_OF_GOODS_SOLD';

-- 3.6: Update Purchase Order transactions (Stock Purchases)
-- These are identified by: detail = 'COST_OF_GOODS_SOLD' AND reference LIKE 'PURCHASE_ORDER#%'
-- Purchase Orders affect Cash but NOT Profit (it's an asset exchange: cash for inventory)
-- Profit impact only happens later when the item is sold and a separate COGS transaction is created
--
-- IMPORTANT: This is different from COGS transactions created during sales:
-- - PURCHASE_ORDER transactions: affect_cash=TRUE, affects_profit=FALSE (buying inventory)
-- - COGS from sales: affect_cash=FALSE, affects_profit=TRUE (recognizing cost when selling)
UPDATE transaction
SET affects_profit = FALSE,
    affects_cash = TRUE,
    wallet_destination = CASE
        WHEN payment_method = 'CASH' THEN 'CASH'
        ELSE 'BANK'
    END
WHERE reference LIKE 'PURCHASE_ORDER#%'
  AND detail = 'COST_OF_GOODS_SOLD';

-- ================================================================================
-- PART 4: Populate Historical Cost Basis for Existing Sales
-- ================================================================================
-- Purpose: Fill cost_basis_at_sale for old sales using current average_cost
-- Note: This is an approximation since we don't have the exact historical cost
-- ================================================================================

UPDATE sale_item si
SET cost_basis_at_sale = ii.average_cost
FROM inventory_item ii
WHERE si.cost_basis_at_sale IS NULL
  AND ii.product_variant_id = si.product_variant_id
  AND ii.inventory_id = (SELECT inventory_id FROM sale WHERE id = si.sale_id);

-- ================================================================================
-- VERIFICATION QUERIES (Run manually after migration)
-- ================================================================================
-- Check 1: All transactions should have ledger flags set
-- SELECT COUNT(*) FROM transaction WHERE affects_profit IS NULL OR affects_cash IS NULL;
-- Expected: 0

-- Check 2: All existing sale items should have cost basis
-- SELECT COUNT(*) FROM sale_item WHERE cost_basis_at_sale IS NULL;
-- Expected: 0 (or only items with no matching inventory)

-- Check 3: Verify wallet destinations are properly set
-- SELECT wallet_destination, COUNT(*), SUM(amount) FROM transaction
-- WHERE affects_cash = TRUE
-- GROUP BY wallet_destination;
-- Expected: CASH and BANK with reasonable counts (NULL should be 0)

-- Check 4: Verify COGS transactions from SALES have correct flags
-- (COGS created during sales - should affect profit but NOT cash)
-- SELECT COUNT(*) FROM transaction
-- WHERE detail = 'COST_OF_GOODS_SOLD'
--   AND reference NOT LIKE 'PURCHASE_ORDER#%'
--   AND (affects_profit != TRUE OR affects_cash != FALSE);
-- Expected: 0

-- Check 5: Verify Purchase Orders (Stock Purchases) have correct flags
-- (COGS with PURCHASE_ORDER reference - should affect cash but NOT profit)
-- SELECT COUNT(*) FROM transaction
-- WHERE reference LIKE 'PURCHASE_ORDER#%'
--   AND detail = 'COST_OF_GOODS_SOLD'
--   AND (affects_profit != FALSE OR affects_cash != TRUE);
-- Expected: 0

-- Check 5b: List all Purchase Order transactions to verify
-- SELECT id, date, description, amount, payment_method, wallet_destination,
--        affects_profit, affects_cash, reference
-- FROM transaction
-- WHERE reference LIKE 'PURCHASE_ORDER#%'
-- ORDER BY date DESC;
-- Expected: All should have affects_profit=FALSE, affects_cash=TRUE, wallet set

-- Check 6: Verify INTERNAL_TRANSFERS have correct flags and destinations
-- SELECT detail, payment_method, wallet_destination, COUNT(*)
-- FROM transaction
-- WHERE detail IN ('INTERNAL_TRANSFER_IN', 'INTERNAL_TRANSFER_OUT')
-- GROUP BY detail, payment_method, wallet_destination
-- ORDER BY detail, payment_method;
-- Expected: Each combination should match the wallet being affected

-- ================================================================================
-- NOTE: This migration (V2_0_0) sets up the ledger flags and columns.
-- Migration V2_0_1 will handle converting EXPENSE amounts to negative values.
-- These migrations MUST run in order: V2_0_0 first, then V2_0_1.
-- ================================================================================
-- END OF MIGRATION
-- ================================================================================
