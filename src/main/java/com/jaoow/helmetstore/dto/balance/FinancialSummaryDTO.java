package com.jaoow.helmetstore.dto.balance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Simple financial summary with profit and cash flow
 * Used for quick overview of financial health
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialSummaryDTO {
    private BigDecimal profit;
    private BigDecimal cashFlow;
}
