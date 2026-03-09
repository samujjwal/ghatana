package com.ghatana.security.ratelimit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Default token-bucket RateLimiter implementation.
 * <p>
 * This is a standalone implementation (not depending on Eventloop) used by
 * middleware and other server components when a production-rate-limiter is not
 * provided. It is thread-safe and approximates a token-bucket per-key.
 */
public final class DefaultRateLimiter implements RateLimiter {
    private final int maxRequestsPerMinute;
    private final int burstSize;
    private final long windowMillis;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    private DefaultRateLimiter(RateLimiterConfig config) {
        this.maxRequestsPerMinute = config.getMaxRequestsPerMinute();
        this.burstSize = config.getBurstSize();
        this.windowMillis = config.getWindowDuration().toMillis();
    }

    public static RateLimiter create(RateLimiterConfig config) {
        return new DefaultRateLimiter(config);
    }

    @Override
    public AcquireResult tryAcquire(String key) {
        long now = System.currentTimeMillis();
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(burstSize, now));
        synchronized (bucket) {
            // Refill tokens based on elapsed time
            double tokensToAdd = ((now - bucket.lastRefillMillis) / (double) windowMillis) * maxRequestsPerMinute;
            if (tokensToAdd > 0) {
                bucket.tokens = Math.min((double) burstSize, bucket.tokens + tokensToAdd);
                bucket.lastRefillMillis = now;
            }

            if (bucket.tokens >= 1.0) {
                bucket.tokens -= 1.0;
                bucket.allowedCount.incrementAndGet();
                return new AcquireResult(true, (int) Math.floor(bucket.tokens), 0L, now / 1000L + (windowMillis / 1000L));
            } else {
                bucket.rejectedCount.incrementAndGet();
                // approximate retryAfter as remaining millis until next token
                long retryAfter = Math.max(0L, (long) Math.ceil((1.0 - bucket.tokens) * (windowMillis / (double) maxRequestsPerMinute)));
                return new AcquireResult(false, 0, retryAfter / 1000L, now / 1000L + (windowMillis / 1000L));
            }
        }
    }

    @Override
    public Stats getStats() {
        return new Stats() {
            @Override
            public long getTotalAllowed() {
                return buckets.values().stream().mapToLong(b -> b.allowedCount.get()).sum();
            }

            @Override
            public long getTotalRejected() {
                return buckets.values().stream().mapToLong(b -> b.rejectedCount.get()).sum();
            }
        };
    }

    private static final class Bucket {
        double tokens;
        long lastRefillMillis;
        final AtomicLong allowedCount = new AtomicLong(0);
        final AtomicLong rejectedCount = new AtomicLong(0);

        Bucket(int initialTokens, long now) {
            this.tokens = initialTokens;
            this.lastRefillMillis = now;
        }
    }
}
