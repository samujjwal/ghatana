package com.ghatana.platform.security.ratelimit;

import java.time.Duration;
import java.util.Objects;

/**
 * @doc.type class
 * @doc.purpose Immutable configuration for keyed rate limiters
 * @doc.layer platform
 * @doc.pattern Configuration Object
 */
public final class RateLimiterConfig {

    private final int maxRequestsPerMinute;
    private final int burstSize;
    private final Duration windowDuration;

    private RateLimiterConfig(int maxRequestsPerMinute, int burstSize, Duration windowDuration) {
        if (maxRequestsPerMinute <= 0) {
            throw new IllegalArgumentException("maxRequestsPerMinute must be positive");
        }
        if (burstSize <= 0) {
            throw new IllegalArgumentException("burstSize must be positive");
        }
        this.maxRequestsPerMinute = maxRequestsPerMinute;
        this.burstSize = burstSize;
        this.windowDuration = Objects.requireNonNull(windowDuration, "windowDuration must not be null");
        if (windowDuration.isZero() || windowDuration.isNegative()) {
            throw new IllegalArgumentException("windowDuration must be positive");
        }
    }

    public int getMaxRequestsPerMinute() {
        return maxRequestsPerMinute;
    }

    public int getBurstSize() {
        return burstSize;
    }

    public Duration getWindowDuration() {
        return windowDuration;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * @doc.type class
     * @doc.purpose Fluent builder for RateLimiterConfig
     * @doc.layer platform
     * @doc.pattern Builder
     */
    public static final class Builder {
        private int maxRequestsPerMinute = 1_000;
        private int burstSize = 100;
        private Duration windowDuration = Duration.ofMinutes(1);

        public Builder maxRequestsPerMinute(int value) {
            this.maxRequestsPerMinute = value;
            return this;
        }

        public Builder burstSize(int value) {
            this.burstSize = value;
            return this;
        }

        public Builder windowDuration(Duration value) {
            this.windowDuration = value;
            return this;
        }

        public RateLimiterConfig build() {
            return new RateLimiterConfig(maxRequestsPerMinute, burstSize, windowDuration);
        }
    }
}