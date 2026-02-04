package com.jaoow.helmetstore.usecase.transaction;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.security.Principal;

/**
 * Use Case: Calculate financial summary with both profit and cash flow
 * 
 * Responsibilities:
 * - Calculate total profit using CalculateProfitUseCase
 * - Calculate total cash flow using CalculateCashFlowUseCase
 * - Return combined financial summary
 */
@Component
@RequiredArgsConstructor
public class CalculateFinancialSummaryUseCase {

    private final CalculateProfitUseCase calculateProfitUseCase;
    private final CalculateCashFlowUseCase calculateCashFlowUseCase;

    @Cacheable(value = com.jaoow.helmetstore.cache.CacheNames.FINANCIAL_SUMMARY, key = "#principal.name")
    public FinancialSummary execute(Principal principal) {
        BigDecimal profit = calculateProfitUseCase.execute(principal);
        BigDecimal cashFlow = calculateCashFlowUseCase.execute(principal);

        return FinancialSummary.builder()
                .profit(profit)
                .cashFlow(cashFlow)
                .build();
    }

    /**
     * Financial summary data class
     */
    @lombok.Data
    @lombok.Builder
    public static class FinancialSummary {
        private BigDecimal profit;
        private BigDecimal cashFlow;
    }
}
