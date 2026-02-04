package com.jaoow.helmetstore.dto.sale;

import com.jaoow.helmetstore.model.balance.PaymentMethod;
import com.jaoow.helmetstore.model.sale.ExchangeReason;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for product exchange operation
 *
 * Contains complete information about the exchange result,
 * including financial details and references to related entities.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductExchangeResponseDTO {

    /**
     * ID of the exchange record
     */
    private Long exchangeId;

    /**
     * Date and time of the exchange
     */
    private LocalDateTime exchangeDate;

    /**
     * ID of the original sale
     */
    private Long originalSaleId;

    /**
     * ID of the new sale created
     */
    private Long newSaleId;

    /**
     * Reason for the exchange
     */
    private ExchangeReason reason;

    /**
     * Additional notes
     */
    private String notes;

    /**
     * User who processed the exchange
     */
    private String processedBy;

    // ============================================================================
    // FINANCIAL SUMMARY
    // ============================================================================

    /**
     * Total amount returned from the original sale
     */
    private BigDecimal returnedAmount;

    /**
     * Total amount of the new sale
     */
    private BigDecimal newSaleAmount;

    /**
     * Difference between new sale and returned amount
     * - Positive: customer pays additional
     * - Negative: customer receives refund
     * - Zero: no financial movement
     */
    private BigDecimal amountDifference;

    // ============================================================================
    // REFUND INFORMATION
    // ============================================================================

    /**
     * Whether a refund was issued
     */
    private Boolean hasRefund;

    /**
     * Refund amount (if applicable)
     */
    private BigDecimal refundAmount;

    /**
     * Refund payment method (if applicable)
     */
    private PaymentMethod refundPaymentMethod;

    /**
     * ID of the refund transaction (if applicable)
     */
    private Long refundTransactionId;

    // ============================================================================
    // ADDITIONAL CHARGE INFORMATION
    // ============================================================================

    /**
     * Whether an additional charge was required
     */
    private Boolean hasAdditionalCharge;

    /**
     * Additional charge amount (if applicable)
     */
    private BigDecimal additionalChargeAmount;

    /**
     * Success message
     */
    private String message;
}
