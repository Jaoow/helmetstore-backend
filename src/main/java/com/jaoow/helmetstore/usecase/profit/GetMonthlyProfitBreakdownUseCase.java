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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Use Case: Get monthly profit breakdown for all months
 * 
 * Responsibilities:
 * - Calculate profit metrics for each month
 * - Track cumulative account balances month by month
 * - Include expense transaction details
 * - Cache results for performance
 * 
 * PERFORMANCE OPTIMIZATION:
 * - Loads all transactions once instead of querying per month
 * - Calculates cumulative balances incrementally
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GetMonthlyProfitBreakdownUseCase {

    private final TransactionRepository transactionRepository;
    private final InventoryHelper inventoryHelper;
    private final ModelMapper modelMapper;
    private final ProfitCalculationHelper profitCalculationHelper;

    @Cacheable(value = com.jaoow.helmetstore.cache.CacheNames.MONTHLY_PROFIT, key = "#principal.name")
    public List<MonthlyProfitDTO> execute(Principal principal) {
        String userEmail = principal.getName();

        log.debug("Executing GetMonthlyProfitBreakdownUseCase for user: {}", userEmail);

        // PERFORMANCE: Get all transactions ONCE instead of querying per month
        List<Transaction> allTransactions = transactionRepository.findByAccountUserEmail(userEmail);

        List<Object[]> distinctMonths = transactionRepository.findDistinctMonthsByUserEmail(userEmail);

        List<YearMonth> sortedMonths = distinctMonths.stream()
            .map(monthData -> {
                Integer year = (Integer) monthData[0];
                Integer month = (Integer) monthData[1];
                return YearMonth.of(year, month);
            })
            .sorted()
            .toList();

        List<MonthlyProfitDTO> result = new ArrayList<>();

        // PERFORMANCE FIX: Calculate cumulative balances incrementally
        BigDecimal cumulativeBankBalance = BigDecimal.ZERO;
        BigDecimal cumulativeCashBalance = BigDecimal.ZERO;

        for (YearMonth yearMonth : sortedMonths) {
            LocalDateTime startOfMonth = yearMonth.atDay(1).atStartOfDay();
            LocalDateTime startOfNextMonth = yearMonth.plusMonths(1).atDay(1).atStartOfDay();

            // Filter transactions for this month from already-loaded list
            List<Transaction> monthlyTransactions = allTransactions.stream()
                .filter(t -> !t.getDate().isBefore(startOfMonth) && t.getDate().isBefore(startOfNextMonth))
                .collect(Collectors.toList());

            var inventory = inventoryHelper.getInventoryFromPrincipal(principal);

            // Calculate monthly values using ProfitCalculationService
            BigDecimal monthlyNetProfit = profitCalculationHelper.calculateNetProfitFromTransactions(monthlyTransactions);
            BigDecimal monthlyGrossProfit = profitCalculationHelper.calculateGrossProfitByDateRange(inventory,
                    startOfMonth, startOfNextMonth);
            BigDecimal monthlyExpenseTransactions = profitCalculationHelper
                    .calculateOperationalExpensesFromTransactions(monthlyTransactions);

            // Get expense transactions for breakdown
            List<Transaction> expenseTransactions = monthlyTransactions.stream()
                .filter(Transaction::isAffectsProfit)
                .filter(t -> t.getAmount().compareTo(BigDecimal.ZERO) < 0)
                .filter(t -> t.getDetail() != com.jaoow.helmetstore.model.balance.TransactionDetail.COST_OF_GOODS_SOLD)
                .collect(Collectors.toList());

            // PERFORMANCE: Update cumulative balances incrementally
            for (Transaction t : monthlyTransactions) {
                if (t.getWalletDestination() == AccountType.BANK) {
                    cumulativeBankBalance = cumulativeBankBalance.add(t.getAmount());
                } else if (t.getWalletDestination() == AccountType.CASH) {
                    cumulativeCashBalance = cumulativeCashBalance.add(t.getAmount());
                }
            }

            MonthlyProfitDTO monthlyData = MonthlyProfitDTO.builder()
                .month(yearMonth)
                .bankAccountBalance(cumulativeBankBalance)
                .cashAccountBalance(cumulativeCashBalance)
                .totalBalance(cumulativeBankBalance.add(cumulativeCashBalance))
                .monthlyProfit(monthlyGrossProfit)
                .monthlyNetProfit(monthlyNetProfit)
                .monthlyExpenseTransactions(monthlyExpenseTransactions)
                .expenseTransactions(convertToTransactionInfo(expenseTransactions))
                .build();

            result.add(monthlyData);
        }

        return result;
    }

    private List<TransactionInfo> convertToTransactionInfo(List<Transaction> transactions) {
        return transactions.stream()
                .filter(t -> t.getDetail() != com.jaoow.helmetstore.model.balance.TransactionDetail.COST_OF_GOODS_SOLD) // Hide COGS from UI
                .map(transaction -> modelMapper.map(transaction, TransactionInfo.class))
                .collect(Collectors.toList());
    }
}
