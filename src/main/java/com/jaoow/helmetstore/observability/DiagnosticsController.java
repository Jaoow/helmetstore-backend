package com.jaoow.helmetstore.observability;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.cache.CacheManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Endpoint customizado para visualizar métricas de performance e diagnóstico
 */
@Slf4j
@RestController
@RequestMapping("/api/diagnostics")
public class DiagnosticsController implements HealthIndicator {

    private final EntityManager entityManager;
    private final QueryPerformanceInspector queryInspector;
    private final CacheManager cacheManager;
    private final MeterRegistry meterRegistry;
    private final RequestHistoryTracker historyTracker;

    public DiagnosticsController(EntityManager entityManager,
                                 QueryPerformanceInspector queryInspector,
                                 CacheManager cacheManager,
                                 MeterRegistry meterRegistry,
                                 RequestHistoryTracker historyTracker) {
        this.entityManager = entityManager;
        this.queryInspector = queryInspector;
        this.cacheManager = cacheManager;
        this.meterRegistry = meterRegistry;
        this.historyTracker = historyTracker;
    }

    /**
     * Visão geral de performance
     */
    @GetMapping("/performance")
    public ResponseEntity<Map<String, Object>> getPerformanceOverview() {
        Map<String, Object> overview = new HashMap<>();

        // Hibernate Statistics
        overview.put("hibernate", getHibernateStatistics());

        // Query Performance
        overview.put("queries", getQueryStatistics());

        // Cache Statistics
        overview.put("cache", getCacheStatistics());

        // JVM & Resources
        overview.put("jvm", getJvmStatistics());

        // HTTP Metrics
        overview.put("http", getHttpMetrics());

        return ResponseEntity.ok(overview);
    }

    /**
     * Estatísticas detalhadas do Hibernate
     */
    @GetMapping("/hibernate")
    public ResponseEntity<Map<String, Object>> getHibernateStatistics() {
        Map<String, Object> stats = new HashMap<>();

        try {
            SessionFactory sessionFactory = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class);
            Statistics hibernateStats = sessionFactory.getStatistics();

            if (!hibernateStats.isStatisticsEnabled()) {
                stats.put("enabled", false);
                stats.put("message", "Hibernate statistics are disabled. Enable with: spring.jpa.properties.hibernate.generate_statistics=true");
                return ResponseEntity.ok(stats);
            }

            stats.put("enabled", true);

            // Query Statistics
            Map<String, Object> queryStats = new HashMap<>();
            queryStats.put("total", hibernateStats.getQueryExecutionCount());
            queryStats.put("cacheable", hibernateStats.getQueryCacheHitCount() + hibernateStats.getQueryCacheMissCount());
            queryStats.put("cacheHits", hibernateStats.getQueryCacheHitCount());
            queryStats.put("cacheMisses", hibernateStats.getQueryCacheMissCount());
            queryStats.put("cachePuts", hibernateStats.getQueryCachePutCount());
            queryStats.put("maxExecutionTime", hibernateStats.getQueryExecutionMaxTime() + "ms");
            queryStats.put("slowestQuery", hibernateStats.getQueryExecutionMaxTimeQueryString());
            stats.put("queries", queryStats);

            // Entity Statistics
            Map<String, Object> entityStats = new HashMap<>();
            entityStats.put("loads", hibernateStats.getEntityLoadCount());
            entityStats.put("fetches", hibernateStats.getEntityFetchCount());
            entityStats.put("inserts", hibernateStats.getEntityInsertCount());
            entityStats.put("updates", hibernateStats.getEntityUpdateCount());
            entityStats.put("deletes", hibernateStats.getEntityDeleteCount());
            stats.put("entities", entityStats);

            // Collection Statistics
            Map<String, Object> collectionStats = new HashMap<>();
            collectionStats.put("loads", hibernateStats.getCollectionLoadCount());
            collectionStats.put("fetches", hibernateStats.getCollectionFetchCount());
            collectionStats.put("updates", hibernateStats.getCollectionUpdateCount());
            collectionStats.put("removes", hibernateStats.getCollectionRemoveCount());
            collectionStats.put("recreates", hibernateStats.getCollectionRecreateCount());
            stats.put("collections", collectionStats);

            // Second Level Cache
            Map<String, Object> secondLevelCache = new HashMap<>();
            secondLevelCache.put("hits", hibernateStats.getSecondLevelCacheHitCount());
            secondLevelCache.put("misses", hibernateStats.getSecondLevelCacheMissCount());
            secondLevelCache.put("puts", hibernateStats.getSecondLevelCachePutCount());
            stats.put("secondLevelCache", secondLevelCache);

            // Session Statistics
            Map<String, Object> sessionStats = new HashMap<>();
            sessionStats.put("opened", hibernateStats.getSessionOpenCount());
            sessionStats.put("closed", hibernateStats.getSessionCloseCount());
            sessionStats.put("transactions", hibernateStats.getTransactionCount());
            sessionStats.put("successfulTransactions", hibernateStats.getSuccessfulTransactionCount());
            sessionStats.put("optimisticLockFailures", hibernateStats.getOptimisticFailureCount());
            stats.put("sessions", sessionStats);

            // Connection Statistics
            Map<String, Object> connectionStats = new HashMap<>();
            connectionStats.put("obtained", hibernateStats.getConnectCount());
            stats.put("connections", connectionStats);

        } catch (Exception e) {
            log.error("Error getting Hibernate statistics", e);
            stats.put("error", e.getMessage());
        }

        return ResponseEntity.ok(stats);
    }

    /**
     * Análise detalhada de queries
     */
    @GetMapping("/queries")
    public ResponseEntity<Map<String, Object>> getQueryStatistics() {
        Map<String, Object> stats = new HashMap<>();

        var queryStats = queryInspector.getQueryStats();

        // Top 10 queries mais executadas
        List<Map<String, Object>> topExecuted = queryStats.entrySet().stream()
                .sorted((e1, e2) -> Integer.compare(e2.getValue().getCount(), e1.getValue().getCount()))
                .limit(10)
                .map(entry -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("query", truncateQuery(entry.getKey()));
                    item.put("count", entry.getValue().getCount());
                    item.put("avgTime", entry.getValue().getAverageExecutionTime() + "ms");
                    item.put("totalTime", entry.getValue().getTotalExecutionTime() + "ms");
                    return item;
                })
                .collect(Collectors.toList());
        stats.put("topExecuted", topExecuted);

        // Top 10 queries mais lentas
        List<Map<String, Object>> slowest = queryStats.entrySet().stream()
                .sorted((e1, e2) -> Long.compare(e2.getValue().getAverageExecutionTime(),
                                                  e1.getValue().getAverageExecutionTime()))
                .limit(10)
                .map(entry -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("query", truncateQuery(entry.getKey()));
                    item.put("count", entry.getValue().getCount());
                    item.put("avgTime", entry.getValue().getAverageExecutionTime() + "ms");
                    item.put("totalTime", entry.getValue().getTotalExecutionTime() + "ms");
                    return item;
                })
                .collect(Collectors.toList());
        stats.put("slowest", slowest);

        stats.put("totalUniqueQueries", queryStats.size());

        return ResponseEntity.ok(stats);
    }

    /**
     * Estatísticas de cache
     */
    @GetMapping("/cache")
    public ResponseEntity<Map<String, Object>> getCacheStatistics() {
        Map<String, Object> stats = new HashMap<>();

        List<Map<String, Object>> caches = cacheManager.getCacheNames().stream()
                .map(cacheName -> {
                    Map<String, Object> cacheInfo = new HashMap<>();
                    cacheInfo.put("name", cacheName);

                    var cache = cacheManager.getCache(cacheName);
                    if (cache != null) {
                        cacheInfo.put("type", cache.getClass().getSimpleName());
                        // Adicionar mais detalhes se necessário
                    }

                    return cacheInfo;
                })
                .collect(Collectors.toList());

        stats.put("caches", caches);
        stats.put("totalCaches", caches.size());

        return ResponseEntity.ok(stats);
    }

    /**
     * Estatísticas da JVM e recursos
     */
    @GetMapping("/jvm")
    public ResponseEntity<Map<String, Object>> getJvmStatistics() {
        Map<String, Object> stats = new HashMap<>();

        // Memory
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        Map<String, Object> memory = new HashMap<>();

        var heapUsage = memoryMXBean.getHeapMemoryUsage();
        Map<String, String> heap = new HashMap<>();
        heap.put("used", formatBytes(heapUsage.getUsed()));
        heap.put("committed", formatBytes(heapUsage.getCommitted()));
        heap.put("max", formatBytes(heapUsage.getMax()));
        heap.put("usagePercent", String.format("%.2f%%", (double) heapUsage.getUsed() / heapUsage.getMax() * 100));
        memory.put("heap", heap);

        var nonHeapUsage = memoryMXBean.getNonHeapMemoryUsage();
        Map<String, String> nonHeap = new HashMap<>();
        nonHeap.put("used", formatBytes(nonHeapUsage.getUsed()));
        nonHeap.put("committed", formatBytes(nonHeapUsage.getCommitted()));
        nonHeap.put("max", formatBytes(nonHeapUsage.getMax()));
        memory.put("nonHeap", nonHeap);

        stats.put("memory", memory);

        // Threads
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        Map<String, Object> threads = new HashMap<>();
        threads.put("current", threadMXBean.getThreadCount());
        threads.put("peak", threadMXBean.getPeakThreadCount());
        threads.put("daemon", threadMXBean.getDaemonThreadCount());
        threads.put("totalStarted", threadMXBean.getTotalStartedThreadCount());
        stats.put("threads", threads);

        // Runtime
        var runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        Map<String, Object> runtime = new HashMap<>();
        runtime.put("uptime", formatDuration(runtimeMXBean.getUptime()));
        runtime.put("vmName", runtimeMXBean.getVmName());
        runtime.put("vmVersion", runtimeMXBean.getVmVersion());
        stats.put("runtime", runtime);

        return ResponseEntity.ok(stats);
    }

    /**
     * Métricas HTTP do Micrometer
     */
    @GetMapping("/http")
    public ResponseEntity<Map<String, Object>> getHttpMetrics() {
        Map<String, Object> stats = new HashMap<>();

        try {
            // Busca métricas de HTTP requests do Micrometer (nome correto no Spring Boot)
            var requestMetrics = meterRegistry.find("http.server.requests").timers();

            if (!requestMetrics.isEmpty()) {
                List<Map<String, Object>> endpoints = requestMetrics.stream()
                        .filter(timer -> timer.count() > 0) // Só mostra timers com dados
                        .sorted((t1, t2) -> Double.compare(
                                t2.mean(java.util.concurrent.TimeUnit.MILLISECONDS),
                                t1.mean(java.util.concurrent.TimeUnit.MILLISECONDS)))
                        .map(timer -> {
                            Map<String, Object> endpoint = new HashMap<>();
                            endpoint.put("uri", timer.getId().getTag("uri"));
                            endpoint.put("method", timer.getId().getTag("method"));
                            endpoint.put("status", timer.getId().getTag("status"));
                            endpoint.put("count", timer.count());
                            endpoint.put("mean", String.format("%.0fms", timer.mean(java.util.concurrent.TimeUnit.MILLISECONDS)));
                            endpoint.put("max", String.format("%.0fms", timer.max(java.util.concurrent.TimeUnit.MILLISECONDS)));
                            endpoint.put("total", String.format("%.0fms", timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)));
                            return endpoint;
                        })
                        .collect(Collectors.toList());

                stats.put("endpoints", endpoints);

                // Identifica endpoint mais lento
                if (!endpoints.isEmpty()) {
                    var slowest = endpoints.get(0);
                    stats.put("slowestEndpoint", slowest);
                }
            } else {
                stats.put("message", "No HTTP metrics collected yet. Make some API requests.");
            }

            // Conta total de requests
            var totalRequests = meterRegistry.find("http.requests.total").counters();
            if (!totalRequests.isEmpty()) {
                long total = totalRequests.stream().mapToLong(c -> (long) c.count()).sum();
                stats.put("totalRequests", total);
            } else {
                stats.put("totalRequests", 0);
            }

            // Slow requests
            var slowRequests = meterRegistry.find("http.requests.slow").counters();
            if (!slowRequests.isEmpty()) {
                long slowTotal = slowRequests.stream().mapToLong(c -> (long) c.count()).sum();
                stats.put("slowRequests", slowTotal);

                // Calcula % de slow requests
                long total = stats.containsKey("totalRequests") ? (long) stats.get("totalRequests") : 0;
                if (total > 0) {
                    double slowPercentage = (double) slowTotal / total * 100;
                    stats.put("slowPercentage", String.format("%.1f%%", slowPercentage));
                }
            } else {
                stats.put("slowRequests", 0);
            }

            // Conta exceptions
            var exceptions = meterRegistry.find("http.requests.exceptions").counters();
            if (!exceptions.isEmpty()) {
                long exceptionTotal = exceptions.stream().mapToLong(c -> (long) c.count()).sum();
                stats.put("exceptions", exceptionTotal);

                // Lista tipos de exceptions
                var exceptionTypes = exceptions.stream()
                        .collect(Collectors.groupingBy(
                                c -> c.getId().getTag("exception"),
                                Collectors.summingLong(c -> (long) c.count())
                        ));
                stats.put("exceptionsByType", exceptionTypes);
            } else {
                stats.put("exceptions", 0);
            }

        } catch (Exception e) {
            log.error("Error getting HTTP metrics", e);
            stats.put("error", e.getMessage());
        }

        return ResponseEntity.ok(stats);
    }

    /**
     * Limpa estatísticas do Hibernate
     */
    @PostMapping("/hibernate/clear")
    public ResponseEntity<Map<String, String>> clearHibernateStatistics() {
        try {
            SessionFactory sessionFactory = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class);
            sessionFactory.getStatistics().clear();

            return ResponseEntity.ok(Map.of("status", "success", "message", "Hibernate statistics cleared"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    /**
     * Limpa estatísticas de queries
     */
    @PostMapping("/queries/clear")
    public ResponseEntity<Map<String, String>> clearQueryStatistics() {
        queryInspector.clearStats();
        return ResponseEntity.ok(Map.of("status", "success", "message", "Query statistics cleared"));
    }

    /**
     * Histórico de requisições recentes (últimas 1000)
     */
    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> getRequestHistory(
            @RequestParam(defaultValue = "100") int limit) {
        Map<String, Object> response = new HashMap<>();

        var stats = historyTracker.getStats();
        response.put("stats", stats);

        var allRecords = historyTracker.getAll();
        // Pega as mais recentes primeiro
        var recentRecords = allRecords.stream()
                .sorted(Comparator.comparing(RequestHistoryTracker.RequestRecord::getTimestamp).reversed())
                .limit(limit)
                .collect(Collectors.toList());
        response.put("recentRequests", recentRecords);

        return ResponseEntity.ok(response);
    }

    /**
     * Top requisições mais lentas do histórico
     */
    @GetMapping("/history/slowest")
    public ResponseEntity<Map<String, Object>> getSlowestRequests(
            @RequestParam(defaultValue = "20") int limit) {
        Map<String, Object> response = new HashMap<>();

        var slowest = historyTracker.getSlowest(limit);
        response.put("slowest", slowest);
        response.put("count", slowest.size());

        return ResponseEntity.ok(response);
    }

    /**
     * Requisições que tiveram N+1 queries
     */
    @GetMapping("/history/nplus1")
    public ResponseEntity<Map<String, Object>> getRequestsWithNPlusOne() {
        Map<String, Object> response = new HashMap<>();

        var nPlusOneRequests = historyTracker.getWithNPlusOne();
        response.put("requests", nPlusOneRequests);
        response.put("count", nPlusOneRequests.size());

        return ResponseEntity.ok(response);
    }

    /**
     * Requisições lentas (acima do threshold)
     */
    @GetMapping("/history/slow")
    public ResponseEntity<Map<String, Object>> getSlowRequests(
            @RequestParam(defaultValue = "500") long thresholdMs) {
        Map<String, Object> response = new HashMap<>();

        var slowRequests = historyTracker.getSlowRequests(thresholdMs);
        response.put("requests", slowRequests);
        response.put("threshold", thresholdMs + "ms");
        response.put("count", slowRequests.size());

        return ResponseEntity.ok(response);
    }

    /**
     * Limpa histórico de requisições
     */
    @PostMapping("/history/clear")
    public ResponseEntity<Map<String, String>> clearRequestHistory() {
        historyTracker.clear();
        return ResponseEntity.ok(Map.of("status", "success", "message", "Request history cleared"));
    }

    /**
     * Health check customizado
     */
    @Override
    public Health health() {
        try {
            // Verifica se o Hibernate está funcionando
            SessionFactory sessionFactory = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class);
            boolean statsEnabled = sessionFactory.getStatistics().isStatisticsEnabled();

            return Health.up()
                    .withDetail("hibernateStats", statsEnabled ? "enabled" : "disabled")
                    .build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }

    // ==================== Utility Methods ====================

    private String truncateQuery(String query) {
        return query.length() > 150 ? query.substring(0, 150) + "..." : query;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.2f %sB", bytes / Math.pow(1024, exp), pre);
    }

    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) return days + "d " + (hours % 24) + "h";
        if (hours > 0) return hours + "h " + (minutes % 60) + "m";
        if (minutes > 0) return minutes + "m " + (seconds % 60) + "s";
        return seconds + "s";
    }
}
