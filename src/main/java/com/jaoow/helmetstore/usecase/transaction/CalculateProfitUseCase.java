package com.jaoow.helmetstore.usecase.transaction;

import com.jaoow.helmetstore.service.ProfitCalculationService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.security.Principal;

/**
 * Use Case: Calculate total Net Profit
 * 
 * Formula: Net Profit = Revenue - COGS - Operational Expenses
 * 
 * Uses the UNIFIED PROFIT CALCULATION SERVICE to ensure consistency
 * across all profit calculations in the system.
 * 
 * This includes ALL profit-affecting transactions with affectsProfit = true:
 * - Revenue from Sales (+)
 * - Cost of Goods Sold (-)
 * - Operational Expenses (Rent, Energy, etc.) (-)
 * 
 * Excludes non-profit transactions:
 * - Stock Purchases (asset transfer, not an expense)
 * - Owner Investments (capital, not revenue)
 * - Internal Transfers (wallet movement, not profit-affecting)
 */
@Component
@RequiredArgsConstructor
public class CalculateProfitUseCase {

    private final ProfitCalculationService profitCalculationService;

    @Cacheable(value = com.jaoow.helmetstore.cache.CacheNames.PROFIT_CALCULATION, key = "#principal.name")
    public BigDecimal execute(Principal principal) {
        return profitCalculationService.calculateTotalNetProfit(principal.getName());
    }
}
