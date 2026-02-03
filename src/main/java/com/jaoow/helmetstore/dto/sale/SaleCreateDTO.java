package com.jaoow.helmetstore.dto.sale;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

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

    /**
     * Flag indicando se esta venda é derivada de uma troca.
     * Quando true, NÃO gera transações financeiras (REVENUE/COGS),
     * pois representa reapontamento da venda original.
     *
     * ⚠️ ATENÇÃO - REGRA DE OURO:
     * Esta flag DEVE ser true SOMENTE quando a venda é criada por ExchangeProductUseCase.
     * NUNCA defina manualmente como true em outros contextos.
     *
     * Risco: Se definida incorretamente, pode causar:
     * - Falta de registro de transações financeiras
     * - Descasamento contábil
     * - Caixa incorreto
     *
     * Contextos válidos: APENAS dentro de ExchangeProductUseCase.buildNewSaleDTO()
     */
    @Builder.Default
    private Boolean isDerivedFromExchange = false;
}
