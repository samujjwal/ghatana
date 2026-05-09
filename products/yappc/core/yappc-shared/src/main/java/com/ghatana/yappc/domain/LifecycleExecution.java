/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.domain;

import org.jetbrains.annotations.NotNull;

/**
 * Canonical LifecycleExecution contract for YAPPC.
 *
 * <p>This is the canonical contract for lifecycle execution records across all YAPPC modules.
 * Lifecycle executions represent end-to-end pipeline runs with full traceability.
 *
 * @doc.type class
 * @doc.purpose Canonical LifecycleExecution contract for YAPPC
 * @doc.layer domain
 * @doc.pattern Domain Model
 */
public final class LifecycleExecution {

    private final String executionId;
    private final String tenantId;
    private final String workspaceId;
    private final String projectId;
    private final String actorId;
    private final String correlationId;
    private final String idempotencyKey;
    private final long startedAt;
    private final long completedAt;
    private final long totalDurationMs;
    private final LifecycleStatus status;
    private final long createdAt;

    public LifecycleExecution(
            @NotNull String executionId,
            @NotNull String tenantId,
            @NotNull String workspaceId,
            @NotNull String projectId,
            @NotNull String actorId,
            @NotNull String correlationId,
            @NotNull String idempotencyKey,
            long startedAt,
            long completedAt,
            long totalDurationMs,
            @NotNull LifecycleStatus status,
            long createdAt
    ) {
        this.executionId = executionId;
        this.tenantId = tenantId;
        this.workspaceId = workspaceId;
        this.projectId = projectId;
        this.actorId = actorId;
        this.correlationId = correlationId;
        this.idempotencyKey = idempotencyKey;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.totalDurationMs = totalDurationMs;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String executionId() {
        return executionId;
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

    public String actorId() {
        return actorId;
    }

    public String correlationId() {
        return correlationId;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public long startedAt() {
        return startedAt;
    }

    public long completedAt() {
        return completedAt;
    }

    public long totalDurationMs() {
        return totalDurationMs;
    }

    public LifecycleStatus status() {
        return status;
    }

    public long createdAt() {
        return createdAt;
    }

    /**
     * Lifecycle status enumeration.
     */
    public enum LifecycleStatus {
        RUNNING,
        SUCCESS,
        FAILED,
        CANCELLED,
        TIMED_OUT
    }
}
