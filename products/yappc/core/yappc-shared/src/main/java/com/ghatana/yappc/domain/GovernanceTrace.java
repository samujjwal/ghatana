/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.domain;

import org.jetbrains.annotations.NotNull;

/**
 * Canonical GovernanceTrace contract for YAPPC.
 *
 * <p>This is the canonical contract for governance traces across all YAPPC modules.
 * Governance traces capture audit events, compliance assessments, and security findings.
 *
 * @doc.type class
 * @doc.purpose Canonical GovernanceTrace contract for YAPPC
 * @doc.layer domain
 * @doc.pattern Domain Model
 */
public final class GovernanceTrace {

    private final String traceId;
    private final String tenantId;
    private final String workspaceId;
    private final String projectId;
    private final String actorId;
    private final String correlationId;
    private final TraceType type;
    private final TraceOutcome outcome;
    private final String resourceType;
    private final String resourceId;
    private final String message;
    private final long timestamp;
    private final long createdAt;

    public GovernanceTrace(
            @NotNull String traceId,
            @NotNull String tenantId,
            @NotNull String workspaceId,
            @NotNull String projectId,
            @NotNull String actorId,
            @NotNull String correlationId,
            @NotNull TraceType type,
            @NotNull TraceOutcome outcome,
            @NotNull String resourceType,
            @NotNull String resourceId,
            @NotNull String message,
            long timestamp,
            long createdAt
    ) {
        this.traceId = traceId;
        this.tenantId = tenantId;
        this.workspaceId = workspaceId;
        this.projectId = projectId;
        this.actorId = actorId;
        this.correlationId = correlationId;
        this.type = type;
        this.outcome = outcome;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.message = message;
        this.timestamp = timestamp;
        this.createdAt = createdAt;
    }

    public String traceId() {
        return traceId;
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

    public TraceType type() {
        return type;
    }

    public TraceOutcome outcome() {
        return outcome;
    }

    public String resourceType() {
        return resourceType;
    }

    public String resourceId() {
        return resourceId;
    }

    public String message() {
        return message;
    }

    public long timestamp() {
        return timestamp;
    }

    public long createdAt() {
        return createdAt;
    }

    /**
     * Trace type enumeration.
     */
    public enum TraceType {
        AUDIT_EVENT,
        COMPLIANCE_ASSESSMENT,
        SECURITY_SCAN,
        ACCESS_CONTROL,
        DATA_CLASSIFICATION,
        APPROVAL_REQUEST,
        POLICY_EVALUATION
    }

    /**
     * Trace outcome enumeration.
     */
    public enum TraceOutcome {
        SUCCESS,
        FAILURE,
        WARNING,
        DENIED,
        PENDING,
        APPROVED,
        REJECTED
    }
}
