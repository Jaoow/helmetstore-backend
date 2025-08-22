package com.jaoow.helmetstore.model.sale;

import com.jaoow.helmetstore.model.ProductVariant;
import com.jaoow.helmetstore.model.Sale;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "sale_item")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id")
    private Sale sale;

    @ManyToOne(optional = false)
    @JoinColumn(name = "product_variant_id")
    private ProductVariant productVariant;

    @NotNull
    @Min(1)
    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "unit_profit", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitProfit;

    @Column(name = "total_item_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalItemPrice;

    @Column(name = "total_item_profit", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalItemProfit;
}

