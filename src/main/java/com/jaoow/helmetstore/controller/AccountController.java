package com.jaoow.helmetstore.controller;

import com.jaoow.helmetstore.dto.balance.AccountInfo;
import com.jaoow.helmetstore.dto.balance.AvailableMonthDTO;
import com.jaoow.helmetstore.dto.balance.BalanceConversionDTO;
import com.jaoow.helmetstore.dto.balance.CashFlowSummaryDTO;
import com.jaoow.helmetstore.dto.balance.FinancialSummaryDTO;
import com.jaoow.helmetstore.dto.balance.MonthlyCashFlowDTO;
import com.jaoow.helmetstore.dto.balance.MonthlyProfitDTO;
import com.jaoow.helmetstore.dto.balance.ProfitSummaryDTO;
import com.jaoow.helmetstore.dto.balance.TransactionCreateDTO;
import com.jaoow.helmetstore.service.AccountService;
import com.jaoow.helmetstore.service.CashFlowService;
import com.jaoow.helmetstore.service.ProfitTrackingService;
import com.jaoow.helmetstore.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/account")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final TransactionService transactionService;
    private final CashFlowService cashFlowService;
    private final ProfitTrackingService profitTrackingService;

    @GetMapping
    public List<AccountInfo> getAccountInfo(Principal principal) {
        return accountService.getAccountInfo(principal);
    }

    /**
     * Get available months with transaction counts (lightweight for month selector UI)
     */
    @GetMapping("/available-months")
    public List<AvailableMonthDTO> getAvailableMonths(Principal principal) {
        return transactionService.getAvailableMonths(principal.getName());
    }

    @GetMapping("/financial-summary")
    public FinancialSummaryDTO getFinancialSummary(Principal principal) {
        return transactionService.calculateFinancialSummary(principal);
    }

    @GetMapping("/profit")
    public BigDecimal getProfit(Principal principal) {
        return transactionService.calculateProfit(principal);
    }

    @GetMapping("/cash-flow")
    public BigDecimal getCashFlow(Principal principal) {
        return transactionService.calculateCashFlow(principal);
    }

    /**
     * Get comprehensive cash flow summary with monthly breakdown
     */
    @GetMapping("/cash-flow-summary")
    public CashFlowSummaryDTO getCashFlowSummary(Principal principal) {
        return cashFlowService.getCashFlowSummary(principal);
    }

    /**
     * Get monthly cash flow breakdown
     */
    @GetMapping("/cash-flow/monthly")
    public List<MonthlyCashFlowDTO> getMonthlyCashFlowBreakdown(Principal principal) {
        return cashFlowService.getMonthlyCashFlowBreakdown(principal.getName());
    }

    /**
     * Get cash flow for a specific month
     */
    @GetMapping("/cash-flow/monthly/{year}/{month}")
    public MonthlyCashFlowDTO getMonthlyCashFlow(
            @PathVariable int year,
            @PathVariable int month,
            Principal principal) {
        YearMonth yearMonth = YearMonth.of(year, month);
        return cashFlowService.getMonthlyCashFlow(principal.getName(), yearMonth);
    }

    /**
     * Get comprehensive profit summary with monthly breakdown
     */
    @GetMapping("/profit-summary")
    public ProfitSummaryDTO getProfitSummary(Principal principal) {
        return profitTrackingService.getProfitSummary(principal);
    }

    /**
     * Get monthly profit breakdown
     */
    @GetMapping("/profit/monthly")
    public List<MonthlyProfitDTO> getMonthlyProfitBreakdown(Principal principal) {
        return profitTrackingService.getMonthlyProfitBreakdown(principal);
    }

    /**
     * Get profit for a specific month
     */
    @GetMapping("/profit/monthly/{year}/{month}")
    public MonthlyProfitDTO getMonthlyProfit(
            @PathVariable int year,
            @PathVariable int month,
            Principal principal) {
        YearMonth yearMonth = YearMonth.of(year, month);
        return profitTrackingService.getMonthlyProfit(principal, yearMonth);
    }

    @PostMapping("/transaction")
    public void createTransaction(@Valid @RequestBody TransactionCreateDTO transactionCreateDTO, Principal principal) {
        transactionService.createManualTransaction(transactionCreateDTO, principal);
    }

    @DeleteMapping("/transaction/{transactionId}")
    public void deleteTransaction(@PathVariable Long transactionId, Principal principal) {
        transactionService.deleteTransactionById(transactionId, principal);
    }

    @PutMapping("/transaction/{transactionId}")
    public void updateTransaction(@PathVariable Long transactionId,
            @RequestBody TransactionCreateDTO transactionCreateDTO, Principal principal) {
        transactionService.updateTransaction(transactionId, transactionCreateDTO, principal);
    }

    @PostMapping("/convert/balance")
    public void convertBalance(@Valid @RequestBody BalanceConversionDTO conversionDTO, Principal principal) {
        accountService.convertBalance(conversionDTO, principal);
    }

    /**
     * @deprecated Reinvestment concept removed. "Reinvesting" is simply NOT withdrawing profit.
     * Use PROFIT_WITHDRAWAL transaction type for withdrawals instead.
     * This endpoint is kept for backward compatibility but will be removed in future versions.
     */
    @Deprecated(since = "2.0.3", forRemoval = true)
    @PostMapping("/reinvest")
    public void reinvest(@Valid @RequestBody Object reinvestmentRequestDTO, Principal principal) {
        throw new UnsupportedOperationException(
            "Reinvestment feature has been removed. Use profit withdrawal transactions instead. " +
            "Profit that is not withdrawn automatically stays in the company cash balance.");
    }

}
