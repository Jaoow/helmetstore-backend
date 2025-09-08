-- Update transaction_detail_check constraint to include new transfer types
-- Remove the old constraint
ALTER TABLE transaction DROP CONSTRAINT IF EXISTS transaction_detail_check;

-- Add the new constraint with updated transaction details
ALTER TABLE transaction ADD CONSTRAINT transaction_detail_check
CHECK (detail IN (
    'SALE',
    'OWNER_INVESTMENT',
    'EXTRA_INCOME',
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
