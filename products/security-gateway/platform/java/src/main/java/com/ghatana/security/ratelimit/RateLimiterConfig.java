package com.ghatana.security.ratelimit;

import java.time.Duration;

/**
 * Configuration holder for RateLimiter.
 */
public final class RateLimiterConfig {
    private final int maxRequestsPerMinute;
    private final int burstSize;
    private final Duration windowDuration;

    private RateLimiterConfig(int maxRequestsPerMinute, int burstSize, Duration windowDuration) {
        this.maxRequestsPerMinute = maxRequestsPerMinute;
        this.burstSize = burstSize;
        this.windowDuration = windowDuration;
    }

    public int getMaxRequestsPerMinute() { return maxRequestsPerMinute; }
    public int getBurstSize() { return burstSize; }
    public Duration getWindowDuration() { return windowDuration; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private int maxRequestsPerMinute = 1000;
        private int burstSize = 100;
        private Duration windowDuration = Duration.ofMinutes(1);

        public Builder maxRequestsPerMinute(int v) { this.maxRequestsPerMinute = v; return this; }
        public Builder burstSize(int v) { this.burstSize = v; return this; }
        public Builder windowDuration(Duration d) { this.windowDuration = d; return this; }
        public RateLimiterConfig build() { return new RateLimiterConfig(maxRequestsPerMinute, burstSize, windowDuration); }
    }
}

