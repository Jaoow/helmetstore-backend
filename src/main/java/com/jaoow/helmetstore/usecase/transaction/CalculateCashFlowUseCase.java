package com.jaoow.helmetstore.usecase.transaction;

import com.jaoow.helmetstore.model.balance.Transaction;
import com.jaoow.helmetstore.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;

/**
 * Use Case: Calculate total Cash Flow (liquidity available)
 * 
 * Formula: Cash Flow = SUM(all transactions where affectsCash = true)
 * 
 * This includes ALL physical money movements:
 * - Revenue from Sales (+)
 * - Stock Purchases (-)
 * - Operational Expenses (-)
 * - Owner Investments (+)
 * 
 * Excludes:
 * - Cost of Goods Sold (accounting entry only, no cash movement)
 */
@Component
@RequiredArgsConstructor
public class CalculateCashFlowUseCase {

    private final TransactionRepository transactionRepository;

    @Cacheable(value = com.jaoow.helmetstore.cache.CacheNames.CASH_FLOW_CALCULATION, key = "#principal.name")
    public BigDecimal execute(Principal principal) {
        List<Transaction> transactions = transactionRepository.findByAccountUserEmail(principal.getName());

        return transactions.stream()
                .filter(Transaction::isAffectsCash) // Only cash-affecting transactions
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
