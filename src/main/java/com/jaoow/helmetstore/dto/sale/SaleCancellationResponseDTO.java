package com.jaoow.helmetstore.dto.sale;

import com.jaoow.helmetstore.model.balance.PaymentMethod;
import com.jaoow.helmetstore.model.sale.CancellationReason;
import com.jaoow.helmetstore.model.sale.SaleStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO de resposta após cancelamento da venda
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleCancellationResponseDTO {

    private Long saleId;
    private SaleStatus status;
    private LocalDateTime cancelledAt;
    private String cancelledBy;
    private CancellationReason cancellationReason;
    private String cancellationNotes;
    private Boolean hasRefund;
    private BigDecimal refundAmount;
    private PaymentMethod refundPaymentMethod;
    private Long refundTransactionId;
    private String message;
}
