package com.jaoow.helmetstore.dto.sale;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleResponseDTO {

    private Long id;
    private LocalDateTime date;
    private List<SaleItemDTO> items;
    private BigDecimal totalAmount;
    private BigDecimal totalProfit;

    // Campos de compatibilidade com o modelo antigo
    @Deprecated
    private Long productVariantId;
    @Deprecated
    private int quantity;
    @Deprecated
    private BigDecimal unitPrice;

}
