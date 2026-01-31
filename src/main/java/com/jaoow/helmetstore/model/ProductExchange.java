package com.jaoow.helmetstore.model;

import com.jaoow.helmetstore.model.balance.PaymentMethod;
import com.jaoow.helmetstore.model.sale.ExchangeReason;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity: ProductExchange
 *
 * Represents a product exchange transaction, linking the original sale,
 * the new sale, and tracking all financial movements (refunds and additional charges).
 *
 * A product exchange is a controlled process that:
 * - Cancels items from the original sale (partial or total)
 * - Optionally generates a refund if the new sale is cheaper
 * - Creates a new sale with the exchanged products
 * - Optionally charges additional amount if the new sale is more expensive
 *
 * This entity ensures full traceability of exchange operations.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "product_exchange", indexes = {
    @Index(name = "idx_exchange_original_sale", columnList = "original_sale_id"),
    @Index(name = "idx_exchange_new_sale", columnList = "new_sale_id"),
    @Index(name = "idx_exchange_date", columnList = "exchange_date")
})
public class ProductExchange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "exchange_date", nullable = false)
    private LocalDateTime exchangeDate;

    @ManyToOne(optional = false)
    @JoinColumn(name = "original_sale_id", nullable = false)
    private Sale originalSale;

    @ManyToOne(optional = false)
    @JoinColumn(name = "new_sale_id", nullable = false)
    private Sale newSale;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 50)
    private ExchangeReason reason;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "processed_by", nullable = false, length = 255)
    private String processedBy;

    @Column(name = "returned_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal returnedAmount;

    @Column(name = "new_sale_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal newSaleAmount;

    /**
     * Value difference resulting from the exchange:
     *
     * - Positve: client should pay additional amount
     * - Negative: client should receive a refund
     * - Zero: exchange with no value difference
     */
    @Column(name = "amount_difference", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountDifference;

    /**
     * Refund amount issued to the customer (if applicable)
     */
    @Column(name = "refund_amount", precision = 12, scale = 2)
    private BigDecimal refundAmount;

    /**
     * Payment method used for any refund issued
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "refund_payment_method", length = 20)
    private PaymentMethod refundPaymentMethod;

    /**
     * ID of the refund transaction (if applicable)
     */
    @Column(name = "refund_transaction_id")
    private Long refundTransactionId;
}
