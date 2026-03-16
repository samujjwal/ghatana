/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.rules;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Circuit-breaker configuration for the OPA evaluation service (STORY-K03-013).
 *
 * <p>Wraps OPA calls with a STRICT profile:
 * <ul>
 *   <li>Opens after 5% failure rate in a 20-call sliding window</li>
 *   <li>Half-open probe allows 3 calls every 30 s before deciding to close</li>
 *   <li>Slow-call threshold: calls slower than 300 ms count as failures</li>
 *   <li>Fallback strategy: {@code DENY} — reject request when CB is open</li>
 * </ul>
 *
 * <p>The {@code DENY} fallback is mandatory for compliance: an unavailable rules engine
 * must never silently allow a transaction through.
 *
 * @doc.type  class
 * @doc.purpose Resilience4j circuit-breaker configuration for OPA evaluations (K03-013)
 * @doc.layer kernel
 * @doc.pattern Config
 */
public final class RulesCircuitBreakerConfig {

    private static final Logger log = LoggerFactory.getLogger(RulesCircuitBreakerConfig.class);

    public static final String CB_NAME = "opa-evaluation";

    private final CircuitBreakerRegistry registry;

    public RulesCircuitBreakerConfig(CircuitBreakerRegistry registry) {
        this.registry = registry;
        initialise();
    }

    /**
     * Returns the {@link CircuitBreaker} for OPA calls. Use it to wrap every call to
     * {@link OpaEvaluationService#evaluate}.
     */
    public CircuitBreaker opaCircuitBreaker() {
        return registry.circuitBreaker(CB_NAME);
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void initialise() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                // Sliding window of 20 calls (count-based)
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(20)
                // Open after 5% failures
                .failureRateThreshold(5.0f)
                // Slow calls (>300 ms) also count as failures
                .slowCallRateThreshold(100.0f)
                .slowCallDurationThreshold(Duration.ofMillis(300))
                // Half-open: test with 3 calls
                .permittedNumberOfCallsInHalfOpenState(3)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                // Minimum calls before computing failure rate
                .minimumNumberOfCalls(5)
                .build();

        registry.addConfiguration(CB_NAME, config);
        registry.circuitBreaker(CB_NAME);

        // Register event listener for observability
        registry.circuitBreaker(CB_NAME).getEventPublisher()
                .onStateTransition(event ->
                        log.warn("Rules circuit-breaker state transition: {} -> {}",
                                event.getStateTransition().getFromState(),
                                event.getStateTransition().getToState()))
                .onError(event ->
                        log.error("Rules circuit-breaker recorded error: {}", event.getThrowable().getMessage()));
    }

    /**
     * Canonical DENY fallback result — returned when the circuit breaker is open or a
     * call fails during evaluation. Consumers must interpret a {@code null} result or
     * {@code RulesUnavailableException} as an implicit DENY.
     */
    public static final class RulesUnavailableException extends RuntimeException {
        public RulesUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }

        public RulesUnavailableException(String message) {
            super(message);
        }
    }
}
