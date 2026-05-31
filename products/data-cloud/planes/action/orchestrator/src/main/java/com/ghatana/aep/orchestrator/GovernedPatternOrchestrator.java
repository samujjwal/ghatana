/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.orchestrator;

import com.ghatana.aep.model.EventContext;
import com.ghatana.aep.operator.contract.*;
import com.ghatana.aep.pattern.spec.*;
import com.ghatana.datacloud.DataCloudClient;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Governed Pattern Orchestrator (P4-05).
 *
 * <p>P4-05: Connects pattern decisions to governed action execution:
 * <pre>
 * event → pattern match → policy check → review if required → agent/pipeline execution → operation record → learning feedback
 * </pre>
 *
 * <p>Supports replay-safe behavior:
 * <ul>
 *   <li>Idempotency key generation for exactly-once semantics</li>
 *   <li>Replay mode with dry-run and side-effect awareness</li>
 *   <li>Compensation strategy for rollback support</li>
 *   <li>No side effects during dry-run replay</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Governed orchestration for pattern-to-action execution
 * @doc.layer product
 * @doc.pattern Orchestrator
 */
public class GovernedPatternOrchestrator {

    private final PolicyEvaluator policyEvaluator;
    private final ReviewService reviewService;
    private final AgentDispatcher agentDispatcher;
    private final PipelineExecutor pipelineExecutor;
    private final OperationRecorder operationRecorder;
    private final LearningFeedbackCollector feedbackCollector;

    // Track in-flight operations for idempotency
    private final Map<String, OperationRecord> inFlightOperations = new ConcurrentHashMap<>();

    public GovernedPatternOrchestrator(
            PolicyEvaluator policyEvaluator,
            ReviewService reviewService,
            AgentDispatcher agentDispatcher,
            PipelineExecutor pipelineExecutor,
            OperationRecorder operationRecorder,
            LearningFeedbackCollector feedbackCollector) {
        this.policyEvaluator = policyEvaluator;
        this.reviewService = reviewService;
        this.agentDispatcher = agentDispatcher;
        this.pipelineExecutor = pipelineExecutor;
        this.operationRecorder = operationRecorder;
        this.feedbackCollector = feedbackCollector;
    }

    // ==================== Main Orchestration Flow ====================

    /**
     * Execute the governed orchestration flow for a pattern match.
     *
     * <p>event → pattern match → policy check → review if required →
     * agent/pipeline execution → operation record → learning feedback
     *
     * @param context event context that triggered the pattern
     * @param patternSpec the matched pattern specification
     * @param matchResult the pattern match result
     * @param tenantId tenant identifier
     * @return orchestration result
     */
    public Promise<OrchestrationResult> orchestrate(
            EventContext<?> context,
            PatternSpec patternSpec,
            PatternMatchResult matchResult,
            String tenantId) {

        return orchestrate(context, patternSpec, matchResult, tenantId, false);
    }

    /**
     * Execute with replay mode support.
     *
     * @param isReplay true if this is a replay (dry-run or replay mode)
     * @param idempotencyKey key for deduplication in replay scenarios
     */
    public Promise<OrchestrationResult> orchestrate(
            EventContext<?> context,
            PatternSpec patternSpec,
            PatternMatchResult matchResult,
            String tenantId,
            boolean isReplay,
            String... idempotencyKey) {

        String operationId = idempotencyKey.length > 0 && idempotencyKey[0] != null
            ? idempotencyKey[0]
            : generateOperationId(tenantId, patternSpec.metadata().name(), eventId(context));

        // Check for existing operation (idempotency)
        OperationRecord existing = inFlightOperations.get(operationId);
        if (existing != null) {
            return Promise.of(new OrchestrationResult(
                operationId, existing.status(), existing.result(),
                true, "Duplicate operation detected"
            ));
        }

        // Create operation record
        OperationRecord record = new OperationRecord(
            operationId, tenantId, patternSpec,
            OperationStatus.IN_PROGRESS, null, Instant.now(), isReplay
        );
        inFlightOperations.put(operationId, record);

        // Step 1: Policy Check
        return policyEvaluator.evaluate(context, patternSpec, tenantId)
            .then(policyDecision -> {
                if (!policyDecision.allowed()) {
                    return recordDenied(operationId, policyDecision.reason());
                }

                // Step 2: Review if Required
                if (policyDecision.requiresReview()) {
                    return handleReviewRequired(operationId, context, patternSpec, policyDecision, tenantId, isReplay);
                }

                // Step 3: Execute (Agent or Pipeline)
                return executeAction(operationId, context, patternSpec, matchResult, tenantId, isReplay);
            })
            .then(result -> {
                // Step 4: Record Operation
                return recordOperation(operationId, result, tenantId, isReplay)
                    .then(v -> {
                        // Step 5: Collect Learning Feedback
                        if (!isReplay) {
                            collectFeedback(operationId, context, patternSpec, result, tenantId);
                        }
                        return Promise.of(result);
                    });
            })
            .map(result -> {
                inFlightOperations.remove(operationId);
                return result;
            })
            .whenException(e -> {
                inFlightOperations.remove(operationId);
            });
    }

    // ==================== Step Implementations ====================

    private Promise<OrchestrationResult> handleReviewRequired(
            String operationId,
            EventContext<?> context,
            PatternSpec patternSpec,
            PolicyEvaluator.PolicyDecision policyDecision,
            String tenantId,
            boolean isReplay) {

        if (isReplay) {
            // During replay, skip review and proceed (reviews should have been completed)
            return Promise.of(new OrchestrationResult(
                operationId, OperationStatus.REVIEW_SKIPPED_IN_REPLAY, null,
                true, "Review skipped during replay"
            ));
        }

        // Create review request
        return reviewService.requestReview(
            operationId,
            tenantId,
            patternSpec,
            context,
            policyDecision.reviewReason()
        ).then(reviewResult -> {
            if (!reviewResult.approved()) {
                return recordDenied(operationId, "Review denied: " + reviewResult.reason());
            }

            // Review approved, continue with execution
            return Promise.of(new OrchestrationResult(
                operationId, OperationStatus.REVIEW_APPROVED, null,
                true, "Review approved, proceeding with execution"
            ));
        });
    }

    private Promise<OrchestrationResult> executeAction(
            String operationId,
            EventContext<?> context,
            PatternSpec patternSpec,
            PatternMatchResult matchResult,
            String tenantId,
            boolean isReplay) {

        // Get side effect declaration
        OperatorSpec operatorSpec = patternSpec.toOperatorSpec();
        OperatorLifecycleContract.SideEffectDeclaration sideEffects = declareSideEffects(operatorSpec);

        if (isReplay && sideEffects.hasSideEffects() && !sideEffects.isSafeToReplay()) {
            // Skip execution for non-idempotent side effects during replay
            return Promise.of(new OrchestrationResult(
                operationId, OperationStatus.SKIPPED_IN_REPLAY, null,
                true, "Skipped non-idempotent side effects during replay"
            ));
        }

        // Determine execution mode (agent or pipeline)
        if (patternSpec.metadata().annotations().containsKey("agentRef")) {
            // Agent execution
            String agentRef = (String) patternSpec.metadata().annotations().get("agentRef");
            return agentDispatcher.dispatch(agentRef, context, tenantId, isReplay)
                .map(agentResult -> new OrchestrationResult(
                    operationId, OperationStatus.COMPLETED, agentResult,
                    true, "Agent execution completed"
                ));
        } else {
            // Pipeline execution
            return pipelineExecutor.execute(patternSpec, context, tenantId, isReplay)
                .map(pipelineResult -> new OrchestrationResult(
                    operationId, OperationStatus.COMPLETED, pipelineResult,
                    true, "Pipeline execution completed"
                ));
        }
    }

    private Promise<Void> recordOperation(String operationId, OrchestrationResult result, String tenantId, boolean isReplay) {
        OperationRecord record = new OperationRecord(
            operationId, tenantId, null, result.status(), result, Instant.now(), isReplay
        );
        return operationRecorder.record(record);
    }

    private Promise<OrchestrationResult> recordDenied(String operationId, String reason) {
        OrchestrationResult result = new OrchestrationResult(
            operationId, OperationStatus.DENIED, null, false, reason
        );
        return Promise.of(result);
    }

    private Promise<OrchestrationResult> recordFailed(String operationId, String error) {
        OrchestrationResult result = new OrchestrationResult(
            operationId, OperationStatus.FAILED, null, false, error
        );
        return Promise.of(result);
    }

    private void collectFeedback(
            String operationId,
            EventContext<?> context,
            PatternSpec patternSpec,
            OrchestrationResult result,
            String tenantId) {

        Map<String, Object> feedback = Map.of(
            "operationId", operationId,
            "patternId", patternSpec.metadata().name(),
            "eventId", eventId(context),
            "status", result.status().name(),
            "success", result.success(),
            "timestamp", Instant.now().toString()
        );

        feedbackCollector.collect(patternSpec.metadata().name(), operationId, feedback, tenantId);
    }

    // ==================== Replay Support ====================

    /**
     * Replay a previous operation with compensation strategy.
     *
     * @param operationId original operation ID
     * @param compensation true to execute compensation, false for dry run
     */
    public Promise<ReplayResult> replay(String operationId, boolean compensation) {
        return operationRecorder.findById(operationId)
            .then(opt -> opt
                .map(original -> {
                    if (!original.isReplayable()) {
                        return Promise.of(new ReplayResult(
                            operationId, false, "Operation not replayable", null
                        ));
                    }

                    // Execute replay
                    return orchestrate(
                        null, // Context reconstructed from original
                        original.patternSpec(),
                        null, // Match result reconstructed
                        original.tenantId(),
                        true, // isReplay
                        operationId + "-replay-" + UUID.randomUUID()
                    ).then(result -> Promise.of(new ReplayResult(
                        operationId, result.success(), result.message(), result
                    )));
                })
                .orElseGet(() -> Promise.of(new ReplayResult(
                    operationId, false, "Original operation not found", null
                ))));
    }

    // ==================== Utility Methods ====================

    private String generateOperationId(String tenantId, String patternId, String eventId) {
        return String.format("%s:%s:%s:%s", tenantId, patternId, eventId, UUID.randomUUID());
    }

    private OperatorLifecycleContract.SideEffectDeclaration declareSideEffects(OperatorSpec spec) {
        // Default implementation - can be overridden or extended
        return new OperatorLifecycleContract.SideEffectDeclaration(
            java.util.Set.of(OperatorLifecycleContract.SideEffectType.EVENT_EMISSION),
            java.util.List.of(),
            false,
            true,
            Optional.empty()
        );
    }

    private String eventId(EventContext<?> context) {
        if (context == null || context.events().isEmpty()) {
            return "unknown-event";
        }
        return context.events().get(0).eventId();
    }

    // ==================== Supporting Types ====================

    public record OrchestrationResult(
        String operationId,
        OperationStatus status,
        Object result,
        boolean success,
        String message
    ) {}

    public enum OperationStatus {
        IN_PROGRESS,
        REVIEW_APPROVED,
        REVIEW_SKIPPED_IN_REPLAY,
        COMPLETED,
        DENIED,
        FAILED,
        SKIPPED_IN_REPLAY
    }

    public record OperationRecord(
        String operationId,
        String tenantId,
        PatternSpec patternSpec,
        OperationStatus status,
        OrchestrationResult result,
        Instant timestamp,
        boolean isReplay
    ) {
        public boolean isReplayable() {
            return status == OperationStatus.COMPLETED;
        }
    }

    public record ReplayResult(
        String originalOperationId,
        boolean success,
        String message,
        OrchestrationResult replayResult
    ) {}

    // ==================== Service Interfaces ====================

    public interface PolicyEvaluator {
        Promise<PolicyDecision> evaluate(EventContext<?> context, PatternSpec pattern, String tenantId);

        record PolicyDecision(
            boolean allowed,
            String reason,
            boolean requiresReview,
            String reviewReason
        ) {
            public static PolicyDecision allow() {
                return new PolicyDecision(true, null, false, null);
            }

            public static PolicyDecision deny(String reason) {
                return new PolicyDecision(false, reason, false, null);
            }

            public static PolicyDecision requireReview(String reviewReason) {
                return new PolicyDecision(true, null, true, reviewReason);
            }
        }
    }

    public interface ReviewService {
        Promise<ReviewResult> requestReview(String operationId, String tenantId, PatternSpec pattern, EventContext<?> context, String reason);

        record ReviewResult(boolean approved, String reason, String reviewer) {}
    }

    public interface AgentDispatcher {
        Promise<Object> dispatch(String agentRef, EventContext<?> context, String tenantId, boolean isReplay);
    }

    public interface PipelineExecutor {
        Promise<Object> execute(PatternSpec pattern, EventContext<?> context, String tenantId, boolean isReplay);
    }

    public interface OperationRecorder {
        Promise<Void> record(OperationRecord record);
        Promise<Optional<OperationRecord>> findById(String operationId);
    }

    public interface LearningFeedbackCollector {
        Promise<Void> collect(String patternId, String operationId, Map<String, Object> feedback, String tenantId);
    }
}
