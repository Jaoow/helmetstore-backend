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

import java.security.Principal;

/**
 * Use Case: Record transaction from a purchase order
 * 
 * Responsibilities:
 * - Find account by payment method
 * - Create expense transaction for inventory purchase
 * - Set proper ledger flags (doesn't affect profit, but reduces cash)
 * - Save transaction to repository
 * - Invalidate financial caches
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 * ACCOUNTING RULE:
 * - Buying stock is an asset transfer (Cash → Inventory)
 * - Does NOT reduce profit (affectsProfit = false)
 * - Does reduce available cash (affectsCash = true)
 * ═══════════════════════════════════════════════════════════════════════════
 */
@Component
@RequiredArgsConstructor
public class RecordTransactionFromPurchaseOrderUseCase {

    private static final String PURCHASE_ORDER_REFERENCE_PREFIX = "PURCHASE_ORDER#";

    private final TransactionRepository transactionRepository;
    private final AccountService accountService;
    private final CacheInvalidationService cacheInvalidationService;

    @Transactional
    public void execute(PurchaseOrder purchaseOrder, Principal principal) {
        Account account = accountService.getOrCreateAccountByPaymentMethod(purchaseOrder.getPaymentMethod(), principal);

        // Determine wallet destination based on payment method
        AccountType walletDest = (purchaseOrder.getPaymentMethod() == PaymentMethod.CASH)
                ? AccountType.CASH
                : AccountType.BANK;

        Transaction transaction = Transaction.builder()
                .date(purchaseOrder.getDate().atStartOfDay())
                .type(TransactionType.EXPENSE)
                .detail(TransactionDetail.INVENTORY_PURCHASE)
                .description("Pedido de Compra #" + purchaseOrder.getOrderNumber())
                .amount(purchaseOrder.getTotalAmount().negate())
                .paymentMethod(purchaseOrder.getPaymentMethod())
                .reference(PURCHASE_ORDER_REFERENCE_PREFIX + purchaseOrder.getId())
                .account(account)
                .affectsProfit(false)  // Buying stock doesn't reduce profit
                .affectsCash(true)     // But it does reduce available cash
                .walletDestination(walletDest)
                .build();

        transactionRepository.save(transaction);

        // Invalidate financial caches after recording transaction from purchase order
        cacheInvalidationService.invalidateFinancialCaches();
    }
}
