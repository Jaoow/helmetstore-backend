-- Add is_derived_from_exchange column to Sale table
-- This flag indicates if a sale is part of an exchange operation
-- and should NOT generate its own financial transactions

ALTER TABLE sale
ADD COLUMN is_derived_from_exchange BOOLEAN NOT NULL DEFAULT FALSE;

-- Add comment for documentation
COMMENT ON COLUMN sale.is_derived_from_exchange IS
'Indicates if this sale is derived from an exchange operation. When true, the sale does NOT generate financial transactions (REVENUE/COGS), as it represents a continuation/reappointment of the original sale.';
