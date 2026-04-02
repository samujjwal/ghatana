/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.operator;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.platform.resilience.DeadLetterQueue;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * An operator that wraps an {@link AgentEventOperator} with dead-letter queue resilience.
 *
 * <p>On a successful invocation, the delegate's result is returned transparently.
 * On failure, the failed event is stored in the provided {@link DeadLetterQueue} and
 * a DLQ receipt map is returned instead of propagating the error. This ensures that
 * no event is silently lost after a processing failure.
 *
 * <p>The DLQ receipt map contains:
 * <ul>
 *   <li>{@code _status}   — {@code "DEAD_LETTERED"}</li>
 *   <li>{@code _dlqId}    — the ID assigned by the DLQ for replay/lookup</li>
 *   <li>{@code _error}    — the error message from the failing exception</li>
 *   <li>{@code _operator} — the agent ID of the delegate operator</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * DeadLetterQueue dlq = DeadLetterQueue.builder()
 *     .maxSize(10_000)
 *     .ttl(Duration.ofHours(24))
 *     .enableReplay(false)
 *     .build();
 *
 * AgentEventOperator delegate = new AgentEventOperator(myAgent);
 * DeadLetterOperator op = DeadLetterOperator.builder()
 *     .delegate(delegate)
 *     .deadLetterQueue(dlq)
 *     .build();
 *
 * Promise<Map<String, Object>> result = op.submit(ctx, event);
 * }</pre>
 *
 * @see AgentEventOperator
 * @see DeadLetterQueue
 * @doc.type class
 * @doc.purpose Wraps an AgentEventOperator with dead-letter queue resilience for AEP pipelines
 * @doc.layer product
 * @doc.pattern Decorator
 */
public final class DeadLetterOperator {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterOperator.class);

    private static final String STATUS_DEAD_LETTERED = "DEAD_LETTERED";

    private final AgentEventOperator delegate;
    private final DeadLetterQueue deadLetterQueue;

    private final AtomicLong totalProcessed = new AtomicLong(0);
    private final AtomicLong totalDeadLettered = new AtomicLong(0);

    private DeadLetterOperator(Builder builder) {
        this.delegate = Objects.requireNonNull(builder.delegate, "delegate must not be null");
        this.deadLetterQueue =
                Objects.requireNonNull(builder.deadLetterQueue, "deadLetterQueue must not be null");
    }

    /**
     * Submit an event for processing.
     *
     * <p>Delegates to the wrapped {@link AgentEventOperator}. If the delegate's promise
     * completes exceptionally, the event is stored in the DLQ and a receipt map is returned.
     *
     * @param ctx   the agent execution context
     * @param event the event payload to process
     * @return a Promise resolving to the delegate's output on success, or a DLQ receipt
     *         map on failure — never fails itself
     */
    public Promise<Map<String, Object>> submit(
            AgentContext ctx, Map<String, Object> event) {
        totalProcessed.incrementAndGet();
        return delegate.submit(ctx, event)
                .then(
                        output -> Promise.of(output),
                        ex -> {
                            String dlqId = deadLetterQueue.store(
                                    event, ex, "operator-failure");
                            totalDeadLettered.incrementAndGet();
                            log.warn("Event dead-lettered (dlqId={}, operator={}, error={})",
                                    dlqId, delegate.getAgentId(), ex.getMessage());

                            Map<String, Object> receipt = new HashMap<>();
                            receipt.put("_status", STATUS_DEAD_LETTERED);
                            receipt.put("_dlqId", dlqId);
                            receipt.put("_error",
                                    ex.getMessage() != null ? ex.getMessage() : ex.toString());
                            receipt.put("_operator", delegate.getAgentId());
                            return Promise.of(receipt);
                        });
    }

    /**
     * Returns the total number of events submitted (including those dead-lettered).
     *
     * @return total processed count
     */
    public long getTotalProcessed() {
        return totalProcessed.get();
    }

    /**
     * Returns the total number of events that were routed to the DLQ.
     *
     * @return total dead-lettered count
     */
    public long getTotalDeadLettered() {
        return totalDeadLettered.get();
    }

    /**
     * Returns the current number of events in the DLQ.
     *
     * @return current DLQ size
     */
    public int getDlqSize() {
        return deadLetterQueue.size();
    }

    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Creates a new builder for {@code DeadLetterOperator}.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link DeadLetterOperator}.
     */
    public static final class Builder {
        private AgentEventOperator delegate;
        private DeadLetterQueue deadLetterQueue;

        private Builder() {}

        /**
         * Sets the delegate operator that processes events.
         *
         * @param delegate the delegate; must not be null
         * @return this builder
         */
        public Builder delegate(AgentEventOperator delegate) {
            this.delegate = delegate;
            return this;
        }

        /**
         * Sets the dead-letter queue to store failed events.
         *
         * @param deadLetterQueue the DLQ; must not be null
         * @return this builder
         */
        public Builder deadLetterQueue(DeadLetterQueue deadLetterQueue) {
            this.deadLetterQueue = deadLetterQueue;
            return this;
        }

        /**
         * Builds the {@link DeadLetterOperator}.
         *
         * @return a configured {@code DeadLetterOperator}
         * @throws NullPointerException if delegate or deadLetterQueue is null
         */
        public DeadLetterOperator build() {
            return new DeadLetterOperator(this);
        }
    }
}
