package com.ghatana.platform.security.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ghatana.platform.observability.MetricsCollector;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Default keyed token-bucket {@link RateLimiter} implementation.
 *
 * <p>This implementation is thread-safe, uses Caffeine for bounded key retention,
 * and is suitable for per-process request throttling in shared services and platform APIs.
 * It intentionally avoids distributed coordination; callers needing cross-instance rate
 * limiting should front this with a shared store such as Redis/Dragonfly.</p>
 *
 * @doc.type class
 * @doc.purpose Default in-process token-bucket rate limiter for platform services
 * @doc.layer platform
 * @doc.pattern Component
 */
public final class DefaultRateLimiter implements RateLimiter {

    private static final long DEFAULT_MAX_KEYS = 10_000L;

    private final int maxRequestsPerMinute;
    private final int burstSize;
    private final long windowMillis;
    private final Cache<String, Bucket> buckets;
    private final MetricsCollector metrics;
    private final String metricPrefix;

    private DefaultRateLimiter(RateLimiterConfig config, MetricsCollector metrics, String metricPrefix) {
        this.maxRequestsPerMinute = config.getMaxRequestsPerMinute();
        this.burstSize = config.getBurstSize();
        this.windowMillis = config.getWindowDuration().toMillis();
        this.metrics = metrics;
        this.metricPrefix = metricPrefix;
        this.buckets = Caffeine.newBuilder()
                .maximumSize(DEFAULT_MAX_KEYS)
                .expireAfterAccess(Math.max(windowMillis * 2, TimeUnit.MINUTES.toMillis(2)), TimeUnit.MILLISECONDS)
                .build();
    }

    public static DefaultRateLimiter create(RateLimiterConfig config) {
        return new DefaultRateLimiter(config, null, null);
    }

    public static DefaultRateLimiter create(RateLimiterConfig config, MetricsCollector metrics, String metricPrefix) {
        Objects.requireNonNull(metrics, "metrics must not be null");
        Objects.requireNonNull(metricPrefix, "metricPrefix must not be null");
        return new DefaultRateLimiter(config, metrics, metricPrefix);
    }

    @Override
    public AcquireResult tryAcquire(String key) {
        Objects.requireNonNull(key, "key must not be null");

        long now = System.currentTimeMillis();
        Bucket bucket = buckets.get(key, ignored -> new Bucket(burstSize, now));

        synchronized (bucket) {
            double tokensToAdd = ((now - bucket.lastRefillMillis) / (double) windowMillis) * maxRequestsPerMinute;
            if (tokensToAdd > 0) {
                bucket.tokens = Math.min((double) burstSize, bucket.tokens + tokensToAdd);
                bucket.lastRefillMillis = now;
            }

            long resetAtEpochSeconds = now / 1000L + Math.max(1L, windowMillis / 1000L);
            if (bucket.tokens >= 1.0d) {
                bucket.tokens -= 1.0d;
                bucket.allowedCount.incrementAndGet();
                emitMetric("allowed", key);
                return new AcquireResult(true, (int) Math.floor(bucket.tokens), 0L, resetAtEpochSeconds);
            }

            bucket.rejectedCount.incrementAndGet();
            emitMetric("rejected", key);
            long retryAfterMillis = Math.max(
                    0L,
                    (long) Math.ceil((1.0d - bucket.tokens) * (windowMillis / (double) maxRequestsPerMinute))
            );
            long retryAfterSeconds = Math.max(1L, (long) Math.ceil(retryAfterMillis / 1000.0d));
            return new AcquireResult(false, 0, retryAfterSeconds, resetAtEpochSeconds);
        }
    }

    @Override
    public Stats getStats() {
        return new Stats() {
            @Override
            public long getTotalAllowed() {
                return buckets.asMap().values().stream().mapToLong(bucket -> bucket.allowedCount.get()).sum();
            }

            @Override
            public long getTotalRejected() {
                return buckets.asMap().values().stream().mapToLong(bucket -> bucket.rejectedCount.get()).sum();
            }
        };
    }

    public void reset(String key) {
        buckets.invalidate(key);
    }

    public void resetAll() {
        buckets.invalidateAll();
    }

    public int getApproximateRemainingTokens(String key) {
        Bucket bucket = buckets.getIfPresent(key);
        return bucket == null ? burstSize : Math.max(0, (int) Math.floor(bucket.tokens));
    }

    public long getTrackedKeyCount() {
        return buckets.estimatedSize();
    }

    public RateLimiterConfig getConfig() {
        return RateLimiterConfig.builder()
                .maxRequestsPerMinute(maxRequestsPerMinute)
                .burstSize(burstSize)
                .windowDuration(Duration.ofMillis(windowMillis))
                .build();
    }

    private void emitMetric(String outcome, String key) {
        if (metrics == null) {
            return;
        }
        metrics.incrementCounter(metricPrefix + "." + outcome, "key", key);
    }

    private static final class Bucket {
        private double tokens;
        private long lastRefillMillis;
        private final AtomicLong allowedCount = new AtomicLong(0);
        private final AtomicLong rejectedCount = new AtomicLong(0);

        private Bucket(int initialTokens, long now) {
            this.tokens = initialTokens;
            this.lastRefillMillis = now;
        }
    }
}