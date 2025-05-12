package com.jaoow.helmetstore.repository;

import com.jaoow.helmetstore.model.inventory.Inventory;
import com.jaoow.helmetstore.model.inventory.InventoryShareLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InventoryShareLinkRepository extends JpaRepository<InventoryShareLink, Long> {

    boolean existsByToken(String token);

    boolean existsByInventory(Inventory inventory);

    Optional<InventoryShareLink> findByInventory(Inventory inventory);

    Optional<InventoryShareLink> findByTokenAndActiveTrue(String token);
}
