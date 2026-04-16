/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.pac;

import com.ghatana.platform.resilience.CircuitBreaker;
import com.ghatana.platform.resilience.CircuitBreakerProfiles;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Circuit-breaker decorator for {@link PolicyAsCodeEngine} that fails closed
 * when the underlying policy store is unavailable.
 *
 * <p>Successful policy evaluations are passed through unchanged. Evaluation
 * failures and open-circuit rejections are converted into deny decisions so
 * downstream callers can reject unsafe operations without repeatedly hammering
 * an unavailable policy backend.
 *
 * @doc.type class
 * @doc.purpose Protect policy evaluation with a circuit breaker and fail-closed fallback
 * @doc.layer platform
 * @doc.pattern Decorator
 */
public final class CircuitBreakingPolicyAsCodeEngine implements PolicyAsCodeEngine {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakingPolicyAsCodeEngine.class);
    private static final String FAILURE_REASON = "Policy engine unavailable; failing closed";

    private final PolicyAsCodeEngine delegate;
    private final Eventloop eventloop;
    private final CircuitBreaker circuitBreaker;

    /**
     * Creates a decorator with a strict resilience profile because policy
     * enforcement is on the authorization path and should fail fast.
     */
    public CircuitBreakingPolicyAsCodeEngine(PolicyAsCodeEngine delegate, Eventloop eventloop) {
        this(delegate, eventloop, CircuitBreakerProfiles.strict("policy-as-code-engine"));
    }

    /**
     * Visible for tests and targeted overrides.
     */
    public CircuitBreakingPolicyAsCodeEngine(
            PolicyAsCodeEngine delegate,
            Eventloop eventloop,
            CircuitBreaker circuitBreaker) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop");
        this.circuitBreaker = Objects.requireNonNull(circuitBreaker, "circuitBreaker");
    }

    @Override
    public Promise<PolicyEvalResult> evaluate(String tenantId, String policyName, Map<String, Object> input) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(policyName, "policyName");
        Objects.requireNonNull(input, "input");

        return circuitBreaker.execute(eventloop, () -> delegate.evaluate(tenantId, policyName, input))
                .then(
                        Promise::of,
                        error -> {
                            log.warn("[pac] Evaluation failed for tenant={} policy={}: {}",
                                    tenantId, policyName, error.getMessage());
                            return Promise.of(PolicyEvalResult.deny(policyName, List.of(FAILURE_REASON), 100));
                        }
                );
    }

    CircuitBreaker.State circuitState() {
        return circuitBreaker.getState();
    }
}