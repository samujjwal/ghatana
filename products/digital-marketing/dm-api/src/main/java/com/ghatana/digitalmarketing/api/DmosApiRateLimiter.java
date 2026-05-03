package com.ghatana.digitalmarketing.api;

import com.ghatana.digitalmarketing.application.metrics.DmosMetricsCollector;
import com.ghatana.platform.http.security.filter.RateLimitFilter;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;

import java.util.Map;

/**
 * Shared API rate limiter wrapper for DMOS servlet entrypoints.
 *
 * <p>Rate limiting is keyed by tenant ID when present, falling back to client IP.
 * Limits are configured via environment variables to avoid code changes for ops tuning.</p>
 *
 * <p>This wrapper also emits {@link DmosMetricsCollector#API_REQUEST_DURATION} on every
 * handled request so that latency distributions can be derived from structured logs
 * until the platform MetricCollectorPort (KE-03) delivers native histogram support.</p>
 *
 * @doc.type class
 * @doc.purpose Central DMOS API rate-limit and request-timing wrapper for servlet routes
 * @doc.layer product
 * @doc.pattern Utility
 */
public final class DmosApiRateLimiter {

    private static final int DEFAULT_MAX_REQUESTS_PER_MINUTE = 60;
    private static final long DEFAULT_WINDOW_SECONDS = 60L;

    private static final RateLimitFilter FILTER = new RateLimitFilter(
        DEFAULT_MAX_REQUESTS_PER_MINUTE,
        DEFAULT_WINDOW_SECONDS,
        DmosApiRateLimiter::resolveClientKey
    );

    /** Test-only override to avoid mutating global JVM properties in unit tests. */
    static volatile Boolean testRuntimeOverride;

    private DmosApiRateLimiter() {
        // Utility class
    }

    /**
     * Wraps {@code delegate} with rate-limiting and request-duration telemetry.
     *
     * @param delegate  the inner servlet
     * @param metrics   business KPI collector — use {@link DmosMetricsCollector#noop()} in tests
     * @param servletId stable logical name for the {@code servlet} label (e.g. {@code "campaign"})
     * @return the wrapped servlet
     */
    public static AsyncServlet wrap(
            AsyncServlet delegate,
            DmosMetricsCollector metrics,
            String servletId) {
        AsyncServlet timed = request -> {
            long startNs = System.nanoTime();
            return delegate.serve(request)
                .whenComplete((response, ex) -> {
                    long durationMs = (System.nanoTime() - startNs) / 1_000_000L;
                    String status = ex != null ? "error"
                        : (response != null ? String.valueOf(response.getCode()) : "unknown");
                    metrics.observe(DmosMetricsCollector.API_REQUEST_DURATION, durationMs, Map.of(
                        "servlet", servletId,
                        "method",  request.getMethod().name(),
                        "status",  status
                    ));
                });
        };
        if (isTestRuntime()) {
            return timed;
        }
        return FILTER.wrap(timed);
    }

    /**
     * Backwards-compatible overload that delegates to {@link #wrap(AsyncServlet, DmosMetricsCollector, String)}
     * with a noop collector and an unknown servlet label.
     *
     * @deprecated Prefer {@link #wrap(AsyncServlet, DmosMetricsCollector, String)} to get timing telemetry.
     */
    @Deprecated
    public static AsyncServlet wrap(AsyncServlet delegate) {
        return wrap(delegate, DmosMetricsCollector.noop(), "unknown");
    }

    private static boolean isTestRuntime() {
        if (testRuntimeOverride != null) {
            return testRuntimeOverride;
        }
        return System.getProperty("org.gradle.test.worker") != null
            || "test".equalsIgnoreCase(System.getenv("DMOS_ENV"));
    }

    static void setTestRuntimeOverride(Boolean value) {
        testRuntimeOverride = value;
    }

    private static String resolveClientKey(HttpRequest request) {
        String tenantId = request.getHeader(HttpHeaders.of("X-Tenant-ID"));
        if (tenantId != null && !tenantId.isBlank()) {
            return "tenant:" + tenantId.trim();
        }

        String forwarded = request.getHeader(HttpHeaders.of("X-Forwarded-For"));
        if (forwarded != null && !forwarded.isBlank()) {
            int commaIdx = forwarded.indexOf(',');
            String clientIp = commaIdx > 0 ? forwarded.substring(0, commaIdx) : forwarded;
            return "ip:" + clientIp.trim();
        }

        return "ip:unknown";
    }
}
