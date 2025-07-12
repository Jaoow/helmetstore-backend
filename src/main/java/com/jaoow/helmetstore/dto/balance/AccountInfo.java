package com.jaoow.helmetstore.dto.balance;

import com.jaoow.helmetstore.model.balance.AccountType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO for {@link com.jaoow.helmetstore.model.balance.Account}
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountInfo {
    private Long id;
    private AccountType type;
    private BigDecimal balance;
    private List<TransactionInfo> transactions;
}