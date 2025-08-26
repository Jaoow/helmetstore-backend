package com.jaoow.helmetstore.repository;

import com.jaoow.helmetstore.model.inventory.Inventory;
import com.jaoow.helmetstore.model.inventory.InventoryCatalog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InventoryCatalogRepository extends JpaRepository<InventoryCatalog, Long> {

    boolean existsByToken(String token);

    boolean existsByInventory(Inventory inventory);

    Optional<InventoryCatalog> findByInventory(Inventory inventory);

    Optional<InventoryCatalog> findByTokenAndActiveTrue(String token);
}
