/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.pac;

import io.activej.promise.Promise;
import java.util.Map;

/**
 * Evaluates a policy document against an input and returns an {@link PolicyEvalResult}.
 *
 * <p>Implementations are responsible for choosing the underlying policy language:
 * <ul>
 *   <li>{@link OpaClient} — delegates to an external Open Policy Agent HTTP endpoint.</li>
 *   <li>{@link InMemoryPolicyEngine} — local Rego-free rule engine for dev/test.</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose Evaluate named policies against arbitrary input maps
 * @doc.layer platform
 * @doc.pattern Service
 */
public interface PolicyAsCodeEngine {

    /**
     * Evaluate a policy.
     *
     * @param tenantId   owning tenant
     * @param policyName the policy to evaluate (e.g. "data_access", "action_approval")
     * @param input      key-value input passed to the policy evaluator
     * @return promise resolving to the evaluation result
     */
    Promise<PolicyEvalResult> evaluate(String tenantId, String policyName, Map<String, Object> input);
}
