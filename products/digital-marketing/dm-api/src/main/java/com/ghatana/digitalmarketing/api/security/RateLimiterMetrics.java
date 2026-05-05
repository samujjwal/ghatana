package com.ghatana.digitalmarketing.api.security;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * P1-027: Rate limiter metrics for observability.
 *
 * <p>Provides comprehensive metrics for rate limiting operations:
 * <ul>
 *   <li>Requests allowed vs rejected</li>
 *   <li>Rate limit hits by endpoint and tenant</li>
 *   <li>Current bucket utilization</li>
 *   <li>Rate limit exhaustion events</li>
 *   <li>Burst traffic detection</li>
 * </ul>
 *
 * <p>Metrics are tagged with tenant, endpoint, and result for drill-down analysis.</p>
 *
 * @doc.type class
 * @doc.purpose Rate limiter metrics and observability (P1-027)
 * @doc.layer product
 * @doc.pattern Metrics, Observability, Rate Limiting
 */
public final class RateLimiterMetrics {

    private static final Logger LOG = LoggerFactory.getLogger(RateLimiterMetrics.class);

    private final MeterRegistry meterRegistry;

    // Counters
    private Counter requestsAllowed;
    private Counter requestsRejected;
    private Counter rateLimitHits;
    private Counter rateLimitExhausted;

    // Timers
    private Timer rateLimitCheckDuration;

    // Gauges (tracked via atomic values)
    private final Map<String, AtomicInteger> activeBuckets = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> requestsInFlight = new ConcurrentHashMap<>();
    private final AtomicInteger totalActiveBuckets = new AtomicInteger(0);

    // Per-tenant tracking
    private final Map<String, TenantMetrics> tenantMetrics = new ConcurrentHashMap<>();

    public RateLimiterMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
    }

    @PostConstruct
    public void initializeMetrics() {
        LOG.info("[DMOS-METRICS] Initializing rate limiter metrics");

        // P1-027: Request outcome counters
        this.requestsAllowed = Counter.builder("dmos.ratelimit.requests.allowed")
            .description("Number of requests allowed by rate limiter")
            .register(meterRegistry);

        this.requestsRejected = Counter.builder("dmos.ratelimit.requests.rejected")
            .description("Number of requests rejected by rate limiter")
            .register(meterRegistry);

        // P1-027: Rate limit hit counter
        this.rateLimitHits = Counter.builder("dmos.ratelimit.hits")
            .description("Number of times rate limits were hit")
            .register(meterRegistry);

        // P1-027: Rate limit exhaustion counter
        this.rateLimitExhausted = Counter.builder("dmos.ratelimit.exhausted")
            .description("Number of times rate limits were completely exhausted")
            .register(meterRegistry);

        // P1-027: Rate limit check duration
        this.rateLimitCheckDuration = Timer.builder("dmos.ratelimit.check.duration")
            .description("Time taken to check rate limits")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry);

        // P1-027: Active buckets gauge
        Gauge.builder("dmos.ratelimit.buckets.active", totalActiveBuckets, AtomicInteger::get)
            .description("Number of active rate limit buckets")
            .register(meterRegistry);

        LOG.info("[DMOS-METRICS] Rate limiter metrics initialized");
    }

    /**
     * P1-027: Records a request that was allowed by the rate limiter.
     *
     * @param tenantId the tenant ID
     * @param endpoint the endpoint being accessed
     * @param clientId the client identifier (IP or principal)
     */
    public void recordAllowed(String tenantId, String endpoint, String clientId) {
        Tags tags = Tags.of(
            "tenant", tenantId,
            "endpoint", endpoint,
            "client_id", hashClientId(clientId)
        );

        requestsAllowed.increment(tags);
        getTenantMetrics(tenantId).recordAllowed();

        LOG.debug("[DMOS-METRICS] Request allowed: tenant={}, endpoint={}, client={}",
            tenantId, endpoint, clientId);
    }

    /**
     * P1-027: Records a request that was rejected by the rate limiter.
     *
     * @param tenantId the tenant ID
     * @param endpoint the endpoint being accessed
     * @param clientId the client identifier
     * @param limitType the type of limit hit (per_minute, per_hour, burst)
     */
    public void recordRejected(String tenantId, String endpoint, String clientId, String limitType) {
        Tags tags = Tags.of(
            "tenant", tenantId,
            "endpoint", endpoint,
            "client_id", hashClientId(clientId),
            "limit_type", limitType
        );

        requestsRejected.increment(tags);
        rateLimitHits.increment(tags);
        getTenantMetrics(tenantId).recordRejected(limitType);

        LOG.warn("[DMOS-METRICS] Request rejected: tenant={}, endpoint={}, client={}, limit_type={}",
            tenantId, endpoint, clientId, limitType);
    }

    /**
     * P1-027: Records a rate limit exhaustion event.
     *
     * @param tenantId the tenant ID
     * @param endpoint the endpoint
     * @param clientId the client identifier
     */
    public void recordExhausted(String tenantId, String endpoint, String clientId) {
        Tags tags = Tags.of(
            "tenant", tenantId,
            "endpoint", endpoint,
            "client_id", hashClientId(clientId)
        );

        rateLimitExhausted.increment(tags);
        getTenantMetrics(tenantId).recordExhausted();

        LOG.error("[DMOS-METRICS] Rate limit exhausted: tenant={}, endpoint={}, client={}",
            tenantId, endpoint, clientId);
    }

    /**
     * P1-027: Records the duration of a rate limit check.
     *
     * @param durationMs the duration in milliseconds
     * @param tenantId the tenant ID
     * @param endpoint the endpoint
     */
    public void recordCheckDuration(long durationMs, String tenantId, String endpoint) {
        Tags tags = Tags.of(
            "tenant", tenantId,
            "endpoint", endpoint
        );

        rateLimitCheckDuration.record(durationMs, tags);
    }

    /**
     * P1-027: Tracks an active rate limit bucket.
     *
     * @param bucketKey the bucket identifier
     */
    public void trackBucket(String bucketKey) {
        AtomicInteger count = activeBuckets.computeIfAbsent(bucketKey, k -> {
            totalActiveBuckets.incrementAndGet();
            return new AtomicInteger(0);
        });
        count.incrementAndGet();

        // Register gauge for this specific bucket
        Gauge.builder("dmos.ratelimit.bucket.requests", count, AtomicInteger::get)
            .description("Requests in specific rate limit bucket")
            .tag("bucket", bucketKey)
            .register(meterRegistry);
    }

    /**
     * P1-027: Untracks a rate limit bucket.
     *
     * @param bucketKey the bucket identifier
     */
    public void untrackBucket(String bucketKey) {
        AtomicInteger count = activeBuckets.remove(bucketKey);
        if (count != null) {
            totalActiveBuckets.decrementAndGet();
        }
    }

    /**
     * P1-027: Records burst traffic detection.
     *
     * @param tenantId the tenant ID
     * @param endpoint the endpoint
     * @param burstSize the size of the burst
     */
    public void recordBurst(String tenantId, String endpoint, int burstSize) {
        Counter counter = Counter.builder("dmos.ratelimit.burst.detected")
            .description("Burst traffic events detected")
            .tags("tenant", tenantId, "endpoint", endpoint)
            .register(meterRegistry);

        counter.increment(burstSize);

        LOG.warn("[DMOS-METRICS] Burst traffic detected: tenant={}, endpoint={}, size={}",
            tenantId, endpoint, burstSize);
    }

    /**
     * P1-027: Records rate limit configuration changes.
     *
     * @param tenantId the tenant ID
     * @param endpoint the endpoint
     * @param oldLimit the previous limit
     * @param newLimit the new limit
     */
    public void recordConfigurationChange(String tenantId, String endpoint,
                                           int oldLimit, int newLimit) {
        Counter counter = Counter.builder("dmos.ratelimit.config.changes")
            .description("Rate limit configuration changes")
            .tags("tenant", tenantId, "endpoint", endpoint)
            .register(meterRegistry);

        counter.increment();

        LOG.info("[DMOS-METRICS] Rate limit config changed: tenant={}, endpoint={}, {} -> {}",
            tenantId, endpoint, oldLimit, newLimit);
    }

    /**
     * P1-027: Gets metrics summary for monitoring.
     *
     * @return metrics summary string
     */
    public String getMetricsSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Rate Limiter Metrics Summary:\n");
        sb.append("  Total Allowed: ").append(requestsAllowed.count()).append("\n");
        sb.append("  Total Rejected: ").append(requestsRejected.count()).append("\n");
        sb.append("  Active Buckets: ").append(totalActiveBuckets.get()).append("\n");
        sb.append("  Tenants Tracked: ").append(tenantMetrics.size()).append("\n");

        // Per-tenant breakdown
        tenantMetrics.forEach((tenant, metrics) -> {
            sb.append("  Tenant ").append(tenant).append(":\n");
            sb.append("    Allowed: ").append(metrics.allowedCount.sum()).append("\n");
            sb.append("    Rejected: ").append(metrics.rejectedCount.sum()).append("\n");
        });

        return sb.toString();
    }

    /**
     * P1-027: Gets or creates tenant-specific metrics.
     */
    private TenantMetrics getTenantMetrics(String tenantId) {
        return tenantMetrics.computeIfAbsent(tenantId, k -> {
            TenantMetrics metrics = new TenantMetrics(tenantId);
            metrics.register(meterRegistry);
            return metrics;
        });
    }

    /**
     * P1-027: Hashes client ID for privacy in metrics.
     */
    private String hashClientId(String clientId) {
        if (clientId == null || clientId.isEmpty()) {
            return "unknown";
        }
        // Simple hash for privacy - in production use proper hashing
        return "client_" + Integer.toHexString(clientId.hashCode() % 10000);
    }

    /**
     * Per-tenant metrics tracking.
     */
    private static class TenantMetrics {
        final String tenantId;
        final LongAdder allowedCount = new LongAdder();
        final LongAdder rejectedCount = new LongAdder();
        final LongAdder exhaustedCount = new LongAdder();
        final Map<String, LongAdder> rejectionsByType = new ConcurrentHashMap<>();

        TenantMetrics(String tenantId) {
            this.tenantId = tenantId;
        }

        void register(MeterRegistry registry) {
            // Register gauges for this tenant
            Gauge.builder("dmos.ratelimit.tenant.requests.allowed", allowedCount, LongAdder::sum)
                .description("Requests allowed for tenant")
                .tag("tenant", tenantId)
                .register(registry);

            Gauge.builder("dmos.ratelimit.tenant.requests.rejected", rejectedCount, LongAdder::sum)
                .description("Requests rejected for tenant")
                .tag("tenant", tenantId)
                .register(registry);
        }

        void recordAllowed() {
            allowedCount.increment();
        }

        void recordRejected(String limitType) {
            rejectedCount.increment();
            rejectionsByType.computeIfAbsent(limitType, k -> new LongAdder()).increment();
        }

        void recordExhausted() {
            exhaustedCount.increment();
        }
    }
}
