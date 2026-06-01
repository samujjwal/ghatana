/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.agent.capability;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * WS9-4: Defines execution policy constraints for agent runs.
 *
 * <p>Execution policies control when and how agents can execute, including
 * approval requirements, resource limits, retry behavior, and safety constraints.
 *
 * @doc.type record
 * @doc.purpose Agent execution policy definition for governance
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record AgentExecutionPolicy(
        String policyId,
        String displayName,
        boolean requiresHumanApproval,
        Set<String> requiredApproverRoles,
        Duration maxExecutionTime,
        int maxRetries,
        Duration retryBackoff,
        boolean allowParallelExecution,
        boolean requireIdempotency,
        boolean requireAuditTrail,
        Map<String, String> safetyConstraints,
        Map<String, String> metadata
) {
    public AgentExecutionPolicy {
        if (policyId == null || policyId.isBlank()) {
            throw new IllegalArgumentException("policyId must not be blank");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        if (requiredApproverRoles == null) {
            requiredApproverRoles = Set.of();
        }
        if (safetyConstraints == null) {
            safetyConstraints = Map.of();
        }
        if (metadata == null) {
            metadata = Map.of();
        }
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must be non-negative");
        }
    }

    /**
     * Returns true if this policy requires approval from the given role.
     */
    public boolean requiresApprovalFrom(String role) {
        return requiresHumanApproval && requiredApproverRoles.contains(role);
    }

    /**
     * Returns a default execution policy with minimal constraints.
     */
    public static AgentExecutionPolicy defaultPolicy() {
        return new AgentExecutionPolicy(
            "default",
            "Default Execution Policy",
            false,
            Set.of(),
            Duration.ofMinutes(30),
            3,
            Duration.ofSeconds(5),
            false,
            true,
            true,
            Map.of(),
            Map.of()
        );
    }
}
