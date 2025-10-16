package com.jaoow.helmetstore.dto.item;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class VariantPriceUpdateDTO {

    @NotNull(message = "Variant ID cannot be null")
    private Long variantId;

    @NotNull(message = "Last purchase price cannot be null")
    @DecimalMin(value = "0.0", inclusive = false, message = "Last purchase price must be greater than 0")
    private BigDecimal lastPurchasePrice;
}