package com.jaoow.helmetstore.observability;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuração central de observabilidade e monitoramento
 */
@Slf4j
@Configuration
public class ObservabilityConfiguration implements WebMvcConfigurer {

    private final MeterRegistry meterRegistry;
    private final CacheManager cacheManager;

    public ObservabilityConfiguration(MeterRegistry meterRegistry,
                                      CacheManager cacheManager) {
        this.meterRegistry = meterRegistry;
        this.cacheManager = cacheManager;
        registerCacheMetrics();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        log.info("ℹ️ HTTP Performance tracking via Filter for complete request coverage");
    }

    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        log.info("✅ @Timed aspect enabled");
        return new TimedAspect(registry);
    }

    private void registerCacheMetrics() {
        cacheManager.getCacheNames().forEach(cacheName -> {
            var cache = cacheManager.getCache(cacheName);
            if (cache instanceof CaffeineCache caffeineCache) {
                var nativeCache = caffeineCache.getNativeCache();
                CaffeineCacheMetrics.monitor(meterRegistry, nativeCache, cacheName);

                log.info("✅ Cache metrics registered for: {}", cacheName);
            }
        });
    }
}
