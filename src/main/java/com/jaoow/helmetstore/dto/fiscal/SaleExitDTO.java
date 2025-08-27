package com.jaoow.helmetstore.dto.fiscal;

import com.jaoow.helmetstore.model.balance.PaymentMethod;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO para saída (venda) no relatório fiscal
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleExitDTO {
    private LocalDate date;
    private Long saleId;
    private String customerName;
    private String customerPhone;
    private BigDecimal totalAmount;
    private PaymentMethod paymentMethod;
}