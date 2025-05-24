package com.jaoow.helmetstore.dto.item;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class VariantStockUpdateDTO {

    @NotNull(message = "Variant ID cannot be null")
    private Long variantId;

    @Min(value = 0, message = "Stock must be greater than or equal to 0")
    private int stock;
}
