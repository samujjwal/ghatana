/*
 * Copyright (c) 2024 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.orchestrator.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/**
 * Day 39: JPA entity for storing individual step checkpoints within pipeline executions.
 * Enables detailed tracking of step execution for resume and debugging capabilities.
 */
@Entity
@Table(name = "step_checkpoints", indexes = {
    @Index(name = "idx_step_checkpoints_instance_id", columnList = "instance_id"),
    @Index(name = "idx_step_checkpoints_step_id", columnList = "step_id"),
    @Index(name = "idx_step_checkpoints_status", columnList = "status"),
    @Index(name = "idx_step_checkpoints_started_at", columnList = "started_at")
})
public class StepCheckpointEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "instance_id", nullable = false, length = 100)
    private String instanceId;

    @Column(name = "step_id", nullable = false, length = 100)
    private String stepId;

    @Column(name = "step_name", nullable = false, length = 200)
    private String stepName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PipelineCheckpointStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input", columnDefinition = "jsonb")
    private Map<String, Object> input;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output", columnDefinition = "jsonb")
    private Map<String, Object> output;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Version
    @Column(name = "version")
    private Long version;

    // Default constructor for JPA
    public StepCheckpointEntity()
    {
    }

    // Constructor for creating new step checkpoints
    public StepCheckpointEntity(String instanceId, String stepId, String stepName,
                              PipelineCheckpointStatus status, Map<String, Object> input) {
        this.instanceId = instanceId;
        this.stepId = stepId;
        this.stepName = stepName;
        this.status = status;
        this.input = input;
        this.startedAt = Instant.now();
        this.retryCount = 0;
    }

    // Convert to domain object
    public StepCheckpoint toDomainObject() {
        return new StepCheckpoint(
            stepId, stepName, status, input, output,
            startedAt, completedAt, errorMessage,
            retryCount != null ? retryCount : 0
        );
    }

    // Update from domain object
    public void updateFrom(StepCheckpoint stepCheckpoint) {
        this.status = stepCheckpoint.getStatus();
        this.output = stepCheckpoint.getOutput();
        this.completedAt = stepCheckpoint.getCompletedAt();
        this.errorMessage = stepCheckpoint.getErrorMessage();
        this.retryCount = stepCheckpoint.getRetryCount();
    }

    // Mark step as completed
    public void markCompleted(PipelineCheckpointStatus status, Map<String, Object> output) {
        this.status = status;
        this.output = output;
        this.completedAt = Instant.now();
    }

    // Mark step as failed
    public void markFailed(String errorMessage) {
        this.status = PipelineCheckpointStatus.STEP_FAILED;
        this.errorMessage = errorMessage;
        this.completedAt = Instant.now();
    }

    // Increment retry count
    public void incrementRetryCount() {
        this.retryCount = (this.retryCount != null ? this.retryCount : 0) + 1;
    }

    @PrePersist
    protected void onCreate() {
        if (startedAt == null) {
            startedAt = Instant.now();
        }
        if (retryCount == null) {
            retryCount = 0;
        }
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getStepId() {
        return stepId;
    }

    public void setStepId(String stepId) {
        this.stepId = stepId;
    }

    public String getStepName() {
        return stepName;
    }

    public void setStepName(String stepName) {
        this.stepName = stepName;
    }

    public PipelineCheckpointStatus getStatus() {
        return status;
    }

    public void setStatus(PipelineCheckpointStatus status) {
        this.status = status;
    }

    public Map<String, Object> getInput() {
        return input;
    }

    public void setInput(Map<String, Object> input) {
        this.input = input;
    }

    public Map<String, Object> getOutput() {
        return output;
    }

    public void setOutput(Map<String, Object> output) {
        this.output = output;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}