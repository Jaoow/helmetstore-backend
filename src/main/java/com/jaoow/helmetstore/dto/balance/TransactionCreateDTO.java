package com.jaoow.helmetstore.dto.balance;

import com.jaoow.helmetstore.model.balance.PaymentMethod;
import com.jaoow.helmetstore.model.balance.TransactionDetail;
import com.jaoow.helmetstore.model.balance.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionCreateDTO {

    @NotNull
    private LocalDateTime date;

    @NotNull
    private TransactionType type;

    @NotNull
    private TransactionDetail detail;

    private String description;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal amount;

    @NotNull
    private PaymentMethod paymentMethod;

    private String reference;

}
