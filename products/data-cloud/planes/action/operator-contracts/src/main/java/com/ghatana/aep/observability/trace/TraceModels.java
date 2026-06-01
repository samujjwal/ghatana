package com.ghatana.aep.observability.trace;

import com.ghatana.aep.model.EventContext;
import com.ghatana.aep.pattern.spec.PatternSpec;
import com.ghatana.aep.operator.contract.OperatorSpec;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Canonical trace/span/event models for pattern evaluation, agent planning, and execution.
 *
 * <p>WS2: Defines the structured trace models for observability across:
 * <ul>
 *   <li>Pattern evaluation: traces for pattern compilation, validation, and matching</li>
 *   <li>Agent planning: traces for agent plan generation and optimization</li>
 *   <li>Execution: traces for action execution with replay safety</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Canonical trace/span/event models for pattern eval/agent plan/execution
 * @doc.layer product
 * @doc.pattern Model
 */
public final class TraceModels {

    private TraceModels() {}

    // ==================== Pattern Evaluation Traces ====================

    /**
     * Trace for pattern evaluation lifecycle.
     */
    public record PatternEvaluationTrace(
            String traceId,
            String patternId,
            String tenantId,
            PatternEvaluationPhase phase,
            Instant startTime,
            Instant endTime,
            EvaluationStatus status,
            String errorMessage,
            Map<String, Object> metadata,
            Set<String> spanIds
    ) {
        public PatternEvaluationTrace {
            metadata = Map.copyOf(metadata != null ? metadata : Map.of());
            spanIds = Set.copyOf(spanIds != null ? spanIds : Set.of());
        }

        public long durationMs() {
            return java.time.Duration.between(startTime, endTime).toMillis();
        }

        public boolean isSuccessful() {
            return status == EvaluationStatus.SUCCESS;
        }
    }

    /**
     * Span for pattern compilation.
     */
    public record PatternCompilationSpan(
            String spanId,
            String traceId,
            String patternId,
            String tenantId,
            Instant startTime,
            Instant endTime,
            CompilationStatus status,
            String compiledPlanId,
            int nodeCount,
            Map<String, Object> compilationMetrics
    ) {
        public PatternCompilationSpan {
            compilationMetrics = Map.copyOf(compilationMetrics != null ? compilationMetrics : Map.of());
        }

        public long durationMs() {
            return java.time.Duration.between(startTime, endTime).toMillis();
        }
    }

    /**
     * Span for pattern matching.
     */
    public record PatternMatchSpan(
            String spanId,
            String traceId,
            String patternId,
            String eventId,
            String tenantId,
            Instant startTime,
            Instant endTime,
            boolean matched,
            double confidence,
            Map<String, Object> matchAttributes
    ) {
        public PatternMatchSpan {
            matchAttributes = Map.copyOf(matchAttributes != null ? matchAttributes : Map.of());
        }

        public long durationMs() {
            return java.time.Duration.between(startTime, endTime).toMillis();
        }
    }

    // ==================== Agent Planning Traces ====================

    /**
     * Trace for agent planning lifecycle.
     */
    public record AgentPlanningTrace(
            String traceId,
            String agentId,
            String tenantId,
            String operationId,
            AgentPlanningPhase phase,
            Instant startTime,
            Instant endTime,
            PlanningStatus status,
            String errorMessage,
            Map<String, Object> metadata,
            Set<String> spanIds
    ) {
        public AgentPlanningTrace {
            metadata = Map.copyOf(metadata != null ? metadata : Map.of());
            spanIds = Set.copyOf(spanIds != null ? spanIds : Set.of());
        }

        public long durationMs() {
            return java.time.Duration.between(startTime, endTime).toMillis();
        }

        public boolean isSuccessful() {
            return status == PlanningStatus.SUCCESS;
        }
    }

    /**
     * Span for agent plan generation.
     */
    public record AgentPlanGenerationSpan(
            String spanId,
            String traceId,
            String agentId,
            String operationId,
            String tenantId,
            Instant startTime,
            Instant endTime,
            PlanGenerationStatus status,
            String planId,
            int stepCount,
            Map<String, Object> planMetrics
    ) {
        public AgentPlanGenerationSpan {
            planMetrics = Map.copyOf(planMetrics != null ? planMetrics : Map.of());
        }

        public long durationMs() {
            return java.time.Duration.between(startTime, endTime).toMillis();
        }
    }

    /**
     * Span for agent plan optimization.
     */
    public record AgentPlanOptimizationSpan(
            String spanId,
            String traceId,
            String agentId,
            String planId,
            String tenantId,
            Instant startTime,
            Instant endTime,
            OptimizationStatus status,
            String optimizationStrategy,
            double costReduction,
            double latencyImprovement,
            Map<String, Object> optimizationMetrics
    ) {
        public AgentPlanOptimizationSpan {
            optimizationMetrics = Map.copyOf(optimizationMetrics != null ? optimizationMetrics : Map.of());
        }

        public long durationMs() {
            return java.time.Duration.between(startTime, endTime).toMillis();
        }
    }

    // ==================== Execution Traces ====================

    /**
     * Trace for execution lifecycle.
     */
    public record ExecutionTrace(
            String traceId,
            String operationId,
            String tenantId,
            ExecutionPhase phase,
            Instant startTime,
            Instant endTime,
            ExecutionStatus status,
            String errorMessage,
            boolean isReplay,
            String replayMode,
            Map<String, Object> metadata,
            Set<String> spanIds
    ) {
        public ExecutionTrace {
            metadata = Map.copyOf(metadata != null ? metadata : Map.of());
            spanIds = Set.copyOf(spanIds != null ? spanIds : Set.of());
        }

        public long durationMs() {
            return java.time.Duration.between(startTime, endTime).toMillis();
        }

        public boolean isSuccessful() {
            return status == ExecutionStatus.SUCCESS;
        }
    }

    /**
     * Span for action execution.
     */
    public record ActionExecutionSpan(
            String spanId,
            String traceId,
            String operationId,
            String actionId,
            String tenantId,
            Instant startTime,
            Instant endTime,
            ActionExecutionStatus status,
            boolean hasSideEffects,
            boolean sideEffectsExecuted,
            String idempotencyKey,
            Map<String, Object> executionMetrics
    ) {
        public ActionExecutionSpan {
            executionMetrics = Map.copyOf(executionMetrics != null ? executionMetrics : Map.of());
        }

        public long durationMs() {
            return java.time.Duration.between(startTime, endTime).toMillis();
        }
    }

    /**
     * Span for replay operation.
     */
    public record ReplaySpan(
            String spanId,
            String traceId,
            String originalOperationId,
            String replayOperationId,
            String tenantId,
            Instant startTime,
            Instant endTime,
            ReplayStatus status,
            String replayMode,
            boolean compensationExecuted,
            String compensationStrategy,
            Map<String, Object> replayMetrics
    ) {
        public ReplaySpan {
            replayMetrics = Map.copyOf(replayMetrics != null ? replayMetrics : Map.of());
        }

        public long durationMs() {
            return java.time.Duration.between(startTime, endTime).toMillis();
        }
    }

    // ==================== Event Models ====================

    /**
     * Event for pattern lifecycle state change.
     */
    public record PatternLifecycleEvent(
            String eventId,
            String patternId,
            String tenantId,
            PatternLifecycleState fromState,
            PatternLifecycleState toState,
            String actor,
            String reason,
            Instant timestamp,
            Map<String, Object> metadata
    ) {
        public PatternLifecycleEvent {
            metadata = Map.copyOf(metadata != null ? metadata : Map.of());
        }
    }

    /**
     * Event for agent execution start.
     */
    public record AgentExecutionStartEvent(
            String eventId,
            String agentId,
            String operationId,
            String tenantId,
            String executionTier,
            Instant timestamp,
            Map<String, Object> context
    ) {
        public AgentExecutionStartEvent {
            context = Map.copyOf(context != null ? context : Map.of());
        }
    }

    /**
     * Event for agent execution completion.
     */
    public record AgentExecutionCompleteEvent(
            String eventId,
            String agentId,
            String operationId,
            String tenantId,
            ExecutionStatus status,
            String result,
            Instant startTime,
            Instant endTime,
            Map<String, Object> metrics
    ) {
        public AgentExecutionCompleteEvent {
            metrics = Map.copyOf(metrics != null ? metrics : Map.of());
        }

        public long durationMs() {
            return java.time.Duration.between(startTime, endTime).toMillis();
        }
    }

    // ==================== Enums ====================

    /**
     * Pattern evaluation phases.
     */
    public enum PatternEvaluationPhase {
        VALIDATION,
        COMPILATION,
        MATCHING,
        EMITTING
    }

    /**
     * Evaluation status.
     */
    public enum EvaluationStatus {
        SUCCESS,
        FAILED,
        TIMEOUT,
        CANCELLED
    }

    /**
     * Compilation status.
     */
    public enum CompilationStatus {
        SUCCESS,
        FAILED_VALIDATION,
        FAILED_OPTIMIZATION,
        TIMEOUT
    }

    /**
     * Agent planning phases.
     */
    public enum AgentPlanningPhase {
        PLAN_GENERATION,
        PLAN_OPTIMIZATION,
        PLAN_VALIDATION
    }

    /**
     * Planning status.
     */
    public enum PlanningStatus {
        SUCCESS,
        FAILED,
        TIMEOUT,
        CANCELLED
    }

    /**
     * Plan generation status.
     */
    public enum PlanGenerationStatus {
        SUCCESS,
        FAILED,
        NO_STEPS_GENERATED
    }

    /**
     * Optimization status.
     */
    public enum OptimizationStatus {
        SUCCESS,
        FAILED,
        NO_IMPROVEMENT
    }

    /**
     * Execution phases.
     */
    public enum ExecutionPhase {
        POLICY_CHECK,
        REVIEW,
        EXECUTION,
        COMPENSATION
    }

    /**
     * Execution status.
     */
    public enum ExecutionStatus {
        SUCCESS,
        FAILED,
        SKIPPED,
        TIMEOUT,
        CANCELLED
    }

    /**
     * Action execution status.
     */
    public enum ActionExecutionStatus {
        SUCCESS,
        FAILED,
        SKIPPED,
        PARTIAL
    }

    /**
     * Replay status.
     */
    public enum ReplayStatus {
        SUCCESS,
        FAILED,
        SKIPPED,
        COMPENSATION_FAILED
    }

    /**
     * Pattern lifecycle state.
     */
    public enum PatternLifecycleState {
        DRAFT,
        VALIDATING,
        CANDIDATE,
        VALIDATED,
        SHADOW,
        RECOMMENDED,
        APPROVED,
        ACTIVE,
        PAUSED,
        DEGRADED,
        SUPERSEDED,
        FAILED,
        ARCHIVED,
        RETIRED
    }

    // ==================== Builders ====================

    /**
     * Builder for PatternEvaluationTrace.
     */
    public static class PatternEvaluationTraceBuilder {
        private String traceId;
        private String patternId;
        private String tenantId;
        private PatternEvaluationPhase phase;
        private Instant startTime = Instant.now();
        private Instant endTime;
        private EvaluationStatus status;
        private String errorMessage;
        private final Map<String, Object> metadata = new java.util.LinkedHashMap<>();
        private final Set<String> spanIds = new java.util.LinkedHashSet<>();

        public PatternEvaluationTraceBuilder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public PatternEvaluationTraceBuilder patternId(String patternId) {
            this.patternId = patternId;
            return this;
        }

        public PatternEvaluationTraceBuilder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public PatternEvaluationTraceBuilder phase(PatternEvaluationPhase phase) {
            this.phase = phase;
            return this;
        }

        public PatternEvaluationTraceBuilder status(EvaluationStatus status) {
            this.status = status;
            this.endTime = Instant.now();
            return this;
        }

        public PatternEvaluationTraceBuilder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public PatternEvaluationTraceBuilder addMetadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        public PatternEvaluationTraceBuilder addSpanId(String spanId) {
            this.spanIds.add(spanId);
            return this;
        }

        public PatternEvaluationTrace build() {
            return new PatternEvaluationTrace(
                traceId != null ? traceId : java.util.UUID.randomUUID().toString(),
                patternId,
                tenantId,
                phase,
                startTime,
                endTime != null ? endTime : Instant.now(),
                status,
                errorMessage,
                metadata,
                spanIds
            );
        }
    }

    /**
     * Builder for ExecutionTrace.
     */
    public static class ExecutionTraceBuilder {
        private String traceId;
        private String operationId;
        private String tenantId;
        private ExecutionPhase phase;
        private Instant startTime = Instant.now();
        private Instant endTime;
        private ExecutionStatus status;
        private String errorMessage;
        private boolean isReplay;
        private String replayMode;
        private final Map<String, Object> metadata = new java.util.LinkedHashMap<>();
        private final Set<String> spanIds = new java.util.LinkedHashSet<>();

        public ExecutionTraceBuilder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public ExecutionTraceBuilder operationId(String operationId) {
            this.operationId = operationId;
            return this;
        }

        public ExecutionTraceBuilder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public ExecutionTraceBuilder phase(ExecutionPhase phase) {
            this.phase = phase;
            return this;
        }

        public ExecutionTraceBuilder status(ExecutionStatus status) {
            this.status = status;
            this.endTime = Instant.now();
            return this;
        }

        public ExecutionTraceBuilder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public ExecutionTraceBuilder isReplay(boolean isReplay) {
            this.isReplay = isReplay;
            return this;
        }

        public ExecutionTraceBuilder replayMode(String replayMode) {
            this.replayMode = replayMode;
            return this;
        }

        public ExecutionTraceBuilder addMetadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        public ExecutionTraceBuilder addSpanId(String spanId) {
            this.spanIds.add(spanId);
            return this;
        }

        public ExecutionTrace build() {
            return new ExecutionTrace(
                traceId != null ? traceId : java.util.UUID.randomUUID().toString(),
                operationId,
                tenantId,
                phase,
                startTime,
                endTime != null ? endTime : Instant.now(),
                status,
                errorMessage,
                isReplay,
                replayMode,
                metadata,
                spanIds
            );
        }
    }
}
