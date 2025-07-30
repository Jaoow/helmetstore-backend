package com.jaoow.helmetstore.dto.balance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Comprehensive cash flow summary with monthly breakdown
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashFlowSummaryDTO {
    
    private BigDecimal totalBankBalance;
    private BigDecimal totalCashBalance;
    private BigDecimal totalBalance;
    
    private BigDecimal totalIncome;
    private BigDecimal totalExpense;
    private BigDecimal totalCashFlow;
    
    private List<MonthlyCashFlowDTO> monthlyBreakdown;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccountSummary {
        private BigDecimal bankBalance;
        private BigDecimal cashBalance;
        private BigDecimal totalBalance;
    }
} 