package com.jaoow.helmetstore.repository;

import com.jaoow.helmetstore.dto.summary.ProductVariantSaleSummary;
import com.jaoow.helmetstore.dto.summary.ProductVariantSalesAndStockSummary;
import com.jaoow.helmetstore.dto.summary.ProductVariantStockSummary;
import com.jaoow.helmetstore.model.ProductVariant;
import com.jaoow.helmetstore.model.PurchaseOrderStatus;
import com.jaoow.helmetstore.model.inventory.Inventory;
import com.jaoow.helmetstore.model.inventory.InventoryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {

    @Query("""
            WITH SalesSummary AS (
                SELECT
                    s.productVariant.id AS variantId,
                    MAX(s.date) AS lastSaleDate,
                    SUM(s.quantity) AS totalSold,
                    SUM(s.unitPrice * s.quantity) AS totalRevenue,
                    SUM(s.totalProfit) AS totalProfit
                FROM Sale s
                WHERE s.inventory = :inventory
                GROUP BY s.productVariant.id
            ),
            StockSummary AS (
                SELECT
                    ii.productVariant.id AS variantId,
                    COALESCE(SUM(CASE WHEN po.status NOT IN (:excludedStatuses) THEN poi.quantity ELSE 0 END), 0) AS incomingStock
                FROM InventoryItem ii
                LEFT JOIN PurchaseOrderItem poi ON poi.productVariant.id = ii.productVariant.id
                LEFT JOIN poi.purchaseOrder po ON po.inventory = :inventory
                WHERE ii.inventory = :inventory
                GROUP BY ii.productVariant.id, ii.quantity
            )
            SELECT
                p.id AS productId,
                p.model AS model,
                p.color AS color,
                p.imgUrl AS imgUrl,
                ii.lastPurchaseDate AS lastPurchaseDate,
                COALESCE(ii.lastPurchasePrice, 0) AS lastPurchasePrice,
                pv.id AS variantId,
                pv.sku AS sku,
                pv.size AS size,
                ii.quantity AS currentStock,
                COALESCE(SUM(poi.quantity), 0) AS totalPurchased,
                COALESCE(s.incomingStock, 0) AS incomingStock,
                COALESCE(ss.lastSaleDate, NULL) AS lastSaleDate,
                COALESCE(ss.totalSold, 0) AS totalSold,
                COALESCE(ss.totalRevenue, 0) AS totalRevenue,
                COALESCE(ss.totalProfit, 0) AS totalProfit,
                ii.quantity + s.incomingStock AS futureStock,
                ii.quantity * COALESCE(ii.lastPurchasePrice, 0) AS totalStockValue,
                CASE
                    WHEN COALESCE(ss.totalRevenue, 0) > 0 THEN
                        (COALESCE(ss.totalProfit, 0) / COALESCE(ss.totalRevenue, 0)) * 100
                    ELSE 0
                END AS profitMargin
            FROM InventoryItem ii
            JOIN ProductVariant pv ON pv.id = ii.productVariant.id
            JOIN Product p ON p.id = pv.product.id
            LEFT JOIN PurchaseOrderItem poi ON poi.productVariant.id = pv.id
            LEFT JOIN poi.purchaseOrder po ON po.inventory = :inventory
            LEFT JOIN SalesSummary ss ON ss.variantId = pv.id
            LEFT JOIN StockSummary s ON s.variantId = pv.id
            WHERE ii.inventory = :inventory
            GROUP BY
                p.id, pv.id, ii.quantity, ii.lastPurchaseDate, ii.lastPurchasePrice, ss.lastSaleDate, ss.totalSold, ss.totalRevenue, ss.totalProfit, s.incomingStock
            ORDER BY p.model ASC, p.color ASC, pv.size
            """)
    List<ProductVariantSalesAndStockSummary> findAllWithSalesAndPurchaseDataByInventory(
            @Param("excludedStatuses") List<PurchaseOrderStatus> excludedStatuses,
            @Param("inventory") Inventory inventory
    );

    @Query("""
                WITH IncomingStock AS (
                    SELECT
                        poi.productVariant.id AS variantId,
                        SUM(poi.quantity) AS incomingStock
                    FROM PurchaseOrderItem poi
                    JOIN poi.purchaseOrder po
                    WHERE po.inventory = :inventory AND po.status NOT IN (:excludedStatuses)
                    GROUP BY poi.productVariant.id
                )
                SELECT
                    p.id AS productId,
                    p.model AS model,
                    p.color AS color,
                    p.imgUrl AS imgUrl,
                    COALESCE(ii.lastPurchasePrice, 0) AS lastPurchasePrice,
                    ii.lastPurchaseDate AS lastPurchaseDate,
                    pv.id AS variantId,
                    pv.sku AS sku,
                    pv.size AS size,
                    ii.quantity AS currentStock,
                    COALESCE(is.incomingStock, 0) AS incomingStock,
                    ii.quantity + COALESCE(is.incomingStock, 0) AS futureStock
                FROM InventoryItem ii
                JOIN ProductVariant pv ON pv.id = ii.productVariant.id
                JOIN Product p ON p.id = pv.product.id
                LEFT JOIN IncomingStock is ON is.variantId = pv.id
                WHERE ii.inventory = :inventory
                ORDER BY p.model ASC, p.color ASC, pv.size
            """)
    List<ProductVariantStockSummary> findAllWithStockDetailsByInventory(
            @Param("excludedStatuses") List<PurchaseOrderStatus> excludedStatuses,
            @Param("inventory") Inventory inventory
    );

    @Query("""
                WITH SalesSummary AS (
                     SELECT
                         s.productVariant.id AS variantId,
                         MAX(s.date) AS lastSaleDate,
                         SUM(s.quantity) AS totalSold,
                         SUM(s.unitPrice * s.quantity) AS totalRevenue,
                         SUM(s.totalProfit) AS totalProfit
                     FROM Sale s
                     JOIN ProductVariant pv ON pv.id = s.productVariant.id
                     JOIN InventoryItem ii ON ii.productVariant.id = pv.id
                     WHERE ii.inventory = :inventory
                     GROUP BY s.productVariant.id
                 )
                 SELECT
                    p.id AS productId,
                    p.model AS model,
                    p.color AS color,
                    p.imgUrl AS imgUrl,
                    pv.id AS variantId,
                    pv.sku AS sku,
                    pv.size AS size,
                    ss.lastSaleDate AS lastSaleDate,
                    COALESCE(ss.totalSold, 0) AS totalSold,
                    COALESCE(ss.totalRevenue, 0) AS totalRevenue,
                    COALESCE(ss.totalProfit, 0) AS totalProfit,
                    COALESCE(ii.quantity, 0) AS currentStock
                 FROM InventoryItem ii
                 JOIN ProductVariant pv ON ii.productVariant.id = pv.id
                 JOIN Product p ON pv.product.id = p.id
                 LEFT JOIN SalesSummary ss ON ss.variantId = pv.id
                 WHERE ii.inventory = :inventory
                 ORDER BY ss.totalSold DESC, p.model ASC, p.color ASC, pv.size
            """)
    List<ProductVariantSaleSummary> findAllWithSalesDataByInventory(@Param("inventory") Inventory inventory);

    Optional<InventoryItem> findByInventoryAndProductVariant(Inventory inventory, ProductVariant productVariant);

}