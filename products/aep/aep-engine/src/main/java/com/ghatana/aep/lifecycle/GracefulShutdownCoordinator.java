/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.lifecycle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Coordinates graceful shutdown for in-flight AEP operations (AEP-021).
 *
 * <p>Callers obtain an {@link OperationTicket} before starting work. Shutdown marks
 * the coordinator as draining, rejects new operations, and waits for existing work
 * to finish until the configured timeout elapses.
 *
 * @doc.type class
 * @doc.purpose Coordinate graceful shutdown and in-flight operation draining
 * @doc.layer product
 * @doc.pattern Coordinator
 */
public final class GracefulShutdownCoordinator implements AutoCloseable {

    public static final String DRAIN_TIMEOUT_MS_KEY = "shutdownDrainTimeoutMs";

    private static final Logger log = LoggerFactory.getLogger(GracefulShutdownCoordinator.class);

    private final String componentName;
    private final Duration drainTimeout;
    private final AtomicBoolean shutdownRequested = new AtomicBoolean(false);
    private final AtomicInteger inFlightOperations = new AtomicInteger(0);
    private final Object drainMonitor = new Object();
    private volatile Thread shutdownHook;

    private GracefulShutdownCoordinator(Builder builder) {
        this.componentName = builder.componentName;
        this.drainTimeout = builder.drainTimeout;
    }

    /**
     * Begins tracking an in-flight operation.
     *
     * @param operationName operation label for diagnostics
     * @return ticket that must be closed when the operation completes
     */
    public OperationTicket beginOperation(String operationName) {
        Objects.requireNonNull(operationName, "operationName must not be null");
        if (shutdownRequested.get()) {
            throw new IllegalStateException(componentName + " is shutting down; rejecting operation " + operationName);
        }

        int current = inFlightOperations.incrementAndGet();
        if (shutdownRequested.get()) {
            inFlightOperations.decrementAndGet();
            throw new IllegalStateException(componentName + " is shutting down; rejecting operation " + operationName);
        }

        log.debug("Accepted operation={} for component={} inFlight={}", operationName, componentName, current);
        return new OperationTicket(operationName);
    }

    /**
     * @return whether shutdown has been requested
     */
    public boolean isShutdownRequested() {
        return shutdownRequested.get();
    }

    /**
     * Registers a JVM shutdown hook that will invoke {@link #shutdown()}.
     */
    public void registerShutdownHook() {
        if (shutdownHook != null) {
            return;
        }

        Thread hook = new Thread(this::shutdown, componentName + "-shutdown-hook");
        hook.setDaemon(true);
        Runtime.getRuntime().addShutdownHook(hook);
        shutdownHook = hook;
    }

    /**
     * Starts draining and waits up to the configured timeout for in-flight work.
     */
    public void shutdown() {
        if (!shutdownRequested.compareAndSet(false, true)) {
            return;
        }

        Instant deadline = Instant.now().plus(drainTimeout);
        synchronized (drainMonitor) {
            while (inFlightOperations.get() > 0 && Instant.now().isBefore(deadline)) {
                long remainingMs = Math.max(1L, Duration.between(Instant.now(), deadline).toMillis());
                try {
                    drainMonitor.wait(remainingMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        if (inFlightOperations.get() > 0) {
            log.warn("Graceful shutdown for component={} timed out with {} in-flight operations remaining",
                componentName, inFlightOperations.get());
        } else {
            log.info("Graceful shutdown complete for component={}", componentName);
        }
    }

    @Override
    public void close() {
        shutdown();
        Thread hook = shutdownHook;
        if (hook != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(hook);
            } catch (IllegalStateException ignored) {
                // JVM is already shutting down.
            }
            shutdownHook = null;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Ticket representing an in-flight operation.
     */
    public final class OperationTicket implements AutoCloseable {
        private final String operationName;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        private OperationTicket(String operationName) {
            this.operationName = operationName;
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }

            int remaining = inFlightOperations.decrementAndGet();
            log.debug("Completed operation={} for component={} inFlight={}", operationName, componentName, remaining);
            synchronized (drainMonitor) {
                drainMonitor.notifyAll();
            }
        }
    }

    /**
     * Builder for {@link GracefulShutdownCoordinator}.
     */
    public static final class Builder {
        private String componentName = "aep-engine";
        private Duration drainTimeout = Duration.ofSeconds(30);

        private Builder() {
        }

        public Builder componentName(String componentName) {
            this.componentName = Objects.requireNonNull(componentName, "componentName must not be null");
            return this;
        }

        public Builder drainTimeout(Duration drainTimeout) {
            this.drainTimeout = Objects.requireNonNull(drainTimeout, "drainTimeout must not be null");
            return this;
        }

        public GracefulShutdownCoordinator build() {
            if (drainTimeout.isZero() || drainTimeout.isNegative()) {
                throw new IllegalArgumentException("drainTimeout must be positive");
            }
            return new GracefulShutdownCoordinator(this);
        }
    }
}