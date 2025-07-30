package com.jaoow.helmetstore.dto.balance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

/**
 * DTO for monthly profit summary
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyProfitDTO {
    
    private YearMonth month;
    
    private BigDecimal bankAccountBalance;
    private BigDecimal cashAccountBalance;
    private BigDecimal totalBalance;
    
    private BigDecimal monthlyProfit;
    private BigDecimal accumulatedProfitAvailableForWithdrawal;
    private BigDecimal monthlyProfitDeductingTransactions;
    
    private List<TransactionInfo> profitDeductingTransactions;
} 