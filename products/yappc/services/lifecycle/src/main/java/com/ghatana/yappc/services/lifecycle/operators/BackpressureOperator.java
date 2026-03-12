/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Lifecycle Service
 */
package com.ghatana.yappc.services.lifecycle.operators;

import com.ghatana.platform.domain.domain.event.Event;
import com.ghatana.platform.domain.domain.event.GEvent;
import com.ghatana.platform.types.identity.OperatorId;
import com.ghatana.platform.workflow.operator.AbstractOperator;
import com.ghatana.platform.workflow.operator.OperatorResult;
import com.ghatana.platform.workflow.operator.OperatorType;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * Applies bounded-buffer backpressure to high-volume agent dispatch events,
 * protecting downstream operators from being overwhelmed.
 *
 * <p><b>Pipeline Position</b><br>
 * Second operator in the {@code agent-orchestration-v1} AEP pipeline. Events that
 * exceed the buffer capacity are dropped according to the configured strategy.
 *
 * <p><b>Strategy: DROP_OLDEST</b><br>
 * When the buffer is full, the oldest buffered event is discarded so the incoming
 * event can be accepted. This preserves freshness — newer agent dispatch requests
 * take priority over stale ones. Dropped events are logged as warnings.
 *
 * <p><b>Thread Safety</b><br>
 * Uses {@link ArrayBlockingQueue} for safe concurrent offer/poll operations.
 * The queue is drained synchronously during {@link #process(Event)} to keep
 * the ActiveJ event-loop promise chain non-blocking.
 *
 * @doc.type class
 * @doc.purpose Bounded backpressure handler for agent dispatch event stream
 * @doc.layer product
 * @doc.pattern Backpressure, Buffer
 * @doc.gaa.lifecycle perceive
 */
public class BackpressureOperator extends AbstractOperator {

    private static final Logger log = LoggerFactory.getLogger(BackpressureOperator.class);

    /** Default buffer capacity matching the agent-orchestration-v1 YAML spec. */
    public static final int DEFAULT_BUFFER_SIZE = 2048;

    private final ArrayBlockingQueue<Event> buffer;

    /**
     * Creates a {@code BackpressureOperator} with the default buffer size (2048).
     */
    public BackpressureOperator() {
        this(DEFAULT_BUFFER_SIZE);
    }

    /**
     * Creates a {@code BackpressureOperator} with a custom buffer capacity.
     *
     * @param bufferSize maximum number of events held in the buffer
     */
    public BackpressureOperator(int bufferSize) {
        super(
            OperatorId.of("yappc", "stream", "backpressure-handler", "1.0.0"),
            OperatorType.STREAM,
            "Backpressure Handler",
            "Bounded buffer (DROP_OLDEST) for high-volume agent dispatch events",
            List.of("agent.backpressure", "agent.buffer"),
            null
        );
        if (bufferSize <= 0) {
            throw new IllegalArgumentException("bufferSize must be positive, got: " + bufferSize);
        }
        this.buffer = new ArrayBlockingQueue<>(bufferSize);
    }

    @Override
    public Promise<OperatorResult> process(Event event) {
        Objects.requireNonNull(event, "event must not be null");

        // DROP_OLDEST when full
        if (!buffer.offer(event)) {
            Event dropped = buffer.poll();
            buffer.offer(event);
            if (dropped != null) {
                log.warn("Backpressure: buffer full (size={}), dropped oldest event type={}",
                        DEFAULT_BUFFER_SIZE, dropped.getType());
            }
        }

        // Drain head and pass it downstream
        Event next = buffer.poll();
        if (next == null) {
            return Promise.of(OperatorResult.empty());
        }

        log.debug("Backpressure: passing event type={} (buffer remaining={})",
                next.getType(), buffer.size());
        return Promise.of(OperatorResult.of(next));
    }

    /**
     * Current number of events queued in the buffer.
     *
     * @return queue size
     */
    public int bufferSize() {
        return buffer.size();
    }

    @Override
    public Event toEvent() {
        return GEvent.builder()
                .type("operator.registered")
                .addPayload("operatorId",   getId().toString())
                .addPayload("operatorName", getName())
                .addPayload("operatorType", getType().name())
                .addPayload("version",      getVersion())
                .addPayload("bufferSize",   DEFAULT_BUFFER_SIZE)
                .build();
    }
}
