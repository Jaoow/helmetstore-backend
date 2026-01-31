package com.jaoow.helmetstore.repository;

import com.jaoow.helmetstore.model.ProductExchange;
import com.jaoow.helmetstore.model.Sale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductExchangeRepository extends JpaRepository<ProductExchange, Long> {

    /**
     * Find all exchanges for a specific original sale
     */
    List<ProductExchange> findByOriginalSale(Sale originalSale);

    /**
     * Find all exchanges for a specific new sale
     */
    List<ProductExchange> findByNewSale(Sale newSale);

    /**
     * Find all exchanges related to a sale (either as original or new sale)
     */
    @Query("SELECT pe FROM ProductExchange pe WHERE pe.originalSale.id = :saleId OR pe.newSale.id = :saleId")
    List<ProductExchange> findAllRelatedToSale(@Param("saleId") Long saleId);
}
