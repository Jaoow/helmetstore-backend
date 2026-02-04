package com.jaoow.helmetstore.usecase.transaction;

import com.jaoow.helmetstore.model.balance.Transaction;
import com.jaoow.helmetstore.model.balance.TransactionDetail;
import com.jaoow.helmetstore.repository.TransactionRepository;
import com.jaoow.helmetstore.service.CacheInvalidationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;

/**
 * Use Case: Delete a transaction by ID
 * 
 * Responsibilities:
 * - Find transaction by ID and validate ownership
 * - Prevent deletion of system-generated transactions (sales, COGS, purchase orders)
 * - Delete transaction from repository
 * - Invalidate financial caches
 */
@Component
@RequiredArgsConstructor
public class DeleteTransactionUseCase {

    private final TransactionRepository transactionRepository;
    private final CacheInvalidationService cacheInvalidationService;

    @Transactional
    public void execute(Long transactionId, Principal principal) {
        Transaction transaction = transactionRepository
                .findByIdAndAccountUserEmail(transactionId, principal.getName())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Transaction not found with ID: " + transactionId));

        if (transaction.getDetail() == TransactionDetail.SALE
                || transaction.getDetail() == TransactionDetail.COST_OF_GOODS_SOLD) {
            throw new IllegalArgumentException(
                    "Você não pode excluir transações vinculadas a vendas ou pedidos de compra.");
        }

        transactionRepository.delete(transaction);
        
        // Invalidate financial caches after deleting a transaction
        cacheInvalidationService.invalidateFinancialCaches();
    }
}
