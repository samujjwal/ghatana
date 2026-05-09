package com.ghatana.digitalmarketing.api;

import com.ghatana.digitalmarketing.application.metrics.DmosMetricsCollector;
import com.ghatana.platform.http.security.filter.RateLimitFilter;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;

import java.util.Map;

/**
 * Shared API rate limiter wrapper for DMOS servlet entrypoints.
 *
 * <p>Rate limiting is keyed by tenant ID when present, falling back to the first IP in
 * {@code X-Forwarded-For}, and ultimately to {@code "ip:unknown"}. Limits are read from
 * environment variables at first use so operators can tune them without code changes:</p>
 *
 * <ul>
 *   <li>{@code DMOS_RATE_LIMIT_MAX_REQUESTS} — max requests per window (default 60)</li>
 *   <li>{@code DMOS_RATE_LIMIT_WINDOW_SECONDS} — sliding window length in seconds (default 60)</li>
 * </ul>
 *
 * <p>The test-runtime bypass is gated <em>exclusively</em> on the Gradle test-worker system
 * property ({@code org.gradle.test.worker}). It cannot be activated through an environment
 * variable, preventing accidental disablement in production deployments.</p>
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

    static final int DEFAULT_MAX_REQUESTS = 60;
    static final long DEFAULT_WINDOW_SECONDS = 60L;

    /**
     * Env var name for the configurable request limit.
     * Value must be a positive integer; invalid or absent values fall back to {@link #DEFAULT_MAX_REQUESTS}.
     */
    static final String ENV_MAX_REQUESTS = "DMOS_RATE_LIMIT_MAX_REQUESTS";

    /**
     * Env var name for the configurable window in seconds.
     * Value must be a positive long; invalid or absent values fall back to {@link #DEFAULT_WINDOW_SECONDS}.
     */
    static final String ENV_WINDOW_SECONDS = "DMOS_RATE_LIMIT_WINDOW_SECONDS";

    /**
     * Lazily initialised filter so that env-var reads happen at application startup,
     * not at class-load time during test harness initialisation.
     */
    private static volatile RateLimitFilter filter;

    /** Test-only override to avoid mutating global JVM properties in unit tests. */
    static volatile Boolean testRuntimeOverride;

    private DmosApiRateLimiter() {
        // Utility class
    }

    /**
     * Wraps {@code delegate} with rate-limiting and request-duration telemetry.
     *
    * <p>The 429 response body uses the canonical DMOS error envelope.</p>
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
        return rateLimitWith429Envelope(getFilter().wrap(timed));
    }

    /**
     * Backwards-compatible overload that delegates to
     * {@link #wrap(AsyncServlet, DmosMetricsCollector, String)} with a noop collector
     * and an {@code "unknown"} servlet label.
     *
     * @deprecated Prefer {@link #wrap(AsyncServlet, DmosMetricsCollector, String)} to get
     *     timing telemetry and a meaningful servlet label in rate-limit metrics.
     */
    @Deprecated
    public static AsyncServlet wrap(AsyncServlet delegate) {
        return wrap(delegate, DmosMetricsCollector.noop(), "unknown");
    }

    // -------------------------------------------------------------------------
    // Package-private helpers — used by tests
    // -------------------------------------------------------------------------

    /** Returns the effective max-requests limit (env override or default). */
    static int getConfiguredMaxRequests() {
        return parsePositiveInt(System.getenv(ENV_MAX_REQUESTS), DEFAULT_MAX_REQUESTS);
    }

    /** Returns the effective window size in seconds (env override or default). */
    static long getConfiguredWindowSeconds() {
        return parsePositiveLong(System.getenv(ENV_WINDOW_SECONDS), DEFAULT_WINDOW_SECONDS);
    }

    static void setTestRuntimeOverride(Boolean value) {
        testRuntimeOverride = value;
    }

    // -------------------------------------------------------------------------
    // Private implementation
    // -------------------------------------------------------------------------

    /**
     * Returns the lazily initialised {@link RateLimitFilter}, reading env vars once at
     * first call.  Double-checked locking with a volatile field is safe in Java 5+.
     */
    private static RateLimitFilter getFilter() {
        if (filter == null) {
            synchronized (DmosApiRateLimiter.class) {
                if (filter == null) {
                    filter = new RateLimitFilter(
                        getConfiguredMaxRequests(),
                        getConfiguredWindowSeconds(),
                        DmosApiRateLimiter::resolveClientKey
                    );
                }
            }
        }
        return filter;
    }

    /**
     * Wraps a servlet so that any HTTP 429 response produced by the rate-limit filter
     * is replaced with a JSON error envelope for consistency with other DMOS error bodies.
     */
    private static AsyncServlet rateLimitWith429Envelope(AsyncServlet inner) {
        return request -> inner.serve(request).then((HttpResponse response) -> {
            if (response.getCode() == 429) {
                long windowSeconds = getConfiguredWindowSeconds();
                HttpResponse error = DmosApiErrorResponses.error(
                    429,
                    "Rate limit exceeded. Retry after " + windowSeconds + " seconds.",
                    request
                );
                return Promise.of(
                    HttpResponse.ofCode(429)
                        .withHeader(HttpHeaders.of("Content-Type"), "application/json")
                        .withHeader(HttpHeaders.of("Retry-After"), String.valueOf(windowSeconds))
                        .withHeader(HttpHeaders.of("X-Correlation-ID"), error.getHeader(HttpHeaders.of("X-Correlation-ID")))
                        .withBody(error.getBody())
                        .build()
                );
            }
            return Promise.of(response);
        });
    }

    /**
     * Test-runtime detection. Only the Gradle test-worker system property is checked —
     * no environment variable path exists, preventing accidental bypass in production.
     */
    private static boolean isTestRuntime() {
        if (testRuntimeOverride != null) {
            return testRuntimeOverride;
        }
        return System.getProperty("org.gradle.test.worker") != null;
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

    private static int parsePositiveInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static long parsePositiveLong(String value, long fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            long parsed = Long.parseLong(value.trim());
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    /** Resets the lazily initialised filter — test use only. */
    static void resetFilter() {
        filter = null;
    }
}
