package com.jaoow.helmetstore.dto.sale;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleItemCreateDTO {

    @NotNull(message = "ID da variante do produto é obrigatório")
    private Long variantId;

    @NotNull(message = "Quantidade é obrigatória")
    @Min(value = 1, message = "quantity: deve ser maior que ou igual à 1")
    private Integer quantity;

    @NotNull(message = "Preço unitário é obrigatório")
    @DecimalMin(value = "0.0", inclusive = false, message = "Preço unitário deve ser maior que zero")
    private BigDecimal unitPrice;
}
