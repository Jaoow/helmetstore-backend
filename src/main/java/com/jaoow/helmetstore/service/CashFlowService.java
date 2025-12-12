package com.jaoow.helmetstore.service;

import com.jaoow.helmetstore.dto.balance.CashFlowSummaryDTO;
import com.jaoow.helmetstore.dto.balance.MonthlyCashFlowDTO;
import com.jaoow.helmetstore.dto.balance.TransactionInfo;
import com.jaoow.helmetstore.model.balance.AccountType;
import com.jaoow.helmetstore.model.balance.Transaction;
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
public class CashFlowService {

    private final TransactionRepository transactionRepository;
    private final ModelMapper modelMapper;
    private final AccountService accountService;

    @Cacheable(value = com.jaoow.helmetstore.cache.CacheNames.CASH_FLOW_SUMMARY, key = "#principal.name")
    public CashFlowSummaryDTO getCashFlowSummary(Principal principal) {
        String userEmail = principal.getName();

        // ============================================================================
        // USE LEDGER SYSTEM: Wallet balances calculated from walletDestination
        // ============================================================================
        BigDecimal bankBalance = accountService.calculateAccountBalanceByType(userEmail, AccountType.BANK);
        BigDecimal cashBalance = accountService.calculateAccountBalanceByType(userEmail, AccountType.CASH);
        BigDecimal totalBalance = bankBalance.add(cashBalance);

        // ============================================================================
        // USE LEDGER SYSTEM: Cash flow calculated from affectsCash flag
        // ============================================================================
        // Only transactions with affectsCash=true represent REAL money movement
        // This excludes COGS (accounting entry) and other non-cash transactions
        List<Transaction> allTransactions = transactionRepository.findByAccountUserEmail(userEmail);

        // Separate positive cash flow (inflows) from negative cash flow (outflows)
        BigDecimal totalIncome = allTransactions.stream()
                .filter(Transaction::isAffectsCash) // Only real cash movements
                .map(Transaction::getAmount) // Positive = inflow
                .filter(amount -> amount.compareTo(BigDecimal.ZERO) > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalExpense = allTransactions.stream()
                .filter(Transaction::isAffectsCash) // Only real cash movements
                .map(Transaction::getAmount) // Negative = outflow
                .filter(amount -> amount.compareTo(BigDecimal.ZERO) < 0)
                .map(BigDecimal::abs) // Convert to positive for display
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Total cash flow = sum of all cash-affecting transactions
        BigDecimal totalCashFlow = allTransactions.stream()
                .filter(Transaction::isAffectsCash)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

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
                .toList();

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

        // ============================================================================
        // USE LEDGER SYSTEM: Only count transactions with affectsCash = true
        // ============================================================================
        BigDecimal monthlyIncome = monthlyTransactions.stream()
                .filter(Transaction::isAffectsCash) // Only real cash movements
                .filter(t -> t.getAmount().compareTo(BigDecimal.ZERO) > 0) // Positive = inflow
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal monthlyExpense = monthlyTransactions.stream()
                .filter(Transaction::isAffectsCash) // Only real cash movements
                .filter(t -> t.getAmount().compareTo(BigDecimal.ZERO) < 0) // Negative = outflow
                .map(Transaction::getAmount)
                .map(BigDecimal::abs) // Convert to positive for display
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal monthlyCashFlow = monthlyTransactions.stream()
                .filter(Transaction::isAffectsCash)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

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
        // ============================================================================
        // USE LEDGER SYSTEM: Wallet balances based on walletDestination
        // ============================================================================
        // walletDestination indicates which wallet (CASH/BANK) the transaction affects
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
                "cash", cashBalance
        );
    }

    private Map<String, BigDecimal> calculateCumulativeAccountBalancesUpToMonth(String userEmail, YearMonth targetMonth) {
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
                "cash", cumulativeCashBalance
        );
    }

    private List<TransactionInfo> convertToTransactionInfo(List<Transaction> transactions) {
        return transactions.stream()
                .filter(Transaction::isAffectsCash) // Only show cash-affecting transactions
                .map(transaction -> modelMapper.map(transaction, TransactionInfo.class))
                .collect(Collectors.toList());
    }
}
