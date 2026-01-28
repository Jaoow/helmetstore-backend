package com.jaoow.helmetstore.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Filter HTTP que captura todas as requisições HTTP com métricas de performance.
 * Executa antes do Spring Security para capturar requisições autenticadas e não autenticadas.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HttpPerformanceFilter implements Filter {

    private final MeterRegistry meterRegistry;
    private final QueryPerformanceInspector queryInspector;
    private final RequestHistoryTracker historyTracker;

    private static final long SLOW_REQUEST_THRESHOLD_MS = 500;

    public HttpPerformanceFilter(MeterRegistry meterRegistry,
                                 QueryPerformanceInspector queryInspector,
                                 RequestHistoryTracker historyTracker) {
        this.meterRegistry = meterRegistry;
        this.queryInspector = queryInspector;
        this.historyTracker = historyTracker;
        log.info("✅ HttpPerformanceFilter initialized with HIGHEST_PRECEDENCE");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String uri = httpRequest.getRequestURI();
        String method = httpRequest.getMethod();

        if (uri.startsWith("/actuator/") || uri.startsWith("/webjars/") ||
            uri.startsWith("/swagger-ui") || uri.startsWith("/v3/api-docs")) {
            chain.doFilter(request, response);
            return;
        }

        long startTime = System.currentTimeMillis();
        queryInspector.startRequestTracking();
        log.info("🚀 Filter: Request started: {} {}", method, uri);

        Exception exception = null;
        try {
            chain.doFilter(request, response);
        } catch (Exception ex) {
            exception = ex;
            throw ex;
        } finally {
            recordMetrics(httpRequest, httpResponse, startTime, exception);
        }
    }

    private void recordMetrics(HttpServletRequest request, HttpServletResponse response,
                              long startTime, Exception exception) {
        try {
            long duration = System.currentTimeMillis() - startTime;
            String method = request.getMethod();
            String uri = request.getRequestURI();
            int status;

            try {
                status = response.getStatus();
            } catch (Exception e) {
                status = exception != null ? 500 : 200;
            }

            var queryAnalysis = queryInspector.endRequestTracking();
            Timer.builder("http.server.requests")
                    .tag("method", method)
                    .tag("uri", normalizeUri(uri))
                    .tag("status", String.valueOf(status))
                    .tag("exception", exception != null ? exception.getClass().getSimpleName() : "none")
                    .tag("outcome", determineOutcome(status))
                    .description("HTTP server requests")
                    .register(meterRegistry)
                    .record(duration, java.util.concurrent.TimeUnit.MILLISECONDS);

            meterRegistry.counter("http.requests.total",
                    "method", method,
                    "uri", normalizeUri(uri),
                    "status", String.valueOf(status)).increment();

            historyTracker.recordRequest(
                    method,
                    uri,
                    status,
                    duration,
                    queryAnalysis.getTotalQueries(),
                    queryAnalysis.isNPlusOneDetected(),
                    queryAnalysis.getSlowQueriesDetected()
            );

            String queryInfo = String.format("queries=%d, n+1=%s, slow=%d",
                    queryAnalysis.getTotalQueries(),
                    queryAnalysis.isNPlusOneDetected() ? "YES" : "no",
                    queryAnalysis.getSlowQueriesDetected());

            if (status >= 500) {
                log.error("❌ Request failed: {} {} - {}ms - status={} - {}",
                        method, uri, duration, status, queryInfo);
            } else if (duration > SLOW_REQUEST_THRESHOLD_MS || queryAnalysis.hasIssues()) {
                log.warn("⚠️ Slow request: {} {} - {}ms - status={} - {}",
                        method, uri, duration, status, queryInfo);
                meterRegistry.counter("http.requests.slow",
                        "method", method,
                        "uri", normalizeUri(uri)).increment();
            } else {
                log.info("✅ Request completed: {} {} - {}ms - status={} - {}",
                        method, uri, duration, status, queryInfo);
            }

            if (exception != null) {
                log.error("💥 Exception during request: {} {} - {}",
                        method, uri, exception.getClass().getSimpleName());
                meterRegistry.counter("http.requests.exceptions",
                        "method", method,
                        "uri", normalizeUri(uri),
                        "exception", exception.getClass().getSimpleName()).increment();
            }

        } catch (Exception e) {
            log.error("Error recording metrics in filter", e);
        }
    }

    private String normalizeUri(String uri) {
        return uri.replaceAll("/\\d+", "/{id}")
                  .replaceAll("/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", "/{uuid}");
    }

    private String determineOutcome(int status) {
        if (status >= 200 && status < 300) return "SUCCESS";
        if (status >= 300 && status < 400) return "REDIRECTION";
        if (status >= 400 && status < 500) return "CLIENT_ERROR";
        if (status >= 500) return "SERVER_ERROR";
        return "UNKNOWN";
    }
}
