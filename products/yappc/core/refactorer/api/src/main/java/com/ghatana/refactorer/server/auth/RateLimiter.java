package com.ghatana.refactorer.server.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Token bucket-based rate limiter implementation. Supports per-client rate limiting with

 * configurable limits and time windows.

 *

 * @doc.type class

 * @doc.purpose Enforce tenant-aware request limits to protect service stability.

 * @doc.layer product

 * @doc.pattern Rate Limiter

 */

public final class RateLimiter {
    private static final Logger logger = LogManager.getLogger(RateLimiter.class);

    private final Cache<String, TokenBucket> buckets;
    private final long defaultLimit;
    private final Duration defaultWindow;

    /**
     * Creates a new rate limiter with default settings.
     *
     * @param defaultLimit the default number of requests per window
     * @param defaultWindow the default time window
     */
    public RateLimiter(long defaultLimit, Duration defaultWindow) {
        this.defaultLimit = defaultLimit;
        this.defaultWindow = Objects.requireNonNull(defaultWindow);
        this.buckets =
                Caffeine.newBuilder()
                        .maximumSize(10_000)
                        .expireAfterAccess(defaultWindow.multipliedBy(2))
                        .build();

        logger.info(
                "Rate limiter initialized with default limit: {} requests per {}",
                defaultLimit,
                defaultWindow);
    }

    /**
     * Attempts to acquire a permit for the given client.
     *
     * @param clientId the client identifier
     * @return true if the request is allowed, false if rate limited
     */
    public boolean tryAcquire(String clientId) {
        return tryAcquire(clientId, defaultLimit, defaultWindow);
    }

    /**
     * Attempts to acquire a permit for the given client with custom limits.
     *
     * @param clientId the client identifier
     * @param limit the request limit
     * @param window the time window
     * @return true if the request is allowed, false if rate limited
     */
    public boolean tryAcquire(String clientId, long limit, Duration window) {
        Objects.requireNonNull(clientId, "Client ID cannot be null");
        Objects.requireNonNull(window, "Window cannot be null");

        if (limit <= 0) {
            logger.warn("Invalid rate limit {} for client {}, denying request", limit, clientId);
            return false;
        }

        TokenBucket bucket = buckets.get(clientId, k -> new TokenBucket(limit, window));
        boolean allowed = bucket.tryConsume();

        if (!allowed) {
            logger.debug("Rate limit exceeded for client: {}", clientId);
        }

        return allowed;
    }

    /**
     * Gets the current state for a client.
     *
     * @param clientId the client identifier
     * @return the rate limit state, or null if client has no state
     */
    public RateLimitState getState(String clientId) {
        Objects.requireNonNull(clientId, "Client ID cannot be null");

        TokenBucket bucket = buckets.getIfPresent(clientId);
        if (bucket == null) {
            return new RateLimitState(
                    defaultLimit, defaultLimit, Instant.now().plus(defaultWindow));
        }

        return bucket.getState();
    }

    /**
     * Resets the rate limit state for a client.
     *
     * @param clientId the client identifier
     */
    public void reset(String clientId) {
        Objects.requireNonNull(clientId, "Client ID cannot be null");
        buckets.invalidate(clientId);
        logger.debug("Reset rate limit state for client: {}", clientId);
    }

    /**
     * Gets the number of clients currently being tracked.
     *
     * @return the number of tracked clients
     */
    public long getTrackedClientCount() {
        return buckets.estimatedSize();
    }

    /**
 * Token bucket implementation for rate limiting. */
    private static final class TokenBucket {
        private final long capacity;
        private final Duration refillPeriod;
        private final AtomicLong tokens;
        private final AtomicReference<Instant> lastRefill;

        TokenBucket(long capacity, Duration refillPeriod) {
            this.capacity = capacity;
            this.refillPeriod = refillPeriod;
            this.tokens = new AtomicLong(capacity);
            this.lastRefill = new AtomicReference<>(Instant.now());
        }

        boolean tryConsume() {
            refill();

            long currentTokens = tokens.get();
            if (currentTokens > 0) {
                return tokens.compareAndSet(currentTokens, currentTokens - 1);
            }

            return false;
        }

        private void refill() {
            Instant now = Instant.now();
            Instant lastRefillTime = lastRefill.get();

            Duration timeSinceLastRefill = Duration.between(lastRefillTime, now);
            if (timeSinceLastRefill.compareTo(refillPeriod) >= 0) {
                // Time to refill
                if (lastRefill.compareAndSet(lastRefillTime, now)) {
                    tokens.set(capacity);
                }
            }
        }

        RateLimitState getState() {
            refill();
            long currentTokens = tokens.get();
            Instant nextRefill = lastRefill.get().plus(refillPeriod);
            return new RateLimitState(capacity, currentTokens, nextRefill);
        }
    }

    /**
 * Represents the current rate limit state for a client. */
    public static final class RateLimitState {
        private final long limit;
        private final long remaining;
        private final Instant resetTime;

        public RateLimitState(long limit, long remaining, Instant resetTime) {
            this.limit = limit;
            this.remaining = remaining;
            this.resetTime = resetTime;
        }

        public long getLimit() {
            return limit;
        }

        public long getRemaining() {
            return remaining;
        }

        public Instant getResetTime() {
            return resetTime;
        }

        public long getResetTimeSeconds() {
            return resetTime.getEpochSecond();
        }

        public boolean isExhausted() {
            return remaining <= 0;
        }

        @Override
        public String toString() {
            return String.format(
                    "RateLimitState{limit=%d, remaining=%d, resetTime=%s}",
                    limit, remaining, resetTime);
        }
    }

    /**
 * Configuration for rate limiting rules. */
    public static final class RateLimitConfig {
        private final long requestsPerWindow;
        private final Duration window;
        private final String description;

        public RateLimitConfig(long requestsPerWindow, Duration window, String description) {
            this.requestsPerWindow = requestsPerWindow;
            this.window = Objects.requireNonNull(window);
            this.description = Objects.requireNonNull(description);
        }

        public long getRequestsPerWindow() {
            return requestsPerWindow;
        }

        public Duration getWindow() {
            return window;
        }

        public String getDescription() {
            return description;
        }

        // Common rate limit configurations
        public static final RateLimitConfig STRICT =
                new RateLimitConfig(10, Duration.ofMinutes(1), "Strict");
        public static final RateLimitConfig MODERATE =
                new RateLimitConfig(100, Duration.ofMinutes(1), "Moderate");
        public static final RateLimitConfig LENIENT =
                new RateLimitConfig(1000, Duration.ofMinutes(1), "Lenient");
        public static final RateLimitConfig BURST =
                new RateLimitConfig(50, Duration.ofSeconds(10), "Burst Protection");
    }

    /**
 * Builder for creating rate limiters with custom configurations. */
    public static final class Builder {
        private long defaultLimit = 100;
        private Duration defaultWindow = Duration.ofMinutes(1);

        public Builder defaultLimit(long limit) {
            if (limit <= 0) {
                throw new IllegalArgumentException("Limit must be positive");
            }
            this.defaultLimit = limit;
            return this;
        }

        public Builder defaultWindow(Duration window) {
            this.defaultWindow = Objects.requireNonNull(window);
            return this;
        }

        public Builder defaultConfig(RateLimitConfig config) {
            Objects.requireNonNull(config);
            this.defaultLimit = config.getRequestsPerWindow();
            this.defaultWindow = config.getWindow();
            return this;
        }

        public RateLimiter build() {
            return new RateLimiter(defaultLimit, defaultWindow);
        }
    }

    /**
     * Creates a new rate limiter builder.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }
}
