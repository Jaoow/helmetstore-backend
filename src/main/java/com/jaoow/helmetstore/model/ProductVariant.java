package com.jaoow.helmetstore.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVariant {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    private String sku;

    private String size;

    private int quantity = 0;

    @ManyToOne
    private Product product;

    @OneToMany(mappedBy = "productVariant", fetch = FetchType.LAZY)
    private List<Sale> sales = new ArrayList<>();

}
