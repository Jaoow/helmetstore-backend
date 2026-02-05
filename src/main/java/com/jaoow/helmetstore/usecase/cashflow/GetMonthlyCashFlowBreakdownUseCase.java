package com.jaoow.helmetstore.usecase.cashflow;

import com.jaoow.helmetstore.dto.balance.MonthlyCashFlowDTO;
import com.jaoow.helmetstore.dto.balance.TransactionInfo;
import com.jaoow.helmetstore.model.balance.AccountType;
import com.jaoow.helmetstore.model.balance.Transaction;
import com.jaoow.helmetstore.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Use Case: Get monthly cash flow breakdown for all months
 * 
 * Responsibilities:
 * - Calculate cash flow metrics for each month
 * - Track cumulative account balances month by month
 * - Include transaction details for each month
 * - Cache results for performance
 * 
 * PERFORMANCE OPTIMIZATION:
 * - Loads all transactions once instead of querying per month
 * - Calculates cumulative balances incrementally
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GetMonthlyCashFlowBreakdownUseCase {

    private final TransactionRepository transactionRepository;
    private final ModelMapper modelMapper;

    @Cacheable(value = com.jaoow.helmetstore.cache.CacheNames.MONTHLY_CASH_FLOW, key = "#userEmail")
    public List<MonthlyCashFlowDTO> execute(String userEmail) {
        log.debug("Executing GetMonthlyCashFlowBreakdownUseCase for user: {}", userEmail);

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

        List<MonthlyCashFlowDTO> result = new ArrayList<>();

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

            // Calculate monthly cash flow values
            BigDecimal monthlyIncome = monthlyTransactions.stream()
                    .filter(Transaction::isAffectsCash)
                    .filter(t -> t.getAmount().compareTo(BigDecimal.ZERO) > 0)
                    .map(Transaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal monthlyExpense = monthlyTransactions.stream()
                    .filter(Transaction::isAffectsCash)
                    .filter(t -> t.getAmount().compareTo(BigDecimal.ZERO) < 0)
                    .map(Transaction::getAmount)
                    .map(BigDecimal::abs)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal monthlyCashFlow = monthlyTransactions.stream()
                    .filter(Transaction::isAffectsCash)
                    .map(Transaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // PERFORMANCE: Update cumulative balances incrementally
            for (Transaction t : monthlyTransactions) {
                if (t.getWalletDestination() == AccountType.BANK) {
                    cumulativeBankBalance = cumulativeBankBalance.add(t.getAmount());
                } else if (t.getWalletDestination() == AccountType.CASH) {
                    cumulativeCashBalance = cumulativeCashBalance.add(t.getAmount());
                }
            }

            MonthlyCashFlowDTO monthlyData = MonthlyCashFlowDTO.builder()
                    .month(yearMonth)
                    .bankAccountBalance(cumulativeBankBalance)
                    .cashAccountBalance(cumulativeCashBalance)
                    .totalBalance(cumulativeBankBalance.add(cumulativeCashBalance))
                    .monthlyIncome(monthlyIncome)
                    .monthlyExpense(monthlyExpense)
                    .monthlyCashFlow(monthlyCashFlow)
                    .transactions(convertToTransactionInfo(monthlyTransactions))
                    .build();

            result.add(monthlyData);
        }

        return result;
    }

    private List<TransactionInfo> convertToTransactionInfo(List<Transaction> transactions) {
        return transactions.stream()
                .filter(Transaction::isAffectsCash) // Only show cash-affecting transactions
                .map(transaction -> modelMapper.map(transaction, TransactionInfo.class))
                .collect(Collectors.toList());
    }
}
