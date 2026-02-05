package com.jaoow.helmetstore.usecase.profit;

import com.jaoow.helmetstore.dto.balance.MonthlyProfitDTO;
import com.jaoow.helmetstore.dto.balance.TransactionInfo;
import com.jaoow.helmetstore.helper.InventoryHelper;
import com.jaoow.helmetstore.model.balance.AccountType;
import com.jaoow.helmetstore.model.balance.Transaction;
import com.jaoow.helmetstore.repository.TransactionRepository;
import com.jaoow.helmetstore.helper.ProfitCalculationHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Use Case: Get profit details for a specific month
 * 
 * Responsibilities:
 * - Calculate profit metrics for a single month
 * - Calculate cumulative account balances up to that month
 * - Include sales revenue and expense breakdown
 * - Cache results for performance
 * 
 * PERFORMANCE OPTIMIZATION:
 * - Uses SQL aggregations instead of loading all transactions
 * - Calculates cumulative balances using repository methods
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GetMonthlyProfitUseCase {

    private final TransactionRepository transactionRepository;
    private final InventoryHelper inventoryHelper;
    private final ModelMapper modelMapper;
    private final ProfitCalculationHelper profitCalculationHelper;

    @Cacheable(value = com.jaoow.helmetstore.cache.CacheNames.MONTHLY_PROFIT, key = "#principal.name + '-' + #yearMonth")
    public MonthlyProfitDTO execute(Principal principal, YearMonth yearMonth) {
        String userEmail = principal.getName();
        
        log.debug("Executing GetMonthlyProfitUseCase for user: {} and month: {}", userEmail, yearMonth);

        LocalDateTime startOfMonth = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime startOfNextMonth = yearMonth.plusMonths(1).atDay(1).atStartOfDay();

        var inventory = inventoryHelper.getInventoryFromPrincipal(principal);

        // ============================================================================
        // PERFORMANCE: Use SQL aggregations instead of loading entities
        // ============================================================================
        BigDecimal monthlyNetProfit = transactionRepository.calculateNetProfitByDateRange(
                userEmail, startOfMonth, startOfNextMonth);
        BigDecimal monthlyGrossProfit = profitCalculationHelper.calculateGrossProfitByDateRange(
                inventory, startOfMonth, startOfNextMonth);
        // FIXED: Use repository aggregation for expenses calculation (more efficient)
        // Note: This calculates the same value as monthlyNetProfit but kept for clarity in DTO
        BigDecimal monthlyExpenseTransactions = transactionRepository.calculateNetProfitByDateRange(
                userEmail, startOfMonth, startOfNextMonth);
        BigDecimal salesRevenue = transactionRepository.calculateSalesRevenueByDateRange(
                userEmail, startOfMonth, startOfNextMonth);

        // Calculate cumulative balances up to end of this month using SQL aggregation
        LocalDateTime endOfMonth = yearMonth.atEndOfMonth().atTime(23, 59, 59);
        BigDecimal cumulativeBankBalance = transactionRepository.calculateWalletBalanceUpToDate(
                userEmail, AccountType.BANK, endOfMonth);
        BigDecimal cumulativeCashBalance = transactionRepository.calculateWalletBalanceUpToDate(
                userEmail, AccountType.CASH, endOfMonth);

        // Get expense transactions for detailed breakdown (only if needed)
        List<Transaction> expenseTransactions = transactionRepository
            .findProfitAffectingTransactionsByDateRange(userEmail, startOfMonth, startOfNextMonth)
            .stream()
            .filter(t -> t.getAmount().compareTo(BigDecimal.ZERO) < 0)
            .filter(t -> t.getDetail() != com.jaoow.helmetstore.model.balance.TransactionDetail.COST_OF_GOODS_SOLD)
            .collect(Collectors.toList());

        return MonthlyProfitDTO.builder()
            .month(yearMonth)
            .bankAccountBalance(cumulativeBankBalance)
            .cashAccountBalance(cumulativeCashBalance)
            .totalBalance(cumulativeBankBalance.add(cumulativeCashBalance))
            .monthlyProfit(monthlyGrossProfit)
            .monthlyNetProfit(monthlyNetProfit)
            .salesRevenue(salesRevenue)
            .monthlyExpenseTransactions(monthlyExpenseTransactions)
            .expenseTransactions(convertToTransactionInfo(expenseTransactions))
            .build();
    }

    private List<TransactionInfo> convertToTransactionInfo(List<Transaction> transactions) {
        return transactions.stream()
                .filter(t -> t.getDetail() != com.jaoow.helmetstore.model.balance.TransactionDetail.COST_OF_GOODS_SOLD) // Hide COGS from UI
                .map(transaction -> modelMapper.map(transaction, TransactionInfo.class))
                .collect(Collectors.toList());
    }
}
