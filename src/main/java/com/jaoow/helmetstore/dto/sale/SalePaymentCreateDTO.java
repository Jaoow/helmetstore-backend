package com.jaoow.helmetstore.dto.sale;

import com.jaoow.helmetstore.model.balance.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalePaymentCreateDTO {

    @NotNull
    private PaymentMethod paymentMethod;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal amount;
}
