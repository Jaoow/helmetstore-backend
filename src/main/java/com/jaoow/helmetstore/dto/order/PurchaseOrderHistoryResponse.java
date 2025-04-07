package com.jaoow.helmetstore.dto.order;

import com.jaoow.helmetstore.dto.reference.SimpleProductDTO;
import com.jaoow.helmetstore.dto.reference.SimpleProductVariantDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class PurchaseOrderHistoryResponse {
    private List<OrderDetailDTO> orders;
    private List<SimpleProductDTO> products;
    private List<SimpleProductVariantDTO> productVariants;
}
