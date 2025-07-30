package com.jaoow.helmetstore.service;

import com.jaoow.helmetstore.cache.CacheNames;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

/**
 * Service responsible for invalidating caches when financial data changes
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CacheInvalidationService {

    private final CacheManager cacheManager;

    /**
     * Invalidate all financial-related caches when a transaction is created, updated, or deleted
     */
    public void invalidateFinancialCaches() {
        log.info("Invalidating all financial caches due to transaction change");
        
        // Invalidate cash flow related caches
        invalidateCache(CacheNames.CASH_FLOW_SUMMARY);
        invalidateCache(CacheNames.MONTHLY_CASH_FLOW);
        invalidateCache(CacheNames.FINANCIAL_SUMMARY);
        invalidateCache(CacheNames.PROFIT_CALCULATION);
        invalidateCache(CacheNames.CASH_FLOW_CALCULATION);
        
        // Invalidate profit tracking related caches
        invalidateCache(CacheNames.PROFIT_SUMMARY);
        invalidateCache(CacheNames.MONTHLY_PROFIT);
        
        // Also invalidate revenue and profit cache as it might be affected
        invalidateCache(CacheNames.REVENUE_AND_PROFIT);
    }

    /**
     * Invalidate specific monthly cash flow cache for a user
     */
    public void invalidateMonthlyCashFlowCache(String userEmail) {
        log.info("Invalidating monthly cash flow cache for user: {}", userEmail);
        invalidateCache(CacheNames.MONTHLY_CASH_FLOW);
    }

    /**
     * Invalidate cash flow summary cache for a user
     */
    public void invalidateCashFlowSummaryCache(String userEmail) {
        log.info("Invalidating cash flow summary cache for user: {}", userEmail);
        invalidateCache(CacheNames.CASH_FLOW_SUMMARY);
    }

    /**
     * Invalidate profit calculation cache
     */
    public void invalidateProfitCache() {
        log.info("Invalidating profit calculation cache");
        invalidateCache(CacheNames.PROFIT_CALCULATION);
    }

    /**
     * Invalidate cash flow calculation cache
     */
    public void invalidateCashFlowCalculationCache() {
        log.info("Invalidating cash flow calculation cache");
        invalidateCache(CacheNames.CASH_FLOW_CALCULATION);
    }

    /**
     * Invalidate financial summary cache
     */
    public void invalidateFinancialSummaryCache() {
        log.info("Invalidating financial summary cache");
        invalidateCache(CacheNames.FINANCIAL_SUMMARY);
    }

    /**
     * Helper method to invalidate a specific cache
     */
    private void invalidateCache(String cacheName) {
        try {
            var cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
                log.debug("Successfully cleared cache: {}", cacheName);
            } else {
                log.warn("Cache not found: {}", cacheName);
            }
        } catch (Exception e) {
            log.error("Error clearing cache {}: {}", cacheName, e.getMessage(), e);
        }
    }
} 