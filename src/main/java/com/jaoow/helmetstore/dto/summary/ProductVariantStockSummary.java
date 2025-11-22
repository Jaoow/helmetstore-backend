package com.jaoow.helmetstore.dto.summary;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface ProductVariantStockSummary {
    Long getProductId();

    String getModel();

    String getColor();

    String getImgUrl();

    String getCategoryName();

    BigDecimal getSalePrice();

    BigDecimal getAverageCost();

    LocalDate getLastPurchaseDate();

    Long getVariantId();

    String getSku();

    String getSize();

    int getCurrentStock();

    int getIncomingStock();

    int getFutureStock();
}
