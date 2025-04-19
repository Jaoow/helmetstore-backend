package com.jaoow.helmetstore.repository;

import com.jaoow.helmetstore.model.Product;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @EntityGraph(attributePaths = {"variants"})
    @Query("SELECT DISTINCT p FROM Product p ORDER BY p.model, p.color")
    List<Product> findAllWithVariants();

}
