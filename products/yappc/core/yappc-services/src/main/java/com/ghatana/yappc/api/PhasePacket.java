/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.api;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Canonical PhaseCockpitPacket contract for backend-driven phase cockpit data.
 *
 * <p>This is the canonical contract for phase cockpit data provided by the backend.
 * The frontend should consume this packet directly without reconstructing lifecycle rules.
 *
 * <p>The packet includes all context needed for rendering a phase cockpit:
 * <ul>
 *   <li>Project and workspace context</li>
 *   <li>Actor context (user performing the action)</li>
 *   <li>Current phase state</li>
 *   <li>Readiness and blockers</li>
 *   <li>Required and completed artifacts</li>
 *   <li>Activity feed</li>
 *   <li>Available actions with capability gating</li>
 *   <li>Governance state</li>
 *   <li>Data Cloud+AEP platform references (where applicable)</li>
 *   <li>Health signals</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Canonical PhaseCockpitPacket for phase cockpit
 * @doc.layer api
 * @doc.pattern Contract
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public final class PhasePacket {

    // Context fields
    private final String phase;
    private final String projectId;
    private final String projectName;
    private final String tenantId;
    private final String workspaceId;
    private final String workspaceName;
    private final ActorContext actor;
    private final String lifecyclePhase;
    
    // Tenant and capability fields
    private final TenantTier tenantTier;
    private final Set<String> enabledPhaseFlags;
    private final CapabilityModel capabilities;
    
    // State fields
    private final List<PhaseBlocker> blockers;
    private final PhaseReadiness readiness;
    private final List<RequiredArtifact> requiredArtifacts;
    private final List<CompletedArtifact> completedArtifacts;
    private final List<ActivityFeedEntry> activityFeed;
    
    // Platform integration fields
    private final List<PhaseEvidence> evidence;
    private final List<GovernanceRecord> governance;
    private final PlatformRunStatus platformRunStatus;
    
    // Action fields
    private final List<PhaseAction> availableActions;
    private final DashboardActionClassification dashboardActions;
    
    // Health signals
    private final HealthSignals healthSignals;
    private final DegradedPacketDetails degradedDetails;
    
    // Metadata
    private final long timestamp;
    private final String correlationId;

    public PhasePacket(
            @NotNull String phase,
            @NotNull String projectId,
            String projectName,
            @NotNull String tenantId,
            @NotNull String workspaceId,
            String workspaceName,
            @NotNull ActorContext actor,
            String lifecyclePhase,
            @NotNull TenantTier tenantTier,
            @NotNull Set<String> enabledPhaseFlags,
            @NotNull CapabilityModel capabilities,
            @NotNull List<PhaseBlocker> blockers,
            @NotNull PhaseReadiness readiness,
            @NotNull List<RequiredArtifact> requiredArtifacts,
            @NotNull List<CompletedArtifact> completedArtifacts,
            @NotNull List<ActivityFeedEntry> activityFeed,
            @NotNull List<PhaseEvidence> evidence,
            @NotNull List<GovernanceRecord> governance,
            PlatformRunStatus platformRunStatus,
            @NotNull List<PhaseAction> availableActions,
            @NotNull DashboardActionClassification dashboardActions,
            @NotNull HealthSignals healthSignals,
            DegradedPacketDetails degradedDetails,
            long timestamp,
            String correlationId
    ) {
        this.phase = phase;
        this.projectId = projectId;
        this.projectName = projectName;
        this.tenantId = tenantId;
        this.workspaceId = workspaceId;
        this.workspaceName = workspaceName;
        this.actor = actor;
        this.lifecyclePhase = lifecyclePhase;
        this.tenantTier = tenantTier;
        this.enabledPhaseFlags = enabledPhaseFlags;
        this.capabilities = capabilities;
        this.blockers = blockers;
        this.readiness = readiness;
        this.requiredArtifacts = requiredArtifacts;
        this.completedArtifacts = completedArtifacts;
        this.activityFeed = activityFeed;
        this.evidence = evidence;
        this.governance = governance;
        this.platformRunStatus = platformRunStatus;
        this.availableActions = availableActions;
        this.dashboardActions = dashboardActions;
        this.healthSignals = healthSignals;
        this.degradedDetails = degradedDetails;
        this.timestamp = timestamp;
        this.correlationId = correlationId;
    }

    // Getters
    public String phase() { return phase; }
    public String projectId() { return projectId; }
    public String projectName() { return projectName; }
    public String tenantId() { return tenantId; }
    public String workspaceId() { return workspaceId; }
    public String workspaceName() { return workspaceName; }
    public ActorContext actor() { return actor; }
    public String lifecyclePhase() { return lifecyclePhase; }
    public TenantTier tenantTier() { return tenantTier; }
    public Set<String> enabledPhaseFlags() { return enabledPhaseFlags; }
    public CapabilityModel capabilities() { return capabilities; }
    public List<PhaseBlocker> blockers() { return blockers; }
    public PhaseReadiness readiness() { return readiness; }
    public List<RequiredArtifact> requiredArtifacts() { return requiredArtifacts; }
    public List<CompletedArtifact> completedArtifacts() { return completedArtifacts; }
    public List<ActivityFeedEntry> activityFeed() { return activityFeed; }
    public List<PhaseEvidence> evidence() { return evidence; }
    public List<GovernanceRecord> governance() { return governance; }
    public PlatformRunStatus platformRunStatus() { return platformRunStatus; }
    public List<PhaseAction> availableActions() { return availableActions; }
    public DashboardActionClassification dashboardActions() { return dashboardActions; }
    public HealthSignals healthSignals() { return healthSignals; }
    public DegradedPacketDetails degradedDetails() { return degradedDetails; }
    public long timestamp() { return timestamp; }
    public String correlationId() { return correlationId; }

    /**
     * Tenant tier enumeration.
     */
    public enum TenantTier {
        FREE,
        PRO,
        ENTERPRISE
    }

    /**
     * Actor context information.
     */
    public record ActorContext(
            String actorId,
            String actorName,
            String role,
            boolean isOwner,
            boolean isAdmin
    ) {}

    /**
     * Capability model for the current actor.
     */
    public record CapabilityModel(
            boolean canRead,
            boolean canCreate,
            boolean canUpdate,
            boolean canDelete,
            boolean canApprove,
            boolean canReject,
            boolean canRollback
    ) {}

    /**
     * Phase blocker record.
     */
    public record PhaseBlocker(
            String id,
            String type,
            String title,
            String description,
            String severity,
            String resourceId,
            boolean resolvable
    ) {}

    /**
     * Phase readiness information.
     */
    public record PhaseReadiness(
            boolean canAdvance,
            String nextPhase,
            List<String> missingPrerequisites,
            double completenessScore,
            boolean isDegraded
    ) {}

    /**
     * Required artifact for the current phase.
     */
    public record RequiredArtifact(
            String artifactId,
            String artifactType,
            String title,
            String description,
            boolean isComplete
    ) {}

    /**
     * Completed artifact for the current phase.
     */
    public record CompletedArtifact(
            String artifactId,
            String artifactType,
            String version,
            String title,
            Instant completedAt,
            String completedBy,
            String evidenceId
    ) {}

    /**
     * Activity feed entry.
     */
    public record ActivityFeedEntry(
            String id,
            String type,
            String action,
            String summary,
            String actor,
            Instant timestamp,
            String severity
    ) {}

    /**
     * Phase evidence record from Data Cloud+AEP.
     */
    public record PhaseEvidence(
            String id,
            String type,
            String title,
            String description,
            Instant timestamp,
            Map<String, Object> metadata,
            String evidenceId
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
            Map<String, Object> metadata,
            String policyDecisionId
    ) {}

    /**
     * Data Cloud+AEP platform run status.
     */
    public record PlatformRunStatus(
            String runId,
            String status,
            String platform,
            Instant startedAt,
            Instant completedAt,
            String traceId,
            List<String> evidenceIds
    ) {}

    /**
     * Phase action contract with capability gating.
     */
    public record PhaseAction(
            String actionId,
            String label,
            String description,
            boolean enabled,
            String disabledReason,
            String requiredPermission,
            Map<String, Object> parameters
    ) {}

    /**
     * Dashboard action classification.
     */
    public record DashboardActionClassification(
            String primaryAction,
            List<String> blockedActions,
            List<String> reviewRequiredActions,
            List<String> safeToContinueActions
    ) {}

    /**
     * Health signals for preview/generation/runtime.
     */
    public record HealthSignals(
            PreviewHealth preview,
            GenerationHealth generation,
            RuntimeHealth runtime
    ) {}

    /**
     * Dependency-specific details for a degraded phase packet.
     */
    public record DegradedPacketDetails(
            String dependency,
            String reason,
            String truthSource,
            String recoveryAction,
            List<String> impactedFeatures
    ) {}

    /**
     * Preview health status.
     */
    public record PreviewHealth(
            boolean isHealthy,
            String status,
            List<String> issues
    ) {}

    /**
     * Generation health status.
     */
    public record GenerationHealth(
            boolean isHealthy,
            String status,
            String lastGeneratedAt,
            List<String> issues
    ) {}

    /**
     * Runtime health status.
     */
    public record RuntimeHealth(
            boolean isHealthy,
            String status,
            String lastDeployedAt,
            List<String> issues
    ) {}
}
