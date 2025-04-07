package com.jaoow.helmetstore.dto.order;

import com.jaoow.helmetstore.model.PurchaseOrderStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrderUpdateDTO {

    @Min(value = 3, message = "Order number must have at least 3 characters")
    private String orderNumber;

    private LocalDate date;

    @NotNull
    private PurchaseOrderStatus status;

}
