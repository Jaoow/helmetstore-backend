package com.jaoow.helmetstore.nf.dto;

import lombok.*;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemDTO implements Serializable {
    private Long id;
    private String sku;
    private String size;
    private int quantity = 0;
    private OrderProductDto product;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class OrderProductDto implements Serializable {
        private Long id;
        private String model;
        private String color;
        private String imgUrl;
    }
}