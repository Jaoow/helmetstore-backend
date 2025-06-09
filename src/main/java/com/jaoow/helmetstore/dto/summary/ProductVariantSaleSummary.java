package com.jaoow.helmetstore.dto.summary;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface ProductVariantSaleSummary {
    Long getProductId();

    String getModel();

    String getColor();

    String getImgUrl();

    Long getVariantId();

    String getSku();

    String getSize();

    BigDecimal getSalePrice();

    LocalDateTime getLastSaleDate();

    Integer getTotalSold();

    BigDecimal getTotalRevenue();

    BigDecimal getTotalProfit();

}
