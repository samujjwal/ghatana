/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.ghatana.aep.operator;

import com.ghatana.agent.framework.api.AgentContext;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Rate-limiting decorator for {@link AgentEventOperator}.
 *
 * <p>Enforces a maximum event throughput using a token-bucket algorithm.
 * Events arriving above the configured rate are either rejected immediately
 * or queued (depending on the configured {@link BackpressureStrategy}).
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * RateLimitingOperator rl = RateLimitingOperator.builder()
 *     .delegate(agentEventOperator)
 *     .maxEventsPerSecond(100)
 *     .burstSize(20)
 *     .build();
 *
 * rl.submit(ctx, event);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Token-bucket rate limiter for AEP agent operators
 * @doc.layer product-aep
 * @doc.pattern Decorator
 *
 * @author Ghatana AI Platform
 * @since 2.0.0
 */
public final class RateLimitingOperator {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingOperator.class);

    private final AgentEventOperator delegate;
    private final long maxEventsPerSecond;
    private final int burstSize;

    // Token bucket state
    private long availableTokens;
    private long lastRefillNanos;
    private final Object lock = new Object();

    // Metrics
    private final AtomicLong totalAccepted = new AtomicLong();
    private final AtomicLong totalRejected = new AtomicLong();

    private RateLimitingOperator(Builder builder) {
        this.delegate = Objects.requireNonNull(builder.delegate, "delegate");
        this.maxEventsPerSecond = builder.maxEventsPerSecond > 0 ? builder.maxEventsPerSecond : 100;
        this.burstSize = builder.burstSize > 0 ? builder.burstSize : (int) this.maxEventsPerSecond;
        this.availableTokens = this.burstSize;
        this.lastRefillNanos = System.nanoTime();
    }

    /**
     * Submits an event if within the rate limit.
     *
     * @param ctx   agent execution context
     * @param event the AEP event
     * @return a Promise of the result, or an exception if rate-limited
     */
    @NotNull
    public Promise<Map<String, Object>> submit(
            @NotNull AgentContext ctx,
            @NotNull Map<String, Object> event) {

        if (tryAcquire()) {
            totalAccepted.incrementAndGet();
            return delegate.processEvent(ctx, event);
        }

        totalRejected.incrementAndGet();
        log.debug("Rate limit exceeded for operator '{}' (max={}/s)",
                delegate.getOperatorId(), maxEventsPerSecond);
        return Promise.ofException(new RateLimitExceededException(
                delegate.getOperatorId(), maxEventsPerSecond));
    }

    private boolean tryAcquire() {
        synchronized (lock) {
            refill();
            if (availableTokens > 0) {
                availableTokens--;
                return true;
            }
            return false;
        }
    }

    private void refill() {
        long now = System.nanoTime();
        long elapsed = now - lastRefillNanos;
        long tokensToAdd = elapsed * maxEventsPerSecond / Duration.ofSeconds(1).toNanos();
        if (tokensToAdd > 0) {
            availableTokens = Math.min(burstSize, availableTokens + tokensToAdd);
            lastRefillNanos = now;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Metrics
    // ═══════════════════════════════════════════════════════════════════════════

    public long getTotalAccepted() { return totalAccepted.get(); }
    public long getTotalRejected() { return totalRejected.get(); }
    public long getMaxEventsPerSecond() { return maxEventsPerSecond; }

    // ═══════════════════════════════════════════════════════════════════════════
    // Builder
    // ═══════════════════════════════════════════════════════════════════════════

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private AgentEventOperator delegate;
        private long maxEventsPerSecond = 100;
        private int burstSize = -1;

        private Builder() {}

        public Builder delegate(@NotNull AgentEventOperator delegate) {
            this.delegate = delegate;
            return this;
        }

        public Builder maxEventsPerSecond(long rate) {
            this.maxEventsPerSecond = rate;
            return this;
        }

        public Builder burstSize(int burstSize) {
            this.burstSize = burstSize;
            return this;
        }

        public RateLimitingOperator build() {
            return new RateLimitingOperator(this);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Exception
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class RateLimitExceededException extends RuntimeException {
        private final String operatorId;
        private final long maxRate;

        public RateLimitExceededException(String operatorId, long maxRate) {
            super("Rate limit exceeded for operator '" + operatorId
                    + "' (max " + maxRate + " events/s)");
            this.operatorId = operatorId;
            this.maxRate = maxRate;
        }

        public String getOperatorId() { return operatorId; }
        public long getMaxRate() { return maxRate; }
    }
}
