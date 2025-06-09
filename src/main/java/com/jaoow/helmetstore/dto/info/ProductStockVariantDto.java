package com.jaoow.helmetstore.dto.info;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
public class ProductStockVariantDto extends BaseProductStockVariantDto {
    private int incomingStock = 0;
    private int futureStock = 0;
}
