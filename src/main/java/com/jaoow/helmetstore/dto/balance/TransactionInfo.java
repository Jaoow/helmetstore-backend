package com.jaoow.helmetstore.dto.balance;

import com.jaoow.helmetstore.model.balance.PaymentMethod;
import com.jaoow.helmetstore.model.balance.TransactionDetail;
import com.jaoow.helmetstore.model.balance.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Projection for {@link com.jaoow.helmetstore.model.balance.Transaction}
 */
public interface TransactionInfo {
    Long getId();

    LocalDateTime getDate();

    String getDescription();

    BigDecimal getAmount();

    PaymentMethod getPaymentMethod();

    TransactionType getType();

    TransactionDetail getDetail();

    String getReference();
}