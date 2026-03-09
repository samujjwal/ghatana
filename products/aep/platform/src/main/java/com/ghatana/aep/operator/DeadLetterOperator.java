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
import com.ghatana.platform.resilience.DeadLetterQueue;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Dead-letter decorator for {@link AgentEventOperator}.
 *
 * <p>Wraps a delegate operator and catches processing failures. Failed events
 * are routed to the platform {@link DeadLetterQueue} instead of propagating
 * the error upstream, ensuring the pipeline continues processing subsequent
 * events.
 *
 * <h2>Integration</h2>
 * <p>Reuses the platform {@code DeadLetterQueue} from
 * {@code com.ghatana.platform.resilience} — no duplicate DLQ implementation.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * DeadLetterOperator dlOp = DeadLetterOperator.builder()
 *     .delegate(agentEventOperator)
 *     .deadLetterQueue(dlq)
 *     .build();
 *
 * dlOp.submit(ctx, event);  // failures routed to DLQ
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Routes failed events to dead-letter queue
 * @doc.layer product-aep
 * @doc.pattern Decorator, Error Channel
 *
 * @author Ghatana AI Platform
 * @since 2.0.0
 */
public final class DeadLetterOperator {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterOperator.class);

    private final AgentEventOperator delegate;
    private final DeadLetterQueue deadLetterQueue;

    // Metrics
    private final AtomicLong totalProcessed = new AtomicLong();
    private final AtomicLong totalDead = new AtomicLong();

    private DeadLetterOperator(Builder builder) {
        this.delegate = Objects.requireNonNull(builder.delegate, "delegate");
        this.deadLetterQueue = Objects.requireNonNull(builder.deadLetterQueue, "deadLetterQueue");
    }

    /**
     * Submits an event for processing. On failure, the event is stored in
     * the dead-letter queue and a DLQ receipt is returned instead of an error.
     */
    @NotNull
    public Promise<Map<String, Object>> submit(
            @NotNull AgentContext ctx,
            @NotNull Map<String, Object> event) {

        totalProcessed.incrementAndGet();

        return delegate.processEvent(ctx, event)
                .then(
                        result -> Promise.of(result),
                        error -> {
                            totalDead.incrementAndGet();

                            String dlqId = deadLetterQueue.store(
                                    event,
                                    error,
                                    "AgentEventOperator '" + delegate.getOperatorId()
                                            + "' processing failed");

                            log.warn("Event routed to DLQ (id={}) for operator '{}': {}",
                                    dlqId, delegate.getOperatorId(), error.getMessage());

                            Map<String, Object> dlqReceipt = new LinkedHashMap<>();
                            dlqReceipt.put("_operator", delegate.getOperatorId());
                            dlqReceipt.put("_status", "DEAD_LETTERED");
                            dlqReceipt.put("_dlqId", dlqId);
                            dlqReceipt.put("_error", error.getMessage());
                            return Promise.of(dlqReceipt);
                        }
                );
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Metrics
    // ═══════════════════════════════════════════════════════════════════════════

    public long getTotalProcessed() { return totalProcessed.get(); }
    public long getTotalDeadLettered() { return totalDead.get(); }
    public int getDlqSize() { return deadLetterQueue.size(); }

    // ═══════════════════════════════════════════════════════════════════════════
    // Builder
    // ═══════════════════════════════════════════════════════════════════════════

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private AgentEventOperator delegate;
        private DeadLetterQueue deadLetterQueue;

        private Builder() {}

        public Builder delegate(@NotNull AgentEventOperator delegate) {
            this.delegate = delegate;
            return this;
        }

        public Builder deadLetterQueue(@NotNull DeadLetterQueue deadLetterQueue) {
            this.deadLetterQueue = deadLetterQueue;
            return this;
        }

        public DeadLetterOperator build() {
            return new DeadLetterOperator(this);
        }
    }
}
