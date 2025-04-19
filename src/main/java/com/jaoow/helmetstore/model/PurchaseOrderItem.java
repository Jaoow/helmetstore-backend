package com.jaoow.helmetstore.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private ProductVariant productVariant;

    private int quantity;

    @Column(precision = 10, scale = 2)
    private BigDecimal purchasePrice; // Preço de compra do produto neste pedido

    @ManyToOne(optional = false)
    private PurchaseOrder purchaseOrder;
}
