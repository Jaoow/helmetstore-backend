package com.jaoow.helmetstore.dto.order;

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
public class PurchaseOrderItemDTO {

    private Long id;

    @NotNull(message = "Product variant id is required")
    private Long productVariantId;

    @Min(value = 1, message = "Quantity must be greater than 0")
    private int quantity;

    @DecimalMin(value = "0.0", message = "Purchase price must be greater than 0")
    private BigDecimal purchasePrice;
}
