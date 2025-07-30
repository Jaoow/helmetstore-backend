package com.jaoow.helmetstore.repository;

import com.jaoow.helmetstore.dto.FinancialSummaryDTO;
import com.jaoow.helmetstore.model.Sale;
import com.jaoow.helmetstore.model.inventory.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import java.util.List;
import java.util.Optional;

@Repository
public interface SaleRepository extends JpaRepository<Sale, Long> {

    Optional<Sale> findByIdAndInventory(Long id, Inventory inventory);

    @Query("""
            SELECT new com.jaoow.helmetstore.dto.FinancialSummaryDTO(
                (SELECT COALESCE(SUM(s.unitPrice * s.quantity), 0) FROM Sale s WHERE s.inventory = :inventory),
                (SELECT COALESCE(SUM(s.totalProfit), 0) FROM Sale s WHERE s.inventory = :inventory )
            )
            """)
    Optional<FinancialSummaryDTO> getFinancialSummary(@Param("inventory") Inventory inventory);

    @Query("""
            SELECT s FROM Sale s
            JOIN FETCH s.productVariant pv
            JOIN FETCH pv.product
            WHERE s.inventory = :inventory
            ORDER BY s.date DESC, s.id DESC
            """)
    List<Sale> findAllByInventoryWithProductVariantsAndProducts(@Param("inventory") Inventory inventory);

    @Query("""
            SELECT COALESCE(SUM(s.totalProfit), 0)
            FROM Sale s
            WHERE s.inventory = :inventory
            AND s.date >= :startDate
            AND s.date < :endDate
            """)
    BigDecimal getTotalProfitByDateRange(
            @Param("inventory") Inventory inventory,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );


}