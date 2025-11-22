package com.jaoow.helmetstore.dto.info;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
public class ProductStockDto extends BaseProductStockDto<ProductStockVariantDto> {
    private BigDecimal averageCost;
    private LocalDate lastPurchaseDate;
}
