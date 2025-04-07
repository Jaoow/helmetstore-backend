package com.jaoow.helmetstore.repository;

import com.jaoow.helmetstore.dto.FinancialSummaryDTO;
import com.jaoow.helmetstore.model.Sale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SaleRepository extends JpaRepository<Sale, Long> {

    @Query("""
            SELECT new com.jaoow.helmetstore.dto.FinancialSummaryDTO(
                (SELECT COALESCE(SUM(s.unitPrice * s.quantity), 0) FROM Sale s),
                (SELECT COALESCE(SUM(s.totalProfit), 0)
                 FROM Sale s JOIN s.productVariant p)
            )
            """)
    Optional<FinancialSummaryDTO> getFinancialSummary();

    @Query("""
            SELECT s FROM Sale s
            JOIN FETCH s.productVariant pv
            JOIN FETCH pv.product
            ORDER BY s.date DESC, s.id DESC
            """)
    List<Sale> findAllWithProductVariantsAndProducts();

}