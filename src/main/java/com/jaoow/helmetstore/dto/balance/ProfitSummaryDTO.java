package com.jaoow.helmetstore.dto.balance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Comprehensive profit summary with monthly breakdown
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfitSummaryDTO {

    private BigDecimal totalBankBalance;
    private BigDecimal totalCashBalance;
    private BigDecimal totalBalance;

    private BigDecimal totalProfit;
    private BigDecimal totalNetProfit;
    private BigDecimal totalExpenseTransactions;

    private List<MonthlyProfitDTO> monthlyBreakdown;
}