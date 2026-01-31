package com.jaoow.helmetstore.model.sale;

import com.jaoow.helmetstore.model.ProductVariant;
import com.jaoow.helmetstore.model.Sale;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "sale_item", indexes = {
    @Index(name = "idx_sale_item_sale_id", columnList = "sale_id"),
    @Index(name = "idx_sale_item_variant_id", columnList = "product_variant_id")
})
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_variant_id")
    private ProductVariant productVariant;

    @NotNull
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

    /**
     * Product average cost at time of sale (COGS snapshot).
     */
    @Column(name = "cost_basis_at_sale", precision = 10, scale = 2)
    private BigDecimal costBasisAtSale;

    /**
     * Flag indicating if the item has been cancelled.
     */
    @Column(name = "is_cancelled", nullable = false)
    @Builder.Default
    private Boolean isCancelled = false;

    /**
     * Cancelled quantity (for partial item cancellations).
     */
    @Column(name = "cancelled_quantity")
    private Integer cancelledQuantity;
}

