package com.jaoow.helmetstore.repository;

import com.jaoow.helmetstore.dto.summary.ProductSalesAndStockSummary;
import com.jaoow.helmetstore.dto.summary.ProductVariantSaleSummary;
import com.jaoow.helmetstore.dto.summary.ProductVariantSalesAndStockSummary;
import com.jaoow.helmetstore.dto.summary.ProductVariantStockSummary;
import com.jaoow.helmetstore.model.ProductVariant;
import com.jaoow.helmetstore.model.PurchaseOrderStatus;
import com.jaoow.helmetstore.model.inventory.Inventory;
import com.jaoow.helmetstore.model.inventory.InventoryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;

@Repository
public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {

    @Query("""
            WITH SalesSummary AS (
                SELECT
                    si.productVariant.id AS variantId,
                    MAX(s.date) AS lastSaleDate,
                    SUM(si.quantity) AS totalSold,
                    SUM(si.totalItemPrice) AS totalRevenue,
                    SUM(si.totalItemProfit) AS totalProfit
                FROM Sale s
                JOIN s.items si
                WHERE s.inventory = :inventory
                GROUP BY si.productVariant.id
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
                COALESCE(c.name, '') AS categoryName,
                ii.lastPurchaseDate AS lastPurchaseDate,
                COALESCE(ii.averageCost, 0) AS averageCost,
                COALESCE(ipd.salePrice, 0) AS salePrice,
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
                ii.quantity * COALESCE(ii.averageCost, 0) AS totalStockValue,
                CASE
                    WHEN COALESCE(ss.totalRevenue, 0) > 0 THEN
                        (COALESCE(ss.totalProfit, 0) / COALESCE(ss.totalRevenue, 0)) * 100
                    ELSE 0
                END AS profitMargin
            FROM InventoryItem ii
            JOIN ProductVariant pv ON pv.id = ii.productVariant.id
            JOIN Product p ON p.id = pv.product.id
            LEFT JOIN p.category c
            LEFT JOIN PurchaseOrderItem poi ON poi.productVariant.id = pv.id
            LEFT JOIN poi.purchaseOrder po ON po.inventory = :inventory
            LEFT JOIN SalesSummary ss ON ss.variantId = pv.id
            LEFT JOIN StockSummary s ON s.variantId = pv.id
            LEFT JOIN ProductData ipd ON ipd.product = p AND ipd.inventory = :inventory
            WHERE ii.inventory = :inventory
            GROUP BY
                p.id, pv.id, ii.quantity, ii.lastPurchaseDate, ii.averageCost,
                ss.lastSaleDate, ss.totalSold, ss.totalRevenue, ss.totalProfit, s.incomingStock, ipd.salePrice, c.name
            ORDER BY p.model ASC, p.color ASC, pv.size
            """)
    List<ProductVariantSalesAndStockSummary> findAllWithSalesAndPurchaseDataByInventory(
            @Param("excludedStatuses") List<PurchaseOrderStatus> excludedStatuses,
            @Param("inventory") Inventory inventory);

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
                    COALESCE(c.name, '') AS categoryName,
                    COALESCE(ii.averageCost, 0) AS averageCost,
                    COALESCE(ipd.salePrice, 0) AS salePrice,
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
                LEFT JOIN p.category c
                LEFT JOIN IncomingStock is ON is.variantId = pv.id
                LEFT JOIN ProductData ipd ON ipd.product = p AND ipd.inventory = :inventory
                WHERE ii.inventory = :inventory
                ORDER BY p.model ASC, p.color ASC, pv.size
            """)
    List<ProductVariantStockSummary> findAllWithStockDetailsByInventory(
            @Param("excludedStatuses") List<PurchaseOrderStatus> excludedStatuses,
            @Param("inventory") Inventory inventory);

    @Query("""
                WITH SalesSummary AS (
                     SELECT
                         si.productVariant.id AS variantId,
                         MAX(s.date) AS lastSaleDate,
                         SUM(si.quantity) AS totalSold,
                         SUM(si.totalItemPrice) AS totalRevenue,
                         SUM(si.totalItemProfit) AS totalProfit
                     FROM Sale s
                     JOIN s.items si
                     JOIN ProductVariant pv ON pv.id = si.productVariant.id
                     JOIN InventoryItem ii ON ii.productVariant.id = pv.id
                     WHERE ii.inventory = :inventory
                     GROUP BY si.productVariant.id
                 )
                 SELECT
                    p.id AS productId,
                    p.model AS model,
                    p.color AS color,
                    p.imgUrl AS imgUrl,
                    COALESCE(c.name, '') AS categoryName,
                    COALESCE(ipd.salePrice, 0) AS salePrice,
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
                 LEFT JOIN p.category c
                 LEFT JOIN SalesSummary ss ON ss.variantId = pv.id
                 LEFT JOIN ProductData ipd ON ipd.product = p AND ipd.inventory = :inventory
                 WHERE ii.inventory = :inventory
                 ORDER BY ss.totalSold DESC, p.model ASC, p.color ASC, pv.size
            """)
    List<ProductVariantSaleSummary> findAllWithSalesDataByInventory(@Param("inventory") Inventory inventory);

    Optional<InventoryItem> findByInventoryAndProductVariant(Inventory inventory, ProductVariant productVariant);

    @Modifying
    @Query("UPDATE InventoryItem ii SET ii.quantity = :quantity WHERE ii.productVariant.id = :variantId AND ii.inventory = :inventory")
    void updateStock(@Param("variantId") Long variantId,
            @Param("quantity") int quantity,
            @Param("inventory") Inventory inventory);

    @Modifying
    @Query("DELETE FROM InventoryItem ii WHERE ii.productVariant.product.id = :productId AND ii.inventory = :inventory")
    void deleteByProductIdAndInventory(@Param("productId") Long productId, @Param("inventory") Inventory inventory);

    @Modifying
	@Query("UPDATE InventoryItem ii SET ii.averageCost = :price WHERE ii.productVariant.id = :variantId AND ii.inventory = :inventory")
	void updatePrice(@Param("variantId") Long variantId,
			@Param("price") BigDecimal price,
			@Param("inventory") Inventory inventory);

    @Query("""
            WITH SalesSummary AS (
                SELECT
                    si.productVariant.id AS variantId,
                    MAX(s.date) AS lastSaleDate,
                    SUM(si.quantity) AS totalSold,
                    SUM(si.totalItemPrice) AS totalRevenue,
                    SUM(si.totalItemProfit) AS totalProfit
                FROM Sale s
                JOIN s.items si
                WHERE s.inventory = :inventory
                GROUP BY si.productVariant.id
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
            ),
            ProductAggregates AS (
                SELECT
                    p.id AS productId,
                    SUM(ii.quantity) AS totalCurrentStock,
                    SUM(s.incomingStock) AS totalIncomingStock,
                    SUM(ii.quantity * COALESCE(ii.averageCost, 0)) AS totalStockValue,
                    MAX(ii.lastPurchaseDate) AS lastPurchaseDate,
                    AVG(ii.averageCost) AS avgPurchasePrice,
                    MAX(ss.lastSaleDate) AS lastSaleDate,
                    SUM(ss.totalSold) AS totalSold,
                    SUM(ss.totalRevenue) AS totalRevenue,
                    SUM(ss.totalProfit) AS totalProfit
                FROM InventoryItem ii
                JOIN ProductVariant pv ON pv.id = ii.productVariant.id
                JOIN Product p ON p.id = pv.product.id
                LEFT JOIN SalesSummary ss ON ss.variantId = pv.id
                LEFT JOIN StockSummary s ON s.variantId = pv.id
                WHERE ii.inventory = :inventory
                GROUP BY p.id
            )
            SELECT
                p.id AS productId,
                p.model AS model,
                p.color AS color,
                p.imgUrl AS imgUrl,
                COALESCE(c.name, '') AS categoryName,
                COALESCE(ipd.salePrice, 0) AS salePrice,
                pa.lastPurchaseDate AS lastPurchaseDate,
                pa.avgPurchasePrice AS averageCost,
                pa.totalCurrentStock AS totalCurrentStock,
                pa.totalIncomingStock AS totalIncomingStock,
                pa.totalCurrentStock + pa.totalIncomingStock AS totalFutureStock,
                pa.lastSaleDate AS lastSaleDate,
                pa.totalSold AS totalSold,
                pa.totalRevenue AS totalRevenue,
                pa.totalProfit AS totalProfit,
                pa.totalStockValue AS totalStockValue,
                CASE
                    WHEN COALESCE(pa.totalRevenue, 0) > 0 THEN
                        (COALESCE(pa.totalProfit, 0) / COALESCE(pa.totalRevenue, 0)) * 100
                    ELSE 0
                END AS profitMargin
            FROM Product p
            JOIN ProductAggregates pa ON pa.productId = p.id
            LEFT JOIN p.category c
            LEFT JOIN ProductData ipd ON ipd.product = p AND ipd.inventory = :inventory
            WHERE EXISTS (
                SELECT 1 FROM InventoryItem ii
                JOIN ProductVariant pv ON pv.id = ii.productVariant.id
                WHERE ii.inventory = :inventory AND pv.product.id = p.id
            )
            ORDER BY p.model ASC, p.color ASC
            """)
    List<ProductSalesAndStockSummary> findAllGroupedByProduct(
            @Param("excludedStatuses") List<PurchaseOrderStatus> excludedStatuses,
            @Param("inventory") Inventory inventory);

    @Modifying
    @Query("UPDATE InventoryItem ii SET ii.averageCost = :price WHERE ii.productVariant.product.id = :productId AND ii.inventory = :inventory")
    void updatePriceByProduct(@Param("productId") Long productId,
            @Param("price") BigDecimal price,
            @Param("inventory") Inventory inventory);
}