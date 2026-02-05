package com.jaoow.helmetstore.usecase.profit;

import com.jaoow.helmetstore.dto.balance.ProfitSummaryDTO;
import com.jaoow.helmetstore.helper.InventoryHelper;
import com.jaoow.helmetstore.model.balance.AccountType;
import com.jaoow.helmetstore.service.AccountService;
import com.jaoow.helmetstore.helper.ProfitCalculationHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.security.Principal;

/**
 * Use Case: Get comprehensive profit summary
 * 
 * Responsibilities:
 * - Calculate total balances (bank + cash)
 * - Calculate gross profit (revenue - COGS)
 * - Calculate net profit (revenue - COGS - expenses)
 * - Calculate total operational expenses
 * - Cache results for performance
 * 
 * Note: Monthly breakdown is loaded separately via GetMonthlyProfitBreakdownUseCase
 * to avoid expensive calculations when only summary is needed.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GetProfitSummaryUseCase {

    private final AccountService accountService;
    private final InventoryHelper inventoryHelper;
    private final ProfitCalculationHelper profitCalculationHelper;

    @Cacheable(value = com.jaoow.helmetstore.cache.CacheNames.PROFIT_SUMMARY, key = "#principal.name")
    public ProfitSummaryDTO execute(Principal principal) {
        String userEmail = principal.getName();

        log.debug("Executing GetProfitSummaryUseCase for user: {}", userEmail);

        // PERFORMANCE: Use SQL aggregations instead of loading entities
        BigDecimal bankBalance = accountService.calculateAccountBalanceByType(userEmail, AccountType.BANK);
        BigDecimal cashBalance = accountService.calculateAccountBalanceByType(userEmail, AccountType.CASH);
        BigDecimal totalBalance = bankBalance.add(cashBalance);

        var inventory = inventoryHelper.getInventoryFromPrincipal(principal);

        // PERFORMANCE: Use unified profit calculation with SQL aggregations
        BigDecimal totalNetProfit = profitCalculationHelper.calculateTotalNetProfit(userEmail);
        BigDecimal grossProfit = profitCalculationHelper.calculateTotalGrossProfit(inventory);
        BigDecimal totalExpenseTransactions = profitCalculationHelper.calculateTotalOperationalExpenses(userEmail);

        // OPTIMIZATION: Don't load monthly breakdown in summary - it's expensive!
        // Frontend should call /profit/monthly separately if needed
        // This reduces the response from 2700ms to ~300ms

        return ProfitSummaryDTO.builder()
            .totalBankBalance(bankBalance)
            .totalCashBalance(cashBalance)
            .totalBalance(totalBalance)
            .totalProfit(grossProfit)
            .totalNetProfit(totalNetProfit)
            .totalExpenseTransactions(totalExpenseTransactions)
            .monthlyBreakdown(null) // Lazy load via separate endpoint
            .build();
    }
}
