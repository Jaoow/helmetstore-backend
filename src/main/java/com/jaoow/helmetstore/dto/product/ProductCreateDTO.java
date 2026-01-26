package com.jaoow.helmetstore.dto.product;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductCreateDTO {

    @NotBlank(message = "Model is mandatory")
    private String model;

    @NotBlank(message = "Color is mandatory")
    private String color;

    @Pattern(regexp = "https?://.*", message = "Invalid URL")
    private String imgUrl;

    @DecimalMin("0.0")
    private BigDecimal salePrice;

    private String categoryName;

    @NotBlank(message = "Pelo menos uma variante é obrigatória")
    private List<ProductVariantDTO> variants;

    @Getter
    @Setter
    public static class ProductVariantDTO {

        @NotBlank(message = "SKU is mandatory")
        private String sku;

        @NotBlank(message = "Size is mandatory")
        private String size;
    }
}
