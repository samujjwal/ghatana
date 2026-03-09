package com.ghatana.platform.http.client;

import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.NoopMetricsCollector;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.google.common.util.concurrent.RateLimiter;

/**
 * Factory for creating configured OkHttpAdapter instances for use across the platform.
 *
 * <p><b>Purpose</b><br>
 * Centralizes default OkHttpClient and RateLimiter configuration so all modules share
 * consistent HTTP behavior (timeouts, connection pool, rate limiting).
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * MetricsCollector metrics = MetricsCollectorFactory.create(registry);
 * OkHttpAdapter adapter = HttpClientFactory.createDefaultAdapter(metrics);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Factory for OkHttpAdapter
 * @doc.layer core
 * @doc.pattern Factory
 */
public final class HttpClientFactory {

    private static final ConcurrentMap<String, RateLimiter> TENANT_LIMITERS = new ConcurrentHashMap<>();
    private static final Executor DEFAULT_EXECUTOR = Executors.newCachedThreadPool();

    private HttpClientFactory() {
    }

    /**
     * Create a default OkHttpAdapter with sensible timeouts and a shared RateLimiter.
     *
     * @param metrics MetricsCollector to record HTTP metrics (may be null)
     * @return OkHttpAdapter configured with default settings
     */
    public static OkHttpAdapter createDefaultAdapter(MetricsCollector metrics) {
        MetricsCollector m = metrics != null ? metrics : new NoopMetricsCollector();

        OkHttpClient client = new OkHttpClient.Builder()
                .callTimeout(Duration.ofSeconds(30))
                .connectTimeout(Duration.ofSeconds(10))
                .retryOnConnectionFailure(true)
                .connectionPool(new ConnectionPool(10, 5, TimeUnit.MINUTES))
                .build();

        // Default global rate limiter: 10 requests per second (tunable)
        RateLimiter rateLimiter = RateLimiter.create(10.0);

        return new OkHttpAdapter(client, rateLimiter, m, DEFAULT_EXECUTOR);
    }

    /**
     * Create or return an OkHttpAdapter scoped to a tenant.
     * Tenant adapters share the same OkHttpClient but have tenant-specific RateLimiters.
     *
     * @param tenantId Tenant identifier (non-null)
     * @param metrics MetricsCollector to record HTTP metrics (may be null)
     * @param requestsPerSecond Per-tenant request rate limit (if <=0 uses default 5 rps)
     * @return OkHttpAdapter configured for the tenant
     */
    public static OkHttpAdapter createTenantAdapter(String tenantId, MetricsCollector metrics, double requestsPerSecond) {
        Objects.requireNonNull(tenantId, "tenantId is required");
        MetricsCollector m = metrics != null ? metrics : new NoopMetricsCollector();

        double rps = requestsPerSecond > 0 ? requestsPerSecond : 5.0;

        RateLimiter limiter = TENANT_LIMITERS.computeIfAbsent(tenantId, id -> RateLimiter.create(rps));

        OkHttpClient client = new OkHttpClient.Builder()
                .callTimeout(Duration.ofSeconds(30))
                .connectTimeout(Duration.ofSeconds(10))
                .retryOnConnectionFailure(true)
                .connectionPool(new ConnectionPool(10, 5, TimeUnit.MINUTES))
                .build();

        return new OkHttpAdapter(client, limiter, m, DEFAULT_EXECUTOR);
    }
}
