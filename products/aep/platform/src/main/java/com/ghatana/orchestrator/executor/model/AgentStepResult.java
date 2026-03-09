/*
 * Copyright (c) 2024 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.orchestrator.executor.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Represents the result of agent step execution.
 * 
 * <p>Contains execution outcome, timing information, retry state, and metrics
 * for observability. Used to track and report agent step execution status.</p>
 * 
 * @doc.type class
 * @doc.purpose Immutable result object capturing agent step execution outcome and metrics
 * @doc.layer product
 * @doc.pattern ValueObject
 * @since 2.0.0
 */
public class AgentStepResult {

    /**
     * Execution status enum.
     */
    public enum ExecutionStatus {
        SUCCESS,
        TIMEOUT,
        RETRY,
        FAILED,
        CANCELLED
    }

    private final String stepId;
    private final String agentId;
    private final ExecutionStatus status;
    private final Object result;
    private final Throwable error;
    private final Instant startTime;
    private final Instant endTime;
    private final int attemptNumber;
    private final int totalAttempts;
    private final Map<String, Object> metrics;
    private final Map<String, String> context;

    private AgentStepResult(Builder builder) {
        this.stepId = Objects.requireNonNull(builder.stepId, "stepId cannot be null");
        this.agentId = Objects.requireNonNull(builder.agentId, "agentId cannot be null");
        this.status = Objects.requireNonNull(builder.status, "status cannot be null");
        this.result = builder.result;
        this.error = builder.error;
        this.startTime = Objects.requireNonNull(builder.startTime, "startTime cannot be null");
        this.endTime = builder.endTime;
        this.attemptNumber = builder.attemptNumber;
        this.totalAttempts = builder.totalAttempts;
        this.metrics = Map.copyOf(builder.metrics);
        this.context = Map.copyOf(builder.context);
    }

    // Getters
    public String getStepId() {
        return stepId;
    }

    public String getAgentId() {
        return agentId;
    }

    /**
     * Compatibility alias: canonical "id" accessor for the agent associated with this result.
     * Preferred new code can call {@code getId()} while existing callers may continue to use {@code getAgentId()}.
     */
    public String getId() {
        return getAgentId();
    }

    public ExecutionStatus getStatus() {
        return status;
    }

    public Object getResult() {
        return result;
    }

    public Throwable getError() {
        return error;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public int getTotalAttempts() {
        return totalAttempts;
    }

    public Map<String, Object> getMetrics() {
        return metrics;
    }

    public Map<String, String> getContext() {
        return context;
    }

    /**
     * Calculate execution duration in milliseconds.
     */
    public long getDurationMs() {
        if (endTime == null) {
            return -1;
        }
        return endTime.toEpochMilli() - startTime.toEpochMilli();
    }

    /**
     * Check if execution was successful.
     */
    public boolean isSuccess() {
        return status == ExecutionStatus.SUCCESS;
    }

    /**
     * Check if execution should be retried.
     */
    public boolean shouldRetry() {
        return status == ExecutionStatus.RETRY;
    }

    /**
     * Check if execution failed permanently.
     */
    public boolean isFailed() {
        return status == ExecutionStatus.FAILED;
    }

    /**
     * Get metric value by key.
     */
    public Object getMetric(String key) {
        return metrics.get(key);
    }

    /**
     * Get context value by key.
     */
    public String getContextValue(String key) {
        return context.get(key);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String stepId;
        private String agentId;
        private ExecutionStatus status;
        private Object result;
        private Throwable error;
        private Instant startTime;
        private Instant endTime;
        private int attemptNumber = 1;
        private int totalAttempts = 1;
        private Map<String, Object> metrics = Map.of();
        private Map<String, String> context = Map.of();

        public Builder stepId(String stepId) {
            this.stepId = stepId;
            return this;
        }

        public Builder agentId(String agentId) {
            this.agentId = agentId;
            return this;
        }

        public Builder status(ExecutionStatus status) {
            this.status = status;
            return this;
        }

        public Builder result(Object result) {
            this.result = result;
            return this;
        }

        public Builder error(Throwable error) {
            this.error = error;
            return this;
        }

        public Builder startTime(Instant startTime) {
            this.startTime = startTime;
            return this;
        }

        public Builder endTime(Instant endTime) {
            this.endTime = endTime;
            return this;
        }

        public Builder attemptNumber(int attemptNumber) {
            this.attemptNumber = attemptNumber;
            return this;
        }

        public Builder totalAttempts(int totalAttempts) {
            this.totalAttempts = totalAttempts;
            return this;
        }

        public Builder metrics(Map<String, Object> metrics) {
            this.metrics = metrics;
            return this;
        }

        public Builder context(Map<String, String> context) {
            this.context = context;
            return this;
        }

        public AgentStepResult build() {
            return new AgentStepResult(this);
        }
    }

    @Override
    public String toString() {
        return String.format(
            "AgentStepResult{stepId='%s', agentId='%s', status=%s, duration=%dms, attempt=%d/%d}",
            stepId, agentId, status, getDurationMs(), attemptNumber, totalAttempts
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AgentStepResult that = (AgentStepResult) o;
        return attemptNumber == that.attemptNumber &&
               totalAttempts == that.totalAttempts &&
               Objects.equals(stepId, that.stepId) &&
               Objects.equals(agentId, that.agentId) &&
               status == that.status &&
               Objects.equals(startTime, that.startTime) &&
               Objects.equals(endTime, that.endTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stepId, agentId, status, startTime, endTime, attemptNumber, totalAttempts);
    }
}
