/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

/**
 * @doc.type class
 * @doc.purpose Circuit breaker wrapper for EventLogStore to prevent cascade storage failures
 * @doc.layer product
 * @doc.pattern Resilience, CircuitBreaker, Decorator
 */
package com.ghatana.datacloud.resilience;

import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.platform.domain.eventstore.TenantContext;
import com.ghatana.platform.resilience.CircuitBreaker;
import com.ghatana.platform.types.identity.Offset;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Circuit-breaker-protected wrapper around {@link EventLogStore}.
 *
 * <p>Addresses DC-009: Prevents cascade failures when the underlying storage
 * tier becomes temporarily unavailable. The circuit breaker transitions between
 * CLOSED → OPEN → HALF_OPEN states:
 * <ul>
 *   <li><strong>CLOSED</strong>: Normal operation; failures are counted.</li>
 *   <li><strong>OPEN</strong>: Storage calls are rejected immediately with
 *       {@link CircuitBreaker.CircuitBreakerOpenException}. Callers should
 *       buffer or retry via their own backoff strategy.</li>
 *   <li><strong>HALF_OPEN</strong>: A probe call is allowed through to test
 *       recovery.</li>
 * </ul>
 *
 * <p>This class is a pure decorator — it does not buffer or replay rejected
 * writes. Callers that need write-ahead logging or replay semantics must
 * implement that at a higher layer.</p>
 *
 * @doc.type class
 * @doc.purpose Resilient EventLogStore with circuit breaker protection
 * @doc.layer product
 * @doc.pattern Resilience, CircuitBreaker, Decorator
 *
 * @since 2026-03-27
 */
public final class ResilientEventLogStore implements EventLogStore {

    private static final Logger log = LoggerFactory.getLogger(ResilientEventLogStore.class);

    /** Default number of consecutive failures before the circuit opens. */
    public static final int DEFAULT_FAILURE_THRESHOLD = 5;
    /** Default number of consecutive successes (in HALF_OPEN) before closing. */
    public static final int DEFAULT_SUCCESS_THRESHOLD = 2;
    /** Default time the circuit stays OPEN before transitioning to HALF_OPEN. */
    public static final Duration DEFAULT_RESET_TIMEOUT = Duration.ofSeconds(30);
    /** Default maximum backoff for exponential reset timeout. */
    public static final Duration DEFAULT_MAX_BACKOFF = Duration.ofMinutes(5);

    private final EventLogStore delegate;
    private final CircuitBreaker circuitBreaker;
    private final Eventloop eventloop;

    /**
     * Creates a resilient store with default circuit breaker settings.
     *
     * @param delegate  the underlying store to protect
     * @param eventloop ActiveJ eventloop for async execution
     */
    public ResilientEventLogStore(EventLogStore delegate, Eventloop eventloop) {
        this(delegate, eventloop, createDefaultCircuitBreaker());
    }

    /**
     * Creates a resilient store with a custom circuit breaker.
     *
     * @param delegate       the underlying store to protect
     * @param eventloop      ActiveJ eventloop for async execution
     * @param circuitBreaker pre-configured circuit breaker instance
     */
    public ResilientEventLogStore(EventLogStore delegate, Eventloop eventloop, CircuitBreaker circuitBreaker) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop");
        this.circuitBreaker = Objects.requireNonNull(circuitBreaker, "circuitBreaker");
    }

    // ── EventLogStore — Append ────────────────────────────────────────────────

    @Override
    public Promise<Offset> append(TenantContext tenant, EventEntry entry) {
        return circuitBreaker.execute(eventloop,
            () -> delegate.append(tenant, entry)
                .then(Promise::of, e -> {
                    log.warn("EventLogStore.append failed for tenant {}: {}", tenant.tenantId(), e.getMessage());
                    return Promise.ofException(e);
                })
        );
    }

    @Override
    public Promise<List<Offset>> appendBatch(TenantContext tenant, List<EventEntry> entries) {
        return circuitBreaker.execute(eventloop,
            () -> delegate.appendBatch(tenant, entries)
                .then(Promise::of, e -> {
                    log.warn("EventLogStore.appendBatch failed for tenant {}: {}", tenant.tenantId(), e.getMessage());
                    return Promise.ofException(e);
                })
        );
    }

    // ── EventLogStore — Read ──────────────────────────────────────────────────

    @Override
    public Promise<List<EventEntry>> read(TenantContext tenant, Offset from, int limit) {
        return circuitBreaker.execute(eventloop,
            () -> delegate.read(tenant, from, limit)
                .then(Promise::of, e -> {
                    log.warn("EventLogStore.read failed for tenant {}: {}", tenant.tenantId(), e.getMessage());
                    return Promise.ofException(e);
                })
        );
    }

    @Override
    public Promise<List<EventEntry>> readByTimeRange(TenantContext tenant,
                                                     Instant startTime,
                                                     Instant endTime,
                                                     int limit) {
        return circuitBreaker.execute(eventloop,
            () -> delegate.readByTimeRange(tenant, startTime, endTime, limit)
                .then(Promise::of, e -> {
                    log.warn("EventLogStore.readByTimeRange failed for tenant {}: {}", tenant.tenantId(), e.getMessage());
                    return Promise.ofException(e);
                })
        );
    }

    @Override
    public Promise<List<EventEntry>> readByType(TenantContext tenant,
                                                String eventType,
                                                Offset from,
                                                int limit) {
        return circuitBreaker.execute(eventloop,
            () -> delegate.readByType(tenant, eventType, from, limit)
                .then(Promise::of, e -> {
                    log.warn("EventLogStore.readByType failed for tenant {}: {}", tenant.tenantId(), e.getMessage());
                    return Promise.ofException(e);
                })
        );
    }

    // ── EventLogStore — Offsets ───────────────────────────────────────────────

    @Override
    public Promise<Offset> getLatestOffset(TenantContext tenant) {
        return circuitBreaker.execute(eventloop,
            () -> delegate.getLatestOffset(tenant)
                .then(Promise::of, e -> {
                    log.warn("EventLogStore.getLatestOffset failed for tenant {}: {}", tenant.tenantId(), e.getMessage());
                    return Promise.ofException(e);
                })
        );
    }

    @Override
    public Promise<Offset> getEarliestOffset(TenantContext tenant) {
        return circuitBreaker.execute(eventloop,
            () -> delegate.getEarliestOffset(tenant)
                .then(Promise::of, e -> {
                    log.warn("EventLogStore.getEarliestOffset failed for tenant {}: {}", tenant.tenantId(), e.getMessage());
                    return Promise.ofException(e);
                })
        );
    }

    // ── EventLogStore — Streaming ─────────────────────────────────────────────

    @Override
    public Promise<Subscription> tail(TenantContext tenant, Offset from, Consumer<EventEntry> handler) {
        return circuitBreaker.execute(eventloop,
            () -> delegate.tail(tenant, from, handler)
                .then(Promise::of, e -> {
                    log.warn("EventLogStore.tail failed for tenant {}: {}", tenant.tenantId(), e.getMessage());
                    return Promise.ofException(e);
                })
        );
    }

    // ── Circuit Breaker Observability ─────────────────────────────────────────

    /**
     * Returns the current circuit breaker state for monitoring.
     */
    public CircuitBreaker.State getCircuitBreakerState() {
        return circuitBreaker.getState();
    }

    /**
     * Returns the underlying circuit breaker for advanced monitoring.
     */
    public CircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }

    /**
     * Manually resets the circuit breaker to CLOSED state.
     * Use with care — only when confident the downstream storage is healthy.
     */
    public void resetCircuitBreaker() {
        log.info("Manually resetting DataCloud EventLogStore circuit breaker");
        circuitBreaker.reset();
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    private static CircuitBreaker createDefaultCircuitBreaker() {
        return CircuitBreaker.builder("datacloud-event-log-store")
            .failureThreshold(DEFAULT_FAILURE_THRESHOLD)
            .successThreshold(DEFAULT_SUCCESS_THRESHOLD)
            .resetTimeout(DEFAULT_RESET_TIMEOUT)
            .maxBackoff(DEFAULT_MAX_BACKOFF)
            .backoffMultiplier(1.5)
            .build();
    }
}
