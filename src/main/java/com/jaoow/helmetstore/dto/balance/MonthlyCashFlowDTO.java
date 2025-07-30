package com.jaoow.helmetstore.dto.balance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

/**
 * DTO for monthly cash flow summary
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyCashFlowDTO {
    
    private YearMonth month;
    
    private BigDecimal bankAccountBalance;
    private BigDecimal cashAccountBalance;
    private BigDecimal totalBalance;
    
    private BigDecimal monthlyIncome;
    private BigDecimal monthlyExpense;
    private BigDecimal monthlyCashFlow;
    
    private List<TransactionInfo> transactions;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccountBalance {
        private BigDecimal bankBalance;
        private BigDecimal cashBalance;
        private BigDecimal totalBalance;
    }
} 