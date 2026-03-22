/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */
package com.ghatana.agent.framework.spec;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A named reference to a governing policy that constrains an agent's behavior.
 *
 * <p>Policy references link an agent definition to versioned, externally managed policy
 * documents (e.g., data-classification policies, human-approval rules, memory-governance
 * policies). The runtime {@code GovernanceEngine} resolves these references at evaluation
 * time via the policy registry.
 *
 * <p>Example YAML representation:
 * <pre>{@code
 * governance:
 *   policyRefs:
 *     - id: policy.data-classification.v2
 *       description: "Controls data classification and redaction."
 *     - id: policy.human-approval.high-risk.v1
 *       description: "Requires human approval for high-risk actions."
 * }</pre>
 *
 * @see AgentSpec.GovernanceSpec
 * @see com.ghatana.agent.framework.governance.GovernanceEngine
 *
 * @doc.type record
 * @doc.purpose Named governance policy reference linking agent definitions to policy documents
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record GovernancePolicyRef(
        /** Stable, unique ID of the governing policy (e.g., {@code policy.data-classification.v2}). */
        @NotNull String id,

        /** Optional human-readable description of what this policy enforces. */
        @Nullable String description,

        /**
         * Optional enforcement mode override for this specific reference.
         * If null, the global enforcement mode of the GovernanceEngine is used.
         * Common values: {@code "hard"} (block on violation), {@code "soft"} (warn only).
         */
        @Nullable String enforcementMode
) {
    /**
     * Creates a strict policy reference with no description or mode override.
     *
     * @param id the policy ID
     * @return a minimal {@code GovernancePolicyRef}
     */
    public static GovernancePolicyRef of(@NotNull String id) {
        return new GovernancePolicyRef(id, null, null);
    }

    /**
     * Creates a policy reference with a description.
     *
     * @param id          the policy ID
     * @param description human-readable enforcement description
     * @return a {@code GovernancePolicyRef} with description
     */
    public static GovernancePolicyRef of(@NotNull String id, @Nullable String description) {
        return new GovernancePolicyRef(id, description, null);
    }
}
