package com.jaoow.helmetstore.dto.product;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductDataUpsertDTO {
    @NotNull
    private Long productId;

    @NotNull
    @DecimalMin("0.0")
    private BigDecimal salePrice;
}
