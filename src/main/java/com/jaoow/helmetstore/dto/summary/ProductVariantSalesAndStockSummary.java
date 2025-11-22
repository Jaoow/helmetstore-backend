package com.jaoow.helmetstore.dto.summary;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public interface ProductVariantSalesAndStockSummary {

    Long getProductId();

    String getModel();

    String getColor();

    String getImgUrl();

    String getCategoryName();

    BigDecimal getSalePrice();

    LocalDate getLastPurchaseDate();

    BigDecimal getAverageCost();

    Long getVariantId();

    String getSku();

    String getSize();

    Integer getCurrentStock();

    Integer getTotalPurchased();

    Integer getIncomingStock();

    Integer getFutureStock();

    LocalDateTime getLastSaleDate();

    Integer getTotalSold();

    BigDecimal getTotalStockValue();

    BigDecimal getTotalRevenue();

    BigDecimal getTotalProfit();

    BigDecimal getProfitMargin();
}
