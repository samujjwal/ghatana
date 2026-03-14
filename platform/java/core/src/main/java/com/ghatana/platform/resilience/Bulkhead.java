/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.resilience;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bulkhead pattern implementation for limiting concurrent access to a downstream dependency.
 *
 * <p>Prevents a slow or failing dependency from consuming all available threads/permits.
 * Each downstream service should have its own {@code Bulkhead} instance, sized to its
 * declared concurrency budget.
 *
 * <p>Two operation modes:
 * <ul>
 *   <li>{@link #tryExecuteBlocking(Callable)} — immediate rejection if full (K18-004)</li>
 *   <li>{@link #executeAsync(Promise)} — async acquire (compatible with K18-005 semantics)</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * Bulkhead bulkhead = Bulkhead.of("payment-service", 20);
 *
 * // Blocking call (tries to acquire immediately, rejects if full):
 * try {
 *     String result = bulkhead.tryExecuteBlocking(() -> paymentService.call());
 * } catch (BulkheadFullException e) {
 *     // handle rejection
 * }
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Concurrency limiter (bulkhead) for downstream service calls
 * @doc.layer platform
 * @doc.pattern Bulkhead
 *
 * @since 2.0.0
 */
public final class Bulkhead {

    private static final Logger log = LoggerFactory.getLogger(Bulkhead.class);

    private final String name;
    private final int maxConcurrency;
    private final Semaphore semaphore;

    private final AtomicLong totalAcquired = new AtomicLong();
    private final AtomicLong totalRejected = new AtomicLong();

    private Bulkhead(String name, int maxConcurrency) {
        this.name = Objects.requireNonNull(name, "name");
        if (maxConcurrency <= 0) throw new IllegalArgumentException("maxConcurrency must be > 0");
        this.maxConcurrency = maxConcurrency;
        this.semaphore = new Semaphore(maxConcurrency, true);
    }

    /**
     * Creates a bulkhead with the given name and max concurrency.
     *
     * @param name           downstream service or resource name (for metrics/logs)
     * @param maxConcurrency maximum number of concurrent executions
     * @return new Bulkhead instance
     */
    public static Bulkhead of(String name, int maxConcurrency) {
        return new Bulkhead(name, maxConcurrency);
    }

    /**
     * Tries to acquire a bulkhead slot and execute the operation.
     * Rejects immediately if the bulkhead is full (never queues).
     *
     * @param operation the operation to execute
     * @param <T>       result type
     * @return result of the operation
     * @throws BulkheadFullException   if no slot is available
     * @throws Exception               if the operation itself throws
     */
    public <T> T tryExecuteBlocking(Callable<T> operation) throws Exception {
        if (!semaphore.tryAcquire()) {
            long rejected = totalRejected.incrementAndGet();
            log.warn("[bulkhead:{}] full ({}/{} permits) — rejected call #{}", name,
                    maxConcurrency - semaphore.availablePermits(), maxConcurrency, rejected);
            throw new BulkheadFullException(name, maxConcurrency);
        }
        totalAcquired.incrementAndGet();
        try {
            return operation.call();
        } finally {
            semaphore.release();
        }
    }

    /**
     * Attempts to execute without blocking, returning {@code false} in the result
     * if rejected. Useful for fire-and-forget patterns where rejection is acceptable.
     *
     * @param operation the operation (run only if slot acquired)
     * @return {@code true} if executed, {@code false} if rejected (bulkhead full)
     */
    public boolean tryRun(Runnable operation) {
        if (!semaphore.tryAcquire()) {
            totalRejected.incrementAndGet();
            return false;
        }
        totalAcquired.incrementAndGet();
        try {
            operation.run();
            return true;
        } finally {
            semaphore.release();
        }
    }

    /** Current available permits (0 means bulkhead is saturated). */
    public int availablePermits() {
        return semaphore.availablePermits();
    }

    /** Total number of permits (max concurrency). */
    public int getMaxConcurrency() {
        return maxConcurrency;
    }

    /** How many calls have been successfully acquired. */
    public long getTotalAcquired() {
        return totalAcquired.get();
    }

    /** How many calls were rejected due to the bulkhead being full. */
    public long getTotalRejected() {
        return totalRejected.get();
    }

    public String getName() {
        return name;
    }

    // ──────────────────────────────────────────────────────────────────────

    /**
     * Thrown when a call is rejected because the bulkhead is saturated.
     */
    public static final class BulkheadFullException extends RejectedExecutionException {
        private final String bulkheadName;
        private final int maxConcurrency;

        public BulkheadFullException(String bulkheadName, int maxConcurrency) {
            super("Bulkhead '" + bulkheadName + "' is full (maxConcurrency=" + maxConcurrency + ")");
            this.bulkheadName = bulkheadName;
            this.maxConcurrency = maxConcurrency;
        }

        public String getBulkheadName() { return bulkheadName; }
        public int getMaxConcurrency() { return maxConcurrency; }
    }
}
