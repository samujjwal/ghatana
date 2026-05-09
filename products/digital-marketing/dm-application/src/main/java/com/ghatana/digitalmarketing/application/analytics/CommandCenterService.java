package com.ghatana.digitalmarketing.application.analytics;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * P0-008: Command center service for dashboard next decision, blockers, readiness, and risks.
 *
 * <p>Computes the command center state to reduce cognitive load while preserving visibility:</p>
 * <ul>
 *   <li>Next decision: what action the user should take next</li>
 *   <li>Blockers: what's preventing progress</li>
 *   <li>Readiness: whether the workspace is ready for operations</li>
 *   <li>Risks: potential issues that need attention</li>
 *   <li>Stale data: data freshness indicators</li>
 *   <li>Actions: actionable items the user can take</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Command center service for dashboard (P0-008)
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public interface CommandCenterService {

    /**
     * Computes the command center state for a workspace.
     *
     * @param ctx the operation context
     * @return the command center state
     */
    Promise<CommandCenterState> computeState(DmOperationContext ctx);

    /**
     * Command center state.
     */
    record CommandCenterState(
        String workspaceId,
        WorkspaceReadiness readiness,
        NextDecision nextDecision,
        List<Blocker> blockers,
        List<Risk> risks,
        StaleDataStatus staleDataStatus,
        List<ActionItem> actions,
        Instant computedAt
    ) {}

    /**
     * Workspace readiness status.
     */
    enum WorkspaceReadiness {
        READY,           // All systems operational, ready for operations
        PARTIAL,         // Some systems degraded but operational
        BLOCKED,         // Blocked by critical issues
        DEGRADED,        // Systems degraded, requires attention
        UNAUTHORIZED,    // User lacks required permissions
        EMPTY            // Workspace has no data/config
    }

    /**
     * Next decision the user should take.
     */
    record NextDecision(
        String decisionType,
        String description,
        String priority, // high, medium, low
        String actionUrl,
        boolean requiresApproval
    ) {}

    /**
     * Blocker preventing progress.
     */
    record Blocker(
        String blockerId,
        String description,
        String severity, // critical, high, medium, low
        String category, // auth, config, data, integration, approval
        Instant blockedSince,
        boolean resolvable
    ) {}

    /**
     * Risk that needs attention.
     */
    record Risk(
        String riskId,
        String description,
        String severity, // high, medium, low
        String category, // budget, timeline, quality, compliance
        double probability,
        double impact,
        String mitigation
    ) {}

    /**
     * Stale data status.
     */
    record StaleDataStatus(
        boolean hasStaleData,
        List<StaleDataItem> staleItems,
        Instant oldestDataTimestamp
    ) {}

    /**
     * Stale data item.
     */
    record StaleDataItem(
        String dataType,
        String description,
        Instant lastUpdated,
        java.time.Duration staleness
    ) {}

    /**
     * Actionable item the user can take.
     */
    record ActionItem(
        String actionId,
        String description,
        String actionType,
        String actionUrl,
        String priority
    ) {}
}
