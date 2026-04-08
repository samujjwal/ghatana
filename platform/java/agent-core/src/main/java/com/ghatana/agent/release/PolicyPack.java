/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.release;

import java.time.Instant;
import java.util.Set;
import java.util.Map;

/**
 * Versioned bundle of governance policies attached to an agent release.
 *
 * <p>A {@code PolicyPack} is an immutable snapshot of the policy configuration
 * that was in effect when the agent was released. It specifies which
 * {@link com.ghatana.agent.framework.governance.ActionClass} values are permitted,
 * sandbox constraints, delegation budgets, egress rules, and data classification rules.
 *
 * @param policyPackId              unique identifier for this policy pack
 * @param version                   semantic version string (e.g. {@code "1.2.0"})
 * @param allowedActionClasses      set of permitted action class names (e.g. {@code "READ"}, {@code "DRAFT"})
 * @param sandboxRules              key-value sandbox constraint rules
 * @param delegationBudgets         delegation budget entries (e.g., max delegation depth)
 * @param egressRules               egress restriction rules
 * @param dataClassificationRules   data classification enforcement rules
 * @param digest                    SHA-256 of this policy pack's canonical representation
 * @param createdAt                 when this policy pack was created
 *
 * @doc.type record
 * @doc.purpose Versioned bundle of governance policies for an agent release
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record PolicyPack(
        String policyPackId,
        String version,
        Set<String> allowedActionClasses,
        Map<String, String> sandboxRules,
        Map<String, Object> delegationBudgets,
        Map<String, String> egressRules,
        Map<String, String> dataClassificationRules,
        String digest,
        Instant createdAt
) {
    public PolicyPack {
        allowedActionClasses    = Set.copyOf(allowedActionClasses);
        sandboxRules            = Map.copyOf(sandboxRules);
        delegationBudgets       = Map.copyOf(delegationBudgets);
        egressRules             = Map.copyOf(egressRules);
        dataClassificationRules = Map.copyOf(dataClassificationRules);
    }
}
