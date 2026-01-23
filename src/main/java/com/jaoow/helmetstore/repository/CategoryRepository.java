package com.jaoow.helmetstore.repository;

import com.jaoow.helmetstore.model.Category;
import com.jaoow.helmetstore.model.inventory.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    Optional<Category> findByNameAndInventory(String name, Inventory inventory);

    Optional<Category> findByIdAndInventory(Long id, Inventory inventory);

    boolean existsByNameAndInventory(String name, Inventory inventory);

    List<Category> findAllByInventory(Inventory inventory);
}