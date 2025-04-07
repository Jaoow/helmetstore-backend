package com.jaoow.helmetstore.dto.sale;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleCreateDTO {

    @NotNull
    private LocalDate date;
    
    @NotNull
    private Long variantId;
    
    @Min(1)
    private int quantity;
    
    @DecimalMin("0.0")
    private BigDecimal unitPrice;
}
