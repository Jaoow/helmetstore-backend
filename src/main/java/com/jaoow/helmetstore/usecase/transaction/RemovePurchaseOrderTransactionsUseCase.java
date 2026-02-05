package com.jaoow.helmetstore.usecase.transaction;

import com.jaoow.helmetstore.model.PurchaseOrder;
import com.jaoow.helmetstore.model.balance.Transaction;
import com.jaoow.helmetstore.repository.TransactionRepository;
import com.jaoow.helmetstore.service.CacheInvalidationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Use Case: Remove transactions linked to a purchase order
 * 
 * Responsibilities:
 * - Find all transactions linked to the purchase order by reference
 * - Validate transactions exist
 * - Delete all linked transactions
 * - Invalidate financial caches
 */
@Component
@RequiredArgsConstructor
public class RemovePurchaseOrderTransactionsUseCase {

    private static final String PURCHASE_ORDER_REFERENCE_PREFIX = "PURCHASE_ORDER#";

    private final TransactionRepository transactionRepository;
    private final CacheInvalidationService cacheInvalidationService;

    @Transactional
    public void execute(PurchaseOrder purchaseOrder) {
        String reference = PURCHASE_ORDER_REFERENCE_PREFIX + purchaseOrder.getId();
        List<Transaction> transactions = transactionRepository.findAllByReference(reference);

        if (transactions.isEmpty()) {
            throw new IllegalArgumentException(
                    "Transaction not found for purchase order ID: " + purchaseOrder.getId());
        }

        transactionRepository.deleteAll(transactions);
        cacheInvalidationService.invalidateFinancialCaches();
    }
}
