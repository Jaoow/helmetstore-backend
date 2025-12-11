-- ================================================================================
-- Migration V2.0.4: Fix Purchase Order Transaction Detail
-- ================================================================================
-- Description: Corrects the semantic naming of Purchase Order transactions.
-- 
-- PROBLEM: Historical Purchase Order transactions were incorrectly saved with
-- detail = 'COST_OF_GOODS_SOLD', but this is semantically wrong:
--   - INVENTORY_PURCHASE = Buying stock (expense, affects cash, NOT profit)
--   - COST_OF_GOODS_SOLD = Cost recognized when selling (affects profit, NOT cash)
--
-- SOLUTION: Update all Purchase Order transactions to use the correct enum value.
-- ================================================================================

-- ================================================================================
-- PART 1: Add INVENTORY_PURCHASE to the Check Constraint
-- ================================================================================
-- Purpose: Allow 'INVENTORY_PURCHASE' as a valid value in the detail column
-- before we try to update existing rows
-- ================================================================================

-- Drop the existing constraint (if it exists)
ALTER TABLE transaction DROP CONSTRAINT IF EXISTS transaction_detail_check;

-- Recreate the constraint with all valid TransactionDetail enum values
ALTER TABLE transaction ADD CONSTRAINT transaction_detail_check
CHECK (detail IN (
    'SALE',
    'OWNER_INVESTMENT',
    'EXTRA_INCOME',
    'INVENTORY_PURCHASE',
    'COST_OF_GOODS_SOLD',
    'FIXED_EXPENSE',
    'VARIABLE_EXPENSE',
    'PRO_LABORE',
    'PROFIT_DISTRIBUTION',
    'INVESTMENT',
    'TAX',
    'PERSONAL_EXPENSE',
    'OTHER_EXPENSE',
    'INTERNAL_TRANSFER_OUT',
    'INTERNAL_TRANSFER_IN'
));

-- ================================================================================
-- PART 2: Update Purchase Order Transactions Detail
-- ================================================================================
-- Purpose: Change detail from 'COST_OF_GOODS_SOLD' to 'INVENTORY_PURCHASE'
-- for all transactions that represent stock purchases (Purchase Orders)
-- ================================================================================

-- Identify Purchase Order transactions by their reference prefix
-- These are the transactions created when purchasing inventory from suppliers
UPDATE transaction
SET detail = 'INVENTORY_PURCHASE'
WHERE reference LIKE 'PURCHASE_ORDER#%'
  AND detail = 'COST_OF_GOODS_SOLD';

-- ================================================================================
-- VERIFICATION QUERIES (Run manually after migration)
-- ================================================================================

-- Check 1: Verify no Purchase Orders still have COST_OF_GOODS_SOLD
-- SELECT COUNT(*) FROM transaction
-- WHERE reference LIKE 'PURCHASE_ORDER#%'
--   AND detail = 'COST_OF_GOODS_SOLD';
-- Expected: 0

-- Check 2: Verify all Purchase Orders now use INVENTORY_PURCHASE
-- SELECT COUNT(*) FROM transaction
-- WHERE reference LIKE 'PURCHASE_ORDER#%'
--   AND detail = 'INVENTORY_PURCHASE';
-- Expected: Number of all Purchase Order transactions

-- Check 3: Verify INVENTORY_PURCHASE flags are correct
-- SELECT COUNT(*) FROM transaction
-- WHERE detail = 'INVENTORY_PURCHASE'
--   AND (affects_profit != FALSE OR affects_cash != TRUE);
-- Expected: 0 (all should have affects_profit=FALSE, affects_cash=TRUE)

-- Check 4: List all INVENTORY_PURCHASE transactions to verify
-- SELECT id, date, description, amount, payment_method, wallet_destination,
--        affects_profit, affects_cash, reference
-- FROM transaction
-- WHERE detail = 'INVENTORY_PURCHASE'
-- ORDER BY date DESC
-- LIMIT 20;
-- Expected: All should be Purchase Orders with correct flags

-- Check 5: Verify COST_OF_GOODS_SOLD now only contains Sales COGS
-- SELECT DISTINCT reference FROM transaction
-- WHERE detail = 'COST_OF_GOODS_SOLD'
-- LIMIT 10;
-- Expected: References should be like 'SALE#123', NOT 'PURCHASE_ORDER#123'

-- ================================================================================
-- SEMANTIC DISTINCTION (for future reference):
-- ================================================================================
-- INVENTORY_PURCHASE:
--   - When: Buying stock from suppliers (Purchase Orders)
--   - Flags: affects_profit=FALSE, affects_cash=TRUE
--   - Reason: It's an asset exchange (cash → inventory), not a cost yet
--   - Reference format: PURCHASE_ORDER#<id>
--
-- COST_OF_GOODS_SOLD:
--   - When: Selling products to customers (Sales)
--   - Flags: affects_profit=TRUE, affects_cash=FALSE
--   - Reason: Recognizes the cost of inventory when revenue is earned
--   - Reference format: SALE#<id>
-- ================================================================================
-- END OF MIGRATION
-- ================================================================================
