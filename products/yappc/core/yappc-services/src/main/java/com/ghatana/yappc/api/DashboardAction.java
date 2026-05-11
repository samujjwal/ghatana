/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.api;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Dashboard action contract for project cards and dashboard actions.
 *
 * <p>This is the canonical contract for dashboard actions provided by the backend.
 * The frontend should consume these actions directly without computing action state.
 *
 * @doc.type class
 * @doc.purpose Backend-driven dashboard action contract
 * @doc.layer api
 * @doc.pattern Contract
 */
public final class DashboardAction {

    private final String actionId;
    private final String label;
    private final String description;
    private final String phase;
    private final String projectId;
    private final ActionKind kind;
    private final boolean enabled;
    private final String disabledReason;
    private final String requiredPermission;
    private final Map<String, Object> parameters;
    private final long timestamp;

    public DashboardAction(
            @NotNull String actionId,
            @NotNull String label,
            String description,
            @NotNull String phase,
            @NotNull String projectId,
            @NotNull ActionKind kind,
            boolean enabled,
            String disabledReason,
            @NotNull String requiredPermission,
            @NotNull Map<String, Object> parameters,
            long timestamp
    ) {
        this.actionId = actionId;
        this.label = label;
        this.description = description;
        this.phase = phase;
        this.projectId = projectId;
        this.kind = kind;
        this.enabled = enabled;
        this.disabledReason = disabledReason;
        this.requiredPermission = requiredPermission;
        this.parameters = parameters;
        this.timestamp = timestamp;
    }

    public String actionId() { return actionId; }
    public String label() { return label; }
    public String description() { return description; }
    public String phase() { return phase; }
    public String projectId() { return projectId; }
    public ActionKind kind() { return kind; }
    public boolean enabled() { return enabled; }
    public String disabledReason() { return disabledReason; }
    public String requiredPermission() { return requiredPermission; }
    public Map<String, Object> parameters() { return parameters; }
    public long timestamp() { return timestamp; }

    /**
     * Action kind enumeration for dashboard classification.
     */
    public enum ActionKind {
        BLOCKER,
        REVIEW_REQUIRED,
        SAFE_TO_CONTINUE,
        PRIMARY
    }

    /**
     * Dashboard action collection for a project.
     */
    public record ProjectDashboardActions(
            String projectId,
            String projectName,
            String primaryAction,
            List<String> blockedActions,
            List<String> reviewRequiredActions,
            List<String> safeToContinueActions,
            String reasonLabel,
            boolean isDegraded,
            long timestamp
    ) {}
}
