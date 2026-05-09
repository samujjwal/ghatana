/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.domain;

import org.jetbrains.annotations.NotNull;

/**
 * Canonical WorkflowRun contract for YAPPC.
 *
 * <p>This is the canonical contract for workflow runs across all YAPPC modules.
 * Workflow runs represent individual phase executions within the lifecycle.
 *
 * @doc.type class
 * @doc.purpose Canonical WorkflowRun contract for YAPPC
 * @doc.layer domain
 * @doc.pattern Domain Model
 */
public final class WorkflowRun {

    private final String runId;
    private final String tenantId;
    private final String workspaceId;
    private final String projectId;
    private final String phase;
    private final String actorId;
    private final String correlationId;
    private final WorkflowStatus status;
    private final long startedAt;
    private final long completedAt;
    private final long createdAt;

    public WorkflowRun(
            @NotNull String runId,
            @NotNull String tenantId,
            @NotNull String workspaceId,
            @NotNull String projectId,
            @NotNull String phase,
            @NotNull String actorId,
            @NotNull String correlationId,
            @NotNull WorkflowStatus status,
            long startedAt,
            long completedAt,
            long createdAt
    ) {
        this.runId = runId;
        this.tenantId = tenantId;
        this.workspaceId = workspaceId;
        this.projectId = projectId;
        this.phase = phase;
        this.actorId = actorId;
        this.correlationId = correlationId;
        this.status = status;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.createdAt = createdAt;
    }

    public String runId() {
        return runId;
    }

    public String tenantId() {
        return tenantId;
    }

    public String workspaceId() {
        return workspaceId;
    }

    public String projectId() {
        return projectId;
    }

    public String phase() {
        return phase;
    }

    public String actorId() {
        return actorId;
    }

    public String correlationId() {
        return correlationId;
    }

    public WorkflowStatus status() {
        return status;
    }

    public long startedAt() {
        return startedAt;
    }

    public long completedAt() {
        return completedAt;
    }

    public long createdAt() {
        return createdAt;
    }

    /**
     * Workflow status enumeration.
     */
    public enum WorkflowStatus {
        PENDING,
        RUNNING,
        SUCCESS,
        FAILED,
        CANCELLED,
        SKIPPED
    }
}
