package com.jaoow.helmetstore.dto.summary;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface ProductVariantSalesAndStockSummary {

    Long getProductId();

    String getModel();

    String getColor();

    String getImgUrl();

    LocalDate getLastPurchaseDate();

    BigDecimal getLastPurchasePrice();

    Long getVariantId();

    String getSku();

    String getSize();

    Integer getCurrentStock();

    Integer getTotalPurchased();

    Integer getIncomingStock();

    Integer getFutureStock();

    LocalDate getLastSaleDate();

    Integer getTotalSold();

    BigDecimal getTotalRevenue();

    BigDecimal getTotalProfit();

    BigDecimal getProfitMargin();
}
