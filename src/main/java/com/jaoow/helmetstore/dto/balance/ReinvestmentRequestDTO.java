package com.jaoow.helmetstore.dto.balance;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMax;
import lombok.*;

import java.math.BigDecimal;

/**
 * @deprecated This DTO is deprecated as of version 2.0.3 and will be removed in future versions.
 * Reinvestment feature has been removed from the system.
 */
@Deprecated(since = "2.0.3", forRemoval = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReinvestmentRequestDTO {

    @NotNull(message = "Tipo de reinvestimento é obrigatório")
    private ReinvestmentType reinvestmentType;

    @NotNull(message = "Valor é obrigatório")
    @DecimalMin(value = "0.01", message = "Valor deve ser maior que zero")
    private BigDecimal value;

    @NotNull(message = "Mês de referência é obrigatório")
    private String referenceMonth; // formato: "yyyy-MM"

    public enum ReinvestmentType {
        PERCENTAGE,
        FIXED_AMOUNT
    }
}

