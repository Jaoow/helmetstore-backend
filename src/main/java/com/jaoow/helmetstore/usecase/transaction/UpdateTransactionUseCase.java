package com.jaoow.helmetstore.usecase.transaction;

import com.jaoow.helmetstore.dto.balance.TransactionCreateDTO;
import com.jaoow.helmetstore.exception.AccountNotFoundException;
import com.jaoow.helmetstore.model.balance.*;
import com.jaoow.helmetstore.repository.TransactionRepository;
import com.jaoow.helmetstore.service.AccountService;
import com.jaoow.helmetstore.service.CacheInvalidationService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.Principal;

/**
 * Use Case: Update an existing transaction
 * 
 * Responsibilities:
 * - Find and validate transaction exists and belongs to user
 * - Validate transaction is not linked to sales or purchase orders
 * - Map new data to existing transaction
 * - Ensure expenses are stored as negative values
 * - Update account if payment method changed
 * - Save updated transaction
 * - Invalidate financial caches
 */
@Component
@RequiredArgsConstructor
public class UpdateTransactionUseCase {

    private final TransactionRepository transactionRepository;
    private final AccountService accountService;
    private final ModelMapper modelMapper;
    private final CacheInvalidationService cacheInvalidationService;

    @Transactional
    public void execute(Long transactionId, TransactionCreateDTO dto, Principal principal) {
        Transaction transaction = transactionRepository
                .findByIdAndAccountUserEmail(transactionId, principal.getName())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Transação não encontrada com ID: " + transactionId));

        if (transaction.getDetail() == TransactionDetail.SALE
                || transaction.getDetail() == TransactionDetail.COST_OF_GOODS_SOLD) {
            throw new IllegalArgumentException(
                    "Você não pode editar transações vinculadas a vendas ou pedidos de compra.");
        }

        // Update the existing transaction with new data
        modelMapper.map(dto, transaction);

        // Ensure expenses are stored as negative values
        if (dto.getType() == TransactionType.EXPENSE && dto.getAmount().compareTo(BigDecimal.ZERO) > 0) {
            transaction.setAmount(dto.getAmount().negate());
        }

        Account account = accountService.findAccountByPaymentMethodAndUser(dto.getPaymentMethod(), principal)
                .orElseThrow(() -> new AccountNotFoundException(dto.getPaymentMethod()));

        transaction.setAccount(account);
        transactionRepository.save(transaction);

        // Invalidate financial caches after updating a transaction
        cacheInvalidationService.invalidateFinancialCaches();
    }
}
