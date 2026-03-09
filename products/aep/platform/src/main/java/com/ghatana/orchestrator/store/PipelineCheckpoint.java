package com.ghatana.orchestrator.store;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Domain representation of a pipeline execution checkpoint.
 * Used by repository implementations and JPA entities for conversion.
 */
public class PipelineCheckpoint {
    private final String instanceId;
    private final String tenantId;
    private final String pipelineId;
    private final String idempotencyKey;
    private final PipelineCheckpointStatus status;
    private final Map<String, Object> state;
    private final Map<String, Object> result;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final String currentStepId;
    private final String currentStepName;
    private final int completedSteps;
    private final int totalSteps;
    private java.util.List<StepCheckpoint> stepCheckpoints;

    public PipelineCheckpoint(String instanceId,
                              String tenantId,
                              String pipelineId,
                              String idempotencyKey,
                              PipelineCheckpointStatus status,
                              Map<String, Object> state,
                              Map<String, Object> result,
                              Instant createdAt,
                              Instant updatedAt,
                              String currentStepId,
                              String currentStepName,
                              int completedSteps,
                              int totalSteps) {
        this.instanceId = instanceId;
        this.tenantId = tenantId;
        this.pipelineId = pipelineId;
        this.idempotencyKey = idempotencyKey;
        this.status = status;
        this.state = state;
        this.result = result;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.currentStepId = currentStepId;
        this.currentStepName = currentStepName;
        this.completedSteps = completedSteps;
        this.totalSteps = totalSteps;
    }

    // Getters
    public String getInstanceId() {
        return instanceId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getPipelineId() {
        return pipelineId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public PipelineCheckpointStatus getStatus() {
        return status;
    }

    public Map<String, Object> getState() {
        return state;
    }

    public Map<String, Object> getResult() {
        return result;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getCurrentStepId() {
        return currentStepId;
    }

    public String getCurrentStepName() {
        return currentStepName;
    }

    public int getCompletedSteps() {
        return completedSteps;
    }

    public int getTotalSteps() {
        return totalSteps;
    }

    public void setStepCheckpoints(java.util.List<StepCheckpoint> stepCheckpoints) {
        this.stepCheckpoints = stepCheckpoints;
    }

    public java.util.List<StepCheckpoint> getStepCheckpoints() {
        return stepCheckpoints != null ? stepCheckpoints : java.util.Collections.emptyList();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (!(o instanceof PipelineCheckpoint)) { return false; }
        PipelineCheckpoint that = (PipelineCheckpoint) o;
        return completedSteps == that.completedSteps &&
                totalSteps == that.totalSteps &&
                Objects.equals(instanceId, that.instanceId) &&
                Objects.equals(tenantId, that.tenantId) &&
                Objects.equals(pipelineId, that.pipelineId) &&
                Objects.equals(idempotencyKey, that.idempotencyKey) &&
                status == that.status &&
                Objects.equals(state, that.state) &&
                Objects.equals(result, that.result) &&
                Objects.equals(createdAt, that.createdAt) &&
                Objects.equals(updatedAt, that.updatedAt) &&
                Objects.equals(currentStepId, that.currentStepId) &&
                Objects.equals(currentStepName, that.currentStepName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(instanceId, tenantId, pipelineId, idempotencyKey, status, state, result, createdAt, updatedAt, currentStepId, currentStepName, completedSteps, totalSteps);
    }

    @Override
    public String toString() {
        return "PipelineCheckpoint{" +
                "instanceId='" + instanceId + '\'' +
                ", tenantId='" + tenantId + '\'' +
                ", pipelineId='" + pipelineId + '\'' +
                ", idempotencyKey='" + idempotencyKey + '\'' +
                ", status=" + status +
                ", currentStepId='" + currentStepId + '\'' +
                ", currentStepName='" + currentStepName + '\'' +
                ", completedSteps=" + completedSteps +
                ", totalSteps=" + totalSteps +
                '}';
    }

    // Convenience helper used by stores/adapters to check if execution is still active
    public boolean isActive() {
        if (status == PipelineCheckpointStatus.CREATED || status == PipelineCheckpointStatus.RUNNING) {
            return true;
        } else {
            return false;
        }
    }
}
