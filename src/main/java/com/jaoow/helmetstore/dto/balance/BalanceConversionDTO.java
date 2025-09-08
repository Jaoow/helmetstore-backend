package com.jaoow.helmetstore.dto.balance;

import com.jaoow.helmetstore.model.balance.AccountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BalanceConversionDTO {

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal amount;

    @NotNull
    private AccountType fromAccountType;

    @NotNull
    private AccountType toAccountType;
}
