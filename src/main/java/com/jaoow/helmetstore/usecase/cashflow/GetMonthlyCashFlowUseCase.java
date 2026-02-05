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
import java.util.List;
import java.util.stream.Collectors;

/**
 * Use Case: Get cash flow details for a specific month
 * 
 * Responsibilities:
 * - Calculate cash flow metrics for a single month
 * - Calculate cumulative account balances up to that month
 * - Include income, expense breakdown and transaction details
 * - Cache results for performance
 * 
 * PERFORMANCE OPTIMIZATION:
 * - Uses SQL aggregations instead of loading all transactions
 * - Calculates cumulative balances using repository methods
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GetMonthlyCashFlowUseCase {

    private final TransactionRepository transactionRepository;
    private final ModelMapper modelMapper;

    @Cacheable(value = com.jaoow.helmetstore.cache.CacheNames.MONTHLY_CASH_FLOW, key = "#userEmail + '-' + #yearMonth")
    public MonthlyCashFlowDTO execute(String userEmail, YearMonth yearMonth) {
        log.debug("Executing GetMonthlyCashFlowUseCase for user: {} and month: {}", userEmail, yearMonth);

        LocalDateTime startOfMonth = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime startOfNextMonth = yearMonth.plusMonths(1).atDay(1).atStartOfDay();
        LocalDateTime endOfMonth = yearMonth.atEndOfMonth().atTime(23, 59, 59);

        // ============================================================================
        // PERFORMANCE: Use SQL aggregations instead of loading entities
        // ============================================================================
        BigDecimal monthlyCashFlow = transactionRepository.calculateCashFlowByDateRange(
                userEmail, startOfMonth, startOfNextMonth);

        // Calculate cumulative balances up to end of this month using SQL aggregation
        BigDecimal cumulativeBankBalance = transactionRepository.calculateWalletBalanceUpToDate(
                userEmail, AccountType.BANK, endOfMonth);
        BigDecimal cumulativeCashBalance = transactionRepository.calculateWalletBalanceUpToDate(
                userEmail, AccountType.CASH, endOfMonth);

        // Calculate monthly income/expense with SQL aggregations
        BigDecimal monthlyIncome = transactionRepository.calculateCashIncomeByDateRange(
                userEmail, startOfMonth, startOfNextMonth);
        BigDecimal monthlyExpense = transactionRepository.calculateCashExpenseByDateRange(
                userEmail, startOfMonth, startOfNextMonth);

        // Get transactions for detailed breakdown (only if needed)
        List<Transaction> monthlyTransactions = transactionRepository
                .findByAccountUserEmailAndDateRange(userEmail, startOfMonth, startOfNextMonth);

        return MonthlyCashFlowDTO.builder()
                .month(yearMonth)
                .bankAccountBalance(cumulativeBankBalance)
                .cashAccountBalance(cumulativeCashBalance)
                .totalBalance(cumulativeBankBalance.add(cumulativeCashBalance))
                .monthlyIncome(monthlyIncome)
                .monthlyExpense(monthlyExpense)
                .monthlyCashFlow(monthlyCashFlow)
                .transactions(convertToTransactionInfo(monthlyTransactions))
                .build();
    }

    private List<TransactionInfo> convertToTransactionInfo(List<Transaction> transactions) {
        return transactions.stream()
                .filter(Transaction::isAffectsCash) // Only show cash-affecting transactions
                .map(transaction -> modelMapper.map(transaction, TransactionInfo.class))
                .collect(Collectors.toList());
    }
}
