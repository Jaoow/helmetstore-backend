package com.jaoow.helmetstore.dto.sale;

import com.jaoow.helmetstore.dto.reference.SimpleProductDTO;
import com.jaoow.helmetstore.dto.reference.SimpleProductVariantDTO;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleDetailDTO {
    private Long id;
    private LocalDateTime date;
    private List<SaleItemDTO> items;
    private BigDecimal totalAmount;
    private BigDecimal totalProfit;
    private List<SalePaymentDTO> payments;

    private List<SimpleProductDTO> products;
    private List<SimpleProductVariantDTO> productVariants;
}
