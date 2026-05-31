package com.ghatana.yappc.services.phase;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Typed lifecycle project snapshot for phase packet orchestration.
 *
 * @doc.type record
 * @doc.purpose Carries canonical tenant/workspace/project lifecycle context for phase services
 * @doc.layer service
 * @doc.pattern DTO
 */
public record ProjectLifecycleSnapshot(
        String tenantId,
        String workspaceId,
        String projectId,
        String projectName,
        String workspaceName,
        String lifecyclePhase,
        String tier,
        String status,
        boolean degraded,
        String degradedReason,
        Set<String> enabledPhaseFlags,
        Map<String, Object> state
) {
    public ProjectLifecycleSnapshot {
        tenantId = Objects.requireNonNullElse(tenantId, "unknown-tenant");
        workspaceId = Objects.requireNonNullElse(workspaceId, "unknown-workspace");
        projectId = Objects.requireNonNullElse(projectId, "unknown-project");
        projectName = Objects.requireNonNullElse(projectName, "Unnamed Project");
        workspaceName = Objects.requireNonNullElse(workspaceName, "Workspace-" + workspaceId);
        lifecyclePhase = Objects.requireNonNullElse(lifecyclePhase, "intent");
        tier = Objects.requireNonNullElse(tier, "FREE");
        status = Objects.requireNonNullElse(status, "active");
        degradedReason = degradedReason == null ? "" : degradedReason;
        enabledPhaseFlags = enabledPhaseFlags == null ? Set.of() : Set.copyOf(enabledPhaseFlags);
        state = state == null ? Map.of() : Map.copyOf(state);
    }
}
