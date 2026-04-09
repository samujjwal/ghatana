/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.ratelimit;

import com.ghatana.platform.security.ratelimit.DefaultRateLimiter;
import com.ghatana.platform.security.ratelimit.RateLimiterConfig;

import java.time.Duration;
import java.util.Objects;

/**
 * AEP-facing rate limiter for tenant-scoped event processing (AEP-022).
 *
 * <p>This class wraps the platform token-bucket rate limiter so the engine can
 * throttle per-tenant ingestion without depending directly on platform-specific
 * APIs in the rest of the processing flow.
 *
 * @doc.type class
 * @doc.purpose Tenant-scoped event processing rate limiter for the AEP engine
 * @doc.layer product
 * @doc.pattern Facade
 */
public final class AepRateLimiter {

    public static final String ENABLED_KEY = "rateLimitEnabled";
    public static final String MAX_REQUESTS_PER_MINUTE_KEY = "rateLimitMaxRequestsPerMinute";
    public static final String BURST_SIZE_KEY = "rateLimitBurstSize";
    public static final String WINDOW_SECONDS_KEY = "rateLimitWindowSeconds";

    private final boolean enabled;
    private final DefaultRateLimiter delegate;

    private AepRateLimiter(Builder builder) {
        this.enabled = builder.enabled;
        this.delegate = builder.enabled
            ? DefaultRateLimiter.create(RateLimiterConfig.builder()
                .maxRequestsPerMinute(builder.maxRequestsPerMinute)
                .burstSize(builder.burstSize)
                .windowDuration(builder.windowDuration)
                .build())
            : null;
    }

    /**
     * Attempts to acquire permission for the tenant's next event.
     *
     * @param tenantId tenant identifier
     * @return decision containing allowance and retry metadata
     */
    public RateLimitDecision tryAcquire(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        if (!enabled) {
            return new RateLimitDecision(true, Integer.MAX_VALUE, 0L, 0L);
        }

        var result = delegate.tryAcquire(tenantId);
        return new RateLimitDecision(
            result.allowed(),
            result.remainingTokens(),
            result.retryAfterSeconds(),
            result.resetAtEpochSeconds()
        );
    }

    /**
     * Clears the tenant's bucket state.
     *
     * @param tenantId tenant identifier
     */
    public void reset(String tenantId) {
        if (!enabled) {
            return;
        }
        delegate.reset(tenantId);
    }

    /**
     * Clears all tracked rate-limit state.
     */
    public void resetAll() {
        if (!enabled) {
            return;
        }
        delegate.resetAll();
    }

    /**
     * @return whether rate limiting is enabled
     */
    public boolean enabled() {
        return enabled;
    }

    /**
     * @return builder for an AEP rate limiter
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * @return disabled rate limiter that always allows requests
     */
    public static AepRateLimiter disabled() {
        return builder().enabled(false).build();
    }

    /**
     * Result of a tenant-scoped acquisition attempt.
     */
    public record RateLimitDecision(
        boolean allowed,
        int remainingTokens,
        long retryAfterSeconds,
        long resetAtEpochSeconds
    ) {
    }

    /**
     * Builder for {@link AepRateLimiter}.
     */
    public static final class Builder {
        private boolean enabled = false;
        private int maxRequestsPerMinute = 10_000;
        private int burstSize = 1_000;
        private Duration windowDuration = Duration.ofMinutes(1);

        private Builder() {
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder maxRequestsPerMinute(int maxRequestsPerMinute) {
            this.maxRequestsPerMinute = maxRequestsPerMinute;
            return this;
        }

        public Builder burstSize(int burstSize) {
            this.burstSize = burstSize;
            return this;
        }

        public Builder windowDuration(Duration windowDuration) {
            this.windowDuration = Objects.requireNonNull(windowDuration, "windowDuration must not be null");
            return this;
        }

        public AepRateLimiter build() {
            return new AepRateLimiter(this);
        }
    }
}
