package com.ghatana.platform.http.client;

import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.NoopMetricsCollector;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
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

    /**
     * Bounded cache for per-tenant Guava RateLimiters.
     *
     * <p>Limiters are evicted 10 minutes after the last access, preventing
     * unbounded growth when many short-lived tenants use the service.
     * Maximum size is capped at 10,000 entries for additional safety.
     */
    private static final Cache<String, RateLimiter> TENANT_LIMITERS = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build();
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
        return createAdapter(HttpClientConfig.builder().build(), metrics);
    }

    /**
     * Create an OkHttpAdapter with custom HttpClientConfig.
     *
     * @param config HttpClientConfig with custom pool and timeout settings
     * @param metrics MetricsCollector to record HTTP metrics (may be null)
     * @return OkHttpAdapter configured with custom settings
     */
    public static OkHttpAdapter createAdapter(HttpClientConfig config, MetricsCollector metrics) {
        Objects.requireNonNull(config, "config is required");
        MetricsCollector m = metrics != null ? metrics : new NoopMetricsCollector();

        OkHttpClient client = new OkHttpClient.Builder()
                .callTimeout(config.getCallTimeout())
                .connectTimeout(config.getConnectTimeout())
                .readTimeout(config.getReadTimeout())
                .retryOnConnectionFailure(config.isRetryOnConnectionFailure())
                .connectionPool(new ConnectionPool(
                        config.getMaxConnections(),
                        config.getKeepAliveDuration().toMinutes(),
                        TimeUnit.MINUTES))
                .build();

        // Use configured requests per second or default to 10
        double rps = config.getRequestsPerSecond() > 0 ? config.getRequestsPerSecond() : 10.0;
        RateLimiter rateLimiter = RateLimiter.create(rps);

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

        RateLimiter limiter = TENANT_LIMITERS.get(tenantId, id -> RateLimiter.create(rps));

        OkHttpClient client = new OkHttpClient.Builder()
                .callTimeout(Duration.ofSeconds(30))
                .connectTimeout(Duration.ofSeconds(10))
                .retryOnConnectionFailure(true)
                .connectionPool(new ConnectionPool(10, 5, TimeUnit.MINUTES))
                .build();

        return new OkHttpAdapter(client, limiter, m, DEFAULT_EXECUTOR);
    }
}
