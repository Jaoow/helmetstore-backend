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

}
