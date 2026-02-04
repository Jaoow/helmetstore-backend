package com.jaoow.helmetstore.dto.sale;

import com.jaoow.helmetstore.model.balance.PaymentMethod;
import com.jaoow.helmetstore.model.sale.ExchangeReason;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for product exchange operation
 *
 * This DTO captures all information needed to perform a product exchange:
 * - Items to return from the original sale
 * - New items to be sold
 * - Reason for the exchange
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductExchangeRequestDTO {

    /**
     * ID of the original sale
     */
    @NotNull(message = "O ID da venda original é obrigatório")
    @Positive(message = "O ID da venda original deve ser positivo")
    private Long originalSaleId;

    /**
     * Reason for the exchange
     */
    @NotNull(message = "O motivo da troca é obrigatório")
    private ExchangeReason reason;

    /**
     * Additional notes about the exchange
     */
    private String notes;

    /**
     * Items to be returned/cancelled from the original sale
     */
    @NotEmpty(message = "Deve haver pelo menos um item para devolução")
    @Valid
    private List<ItemToReturnDTO> itemsToReturn;

    /**
     * New items for the exchange (new sale)
     */
    @NotEmpty(message = "Deve haver pelo menos um item para a nova venda")
    @Valid
    private List<NewItemDTO> newItems;

    /**
     * Payment information for the new sale
     * Can be empty when there's no additional payment required (equal value exchange)
     */
    @Valid
    private List<SalePaymentCreateDTO> newSalePayments;

    /**
     * Payment method for refund (when applicable)
     * Required when new sale is cheaper or equal to returned amount
     */
    private PaymentMethod refundPaymentMethod;

    /**
     * DTO for items to be returned from the original sale
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemToReturnDTO {

        /**
         * ID of the sale item to be returned/cancelled
         */
        @NotNull(message = "O ID do item é obrigatório")
        @Positive(message = "O ID do item deve ser positivo")
        private Long saleItemId;

        /**
         * Quantity to return (must be <= quantity in the original sale item)
         */
        @NotNull(message = "A quantidade a devolver é obrigatória")
        @Positive(message = "A quantidade a devolver deve ser positiva")
        private Integer quantityToReturn;
    }

    /**
     * DTO for new items in the exchange
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NewItemDTO {

        /**
         * ID of the product variant for the new item
         */
        @NotNull(message = "O ID da variante é obrigatório")
        @Positive(message = "O ID da variante deve ser positivo")
        private Long variantId;

        /**
         * Quantity of the new item
         */
        @NotNull(message = "A quantidade é obrigatória")
        @Positive(message = "A quantidade deve ser positiva")
        private Integer quantity;

        /**
         * Unit price for the new item
         */
        @NotNull(message = "O preço unitário é obrigatório")
        @Positive(message = "O preço unitário deve ser positivo")
        private java.math.BigDecimal unitPrice;
    }
}
