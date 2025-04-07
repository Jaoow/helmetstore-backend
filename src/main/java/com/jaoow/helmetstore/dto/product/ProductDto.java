package com.jaoow.helmetstore.dto.product;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProductDto implements Serializable {
    private Long id;
    private String model;
    private String color;
    private String imgUrl;
    private BigDecimal lastPurchasePrice;
    private LocalDate lastPurchaseDate;
    private List<ProductVariantDto> variants = new ArrayList<>();

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ProductVariantDto implements Serializable {
        private Long id;
        private String sku;
        private String size;
        private int quantity = 0;
    }
}