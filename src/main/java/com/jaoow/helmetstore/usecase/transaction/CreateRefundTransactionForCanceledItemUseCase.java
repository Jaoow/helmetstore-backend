package com.jaoow.helmetstore.usecase.transaction;

import com.jaoow.helmetstore.exception.AccountNotFoundException;
import com.jaoow.helmetstore.model.PurchaseOrder;
import com.jaoow.helmetstore.model.balance.Account;
import com.jaoow.helmetstore.model.balance.AccountType;
import com.jaoow.helmetstore.model.balance.PaymentMethod;
import com.jaoow.helmetstore.model.balance.Transaction;
import com.jaoow.helmetstore.model.balance.TransactionDetail;
import com.jaoow.helmetstore.model.balance.TransactionType;
import com.jaoow.helmetstore.repository.TransactionRepository;
import com.jaoow.helmetstore.service.AccountService;
import com.jaoow.helmetstore.service.CacheInvalidationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDateTime;

/**
 * Use Case: Create a refund transaction for a canceled purchase order item
 * 
 * Responsibilities:
 * - Find account by payment method
 * - Create income transaction for refund
 * - Set proper ledger flags (doesn't affect profit, but increases cash)
 * - Save transaction to repository
 * - Invalidate financial caches
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 * ACCOUNTING RULE:
 * - Refund is the reverse of a purchase (Inventory → Cash)
 * - Does NOT affect profit (affectsProfit = false)
 * - Does increase available cash (affectsCash = true)
 * ═══════════════════════════════════════════════════════════════════════════
 */
@Component
@RequiredArgsConstructor
public class CreateRefundTransactionForCanceledItemUseCase {

    private static final String PURCHASE_ORDER_REFERENCE_PREFIX = "PURCHASE_ORDER#";

    private final TransactionRepository transactionRepository;
    private final AccountService accountService;
    private final CacheInvalidationService cacheInvalidationService;

    @Transactional
    public void execute(PurchaseOrder purchaseOrder, BigDecimal refundAmount,
                       String itemDescription, Principal principal) {
        Account account = accountService.getOrCreateAccountByPaymentMethod(purchaseOrder.getPaymentMethod(), principal);

        // Determine wallet destination based on payment method
        AccountType walletDest = (purchaseOrder.getPaymentMethod() == PaymentMethod.CASH)
                ? AccountType.CASH
                : AccountType.BANK;

        Transaction refundTransaction = Transaction.builder()
                .date(LocalDateTime.now())
                .type(TransactionType.INCOME)
                .detail(TransactionDetail.REFUND)
                .description("Reembolso cancelamento: " + itemDescription + " - Pedido #" + purchaseOrder.getOrderNumber())
                .amount(refundAmount)
                .paymentMethod(purchaseOrder.getPaymentMethod())
                .reference("REFUND_" + PURCHASE_ORDER_REFERENCE_PREFIX + purchaseOrder.getId())
                .account(account)
                .affectsProfit(false)  // Refund doesn't affect profit
                .affectsCash(true)     // Refund increases cash/bank balance
                .walletDestination(walletDest)
                .build();

        transactionRepository.save(refundTransaction);

        // Invalidate financial caches after creating refund transaction
        cacheInvalidationService.invalidateFinancialCaches();
    }
}
