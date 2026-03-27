/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.pac;

import java.util.List;

/**
 * The result of a policy evaluation.
 *
 * @param allowed    {@code true} if the policy permits the requested action
 * @param policyName the policy that was evaluated
 * @param reasons    human-readable explanation clauses (empty when allowed)
 * @param riskScore  0–100 normalised risk score; 0 = no risk, 100 = maximum risk
 *
 * @doc.type record
 * @doc.purpose Immutable result of a policy evaluation containing decision and reasons
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record PolicyEvalResult(
    boolean allowed,
    String policyName,
    List<String> reasons,
    int riskScore
) {
    /**
     * Canonical constructor — enforces risk score bounds and immutable reasons list.
     */
    public PolicyEvalResult {
        if (riskScore < 0 || riskScore > 100) {
            throw new IllegalArgumentException("riskScore must be between 0 and 100, got: " + riskScore);
        }
        reasons = List.copyOf(reasons);
    }

    /** Convenience factory for a simple allow result. */
    public static PolicyEvalResult allow(String policyName) {
        return new PolicyEvalResult(true, policyName, List.of(), 0);
    }

    /** Convenience factory for a deny result with reasons. */
    public static PolicyEvalResult deny(String policyName, List<String> reasons, int riskScore) {
        return new PolicyEvalResult(false, policyName, reasons, riskScore);
    }
}
