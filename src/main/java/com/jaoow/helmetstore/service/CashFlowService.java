package com.jaoow.helmetstore.service;

import com.jaoow.helmetstore.dto.balance.CashFlowSummaryDTO;
import com.jaoow.helmetstore.dto.balance.MonthlyCashFlowDTO;
import com.jaoow.helmetstore.dto.balance.TransactionInfo;
import com.jaoow.helmetstore.model.balance.AccountType;
import com.jaoow.helmetstore.model.balance.PaymentMethod;
import com.jaoow.helmetstore.model.balance.Transaction;
import com.jaoow.helmetstore.model.balance.TransactionType;
import com.jaoow.helmetstore.repository.AccountRepository;
import com.jaoow.helmetstore.repository.TransactionRepository;
import com.jaoow.helmetstore.service.AccountService;
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
public class CashFlowService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final ModelMapper modelMapper;
    private final AccountService accountService;

    @Cacheable(value = com.jaoow.helmetstore.cache.CacheNames.CASH_FLOW_SUMMARY, key = "#principal.name")
    public CashFlowSummaryDTO getCashFlowSummary(Principal principal) {
        String userEmail = principal.getName();

        BigDecimal bankBalance = accountService.calculateAccountBalanceByType(userEmail, AccountType.BANK);
        BigDecimal cashBalance = accountService.calculateAccountBalanceByType(userEmail, AccountType.CASH);
        BigDecimal totalBalance = bankBalance.add(cashBalance);

        List<Transaction> allTransactions = transactionRepository.findByAccountUserEmail(userEmail);

        BigDecimal totalIncome = allTransactions.stream()
                .filter(t -> t.getType() == TransactionType.INCOME)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalExpense = allTransactions.stream()
                .filter(t -> t.getType() == TransactionType.EXPENSE)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCashFlow = totalIncome.subtract(totalExpense);

        List<MonthlyCashFlowDTO> monthlyBreakdown = getMonthlyCashFlowBreakdown(userEmail);

        return CashFlowSummaryDTO.builder()
                .totalBankBalance(bankBalance)
                .totalCashBalance(cashBalance)
                .totalBalance(totalBalance)
                .totalIncome(totalIncome)
                .totalExpense(totalExpense)
                .totalCashFlow(totalCashFlow)
                .monthlyBreakdown(monthlyBreakdown)
                .build();
    }

    @Cacheable(value = com.jaoow.helmetstore.cache.CacheNames.MONTHLY_CASH_FLOW, key = "#userEmail")
    public List<MonthlyCashFlowDTO> getMonthlyCashFlowBreakdown(String userEmail) {
        List<Object[]> distinctMonths = transactionRepository.findDistinctMonthsByUserEmail(userEmail);

        List<YearMonth> sortedMonths = distinctMonths.stream()
                .map(monthData -> {
                    Integer year = (Integer) monthData[0];
                    Integer month = (Integer) monthData[1];
                    return YearMonth.of(year, month);
                })
                .sorted()
                .collect(Collectors.toList());

        List<MonthlyCashFlowDTO> result = new ArrayList<>();

        for (YearMonth yearMonth : sortedMonths) {
            Map<String, BigDecimal> cumulativeBalances = calculateCumulativeAccountBalancesUpToMonth(userEmail, yearMonth);

            MonthlyCashFlowDTO monthlyData = getMonthlyCashFlow(userEmail, yearMonth);

            MonthlyCashFlowDTO monthlyWithCumulativeBalance = MonthlyCashFlowDTO.builder()
                    .month(yearMonth)
                    .bankAccountBalance(cumulativeBalances.get("bank"))
                    .cashAccountBalance(cumulativeBalances.get("cash"))
                    .totalBalance(cumulativeBalances.get("bank").add(cumulativeBalances.get("cash")))
                    .monthlyIncome(monthlyData.getMonthlyIncome())
                    .monthlyExpense(monthlyData.getMonthlyExpense())
                    .monthlyCashFlow(monthlyData.getMonthlyCashFlow())
                    .transactions(monthlyData.getTransactions())
                    .build();

            result.add(monthlyWithCumulativeBalance);
        }

        return result;
    }

    @Cacheable(value = com.jaoow.helmetstore.cache.CacheNames.MONTHLY_CASH_FLOW, key = "#userEmail + '-' + #yearMonth")
    public MonthlyCashFlowDTO getMonthlyCashFlow(String userEmail, YearMonth yearMonth) {
        LocalDateTime startOfMonth = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime startOfNextMonth = yearMonth.plusMonths(1).atDay(1).atStartOfDay();

        List<Transaction> monthlyTransactions = transactionRepository
                .findByAccountUserEmailAndDateRange(userEmail, startOfMonth, startOfNextMonth);

        BigDecimal monthlyIncome = monthlyTransactions.stream()
                .filter(t -> t.getType() == TransactionType.INCOME)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal monthlyExpense = monthlyTransactions.stream()
                .filter(t -> t.getType() == TransactionType.EXPENSE)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal monthlyCashFlow = monthlyIncome.subtract(monthlyExpense);

        Map<String, BigDecimal> accountBalances = calculateMonthlyAccountBalances(monthlyTransactions);

        return MonthlyCashFlowDTO.builder()
                .month(yearMonth)
                .bankAccountBalance(accountBalances.get("bank"))
                .cashAccountBalance(accountBalances.get("cash"))
                .totalBalance(accountBalances.get("bank").add(accountBalances.get("cash")))
                .monthlyIncome(monthlyIncome)
                .monthlyExpense(monthlyExpense)
                .monthlyCashFlow(monthlyCashFlow)
                .transactions(convertToTransactionInfo(monthlyTransactions))
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
                "cash", cashBalance
        );
    }

    private Map<String, BigDecimal> calculateCumulativeAccountBalancesUpToMonth(String userEmail, YearMonth targetMonth) {
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
                "cash", cumulativeCashBalance
        );
    }

    private List<TransactionInfo> convertToTransactionInfo(List<Transaction> transactions) {
        return transactions.stream()
                .map(transaction -> modelMapper.map(transaction, TransactionInfo.class))
                .collect(Collectors.toList());
    }
} 