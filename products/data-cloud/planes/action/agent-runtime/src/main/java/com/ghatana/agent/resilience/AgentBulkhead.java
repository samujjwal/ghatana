/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.ghatana.agent.resilience;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Semaphore-based bulkhead that limits the number of concurrent agent invocations.
 *
 * <p>Prevents a single runaway agent from consuming all system threads/resources.
 * Each agent should have its own {@code AgentBulkhead} instance, sized according
 * to its declared {@code max_concurrency} in the agent descriptor.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * AgentBulkhead bulkhead = AgentBulkhead.of("my-agent", 20);
 * if (bulkhead.tryAcquire()) {
 *     try {
 *         agent.process(ctx, input);
 *     } finally {
 *         bulkhead.release();
 *     }
 * } else {
 *     // reject or queue
 * }
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Concurrency limiter (bulkhead) for TypedAgent invocations
 * @doc.layer platform
 * @doc.pattern Bulkhead
 *
 * @author Ghatana AI Platform
 * @since 2.0.0
 */
public final class AgentBulkhead {

    private final String agentId;
    private final int maxConcurrency;
    private final Semaphore semaphore;

    private final AtomicLong totalAcquired = new AtomicLong();
    private final AtomicLong totalRejected = new AtomicLong();

    private AgentBulkhead(String agentId, int maxConcurrency) {
        this.agentId = Objects.requireNonNull(agentId, "agentId");
        if (maxConcurrency <= 0) throw new IllegalArgumentException("maxConcurrency must be > 0");
        this.maxConcurrency = maxConcurrency;
        this.semaphore = new Semaphore(maxConcurrency, true);
    }

    /**
     * Creates a bulkhead for the given agent with the given max concurrency.
     *
     * @param agentId        agent identifier (for metrics/logging)
     * @param maxConcurrency maximum number of concurrent invocations
     * @return new AgentBulkhead instance
     */
    public static AgentBulkhead of(@NotNull String agentId, int maxConcurrency) {
        return new AgentBulkhead(agentId, maxConcurrency);
    }

    /**
     * Attempts to acquire a slot. Returns {@code true} if acquired (and a
     * corresponding {@link #release()} must be called), {@code false} if full.
     *
     * @return true if a slot was acquired
     */
    public boolean tryAcquire() {
        boolean acquired = semaphore.tryAcquire();
        if (acquired) {
            totalAcquired.incrementAndGet();
        } else {
            totalRejected.incrementAndGet();
        }
        return acquired;
    }

    /**
     * Releases a previously acquired slot. Must be called exactly once per
     * successful {@link #tryAcquire()}.
     */
    public void release() {
        semaphore.release();
    }

    /**
     * Returns {@code true} if no slots are available (all in use).
     */
    public boolean isExhausted() {
        return semaphore.availablePermits() == 0;
    }

    /**
     * Returns the number of currently available (free) slots.
     */
    public int availableSlots() {
        return semaphore.availablePermits();
    }

    /**
     * Returns the configured maximum concurrency.
     */
    public int getMaxConcurrency() {
        return maxConcurrency;
    }

    /**
     * Returns the agent ID this bulkhead is associated with.
     */
    public String getAgentId() {
        return agentId;
    }

    /**
     * Returns total successful acquires since creation.
     */
    public long getTotalAcquired() {
        return totalAcquired.get();
    }

    /**
     * Returns total rejected (bulkhead-full) calls since creation.
     */
    public long getTotalRejected() {
        return totalRejected.get();
    }

    /**
     * Returns the current utilization ratio [0.0, 1.0].
     */
    public double getUtilization() {
        int inUse = maxConcurrency - semaphore.availablePermits();
        return (double) inUse / maxConcurrency;
    }

    @Override
    public String toString() {
        return "AgentBulkhead{agentId='" + agentId
                + "', max=" + maxConcurrency
                + ", available=" + semaphore.availablePermits()
                + ", acquired=" + totalAcquired.get()
                + ", rejected=" + totalRejected.get() + '}';
    }
}
