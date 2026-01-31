-- Migration: Add Product Exchange System
-- Version: V5_0_0
-- Description: Adds product exchange functionality with full traceability and financial tracking

-- ============================================================================
-- PRODUCT_EXCHANGE TABLE
-- ============================================================================

CREATE TABLE product_exchange (
    id BIGSERIAL PRIMARY KEY,
    exchange_date TIMESTAMP NOT NULL,
    original_sale_id BIGINT NOT NULL,
    new_sale_id BIGINT NOT NULL,
    reason VARCHAR(50) NOT NULL,
    notes TEXT,
    processed_by VARCHAR(255) NOT NULL,

    -- Financial tracking
    returned_amount DECIMAL(12,2) NOT NULL,
    new_sale_amount DECIMAL(12,2) NOT NULL,
    amount_difference DECIMAL(12,2) NOT NULL,

      -- Refund tracking (when applicable)
    refund_amount DECIMAL(12,2),
    refund_payment_method VARCHAR(20),
    refund_transaction_id BIGINT,

    -- Foreign keys
    CONSTRAINT fk_exchange_original_sale FOREIGN KEY (original_sale_id)
        REFERENCES sale(id) ON DELETE RESTRICT,
    CONSTRAINT fk_exchange_new_sale FOREIGN KEY (new_sale_id)
        REFERENCES sale(id) ON DELETE RESTRICT,
    CONSTRAINT fk_exchange_refund_transaction FOREIGN KEY (refund_transaction_id)
        REFERENCES transaction(id) ON DELETE SET NULL
);

-- ============================================================================
-- INDEXES for performance
-- ============================================================================

CREATE INDEX idx_exchange_original_sale ON product_exchange(original_sale_id);
CREATE INDEX idx_exchange_new_sale ON product_exchange(new_sale_id);
CREATE INDEX idx_exchange_date ON product_exchange(exchange_date);

-- ============================================================================
-- CONSTRAINTS for data integrity
-- ============================================================================

-- Ensure amounts are not negative
ALTER TABLE product_exchange
ADD CONSTRAINT chk_returned_amount_positive CHECK (returned_amount >= 0);

ALTER TABLE product_exchange
ADD CONSTRAINT chk_new_sale_amount_positive CHECK (new_sale_amount >= 0);

ALTER TABLE product_exchange
ADD CONSTRAINT chk_refund_amount_positive CHECK (refund_amount IS NULL OR refund_amount >= 0);

-- ============================================================================
-- COMMENTS for documentation
-- ============================================================================

COMMENT ON TABLE product_exchange IS
'Records product exchange operations, linking original and new sales with full financial tracking';

COMMENT ON COLUMN product_exchange.exchange_date IS
'Date and time when the exchange was processed';

COMMENT ON COLUMN product_exchange.original_sale_id IS
'Reference to the sale from which products were returned';

COMMENT ON COLUMN product_exchange.new_sale_id IS
'Reference to the new sale created with exchanged products';

COMMENT ON COLUMN product_exchange.reason IS
'Reason for the exchange (DEFEITO, TAMANHO, PREFERENCIA, etc)';

COMMENT ON COLUMN product_exchange.returned_amount IS
'Total amount of returned items from original sale';

COMMENT ON COLUMN product_exchange.new_sale_amount IS
'Total amount of the new sale';

COMMENT ON COLUMN product_exchange.amount_difference IS
'Difference between new sale and returned amount (positive = customer pays, negative = customer receives refund)';
