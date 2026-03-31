package com.ghatana.yappc.infrastructure.resilience;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Circuit breaker pattern implementation for resilient service calls.
 *
 * <p><b>Purpose</b><br>
 * Prevents cascading failures by temporarily rejecting requests when a service
 * is experiencing high error rates. Automatically transitions between states
 * based on success/failure metrics.
 *
 * <p><b>States</b><br>
 * - CLOSED: Normal operation, requests pass through<br>
 * - OPEN: Failure threshold exceeded, requests fail fast<br>
 * - HALF_OPEN: Testing if service has recovered<br>
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * CircuitBreaker breaker = CircuitBreaker.builder()
 *     .failureThreshold(5)
 *     .recoveryTimeout(Duration.ofSeconds(30))
 *     .halfOpenMaxCalls(3)
 *     .build();
 *
 * // Wrap AI service call
 * Promise<Result> result = breaker.execute(() -> aiService.complete(request));
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Circuit breaker for fault tolerance
 * @doc.layer infrastructure
 * @doc.pattern Resilience, Circuit Breaker
 */
public final class CircuitBreaker {

    private static final Logger LOG = LoggerFactory.getLogger(CircuitBreaker.class);

    private final int failureThreshold;
    private final Duration recoveryTimeout;
    private final int halfOpenMaxCalls;
    private final double successThreshold;

    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger halfOpenCalls = new AtomicInteger(0);
    private final AtomicLong lastFailureTime = new AtomicLong(0);

    private CircuitBreaker(Builder builder) {
        this.failureThreshold = builder.failureThreshold;
        this.recoveryTimeout = builder.recoveryTimeout;
        this.halfOpenMaxCalls = builder.halfOpenMaxCalls;
        this.successThreshold = builder.successThreshold;
    }

    /**
     * Executes the given supplier with circuit breaker protection.
     *
     * @param supplier the operation to execute
     * @param <T> the result type
     * @return Promise of the operation result
     * @throws CircuitBreakerOpenException if circuit is OPEN
     */
    public <T> Promise<T> execute(Supplier<Promise<T>> supplier) {
        State currentState = state.get();

        switch (currentState) {
            case OPEN:
                if (shouldAttemptReset()) {
                    transitionTo(State.HALF_OPEN);
                    return executeHalfOpen(supplier);
                }
                LOG.debug("Circuit breaker OPEN - failing fast");
                return Promise.ofException(new CircuitBreakerOpenException(
                    "Service temporarily unavailable", getRemainingTimeout()));

            case HALF_OPEN:
                return executeHalfOpen(supplier);

            case CLOSED:
            default:
                return executeClosed(supplier);
        }
    }

    private <T> Promise<T> executeClosed(Supplier<Promise<T>> supplier) {
        try {
            return supplier.get()
                .whenResult(result -> {
                    // Success - reset failure count
                    failureCount.set(0);
                })
                .whenException(exception -> {
                    // Failure - increment count and check threshold
                    int failures = failureCount.incrementAndGet();
                    lastFailureTime.set(System.currentTimeMillis());
                    if (failures >= failureThreshold) {
                        LOG.warn("Circuit breaker threshold exceeded ({} failures), transitioning to OPEN", failures);
                        transitionTo(State.OPEN);
                    }
                });
        } catch (Exception e) {
            failureCount.incrementAndGet();
            lastFailureTime.set(System.currentTimeMillis());
            return Promise.ofException(e);
        }
    }

    private <T> Promise<T> executeHalfOpen(Supplier<Promise<T>> supplier) {
        int currentHalfOpenCalls = halfOpenCalls.incrementAndGet();
        if (currentHalfOpenCalls > halfOpenMaxCalls) {
            return Promise.ofException(new CircuitBreakerOpenException(
                "Half-open call limit exceeded", getRemainingTimeout()));
        }

        try {
            return supplier.get()
                .whenResult(result -> {
                    // Success in half-open - increment success count
                    int successes = successCount.incrementAndGet();
                    halfOpenCalls.decrementAndGet();
                    if (successes >= halfOpenMaxCalls * successThreshold) {
                        LOG.info("Circuit breaker recovered, transitioning to CLOSED");
                        transitionTo(State.CLOSED);
                    }
                })
                .whenException(exception -> {
                    // Failure in half-open - go back to OPEN
                    halfOpenCalls.decrementAndGet();
                    LOG.warn("Circuit breaker recovery failed, transitioning back to OPEN");
                    transitionTo(State.OPEN);
                });
        } catch (Exception e) {
            halfOpenCalls.decrementAndGet();
            transitionTo(State.OPEN);
            return Promise.ofException(e);
        }
    }

    private boolean shouldAttemptReset() {
        long lastFailure = lastFailureTime.get();
        return System.currentTimeMillis() - lastFailure >= recoveryTimeout.toMillis();
    }

    private Duration getRemainingTimeout() {
        long lastFailure = lastFailureTime.get();
        long elapsed = System.currentTimeMillis() - lastFailure;
        long remaining = Math.max(0, recoveryTimeout.toMillis() - elapsed);
        return Duration.ofMillis(remaining);
    }

    private void transitionTo(State newState) {
        State oldState = state.getAndSet(newState);
        if (oldState != newState) {
            LOG.info("Circuit breaker state transition: {} -> {}", oldState, newState);
            // Reset counters on state change
            failureCount.set(0);
            successCount.set(0);
            halfOpenCalls.set(0);
        }
    }

    /**
     * Returns current circuit breaker state.
     *
     * @return current state
     */
    public State getState() {
        return state.get();
    }

    /**
     * Manually reset circuit breaker to CLOSED state.
     */
    public void reset() {
        transitionTo(State.CLOSED);
        lastFailureTime.set(0);
        LOG.info("Circuit breaker manually reset to CLOSED");
    }

    /**
     * Circuit breaker states.
     */
    public enum State {
        CLOSED,     // Normal operation
        OPEN,       // Failing fast
        HALF_OPEN   // Testing recovery
    }

    /**
     * Creates a new builder for circuit breaker configuration.
     *
     * @return builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for CircuitBreaker.
     */
    public static final class Builder {
        private int failureThreshold = 5;
        private Duration recoveryTimeout = Duration.ofSeconds(30);
        private int halfOpenMaxCalls = 3;
        private double successThreshold = 0.5;

        private Builder() {}

        public Builder failureThreshold(int threshold) {
            this.failureThreshold = threshold;
            return this;
        }

        public Builder recoveryTimeout(Duration timeout) {
            this.recoveryTimeout = timeout;
            return this;
        }

        public Builder halfOpenMaxCalls(int maxCalls) {
            this.halfOpenMaxCalls = maxCalls;
            return this;
        }

        public Builder successThreshold(double threshold) {
            this.successThreshold = threshold;
            return this;
        }

        public CircuitBreaker build() {
            return new CircuitBreaker(this);
        }
    }
}
