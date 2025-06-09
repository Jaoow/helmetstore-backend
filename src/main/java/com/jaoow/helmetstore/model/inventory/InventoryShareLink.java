package com.jaoow.helmetstore.model.inventory;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryShareLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String storeName;

    @Column(unique = true, nullable = false)
    private String token;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private boolean showStockQuantity;

    @Column(nullable = false)
    private boolean showPrice;

    @Column(nullable = false)
    private boolean showWhatsappButton;

    @Column
    private String whatsappNumber;

    @Column
    private String whatsappMessage;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @OneToOne
    @JoinColumn(name = "inventory_id", nullable = false, unique = true)
    private Inventory inventory;
}
