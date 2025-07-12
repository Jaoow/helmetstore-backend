package com.jaoow.helmetstore.dto.balance;

import com.jaoow.helmetstore.model.balance.PaymentMethod;
import com.jaoow.helmetstore.model.balance.TransactionDetail;
import com.jaoow.helmetstore.model.balance.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO for {@link com.jaoow.helmetstore.model.balance.Transaction}
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionInfo {
    private Long id;
    private LocalDateTime date;
    private String description;
    private BigDecimal amount;
    private PaymentMethod paymentMethod;
    private TransactionType type;
    private TransactionDetail detail;
    private String reference;
}