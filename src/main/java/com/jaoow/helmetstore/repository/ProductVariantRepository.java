package com.jaoow.helmetstore.repository;

import com.jaoow.helmetstore.model.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

  Optional<ProductVariant> findBySku(String sku);

}