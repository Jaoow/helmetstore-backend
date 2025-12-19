package com.jaoow.helmetstore.model.inventory;

import com.jaoow.helmetstore.model.PurchaseOrder;
import com.jaoow.helmetstore.model.Sale;
import com.jaoow.helmetstore.model.user.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Inventory {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(nullable = false)
    private Long id;

    @OneToMany(mappedBy = "inventory", fetch = FetchType.LAZY)
    private Set<InventoryItem> items;

    @OneToMany(mappedBy = "inventory", fetch = FetchType.LAZY)
    private Set<Sale> sales;

    @OneToMany(mappedBy = "inventory", fetch = FetchType.LAZY)
    private Set<PurchaseOrder> orders;

    @OneToOne(mappedBy = "inventory", optional = false, fetch = FetchType.LAZY)
    private User user;

}
