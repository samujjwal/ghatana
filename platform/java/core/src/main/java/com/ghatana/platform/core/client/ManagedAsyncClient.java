package com.ghatana.platform.core.client;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Shared running-state support for {@link AsyncClient} implementations.
 *
 * <p>Use this base class when a client needs consistent lifecycle state tracking
 * without re-implementing {@code isRunning()} and the associated start/stop flag.
 * Subclasses remain responsible for their actual initialization and shutdown work,
 * but can reuse the same atomic state transition helpers.</p>
 *
 * @doc.type class
 * @doc.purpose Shared AsyncClient lifecycle state support
 * @doc.layer core
 * @doc.pattern Template Method, Client
 */
public abstract class ManagedAsyncClient implements AsyncClient {

    private final AtomicBoolean running;

    protected ManagedAsyncClient() {
        this(false);
    }

    protected ManagedAsyncClient(boolean initialRunningState) {
        this.running = new AtomicBoolean(initialRunningState);
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Marks the client as running.
     *
     * @return true when the state transitioned from stopped to running
     */
    protected final boolean markStarted() {
        return running.compareAndSet(false, true);
    }

    /**
     * Marks the client as stopped.
     *
     * @return true when the state transitioned from running to stopped
     */
    protected final boolean markStopped() {
        return running.compareAndSet(true, false);
    }

    /**
     * Forces the running state to a specific value.
     *
     * @param value the desired running flag
     */
    protected final void setRunning(boolean value) {
        running.set(value);
    }
}
