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

    @OneToMany(mappedBy = "inventory")
    private Set<InventoryItem> items;

    @OneToMany(mappedBy = "inventory")
    private Set<Sale> sales;

    @OneToMany(mappedBy = "inventory")
    private Set<PurchaseOrder> orders;

    @OneToOne(mappedBy = "inventory", optional = false)
    private User user;

}
