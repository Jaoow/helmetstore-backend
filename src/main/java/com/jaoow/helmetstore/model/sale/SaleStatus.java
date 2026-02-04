package com.jaoow.helmetstore.model.sale;

/**
 * Sale status indicates the commercial state of the operation.
 * Financial refund is controlled by refund flag and amount, not by status.
 */
public enum SaleStatus {
    /**
     * Completed and confirmed sale
     */
    COMPLETED,

    /**
     * Fully cancelled sale
     */
    CANCELLED,

    /**
     * Partially cancelled sale (some items were cancelled)
     */
    PARTIALLY_CANCELLED,

    /**
     * Sale with exchanged items (not a cancellation, but an adjustment)
     * The original sale remains as part of exchange history
     */
    EXCHANGED
}
