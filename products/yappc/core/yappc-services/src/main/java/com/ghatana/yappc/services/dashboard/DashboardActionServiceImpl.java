/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.services.dashboard;

import com.ghatana.platform.governance.security.Principal;
import com.ghatana.yappc.api.DashboardAction;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of dashboard action service.
 *
 * <p>This service determines which actions should be available on the dashboard
 * based on project state, user capabilities, and lifecycle phase.
 *
 * @doc.type class
 * @doc.purpose Dashboard action service implementation
 * @doc.layer service
 * @doc.pattern Service
 */
public final class DashboardActionServiceImpl implements DashboardActionService {

    private static final Logger log = LoggerFactory.getLogger(DashboardActionServiceImpl.class);

    @Override
    public Promise<List<DashboardAction.ProjectDashboardActions>> buildDashboardActions(
            String workspaceId,
            Principal principal,
            String correlationId
    ) {
        // TODO: Implement actual dashboard action logic
        // This is a placeholder that returns empty actions
        // In production, this would:
        // 1. Query projects in the workspace
        // 2. Determine lifecycle phase for each project
        // 3. Check user capabilities for each project
        // 4. Classify actions based on blockers, readiness, and governance
        // 5. Return the classified actions
        
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
        // TODO: Implement actual project dashboard action logic
        // This is a placeholder that returns default actions
        // In production, this would:
        // 1. Query project state and lifecycle phase
        // 2. Check for blockers and readiness
        // 3. Determine user capabilities
        // 4. Classify actions as blocked, review-required, or safe-to-continue
        // 5. Return the primary action with reason label
        
        log.info("Building project dashboard actions for project={}, workspace={}, actor={}, correlationId={}",
            projectId, workspaceId, principal.getName(), correlationId);
        
        // Placeholder: Return default actions
        return Promise.of(new DashboardAction.ProjectDashboardActions(
            projectId,
            "Project Name", // TODO: Fetch from project service
            "shape", // Default primary action
            List.of(), // No blocked actions
            List.of(), // No review-required actions
            List.of("shape", "validate", "generate"), // Safe to continue
            "Ready to proceed", // Reason label
            false, // Not degraded
            Instant.now().toEpochMilli()
        ));
    }

    /**
     * Determines the primary action for a project based on lifecycle phase and state.
     */
    private String determinePrimaryAction(String phase, boolean hasBlockers, boolean isDegraded) {
        if (isDegraded) {
            return "review"; // Degraded state requires review
        }
        
        if (hasBlockers) {
            return "resolve-blockers"; // Blockers need resolution
        }
        
        return switch (phase.toLowerCase()) {
            case "intent" -> "shape";
            case "shape" -> "validate";
            case "validate" -> "generate";
            case "generate" -> "run";
            case "run" -> "observe";
            case "observe" -> "learn";
            case "learn" -> "evolve";
            case "evolve" -> "shape"; // Loop back
            default -> "shape";
        };
    }

    /**
     * Classifies actions based on project state and user capabilities.
     */
    private Map<String, DashboardAction.ActionKind> classifyActions(
            String phase,
            boolean hasBlockers,
            String userRole
    ) {
        Map<String, DashboardAction.ActionKind> classification = new HashMap<>();
        
        // VIEWER role: only read actions are safe
        if ("VIEWER".equalsIgnoreCase(userRole)) {
            classification.put("view", DashboardAction.ActionKind.SAFE_TO_CONTINUE);
            return classification;
        }
        
        // DEVELOPER/ADMIN/OWNER roles
        if (hasBlockers) {
            classification.put("resolve-blockers", DashboardAction.ActionKind.BLOCKER);
            classification.put("view", DashboardAction.ActionKind.SAFE_TO_CONTINUE);
        } else {
            String nextPhase = determinePrimaryAction(phase, false, false);
            classification.put(nextPhase, DashboardAction.ActionKind.PRIMARY);
            classification.put("view", DashboardAction.ActionKind.SAFE_TO_CONTINUE);
        }
        
        return classification;
    }
}
