package com.jaoow.helmetstore.repository;

import com.jaoow.helmetstore.model.Product;
import com.jaoow.helmetstore.model.inventory.Inventory;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @EntityGraph(attributePaths = { "variants", "category" })
    @Query("SELECT DISTINCT p FROM Product p WHERE p.inventory = :inventory ORDER BY p.model, p.color")
    List<Product> findAllByInventoryWithVariants(@Param("inventory") Inventory inventory);

    @EntityGraph(attributePaths = { "variants", "category" })
    Optional<Product> findByIdAndInventory(Long id, Inventory inventory);

    List<Product> findAllByInventory(Inventory inventory);

    boolean existsByIdAndInventory(Long id, Inventory inventory);

}
