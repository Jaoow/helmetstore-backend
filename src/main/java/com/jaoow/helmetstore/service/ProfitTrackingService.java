package com.jaoow.helmetstore.service;

import com.jaoow.helmetstore.dto.FinancialSummaryDTO;
import com.jaoow.helmetstore.dto.balance.MonthlyProfitDTO;
import com.jaoow.helmetstore.dto.balance.ProfitSummaryDTO;
import com.jaoow.helmetstore.dto.balance.TransactionInfo;
import com.jaoow.helmetstore.model.balance.AccountType;
import com.jaoow.helmetstore.model.balance.PaymentMethod;
import com.jaoow.helmetstore.model.balance.Transaction;
import com.jaoow.helmetstore.model.balance.TransactionType;
import com.jaoow.helmetstore.helper.InventoryHelper;
import com.jaoow.helmetstore.repository.AccountRepository;
import com.jaoow.helmetstore.repository.SaleRepository;
import com.jaoow.helmetstore.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
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
public class ProfitTrackingService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final SaleRepository saleRepository;
    private final InventoryHelper inventoryHelper;
    private final ModelMapper modelMapper;

    /**
     * Get comprehensive profit summary with monthly breakdown
     */
    @Cacheable(value = com.jaoow.helmetstore.cache.CacheNames.PROFIT_SUMMARY, key = "#principal.name")
    public ProfitSummaryDTO getProfitSummary(Principal principal) {
        String userEmail = principal.getName();

        // Get current account balances
        BigDecimal bankBalance = accountRepository.findBalanceByUserEmailAndType(userEmail, AccountType.BANK)
                .orElse(BigDecimal.ZERO);
        BigDecimal cashBalance = accountRepository.findBalanceByUserEmailAndType(userEmail, AccountType.CASH)
                .orElse(BigDecimal.ZERO);
        BigDecimal totalBalance = bankBalance.add(cashBalance);

        // Get all transactions
        List<Transaction> allTransactions = transactionRepository.findByAccountUserEmail(userEmail);

        // Calculate total profit from sales using totalProfit field
        var inventory = inventoryHelper.getInventoryFromPrincipal(principal);
        BigDecimal totalProfit = saleRepository.getFinancialSummary(inventory)
                .map(FinancialSummaryDTO::getTotalProfit)
                .orElse(BigDecimal.ZERO);

        // Calculate total profit-deducting transactions
        BigDecimal totalProfitDeductingTransactions = allTransactions.stream()
                .filter(t -> t.getDetail().deductsFromProfit())
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calculate profit available for withdrawal
        BigDecimal totalProfitAvailableForWithdrawal = totalProfit.subtract(totalProfitDeductingTransactions);

        // Get monthly breakdown with cumulative balances
        List<MonthlyProfitDTO> monthlyBreakdown = getMonthlyProfitBreakdown(principal);

        return ProfitSummaryDTO.builder()
                .totalBankBalance(bankBalance)
                .totalCashBalance(cashBalance)
                .totalBalance(totalBalance)
                .totalProfit(totalProfit)
                .totalProfitAvailableForWithdrawal(totalProfitAvailableForWithdrawal)
                .totalProfitDeductingTransactions(totalProfitDeductingTransactions)
                .monthlyBreakdown(monthlyBreakdown)
                .build();
    }

    /**
     * Get monthly profit breakdown with cumulative balances
     */
    @Cacheable(value = com.jaoow.helmetstore.cache.CacheNames.MONTHLY_PROFIT, key = "#principal.name")
    public List<MonthlyProfitDTO> getMonthlyProfitBreakdown(Principal principal) {
        String userEmail = principal.getName();
        List<Object[]> distinctMonths = transactionRepository.findDistinctMonthsByUserEmail(userEmail);

        // Sort months chronologically (oldest first) to calculate cumulative balances
        List<YearMonth> sortedMonths = distinctMonths.stream()
                .map(monthData -> {
                    Integer year = (Integer) monthData[0];
                    Integer month = (Integer) monthData[1];
                    return YearMonth.of(year, month);
                })
                .sorted()
                .collect(Collectors.toList());

        // Calculate cumulative balances month by month using traditional loop
        List<MonthlyProfitDTO> result = new ArrayList<>();
        BigDecimal accumulatedProfitAvailableForWithdrawal = BigDecimal.ZERO;

        for (YearMonth yearMonth : sortedMonths) {
            // Calculate cumulative balances up to the end of this month
            Map<String, BigDecimal> cumulativeBalances = calculateCumulativeAccountBalancesUpToMonth(userEmail,
                    yearMonth);

            // Get monthly data for this month
            MonthlyProfitDTO monthlyData = getMonthlyProfit(principal, yearMonth);

            // Calculate accumulated profit available for withdrawal
            BigDecimal monthlyProfitAvailable = monthlyData.getMonthlyProfit()
                    .subtract(monthlyData.getMonthlyProfitDeductingTransactions());
            accumulatedProfitAvailableForWithdrawal = accumulatedProfitAvailableForWithdrawal
                    .add(monthlyProfitAvailable);

            // Create the monthly data with cumulative balances and accumulated profit
            MonthlyProfitDTO monthlyWithCumulativeBalance = MonthlyProfitDTO.builder()
                    .month(yearMonth)
                    .bankAccountBalance(cumulativeBalances.get("bank"))
                    .cashAccountBalance(cumulativeBalances.get("cash"))
                    .totalBalance(cumulativeBalances.get("bank").add(cumulativeBalances.get("cash")))
                    .monthlyProfit(monthlyData.getMonthlyProfit())
                    .accumulatedProfitAvailableForWithdrawal(accumulatedProfitAvailableForWithdrawal)
                    .monthlyProfitDeductingTransactions(monthlyData.getMonthlyProfitDeductingTransactions())
                    .profitDeductingTransactions(monthlyData.getProfitDeductingTransactions())
                    .build();

            result.add(monthlyWithCumulativeBalance);
        }

        return result;
    }

    /**
     * Get profit for a specific month with cumulative balance calculation
     */
    @Cacheable(value = com.jaoow.helmetstore.cache.CacheNames.MONTHLY_PROFIT, key = "#principal.name + '-' + #yearMonth")
    public MonthlyProfitDTO getMonthlyProfit(Principal principal, YearMonth yearMonth) {
        String userEmail = principal.getName();
        LocalDateTime startOfMonth = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime startOfNextMonth = yearMonth.plusMonths(1).atDay(1).atStartOfDay();

        List<Transaction> monthlyTransactions = transactionRepository
                .findByAccountUserEmailAndDateRange(userEmail, startOfMonth, startOfNextMonth);

        // Calculate monthly profit from sales using totalProfit field
        var inventory = inventoryHelper.getInventoryFromPrincipal(principal);
        BigDecimal monthlyProfit = saleRepository.getTotalProfitByDateRange(inventory, startOfMonth, startOfNextMonth);

        // Calculate monthly profit-deducting transactions
        BigDecimal monthlyProfitDeductingTransactions = monthlyTransactions.stream()
                .filter(t -> t.getDetail().deductsFromProfit())
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Get profit-deducting transactions for this month
        List<Transaction> profitDeductingTransactions = monthlyTransactions.stream()
                .filter(t -> t.getDetail().deductsFromProfit())
                .collect(Collectors.toList());

        // Calculate separate bank and cash balances for this month
        Map<String, BigDecimal> accountBalances = calculateMonthlyAccountBalances(monthlyTransactions);

        return MonthlyProfitDTO.builder()
                .month(yearMonth)
                .bankAccountBalance(accountBalances.get("bank"))
                .cashAccountBalance(accountBalances.get("cash"))
                .totalBalance(accountBalances.get("bank").add(accountBalances.get("cash")))
                .monthlyProfit(monthlyProfit)
                .monthlyProfitDeductingTransactions(monthlyProfitDeductingTransactions)
                .profitDeductingTransactions(convertToTransactionInfo(profitDeductingTransactions))
                .build();
    }

    /**
     * Calculate separate bank and cash balances for monthly transactions
     */
    private Map<String, BigDecimal> calculateMonthlyAccountBalances(List<Transaction> transactions) {
        BigDecimal bankBalance = BigDecimal.ZERO;
        BigDecimal cashBalance = BigDecimal.ZERO;

        for (Transaction transaction : transactions) {
            BigDecimal amount = transaction.getAmount();
            if (transaction.getType() == TransactionType.EXPENSE) {
                amount = amount.negate(); // Expenses reduce balance
            }

            // Determine which account this transaction affects based on payment method
            PaymentMethod paymentMethod = transaction.getPaymentMethod();
            if (paymentMethod == PaymentMethod.CASH) {
                cashBalance = cashBalance.add(amount);
            } else {
                // PIX and CARD transactions affect bank balance
                bankBalance = bankBalance.add(amount);
            }
        }

        return Map.of(
                "bank", bankBalance,
                "cash", cashBalance);
    }

    /**
     * Calculate cumulative bank and cash balances up to the end of a specific month
     */
    private Map<String, BigDecimal> calculateCumulativeAccountBalancesUpToMonth(String userEmail,
            YearMonth targetMonth) {
        // Get all transactions up to the end of the target month
        LocalDateTime endOfTargetMonth = targetMonth.atEndOfMonth().atTime(23, 59, 59);

        List<Transaction> transactionsUpToMonth = transactionRepository
                .findByAccountUserEmailAndDateRange(userEmail,
                        LocalDateTime.of(1900, 1, 1, 0, 0), // Start from beginning
                        endOfTargetMonth.plusSeconds(1)); // Include the entire target month

        BigDecimal cumulativeBankBalance = BigDecimal.ZERO;
        BigDecimal cumulativeCashBalance = BigDecimal.ZERO;

        for (Transaction transaction : transactionsUpToMonth) {
            BigDecimal amount = transaction.getAmount();
            if (transaction.getType() == TransactionType.EXPENSE) {
                amount = amount.negate(); // Expenses reduce balance
            }

            // Determine which account this transaction affects based on payment method
            PaymentMethod paymentMethod = transaction.getPaymentMethod();
            if (paymentMethod == PaymentMethod.CASH) {
                cumulativeCashBalance = cumulativeCashBalance.add(amount);
            } else {
                // PIX and CARD transactions affect bank balance
                cumulativeBankBalance = cumulativeBankBalance.add(amount);
            }
        }

        return Map.of(
                "bank", cumulativeBankBalance,
                "cash", cumulativeCashBalance);
    }

    /**
     * Convert Transaction entities to TransactionInfo DTOs using ModelMapper
     */
    private List<TransactionInfo> convertToTransactionInfo(List<Transaction> transactions) {
        return transactions.stream()
                .map(transaction -> modelMapper.map(transaction, TransactionInfo.class))
                .collect(Collectors.toList());
    }
}