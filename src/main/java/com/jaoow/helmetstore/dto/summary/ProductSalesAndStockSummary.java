package com.jaoow.helmetstore.dto.summary;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface ProductSalesAndStockSummary {

    Long getProductId();

    String getModel();

    String getColor();

    String getImgUrl();

    String getCategoryName();

    BigDecimal getSalePrice();

    LocalDate getLastPurchaseDate();

    BigDecimal getAverageCost();

    Integer getTotalCurrentStock();

    Integer getTotalIncomingStock();

    Integer getTotalFutureStock();

    LocalDateTime getLastSaleDate();

    Integer getTotalSold();

    BigDecimal getTotalStockValue();

    BigDecimal getTotalRevenue();

    BigDecimal getTotalProfit();

    BigDecimal getProfitMargin();

    List<ProductVariantInfo> getVariants();
}

interface ProductVariantInfo {
    Long getVariantId();
    String getSku();
    String getSize();
    Integer getCurrentStock();
    Integer getIncomingStock();
    Integer getFutureStock();
    BigDecimal getAverageCost();
    LocalDate getLastPurchaseDate();
}