package com.jaoow.helmetstore.model;

import com.jaoow.helmetstore.model.inventory.Inventory;
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
public class Sale {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private LocalDate date;

    @ManyToOne(fetch = FetchType.LAZY)
    private ProductVariant productVariant;

    private int quantity;

    private BigDecimal unitPrice;

    private BigDecimal totalProfit;

    @ManyToOne(optional = false)
    private Inventory inventory;

}
