package com.jaoow.helmetstore.dto.summary;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface ProductVariantSaleSummary {
    Long getProductId();

    String getModel();

    String getColor();

    String getImgUrl();

    Long getVariantId();

    String getSku();

    String getSize();

    LocalDate getLastSaleDate();

    Integer getTotalSold();

    BigDecimal getTotalRevenue();

    BigDecimal getTotalProfit();

}
