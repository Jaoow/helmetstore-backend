package com.jaoow.helmetstore.controller;

import com.jaoow.helmetstore.dto.balance.AccountInfo;
import com.jaoow.helmetstore.dto.balance.CashFlowSummaryDTO;
import com.jaoow.helmetstore.dto.balance.MonthlyCashFlowDTO;
import com.jaoow.helmetstore.dto.balance.TransactionCreateDTO;
import com.jaoow.helmetstore.service.AccountService;
import com.jaoow.helmetstore.service.CashFlowService;
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

    @GetMapping
    public List<AccountInfo> getAccountInfo(Principal principal) {
        return accountService.getAccountInfo(principal);
    }

    @GetMapping("/financial-summary")
    public TransactionService.FinancialSummary getFinancialSummary(Principal principal) {
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

    @PostMapping("/transaction")
    public void createTransaction(@Valid @RequestBody TransactionCreateDTO transactionCreateDTO, Principal principal) {
        transactionService.createManualTransaction(transactionCreateDTO, principal);
    }

    @DeleteMapping("/transaction/{transactionId}")
    public void deleteTransaction(@PathVariable Long transactionId, Principal principal) {
        transactionService.deleteTransactionById(transactionId, principal);
    }

    @PutMapping("/transaction/{transactionId}")
    public void updateTransaction(@PathVariable Long transactionId, @RequestBody TransactionCreateDTO transactionCreateDTO, Principal principal) {
        transactionService.updateTransaction(transactionId, transactionCreateDTO, principal);
    }

}
