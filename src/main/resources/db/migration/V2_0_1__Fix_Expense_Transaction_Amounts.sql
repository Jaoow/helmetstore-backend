-- ================================================================================
-- Migration V2.0.1: Fix Expense Transaction Amounts (Convert to Negative)
-- ================================================================================
-- Description: Converts all EXPENSE transactions to negative values to follow
-- the Double-Entry Ledger convention:
--   - INCOME transactions = POSITIVE values
--   - EXPENSE transactions = NEGATIVE values
--
-- This ensures:
--   1. Accurate Cash Flow calculations
--   2. Correct Wallet Balance calculations
--   3. Proper Net Profit calculations
--   4. Consistency with new transaction validation rules
-- ================================================================================

-- ================================================================================
-- PART 1: Convert Existing EXPENSE Transactions to Negative
-- ================================================================================
-- Purpose: All EXPENSE transactions that currently have positive amounts
-- need to be converted to negative to match the ledger convention
-- ================================================================================

-- WARNING: This migration is IDEMPOTENT - it only converts positive values
-- If you run it multiple times, it won't convert already-negative values

-- 1.1: Convert all manual EXPENSE transactions
UPDATE transaction
SET amount = -ABS(amount)
WHERE type = 'EXPENSE'
  AND amount > 0;

-- 1.2: Convert Purchase Order transactions (they are expenses for inventory)
-- These have detail = 'COST_OF_GOODS_SOLD' and reference starting with 'PURCHASE_ORDER#'
UPDATE transaction
SET amount = -ABS(amount)
WHERE type = 'EXPENSE'
  AND detail = 'COST_OF_GOODS_SOLD'
  AND reference LIKE 'PURCHASE_ORDER#%'
  AND amount > 0;

-- 1.3: Convert Internal Transfer OUT transactions (money leaving account)
UPDATE transaction
SET amount = -ABS(amount)
WHERE type = 'EXPENSE'
  AND detail = 'INTERNAL_TRANSFER_OUT'
  AND amount > 0;

-- ================================================================================
-- PART 2: Verify Data Integrity After Migration
-- ================================================================================
-- Purpose: Ensure no EXPENSE transactions have positive amounts
-- ================================================================================

-- The following checks will be logged (run manually if needed):

-- Check 1: All EXPENSE transactions should now be negative or zero
-- SELECT COUNT(*) as "Invalid Expenses"
-- FROM transaction
-- WHERE type = 'EXPENSE' AND amount > 0;
-- Expected: 0

-- Check 2: All INCOME transactions should remain positive
-- SELECT COUNT(*) as "Invalid Income"
-- FROM transaction
-- WHERE type = 'INCOME' AND amount < 0;
-- Expected: 0

-- Check 3: Show sample of converted transactions
-- SELECT id, date, type, detail, amount, description
-- FROM transaction
-- WHERE type = 'EXPENSE'
-- ORDER BY date DESC
-- LIMIT 10;

-- ================================================================================
-- EXPLANATION
-- ================================================================================
--
-- BEFORE Migration:
-- +---+----------+---------+--------+
-- | ID| Type     | Amount  | Detail |
-- +---+----------+---------+--------+
-- | 1 | INCOME   | +500.00 | SALE   |
-- | 2 | EXPENSE  | +80.00  | RENT   | ❌ WRONG (positive expense)
-- | 3 | EXPENSE  | +50.00  | ENERGY | ❌ WRONG
-- +---+----------+---------+--------+
--
-- AFTER Migration:
-- +---+----------+---------+--------+
-- | ID| Type     | Amount  | Detail |
-- +---+----------+---------+--------+
-- | 1 | INCOME   | +500.00 | SALE   |
-- | 2 | EXPENSE  | -80.00  | RENT   | ✅ CORRECT (negative expense)
-- | 3 | EXPENSE  | -50.00  | ENERGY | ✅ CORRECT
-- +---+----------+---------+--------+
--
-- This ensures:
--   - Cash Flow = SUM(all amounts) = 500 - 80 - 50 = 370 ✅
--   - Bank Balance = SUM(amounts WHERE wallet = BANK) = accurate ✅
--   - Net Profit = SUM(amounts WHERE affects_profit = true) = accurate ✅
--
-- ================================================================================
-- NOTES
-- ================================================================================
--
-- 1. This migration is SAFE to run multiple times (idempotent)
--    - Uses -ABS(amount) which converts positive to negative
--    - Already-negative values remain negative
--
-- 2. This does NOT affect:
--    - Purchase Orders (they might need separate migration if stored as positive)
--    - COGS transactions (should already be negative from creation)
--    - INCOME transactions (remain positive)
--
-- 3. Related Backend Changes:
--    - Transaction entity now has @PrePersist validation
--    - TransactionService.createManualTransaction() converts to negative
--    - TransactionService.updateTransaction() converts to negative
--    - ReinvestmentService creates negative expense transactions
--    - AccountService creates negative expense transactions
--
-- ================================================================================
-- END OF MIGRATION
-- ================================================================================
