/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.ledger.dtc;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Middleware that attaches a {@link VersionVector} to outgoing events and enforces
 * causal ordering on incoming events (K17-005).
 *
 * <p>On <strong>send</strong>: the local vector is incremented before publishing,
 * so every event carries a vector that reflects the node's causal history.
 *
 * <p>On <strong>receive</strong>: the incoming event's vector is compared with the
 * local vector:
 * <ul>
 *   <li>{@code BEFORE / AFTER} → event is causally ordered; handle immediately and
 *       merge the incoming vector into local.</li>
 *   <li>{@code CONCURRENT} → buffer the event; retry after {@code bufferTimeout}.
 *       If the timeout elapses, deliver anyway (best-effort).</li>
 * </ul>
 *
 * <p>Default buffer timeout is {@value DEFAULT_BUFFER_TIMEOUT_SECONDS} seconds.
 *
 * @doc.type class
 * @doc.purpose Causal ordering enforcement middleware for distributed events (K17-005)
 * @doc.layer core
 * @doc.pattern Service
 */
public final class CausalOrderingMiddleware {

    private static final Logger log = LoggerFactory.getLogger(CausalOrderingMiddleware.class);

    private static final int DEFAULT_BUFFER_TIMEOUT_SECONDS = 5;

    private final String nodeId;
    private final Duration bufferTimeout;

    /** Local vector clock for this node. Guarded by {@code this}. */
    private volatile VersionVector localVector = VersionVector.empty();

    /** Pending out-of-order events, waiting for causal dependency. */
    private final Deque<BufferedEvent> pendingBuffer = new ArrayDeque<>();

    public CausalOrderingMiddleware(String nodeId) {
        this(nodeId, Duration.ofSeconds(DEFAULT_BUFFER_TIMEOUT_SECONDS));
    }

    public CausalOrderingMiddleware(String nodeId, Duration bufferTimeout) {
        this.nodeId        = Objects.requireNonNull(nodeId, "nodeId");
        this.bufferTimeout = Objects.requireNonNull(bufferTimeout, "bufferTimeout");
    }

    // ─── Outgoing ─────────────────────────────────────────────────────────────

    /**
     * Increments the local clock and returns the stamped vector to attach to the outgoing event.
     * Call this immediately before publishing an event.
     *
     * @param eventType human-readable event type (for logging)
     * @return the vector that must be attached to the outgoing event header/payload
     */
    public synchronized VersionVector stampOutgoing(String eventType) {
        localVector = localVector.increment(nodeId);
        log.debug("Stamped outgoing [{}] with vector {}", eventType, localVector);
        return localVector;
    }

    // ─── Incoming ─────────────────────────────────────────────────────────────

    /**
     * Processes an incoming event.
     *
     * <p>If the event's vector is causally ordered with the local clock,
     * the handler is invoked immediately. If CONCURRENT, the event is buffered for
     * up to {@link #bufferTimeout}; after the timeout it is delivered anyway to prevent starvation.
     *
     * @param incomingVector version vector from the incoming event
     * @param eventType      human-readable event type (for logging)
     * @param handler        async handler to invoke when the event is cleared for delivery
     * @return promise that resolves when the handler has been dispatched
     */
    public synchronized Promise<Void> onReceive(VersionVector incomingVector,
                                                String eventType,
                                                EventHandler handler) {
        Objects.requireNonNull(incomingVector, "incomingVector");
        Objects.requireNonNull(handler, "handler");

        VersionVector.Ordering ordering = incomingVector.compare(localVector);
        if (ordering != VersionVector.Ordering.CONCURRENT) {
            log.debug("Causally ordered [{}]: {} — delivering immediately", eventType, ordering);
            localVector = localVector.merge(incomingVector);
            drainBuffer();
            return handler.handle();
        }

        // CONCURRENT: buffer, try to deliver within timeout
        log.info("Concurrent event [{}]: buffering (timeout={}s)", eventType, bufferTimeout.toSeconds());
        BufferedEvent buffered = new BufferedEvent(incomingVector, eventType, handler,
                                                   Instant.now().plus(bufferTimeout));
        pendingBuffer.addLast(buffered);
        return Promise.complete();
    }

    // ─── Buffer drain ─────────────────────────────────────────────────────────

    /**
     * Called periodically (or after each delivery) to drain buffered events whose
     * causal dependency is now satisfied or whose timeout has elapsed.
     */
    public synchronized void drainBuffer() {
        Instant now = Instant.now();
        Deque<BufferedEvent> retry = new ArrayDeque<>();

        while (!pendingBuffer.isEmpty()) {
            BufferedEvent ev = pendingBuffer.pollFirst();
            VersionVector.Ordering ord = ev.vector().compare(localVector);
            if (ord != VersionVector.Ordering.CONCURRENT || now.isAfter(ev.deadline())) {
                log.info("Delivering buffered event [{}] (ordering={}, timedOut={})",
                         ev.eventType(), ord, now.isAfter(ev.deadline()));
                localVector = localVector.merge(ev.vector());
                ev.handler().handle();  // fire-and-forget; timeout = best-effort
            } else {
                retry.addLast(ev);
            }
        }
        pendingBuffer.addAll(retry);
    }

    /** Returns an immutable snapshot of the current local vector. */
    public synchronized VersionVector localVector() {
        return localVector;
    }

    // ─── Types ────────────────────────────────────────────────────────────────

    /** Async handler for a delivered event. */
    @FunctionalInterface
    public interface EventHandler {
        Promise<Void> handle();
    }

    /** A pending event waiting for its causal dependency or timeout. */
    private record BufferedEvent(
            VersionVector vector,
            String eventType,
            EventHandler handler,
            Instant deadline
    ) {}
}
