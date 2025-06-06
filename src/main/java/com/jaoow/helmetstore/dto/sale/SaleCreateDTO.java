package com.jaoow.helmetstore.dto.sale;

import com.jaoow.helmetstore.model.balance.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleCreateDTO {

    @NotNull
    private LocalDateTime date;

    @NotNull
    private Long variantId;

    @NotNull
    private PaymentMethod paymentMethod;

    @Min(1)
    private int quantity;

    @DecimalMin("0.0")
    private BigDecimal unitPrice;
}
