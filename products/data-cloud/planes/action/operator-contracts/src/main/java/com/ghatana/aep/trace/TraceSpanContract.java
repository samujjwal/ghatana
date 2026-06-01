/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.trace;

import com.ghatana.aep.pattern.spec.PatternSpec;
import com.ghatana.datacloud.entity.agent.RunTrace;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * WS2: Canonical trace/span/event models for pattern evaluation/agent plan/approval/execution.
 *
 * <p>Defines the contract for distributed tracing across the action plane:
 * <ul>
 *   <li>Pattern evaluation traces - trace pattern spec evaluation and matching</li>
 *   <li>Agent plan traces - trace agent plan generation and execution</li>
 *   <li>Approval traces - trace human approval workflows</li>
 *   <li>Execution traces - trace actual action/agent execution</li>
 * </ul>
 *
 * <p>This contract ensures consistent trace/span/event modeling across all action plane
 * components while leveraging the existing RunTrace entity for persistence.
 *
 * @doc.type interface
 * @doc.purpose Canonical trace/span/event models for action plane observability
 * @doc.layer product
 * @doc.pattern Contract
 */
public interface TraceSpanContract {

    /**
     * Pattern evaluation trace contract.
     */
    interface PatternEvaluationTrace {
        /**
         * Start a pattern evaluation trace.
         *
         * @param tenantId tenant identifier
         * @param patternId pattern identifier
         * @param patternSpec pattern specification
         * @param context evaluation context
         * @return trace span ID
         */
        String startEvaluation(String tenantId, String patternId, PatternSpec patternSpec, EvaluationContext context);

        /**
         * Record a pattern match result.
         *
         * @param spanId trace span ID
         * @param matched whether pattern matched
         * @param matchScore match confidence score
         * @param matchDetails match details
         */
        void recordMatch(String spanId, boolean matched, double matchScore, Map<String, Object> matchDetails);

        /**
         * Record a pattern evaluation step.
         *
         * @param spanId trace span ID
         * @param stepType step type (e.g., "CONDITION_CHECK", "EVENT_FILTER")
         * @param stepData step data
         */
        void recordStep(String spanId, String stepType, Map<String, Object> stepData);

        /**
         * Complete the pattern evaluation trace.
         *
         * @param spanId trace span ID
         * @param outcome evaluation outcome
         */
        void completeEvaluation(String spanId, EvaluationOutcome outcome);

        /**
         * Get the pattern evaluation trace.
         *
         * @param spanId trace span ID
         * @return trace data
         */
        Optional<PatternEvaluationTraceData> getTrace(String spanId);
    }

    /**
     * Agent plan trace contract.
     */
    interface AgentPlanTrace {
        /**
         * Start an agent plan trace.
         *
         * @param tenantId tenant identifier
         * @param agentId agent identifier
         * @param planType plan type (e.g., "GENERATION", "EXECUTION")
         * @param context planning context
         * @return trace span ID
         */
        String startPlan(String tenantId, String agentId, String planType, PlanningContext context);

        /**
         * Record a plan generation step.
         *
         * @param spanId trace span ID
         * @param stepType step type (e.g., "TOOL_SELECTION", "PARAMETER_BINDING")
         * @param stepData step data
         */
        void recordPlanStep(String spanId, String stepType, Map<String, Object> stepData);

        /**
         * Record a plan decision.
         *
         * @param spanId trace span ID
         * @param decisionType decision type
         * @param options available options
         * @param selectedOption selected option
         * @param reasoning decision reasoning
         */
        void recordDecision(String spanId, String decisionType, java.util.List<String> options,
                          String selectedOption, Map<String, Object> reasoning);

        /**
         * Complete the agent plan trace.
         *
         * @param spanId trace span ID
         * @param plan generated plan
         * @param outcome planning outcome
         */
        void completePlan(String spanId, Map<String, Object> plan, PlanningOutcome outcome);

        /**
         * Get the agent plan trace.
         *
         * @param spanId trace span ID
         * @return trace data
         */
        Optional<AgentPlanTraceData> getTrace(String spanId);
    }

    /**
     * Approval trace contract.
     */
    interface ApprovalTrace {
        /**
         * Start an approval trace.
         *
         * @param tenantId tenant identifier
         * @param executionId execution identifier
         * @param approvalType approval type
         * @param approverId approver identifier
         * @param context approval context
         * @return trace span ID
         */
        String startApproval(String tenantId, String executionId, String approvalType,
                          String approverId, ApprovalContext context);

        /**
         * Record an approval decision.
         *
         * @param spanId trace span ID
         * @param approved whether approved
         * @param decisionReason decision reason
         * @param conditions any conditions attached to approval
         */
        void recordDecision(String spanId, boolean approved, String decisionReason,
                          Map<String, Object> conditions);

        /**
         * Record an approval escalation.
         *
         * @param spanId trace span ID
         * @param escalationTarget escalation target
         * @param escalationReason escalation reason
         */
        void recordEscalation(String spanId, String escalationTarget, String escalationReason);

        /**
         * Complete the approval trace.
         *
         * @param spanId trace span ID
         * @param finalOutcome final approval outcome
         */
        void completeApproval(String spanId, ApprovalOutcome finalOutcome);

        /**
         * Get the approval trace.
         *
         * @param spanId trace span ID
         * @return trace data
         */
        Optional<ApprovalTraceData> getTrace(String spanId);
    }

    /**
     * Execution trace contract.
     *
     * <p>Leverages the existing RunTrace entity for persistence.
     */
    interface ExecutionTrace {
        /**
         * Start an execution trace.
         *
         * @param tenantId tenant identifier
         * @param executionId execution identifier
         * @param executionType execution type
         * @param context execution context
         * @return trace span ID
         */
        String startExecution(String tenantId, String executionId, String executionType,
                            ExecutionContext context);

        /**
         * Record an execution step.
         *
         * @param spanId trace span ID
         * @param stepType step type
         * @param stepData step data
         */
        void recordStep(String spanId, String stepType, Map<String, Object> stepData);

        /**
         * Record an execution decision.
         *
         * @param spanId trace span ID
         * @param decisionType decision type
         * @param options available options
         * @param selectedOption selected option
         * @param reasoning decision reasoning
         */
        void recordDecision(String spanId, String decisionType, java.util.List<String> options,
                          String selectedOption, Map<String, Object> reasoning);

        /**
         * Record an execution error.
         *
         * @param spanId trace span ID
         * @param errorType error type
         * @param errorMessage error message
         * @param errorContext error context
         */
        void recordError(String spanId, String errorType, String errorMessage, Map<String, Object> errorContext);

        /**
         * Complete the execution trace.
         *
         * @param spanId trace span ID
         * @param finalOutcome final execution outcome
         */
        void completeExecution(String spanId, ExecutionOutcome finalOutcome);

        /**
         * Get the execution trace as RunTrace.
         *
         * @param spanId trace span ID
         * @return RunTrace entity
         */
        Optional<RunTrace> getTrace(String spanId);
    }

    // ==================== Supporting Types ====================

    /**
     * Evaluation context.
     */
    record EvaluationContext(
        String userId,
        String environment,
        Map<String, Object> metadata
    ) {
        public EvaluationContext {
            metadata = Map.copyOf(metadata != null ? metadata : Map.of());
        }
    }

    /**
     * Evaluation outcome.
     */
    enum EvaluationOutcome {
        MATCHED,
        NOT_MATCHED,
        ERROR,
        TIMEOUT
    }

    /**
     * Planning context.
     */
    record PlanningContext(
        String userId,
        String sessionId,
        Map<String, Object> input,
        Map<String, Object> constraints
    ) {
        public PlanningContext {
            input = Map.copyOf(input != null ? input : Map.of());
            constraints = Map.copyOf(constraints != null ? constraints : Map.of());
        }
    }

    /**
     * Planning outcome.
     */
    enum PlanningOutcome {
        SUCCESS,
        FAILED,
        TIMEOUT,
        CANCELLED
    }

    /**
     * Approval context.
     */
    record ApprovalContext(
        String requesterId,
        String requestId,
        Map<String, Object> requestData,
        Map<String, Object> policies
    ) {
        public ApprovalContext {
            requestData = Map.copyOf(requestData != null ? requestData : Map.of());
            policies = Map.copyOf(policies != null ? policies : Map.of());
        }
    }

    /**
     * Approval outcome.
     */
    enum ApprovalOutcome {
        APPROVED,
        DENIED,
        ESCALATED,
        EXPIRED,
        CANCELLED
    }

    /**
     * Execution context.
     */
    record ExecutionContext(
        String userId,
        String sessionId,
        Map<String, Object> input,
        Map<String, Object> config,
        boolean isReplay,
        String idempotencyKey
    ) {
        public ExecutionContext {
            input = Map.copyOf(input != null ? input : Map.of());
            config = Map.copyOf(config != null ? config : Map.of());
        }
    }

    /**
     * Execution outcome.
     */
    enum ExecutionOutcome {
        SUCCESS,
        FAILED,
        CANCELLED,
        TIMEOUT,
        ROLLED_BACK
    }

    /**
     * Pattern evaluation trace data.
     */
    record PatternEvaluationTraceData(
        String spanId,
        String tenantId,
        String patternId,
        Instant startedAt,
        Instant completedAt,
        EvaluationOutcome outcome,
        java.util.List<EvaluationStep> steps,
        Map<String, Object> metadata
    ) {
        public PatternEvaluationTraceData {
            steps = java.util.List.copyOf(steps != null ? steps : java.util.List.of());
            metadata = Map.copyOf(metadata != null ? metadata : Map.of());
        }
    }

    /**
     * Evaluation step.
     */
    record EvaluationStep(
        String stepType,
        Instant timestamp,
        Map<String, Object> stepData
    ) {
        public EvaluationStep {
            stepData = Map.copyOf(stepData != null ? stepData : Map.of());
        }
    }

    /**
     * Agent plan trace data.
     */
    record AgentPlanTraceData(
        String spanId,
        String tenantId,
        String agentId,
        String planType,
        Instant startedAt,
        Instant completedAt,
        PlanningOutcome outcome,
        java.util.List<PlanStep> steps,
        Map<String, Object> plan,
        Map<String, Object> metadata
    ) {
        public AgentPlanTraceData {
            steps = java.util.List.copyOf(steps != null ? steps : java.util.List.of());
            plan = Map.copyOf(plan != null ? plan : Map.of());
            metadata = Map.copyOf(metadata != null ? metadata : Map.of());
        }
    }

    /**
     * Plan step.
     */
    record PlanStep(
        String stepType,
        Instant timestamp,
        Map<String, Object> stepData
    ) {
        public PlanStep {
            stepData = Map.copyOf(stepData != null ? stepData : Map.of());
        }
    }

    /**
     * Approval trace data.
     */
    record ApprovalTraceData(
        String spanId,
        String tenantId,
        String executionId,
        String approvalType,
        String approverId,
        Instant startedAt,
        Instant completedAt,
        ApprovalOutcome outcome,
        boolean approved,
        String decisionReason,
        Map<String, Object> conditions,
        Map<String, Object> metadata
    ) {
        public ApprovalTraceData {
            conditions = Map.copyOf(conditions != null ? conditions : Map.of());
            metadata = Map.copyOf(metadata != null ? metadata : Map.of());
        }
    }
}
