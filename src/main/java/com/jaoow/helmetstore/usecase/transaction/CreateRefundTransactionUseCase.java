package com.jaoow.helmetstore.usecase.transaction;

import com.jaoow.helmetstore.exception.AccountNotFoundException;
import com.jaoow.helmetstore.model.PurchaseOrder;
import com.jaoow.helmetstore.model.balance.*;
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
 * Use Case: Create refund transaction for canceled purchase order item
 * 
 * Responsibilities:
 * - Find and validate account exists
 * - Determine wallet destination based on payment method
 * - Create income transaction for refund
 * - Set appropriate ledger flags (affects cash but not profit)
 * - Save refund transaction
 * - Invalidate financial caches
 */
@Component
@RequiredArgsConstructor
public class CreateRefundTransactionUseCase {

    private static final String PURCHASE_ORDER_REFERENCE_PREFIX = "PURCHASE_ORDER#";

    private final TransactionRepository transactionRepository;
    private final AccountService accountService;
    private final CacheInvalidationService cacheInvalidationService;

    @Transactional
    public void execute(PurchaseOrder purchaseOrder, BigDecimal refundAmount,
                        String itemDescription, Principal principal) {
        Account account = accountService.findAccountByPaymentMethodAndUser(purchaseOrder.getPaymentMethod(), principal)
                .orElseThrow(() -> new AccountNotFoundException(purchaseOrder.getPaymentMethod()));

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
