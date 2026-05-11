/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.services.dashboard;

import com.ghatana.platform.governance.security.Principal;
import com.ghatana.yappc.api.DashboardAction;
import com.ghatana.yappc.api.PhasePacket;
import com.ghatana.yappc.services.phase.PhasePacketService;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implementation of dashboard action service.
 *
 * <p>This service determines which actions should be available on the dashboard
 * based on project state, user capabilities, and lifecycle phase.
 *
 * Actions are derived from the canonical phase packet data to ensure consistency
 * between dashboard and phase cockpit views.
 *
 * @doc.type class
 * @doc.purpose Dashboard action service implementation
 * @doc.layer service
 * @doc.pattern Service
 */
public final class DashboardActionServiceImpl implements DashboardActionService {

    private static final Logger log = LoggerFactory.getLogger(DashboardActionServiceImpl.class);

    private final PhasePacketService phasePacketService;

    public DashboardActionServiceImpl(@NotNull PhasePacketService phasePacketService) {
        this.phasePacketService = phasePacketService;
    }

    @Override
    public Promise<List<DashboardAction.ProjectDashboardActions>> buildDashboardActions(
            String workspaceId,
            Principal principal,
            String correlationId
    ) {
        // TODO: Implement actual dashboard action logic for workspace-level actions
        // This requires a project repository to list projects in the workspace
        // For now, return empty list as workspace-level dashboard is not yet implemented
        
        log.info("Building dashboard actions for workspace={}, actor={}, correlationId={}",
            workspaceId, principal.getName(), correlationId);
        
        return Promise.of(new ArrayList<>());
    }

    @Override
    public Promise<DashboardAction.ProjectDashboardActions> buildProjectDashboardActions(
            String projectId,
            String workspaceId,
            Principal principal,
            String correlationId
    ) {
        log.info("Building project dashboard actions for project={}, workspace={}, actor={}, correlationId={}",
            projectId, workspaceId, principal.getName(), correlationId);
        
        // Derive actions from the canonical phase packet
        // Use the current phase from the packet to get the most up-to-date state
        return phasePacketService.buildPhasePacket("intent", projectId, workspaceId, principal, correlationId)
            .map(packet -> deriveActionsFromPacket(packet));
    }

    /**
     * Derives dashboard actions from the canonical phase packet.
     *
     * This ensures that dashboard actions are consistent with the phase cockpit
     * and are derived from the same backend data source.
     */
    private DashboardAction.ProjectDashboardActions deriveActionsFromPacket(PhasePacket packet) {
        // Extract action data from phase packet
        List<String> blockedActions = new ArrayList<>();
        List<String> reviewRequiredActions = new ArrayList<>();
        List<String> safeToContinueActions = new ArrayList<>();
        
        // Classify available actions based on blockers, readiness, and governance
        if (packet.blockers() != null && !packet.blockers().isEmpty()) {
            blockedActions.addAll(packet.blockers().stream()
                .map(b -> b.id())
                .toList());
        }
        
        if (packet.governance() != null && !packet.governance().isEmpty()) {
            reviewRequiredActions.addAll(packet.governance().stream()
                .filter(g -> "pending".equalsIgnoreCase(g.outcome()) || "review".equalsIgnoreCase(g.type()))
                .map(g -> g.id())
                .toList());
        }
        
        if (packet.availableActions() != null) {
            safeToContinueActions.addAll(packet.availableActions().stream()
                .filter(PhasePacket.PhaseAction::enabled)
                .map(PhasePacket.PhaseAction::actionId)
                .toList());
        }
        
        // Determine primary action based on readiness and blockers
        String primaryAction = determinePrimaryAction(packet);
        String reasonLabel = determineReasonLabel(packet);
        boolean isDegraded = determineDegradedState(packet);
        
        return new DashboardAction.ProjectDashboardActions(
            packet.projectId(),
            packet.projectName(),
            primaryAction,
            blockedActions,
            reviewRequiredActions,
            safeToContinueActions,
            reasonLabel,
            isDegraded,
            packet.generatedAt()
        );
    }

    /**
     * Determines the primary action for a project based on phase packet state.
     */
    private String determinePrimaryAction(PhasePacket packet) {
        if (packet.readiness() != null && !packet.readiness().canAdvance()) {
            return "resolve-blockers";
        }
        
        if (packet.readiness() != null && packet.readiness().nextPhase() != null) {
            return packet.readiness().nextPhase().toLowerCase();
        }
        
        return packet.phase().toLowerCase();
    }

    /**
     * Determines the reason label based on phase packet state.
     */
    private String determineReasonLabel(PhasePacket packet) {
        if (packet.blockers() != null && !packet.blockers().isEmpty()) {
            return "Blocked: " + packet.blockers().get(0).title();
        }
        
        if (packet.readiness() != null && packet.readiness().canAdvance()) {
            return "Ready to proceed to " + packet.readiness().nextPhase();
        }
        
        return "Continue with " + packet.phase();
    }

    /**
     * Determines if the project is in a degraded state.
     */
    private boolean determineDegradedState(PhasePacket packet) {
        if (packet.healthSignals() == null) {
            return false;
        }
        
        return !packet.healthSignals().preview().healthy()
            || !packet.healthSignals().generation().healthy()
            || !packet.healthSignals().runtime().healthy();
    }
}
