/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC Lifecycle Service
 */
package com.ghatana.yappc.services.lifecycle;

import com.ghatana.yappc.api.PhasePacket;

import java.util.Set;

/**
 * Input parameters for a lifecycle phase advance request.
 *
 * @param projectId   unique identifier of the YAPPC project
 * @param fromPhase   current phase of the project (e.g., {@code "intent"})
 * @param toPhase     requested target phase (e.g., {@code "context"})
 * @param tenantId    tenant owning the project
 * @param requestedBy user ID or agent ID requesting the transition
 * @param workspaceId workspace scope for capability evaluation
 * @param tenantTier tenant tier for action entitlement checks
 * @param enabledPhaseFlags backend-enabled phase flags for action entitlement checks
 * @param idempotencyKey caller-supplied retry key for primary phase actions
 *
 * @doc.type class
 * @doc.purpose Value object carrying phase advance request parameters
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record TransitionRequest(
    String projectId,
    String fromPhase,
    String toPhase,
    String tenantId,
    String requestedBy,
    String workspaceId,
    PhasePacket.TenantTier tenantTier,
    Set<String> enabledPhaseFlags,
    String idempotencyKey
) {
    public TransitionRequest(
            String projectId,
            String fromPhase,
            String toPhase,
            String tenantId,
            String requestedBy
    ) {
        this(projectId, fromPhase, toPhase, tenantId, requestedBy, null, PhasePacket.TenantTier.PRO, Set.of(), null);
    }

    public TransitionRequest(
            String projectId,
            String fromPhase,
            String toPhase,
            String tenantId,
            String requestedBy,
            String workspaceId,
            PhasePacket.TenantTier tenantTier,
            Set<String> enabledPhaseFlags
    ) {
        this(projectId, fromPhase, toPhase, tenantId, requestedBy, workspaceId, tenantTier, enabledPhaseFlags, null);
    }

    public TransitionRequest {
        tenantTier = tenantTier != null ? tenantTier : PhasePacket.TenantTier.PRO;
        enabledPhaseFlags = enabledPhaseFlags != null ? Set.copyOf(enabledPhaseFlags) : Set.of();
        idempotencyKey = idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey.trim();
    }
}
