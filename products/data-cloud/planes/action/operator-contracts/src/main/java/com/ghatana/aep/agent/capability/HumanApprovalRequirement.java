/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.agent.capability;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * WS9-4: Defines human approval requirements for agent actions.
 *
 * <p>Human approval requirements specify when agent actions need human review,
 * who can approve, timeout policies, and escalation behavior.
 *
 * @doc.type record
 * @doc.purpose Human approval requirement definition for agent governance
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record HumanApprovalRequirement(
        String requirementId,
        String displayName,
        Set<String> approvableActions,
        Set<String> requiredApproverRoles,
        Duration approvalTimeout,
        boolean autoRejectOnTimeout,
        boolean allowDelegation,
        String escalationPolicy,
        Map<String, String> metadata
) {
    public HumanApprovalRequirement {
        if (requirementId == null || requirementId.isBlank()) {
            throw new IllegalArgumentException("requirementId must not be blank");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        if (approvableActions == null) {
            approvableActions = Set.of();
        }
        if (requiredApproverRoles == null) {
            requiredApproverRoles = Set.of();
        }
        if (metadata == null) {
            metadata = Map.of();
        }
    }

    /**
     * Returns true if the given action requires approval.
     */
    public boolean requiresApproval(String action) {
        return approvableActions.contains(action);
    }

    /**
     * Returns true if the given role can approve actions.
     */
    public boolean canApprove(String role) {
        return requiredApproverRoles.contains(role);
    }

    /**
     * Returns a default approval requirement with no approval needed.
     */
    public static HumanApprovalRequirement noApprovalRequired() {
        return new HumanApprovalRequirement(
            "none",
            "No Approval Required",
            Set.of(),
            Set.of(),
            Duration.ZERO,
            false,
            false,
            "none",
            Map.of()
        );
    }
}
