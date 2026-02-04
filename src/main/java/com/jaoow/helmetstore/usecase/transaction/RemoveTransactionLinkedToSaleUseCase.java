package com.jaoow.helmetstore.usecase.transaction;

import com.jaoow.helmetstore.model.Sale;
import com.jaoow.helmetstore.model.balance.Transaction;
import com.jaoow.helmetstore.repository.TransactionRepository;
import com.jaoow.helmetstore.service.CacheInvalidationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Use Case: Remove transactions linked to a sale
 * 
 * Responsibilities:
 * - Find all transactions by sale reference
 * - Validate that transactions exist
 * - Delete all related transactions (revenue and COGS)
 * - Invalidate financial caches
 */
@Component
@RequiredArgsConstructor
public class RemoveTransactionLinkedToSaleUseCase {

    private static final String SALE_REFERENCE_PREFIX = "SALE#";

    private final TransactionRepository transactionRepository;
    private final CacheInvalidationService cacheInvalidationService;

    @Transactional
    public void execute(Sale sale) {
        String reference = SALE_REFERENCE_PREFIX + sale.getId();
        List<Transaction> transactions = transactionRepository.findAllByReference(reference);

        if (transactions.isEmpty()) {
            throw new IllegalArgumentException("Transação não encontrada para venda ID: " + sale.getId());
        }

        transactionRepository.deleteAll(transactions);
        cacheInvalidationService.invalidateFinancialCaches();
    }
}
