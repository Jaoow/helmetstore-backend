package com.jaoow.helmetstore.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.jaoow.helmetstore.cache.CacheNames;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;

@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * PERFORMANCE OPTIMIZATION: Different cache strategies for different data types
     */
    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();

        cacheManager.setCaches(Arrays.asList(
            // ============================================================================
            // HIGH-FREQUENCY ENDPOINTS - Short TTL, High Max Size
            // ============================================================================
            // /sales/history: 3698ms max - Cache por 10 min
            buildCache(CacheNames.SALES_HISTORY, 10, ChronoUnit.MINUTES, 200),

            // /account/profit-summary: 5284ms - Cache por 15 min
            buildCache(CacheNames.PROFIT_SUMMARY, 15, ChronoUnit.MINUTES, 100),
            buildCache(CacheNames.MONTHLY_PROFIT, 15, ChronoUnit.MINUTES, 200),
            buildCache(CacheNames.PROFIT_CALCULATION, 15, ChronoUnit.MINUTES, 100),

            // /account/cash-flow-summary: 4640ms - Cache por 15 min
            buildCache(CacheNames.CASH_FLOW_SUMMARY, 15, ChronoUnit.MINUTES, 100),
            buildCache(CacheNames.MONTHLY_CASH_FLOW, 15, ChronoUnit.MINUTES, 200),
            buildCache(CacheNames.CASH_FLOW_CALCULATION, 15, ChronoUnit.MINUTES, 100),

            // ============================================================================
            // MEDIUM-FREQUENCY ENDPOINTS - Medium TTL
            // ============================================================================
            // /orders/history: 1803ms - Cache por 20 min
            buildCache(CacheNames.PURCHASE_ORDER_HISTORY, 20, ChronoUnit.MINUTES, 150),

            // /reports endpoints: ~1000ms - Cache por 30 min
            buildCache(CacheNames.MOST_SOLD_PRODUCTS, 30, ChronoUnit.MINUTES, 100),
            buildCache(CacheNames.PRODUCT_INDICATORS, 30, ChronoUnit.MINUTES, 150),
            buildCache(CacheNames.PRODUCT_INDICATORS_GROUPED, 30, ChronoUnit.MINUTES, 150),
            buildCache(CacheNames.PRODUCT_STOCK, 30, ChronoUnit.MINUTES, 150),

            // ============================================================================
            // LOW-FREQUENCY, STABLE DATA - Long TTL
            // ============================================================================
            // /products, /categories: ~600ms - Cache por 1 hora
            buildCache(CacheNames.PRODUCT, 1, ChronoUnit.HOURS, 500),
            buildCache(CacheNames.CATEGORY, 1, ChronoUnit.HOURS, 100),

            // Revenue and profit calculations - Cache por 30 min
            buildCache(CacheNames.REVENUE_AND_PROFIT, 30, ChronoUnit.MINUTES, 100),

            // Financial summary - Cache por 15 min
            buildCache(CacheNames.FINANCIAL_SUMMARY, 15, ChronoUnit.MINUTES, 100)
        ));

        return cacheManager;
    }

    /**
     * Build a Caffeine cache with specific configuration
     */
    private CaffeineCache buildCache(String name, long duration, ChronoUnit unit, int maxSize) {
        return new CaffeineCache(name, Caffeine.newBuilder()
                .recordStats() // Enable metrics
                .expireAfterWrite(Duration.of(duration, unit))
                .maximumSize(maxSize)
                .build());
    }
}
