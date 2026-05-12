/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.services.platform;

/**
 * Typed client for platform policy and guardrails operations.
 * Handles communication with Data Cloud+AEP policy evaluation services.
 *
 * @doc.type interface
 * @doc.purpose Typed client for platform policy and guardrails operations
 * @doc.layer product
 * @doc.pattern Client
 */
public interface PlatformPolicyClient {

    /**
     * Evaluates policy/guardrails for a request (policy/guardrails).
     *
     * @param request The policy evaluation request
     * @return PlatformPolicy containing the policy decision
     */
    PlatformPolicy evaluatePolicy(PlatformPolicy.PolicyRequest request);
}
