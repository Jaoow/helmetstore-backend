package com.jaoow.helmetstore.repository;

import com.jaoow.helmetstore.model.sale.SaleItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SaleItemRepository extends JpaRepository<SaleItem, Long> {
}
