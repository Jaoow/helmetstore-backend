package com.jaoow.helmetstore.dto.reference;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SimpleProductVariantDTO {
    private Long id;
    private String size;
    private String sku;
    private Long productId;
}