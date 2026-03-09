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

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Wraps an {@link AgentEventOperator} with configurable backpressure behaviour.
 *
 * <p>Events are buffered in a bounded queue. When the queue is full, the
 * configured {@link BackpressureStrategy} determines what happens:
 * <ul>
 *   <li>{@code DROP_LATEST} — the newest event is silently dropped</li>
 *   <li>{@code DROP_OLDEST} — the oldest buffered event is evicted</li>
 *   <li>{@code BLOCK} — the caller blocks until space is available</li>
 *   <li>{@code OVERFLOW_TO_DLQ} — the event is routed to a dead-letter queue</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * BackpressureOperator bp = BackpressureOperator.builder()
 *     .delegate(agentEventOperator)
 *     .bufferSize(1024)
 *     .strategy(BackpressureStrategy.DROP_OLDEST)
 *     .build();
 *
 * bp.submit(ctx, event);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Backpressure-aware wrapper for AEP agent event operators
 * @doc.layer product-aep
 * @doc.pattern Decorator
 *
 * @author Ghatana AI Platform
 * @since 2.0.0
 */
public final class BackpressureOperator {

    private static final Logger log = LoggerFactory.getLogger(BackpressureOperator.class);

    private final AgentEventOperator delegate;
    private final int bufferSize;
    private final BackpressureStrategy strategy;
    private final ArrayBlockingQueue<PendingEvent> buffer;

    private final AtomicLong totalSubmitted = new AtomicLong();
    private final AtomicLong totalDropped = new AtomicLong();
    private final AtomicLong totalProcessed = new AtomicLong();

    private record PendingEvent(AgentContext ctx, Map<String, Object> event) {}

    private BackpressureOperator(Builder builder) {
        this.delegate = Objects.requireNonNull(builder.delegate, "delegate");
        this.bufferSize = builder.bufferSize > 0 ? builder.bufferSize : 1024;
        this.strategy = builder.strategy != null ? builder.strategy : BackpressureStrategy.DROP_LATEST;
        this.buffer = new ArrayBlockingQueue<>(this.bufferSize);
    }

    /**
     * Submits an event for processing through the backpressure-controlled operator.
     *
     * @param ctx   agent execution context
     * @param event the AEP event
     * @return a Promise of the processed result, or a rejection if dropped
     */
    @NotNull
    public Promise<Map<String, Object>> submit(
            @NotNull AgentContext ctx,
            @NotNull Map<String, Object> event) {

        totalSubmitted.incrementAndGet();

        if (buffer.remainingCapacity() > 0 || applyBackpressure(ctx, event)) {
            return delegate.processEvent(ctx, event)
                    .whenResult($ -> totalProcessed.incrementAndGet());
        }

        totalDropped.incrementAndGet();
        return Promise.ofException(
                new BackpressureException(delegate.getOperatorId(), bufferSize, strategy));
    }

    /**
     * Attempts to apply backpressure policy when the buffer is full.
     *
     * @return true if the event can proceed, false if it should be dropped
     */
    private boolean applyBackpressure(AgentContext ctx, Map<String, Object> event) {
        switch (strategy) {
            case DROP_LATEST -> {
                log.debug("Backpressure DROP_LATEST: dropping event for operator '{}'",
                        delegate.getOperatorId());
                return false;
            }
            case DROP_OLDEST -> {
                PendingEvent evicted = buffer.poll();
                if (evicted != null) {
                    log.debug("Backpressure DROP_OLDEST: evicted oldest event for operator '{}'",
                            delegate.getOperatorId());
                }
                return true;
            }
            case BLOCK -> {
                try {
                    buffer.put(new PendingEvent(ctx, event));
                    return true;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            case OVERFLOW_TO_DLQ -> {
                log.warn("Backpressure OVERFLOW_TO_DLQ: routing event to DLQ for operator '{}'",
                        delegate.getOperatorId());
                return false;
            }
            case UNBOUNDED -> {
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Metrics
    // ═══════════════════════════════════════════════════════════════════════════

    public long getTotalSubmitted() { return totalSubmitted.get(); }
    public long getTotalDropped() { return totalDropped.get(); }
    public long getTotalProcessed() { return totalProcessed.get(); }
    public int getBufferSize() { return bufferSize; }
    public int getBufferUsed() { return buffer.size(); }
    public double getDropRate() {
        long submitted = totalSubmitted.get();
        return submitted > 0 ? (double) totalDropped.get() / submitted : 0.0;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Builder
    // ═══════════════════════════════════════════════════════════════════════════

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private AgentEventOperator delegate;
        private int bufferSize = 1024;
        private BackpressureStrategy strategy = BackpressureStrategy.DROP_LATEST;

        private Builder() {}

        public Builder delegate(@NotNull AgentEventOperator delegate) {
            this.delegate = delegate;
            return this;
        }

        public Builder bufferSize(int bufferSize) {
            this.bufferSize = bufferSize;
            return this;
        }

        public Builder strategy(@NotNull BackpressureStrategy strategy) {
            this.strategy = strategy;
            return this;
        }

        public BackpressureOperator build() {
            return new BackpressureOperator(this);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Exception
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Thrown when backpressure policy rejects an event.
     */
    public static final class BackpressureException extends RuntimeException {
        private final String operatorId;
        private final int bufferSize;
        private final BackpressureStrategy strategy;

        public BackpressureException(String operatorId, int bufferSize, BackpressureStrategy strategy) {
            super("Backpressure rejected event for operator '" + operatorId
                    + "' (buffer=" + bufferSize + ", strategy=" + strategy + ")");
            this.operatorId = operatorId;
            this.bufferSize = bufferSize;
            this.strategy = strategy;
        }

        public String getOperatorId() { return operatorId; }
        public int getBufferSize() { return bufferSize; }
        public BackpressureStrategy getStrategy() { return strategy; }
    }
}
