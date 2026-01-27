package com.jaoow.helmetstore.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Interceptor de queries para detectar N+1 queries, queries lentas e analisar performance
 */
@Slf4j
@Component
public class QueryPerformanceInspector implements StatementInspector {

    private final MeterRegistry meterRegistry;
    private final ThreadLocal<QueryContext> queryContext = ThreadLocal.withInitial(QueryContext::new);
    private final ConcurrentHashMap<String, QueryStats> queryStatsMap = new ConcurrentHashMap<>();

    // Threshold para considerar uma query como "lenta" (em ms)
    private static final long SLOW_QUERY_THRESHOLD_MS = 100;

    // Threshold para N+1 - se mais de X queries similares em uma única requisição
    private static final int N_PLUS_ONE_THRESHOLD = 5;

    // Patterns para análise
    private static final Pattern SELECT_PATTERN = Pattern.compile("^\\s*select", Pattern.CASE_INSENSITIVE);
    private static final Pattern INSERT_PATTERN = Pattern.compile("^\\s*insert", Pattern.CASE_INSENSITIVE);
    private static final Pattern UPDATE_PATTERN = Pattern.compile("^\\s*update", Pattern.CASE_INSENSITIVE);
    private static final Pattern DELETE_PATTERN = Pattern.compile("^\\s*delete", Pattern.CASE_INSENSITIVE);

    public QueryPerformanceInspector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public String inspect(String sql) {
        QueryContext ctx = queryContext.get();
        long startTime = System.currentTimeMillis();
        ctx.incrementQueryCount();
        String normalizedQuery = normalizeQuery(sql);
        ctx.addQuery(normalizedQuery);
        updateQueryStats(normalizedQuery, startTime);
        String queryType = detectQueryType(sql);
        meterRegistry.counter("hibernate.queries.total", "type", queryType).increment();

        // Log de query (apenas em DEBUG)
        if (log.isDebugEnabled()) {
            log.debug("🔍 Executing {} query #{}: {}", queryType, ctx.getQueryCount(),
                    sql.length() > 100 ? sql.substring(0, 100) + "..." : sql);
        }

        return sql;
    }

    private String normalizeQuery(String sql) {
        return sql.replaceAll("\\?|'[^']*'|\\d+", "?")
                  .replaceAll("\\s+", " ")
                  .trim();
    }

    /**
     * Detecta o tipo de query
     */
    private String detectQueryType(String sql) {
        if (SELECT_PATTERN.matcher(sql).find()) return "SELECT";
        if (INSERT_PATTERN.matcher(sql).find()) return "INSERT";
        if (UPDATE_PATTERN.matcher(sql).find()) return "UPDATE";
        if (DELETE_PATTERN.matcher(sql).find()) return "DELETE";
        return "OTHER";
    }

    /**
     * Atualiza estatísticas da query
     */
    private void updateQueryStats(String normalizedQuery, long startTime) {
        QueryStats stats = queryStatsMap.computeIfAbsent(normalizedQuery, k -> new QueryStats());
        stats.incrementCount();

        long executionTime = System.currentTimeMillis() - startTime;
        stats.addExecutionTime(executionTime);

        // Registra no Micrometer
        Timer.builder("hibernate.query.execution")
                .tag("query_pattern", Integer.toString(normalizedQuery.hashCode()))
                .register(meterRegistry)
                .record(executionTime, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * Inicia contexto de monitoramento para uma requisição
     */
    public void startRequestTracking() {
        queryContext.get().reset();
    }

    /**
     * Finaliza contexto e detecta problemas (N+1, slow queries)
     */
    public QueryAnalysisResult endRequestTracking() {
        QueryContext ctx = queryContext.get();
        QueryAnalysisResult result = new QueryAnalysisResult();

        result.setTotalQueries(ctx.getQueryCount());
        result.setSlowQueriesDetected(detectSlowQueries());
        result.setNPlusOneDetected(detectNPlusOne(ctx));

        // Log de warning se detectar problemas
        if (result.hasIssues()) {
            log.warn("⚠️ Performance issues detected - Queries: {}, N+1: {}, Slow queries: {}",
                    result.getTotalQueries(),
                    result.isNPlusOneDetected(),
                    result.getSlowQueriesDetected());
        } else if (ctx.getQueryCount() > 0) {
            log.info("✅ Request completed - Total queries: {}", ctx.getQueryCount());
        }

        queryContext.remove();
        return result;
    }

    /**
     * Detecta N+1 queries analisando padrões de repetição
     */
    private boolean detectNPlusOne(QueryContext ctx) {
        // Obtém as queries já contabilizadas
        ConcurrentHashMap<String, Integer> queryCounts = ctx.getQueries();

        // Se algum padrão se repete mais que o threshold, é N+1
        for (var entry : queryCounts.entrySet()) {
            if (entry.getValue() >= N_PLUS_ONE_THRESHOLD) {
                log.warn("🚨 N+1 query detected! Pattern executed {} times: {}",
                        entry.getValue(),
                        entry.getKey().substring(0, Math.min(100, entry.getKey().length())));

                meterRegistry.counter("hibernate.queries.nplus1").increment();
                return true;
            }
        }

        return false;
    }

    /**
     * Detecta queries lentas baseado no threshold
     */
    private int detectSlowQueries() {
        int slowCount = 0;

        for (var entry : queryStatsMap.entrySet()) {
            QueryStats stats = entry.getValue();
            if (stats.getAverageExecutionTime() > SLOW_QUERY_THRESHOLD_MS) {
                slowCount++;

                if (log.isWarnEnabled()) {
                    log.warn("🐌 Slow query detected (avg: {}ms, count: {}): {}",
                            stats.getAverageExecutionTime(),
                            stats.getCount(),
                            entry.getKey().substring(0, Math.min(100, entry.getKey().length())));
                }

                meterRegistry.counter("hibernate.queries.slow",
                        "pattern", Integer.toString(entry.getKey().hashCode())).increment();
            }
        }

        return slowCount;
    }

    /**
     * Retorna estatísticas de todas as queries executadas
     */
    public ConcurrentHashMap<String, QueryStats> getQueryStats() {
        return queryStatsMap;
    }

    /**
     * Limpa estatísticas (útil para testes ou reset)
     */
    public void clearStats() {
        queryStatsMap.clear();
    }

    // ===================== Classes internas =====================

    /**
     * Contexto de queries por thread/requisição
     */
    private static class QueryContext {
        private final AtomicInteger queryCount = new AtomicInteger(0);
        private final ConcurrentHashMap<String, Integer> queries = new ConcurrentHashMap<>();

        void incrementQueryCount() {
            queryCount.incrementAndGet();
        }

        int getQueryCount() {
            return queryCount.get();
        }

        void addQuery(String normalizedQuery) {
            queries.merge(normalizedQuery, 1, Integer::sum);
        }

        ConcurrentHashMap<String, Integer> getQueries() {
            return queries;
        }

        void reset() {
            queryCount.set(0);
            queries.clear();
        }
    }

    /**
     * Estatísticas de uma query específica
     */
    public static class QueryStats {
        private final AtomicInteger count = new AtomicInteger(0);
        private long totalExecutionTime = 0;

        void incrementCount() {
            count.incrementAndGet();
        }

        void addExecutionTime(long time) {
            totalExecutionTime += time;
        }

        public int getCount() {
            return count.get();
        }

        public long getAverageExecutionTime() {
            return count.get() > 0 ? totalExecutionTime / count.get() : 0;
        }

        public long getTotalExecutionTime() {
            return totalExecutionTime;
        }
    }

    /**
     * Resultado da análise de queries de uma requisição
     */
    public static class QueryAnalysisResult {
        private int totalQueries;
        private boolean nPlusOneDetected;
        private int slowQueriesDetected;

        public boolean hasIssues() {
            return nPlusOneDetected || slowQueriesDetected > 0;
        }

        // Getters e Setters
        public int getTotalQueries() { return totalQueries; }
        public void setTotalQueries(int totalQueries) { this.totalQueries = totalQueries; }
        public boolean isNPlusOneDetected() { return nPlusOneDetected; }
        public void setNPlusOneDetected(boolean nPlusOneDetected) { this.nPlusOneDetected = nPlusOneDetected; }
        public int getSlowQueriesDetected() { return slowQueriesDetected; }
        public void setSlowQueriesDetected(int slowQueriesDetected) { this.slowQueriesDetected = slowQueriesDetected; }
    }
}
