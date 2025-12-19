package com.jaoow.helmetstore.model.inventory;

import com.jaoow.helmetstore.model.ProductVariant;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(indexes = {
    @Index(name = "idx_inventory_item_variant", columnList = "productVariant_id"),
    @Index(name = "idx_inventory_item_inventory_variant", columnList = "inventory_id, productVariant_id", unique = true)
})
public class InventoryItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int quantity;

    private BigDecimal averageCost;

    private LocalDate lastPurchaseDate;

    @ManyToOne(optional = false)
    private ProductVariant productVariant;

    @ManyToOne(optional = false)
    private Inventory inventory;

}
