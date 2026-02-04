-- ================================================================================
-- Migration V5.0.3: Add COGS_REVERSAL and SALE_REFUND Transaction Details
-- ================================================================================
-- Description: Adds new TransactionDetail values required for proper accounting
-- of sales cancellations and product exchanges.
--
-- PURPOSE:
--   - COGS_REVERSAL = Reverses Cost of Goods Sold when products return to inventory
--                     (affects profit, NOT cash - it's an accounting adjustment)
--   - SALE_REFUND = Refund of a sale to customer (affects profit AND cash)
--                   Different from REFUND which doesn't affect profit
-- ================================================================================

-- Drop the existing constraint
ALTER TABLE transaction DROP CONSTRAINT IF EXISTS transaction_detail_check;

-- Recreate the constraint with new values added
ALTER TABLE transaction ADD CONSTRAINT transaction_detail_check
CHECK (detail IN (
    -- INCOME (entries)
    'SALE',
    'OWNER_INVESTMENT',
    'EXTRA_INCOME',
    'COGS_REVERSAL',         -- NEW: Reverses COGS when product returns to stock

    -- EXPENSES (exits)
    'INVENTORY_PURCHASE',
    'COST_OF_GOODS_SOLD',
    'SALE_REFUND',           -- NEW: Refund of a sale (affects profit + cash)
    'REFUND',                -- Generic refund (doesn't affect profit)
    'FIXED_EXPENSE',
    'VARIABLE_EXPENSE',
    'PRO_LABORE',
    'PROFIT_DISTRIBUTION',
    'INVESTMENT',
    'TAX',
    'PERSONAL_EXPENSE',
    'OTHER_EXPENSE',

    -- TRANSFERS
    'INTERNAL_TRANSFER_OUT',
    'INTERNAL_TRANSFER_IN'
));

-- ================================================================================
-- Documentation
-- ================================================================================
COMMENT ON CONSTRAINT transaction_detail_check ON transaction IS
'Validates TransactionDetail enum values including COGS_REVERSAL and SALE_REFUND';
