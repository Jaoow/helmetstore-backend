package com.jaoow.helmetstore.dto.info;

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
public class ProductStockDto implements Serializable {
    private Long productId;
    private String model;
    private String color;
    private String imgUrl;
    private BigDecimal lastPurchasePrice;
    private LocalDate lastPurchaseDate;
    private List<ProductStockVariantDto> variants = new ArrayList<>();

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ProductStockVariantDto implements Serializable {
        private Long variantId;
        private String sku;
        private String size;
        private int currentStock = 0;
        private int incomingStock = 0;
        private int futureStock = 0;
    }
}