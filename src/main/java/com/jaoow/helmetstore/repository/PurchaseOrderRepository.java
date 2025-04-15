package com.jaoow.helmetstore.repository;

import com.jaoow.helmetstore.model.PurchaseOrder;
import com.jaoow.helmetstore.model.inventory.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    @Query("""
            SELECT po FROM PurchaseOrder po
            JOIN FETCH po.items i
            JOIN FETCH i.productVariant pv
            JOIN FETCH pv.product pr
            WHERE po.inventory = :inventory
            ORDER BY po.date DESC, po.id DESC, pr.model, pr.color, pv.size
            """)
    List<PurchaseOrder> findAllByInventoryWithItemsAndVariants();

    @Query("""
            SELECT po FROM PurchaseOrder po
            JOIN FETCH po.items i
            JOIN FETCH i.productVariant pv
            JOIN FETCH pv.product pr
            WHERE po.inventory = :inventory
            ORDER BY po.date DESC, po.id DESC, pr.model, pr.color, pv.size
            """)
    List<PurchaseOrder> findAllByInventoryWithItemsAndVariants(@Param("inventory") Inventory inventory);

    Optional<PurchaseOrder> findByIdAndInventory(Long id, Inventory inventory);

    boolean existsByInventoryAndOrderNumber(Inventory inventory, String orderNumber);
}