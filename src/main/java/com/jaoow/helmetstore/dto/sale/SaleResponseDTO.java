package com.jaoow.helmetstore.dto.sale;

import com.jaoow.helmetstore.model.balance.PaymentMethod;
import com.jaoow.helmetstore.model.sale.CancellationReason;
import com.jaoow.helmetstore.model.sale.SaleStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleResponseDTO {

    private Long id;
    private LocalDateTime date;
    private List<SaleItemDTO> items;
    private List<SalePaymentDTO> payments;
    private BigDecimal totalAmount;
    private BigDecimal totalProfit;

    // Cancellation fields
    private SaleStatus status;
    private LocalDateTime cancelledAt;
    private String cancelledBy;
    private CancellationReason cancellationReason;
    private String cancellationNotes;

    // Refund fields
    private Boolean hasRefund;
    private BigDecimal refundAmount;
    private PaymentMethod refundPaymentMethod;

}
