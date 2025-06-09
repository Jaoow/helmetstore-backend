package com.jaoow.helmetstore.model.inventory;

import com.jaoow.helmetstore.model.Product;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductData {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private BigDecimal salePrice;

    @ManyToOne(optional = false)
    private Product product;

    @ManyToOne(optional = false)
    private Inventory inventory;
}
