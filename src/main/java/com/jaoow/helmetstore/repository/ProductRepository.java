package com.jaoow.helmetstore.repository;

import com.jaoow.helmetstore.dto.summary.ProductVariantSalesAndStockSummary;
import com.jaoow.helmetstore.dto.summary.ProductVariantSaleSummary;
import com.jaoow.helmetstore.dto.summary.ProductVariantStockSummary;
import com.jaoow.helmetstore.model.Product;
import com.jaoow.helmetstore.model.PurchaseOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

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
                    GROUP BY s.productVariant.id
            )
            SELECT
                p.id AS productId,
                p.model AS model,
                p.color AS color,
                p.imgUrl AS imgUrl,
                COALESCE(p.lastPurchaseDate, NULL) AS lastPurchaseDate,
                COALESCE(p.lastPurchasePrice, 0) AS lastPurchasePrice,
                pv.id AS variantId,
                pv.sku AS sku,
                pv.size AS size,
                COALESCE(pv.quantity, 0) AS currentStock,
                COALESCE(SUM(poi.quantity), 0) AS totalPurchased,
                COALESCE(SUM(CASE WHEN po.status NOT IN (:excludedStatuses) THEN poi.quantity ELSE 0 END), 0) AS incomingStock,
                COALESCE(ss.lastSaleDate, NULL) AS lastSaleDate,
                COALESCE(ss.totalSold, 0) AS totalSold,
                COALESCE(ss.totalRevenue, 0) AS totalRevenue,
                COALESCE(ss.totalProfit, 0) AS totalProfit,
                CASE
                    WHEN COALESCE(ss.totalRevenue, 0) > 0 THEN
                        (COALESCE(ss.totalProfit, 0) / COALESCE(ss.totalRevenue, 0)) * 100
                    ELSE 0
                END AS profitMargin
            FROM ProductVariant pv
            JOIN Product p ON p.id = pv.product.id
            LEFT JOIN PurchaseOrderItem poi ON poi.productVariant.id = pv.id
            LEFT JOIN poi.purchaseOrder po
            LEFT JOIN SalesSummary ss ON ss.variantId = pv.id
            GROUP BY
                p.id, pv.id, ss.lastSaleDate, ss.totalSold, ss.totalRevenue, ss.totalProfit
            ORDER BY p.model ASC, p.color ASC, pv.size
            """)
    List<ProductVariantSalesAndStockSummary> findAllWithSalesAndPurchaseData(@Param("excludedStatuses") List<PurchaseOrderStatus> excludedStatuses);

    @Query("""
               WITH IncomingStock AS (
                    SELECT
                        poi.productVariant.id AS variantId,
                        SUM(poi.quantity) AS incomingStock
                    FROM PurchaseOrderItem poi
                    JOIN poi.purchaseOrder po
                    WHERE po.status NOT IN (:excludedStatuses)
                    GROUP BY poi.productVariant.id
                )
                SELECT
                    p.id AS productId,
                    p.model AS model,
                    p.color AS color,
                    p.imgUrl AS imgUrl,
                    COALESCE(p.lastPurchasePrice, 0) AS lastPurchasePrice,
                    p.lastPurchaseDate AS lastPurchaseDate,
                    pv.id AS variantId,
                    pv.sku AS sku,
                    pv.size AS size,
                    COALESCE(pv.quantity, 0) AS currentStock,
                    COALESCE(is.incomingStock, 0) AS incomingStock,
                     pv.quantity + COALESCE(is.incomingStock, 0) AS futureStock
                FROM Product p
                JOIN ProductVariant pv ON pv.product.id = p.id
                LEFT JOIN IncomingStock is ON is.variantId = pv.id
                ORDER BY p.model ASC, p.color ASC, pv.size
            """)
    List<ProductVariantStockSummary> findAllWithStockDetails(@Param("excludedStatuses") List<PurchaseOrderStatus> excludedStatuses);


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
                COALESCE(ss.lastSaleDate, NULL) AS lastSaleDate,
                COALESCE(ss.totalSold, 0) AS totalSold,
                COALESCE(ss.totalRevenue, 0) AS totalRevenue,
                COALESCE(ss.totalProfit, 0) AS totalProfit
             FROM Product p
             JOIN ProductVariant pv ON pv.product.id = p.id
             LEFT JOIN SalesSummary ss ON ss.variantId = pv.id
             ORDER BY ss.totalSold DESC, p.model ASC, p.color ASC, pv.size
            """)
    List<ProductVariantSaleSummary> findAllWithSalesData();

    @Query("SELECT DISTINCT p FROM Product p JOIN FETCH p.variants pv ORDER BY p.model ASC, p.color ASC, pv.size")
    List<Product> findAllWithVariants();

}
