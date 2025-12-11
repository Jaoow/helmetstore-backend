package com.jaoow.helmetstore.dto.balance;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @deprecated This DTO is deprecated as of version 2.0.3 and will be removed in future versions.
 * Reinvestment feature has been removed from the system.
 */
@Deprecated(since = "2.0.3", forRemoval = true)
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

