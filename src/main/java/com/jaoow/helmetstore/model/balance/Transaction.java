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

    private String reference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    // ============================================================================
    // DOUBLE-ENTRY LEDGER FLAGS
    // ============================================================================
    // These flags enable clean separation of Cash Flow vs Profitability reporting
    // without complex JOINs or hardcoded business logic in queries.
    // ============================================================================

    /**
     * Indicates whether this transaction affects Net Profit calculation.
     * <p>
     * TRUE for:
     * - Revenue (SALE)
     * - Cost of Goods Sold (COGS)
     * - Operational Expenses (FIXED_EXPENSE, VARIABLE_EXPENSE, etc.)
     * <p>
     * FALSE for:
     * - Owner Investments (OWNER_INVESTMENT)
     * - Internal Transfers (INTERNAL_TRANSFER_IN/OUT)
     * - Stock Purchases (asset transfer, not an expense)
     */
    @Column(nullable = false)
    private boolean affectsProfit;

    /**
     * Indicates whether this transaction affects Cash/Bank balance.
     * <p>
     * TRUE for:
     * - Sales Revenue (money received)
     * - Bill Payments (money spent)
     * - Stock Purchases (money spent)
     * - Owner Investments (money deposited)
     * <p>
     * FALSE for:
     * - Cost of Goods Sold (accounting entry only, no cash movement)
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
     * <p>
     * CRITICAL RULE: EXPENSE transactions MUST have negative amounts.
     * INCOME transactions MUST have positive amounts.
     * <p>
     * This prevents bugs where expenses are accidentally stored as positive values,
     * which would corrupt cash flow, profit calculations, and wallet balances.
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
