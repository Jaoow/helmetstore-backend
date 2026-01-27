package com.jaoow.helmetstore.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Aspect para monitorar operações de cache e registrar métricas detalhadas
 */
@Slf4j
@Aspect
@Component
public class CacheMetricsAspect {

    private final MeterRegistry meterRegistry;
    private final CacheManager cacheManager;
    private final ConcurrentHashMap<String, CacheMetrics> cacheMetricsMap = new ConcurrentHashMap<>();

    public CacheMetricsAspect(MeterRegistry meterRegistry, CacheManager cacheManager) {
        this.meterRegistry = meterRegistry;
        this.cacheManager = cacheManager;
    }

    /**
     * Intercepta métodos anotados com @Cacheable
     */
    @Around("@annotation(org.springframework.cache.annotation.Cacheable)")
    public Object aroundCacheable(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().toShortString();
        long startTime = System.currentTimeMillis();

        try {
            Object result = joinPoint.proceed();

            long duration = System.currentTimeMillis() - startTime;

            // Se demorou muito pouco, provavelmente veio do cache (hit)
            // Se demorou mais, provavelmente teve que executar (miss)
            boolean likelyCacheHit = duration < 10; // Heurística: menos de 10ms = cache hit

            String resultType = likelyCacheHit ? "hit" : "miss";

            meterRegistry.counter("cache.operation",
                    "method", methodName,
                    "result", resultType).increment();

            log.debug("📦 Cache {} for method: {} ({}ms)",
                    resultType.toUpperCase(), methodName, duration);

            return result;

        } catch (Exception e) {
            meterRegistry.counter("cache.operation",
                    "method", methodName,
                    "result", "error").increment();
            throw e;
        }
    }

    /**
     * Intercepta métodos anotados com @CacheEvict
     */
    @Around("@annotation(org.springframework.cache.annotation.CacheEvict)")
    public Object aroundCacheEvict(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().toShortString();

        meterRegistry.counter("cache.eviction",
                "method", methodName).increment();

        log.debug("🗑️ Cache eviction triggered by: {}", methodName);

        return joinPoint.proceed();
    }

    /**
     * Obtém estatísticas de cache
     */
    public ConcurrentHashMap<String, CacheMetrics> getCacheMetrics() {
        // Atualiza métricas atuais
        cacheManager.getCacheNames().forEach(cacheName -> {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                CacheMetrics metrics = cacheMetricsMap.computeIfAbsent(cacheName, k -> new CacheMetrics());
                // Aqui você pode adicionar lógica para extrair métricas específicas do cache
            }
        });

        return cacheMetricsMap;
    }

    /**
     * Classe para armazenar métricas de cache
     */
    public static class CacheMetrics {
        private long hits;
        private long misses;
        private long evictions;

        public void incrementHits() { hits++; }
        public void incrementMisses() { misses++; }
        public void incrementEvictions() { evictions++; }

        public long getHits() { return hits; }
        public long getMisses() { return misses; }
        public long getEvictions() { return evictions; }

        public double getHitRatio() {
            long total = hits + misses;
            return total > 0 ? (double) hits / total * 100 : 0;
        }
    }
}
