package com.jaoow.helmetstore.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.jaoow.helmetstore.cache.CacheNames;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public Caffeine<Object, Object> caffeineConfig() {
        return Caffeine.newBuilder()
                .recordStats()
                .expireAfterWrite(Duration.of(24, ChronoUnit.HOURS))
                .maximumSize(500);
    }

    @Bean
    public CacheManager cacheManager(Caffeine<Object, Object> caffeine) {
        CaffeineCacheManager manager = new CaffeineCacheManager(CacheNames.ALL_CACHE_NAMES);
        manager.setCaffeine(caffeine);
        return manager;
    }
}
