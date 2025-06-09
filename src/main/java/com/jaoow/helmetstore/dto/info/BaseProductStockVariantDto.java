package com.jaoow.helmetstore.dto.info;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BaseProductStockVariantDto implements Serializable {
    private Long variantId;
    private String sku;
    private String size;
    private Integer currentStock = 0;
}
