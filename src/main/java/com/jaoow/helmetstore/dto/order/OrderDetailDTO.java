package com.jaoow.helmetstore.dto.order;

import com.jaoow.helmetstore.model.balance.PaymentMethod;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class OrderDetailDTO {
    private Long id;
    private String orderNumber;
    private BigDecimal totalAmount;
    private LocalDate date;
    private String status;
    private PaymentMethod paymentMethod;
    private List<OrderItemDTO> items;

    @Getter
    @Setter
    public static class OrderItemDTO {
        private Long id;
        private Long productVariantId;
        private BigDecimal purchasePrice;
        private int quantity;
    }
}
