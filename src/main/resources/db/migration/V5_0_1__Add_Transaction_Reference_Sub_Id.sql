-- Migration: Add reference_sub_id to transaction table
-- Version: V5_0_1
-- Description: Adds reference_sub_id column to track specific sub-entities and prevent duplicate transactions

-- Add reference_sub_id column
ALTER TABLE transaction
ADD COLUMN reference_sub_id BIGINT;

-- Create index for reference_sub_id
CREATE INDEX idx_transaction_reference_sub_id ON transaction(reference_sub_id);

-- Add comment for documentation
COMMENT ON COLUMN transaction.reference_sub_id IS
'Sub-reference ID for duplicate prevention. Used to track specific sub-entities (e.g., SalePayment.id within a Sale) to prevent creating duplicate transactions in exchange scenarios.';
