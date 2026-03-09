/*
 * Copyright (c) 2025 Ghatana
 */
package com.ghatana.services.auth;

import com.ghatana.platform.observability.MetricsCollector;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rate limiter for API requests with per-tenant tracking.
 *
 * <p>
 * <b>Purpose</b><br>
 * Implements sliding window rate limiting to protect services from abuse.
 * Tracks requests per tenant and enforces configurable rate limits.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * RateLimiter limiter = new RateLimiter(100, metrics); // 100 req/min
 *
 * // Check if request allowed
 * if (limiter.allowRequest("tenant-123")) {
 *     // Process request
 * } else {
 *     // Reject with 429 Too Many Requests
 * }
 * }</pre>
 *
 * <p>
 * <b>Architecture Role</b><br>
 * Protection layer for all platform services. Prevents abuse and ensures fair
 * resource allocation across tenants.
 *
 * <p>
 * <b>Thread Safety</b><br>
 * Thread-safe - uses Guava Cache with atomic counters
 *
 * <p>
 * <b>Performance Characteristics</b><br>
 * - Check: O(1) via cache lookup - Memory: O(n) where n = number of active
 * tenants
 *
 * @doc.type class
 * @doc.purpose Per-tenant rate limiting
 * @doc.layer product
 * @doc.pattern Rate Limiter
 */
public class RateLimiter {

    private static final Logger LOGGER = LoggerFactory.getLogger(RateLimiter.class);

    private final int requestsPerMinute;
    private final MetricsCollector metrics;
    private final Cache<String, AtomicInteger> requestCounts;

    /**
     * Constructs rate limiter with configured limits.
     *
     * @param requestsPerMinute maximum requests per minute per tenant
     * @param metrics metrics collector
     */
    public RateLimiter(int requestsPerMinute, MetricsCollector metrics) {
        if (requestsPerMinute <= 0) {
            throw new IllegalArgumentException("requestsPerMinute must be positive");
        }

        this.requestsPerMinute = requestsPerMinute;
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");

        // Cache with 1-minute expiry for sliding window
        this.requestCounts = CacheBuilder.newBuilder()
                .expireAfterWrite(1, TimeUnit.MINUTES)
                .maximumSize(10_000) // Max 10k tenants tracked
                .build();

        LOGGER.info("Rate limiter initialized: limit={} requests/minute", requestsPerMinute);
    }

    /**
     * Checks if request is allowed for tenant.
     *
     * GIVEN: Tenant ID WHEN: allowRequest() is called THEN: True if within rate
     * limit, false if exceeded
     *
     * @param tenantId tenant identifier
     * @return true if request allowed, false if rate limit exceeded
     */
    public boolean allowRequest(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        AtomicInteger count = requestCounts.getIfPresent(tenantId);

        if (count == null) {
            // First request in window
            count = new AtomicInteger(1);
            requestCounts.put(tenantId, count);

            metrics.incrementCounter("rate.limiter.requests.allowed",
                    "tenant", tenantId);

            LOGGER.trace("First request in window for tenant: {}", tenantId);
            return true;
        }

        int currentCount = count.incrementAndGet();

        if (currentCount <= requestsPerMinute) {
            metrics.incrementCounter("rate.limiter.requests.allowed",
                    "tenant", tenantId);

            LOGGER.trace("Request allowed for tenant: {}, count={}/{}",
                    tenantId, currentCount, requestsPerMinute);
            return true;
        }

        // Rate limit exceeded
        metrics.incrementCounter("rate.limiter.requests.rejected",
                "tenant", tenantId);

        LOGGER.warn("Rate limit exceeded for tenant: {}, count={}/{}",
                tenantId, currentCount, requestsPerMinute);

        return false;
    }

    /**
     * Resets rate limit counter for tenant (for testing).
     *
     * @param tenantId tenant identifier
     */
    public void reset(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        requestCounts.invalidate(tenantId);
        LOGGER.debug("Reset rate limit for tenant: {}", tenantId);
    }

    /**
     * Resets all rate limit counters (for testing).
     */
    public void resetAll() {
        requestCounts.invalidateAll();
        LOGGER.debug("Reset all rate limits");
    }

    /**
     * Returns current request count for tenant.
     *
     * @param tenantId tenant identifier
     * @return current request count in window
     */
    public int getCurrentCount(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        AtomicInteger count = requestCounts.getIfPresent(tenantId);
        return count != null ? count.get() : 0;
    }

    /**
     * Returns configured rate limit.
     *
     * @return requests per minute limit
     */
    public int getLimit() {
        return requestsPerMinute;
    }

    /**
     * Returns number of tenants currently tracked.
     *
     * @return active tenant count
     */
    public long getActiveTenantCount() {
        return requestCounts.size();
    }
}
