package com.jaoow.helmetstore.dto.balance;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReinvestmentResponseDTO {

    private Long transactionId;
    private BigDecimal reinvestedAmount;
    private BigDecimal percentageOfProfit;
    private String description;
    private LocalDateTime reinvestmentDate;
    private BigDecimal remainingProfit;
    private String message;
}

