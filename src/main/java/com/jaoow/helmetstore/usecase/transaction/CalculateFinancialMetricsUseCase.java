package com.jaoow.helmetstore.usecase.transaction;

import com.jaoow.helmetstore.cache.CacheNames;
import com.jaoow.helmetstore.dto.balance.FinancialSummaryDTO;
import com.jaoow.helmetstore.model.balance.Transaction;
import com.jaoow.helmetstore.repository.TransactionRepository;
import com.jaoow.helmetstore.service.ProfitCalculationService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;

/**
 * Use Case: Calculate financial metrics
 * 
 * Responsibilities:
 * - Calculate total net profit using unified profit calculation service
 * - Calculate total cash flow from cash-affecting transactions
 * - Provide financial summary with both metrics
 * - Cache results for performance
 */
@Component
@RequiredArgsConstructor
public class CalculateFinancialMetricsUseCase {

    private final TransactionRepository transactionRepository;
    private final ProfitCalculationService profitCalculationService;

    /**
     * Calculate total Net Profit (TRUE business profitability).
     * <p>
     * Formula: Net Profit = Revenue - COGS - Operational Expenses
     * <p>
     * Uses the UNIFIED PROFIT CALCULATION SERVICE to ensure consistency
     * across all profit calculations in the system.
     * <p>
     * This includes ALL profit-affecting transactions with affectsProfit = true:
     * - Revenue from Sales (+)
     * - Cost of Goods Sold (-)
     * - Operational Expenses (Rent, Energy, etc.) (-)
     * <p>
     * Excludes non-profit transactions:
     * - Stock Purchases (asset transfer, not an expense)
     * - Owner Investments (capital, not revenue)
     * - Internal Transfers (wallet movement, not profit-affecting)
     */
    @Cacheable(value = CacheNames.PROFIT_CALCULATION, key = "#principal.name")
    public BigDecimal calculateProfit(Principal principal) {
        return profitCalculationService.calculateTotalNetProfit(principal.getName());
    }

    /**
     * Calculate total Cash Flow (liquidity available).
     * <p>
     * Formula: Cash Flow = SUM(all transactions where affectsCash = true)
     * <p>
     * This includes ALL physical money movements:
     * - Revenue from Sales (+)
     * - Stock Purchases (-)
     * - Operational Expenses (-)
     * - Owner Investments (+)
     * <p>
     * Excludes:
     * - Cost of Goods Sold (accounting entry only, no cash movement)
     */
    @Cacheable(value = CacheNames.CASH_FLOW_CALCULATION, key = "#principal.name")
    public BigDecimal calculateCashFlow(Principal principal) {
        List<Transaction> transactions = transactionRepository.findByAccountUserEmail(principal.getName());

        return transactions.stream()
                .filter(Transaction::isAffectsCash) // Only cash-affecting transactions
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Get financial summary with both profit and cash flow
     */
    @Cacheable(value = CacheNames.FINANCIAL_SUMMARY, key = "#principal.name")
    public FinancialSummaryDTO calculateFinancialSummary(Principal principal) {
        BigDecimal profit = calculateProfit(principal);
        BigDecimal cashFlow = calculateCashFlow(principal);

        return FinancialSummaryDTO.builder()
                .profit(profit)
                .cashFlow(cashFlow)
                .build();
    }
}
