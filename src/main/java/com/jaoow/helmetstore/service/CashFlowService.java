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
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
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
public class CashFlowService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final ModelMapper modelMapper;

    /**
     * Get comprehensive cash flow summary with monthly breakdown
     */
    public CashFlowSummaryDTO getCashFlowSummary(Principal principal) {
        String userEmail = principal.getName();
        
        // Get current account balances
        BigDecimal bankBalance = accountRepository.findBalanceByUserEmailAndType(userEmail, AccountType.BANK)
                .orElse(BigDecimal.ZERO);
        BigDecimal cashBalance = accountRepository.findBalanceByUserEmailAndType(userEmail, AccountType.CASH)
                .orElse(BigDecimal.ZERO);
        BigDecimal totalBalance = bankBalance.add(cashBalance);
        
        // Get all transactions
        List<Transaction> allTransactions = transactionRepository.findByAccountUserEmail(userEmail);
        
        // Calculate totals
        BigDecimal totalIncome = allTransactions.stream()
                .filter(t -> t.getType() == TransactionType.INCOME)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal totalExpense = allTransactions.stream()
                .filter(t -> t.getType() == TransactionType.EXPENSE)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal totalCashFlow = totalIncome.subtract(totalExpense);
        
        // Get monthly breakdown with cumulative balances
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

    /**
     * Get monthly cash flow breakdown with cumulative balances
     */
    public List<MonthlyCashFlowDTO> getMonthlyCashFlowBreakdown(String userEmail) {
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
        List<MonthlyCashFlowDTO> result = new ArrayList<>();
        
        for (YearMonth yearMonth : sortedMonths) {
            // Calculate cumulative balances up to the end of this month
            Map<String, BigDecimal> cumulativeBalances = calculateCumulativeAccountBalancesUpToMonth(userEmail, yearMonth);
            
            // Get monthly data for this month
            MonthlyCashFlowDTO monthlyData = getMonthlyCashFlow(userEmail, yearMonth);
            
            // Create the monthly data with cumulative balances
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

    /**
     * Get cash flow for a specific month with cumulative balance calculation
     */
    public MonthlyCashFlowDTO getMonthlyCashFlow(String userEmail, YearMonth yearMonth) {
        LocalDateTime startOfMonth = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime startOfNextMonth = yearMonth.plusMonths(1).atDay(1).atStartOfDay();
        
        List<Transaction> monthlyTransactions = transactionRepository
                .findByAccountUserEmailAndDateRange(userEmail, startOfMonth, startOfNextMonth);
        
        // Calculate monthly totals
        BigDecimal monthlyIncome = monthlyTransactions.stream()
                .filter(t -> t.getType() == TransactionType.INCOME)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal monthlyExpense = monthlyTransactions.stream()
                .filter(t -> t.getType() == TransactionType.EXPENSE)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal monthlyCashFlow = monthlyIncome.subtract(monthlyExpense);
        
        // Calculate separate bank and cash balances for this month
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
                "cash", cashBalance
        );
    }

    /**
     * Calculate cumulative balance up to the end of a specific month
     */
    private BigDecimal calculateCumulativeBalanceUpToMonth(String userEmail, YearMonth targetMonth) {
        // Get all transactions up to the end of the target month
        LocalDateTime endOfTargetMonth = targetMonth.atEndOfMonth().atTime(23, 59, 59);
        
        List<Transaction> transactionsUpToMonth = transactionRepository
                .findByAccountUserEmailAndDateRange(userEmail, 
                        LocalDateTime.of(1900, 1, 1, 0, 0), // Start from beginning
                        endOfTargetMonth.plusSeconds(1)); // Include the entire target month
        
        // Calculate cumulative balance
        BigDecimal cumulativeBalance = transactionsUpToMonth.stream()
                .map(transaction -> {
                    if (transaction.getType() == TransactionType.INCOME) {
                        return transaction.getAmount();
                    } else {
                        return transaction.getAmount().negate();
                    }
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return cumulativeBalance;
    }

    /**
     * Calculate cumulative bank and cash balances up to the end of a specific month
     */
    private Map<String, BigDecimal> calculateCumulativeAccountBalancesUpToMonth(String userEmail, YearMonth targetMonth) {
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
                "cash", cumulativeCashBalance
        );
    }

    /**
     * Get account balances at a specific date (simplified for current implementation)
     */
    private Map<AccountType, BigDecimal> getAccountBalancesAtDate(String userEmail, LocalDateTime date) {
        // This is a simplified implementation
        // In a real system, you might want to calculate running balances
        BigDecimal bankBalance = accountRepository.findBalanceByUserEmailAndType(userEmail, AccountType.BANK)
                .orElse(BigDecimal.ZERO);
        BigDecimal cashBalance = accountRepository.findBalanceByUserEmailAndType(userEmail, AccountType.CASH)
                .orElse(BigDecimal.ZERO);
        
        return Map.of(
                AccountType.BANK, bankBalance,
                AccountType.CASH, cashBalance
        );
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