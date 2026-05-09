/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.api;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Phase packet contract for backend-driven phase cockpit data.
 *
 * <p>This is the canonical contract for phase cockpit data provided by the backend.
 * The frontend should consume this packet directly without reconstructing lifecycle rules.
 *
 * @doc.type class
 * @doc.purpose Backend-driven phase packet for phase cockpit
 * @doc.layer api
 * @doc.pattern Contract
 */
public final class PhasePacket {

    private final String phase;
    private final String projectId;
    private final String tenantId;
    private final String workspaceId;
    private final String lifecyclePhase;
    private final TenantTier tenantTier;
    private final Set<String> enabledPhaseFlags;
    private final List<PhaseBlocker> blockers;
    private final List<PhaseEvidence> evidence;
    private final List<GovernanceRecord> governance;
    private final List<PhaseAction> availableActions;
    private final PhaseReadiness readiness;
    private final long timestamp;

    public PhasePacket(
            @NotNull String phase,
            @NotNull String projectId,
            @NotNull String tenantId,
            @NotNull String workspaceId,
            String lifecyclePhase,
            @NotNull TenantTier tenantTier,
            @NotNull Set<String> enabledPhaseFlags,
            @NotNull List<PhaseBlocker> blockers,
            @NotNull List<PhaseEvidence> evidence,
            @NotNull List<GovernanceRecord> governance,
            @NotNull List<PhaseAction> availableActions,
            @NotNull PhaseReadiness readiness,
            long timestamp
    ) {
        this.phase = phase;
        this.projectId = projectId;
        this.tenantId = tenantId;
        this.workspaceId = workspaceId;
        this.lifecyclePhase = lifecyclePhase;
        this.tenantTier = tenantTier;
        this.enabledPhaseFlags = enabledPhaseFlags;
        this.blockers = blockers;
        this.evidence = evidence;
        this.governance = governance;
        this.availableActions = availableActions;
        this.readiness = readiness;
        this.timestamp = timestamp;
    }

    public String phase() {
        return phase;
    }

    public String projectId() {
        return projectId;
    }

    public String tenantId() {
        return tenantId;
    }

    public String workspaceId() {
        return workspaceId;
    }

    public String lifecyclePhase() {
        return lifecyclePhase;
    }

    public TenantTier tenantTier() {
        return tenantTier;
    }

    public Set<String> enabledPhaseFlags() {
        return enabledPhaseFlags;
    }

    public List<PhaseBlocker> blockers() {
        return blockers;
    }

    public List<PhaseEvidence> evidence() {
        return evidence;
    }

    public List<GovernanceRecord> governance() {
        return governance;
    }

    public List<PhaseAction> availableActions() {
        return availableActions;
    }

    public PhaseReadiness readiness() {
        return readiness;
    }

    public long timestamp() {
        return timestamp;
    }

    /**
     * Tenant tier enumeration.
     */
    public enum TenantTier {
        FREE,
        PRO,
        ENTERPRISE
    }

    /**
     * Phase blocker record.
     */
    public record PhaseBlocker(
            String id,
            String type,
            String title,
            String description,
            String severity,
            String resourceId
    ) {}

    /**
     * Phase evidence record.
     */
    public record PhaseEvidence(
            String id,
            String type,
            String title,
            String description,
            Instant timestamp,
            Map<String, Object> metadata
    ) {}

    /**
     * Governance record.
     */
    public record GovernanceRecord(
            String id,
            String type,
            String outcome,
            String actor,
            Instant timestamp,
            Map<String, Object> metadata
    ) {}

    /**
     * Phase action contract.
     */
    public record PhaseAction(
            String actionId,
            String label,
            String description,
            boolean enabled,
            String disabledReason,
            Map<String, Object> parameters
    ) {}

    /**
     * Phase readiness information.
     */
    public record PhaseReadiness(
            boolean canAdvance,
            String nextPhase,
            List<String> missingPrerequisites,
            double completenessScore
    ) {}
}
