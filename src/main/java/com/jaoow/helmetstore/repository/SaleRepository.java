package com.jaoow.helmetstore.repository;

import com.jaoow.helmetstore.model.Sale;
import com.jaoow.helmetstore.model.inventory.Inventory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.QueryHint;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import java.util.List;
import java.util.Optional;

@Repository
public interface SaleRepository extends JpaRepository<Sale, Long> {

        Optional<Sale> findByIdAndInventory(Long id, Inventory inventory);

        @Query("""
                        SELECT COALESCE(SUM(s.totalProfit), 0)
                        FROM Sale s
                        WHERE s.inventory = :inventory
                        """)
        BigDecimal getTotalGrossProfit(@Param("inventory") Inventory inventory);

        // Versão otimizada com hint para queries read-only em grande volume
        @QueryHints(@QueryHint(name = "org.hibernate.readOnly", value = "true"))
        @Query("""
                        SELECT DISTINCT s FROM Sale s
                        LEFT JOIN FETCH s.items si
                        LEFT JOIN FETCH si.productVariant sipv
                        LEFT JOIN FETCH sipv.product
                        WHERE s.inventory = :inventory
                        ORDER BY s.date DESC, s.id DESC
                        """)
        List<Sale> findAllByInventoryWithProductVariantsAndProducts(@Param("inventory") Inventory inventory);

        // Versão paginada para consultas com muitos registros
        @QueryHints(@QueryHint(name = "org.hibernate.readOnly", value = "true"))
        @Query(value = """
                        SELECT DISTINCT s FROM Sale s
                        WHERE s.inventory = :inventory
                        ORDER BY s.date DESC, s.id DESC
                        """,
               countQuery = """
                        SELECT COUNT(DISTINCT s) FROM Sale s
                        WHERE s.inventory = :inventory
                        """)
        Page<Sale> findAllByInventoryPaginated(@Param("inventory") Inventory inventory, Pageable pageable);

        // Query otimizada para buscar por período (mais performática)
        @QueryHints(@QueryHint(name = "org.hibernate.readOnly", value = "true"))
        @Query("""
                        SELECT DISTINCT s FROM Sale s
                        LEFT JOIN FETCH s.items si
                        LEFT JOIN FETCH si.productVariant sipv
                        LEFT JOIN FETCH sipv.product
                        WHERE s.inventory = :inventory
                        AND s.date >= :startDate
                        AND s.date < :endDate
                        ORDER BY s.date DESC, s.id DESC
                        """)
        List<Sale> findByInventoryAndDateRange(
                        @Param("inventory") Inventory inventory,
                        @Param("startDate") LocalDateTime startDate,
                        @Param("endDate") LocalDateTime endDate);

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
                        @Param("endDate") LocalDateTime endDate);

}
