package com.jaoow.helmetstore.dto.sale;

import com.jaoow.helmetstore.model.balance.PaymentMethod;
import com.jaoow.helmetstore.model.sale.CancellationReason;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO para solicitar o cancelamento de uma venda
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleCancellationRequestDTO {

    /**
     * Motivo do cancelamento
     */
    @NotNull(message = "O motivo do cancelamento é obrigatório")
    private CancellationReason reason;

    /**
     * Observações adicionais sobre o cancelamento
     */
    private String notes;

    /**
     * Se verdadeiro, cancela a venda inteira. Se falso, cancela apenas os itens especificados
     */
    @Builder.Default
    private Boolean cancelEntireSale = true;

    /**
     * Lista de itens a serem cancelados (apenas para cancelamento parcial)
     * Cada elemento contém: { itemId, quantityToCancel }
     */
    private List<ItemCancellationDTO> itemsToCancel;

    /**
     * Se verdadeiro, gera estorno/reembolso
     */
    @Builder.Default
    private Boolean generateRefund = false;

    /**
     * Valor do estorno (obrigatório se generateRefund = true)
     */
    @DecimalMin(value = "0.0", inclusive = false, message = "O valor do estorno deve ser maior que zero")
    private BigDecimal refundAmount;

    /**
     * Método de reembolso (obrigatório se generateRefund = true)
     */
    private PaymentMethod refundPaymentMethod;

    /**
     * Indica se este cancelamento faz parte de uma troca
     * Se true, a venda será marcada como EXCHANGED ao invés de CANCELLED
     */
    @Builder.Default
    private Boolean isPartOfExchange = false;

    /**
     * Indica se este cancelamento tem impacto financeiro real.
     *
     * - true (padrão): Tem impacto financeiro - gera REFUND e COGS_REVERSAL
     * - false: Apenas operacional (parte de troca) - só devolve estoque
     *
     * ⚠️ REGRA: Em trocas sem reembolso real, deve ser false.
     * O dinheiro já está contabilizado na venda original.
     */
    @Builder.Default
    private Boolean hasFinancialImpact = true;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ItemCancellationDTO {
        /**
         * ID do item da venda
         */
        @NotNull(message = "O ID do item é obrigatório")
        private Long itemId;

        /**
         * Quantidade a cancelar
         */
        @NotNull(message = "A quantidade é obrigatória")
        @DecimalMin(value = "1", message = "A quantidade deve ser pelo menos 1")
        private Integer quantityToCancel;
    }
}
