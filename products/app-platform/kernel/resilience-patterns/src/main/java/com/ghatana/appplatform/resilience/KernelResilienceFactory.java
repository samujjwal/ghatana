/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.resilience;

import com.ghatana.platform.resilience.Bulkhead;
import com.ghatana.platform.resilience.CircuitBreaker;
import com.ghatana.platform.resilience.CircuitBreakerProfiles;
import com.ghatana.platform.resilience.RetryPolicy;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Factory that assembles pre-configured resilience composites for kernel services.
 *
 * <p>Each kernel service domain has a named preset that combines:
 * <ul>
 *   <li>{@link CircuitBreaker} — fault isolation (from platform:java:core)</li>
 *   <li>{@link Bulkhead} — concurrency limiting (from platform:java:core)</li>
 *   <li>{@link RetryPolicy} — retry with exponential backoff (from platform:java:core)</li>
 * </ul>
 *
 * <p>This factory does NOT re-implement the resilience algorithms; it composes
 * the platform primitives with domain-appropriate parameters for K-18.
 *
 * <p>Usage:
 * <pre>{@code
 * KernelResilienceFactory factory = KernelResilienceFactory.create();
 * Promise<TokenResponse> result = factory.ledgerTransact(eventloop,
 *     () -> ledgerService.postJournal(journal));
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose K-18 resilience composite factory for kernel service domains
 * @doc.layer product
 * @doc.pattern Factory
 */
public final class KernelResilienceFactory {

    private static final Logger log = LoggerFactory.getLogger(KernelResilienceFactory.class);

    // ─── Circuit Breakers (K18-001, K18-003) ────────────────────────────────
    private final CircuitBreaker ledgerBreaker;
    private final CircuitBreaker iamBreaker;
    private final CircuitBreaker calendarBreaker;
    private final CircuitBreaker secretsBreaker;

    // ─── Bulkheads (K18-004) ─────────────────────────────────────────────────
    private final Bulkhead ledgerBulkhead;
    private final Bulkhead iamBulkhead;
    private final Bulkhead calendarBulkhead;

    // ─── Retry policies (K18-006) ─────────────────────────────────────────────
    private final RetryPolicy ledgerRetry;
    private final RetryPolicy iamRetry;
    private final RetryPolicy calendarRetry;

    private KernelResilienceFactory(Builder builder) {
        // Ledger: STRICT — financial operations require low failure tolerance
        this.ledgerBreaker = CircuitBreakerProfiles.strict("kernel-ledger");
        this.ledgerBulkhead = Bulkhead.of("kernel-ledger", builder.ledgerBulkheadSize);
        this.ledgerRetry = RetryPolicy.builder()
                .maxRetries(2)
                .initialDelay(Duration.ofMillis(50))
                .maxDelay(Duration.ofMillis(500))
                .multiplier(2.0)
                .jitter(0.2)
                .build();

        // IAM: STRICT — authentication failures should not cascade
        this.iamBreaker = CircuitBreakerProfiles.strict("kernel-iam");
        this.iamBulkhead = Bulkhead.of("kernel-iam", builder.iamBulkheadSize);
        this.iamRetry = RetryPolicy.builder()
                .maxRetries(2)
                .initialDelay(Duration.ofMillis(100))
                .maxDelay(Duration.ofMillis(1000))
                .multiplier(2.0)
                .jitter(0.25)
                .build();

        // Calendar: STANDARD — reads only, degraded mode acceptable (no REVOKE ops)
        this.calendarBreaker = CircuitBreakerProfiles.standard("kernel-calendar");
        this.calendarBulkhead = Bulkhead.of("kernel-calendar", builder.calendarBulkheadSize);
        this.calendarRetry = RetryPolicy.builder()
                .maxRetries(3)
                .initialDelay(Duration.ofMillis(50))
                .maxDelay(Duration.ofMillis(300))
                .multiplier(1.5)
                .jitter(0.15)
                .build();

        // Secrets: STRICT — secret retrieval failures must not silently degrade
        this.secretsBreaker = CircuitBreakerProfiles.strict("kernel-secrets");
    }

    /**
     * Creates a factory with default bulkhead sizes.
     *
     * @return new {@code KernelResilienceFactory}
     */
    public static KernelResilienceFactory create() {
        return new Builder().build();
    }

    /**
     * Creates a factory builder for custom bulkhead sizes (K18-004).
     *
     * @return new Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    // ─── Ledger domain ───────────────────────────────────────────────────────

    /**
     * Executes a ledger operation protected by STRICT circuit breaker,
     * bulkhead, and retry (K18-001, K18-002, K18-004, K18-006).
     *
     * <p>Bulkhead is acquired synchronously before entering the async retry+CB chain.
     * Use {@link #ledgerTransact(Eventloop, Supplier, Supplier)} to provide a fallback
     * when the circuit is OPEN.
     *
     * @param eventloop ActiveJ eventloop
     * @param operation ledger operation to protect
     * @param <T> result type
     * @return promise for the operation result; fails with {@link Bulkhead.BulkheadFullException}
     *         if the bulkhead is saturated, or {@link com.ghatana.platform.resilience.CircuitBreakerOpenException}
     *         if the circuit is OPEN
     */
    public <T> Promise<T> ledgerTransact(Eventloop eventloop, Supplier<Promise<T>> operation) {
        return withBulkhead(ledgerBulkhead, eventloop,
                () -> ledgerRetry.execute(eventloop,
                        () -> ledgerBreaker.execute(eventloop, operation, null)));
    }

    /**
     * Executes a ledger operation with a fallback for OPEN circuit state (K18-002).
     *
     * @param eventloop ActiveJ eventloop
     * @param operation ledger operation
     * @param fallback  fallback supplier when circuit is OPEN
     * @param <T> result type
     * @return promise for the operation or fallback result
     */
    public <T> Promise<T> ledgerTransact(
            Eventloop eventloop,
            Supplier<Promise<T>> operation,
            Supplier<T> fallback) {
        return withBulkhead(ledgerBulkhead, eventloop,
                () -> ledgerRetry.execute(eventloop,
                        () -> ledgerBreaker.execute(eventloop, operation, fallback)));
    }

    // ─── IAM domain ──────────────────────────────────────────────────────────

    /**
     * Executes an IAM operation protected by STRICT circuit breaker, bulkhead, and retry.
     *
     * @param eventloop ActiveJ eventloop
     * @param operation IAM operation to protect
     * @param <T> result type
     * @return promise for the operation result
     */
    public <T> Promise<T> iamOperation(Eventloop eventloop, Supplier<Promise<T>> operation) {
        return withBulkhead(iamBulkhead, eventloop,
                () -> iamRetry.execute(eventloop,
                        () -> iamBreaker.execute(eventloop, operation, null)));
    }

    // ─── Calendar domain ─────────────────────────────────────────────────────

    /**
     * Executes a calendar operation protected by STANDARD circuit breaker, bulkhead, and retry.
     *
     * @param eventloop ActiveJ eventloop
     * @param operation calendar operation to protect
     * @param <T> result type
     * @return promise for the operation result
     */
    public <T> Promise<T> calendarQuery(Eventloop eventloop, Supplier<Promise<T>> operation) {
        return withBulkhead(calendarBulkhead, eventloop,
                () -> calendarRetry.execute(eventloop,
                        () -> calendarBreaker.execute(eventloop, operation, null)));
    }

    /**
     * Executes a calendar operation with a fallback for OPEN circuit state.
     *
     * @param eventloop ActiveJ eventloop
     * @param operation calendar operation
     * @param fallback  fallback supplier when circuit is OPEN
     * @param <T> result type
     * @return promise for the operation or fallback result
     */
    public <T> Promise<T> calendarQuery(
            Eventloop eventloop,
            Supplier<Promise<T>> operation,
            Supplier<T> fallback) {
        return withBulkhead(calendarBulkhead, eventloop,
                () -> calendarRetry.execute(eventloop,
                        () -> calendarBreaker.execute(eventloop, operation, fallback)));
    }

    // ─── Secrets domain ──────────────────────────────────────────────────────

    /**
     * Executes a secret retrieval protected by STRICT circuit breaker (K18-001).
     * No retry — secret misses should fail fast to avoid amplifying load on secret backend.
     *
     * @param eventloop ActiveJ eventloop
     * @param operation secret operation to protect
     * @param <T> result type
     * @return promise for the operation result
     */
    public <T> Promise<T> secretAccess(Eventloop eventloop, Supplier<Promise<T>> operation) {
        return secretsBreaker.execute(eventloop, operation, null);
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    /**
     * Wraps an async operation with a bulkhead guard (K18-004).
     *
     * <p>Checks availability via {@link Bulkhead#availablePermits()} before starting the
     * async operation. Uses {@link Bulkhead#tryExecuteBlocking(java.util.concurrent.Callable)}
     * which releases the permit after the supplier returns the Promise — this limits
     * the number of in-flight Promise initiations, preventing resource starvation
     * at the call-initiation layer.
     *
     * @param bulkhead  bulkhead to apply
     * @param eventloop eventloop (unused directly, passed through for clarity)
     * @param operation async operation supplier
     * @param <T>       result type
     * @return promise that fails with {@link Bulkhead.BulkheadFullException} if the bulkhead is full
     */
    private <T> Promise<T> withBulkhead(Bulkhead bulkhead, Eventloop eventloop, Supplier<Promise<T>> operation) {
        try {
            // tryExecuteBlocking acquires permit, runs the supplier (which starts the async pipeline),
            // then releases the permit. This bounds concurrent pipeline *initiations*, not completions.
            return bulkhead.tryExecuteBlocking(operation::get);
        } catch (Bulkhead.BulkheadFullException e) {
            log.warn("[kernel-resilience] bulkhead {} full — rejecting call", bulkhead.getName());
            return Promise.ofException(e);
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }

    // ─── State inspection (K18-001) ───────────────────────────────────────────

    /**
     * Returns the current state of the ledger circuit breaker (for health checks).
     *
     * @return CLOSED, OPEN, or HALF_OPEN
     */
    public CircuitBreaker.State ledgerCircuitState() {
        return ledgerBreaker.getState();
    }

    /**
     * Returns the current state of the IAM circuit breaker.
     *
     * @return CLOSED, OPEN, or HALF_OPEN
     */
    public CircuitBreaker.State iamCircuitState() {
        return iamBreaker.getState();
    }

    /**
     * Returns the current state of the calendar circuit breaker.
     *
     * @return CLOSED, OPEN, or HALF_OPEN
     */
    public CircuitBreaker.State calendarCircuitState() {
        return calendarBreaker.getState();
    }

    // ─── Bulkhead utilization (K18-011 monitoring) ────────────────────────────

    /**
     * Returns the fraction of ledger bulkhead slots in use (0.0 = empty, 1.0 = saturated).
     *
     * @return utilization ratio between 0.0 and 1.0
     */
    public double ledgerBulkheadUtilization() {
        int used = ledgerBulkhead.getMaxConcurrency() - ledgerBulkhead.availablePermits();
        return (double) used / ledgerBulkhead.getMaxConcurrency();
    }

    /**
     * Returns the fraction of IAM bulkhead slots in use (0.0 = empty, 1.0 = saturated).
     *
     * @return utilization ratio between 0.0 and 1.0
     */
    public double iamBulkheadUtilization() {
        int used = iamBulkhead.getMaxConcurrency() - iamBulkhead.availablePermits();
        return (double) used / iamBulkhead.getMaxConcurrency();
    }

    /**
     * Returns the fraction of calendar bulkhead slots in use (0.0 = empty, 1.0 = saturated).
     *
     * @return utilization ratio between 0.0 and 1.0
     */
    public double calendarBulkheadUtilization() {
        int used = calendarBulkhead.getMaxConcurrency() - calendarBulkhead.availablePermits();
        return (double) used / calendarBulkhead.getMaxConcurrency();
    }

    // ─── Builder (K18-003 profile customization) ──────────────────────────────

    /**
     * Builder for {@code KernelResilienceFactory} allowing bulkhead size overrides.
     *
     * @doc.type class
     * @doc.purpose Builder for KernelResilienceFactory (K18-003, K18-004)
     * @doc.layer product
     * @doc.pattern Builder
     */
    public static final class Builder {
        private int ledgerBulkheadSize = 50;
        private int iamBulkheadSize = 100;
        private int calendarBulkheadSize = 200;

        /**
         * Sets the ledger bulkhead max concurrency (default 50).
         *
         * @param size max concurrent ledger operations
         * @return this builder
         */
        public Builder ledgerBulkheadSize(int size) {
            if (size <= 0) throw new IllegalArgumentException("ledgerBulkheadSize must be > 0");
            this.ledgerBulkheadSize = size;
            return this;
        }

        /**
         * Sets the IAM bulkhead max concurrency (default 100).
         *
         * @param size max concurrent IAM operations
         * @return this builder
         */
        public Builder iamBulkheadSize(int size) {
            if (size <= 0) throw new IllegalArgumentException("iamBulkheadSize must be > 0");
            this.iamBulkheadSize = size;
            return this;
        }

        /**
         * Sets the calendar bulkhead max concurrency (default 200).
         *
         * @param size max concurrent calendar operations
         * @return this builder
         */
        public Builder calendarBulkheadSize(int size) {
            if (size <= 0) throw new IllegalArgumentException("calendarBulkheadSize must be > 0");
            this.calendarBulkheadSize = size;
            return this;
        }

        /**
         * Builds the {@code KernelResilienceFactory}.
         *
         * @return new factory instance
         */
        public KernelResilienceFactory build() {
            return new KernelResilienceFactory(this);
        }
    }
}
