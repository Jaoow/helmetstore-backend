package com.jaoow.helmetstore.dto.sale;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleItemDTO {
    private Long id;
    private Long productVariantId;
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal unitProfit;
    private BigDecimal totalItemPrice;
    private BigDecimal totalItemProfit;
}
