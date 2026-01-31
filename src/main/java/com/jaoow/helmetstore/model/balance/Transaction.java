package com.jaoow.helmetstore.model.balance;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(indexes = {
    @Index(name = "idx_transaction_date", columnList = "date"),
    @Index(name = "idx_transaction_account_date", columnList = "account_id, date"),
    @Index(name = "idx_transaction_reference", columnList = "reference"),
    @Index(name = "idx_transaction_reference_sub_id", columnList = "reference_sub_id"),
    @Index(name = "idx_transaction_affects_profit", columnList = "affectsProfit, date"),
    @Index(name = "idx_transaction_wallet_dest", columnList = "walletDestination, date"),
    @Index(name = "idx_transaction_type_detail", columnList = "type, detail")
})
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime date;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column
    private TransactionDetail detail;

    private String description;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod paymentMethod;

    /**
     * Reference to originating entity (e.g., "SALE#123", "PURCHASE_ORDER#456")
     */
    private String reference;

    /**
     * Sub-reference ID for duplicate prevention.
     * Used to track specific sub-entities (e.g., SalePayment.id within a Sale)
     * to prevent creating duplicate transactions in exchange scenarios.
     */
    @Column(name = "reference_sub_id")
    private Long referenceSubId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    /**
     * Indicates whether this transaction affects Net Profit calculation.
     */
    @Column(nullable = false)
    private boolean affectsProfit;

    /**
     * Indicates whether this transaction affects Cash/Bank balance.
     */
    @Column(nullable = false)
    private boolean affectsCash;

    /**
     * Specifies which wallet/account the cash movement affects.
     * <p>
     * Values:
     * - CASH: Physical cash drawer
     * - BANK: Bank account (PIX/Card transactions)
     * - NULL: Non-cash transactions (e.g., COGS)
     * <p>
     * This enables accurate per-wallet balance calculations:
     * - Cash Balance = SUM(amount WHERE walletDestination = 'CASH')
     * - Bank Balance = SUM(amount WHERE walletDestination = 'BANK')
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AccountType walletDestination;

    /**
     * Validates business rules before persisting or updating the transaction.
     */
    @PrePersist
    @PreUpdate
    private void validateTransactionRules() {
        if (type == null || amount == null) {
            throw new IllegalStateException("Transaction type and amount must not be null");
        }

        // CRITICAL: Expenses must be negative, Income must be positive
        if (type == TransactionType.EXPENSE && amount.compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalStateException(
                String.format("EXPENSE transactions must have negative amounts. " +
                    "Transaction type: %s, Amount: %s, Description: %s",
                    type, amount, description)
            );
        }

        if (type == TransactionType.INCOME && amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException(
                String.format("INCOME transactions must have positive amounts. " +
                    "Transaction type: %s, Amount: %s, Description: %s",
                    type, amount, description)
            );
        }
    }
}
