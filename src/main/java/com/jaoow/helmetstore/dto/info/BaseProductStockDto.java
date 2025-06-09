package com.jaoow.helmetstore.dto.info;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BaseProductStockDto<V extends BaseProductStockVariantDto> implements Serializable {
    private Long productId;
    private String model;
    private String color;
    private String imgUrl;
    private BigDecimal salePrice;
    private List<V> variants = new ArrayList<>();
}

