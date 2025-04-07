package com.jaoow.helmetstore.dto.sale;

import com.jaoow.helmetstore.dto.reference.SimpleProductDTO;
import com.jaoow.helmetstore.dto.reference.SimpleProductVariantDTO;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleHistoryResponse {
    private List<SaleResponseDTO> sales;
    private List<SimpleProductDTO> products;
    private List<SimpleProductVariantDTO> productVariants;
}