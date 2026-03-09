/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.resilience;

import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Circuit breaker pattern for ActiveJ Promise-based operations.
 *
 * <p>States:
 * <ul>
 *   <li><b>CLOSED</b> — Normal operation. Failures counted; opens after threshold.</li>
 *   <li><b>OPEN</b> — All calls rejected immediately. Transitions to HALF_OPEN after reset timeout.</li>
 *   <li><b>HALF_OPEN</b> — One probe call allowed. Success → CLOSED; failure → OPEN.</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>
 * CircuitBreaker cb = CircuitBreaker.builder("my-service")
 *     .failureThreshold(5)
 *     .resetTimeout(Duration.ofSeconds(30))
 *     .successThreshold(2)
 *     .build();
 *
 * cb.execute(eventloop, () -&gt; callRemoteService())
 *     .whenResult(result -&gt; ...)
 *     .whenException(e -&gt; ...);
 * </pre>
 *
 * <p>Thread-safe via atomic operations. Designed for single-eventloop usage
 * but safe for concurrent access from blocking executors.</p>
 *
 * @doc.type class
 * @doc.purpose Circuit breaker for fault isolation in service calls
 * @doc.layer platform
 * @doc.pattern Service
 */
public final class CircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreaker.class);

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final String name;
    private final int failureThreshold;
    private final int successThreshold;
    private final Duration resetTimeout;
    private final Duration maxBackoff;
    private final double backoffMultiplier;

    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicLong lastFailureTimestamp = new AtomicLong(0);
    private final AtomicInteger consecutiveOpens = new AtomicInteger(0);

    // Metrics
    private final AtomicLong totalCalls = new AtomicLong(0);
    private final AtomicLong totalSuccesses = new AtomicLong(0);
    private final AtomicLong totalFailures = new AtomicLong(0);
    private final AtomicLong totalRejections = new AtomicLong(0);

    private CircuitBreaker(Builder builder) {
        this.name = Objects.requireNonNull(builder.name, "Circuit breaker name required");
        this.failureThreshold = builder.failureThreshold;
        this.successThreshold = builder.successThreshold;
        this.resetTimeout = builder.resetTimeout;
        this.maxBackoff = builder.maxBackoff;
        this.backoffMultiplier = builder.backoffMultiplier;
    }

    /**
     * Execute an operation through the circuit breaker.
     *
     * @param eventloop ActiveJ eventloop for scheduling half-open probes
     * @param operation the async operation to protect
     * @param <T>       result type
     * @return promise that completes with the operation result or fails with
     *         {@link CircuitBreakerOpenException} if the circuit is open
     */
    public <T> Promise<T> execute(Eventloop eventloop, Supplier<Promise<T>> operation) {
        totalCalls.incrementAndGet();

        State current = state.get();

        if (current == State.OPEN) {
            if (shouldTransitionToHalfOpen()) {
                if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                    log.info("[{}] circuit breaker: OPEN → HALF_OPEN (probe allowed)", name);
                    successCount.set(0);
                    return executeProbe(eventloop, operation);
                }
            }
            totalRejections.incrementAndGet();
            return Promise.ofException(new CircuitBreakerOpenException(name, effectiveResetTimeout()));
        }

        if (current == State.HALF_OPEN) {
            return executeProbe(eventloop, operation);
        }

        // CLOSED — normal execution
        return operation.get()
                .then(
                        result -> {
                            onSuccess();
                            return Promise.of(result);
                        },
                        error -> {
                            onFailure();
                            return Promise.ofException(error);
                        }
                );
    }

    /**
     * Reset the circuit breaker to CLOSED state, clearing all counters.
     */
    public void reset() {
        state.set(State.CLOSED);
        failureCount.set(0);
        successCount.set(0);
        consecutiveOpens.set(0);
        log.info("[{}] circuit breaker: manually reset to CLOSED", name);
    }

    public State getState() {
        return state.get();
    }

    public String getName() {
        return name;
    }

    public int getFailureCount() {
        return failureCount.get();
    }

    public long getTotalCalls() {
        return totalCalls.get();
    }

    public long getTotalSuccesses() {
        return totalSuccesses.get();
    }

    public long getTotalFailures() {
        return totalFailures.get();
    }

    public long getTotalRejections() {
        return totalRejections.get();
    }

    // ────────────────────────────────────────────────────────────────────

    private <T> Promise<T> executeProbe(Eventloop eventloop, Supplier<Promise<T>> operation) {
        return operation.get()
                .then(
                        result -> {
                            onProbeSuccess();
                            return Promise.of(result);
                        },
                        error -> {
                            onProbeFailure();
                            return Promise.ofException(error);
                        }
                );
    }

    private void onSuccess() {
        totalSuccesses.incrementAndGet();
        failureCount.set(0);
    }

    private void onFailure() {
        totalFailures.incrementAndGet();
        int failures = failureCount.incrementAndGet();
        if (failures >= failureThreshold) {
            if (state.compareAndSet(State.CLOSED, State.OPEN)) {
                lastFailureTimestamp.set(System.currentTimeMillis());
                consecutiveOpens.incrementAndGet();
                log.warn("[{}] circuit breaker: CLOSED → OPEN ({} failures, threshold {})",
                        name, failures, failureThreshold);
            }
        }
    }

    private void onProbeSuccess() {
        totalSuccesses.incrementAndGet();
        int successes = successCount.incrementAndGet();
        if (successes >= successThreshold) {
            if (state.compareAndSet(State.HALF_OPEN, State.CLOSED)) {
                failureCount.set(0);
                consecutiveOpens.set(0);
                log.info("[{}] circuit breaker: HALF_OPEN → CLOSED ({} consecutive successes)",
                        name, successes);
            }
        }
    }

    private void onProbeFailure() {
        totalFailures.incrementAndGet();
        if (state.compareAndSet(State.HALF_OPEN, State.OPEN)) {
            lastFailureTimestamp.set(System.currentTimeMillis());
            log.warn("[{}] circuit breaker: HALF_OPEN → OPEN (probe failed)", name);
        }
    }

    private boolean shouldTransitionToHalfOpen() {
        long elapsed = System.currentTimeMillis() - lastFailureTimestamp.get();
        return elapsed >= effectiveResetTimeout().toMillis();
    }

    /**
     * Exponential backoff for consecutive opens: resetTimeout × (multiplier ^ consecutiveOpens),
     * capped at maxBackoff.
     */
    private Duration effectiveResetTimeout() {
        int opens = consecutiveOpens.get();
        if (opens <= 1 || backoffMultiplier <= 1.0) {
            return resetTimeout;
        }
        double factor = Math.pow(backoffMultiplier, opens - 1);
        long effectiveMs = (long) (resetTimeout.toMillis() * factor);
        long capMs = maxBackoff.toMillis();
        return Duration.ofMillis(Math.min(effectiveMs, capMs));
    }

    // ────────────────────────────────────────────────────────────────────

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static final class Builder {
        private final String name;
        private int failureThreshold = 5;
        private int successThreshold = 2;
        private Duration resetTimeout = Duration.ofSeconds(30);
        private Duration maxBackoff = Duration.ofMinutes(5);
        private double backoffMultiplier = 2.0;

        private Builder(String name) {
            this.name = name;
        }

        public Builder failureThreshold(int threshold) {
            this.failureThreshold = threshold;
            return this;
        }

        public Builder successThreshold(int threshold) {
            this.successThreshold = threshold;
            return this;
        }

        public Builder resetTimeout(Duration timeout) {
            this.resetTimeout = timeout;
            return this;
        }

        public Builder maxBackoff(Duration maxBackoff) {
            this.maxBackoff = maxBackoff;
            return this;
        }

        public Builder backoffMultiplier(double multiplier) {
            this.backoffMultiplier = multiplier;
            return this;
        }

        public CircuitBreaker build() {
            return new CircuitBreaker(this);
        }
    }

    // ────────────────────────────────────────────────────────────────────

    /**
     * Thrown when a call is rejected because the circuit breaker is open.
     */
    public static final class CircuitBreakerOpenException extends RuntimeException {
        private final String circuitName;
        private final Duration retryAfter;

        public CircuitBreakerOpenException(String circuitName, Duration retryAfter) {
            super("Circuit breaker '" + circuitName + "' is OPEN. Retry after " + retryAfter);
            this.circuitName = circuitName;
            this.retryAfter = retryAfter;
        }

        public String getCircuitName() { return circuitName; }
        public Duration getRetryAfter() { return retryAfter; }
    }
}
