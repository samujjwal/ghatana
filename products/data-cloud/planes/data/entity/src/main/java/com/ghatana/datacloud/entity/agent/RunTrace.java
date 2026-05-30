package com.ghatana.datacloud.entity.agent;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.*;

/**
 * Represents a detailed execution trace for an agent run.
 *
 * <p><b>Purpose</b><br>
 * Captures comprehensive execution trace information including decision points,
 * state transitions, performance metrics, and debugging information. Provides
 * detailed observability and audit trail for agent execution analysis and optimization.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * RunTrace trace = RunTrace.builder()
 *     .agentRunId("run-456")
 *     .traceLevel(RunTrace.TraceLevel.DETAILED)
 *     .build();
 * 
 * // Add execution steps
 * trace.addStep("INITIALIZATION", "Agent initialized", Map.of("config", configData));
 * trace.addStep("TOOL_CALL", "Called customer_lookup tool", Map.of("tool", "customer_lookup"));
 * 
 * // Record decision point
 * trace.addDecisionPoint("APPROVAL_NEEDED", "Human approval required", 
 *                       List.of("proceed", "escalate", "deny"));
 * 
 * // Complete trace
 * trace.complete(RunTrace.TraceStatus.SUCCESS);
 * }</pre>
 *
 * @see AgentRun
 * @doc.type class
 * @doc.purpose Detailed execution trace with observability
 * @doc.layer product
 * @doc.pattern Domain Model (JPA Embeddable)
 */
@jakarta.persistence.Embeddable
public class RunTrace {

    @Column(name = "agent_run_id", length = 255)
    private String agentRunId;

    @Enumerated(EnumType.STRING)
    @Column(name = "trace_level", nullable = false, length = 50)
    private TraceLevel traceLevel = TraceLevel.STANDARD;

    @Enumerated(EnumType.STRING)
    @Column(name = "trace_status", nullable = false, length = 50)
    private TraceStatus status = TraceStatus.ACTIVE;

    /**
     * Sequential execution steps.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "execution_steps", columnDefinition = "jsonb")
    private List<ExecutionStep> executionSteps = new ArrayList<>();

    /**
     * Decision points during execution.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "decision_points", columnDefinition = "jsonb")
    private List<DecisionPoint> decisionPoints = new ArrayList<>();

    /**
     * State transitions during execution.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "state_transitions", columnDefinition = "jsonb")
    private List<StateTransition> stateTransitions = new ArrayList<>();

    /**
     * Performance metrics throughout execution.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "performance_metrics", columnDefinition = "jsonb")
    private Map<String, Object> performanceMetrics = new HashMap<>();

    /**
     * Error and exception information.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "error_events", columnDefinition = "jsonb")
    private List<ErrorEvent> errorEvents = new ArrayList<>();

    /**
     * Resource utilization tracking.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "resource_utilization", columnDefinition = "jsonb")
    private List<ResourceSnapshot> resourceUtilization = new ArrayList<>();

    /**
     * Interaction events with external systems.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "interaction_events", columnDefinition = "jsonb")
    private List<InteractionEvent> interactionEvents = new ArrayList<>();

    /**
     * Debug and diagnostic information.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "debug_info", columnDefinition = "jsonb")
    private Map<String, Object> debugInfo = new HashMap<>();

    /**
     * Trace metadata and configuration.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "trace_metadata", columnDefinition = "jsonb")
    private Map<String, Object> traceMetadata = new HashMap<>();

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "step_count")
    private Integer stepCount = 0;

    @Column(name = "error_count")
    private Integer errorCount = 0;

    @Column(name = "decision_count")
    private Integer decisionCount = 0;

    /**
     * Trace level enumeration.
     */
    public enum TraceLevel {
        MINIMAL,       // Only critical events
        STANDARD,      // Standard execution flow
        DETAILED,      // Detailed with intermediate steps
        VERBOSE,       // All events including debug
        DEBUG          // Maximum detail for troubleshooting
    }

    /**
     * Trace status enumeration.
     */
    public enum TraceStatus {
        ACTIVE,        // Trace is actively recording
        COMPLETED,     // Trace completed successfully
        FAILED,        // Trace failed
        CANCELLED,     // Trace cancelled
        CORRUPTED      // Trace data corrupted
    }

    /**
     * Individual execution step.
     */
    public record ExecutionStep(
        String stepId,
        String stepType,
        String description,
        Instant timestamp,
        Long durationMs,
        Map<String, Object> stepData,
        Map<String, Object> metadata,
        String parentStepId,
        List<String> childStepIds
    ) {}

    /**
     * Decision point during execution.
     */
    public record DecisionPoint(
        String decisionId,
        String decisionType,
        String description,
        Instant timestamp,
        List<String> availableOptions,
        String selectedOption,
        String decisionMaker,
        Map<String, Object> decisionContext,
        Map<String, Object> reasoning
    ) {}

    /**
     * State transition during execution.
     */
    public record StateTransition(
        String transitionId,
        String fromState,
        String toState,
        String transitionType,
        Instant timestamp,
        String trigger,
        Map<String, Object> context,
        boolean isValid
    ) {}

    /**
     * Error event during execution.
     */
    public record ErrorEvent(
        String errorId,
        String errorType,
        String errorMessage,
        String errorSeverity,
        Instant timestamp,
        String component,
        Map<String, Object> errorContext,
        String stackTrace,
        boolean recovered,
        String recoveryAction
    ) {}

    /**
     * Resource utilization snapshot.
     */
    public record ResourceSnapshot(
        String snapshotId,
        Instant timestamp,
        Map<String, Object> cpuUsage,
        Map<String, Object> memoryUsage,
        Map<String, Object> diskUsage,
        Map<String, Object> networkUsage,
        Map<String, Object> customMetrics
    ) {}

    /**
     * Interaction with external systems.
     */
    public record InteractionEvent(
        String interactionId,
        String interactionType,
        String targetSystem,
        String operation,
        Instant timestamp,
        Long durationMs,
        boolean successful,
        Map<String, Object> request,
        Map<String, Object> response,
        String errorCode
    ) {}

    // ============ Getters & Setters ============

    public String getAgentRunId() {
        return agentRunId;
    }

    public void setAgentRunId(String agentRunId) {
        this.agentRunId = agentRunId;
    }

    public TraceLevel getTraceLevel() {
        return traceLevel;
    }

    public void setTraceLevel(TraceLevel traceLevel) {
        this.traceLevel = traceLevel;
    }

    public TraceStatus getStatus() {
        return status;
    }

    public void setStatus(TraceStatus status) {
        this.status = status;
    }

    public List<ExecutionStep> getExecutionSteps() {
        return executionSteps;
    }

    public void setExecutionSteps(List<ExecutionStep> executionSteps) {
        this.executionSteps = executionSteps;
    }

    public List<DecisionPoint> getDecisionPoints() {
        return decisionPoints;
    }

    public void setDecisionPoints(List<DecisionPoint> decisionPoints) {
        this.decisionPoints = decisionPoints;
    }

    public List<StateTransition> getStateTransitions() {
        return stateTransitions;
    }

    public void setStateTransitions(List<StateTransition> stateTransitions) {
        this.stateTransitions = stateTransitions;
    }

    public Map<String, Object> getPerformanceMetrics() {
        return performanceMetrics;
    }

    public void setPerformanceMetrics(Map<String, Object> performanceMetrics) {
        this.performanceMetrics = performanceMetrics;
    }

    public List<ErrorEvent> getErrorEvents() {
        return errorEvents;
    }

    public void setErrorEvents(List<ErrorEvent> errorEvents) {
        this.errorEvents = errorEvents;
    }

    public List<ResourceSnapshot> getResourceUtilization() {
        return resourceUtilization;
    }

    public void setResourceUtilization(List<ResourceSnapshot> resourceUtilization) {
        this.resourceUtilization = resourceUtilization;
    }

    public List<InteractionEvent> getInteractionEvents() {
        return interactionEvents;
    }

    public void setInteractionEvents(List<InteractionEvent> interactionEvents) {
        this.interactionEvents = interactionEvents;
    }

    public Map<String, Object> getDebugInfo() {
        return debugInfo;
    }

    public void setDebugInfo(Map<String, Object> debugInfo) {
        this.debugInfo = debugInfo;
    }

    public Map<String, Object> getTraceMetadata() {
        return traceMetadata;
    }

    public void setTraceMetadata(Map<String, Object> traceMetadata) {
        this.traceMetadata = traceMetadata;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Integer getStepCount() {
        return stepCount;
    }

    public void setStepCount(Integer stepCount) {
        this.stepCount = stepCount;
    }

    public Integer getErrorCount() {
        return errorCount;
    }

    public void setErrorCount(Integer errorCount) {
        this.errorCount = errorCount;
    }

    public Integer getDecisionCount() {
        return decisionCount;
    }

    public void setDecisionCount(Integer decisionCount) {
        this.decisionCount = decisionCount;
    }

    // ============ Business Methods ============

    /**
     * Adds an execution step to the trace.
     */
    public void addStep(String stepType, String description, Map<String, Object> stepData) {
        ExecutionStep step = new ExecutionStep(
            UUID.randomUUID().toString(),
            stepType,
            description,
            Instant.now(),
            null, // Duration will be calculated on completion
            stepData,
            Map.of(),
            null, // Parent step ID
            new ArrayList<>() // Child step IDs
        );
        
        this.executionSteps.add(step);
        this.stepCount++;
        this.updatedAt = Instant.now();
    }

    /**
     * Adds an execution step with duration.
     */
    public void addStep(String stepType, String description, Map<String, Object> stepData, Long durationMs) {
        ExecutionStep step = new ExecutionStep(
            UUID.randomUUID().toString(),
            stepType,
            description,
            Instant.now(),
            durationMs,
            stepData,
            Map.of(),
            null,
            new ArrayList<>()
        );
        
        this.executionSteps.add(step);
        this.stepCount++;
        this.updatedAt = Instant.now();
    }

    /**
     * Adds a decision point to the trace.
     */
    public void addDecisionPoint(String decisionType, String description, List<String> availableOptions) {
        DecisionPoint decision = new DecisionPoint(
            UUID.randomUUID().toString(),
            decisionType,
            description,
            Instant.now(),
            availableOptions,
            null, // Selected option will be set when decision is made
            null, // Decision maker will be set when decision is made
            Map.of(),
            Map.of()
        );
        
        this.decisionPoints.add(decision);
        this.decisionCount++;
        this.updatedAt = Instant.now();
    }

    /**
     * Records a decision at a decision point.
     */
    public void recordDecision(String decisionId, String selectedOption, String decisionMaker, Map<String, Object> reasoning) {
        Optional<DecisionPoint> decisionOpt = decisionPoints.stream()
            .filter(d -> d.decisionId().equals(decisionId))
            .findFirst();
        
        if (decisionOpt.isPresent()) {
            DecisionPoint decision = decisionOpt.get();
            DecisionPoint updatedDecision = new DecisionPoint(
                decision.decisionId(),
                decision.decisionType(),
                decision.description(),
                decision.timestamp(),
                decision.availableOptions(),
                selectedOption,
                decisionMaker,
                decision.decisionContext(),
                reasoning
            );
            
            int index = decisionPoints.indexOf(decision);
            decisionPoints.set(index, updatedDecision);
            this.updatedAt = Instant.now();
        }
    }

    /**
     * Adds a state transition to the trace.
     */
    public void addStateTransition(String fromState, String toState, String transitionType, String trigger) {
        StateTransition transition = new StateTransition(
            UUID.randomUUID().toString(),
            fromState,
            toState,
            transitionType,
            Instant.now(),
            trigger,
            Map.of(),
            true
        );
        
        this.stateTransitions.add(transition);
        this.updatedAt = Instant.now();
    }

    /**
     * Adds an error event to the trace.
     */
    public void addErrorEvent(String errorType, String errorMessage, String errorSeverity, 
                            String component, Map<String, Object> errorContext, String stackTrace) {
        ErrorEvent error = new ErrorEvent(
            UUID.randomUUID().toString(),
            errorType,
            errorMessage,
            errorSeverity,
            Instant.now(),
            component,
            errorContext,
            stackTrace,
            false,
            null
        );
        
        this.errorEvents.add(error);
        this.errorCount++;
        this.updatedAt = Instant.now();
    }

    /**
     * Records error recovery.
     */
    public void recordErrorRecovery(String errorId, String recoveryAction) {
        Optional<ErrorEvent> errorOpt = errorEvents.stream()
            .filter(e -> e.errorId().equals(errorId))
            .findFirst();
        
        if (errorOpt.isPresent()) {
            ErrorEvent error = errorOpt.get();
            ErrorEvent updatedError = new ErrorEvent(
                error.errorId(),
                error.errorType(),
                error.errorMessage(),
                error.errorSeverity(),
                error.timestamp(),
                error.component(),
                error.errorContext(),
                error.stackTrace(),
                true,
                recoveryAction
            );
            
            int index = errorEvents.indexOf(error);
            errorEvents.set(index, updatedError);
            this.updatedAt = Instant.now();
        }
    }

    /**
     * Adds a resource utilization snapshot.
     */
    public void addResourceSnapshot(Map<String, Object> cpuUsage, Map<String, Object> memoryUsage,
                                  Map<String, Object> diskUsage, Map<String, Object> networkUsage) {
        ResourceSnapshot snapshot = new ResourceSnapshot(
            UUID.randomUUID().toString(),
            Instant.now(),
            cpuUsage,
            memoryUsage,
            diskUsage,
            networkUsage,
            Map.of()
        );
        
        this.resourceUtilization.add(snapshot);
        this.updatedAt = Instant.now();
    }

    /**
     * Adds an interaction event.
     */
    public void addInteractionEvent(String interactionType, String targetSystem, String operation,
                                  Long durationMs, boolean successful, Map<String, Object> request,
                                  Map<String, Object> response, String errorCode) {
        InteractionEvent interaction = new InteractionEvent(
            UUID.randomUUID().toString(),
            interactionType,
            targetSystem,
            operation,
            Instant.now(),
            durationMs,
            successful,
            request,
            response,
            errorCode
        );
        
        this.interactionEvents.add(interaction);
        this.updatedAt = Instant.now();
    }

    /**
     * Updates performance metrics.
     */
    public void updatePerformanceMetrics(Map<String, Object> newMetrics) {
        this.performanceMetrics.putAll(newMetrics);
        this.updatedAt = Instant.now();
    }

    /**
     * Updates debug information.
     */
    public void updateDebugInfo(Map<String, Object> newDebugInfo) {
        this.debugInfo.putAll(newDebugInfo);
        this.updatedAt = Instant.now();
    }

    /**
     * Completes the trace.
     */
    public void complete(TraceStatus finalStatus) {
        this.status = finalStatus;
        this.completedAt = Instant.now();
        
        // Update final metrics
        performanceMetrics.put("totalSteps", stepCount);
        performanceMetrics.put("totalErrors", errorCount);
        performanceMetrics.put("totalDecisions", decisionCount);
        if (startedAt != null && completedAt != null) {
            performanceMetrics.put("totalDurationMs", 
                java.time.Duration.between(startedAt, completedAt).toMillis());
        }
        
        this.updatedAt = Instant.now();
    }

    /**
     * Gets the total duration of the trace in milliseconds.
     */
    public Long getTotalDurationMs() {
        if (startedAt == null) return null;
        Instant end = completedAt != null ? completedAt : Instant.now();
        return java.time.Duration.between(startedAt, end).toMillis();
    }

    /**
     * Gets the average step duration in milliseconds.
     */
    public Double getAverageStepDurationMs() {
        List<Long> durations = executionSteps.stream()
            .map(ExecutionStep::durationMs)
            .filter(Objects::nonNull)
            .toList();
        
        if (durations.isEmpty()) return null;
        return durations.stream().mapToLong(Long::longValue).average().orElse(0.0);
    }

    /**
     * Gets error rate as percentage.
     */
    public Double getErrorRate() {
        if (stepCount == 0) return 0.0;
        return (double) errorCount / stepCount * 100.0;
    }

    /**
     * Gets steps by type.
     */
    public List<ExecutionStep> getStepsByType(String stepType) {
        return executionSteps.stream()
            .filter(step -> step.stepType().equals(stepType))
            .toList();
    }

    /**
     * Gets decision points by type.
     */
    public List<DecisionPoint> getDecisionPointsByType(String decisionType) {
        return decisionPoints.stream()
            .filter(decision -> decision.decisionType().equals(decisionType))
            .toList();
    }

    /**
     * Gets error events by severity.
     */
    public List<ErrorEvent> getErrorEventsBySeverity(String severity) {
        return errorEvents.stream()
            .filter(error -> error.errorSeverity().equals(severity))
            .toList();
    }

    /**
     * Checks if the trace is in a terminal state.
     */
    public boolean isTerminal() {
        return status == TraceStatus.COMPLETED || 
               status == TraceStatus.FAILED || 
               status == TraceStatus.CANCELLED || 
               status == TraceStatus.CORRUPTED;
    }

    /**
     * Checks if the trace is active.
     */
    public boolean isActive() {
        return status == TraceStatus.ACTIVE;
    }

    /**
     * Checks if the trace has errors.
     */
    public boolean hasErrors() {
        return !errorEvents.isEmpty();
    }

    /**
     * Gets the most recent error event.
     */
    public Optional<ErrorEvent> getMostRecentError() {
        return errorEvents.stream()
            .max(Comparator.comparing(ErrorEvent::timestamp));
    }

    /**
     * Gets the most recent execution step.
     */
    public Optional<ExecutionStep> getMostRecentStep() {
        return executionSteps.stream()
            .max(Comparator.comparing(ExecutionStep::timestamp));
    }

    // ============ Lifecycle Callbacks ============

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (startedAt == null) startedAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // ============ Builder Pattern ============

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String agentRunId;
        private TraceLevel traceLevel = TraceLevel.STANDARD;
        private TraceStatus status = TraceStatus.ACTIVE;
        private List<ExecutionStep> executionSteps = new ArrayList<>();
        private List<DecisionPoint> decisionPoints = new ArrayList<>();
        private List<StateTransition> stateTransitions = new ArrayList<>();
        private Map<String, Object> performanceMetrics = new HashMap<>();
        private List<ErrorEvent> errorEvents = new ArrayList<>();
        private List<ResourceSnapshot> resourceUtilization = new ArrayList<>();
        private List<InteractionEvent> interactionEvents = new ArrayList<>();
        private Map<String, Object> debugInfo = new HashMap<>();
        private Map<String, Object> traceMetadata = new HashMap<>();
        private Instant startedAt;
        private Instant updatedAt;
        private Instant completedAt;
        private Integer stepCount = 0;
        private Integer errorCount = 0;
        private Integer decisionCount = 0;

        public Builder agentRunId(String agentRunId) {
            this.agentRunId = agentRunId;
            return this;
        }

        public Builder traceLevel(TraceLevel traceLevel) {
            this.traceLevel = traceLevel;
            return this;
        }

        public Builder status(TraceStatus status) {
            this.status = status;
            return this;
        }

        public Builder executionSteps(List<ExecutionStep> executionSteps) {
            this.executionSteps = executionSteps;
            return this;
        }

        public Builder decisionPoints(List<DecisionPoint> decisionPoints) {
            this.decisionPoints = decisionPoints;
            return this;
        }

        public Builder stateTransitions(List<StateTransition> stateTransitions) {
            this.stateTransitions = stateTransitions;
            return this;
        }

        public Builder performanceMetrics(Map<String, Object> performanceMetrics) {
            this.performanceMetrics = performanceMetrics;
            return this;
        }

        public Builder errorEvents(List<ErrorEvent> errorEvents) {
            this.errorEvents = errorEvents;
            return this;
        }

        public Builder resourceUtilization(List<ResourceSnapshot> resourceUtilization) {
            this.resourceUtilization = resourceUtilization;
            return this;
        }

        public Builder interactionEvents(List<InteractionEvent> interactionEvents) {
            this.interactionEvents = interactionEvents;
            return this;
        }

        public Builder debugInfo(Map<String, Object> debugInfo) {
            this.debugInfo = debugInfo;
            return this;
        }

        public Builder traceMetadata(Map<String, Object> traceMetadata) {
            this.traceMetadata = traceMetadata;
            return this;
        }

        public Builder startedAt(Instant startedAt) {
            this.startedAt = startedAt;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public Builder completedAt(Instant completedAt) {
            this.completedAt = completedAt;
            return this;
        }

        public Builder stepCount(Integer stepCount) {
            this.stepCount = stepCount;
            return this;
        }

        public Builder errorCount(Integer errorCount) {
            this.errorCount = errorCount;
            return this;
        }

        public Builder decisionCount(Integer decisionCount) {
            this.decisionCount = decisionCount;
            return this;
        }

        public RunTrace build() {
            RunTrace trace = new RunTrace();
            trace.agentRunId = this.agentRunId;
            trace.traceLevel = this.traceLevel;
            trace.status = this.status;
            trace.executionSteps = this.executionSteps;
            trace.decisionPoints = this.decisionPoints;
            trace.stateTransitions = this.stateTransitions;
            trace.performanceMetrics = this.performanceMetrics;
            trace.errorEvents = this.errorEvents;
            trace.resourceUtilization = this.resourceUtilization;
            trace.interactionEvents = this.interactionEvents;
            trace.debugInfo = this.debugInfo;
            trace.traceMetadata = this.traceMetadata;
            trace.startedAt = this.startedAt;
            trace.updatedAt = this.updatedAt;
            trace.completedAt = this.completedAt;
            trace.stepCount = this.stepCount;
            trace.errorCount = this.errorCount;
            trace.decisionCount = this.decisionCount;
            return trace;
        }
    }

    @Override
    public String toString() {
        return "RunTrace{" +
                "agentRunId='" + agentRunId + '\'' +
                ", traceLevel=" + traceLevel +
                ", status=" + status +
                ", stepCount=" + stepCount +
                ", errorCount=" + errorCount +
                ", totalDurationMs=" + getTotalDurationMs() +
                '}';
    }
}
