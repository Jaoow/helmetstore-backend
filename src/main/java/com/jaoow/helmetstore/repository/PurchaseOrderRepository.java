package com.jaoow.helmetstore.repository;

import com.jaoow.helmetstore.model.PurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    @Query("""
            SELECT po FROM PurchaseOrder po
            JOIN FETCH po.items i
            JOIN FETCH i.productVariant pv
            JOIN FETCH pv.product pr
            ORDER BY po.date DESC, po.id DESC, pr.model, pr.color, pv.size
            """)
    List<PurchaseOrder> findAllWithItemsAndVariants();

    boolean existsByOrderNumber(String orderNumber);
}