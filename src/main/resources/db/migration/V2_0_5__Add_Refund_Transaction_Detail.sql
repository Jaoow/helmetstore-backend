-- ================================================================================
-- Migration V2.0.5: Add REFUND Transaction Detail
-- ================================================================================
-- Description: Adds REFUND as a valid TransactionDetail value for canceled items.
-- 
-- PURPOSE: Enable tracking of refunds when order items are canceled, allowing
-- proper auditability without modifying original purchase transactions.
--   - REFUND = Money returned when canceling order items (affects cash, NOT profit)
-- ================================================================================

-- Drop the existing constraint
ALTER TABLE transaction DROP CONSTRAINT IF EXISTS transaction_detail_check;

-- Recreate the constraint with REFUND added
ALTER TABLE transaction ADD CONSTRAINT transaction_detail_check
CHECK (detail IN (
    'SALE',
    'OWNER_INVESTMENT',
    'EXTRA_INCOME',
    'REFUND',
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
