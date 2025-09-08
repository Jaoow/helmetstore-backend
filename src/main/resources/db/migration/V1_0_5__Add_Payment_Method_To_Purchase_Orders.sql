-- Add payment method column to purchase orders table
ALTER TABLE purchase_order ADD COLUMN payment_method VARCHAR(20) NOT NULL DEFAULT 'PIX';

-- Update existing records to use PIX as default (since they were all PIX before)
UPDATE purchase_order SET payment_method = 'PIX' WHERE payment_method IS NULL;

-- Add constraint to ensure valid payment methods
ALTER TABLE purchase_order ADD CONSTRAINT chk_purchase_order_payment_method
    CHECK (payment_method IN ('CASH', 'PIX', 'CARD'));
