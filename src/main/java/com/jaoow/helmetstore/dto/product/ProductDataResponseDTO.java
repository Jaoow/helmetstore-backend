package com.jaoow.helmetstore.dto.product;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductDataResponseDTO {
    private Long id;
    private Long productId;
    private Long inventoryId;
    private BigDecimal salePrice;

}
