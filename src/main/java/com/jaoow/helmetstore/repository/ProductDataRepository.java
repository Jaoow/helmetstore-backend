package com.jaoow.helmetstore.repository;

import com.jaoow.helmetstore.model.Product;
import com.jaoow.helmetstore.model.inventory.Inventory;
import com.jaoow.helmetstore.model.inventory.ProductData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductDataRepository extends JpaRepository<ProductData, Long> {


    Optional<ProductData> findByInventoryAndProduct(Inventory inventory, Product product);
}