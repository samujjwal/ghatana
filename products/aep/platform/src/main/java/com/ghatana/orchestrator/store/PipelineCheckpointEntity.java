/*
 * Copyright (c) 2024 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.orchestrator.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/**
 * Day 39: JPA entity for storing pipeline execution checkpoints in PostgreSQL.
 * Supports exactly-once semantics and checkpoint-based resume functionality.
 *
 * @doc.type class
 * @doc.purpose Multi-tenant pipeline execution checkpoint persistence
 * @doc.layer core
 * @doc.pattern Entity
 */
@Entity
@Table(name = "pipeline_checkpoints", indexes = {
    @Index(name = "idx_pipeline_checkpoints_tenant_idempotency", columnList = "tenant_id, idempotency_key", unique = true),
    @Index(name = "idx_pipeline_checkpoints_idempotency", columnList = "idempotency_key"),
    @Index(name = "idx_pipeline_checkpoints_tenant_pipeline", columnList = "tenant_id, pipeline_id"),
    @Index(name = "idx_pipeline_checkpoints_pipeline_id", columnList = "pipeline_id"),
    @Index(name = "idx_pipeline_checkpoints_status", columnList = "status"),
    @Index(name = "idx_pipeline_checkpoints_created_at", columnList = "created_at"),
    @Index(name = "idx_pipeline_checkpoints_updated_at", columnList = "updated_at")
})
public class PipelineCheckpointEntity {

    @Id
    @Column(name = "instance_id", length = 100)
    private String instanceId;

    /**
     * Tenant identifier for multi-tenancy isolation.
     * Phase 4: All checkpoint operations are now tenant-scoped.
     */
    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "pipeline_id", nullable = false, length = 100)
    private String pipelineId;

    @Column(name = "idempotency_key", nullable = false, unique = false, length = 200)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PipelineCheckpointStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "state", columnDefinition = "jsonb")
    private Map<String, Object> state;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result", columnDefinition = "jsonb")
    private Map<String, Object> result;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "current_step_id", length = 100)
    private String currentStepId;

    @Column(name = "current_step_name", length = 200)
    private String currentStepName;

    @Column(name = "completed_steps", nullable = false)
    private Integer completedSteps = 0;

    @Column(name = "total_steps", nullable = false)
    private Integer totalSteps = 0;

    @Version
    @Column(name = "version")
    private Long version;

    // Default constructor for JPA
    public PipelineCheckpointEntity()
    {
    }

    // Constructor for creating new checkpoints (with tenant)
    public PipelineCheckpointEntity(String instanceId, String tenantId, String pipelineId, String idempotencyKey,
                                  PipelineCheckpointStatus status, Map<String, Object> state) {
        this.instanceId = instanceId;
        this.tenantId = tenantId;
        this.pipelineId = pipelineId;
        this.idempotencyKey = idempotencyKey;
        this.status = status;
        this.state = state;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.completedSteps = 0;
        this.totalSteps = 0;
    }

    // Convert to domain object
    public PipelineCheckpoint toDomainObject() {
        return new PipelineCheckpoint(
            instanceId, tenantId, pipelineId, idempotencyKey, status, state, result,
            createdAt, updatedAt, currentStepId, currentStepName,
            completedSteps != null ? completedSteps : 0,
            totalSteps != null ? totalSteps : 0
        );
    }

    // Update from domain object
    public void updateFrom(PipelineCheckpoint checkpoint) {
        this.status = checkpoint.getStatus();
        this.state = checkpoint.getState();
        this.result = checkpoint.getResult();
        this.currentStepId = checkpoint.getCurrentStepId();
        this.currentStepName = checkpoint.getCurrentStepName();
        this.completedSteps = checkpoint.getCompletedSteps();
        this.totalSteps = checkpoint.getTotalSteps();
        this.updatedAt = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Getters and Setters
    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getPipelineId() {
        return pipelineId;
    }

    public void setPipelineId(String pipelineId) {
        this.pipelineId = pipelineId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public PipelineCheckpointStatus getStatus() {
        return status;
    }

    public void setStatus(PipelineCheckpointStatus status) {
        this.status = status;
    }

    public Map<String, Object> getState() {
        return state;
    }

    public void setState(Map<String, Object> state) {
        this.state = state;
    }

    public Map<String, Object> getResult() {
        return result;
    }

    public void setResult(Map<String, Object> result) {
        this.result = result;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getCurrentStepId() {
        return currentStepId;
    }

    public void setCurrentStepId(String currentStepId) {
        this.currentStepId = currentStepId;
    }

    public String getCurrentStepName() {
        return currentStepName;
    }

    public void setCurrentStepName(String currentStepName) {
        this.currentStepName = currentStepName;
    }

    public Integer getCompletedSteps() {
        return completedSteps;
    }

    public void setCompletedSteps(Integer completedSteps) {
        this.completedSteps = completedSteps;
    }

    public Integer getTotalSteps() {
        return totalSteps;
    }

    public void setTotalSteps(Integer totalSteps) {
        this.totalSteps = totalSteps;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
