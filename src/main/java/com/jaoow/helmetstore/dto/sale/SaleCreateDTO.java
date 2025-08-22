package com.jaoow.helmetstore.dto.sale;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleCreateDTO {

    @NotNull(message = "Data da venda é obrigatória")
    private LocalDateTime date;

    @NotEmpty(message = "Deve haver pelo menos um item na venda")
    @Valid
    private List<SaleItemCreateDTO> items;

    @NotEmpty(message = "Deve haver pelo menos um método de pagamento")
    @Valid
    private List<SalePaymentCreateDTO> payments;
}
