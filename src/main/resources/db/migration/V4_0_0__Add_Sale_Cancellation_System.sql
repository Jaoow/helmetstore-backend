-- Migration: Add Sale Cancellation System
-- Version: V4_0_0
-- Description: Adds cancellation support to sales with status tracking, refund control, and partial cancellation

-- ============================================================================
-- SALE TABLE - Add cancellation and refund fields
-- ============================================================================

-- Add status column (defaults to COMPLETED for existing sales)
ALTER TABLE sale 
ADD COLUMN status VARCHAR(30) NOT NULL DEFAULT 'COMPLETED';

-- Add cancellation tracking fields
ALTER TABLE sale 
ADD COLUMN cancelled_at TIMESTAMP,
ADD COLUMN cancelled_by VARCHAR(255),
ADD COLUMN cancellation_reason VARCHAR(50),
ADD COLUMN cancellation_notes TEXT;

-- Add refund tracking fields
ALTER TABLE sale 
ADD COLUMN has_refund BOOLEAN NOT NULL DEFAULT FALSE,
ADD COLUMN refund_amount DECIMAL(12,2),
ADD COLUMN refund_payment_method VARCHAR(20),
ADD COLUMN refund_transaction_id BIGINT;

-- ============================================================================
-- SALE_ITEM TABLE - Add cancellation fields for partial cancellations
-- ============================================================================

ALTER TABLE sale_item 
ADD COLUMN is_cancelled BOOLEAN NOT NULL DEFAULT FALSE,
ADD COLUMN cancelled_quantity INT;

-- ============================================================================
-- INDEXES for performance
-- ============================================================================

CREATE INDEX idx_sale_status ON sale(status);

-- ============================================================================
-- CONSTRAINTS
-- ============================================================================

-- Ensure refund amount is not negative
ALTER TABLE sale 
ADD CONSTRAINT chk_refund_amount_positive CHECK (refund_amount IS NULL OR refund_amount >= 0);

-- Ensure cancelled_quantity is not negative
ALTER TABLE sale_item 
ADD CONSTRAINT chk_cancelled_quantity_positive CHECK (cancelled_quantity IS NULL OR cancelled_quantity >= 0);

-- Ensure cancelled_quantity does not exceed original quantity
ALTER TABLE sale_item 
ADD CONSTRAINT chk_cancelled_quantity_max CHECK (cancelled_quantity IS NULL OR cancelled_quantity <= quantity);

-- ============================================================================
-- COMMENTS for documentation
-- ============================================================================

COMMENT ON COLUMN sale.status IS 'Sale status: COMPLETED, CANCELLED, or PARTIALLY_CANCELLED';
COMMENT ON COLUMN sale.cancelled_at IS 'Data e hora do cancelamento';
COMMENT ON COLUMN sale.cancelled_by IS 'Usuário que realizou o cancelamento';
COMMENT ON COLUMN sale.cancellation_reason IS 'Motivo do cancelamento';
COMMENT ON COLUMN sale.cancellation_notes IS 'Observações adicionais sobre o cancelamento';
COMMENT ON COLUMN sale.has_refund IS 'Flag indicando se houve estorno/reembolso';
COMMENT ON COLUMN sale.refund_amount IS 'Valor do estorno (pode ser total ou parcial)';
COMMENT ON COLUMN sale.refund_payment_method IS 'Método utilizado no reembolso';
COMMENT ON COLUMN sale.refund_transaction_id IS 'ID da transação de reembolso';
COMMENT ON COLUMN sale_item.is_cancelled IS 'Flag indicando se o item foi cancelado';
COMMENT ON COLUMN sale_item.cancelled_quantity IS 'Quantidade cancelada do item';
