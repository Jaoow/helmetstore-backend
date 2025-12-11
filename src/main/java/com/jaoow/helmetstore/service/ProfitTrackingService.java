package com.jaoow.helmetstore.service;

import com.jaoow.helmetstore.dto.balance.MonthlyProfitDTO;
import com.jaoow.helmetstore.dto.balance.ProfitSummaryDTO;
import com.jaoow.helmetstore.dto.balance.TransactionInfo;
import com.jaoow.helmetstore.helper.InventoryHelper;
import com.jaoow.helmetstore.model.balance.AccountType;
import com.jaoow.helmetstore.model.balance.Transaction;
import com.jaoow.helmetstore.repository.SaleRepository;
import com.jaoow.helmetstore.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfitTrackingService {

    private final TransactionRepository transactionRepository;
    private final SaleRepository saleRepository;
    private final InventoryHelper inventoryHelper;
    private final ModelMapper modelMapper;
    private final AccountService accountService;
    private final ProfitCalculationService profitCalculationService; // NEW: Unified profit calculations

    @Cacheable(value = com.jaoow.helmetstore.cache.CacheNames.PROFIT_SUMMARY, key = "#principal.name")
    public ProfitSummaryDTO getProfitSummary(Principal principal) {
        String userEmail = principal.getName();

        BigDecimal bankBalance = accountService.calculateAccountBalanceByType(userEmail, AccountType.BANK);
        BigDecimal cashBalance = accountService.calculateAccountBalanceByType(userEmail, AccountType.CASH);
        BigDecimal totalBalance = bankBalance.add(cashBalance);

        var inventory = inventoryHelper.getInventoryFromPrincipal(principal);

        // ============================================================================
        // USE UNIFIED PROFIT CALCULATION SERVICE
        // ============================================================================
        BigDecimal totalNetProfit = profitCalculationService.calculateTotalNetProfit(userEmail);
        BigDecimal grossProfit = profitCalculationService.calculateTotalGrossProfit(inventory);
        BigDecimal totalExpenseTransactions = profitCalculationService.calculateTotalOperationalExpenses(userEmail);

        List<MonthlyProfitDTO> monthlyBreakdown = getMonthlyProfitBreakdown(principal);

        return ProfitSummaryDTO.builder()
            .totalBankBalance(bankBalance)
            .totalCashBalance(cashBalance)
            .totalBalance(totalBalance)
            .totalProfit(grossProfit) // Gross Profit (for backward compatibility)
            .totalNetProfit(totalNetProfit) // REAL Net Profit (includes all expenses)
            .totalExpenseTransactions(totalExpenseTransactions) // Operational expenses only
            .monthlyBreakdown(monthlyBreakdown)
            .build();
    }

    @Cacheable(value = com.jaoow.helmetstore.cache.CacheNames.MONTHLY_PROFIT, key = "#principal.name")
    public List<MonthlyProfitDTO> getMonthlyProfitBreakdown(Principal principal) {
        String userEmail = principal.getName();
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
        BigDecimal accumulatedNetProfit = BigDecimal.ZERO;

        for (YearMonth yearMonth : sortedMonths) {
            Map<String, BigDecimal> cumulativeBalances = calculateCumulativeAccountBalancesUpToMonth(
                userEmail,
                yearMonth);

            MonthlyProfitDTO monthlyData = getMonthlyProfit(principal, yearMonth);

            // ============================================================================
            // FIX: Use actual Net Profit instead of manually calculating it
            // ============================================================================
            // monthlyNetProfit already includes (Revenue - COGS - All Expenses)
            accumulatedNetProfit = accumulatedNetProfit.add(monthlyData.getMonthlyNetProfit());

            MonthlyProfitDTO monthlyWithCumulativeBalance = MonthlyProfitDTO.builder()
                .month(yearMonth)
                .bankAccountBalance(cumulativeBalances.get("bank"))
                .cashAccountBalance(cumulativeBalances.get("cash"))
                .totalBalance(cumulativeBalances.get("bank")
                    .add(cumulativeBalances.get("cash")))
                .monthlyProfit(monthlyData.getMonthlyProfit()) // Gross Profit
                .monthlyNetProfit(monthlyData.getMonthlyNetProfit()) // Net Profit
                .accumulatedNetProfit(accumulatedNetProfit)
                .monthlyExpenseTransactions(
                    monthlyData.getMonthlyExpenseTransactions())
                .expenseTransactions(monthlyData.getExpenseTransactions())
                .build();

            result.add(monthlyWithCumulativeBalance);
        }

        return result;
    }

    @Cacheable(value = com.jaoow.helmetstore.cache.CacheNames.MONTHLY_PROFIT, key = "#principal.name + '-' + #yearMonth")
    public MonthlyProfitDTO getMonthlyProfit(Principal principal, YearMonth yearMonth) {
        String userEmail = principal.getName();
        LocalDateTime startOfMonth = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime startOfNextMonth = yearMonth.plusMonths(1).atDay(1).atStartOfDay();

        List<Transaction> monthlyTransactions = transactionRepository
            .findByAccountUserEmailAndDateRange(userEmail, startOfMonth, startOfNextMonth);

        var inventory = inventoryHelper.getInventoryFromPrincipal(principal);

        // ============================================================================
        // USE UNIFIED PROFIT CALCULATION SERVICE
        // ============================================================================
        BigDecimal monthlyNetProfit = profitCalculationService.calculateNetProfitFromTransactions(monthlyTransactions);
        BigDecimal monthlyGrossProfit = profitCalculationService.calculateGrossProfitByDateRange(inventory,
                startOfMonth, startOfNextMonth);
        BigDecimal monthlyExpenseTransactions = profitCalculationService
                .calculateOperationalExpensesFromTransactions(monthlyTransactions);

        // Get all operational expense transactions for detailed breakdown
        List<Transaction> expenseTransactions = monthlyTransactions.stream()
            .filter(Transaction::isAffectsProfit)
            .filter(t -> t.getAmount().compareTo(BigDecimal.ZERO) < 0)
            .filter(t -> t.getDetail() != com.jaoow.helmetstore.model.balance.TransactionDetail.COST_OF_GOODS_SOLD)
            .collect(Collectors.toList());

        Map<String, BigDecimal> accountBalances = calculateMonthlyAccountBalances(monthlyTransactions);

        return MonthlyProfitDTO.builder()
            .month(yearMonth)
            .bankAccountBalance(accountBalances.get("bank"))
            .cashAccountBalance(accountBalances.get("cash"))
            .totalBalance(accountBalances.get("bank").add(accountBalances.get("cash")))
            .monthlyProfit(monthlyGrossProfit) // Gross Profit (for backward compatibility)
            .monthlyNetProfit(monthlyNetProfit) // Net Profit (Revenue - COGS - Expenses)
            .monthlyExpenseTransactions(monthlyExpenseTransactions) // Operational expenses only
            .expenseTransactions(convertToTransactionInfo(expenseTransactions))
            .build();
    }

    private Map<String, BigDecimal> calculateMonthlyAccountBalances(List<Transaction> transactions) {
        // ============================================================================
        // USE LEDGER SYSTEM: Wallet balances based on walletDestination
        // ============================================================================
        BigDecimal bankBalance = transactions.stream()
                .filter(t -> t.getWalletDestination() == AccountType.BANK)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal cashBalance = transactions.stream()
                .filter(t -> t.getWalletDestination() == AccountType.CASH)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return Map.of(
            "bank", bankBalance,
            "cash", cashBalance);
    }

    private Map<String, BigDecimal> calculateCumulativeAccountBalancesUpToMonth(String userEmail,
                                                                                YearMonth targetMonth) {
        LocalDateTime endOfTargetMonth = targetMonth.atEndOfMonth().atTime(23, 59, 59);

        List<Transaction> transactionsUpToMonth = transactionRepository
            .findByAccountUserEmailAndDateRange(userEmail,
                LocalDateTime.of(1900, 1, 1, 0, 0), // Start from beginning
                endOfTargetMonth.plusSeconds(1)); // Include the entire target month

        // ============================================================================
        // USE LEDGER SYSTEM: Cumulative wallet balances based on walletDestination
        // ============================================================================
        BigDecimal cumulativeBankBalance = transactionsUpToMonth.stream()
                .filter(t -> t.getWalletDestination() == AccountType.BANK)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal cumulativeCashBalance = transactionsUpToMonth.stream()
                .filter(t -> t.getWalletDestination() == AccountType.CASH)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return Map.of(
            "bank", cumulativeBankBalance,
            "cash", cumulativeCashBalance);
    }

    private List<TransactionInfo> convertToTransactionInfo(List<Transaction> transactions) {
        return transactions.stream()
                .filter(t -> t.getDetail() != com.jaoow.helmetstore.model.balance.TransactionDetail.COST_OF_GOODS_SOLD) // Hide COGS from UI
                .map(transaction -> modelMapper.map(transaction, TransactionInfo.class))
                .collect(Collectors.toList());
    }
}
