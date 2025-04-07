package com.jaoow.helmetstore.dto.sale;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleResponseDTO {

    private Long id;
    private LocalDate date;
    private Long productVariantId;
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalProfit;

}
