/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Pass 9: Unified operation trace model for cross-plane observability.
 *
 * <p>Provides canonical operation tracking across Data Cloud, AEP, agents, and media.
 * Each operation gets a unique operationId and traceId for end-to-end correlation.
 *
 * @doc.type class
 * @doc.purpose Unified cross-plane operation trace model
 * @doc.layer product
 * @doc.pattern Entity, Observability
 */
@Entity
@Table(name = "operation_traces", indexes = {
    @Index(name = "idx_operation_trace_id", columnList = "operationId"),
    @Index(name = "idx_trace_id", columnList = "traceId"),
    @Index(name = "idx_tenant_id", columnList = "tenantId"),
    @Index(name = "idx_plane", columnList = "plane"),
    @Index(name = "idx_operation_kind", columnList = "operationKind"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_started_at", columnList = "startedAt"),
    @Index(name = "idx_correlation_id", columnList = "correlationId")
})
public class OperationTrace {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Canonical operation identifier (unique per operation).
     */
    @Column(nullable = false, unique = true, updatable = false)
    private String operationId;

    /**
     * Trace identifier for request correlation (shared across related operations).
     */
    @Column(nullable = false)
    private String traceId;

    /**
     * Request identifier for the original request that triggered this operation.
     */
    @Column(nullable = false)
    private String requestId;

    /**
     * Tenant scope for multi-tenancy.
     */
    @Column(nullable = false)
    private String tenantId;

    /**
     * Principal/user who initiated the operation.
     */
    @Column(nullable = false)
    private String principalId;

    /**
     * Plane identifier (data-plane, action-plane, event-plane, governance-plane).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OperationPlane plane;

    /**
     * Operation kind (workflow, media, agent, connector, pipeline, etc.).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OperationKind operationKind;

    /**
     * Operation type (specific operation within the kind).
     */
    @Column(nullable = false)
    private String operationType;

    /**
     * Operation status.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OperationStatus status;

    /**
     * Timestamp when operation started.
     */
    @Column(nullable = false)
    private Instant startedAt;

    /**
     * Timestamp when operation completed (null if still running).
     */
    private Instant completedAt;

    /**
     * Duration in milliseconds (null if still running).
     */
    private Long durationMs;

    /**
     * Correlation ID for linking to external systems.
     */
    private String correlationId;

    /**
     * Parent operation ID for nested operations.
     */
    private String parentOperationId;

    /**
     * Event offset for event-plane operations.
     */
    private Long eventOffset;

    /**
     * Pipeline execution ID for workflow operations.
     */
    private String pipelineExecutionId;

    /**
     * Agent invocation ID for agent operations.
     */
    private String agentInvocationId;

    /**
     * Media job ID for media processing operations.
     */
    private String mediaJobId;

    /**
     * Collection ID for data-plane operations.
     */
    private String collectionId;

    /**
     * Entity ID for entity-level operations.
     */
    private String entityId;

    /**
     * Error message if operation failed.
     */
    @Column(length = 4000)
    private String errorMessage;

    /**
     * Error code for categorization.
     */
    private String errorCode;

    /**
     * Additional metadata as JSON.
     */
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    /**
     * Default constructor for JPA.
     */
    protected OperationTrace() {}

    /**
     * Builder constructor.
     */
    private OperationTrace(Builder builder) {
        this.operationId = builder.operationId;
        this.traceId = builder.traceId;
        this.requestId = builder.requestId;
        this.tenantId = builder.tenantId;
        this.principalId = builder.principalId;
        this.plane = builder.plane;
        this.operationKind = builder.operationKind;
        this.operationType = builder.operationType;
        this.status = builder.status;
        this.startedAt = builder.startedAt;
        this.completedAt = builder.completedAt;
        this.durationMs = builder.durationMs;
        this.correlationId = builder.correlationId;
        this.parentOperationId = builder.parentOperationId;
        this.eventOffset = builder.eventOffset;
        this.pipelineExecutionId = builder.pipelineExecutionId;
        this.agentInvocationId = builder.agentInvocationId;
        this.mediaJobId = builder.mediaJobId;
        this.collectionId = builder.collectionId;
        this.entityId = builder.entityId;
        this.errorMessage = builder.errorMessage;
        this.errorCode = builder.errorCode;
        this.metadata = builder.metadata;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Mark operation as completed successfully.
     */
    public void markCompleted() {
        this.status = OperationStatus.COMPLETED;
        this.completedAt = Instant.now();
        this.durationMs = java.time.Duration.between(startedAt, completedAt).toMillis();
    }

    /**
     * Mark operation as failed.
     */
    public void markFailed(String errorMessage, String errorCode) {
        this.status = OperationStatus.FAILED;
        this.completedAt = Instant.now();
        this.durationMs = java.time.Duration.between(startedAt, completedAt).toMillis();
        this.errorMessage = errorMessage;
        this.errorCode = errorCode;
    }

    /**
     * Mark operation as cancelled.
     */
    public void markCancelled() {
        this.status = OperationStatus.CANCELLED;
        this.completedAt = Instant.now();
        this.durationMs = java.time.Duration.between(startedAt, completedAt).toMillis();
    }

    // Getters
    public UUID getId() { return id; }
    public String getOperationId() { return operationId; }
    public String getTraceId() { return traceId; }
    public String getRequestId() { return requestId; }
    public String getTenantId() { return tenantId; }
    public String getPrincipalId() { return principalId; }
    public OperationPlane getPlane() { return plane; }
    public OperationKind getOperationKind() { return operationKind; }
    public String getOperationType() { return operationType; }
    public OperationStatus getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Long getDurationMs() { return durationMs; }
    public String getCorrelationId() { return correlationId; }
    public String getParentOperationId() { return parentOperationId; }
    public Long getEventOffset() { return eventOffset; }
    public String getPipelineExecutionId() { return pipelineExecutionId; }
    public String getAgentInvocationId() { return agentInvocationId; }
    public String getMediaJobId() { return mediaJobId; }
    public String getCollectionId() { return collectionId; }
    public String getEntityId() { return entityId; }
    public String getErrorMessage() { return errorMessage; }
    public String getErrorCode() { return errorCode; }
    public Map<String, Object> getMetadata() { return metadata; }

    /**
     * Operation plane enumeration.
     */
    public enum OperationPlane {
        DATA_PLANE,
        ACTION_PLANE,
        EVENT_PLANE,
        GOVERNANCE_PLANE,
        PLATFORM
    }

    /**
     * Operation kind enumeration.
     */
    public enum OperationKind {
        WORKFLOW,
        MEDIA,
        AGENT,
        CONNECTOR,
        PIPELINE,
        ENTITY,
        EVENT,
        GOVERNANCE,
        PLUGIN,
        ANALYTICS
    }

    /**
     * Operation status enumeration.
     */
    public enum OperationStatus {
        INITIATED,
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED,
        TIMEOUT,
        BLOCKED
    }

    /**
     * Builder for OperationTrace.
     */
    public static class Builder {
        private String operationId;
        private String traceId;
        private String requestId;
        private String tenantId;
        private String principalId;
        private OperationPlane plane;
        private OperationKind operationKind;
        private String operationType;
        private OperationStatus status = OperationStatus.INITIATED;
        private Instant startedAt = Instant.now();
        private Instant completedAt;
        private Long durationMs;
        private String correlationId;
        private String parentOperationId;
        private Long eventOffset;
        private String pipelineExecutionId;
        private String agentInvocationId;
        private String mediaJobId;
        private String collectionId;
        private String entityId;
        private String errorMessage;
        private String errorCode;
        private Map<String, Object> metadata;

        public Builder operationId(String operationId) {
            this.operationId = operationId;
            return this;
        }

        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder principalId(String principalId) {
            this.principalId = principalId;
            return this;
        }

        public Builder plane(OperationPlane plane) {
            this.plane = plane;
            return this;
        }

        public Builder operationKind(OperationKind operationKind) {
            this.operationKind = operationKind;
            return this;
        }

        public Builder operationType(String operationType) {
            this.operationType = operationType;
            return this;
        }

        public Builder status(OperationStatus status) {
            this.status = status;
            return this;
        }

        public Builder startedAt(Instant startedAt) {
            this.startedAt = startedAt;
            return this;
        }

        public Builder completedAt(Instant completedAt) {
            this.completedAt = completedAt;
            return this;
        }

        public Builder durationMs(Long durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder parentOperationId(String parentOperationId) {
            this.parentOperationId = parentOperationId;
            return this;
        }

        public Builder eventOffset(Long eventOffset) {
            this.eventOffset = eventOffset;
            return this;
        }

        public Builder pipelineExecutionId(String pipelineExecutionId) {
            this.pipelineExecutionId = pipelineExecutionId;
            return this;
        }

        public Builder agentInvocationId(String agentInvocationId) {
            this.agentInvocationId = agentInvocationId;
            return this;
        }

        public Builder mediaJobId(String mediaJobId) {
            this.mediaJobId = mediaJobId;
            return this;
        }

        public Builder collectionId(String collectionId) {
            this.collectionId = collectionId;
            return this;
        }

        public Builder entityId(String entityId) {
            this.entityId = entityId;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder errorCode(String errorCode) {
            this.errorCode = errorCode;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public OperationTrace build() {
            if (operationId == null) {
                operationId = UUID.randomUUID().toString();
            }
            if (traceId == null) {
                traceId = UUID.randomUUID().toString();
            }
            if (requestId == null) {
                requestId = traceId;
            }
            return new OperationTrace(this);
        }
    }
}
