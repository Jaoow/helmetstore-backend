package com.jaoow.helmetstore.dto.balance;

import com.jaoow.helmetstore.model.balance.AccountType;

import java.math.BigDecimal;
import java.util.List;

/**
 * Projection for {@link com.jaoow.helmetstore.model.balance.Account}
 */
public interface AccountInfo {
    Long getId();

    AccountType getType();

    BigDecimal getBalance();

    List<TransactionInfo> getTransactions();
}