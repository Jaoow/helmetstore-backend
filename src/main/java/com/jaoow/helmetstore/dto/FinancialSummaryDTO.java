package com.jaoow.helmetstore.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FinancialSummaryDTO {
    private BigDecimal totalRevenue;
    private BigDecimal totalProfit;
}
