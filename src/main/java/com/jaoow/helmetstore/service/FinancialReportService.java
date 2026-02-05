package com.jaoow.helmetstore.service;

import com.jaoow.helmetstore.dto.balance.CashFlowSummaryDTO;
import com.jaoow.helmetstore.dto.balance.MonthlyCashFlowDTO;
import com.jaoow.helmetstore.dto.balance.MonthlyProfitDTO;
import com.jaoow.helmetstore.dto.balance.ProfitSummaryDTO;
import com.jaoow.helmetstore.usecase.cashflow.GetCashFlowSummaryUseCase;
import com.jaoow.helmetstore.usecase.cashflow.GetMonthlyCashFlowBreakdownUseCase;
import com.jaoow.helmetstore.usecase.cashflow.GetMonthlyCashFlowUseCase;
import com.jaoow.helmetstore.usecase.profit.GetMonthlyProfitBreakdownUseCase;
import com.jaoow.helmetstore.usecase.profit.GetMonthlyProfitUseCase;
import com.jaoow.helmetstore.usecase.profit.GetProfitSummaryUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.time.YearMonth;
import java.util.List;

/**
 * Financial Report Service - Orchestrates financial reporting operations
 * 
 * This service acts as a facade for profit and cash flow reporting,
 * delegating business logic to specific use cases following the architecture rule
 * that controllers should not access use cases directly.
 */
@Service
@RequiredArgsConstructor
public class FinancialReportService {

    // Profit Use Cases
    private final GetProfitSummaryUseCase getProfitSummaryUseCase;
    private final GetMonthlyProfitBreakdownUseCase getMonthlyProfitBreakdownUseCase;
    private final GetMonthlyProfitUseCase getMonthlyProfitUseCase;

    // Cash Flow Use Cases
    private final GetCashFlowSummaryUseCase getCashFlowSummaryUseCase;
    private final GetMonthlyCashFlowBreakdownUseCase getMonthlyCashFlowBreakdownUseCase;
    private final GetMonthlyCashFlowUseCase getMonthlyCashFlowUseCase;

    // ============================================================================
    // PROFIT OPERATIONS
    // ============================================================================

    public ProfitSummaryDTO getProfitSummary(Principal principal) {
        return getProfitSummaryUseCase.execute(principal);
    }

    public List<MonthlyProfitDTO> getMonthlyProfitBreakdown(Principal principal) {
        return getMonthlyProfitBreakdownUseCase.execute(principal);
    }

    public MonthlyProfitDTO getMonthlyProfit(Principal principal, YearMonth yearMonth) {
        return getMonthlyProfitUseCase.execute(principal, yearMonth);
    }

    // ============================================================================
    // CASH FLOW OPERATIONS
    // ============================================================================

    public CashFlowSummaryDTO getCashFlowSummary(Principal principal) {
        return getCashFlowSummaryUseCase.execute(principal);
    }

    public List<MonthlyCashFlowDTO> getMonthlyCashFlowBreakdown(String userEmail) {
        return getMonthlyCashFlowBreakdownUseCase.execute(userEmail);
    }

    public MonthlyCashFlowDTO getMonthlyCashFlow(String userEmail, YearMonth yearMonth) {
        return getMonthlyCashFlowUseCase.execute(userEmail, yearMonth);
    }
}
