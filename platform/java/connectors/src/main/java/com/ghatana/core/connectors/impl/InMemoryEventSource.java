package com.ghatana.core.connectors.impl;

import com.ghatana.core.connectors.EventSource;
import com.ghatana.core.connectors.IngestEvent;
import io.activej.promise.Promise;
import io.activej.promise.SettablePromise;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * In-memory EventSource with support for awaiting events (Promise-based).
 *
 * <p><b>Purpose</b><br>
 * Provides a test/development event source that supports both polling and
 * waiting for events via Promises.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * InMemoryEventSource source = new InMemoryEventSource();
 * source.start().get();
 *
 * // In another thread/async context:
 * source.addEvent(event);
 *
 * // Consumer awaits event:
 * Promise<IngestEvent> p = source.next(); // completes when event available
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe for concurrent addEvent() and next() calls.
 *
 * @doc.type class
 * @doc.purpose In-memory event source with await support
 * @doc.layer core
 * @doc.pattern Producer-Consumer
 */
public final class InMemoryEventSource implements EventSource {
    private final Queue<IngestEvent> queue = new ConcurrentLinkedQueue<>();
    private final Queue<SettablePromise<IngestEvent>> waiters = new ConcurrentLinkedQueue<>();
    private volatile boolean started = false;

    public InMemoryEventSource() {
    }

    /**
     * Add an event to the queue. If there are waiters, completes the first waiter.
     *
     * @param event Event to add (non-null)
     */
    public void addEvent(IngestEvent event) {
        Objects.requireNonNull(event, "event cannot be null");

        // Try to complete a waiter first
        SettablePromise<IngestEvent> waiter = waiters.poll();
        if (waiter != null) {
            waiter.set(event);
        } else {
            queue.add(event);
        }
    }

    @Override
    public Promise<Void> start() {
        started = true;
        return Promise.complete();
    }

    @Override
    public Promise<Void> stop() {
        started = false;
        // Complete all waiters with exception
        SettablePromise<IngestEvent> waiter;
        while ((waiter = waiters.poll()) != null) {
            waiter.setException(new IllegalStateException("source stopped"));
        }
        return Promise.complete();
    }

    @Override
    public Promise<IngestEvent> next() {
        if (!started) {
            return Promise.ofException(new IllegalStateException("source not started"));
        }

        // Try to return immediately if event available
        IngestEvent event = queue.poll();
        if (event != null) {
            return Promise.of(event);
        }

        // Otherwise, create a promise that will be completed when event arrives
        SettablePromise<IngestEvent> promise = new SettablePromise<>();
        waiters.add(promise);
        return promise;
    }

    /**
     * Get the number of queued events (for testing).
     *
     * @return Number of events in queue
     */
    public int queueSize() {
        return queue.size();
    }

    /**
     * Get the number of waiters (for testing).
     *
     * @return Number of pending waiters
     */
    public int waiterCount() {
        return waiters.size();
    }
}

