package com.ghatana.platform.testing.chaos;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Context object that holds chaos test configuration and statistics.
 *
 * @doc.type class
 * @doc.purpose Holds runtime state for a chaos test execution
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public class ChaosContext {

    private final ChaosType chaosType;
    private final double failureProbability;
    private final long maxDurationMs;
    private final long startTime;
    private final AtomicInteger injectionCount;
    private final AtomicInteger failureCount;

    /**
     * Creates a new ChaosContext.
     *
     * @param chaosType          the type of chaos to inject
     * @param failureProbability probability of failure (0.0 to 1.0)
     * @param maxDurationMs      maximum duration for chaos injection
     */
    public ChaosContext(ChaosType chaosType, double failureProbability, long maxDurationMs) {
        this.chaosType = chaosType;
        this.failureProbability = Math.max(0.0, Math.min(1.0, failureProbability));
        this.maxDurationMs = maxDurationMs;
        this.startTime = System.currentTimeMillis();
        this.injectionCount = new AtomicInteger(0);
        this.failureCount = new AtomicInteger(0);
    }

    /**
     * Returns the type of chaos being injected.
     */
    public ChaosType getChaosType() {
        return chaosType;
    }

    /**
     * Returns the probability of failure injection.
     */
    public double getFailureProbability() {
        return failureProbability;
    }

    /**
     * Returns the maximum duration for chaos injection.
     */
    public long getMaxDurationMs() {
        return maxDurationMs;
    }

    /**
     * Checks if the chaos injection window is still active.
     */
    public boolean isActive() {
        return System.currentTimeMillis() - startTime < maxDurationMs;
    }

    /**
     * Returns the elapsed time since chaos injection started.
     */
    public long getElapsedMs() {
        return System.currentTimeMillis() - startTime;
    }

    /**
     * Records an injection attempt.
     */
    public void recordInjection() {
        injectionCount.incrementAndGet();
    }

    /**
     * Records a failure injection.
     */
    public void recordFailure() {
        failureCount.incrementAndGet();
    }

    /**
     * Returns the total number of injection attempts.
     */
    public int getInjectionCount() {
        return injectionCount.get();
    }

    /**
     * Returns the number of actual failures injected.
     */
    public int getFailureCount() {
        return failureCount.get();
    }

    /**
     * Determines if a failure should be injected based on probability.
     */
    public boolean shouldInjectFailure() {
        if (!isActive()) {
            return false;
        }
        recordInjection();
        boolean inject = Math.random() < failureProbability;
        if (inject) {
            recordFailure();
        }
        return inject;
    }
}
