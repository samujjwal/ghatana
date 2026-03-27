/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.pac;

import io.activej.promise.Promise;
import java.util.Map;
import java.util.function.Function;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link PolicyAsCodeEngine} for development and testing.
 *
 * <p>Policies are registered as Java {@link Function}s that accept the input map
 * and return a {@link PolicyEvalResult}. Unregistered policies default to DENY.
 *
 * @doc.type class
 * @doc.purpose In-memory, function-based policy engine for dev/test
 * @doc.layer platform
 * @doc.pattern Service
 */
public final class InMemoryPolicyEngine implements PolicyAsCodeEngine {

    private final Map<String, Function<Map<String, Object>, PolicyEvalResult>> policies =
        new ConcurrentHashMap<>();

    /**
     * Register a policy rule for a given policy name.
     *
     * @param policyName the policy identifier
     * @param rule       function receiving the input map and returning an evaluation result
     */
    public void register(String policyName, Function<Map<String, Object>, PolicyEvalResult> rule) {
        policies.put(policyName, rule);
    }

    @Override
    public Promise<PolicyEvalResult> evaluate(
            String tenantId, String policyName, Map<String, Object> input) {
        Function<Map<String, Object>, PolicyEvalResult> rule = policies.get(policyName);
        if (rule == null) {
            return Promise.of(PolicyEvalResult.deny(
                policyName,
                java.util.List.of("No policy registered for: " + policyName),
                100));
        }
        try {
            return Promise.of(rule.apply(input));
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }
}
