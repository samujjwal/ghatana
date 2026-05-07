/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.resilience;

import com.ghatana.platform.resilience.CircuitBreaker;

import java.time.Duration;

/**
 * Factory for named, pre-configured circuit breakers used across data-cloud services.
 *
 * <p>Centralises failure thresholds and reset timeouts so that every circuit breaker
 * in the data-cloud product follows the same resilience profile instead of each service
 * defining its own ad-hoc parameters (DC-016).
 *
 * <p>Callers that need a customised circuit breaker (e.g., for tests) should use
 * {@link CircuitBreaker#builder(String)} directly.
 *
 * @doc.type class
 * @doc.purpose Factory for standard data-cloud circuit breaker instances
 * @doc.layer product
 * @doc.pattern Factory, Resilience
 */
public final class DataCloudCircuitBreakers {

    /** Maximum consecutive failures before opening the circuit. */
    public static final int DEFAULT_FAILURE_THRESHOLD = 5;
    /** Consecutive successes in HALF_OPEN before closing the circuit. */
    public static final int DEFAULT_SUCCESS_THRESHOLD = 2;
    /** Minimum time the circuit stays OPEN before allowing a probe request. */
    public static final Duration DEFAULT_RESET_TIMEOUT = Duration.ofSeconds(30);
    /** Maximum backoff when the circuit repeatedly opens. */
    public static final Duration DEFAULT_MAX_BACKOFF = Duration.ofMinutes(5);

    private DataCloudCircuitBreakers() {}

    /**
     * Creates a circuit breaker tuned for storage routing lookups (read-heavy).
     *
     * <p>Uses the standard data-cloud resilience profile.
     *
     * @return a new {@link CircuitBreaker} for the storage router service
     */
    public static CircuitBreaker storageRouter() {
        return CircuitBreaker.builder("data-cloud.storage-router")
                .failureThreshold(DEFAULT_FAILURE_THRESHOLD)
                .resetTimeout(DEFAULT_RESET_TIMEOUT)
                .successThreshold(DEFAULT_SUCCESS_THRESHOLD)
                .build();
    }

    /**
     * Creates a circuit breaker tuned for event log store operations.
     *
     * <p>Uses the standard data-cloud resilience profile.
     *
     * @return a new {@link CircuitBreaker} for the event log store
     */
    public static CircuitBreaker eventLogStore() {
        return CircuitBreaker.builder("data-cloud.event-log-store")
                .failureThreshold(DEFAULT_FAILURE_THRESHOLD)
                .resetTimeout(DEFAULT_RESET_TIMEOUT)
                .successThreshold(DEFAULT_SUCCESS_THRESHOLD)
                .build();
    }

    /**
     * Creates a circuit breaker tuned for feature store ingest (write-heavy, DLQ-backed).
     *
     * <p>Uses a higher failure threshold because individual write failures are routed
     * to the dead-letter queue before the circuit counts them.
     *
     * @return a new {@link CircuitBreaker} for feature store ingest
     */
    public static CircuitBreaker featureStoreIngest() {
        return CircuitBreaker.builder("data-cloud.feature-store-ingest")
                .failureThreshold(10)
                .resetTimeout(DEFAULT_RESET_TIMEOUT)
                .successThreshold(DEFAULT_SUCCESS_THRESHOLD)
                .build();
    }

    /**
     * Creates a circuit breaker with the standard data-cloud profile using a custom name.
     *
     * <p>Use this for service-specific circuit breakers that should still follow the
     * standard failure thresholds.
     *
     * @param name circuit breaker name (used in metrics and log messages)
     * @return a new {@link CircuitBreaker} with standard data-cloud settings
     */
    public static CircuitBreaker standard(String name) {
        return CircuitBreaker.builder(name)
                .failureThreshold(DEFAULT_FAILURE_THRESHOLD)
                .resetTimeout(DEFAULT_RESET_TIMEOUT)
                .successThreshold(DEFAULT_SUCCESS_THRESHOLD)
                .build();
    }
}
