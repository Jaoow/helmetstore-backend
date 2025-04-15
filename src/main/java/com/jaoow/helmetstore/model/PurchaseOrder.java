package com.jaoow.helmetstore.model;

import com.jaoow.helmetstore.model.inventory.Inventory;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String orderNumber;

    @Enumerated(EnumType.STRING)
    private PurchaseOrderStatus status = PurchaseOrderStatus.INVOICED;

    @Column(columnDefinition = "DATE")
    private LocalDate date;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "purchaseOrder")
    private List<PurchaseOrderItem> items;

    @Column(precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @ManyToOne
    private Inventory inventory;
}
