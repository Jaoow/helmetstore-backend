package com.jaoow.helmetstore.dto.sale;

import com.jaoow.helmetstore.model.balance.PaymentMethod;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalePaymentDTO {
    private PaymentMethod paymentMethod;
    private BigDecimal amount;
}
