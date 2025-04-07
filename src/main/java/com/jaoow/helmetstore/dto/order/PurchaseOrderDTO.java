package com.jaoow.helmetstore.dto.order;

import com.jaoow.helmetstore.model.PurchaseOrderStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrderDTO {

    private Long id;
    private String orderNumber;
    private LocalDate date = LocalDate.now();
    private PurchaseOrderStatus status;
    private List<PurchaseOrderItemDTO> items;
    private BigDecimal totalAmount;
}
