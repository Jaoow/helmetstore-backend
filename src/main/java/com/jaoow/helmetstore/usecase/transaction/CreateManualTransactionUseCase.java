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
 * Use Case: Create a manual transaction
 * 
 * Responsibilities:
 * - Map DTO to Transaction entity
 * - Find and validate account exists
 * - Ensure expenses are stored as negative values
 * - Set ledger flags based on transaction type
 * - Save transaction
 * - Invalidate financial caches
 */
@Component
@RequiredArgsConstructor
public class CreateManualTransactionUseCase {

    private final TransactionRepository transactionRepository;
    private final AccountService accountService;
    private final ModelMapper modelMapper;
    private final CacheInvalidationService cacheInvalidationService;

    @Transactional
    public void execute(TransactionCreateDTO dto, Principal principal) {
        Transaction transaction = modelMapper.map(dto, Transaction.class);
        Account account = accountService.findAccountByPaymentMethodAndUser(dto.getPaymentMethod(), principal)
                .orElseThrow(() -> new AccountNotFoundException(dto.getPaymentMethod()));

        transaction.setAccount(account);

        // Ensure expenses are stored as negative values
        if (dto.getType() == TransactionType.EXPENSE && dto.getAmount().compareTo(BigDecimal.ZERO) > 0) {
            transaction.setAmount(dto.getAmount().negate());
        }

        // Set ledger flags based on transaction type and detail
        AccountType walletDest = (dto.getPaymentMethod() == PaymentMethod.CASH)
                ? AccountType.CASH
                : AccountType.BANK;

        // All manual transactions affect cash (they represent real money movement)
        TransactionDetail detail = transaction.getDetail();

        transaction.setAffectsProfit(detail.isAffectsProfit());
        transaction.setAffectsCash(detail.isAffectsCash());
        transaction.setWalletDestination(walletDest);

        transactionRepository.save(transaction);

        // Invalidate financial caches after creating a transaction
        cacheInvalidationService.invalidateFinancialCaches();
    }
}
