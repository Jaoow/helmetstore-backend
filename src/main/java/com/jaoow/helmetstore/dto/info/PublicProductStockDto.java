package com.jaoow.helmetstore.dto.info;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class PublicProductStockDto implements Serializable {
    private Long productId;
    private String model;
    private String color;
    private String imgUrl;
    private List<PublicProductStockVariantDto> variants = new ArrayList<>();

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PublicProductStockVariantDto implements Serializable {
        private Long variantId;
        private String sku;
        private String size;
        private Integer currentStock = 0;
    }
}