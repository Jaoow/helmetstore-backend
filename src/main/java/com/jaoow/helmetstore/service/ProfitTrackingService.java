package com.jaoow.helmetstore.service;

import com.jaoow.helmetstore.dto.FinancialSummaryDTO;
import com.jaoow.helmetstore.dto.balance.MonthlyProfitDTO;
import com.jaoow.helmetstore.dto.balance.ProfitSummaryDTO;
import com.jaoow.helmetstore.dto.balance.TransactionInfo;
import com.jaoow.helmetstore.helper.InventoryHelper;
import com.jaoow.helmetstore.model.balance.AccountType;
import com.jaoow.helmetstore.model.balance.PaymentMethod;
import com.jaoow.helmetstore.model.balance.Transaction;
import com.jaoow.helmetstore.model.balance.TransactionType;
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

        @Cacheable(value = com.jaoow.helmetstore.cache.CacheNames.PROFIT_SUMMARY, key = "#principal.name")
        public ProfitSummaryDTO getProfitSummary(Principal principal) {
                String userEmail = principal.getName();

                BigDecimal bankBalance = accountService.calculateAccountBalanceByType(userEmail, AccountType.BANK);
                BigDecimal cashBalance = accountService.calculateAccountBalanceByType(userEmail, AccountType.CASH);
                BigDecimal totalBalance = bankBalance.add(cashBalance);

                List<Transaction> allTransactions = transactionRepository.findByAccountUserEmail(userEmail);

                var inventory = inventoryHelper.getInventoryFromPrincipal(principal);
                BigDecimal totalProfit = saleRepository.getFinancialSummary(inventory)
                                .map(FinancialSummaryDTO::getTotalProfit)
                                .orElse(BigDecimal.ZERO);

                BigDecimal totalExpenseTransactions = allTransactions.stream()
                                .filter(Transaction::affectsWithdrawableProfit)
                                .map(Transaction::getAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal totalNetProfit = totalProfit.subtract(totalExpenseTransactions);

                List<MonthlyProfitDTO> monthlyBreakdown = getMonthlyProfitBreakdown(principal);

                return ProfitSummaryDTO.builder()
                                .totalBankBalance(bankBalance)
                                .totalCashBalance(cashBalance)
                                .totalBalance(totalBalance)
                                .totalProfit(totalProfit)
                                .totalNetProfit(totalNetProfit)
                                .totalExpenseTransactions(totalExpenseTransactions)
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
                                .collect(Collectors.toList());

                List<MonthlyProfitDTO> result = new ArrayList<>();
                BigDecimal accumulatedNetProfit = BigDecimal.ZERO;

                for (YearMonth yearMonth : sortedMonths) {
                        Map<String, BigDecimal> cumulativeBalances = calculateCumulativeAccountBalancesUpToMonth(
                                        userEmail,
                                        yearMonth);

                        MonthlyProfitDTO monthlyData = getMonthlyProfit(principal, yearMonth);

                        BigDecimal monthlyProfitAvailable = monthlyData.getMonthlyProfit()
                                        .subtract(monthlyData.getMonthlyExpenseTransactions());
                        accumulatedNetProfit = accumulatedNetProfit
                                        .add(monthlyProfitAvailable);

                        MonthlyProfitDTO monthlyWithCumulativeBalance = MonthlyProfitDTO.builder()
                                        .month(yearMonth)
                                        .bankAccountBalance(cumulativeBalances.get("bank"))
                                        .cashAccountBalance(cumulativeBalances.get("cash"))
                                        .totalBalance(cumulativeBalances.get("bank")
                                                        .add(cumulativeBalances.get("cash")))
                                        .monthlyProfit(monthlyData.getMonthlyProfit())
                                        .accumulatedNetProfit(
                                                        accumulatedNetProfit)
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
                BigDecimal monthlyProfit = saleRepository.getTotalProfitByDateRange(inventory, startOfMonth,
                                startOfNextMonth);

                BigDecimal monthlyExpenseTransactions = monthlyTransactions.stream()
                                .filter(Transaction::affectsWithdrawableProfit)
                                .map(Transaction::getAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                List<Transaction> expenseTransactions = monthlyTransactions.stream()
                                .filter(Transaction::affectsWithdrawableProfit)
                                .collect(Collectors.toList());

                Map<String, BigDecimal> accountBalances = calculateMonthlyAccountBalances(monthlyTransactions);

                return MonthlyProfitDTO.builder()
                                .month(yearMonth)
                                .bankAccountBalance(accountBalances.get("bank"))
                                .cashAccountBalance(accountBalances.get("cash"))
                                .totalBalance(accountBalances.get("bank").add(accountBalances.get("cash")))
                                .monthlyProfit(monthlyProfit)
                                .monthlyExpenseTransactions(monthlyExpenseTransactions)
                                .expenseTransactions(convertToTransactionInfo(expenseTransactions))
                                .build();
        }

        private Map<String, BigDecimal> calculateMonthlyAccountBalances(List<Transaction> transactions) {
                BigDecimal bankBalance = BigDecimal.ZERO;
                BigDecimal cashBalance = BigDecimal.ZERO;

                for (Transaction transaction : transactions) {
                        BigDecimal amount = transaction.getAmount();
                        if (transaction.getType() == TransactionType.EXPENSE) {
                                amount = amount.negate();
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

        private Map<String, BigDecimal> calculateCumulativeAccountBalancesUpToMonth(String userEmail,
                        YearMonth targetMonth) {
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

        private List<TransactionInfo> convertToTransactionInfo(List<Transaction> transactions) {
                return transactions.stream()
                                .map(transaction -> modelMapper.map(transaction, TransactionInfo.class))
                                .collect(Collectors.toList());
        }
}