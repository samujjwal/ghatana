package com.ghatana.orchestrator.store;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Domain representation of an individual step checkpoint within a pipeline execution.
 */
public class StepCheckpoint {
    private final String stepId;
    private final String stepName;
    private final PipelineCheckpointStatus status;
    private final Map<String, Object> input;
    private final Map<String, Object> output;
    private final Instant startedAt;
    private final Instant completedAt;
    private final String errorMessage;
    private final int retryCount;

    public StepCheckpoint(String stepId,
                          String stepName,
                          PipelineCheckpointStatus status,
                          Map<String, Object> input,
                          Map<String, Object> output,
                          Instant startedAt,
                          Instant completedAt,
                          String errorMessage,
                          int retryCount) {
        this.stepId = stepId;
        this.stepName = stepName;
        this.status = status;
        this.input = input;
        this.output = output;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.errorMessage = errorMessage;
        this.retryCount = retryCount;
    }

    // Getters
    public String getStepId() {
        return stepId;
    }

    public String getStepName() {
        return stepName;
    }

    public PipelineCheckpointStatus getStatus() {
        return status;
    }

    public Map<String, Object> getInput() {
        return input;
    }

    public Map<String, Object> getOutput() {
        return output;
    }

    public Map<String, Object> getOutputData() {
        return output;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public int getRetryCount() {
        return retryCount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof StepCheckpoint)) {
            return false;
        }
        StepCheckpoint that = (StepCheckpoint) o;
        return retryCount == that.retryCount &&
                Objects.equals(stepId, that.stepId) &&
                Objects.equals(stepName, that.stepName) &&
                status == that.status &&
                Objects.equals(input, that.input) &&
                Objects.equals(output, that.output) &&
                Objects.equals(startedAt, that.startedAt) &&
                Objects.equals(completedAt, that.completedAt) &&
                Objects.equals(errorMessage, that.errorMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stepId, stepName, status, input, output, startedAt, completedAt, errorMessage, retryCount);
    }

    @Override
    public String toString() {
        return "StepCheckpoint{" +
                "stepId='" + stepId + '\'' +
                ", stepName='" + stepName + '\'' +
                ", status=" + status +
                ", retryCount=" + retryCount +
                '}';
    }
}
