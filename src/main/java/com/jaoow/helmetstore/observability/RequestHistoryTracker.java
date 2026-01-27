package com.jaoow.helmetstore.observability;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

/**
 * Rastreia histórico das últimas 1000 requisições HTTP em buffer circular na memória.
 */
@Slf4j
@Component
public class RequestHistoryTracker {

    private static final int MAX_HISTORY_SIZE = 1000;
    private final ConcurrentLinkedQueue<RequestRecord> history = new ConcurrentLinkedQueue<>();

    public void recordRequest(String method, String uri, int status, long durationMs,
                              int queryCount, boolean hadNPlusOne, int slowQueries) {

        var record = new RequestRecord(
                LocalDateTime.now(),
                method,
                uri,
                status,
                durationMs,
                queryCount,
                hadNPlusOne,
                slowQueries
        );

        history.offer(record);

        while (history.size() > MAX_HISTORY_SIZE) {
            history.poll();
        }
    }

    public List<RequestRecord> getAll() {
        return new ArrayList<>(history);
    }

    public List<RequestRecord> getSlowest(int limit) {
        return history.stream()
                .sorted(Comparator.comparingLong(RequestRecord::getDurationMs).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    public List<RequestRecord> getWithNPlusOne() {
        return history.stream()
                .filter(RequestRecord::isHadNPlusOne)
                .collect(Collectors.toList());
    }

    public List<RequestRecord> getSlowRequests(long thresholdMs) {
        return history.stream()
                .filter(r -> r.getDurationMs() > thresholdMs)
                .sorted(Comparator.comparingLong(RequestRecord::getDurationMs).reversed())
                .collect(Collectors.toList());
    }

    public void clear() {
        history.clear();
        log.info("Request history cleared");
    }

    public HistoryStats getStats() {
        var records = new ArrayList<>(history);

        if (records.isEmpty()) {
            return new HistoryStats(0, 0, 0, 0, 0, 0, 0);
        }

        long totalDuration = records.stream().mapToLong(RequestRecord::getDurationMs).sum();
        long avgDuration = totalDuration / records.size();
        long maxDuration = records.stream().mapToLong(RequestRecord::getDurationMs).max().orElse(0);
        long minDuration = records.stream().mapToLong(RequestRecord::getDurationMs).min().orElse(0);
        long slowCount = records.stream().filter(r -> r.getDurationMs() > 500).count();
        long nPlusOneCount = records.stream().filter(RequestRecord::isHadNPlusOne).count();

        return new HistoryStats(
                records.size(),
                avgDuration,
                maxDuration,
                minDuration,
                slowCount,
                nPlusOneCount,
                records.stream().mapToInt(RequestRecord::getQueryCount).sum()
        );
    }

    /**
     * Registro de uma requisição HTTP
     */
    @Data
    @AllArgsConstructor
    public static class RequestRecord {
        private LocalDateTime timestamp;
        private String method;
        private String uri;
        private int status;
        private long durationMs;
        private int queryCount;
        private boolean hadNPlusOne;
        private int slowQueries;
    }

    /**
     * Estatísticas agregadas do histórico
     */
    @Data
    @AllArgsConstructor
    public static class HistoryStats {
        private int totalRequests;
        private long avgDuration;
        private long maxDuration;
        private long minDuration;
        private long slowRequests;
        private long nPlusOneRequests;
        private long totalQueries;
    }
}
