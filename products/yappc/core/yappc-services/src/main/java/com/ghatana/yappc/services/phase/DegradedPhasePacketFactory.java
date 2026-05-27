/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.services.phase;

import com.ghatana.platform.governance.security.Principal;
import com.ghatana.yappc.api.PhasePacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Creates explicit degraded phase packets for dependency failure scenarios.
 *
 * @doc.type class
 * @doc.purpose Builds fail-closed phase packets with dependency-specific degraded details
 * @doc.layer services
 * @doc.pattern Factory
 */
public final class DegradedPhasePacketFactory {

    public PhasePacket build(
            @NotNull String phase,
            @NotNull String projectId,
            @NotNull String workspaceId,
            @NotNull Principal principal,
            @NotNull String correlationId,
            @Nullable String degradedReason
    ) {
        String reason = degradedReason == null || degradedReason.isBlank()
                ? "PHASE_PACKET_DEPENDENCY_UNAVAILABLE"
                : degradedReason;
        PhasePacket.DegradedPacketDetails degradedDetails = buildDegradedPacketDetails(reason);
        PhasePacket.ActorContext actor = buildActorContext(principal);
        PhasePacket.CapabilityModel capabilities = new PhasePacket.CapabilityModel(
                true,
                false,
                false,
                false,
                false,
                false,
                false
        );
        List<PhasePacket.PhaseBlocker> blockers = List.of(
                new PhasePacket.PhaseBlocker(
                        "data-cloud-degraded",
                        "SYSTEM",
                        "Data Cloud Service Unavailable",
                        reason,
                        "CRITICAL",
                        projectId,
                        false
                )
        );
        PhasePacket.PhaseReadiness readiness = new PhasePacket.PhaseReadiness(
                false,
                null,
                List.of("Data Cloud service unavailable"),
                0.0,
                true,
                "Blocked",
                24,
                0.35
        );
        PhasePacket.HealthSignals healthSignals = new PhasePacket.HealthSignals(
                new PhasePacket.PreviewHealth(false, "degraded", List.of(reason)),
                new PhasePacket.GenerationHealth(false, "degraded", null, List.of(reason)),
                new PhasePacket.RuntimeHealth(false, "degraded", null, List.of(reason))
        );

        return new PhasePacket(
                phase,
                projectId,
                "degraded-project",
                principal.getTenantId(),
                workspaceId,
                "degraded-workspace",
                actor,
                phase,
                PhasePacket.TenantTier.FREE,
                Set.of(),
                capabilities,
                blockers,
                readiness,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                new PhasePacket.PlatformRunStatus(
                        "unavailable-" + projectId,
                        "NOT_READY",
                        "data-cloud-aep",
                        Instant.now(),
                        null,
                        correlationId,
                        List.of()
                ),
                List.of(),
                new PhasePacket.DashboardActionClassification(null, List.of("all"), List.of(), List.of()),
                List.of(
                        new PhasePacket.PhasePanelView(
                                "generate",
                                "degraded",
                                "Phase panel data is degraded because lifecycle dependencies are unavailable.",
                                "Restore dependency health and retry packet retrieval.",
                                "backend:degraded-factory",
                                0.2,
                                correlationId,
                                List.of(
                                        new PhasePacket.PhasePanelCard(
                                                "degraded-dependency",
                                                "Dependency health",
                                                reason,
                                                "degraded",
                                                correlationId,
                                                java.util.Map.of("dependency", degradedDetails.dependency()))))),
                healthSignals,
                degradedDetails,
                Instant.now().toEpochMilli(),
                correlationId
        );
    }

    private PhasePacket.ActorContext buildActorContext(Principal principal) {
        String role = principal.getRoles() != null && !principal.getRoles().isEmpty()
                ? principal.getRoles().iterator().next()
                : "VIEWER";
        boolean isAdmin = role.equals("ADMIN") || role.equals("OWNER");
        return new PhasePacket.ActorContext(
                principal.getName(),
                principal.getName(),
                role,
                isAdmin,
                isAdmin
        );
    }

    private PhasePacket.DegradedPacketDetails buildDegradedPacketDetails(String reason) {
        String upperReason = reason.toUpperCase();
        if (upperReason.contains("KERNEL")) {
            return new PhasePacket.DegradedPacketDetails(
                    "KERNEL",
                    reason,
                    "kernel_lifecycle_truth",
                    "Restore Kernel lifecycle truth ingestion and retry phase packet retrieval.",
                    List.of("kernel-health", "phase-readiness", "phase-actions", "runtime-observe")
            );
        }
        if (upperReason.contains("AEP") || upperReason.contains("EVIDENCE") || upperReason.contains("GOVERNANCE")
                || upperReason.contains("POLICY")) {
            return new PhasePacket.DegradedPacketDetails(
                    "AEP",
                    reason,
                    "aep_evidence_and_policy",
                    "Restore AEP evidence or governance policy access before allowing lifecycle advancement.",
                    List.of("evidence", "governance", "phase-actions", "safe-advance")
            );
        }
        return new PhasePacket.DegradedPacketDetails(
                "DATA_CLOUD",
                reason,
                "projects",
                "Restore Data Cloud project state access and retry phase packet retrieval.",
                List.of("phase-readiness", "phase-actions", "artifact-status", "activity-feed")
        );
    }
}
