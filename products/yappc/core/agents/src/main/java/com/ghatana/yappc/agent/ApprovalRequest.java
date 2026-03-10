/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC Core Agents
 */
package com.ghatana.yappc.agent;

import java.time.Instant;
import java.util.List;

/**
 * Input to {@link HumanInTheLoopCoordinatorAgent} representing a lifecycle gate
 * requiring human approval.
 *
 * <p>This is the agent-layer counterpart to the lifecycle service's own
 * {@code ApprovalRequest} record. It includes just the fields the coordinator
 * agent needs to submit and track the request.
 *
 * @param requestId          unique ID of this approval request (assigned by the gateway on submit if empty)
 * @param tenantId           tenant owning the request
 * @param projectId          YAPPC project requiring the approval
 * @param requestingAgentId  agent that generated the blocked step (may be {@code null})
 * @param approvalType       one of {@code PHASE_ADVANCE | DEPLOYMENT | RISK_ACCEPTANCE}
 * @param fromPhase          the blocked "from" phase (applicable for PHASE_ADVANCE)
 * @param toPhase            the requested "to" phase (applicable for PHASE_ADVANCE)
 * @param blockReason        human-readable reason for the gate
 * @param unmetCriteria      list of criteria that were not satisfied
 * @param missingArtifacts   artifact IDs that are absent
 * @param expiresAt          optional SLA deadline ({@code null} means no expiry)
 *
 * @doc.type record
 * @doc.purpose Agent-layer input for the HumanInTheLoopCoordinatorAgent
 * @doc.layer product
 * @doc.pattern ValueObject
 * @doc.gaa.lifecycle act
 */
public record ApprovalRequest(
        String requestId,
        String tenantId,
        String projectId,
        String requestingAgentId,
        String approvalType,
        String fromPhase,
        String toPhase,
        String blockReason,
        List<String> unmetCriteria,
        List<String> missingArtifacts,
        Instant expiresAt
) {
    public ApprovalRequest {
        unmetCriteria    = unmetCriteria    != null ? List.copyOf(unmetCriteria)    : List.of();
        missingArtifacts = missingArtifacts != null ? List.copyOf(missingArtifacts) : List.of();
    }

    /** Supported approval types — must match the lifecycle service enum values. */
    public static final String TYPE_PHASE_ADVANCE    = "PHASE_ADVANCE";
    public static final String TYPE_DEPLOYMENT       = "DEPLOYMENT";
    public static final String TYPE_RISK_ACCEPTANCE  = "RISK_ACCEPTANCE";
}
