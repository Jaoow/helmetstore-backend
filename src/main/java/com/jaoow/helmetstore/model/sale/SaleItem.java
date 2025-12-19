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

    @ManyToOne(optional = false)
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
     * Snapshot of the product's average cost at the exact moment this sale occurred.
     * <p>
     * Critical for historical accuracy: If we buy expensive stock tomorrow, it should
     * NOT retroactively change the profit calculation of items sold today.
     * <p>
     * This field freezes the cost basis, enabling accurate Cost of Goods Sold (COGS)
     * tracking in the double-entry ledger system.
     * <p>
     * Calculation: costBasisAtSale = inventoryItem.averageCost (at moment of sale)
     * <p>
     * Example:
     * - Today: averageCost = $100, we sell 1 unit for $150 → costBasisAtSale = $100
     * - Tomorrow: We buy stock at $200/unit, averageCost becomes $150
     * - Result: Today's sale still shows $50 profit (not retroactively reduced)
     */
    @Column(name = "cost_basis_at_sale", precision = 10, scale = 2)
    private BigDecimal costBasisAtSale;
}

