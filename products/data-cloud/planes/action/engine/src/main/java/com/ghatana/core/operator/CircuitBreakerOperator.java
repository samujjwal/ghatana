/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.core.operator;

import com.ghatana.platform.domain.event.Event;
import com.ghatana.platform.domain.event.GEvent;
import com.ghatana.platform.domain.event.EventTime;
import com.ghatana.platform.resilience.CircuitBreaker;
import com.ghatana.platform.resilience.CircuitBreakerProfiles;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Operator decorator that wraps another operator with a {@link CircuitBreaker}.
 *
 * <p><b>Purpose</b><br>
 * Protects downstream systems from cascading failures by opening the circuit when
 * the wrapped operator fails repeatedly. When the circuit is OPEN, events fail fast
 * without calling the delegate, allowing the downstream to recover.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * CircuitBreakerOperator cb = CircuitBreakerOperator.builder()
 *     .operator(httpEnrichmentOperator)
 *     .name("enrichment-cb")
 *     .failureThreshold(5)
 *     .resetTimeout(Duration.ofSeconds(30))
 *     .build();
 *
 * // Passes events through when circuit is CLOSED or HALF_OPEN
 * // Fails fast with OperatorResult.failed() when circuit is OPEN
 * Promise<OperatorResult> result = cb.process(event);
 * }</pre>
 *
 * <p><b>Circuit States</b><br>
 * <ul>
 *   <li><b>CLOSED</b> — Normal operation; failures are counted.</li>
 *   <li><b>OPEN</b> — All calls fail fast with {@code circuit-open} error.</li>
 *   <li><b>HALF_OPEN</b> — Single probe call allowed; success closes the circuit.</li>
 * </ul>
 *
 * <p><b>Reuse</b><br>
 * Wraps {@link CircuitBreaker} from {@code platform:java:core}, which provides the
 * full 3-state implementation with atomic state transitions and exponential backoff
 * reset. An {@link Eventloop} is required for scheduling the HALF_OPEN probe timer.
 *
 * @see CircuitBreaker
 * @see RetryOperator
 *
 * @doc.type class
 * @doc.purpose Circuit breaker decorator for operators to prevent cascading failures
 * @doc.layer core
 * @doc.pattern Decorator
 */
public final class CircuitBreakerOperator extends AbstractOperator {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerOperator.class);

    private final UnifiedOperator delegate;
    private final CircuitBreaker circuitBreaker;
    private final Eventloop eventloop;

    private CircuitBreakerOperator(Builder builder) {
        super(
            OperatorId.of("ghatana", "resilience", "circuit-breaker", "1.0.0"),
            OperatorType.STREAM,
            "Circuit Breaker Operator",
            "Wraps an operator with circuit-breaker fault isolation",
            List.of("resilience", "circuit-breaker", "fault-isolation"),
            null
        );
        this.delegate = Objects.requireNonNull(builder.operator, "wrapped operator is required");
        this.eventloop = Objects.requireNonNull(builder.eventloop, "eventloop is required");

        CircuitBreaker.Builder cbBuilder = CircuitBreaker.builder(
                Objects.requireNonNull(builder.name, "circuit breaker name is required"))
            .failureThreshold(builder.failureThreshold)
            .successThreshold(builder.successThreshold)
            .resetTimeout(builder.resetTimeout)
            .maxBackoff(builder.maxBackoff)
            .backoffMultiplier(builder.backoffMultiplier);

        this.circuitBreaker = cbBuilder.build();
    }

    @Override
    public Promise<OperatorResult> process(Event event) {
        return circuitBreaker.execute(
            eventloop,
            () -> delegate.process(event),
            null  // no fallback — return failure result when open
        ).then(
            result -> Promise.of(result),
            ex -> {
                String msg = ex.getMessage();
                if (msg != null && (msg.contains("circuit is OPEN") || msg.contains("is OPEN"))) {
                    log.debug("Circuit open; fast-failing event {}", event.getId());
                    return Promise.of(OperatorResult.failed("circuit-open: downstream unavailable"));
                }
                log.warn("Delegate operator threw unexpected exception: {}", msg);
                return Promise.of(OperatorResult.failed(msg != null ? msg : "unknown error"));
            }
        );
    }

    @Override
    protected Promise<Void> doInitialize(OperatorConfig config) {
        return delegate.initialize(config);
    }

    @Override
    protected Promise<Void> doStart() {
        return delegate.start();
    }

    @Override
    protected Promise<Void> doStop() {
        return delegate.stop();
    }

    @Override
    public boolean isHealthy() {
        return delegate.isHealthy() && circuitBreaker.getState() != CircuitBreaker.State.OPEN;
    }

    @Override
    public boolean isStateful() {
        return delegate.isStateful();
    }

    /**
     * Returns current circuit state for monitoring.
     */
    public CircuitBreaker.State getCircuitState() {
        return circuitBreaker.getState();
    }

    @Override
    public Map<String, Object> getInternalState() {
        Map<String, Object> state = new HashMap<>(super.getInternalState());
        state.put("circuit_state", circuitBreaker.getState().name());
        Map<String, Long> metrics = new HashMap<>();
        metrics.put("total_calls", circuitBreaker.getTotalCalls());
        metrics.put("total_successes", circuitBreaker.getTotalSuccesses());
        metrics.put("total_failures", circuitBreaker.getTotalFailures());
        metrics.put("total_rejections", circuitBreaker.getTotalRejections());
        state.put("circuit_metrics", metrics);
        return state;
    }

    @Override
    public Event toEvent() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "operator.circuit-breaker");
        payload.put("name", getName());
        payload.put("circuit_state", circuitBreaker.getState().name());

        Map<String, String> headers = new HashMap<>();
        headers.put("operatorId", getId().toString());
        headers.put("tenantId", getId().getNamespace());

        return GEvent.builder()
            .type("operator.registered")
            .headers(headers)
            .payload(payload)
            .time(EventTime.now())
            .build();
    }

    // ── Builder ────────────────────────────────────────────────────────────

    /**
     * Builder for {@link CircuitBreakerOperator}.
     */
    public static final class Builder {
        private UnifiedOperator operator;
        private Eventloop eventloop;
        private String name;
        private int failureThreshold = 5;
        private int successThreshold = 2;
        private Duration resetTimeout = Duration.ofSeconds(30);
        private Duration maxBackoff = Duration.ofMinutes(5);
        private double backoffMultiplier = 2.0;

        /**
         * The operator to protect with a circuit breaker.
         */
        public Builder operator(UnifiedOperator operator) {
            this.operator = operator;
            return this;
        }

        /**
         * ActiveJ eventloop required for scheduling HALF_OPEN probe delays.
         */
        public Builder eventloop(Eventloop eventloop) {
            this.eventloop = eventloop;
            return this;
        }

        /**
         * Unique name for this circuit breaker instance (used in metrics and logs).
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Number of consecutive failures before opening the circuit. Default: 5.
         */
        public Builder failureThreshold(int failureThreshold) {
            if (failureThreshold < 1) throw new IllegalArgumentException("failureThreshold must be >= 1");
            this.failureThreshold = failureThreshold;
            return this;
        }

        /**
         * Number of consecutive successes in HALF_OPEN to close the circuit. Default: 2.
         */
        public Builder successThreshold(int successThreshold) {
            if (successThreshold < 1) throw new IllegalArgumentException("successThreshold must be >= 1");
            this.successThreshold = successThreshold;
            return this;
        }

        /**
         * Time to wait in OPEN state before attempting a probe. Default: 30s.
         */
        public Builder resetTimeout(Duration resetTimeout) {
            this.resetTimeout = Objects.requireNonNull(resetTimeout);
            return this;
        }

        /**
         * Maximum backoff duration for repeated OPEN cycles. Default: 5m.
         */
        public Builder maxBackoff(Duration maxBackoff) {
            this.maxBackoff = Objects.requireNonNull(maxBackoff);
            return this;
        }

        /**
         * Multiplier applied to resetTimeout on each successive OPEN cycle. Default: 2.0.
         */
        public Builder backoffMultiplier(double backoffMultiplier) {
            if (backoffMultiplier < 1.0) throw new IllegalArgumentException("backoffMultiplier must be >= 1.0");
            this.backoffMultiplier = backoffMultiplier;
            return this;
        }

        /**
         * Apply a named profile preset: {@code STRICT}, {@code STANDARD}, or {@code RELAXED}.
         * Individual properties set after calling this method override the profile defaults.
         */
        public Builder profile(String profile) {
            CircuitBreaker cb = CircuitBreakerProfiles.forProfile("_probe_", profile);
            // Re-read settings by constructing a probe; unfortunately we must mirror the values here
            switch (profile == null ? CircuitBreakerProfiles.STANDARD : profile.toUpperCase()) {
                case CircuitBreakerProfiles.STRICT ->
                    { failureThreshold = 3; successThreshold = 2;
                      resetTimeout = Duration.ofSeconds(60); maxBackoff = Duration.ofMinutes(10); }
                case CircuitBreakerProfiles.RELAXED ->
                    { failureThreshold = 10; successThreshold = 1;
                      resetTimeout = Duration.ofSeconds(15); maxBackoff = Duration.ofMinutes(2); }
                default ->
                    { failureThreshold = 5; successThreshold = 2;
                      resetTimeout = Duration.ofSeconds(30); maxBackoff = Duration.ofMinutes(5); }
            }
            return this;
        }

        public CircuitBreakerOperator build() {
            return new CircuitBreakerOperator(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
