/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.learning;

import com.ghatana.agent.mastery.MasteryState;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Default implementation of LearningDeltaEvaluator with target-specific validation checks.
 *
 * <p>Evaluates learning deltas based on:
 * <ul>
 *   <li>Evidence completeness and quality</li>
 *   <li>Target-specific validation rules</li>
 *   <li>Confidence thresholds</li>
 *   <li>Safety and compatibility checks</li>
 *   <li>MASTERY_STATE governance enforcement</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Default implementation of LearningDeltaEvaluator with target-specific checks
 * @doc.layer agent-core
 * @doc.pattern Service
 */
public final class DefaultLearningDeltaEvaluator implements LearningDeltaEvaluator {

    private final double defaultConfidenceThreshold;
    private final double minimumEvidenceCount;

    /**
     * Creates a default learning delta evaluator.
     */
    public DefaultLearningDeltaEvaluator() {
        this(0.7, 1);
    }

    /**
     * Creates a learning delta evaluator with custom thresholds.
     *
     * @param defaultConfidenceThreshold minimum confidence for auto-approval
     * @param minimumEvidenceCount minimum number of evidence references required
     */
    public DefaultLearningDeltaEvaluator(double defaultConfidenceThreshold, int minimumEvidenceCount) {
        this.defaultConfidenceThreshold = defaultConfidenceThreshold;
        this.minimumEvidenceCount = minimumEvidenceCount;
    }

    @Override
    @NotNull
    public Promise<EvaluationResult> evaluate(@NotNull LearningDelta delta) {
        // Enforce MASTERY_STATE governance
        if (delta.target() == LearningTarget.MASTERY_STATE) {
            EvaluationResult governanceCheck = validateMasteryStateGovernance(delta);
            if (!governanceCheck.approved()) {
                return Promise.of(governanceCheck);
            }
        }

        // Check for required evidence
        if (delta.evidenceRefs().isEmpty()) {
            return Promise.of(EvaluationResult.rejected(
                    delta.deltaId(),
                    0.0,
                    ReasonCode.INSUFFICIENT_EVIDENCE,
                    "No evidence references provided. Learning deltas require evidence for evaluation.",
                    SafetyGrade.MEDIUM_RISK,
                    List.of("Gather evidence from execution traces", "Document learning outcomes", "Resubmit with evidence")
            ));
        }

        // Check minimum evidence count
        if (delta.evidenceRefs().size() < minimumEvidenceCount) {
            return Promise.of(EvaluationResult.rejected(
                    delta.deltaId(),
                    0.0,
                    ReasonCode.INSUFFICIENT_EVIDENCE,
                    String.format("Insufficient evidence: %d evidence references provided, minimum %d required.",
                            delta.evidenceRefs().size(), minimumEvidenceCount),
                    SafetyGrade.MEDIUM_RISK,
                    List.of("Gather more evidence", "Ensure sufficient execution traces", "Resubmit when evidence count meets threshold")
            ));
        }

        // Target-specific validation
        EvaluationResult targetCheck = validateTargetSpecific(delta);
        if (!targetCheck.approved()) {
            return Promise.of(targetCheck);
        }

        // Check confidence threshold
        if (delta.confidenceAfter() < defaultConfidenceThreshold && !delta.requiresHumanReview()) {
            return Promise.of(EvaluationResult.rejected(
                    delta.deltaId(),
                    delta.confidenceAfter(),
                    ReasonCode.LOW_CONFIDENCE,
                    String.format("Confidence %f below threshold %f. Requires human review.",
                            delta.confidenceAfter(), defaultConfidenceThreshold),
                    SafetyGrade.LOW_RISK,
                    List.of("Request human review", "Improve confidence with more evidence", "Resubmit with higher confidence")
            ));
        }

        // Check for rollback reference for execution targets
        if (delta.target().isExecutionTarget() && delta.rollbackRef() == null) {
            return Promise.of(EvaluationResult.rejected(
                    delta.deltaId(),
                    delta.confidenceAfter(),
                    ReasonCode.SAFETY_CHECK_FAILED,
                    "Execution targets require a rollback reference for safety.",
                    SafetyGrade.HIGH_RISK,
                    List.of("Add rollback reference", "Ensure safe rollback mechanism", "Resubmit with rollback capability")
            ));
        }

        // All checks passed
        return Promise.of(EvaluationResult.approved(
                delta.deltaId(),
                delta.confidenceAfter(),
                "Delta passed all validation checks: evidence complete, target-specific checks passed, confidence acceptable."
        ));
    }

    /**
     * Validates MASTERY_STATE governance requirements.
     * MASTERY_STATE transitions require governance workflow authorization.
     */
    @NotNull
    private EvaluationResult validateMasteryStateGovernance(@NotNull LearningDelta delta) {
        // Check if governance workflow is enabled (check labels for governance flag)
        boolean isGovernanceWorkflow = Boolean.parseBoolean(delta.labels().getOrDefault("governanceWorkflow", "false"));
        
        if (!isGovernanceWorkflow) {
            return EvaluationResult.rejected(
                    delta.deltaId(),
                    0.0,
                    ReasonCode.GOVERNANCE_VIOLATION,
                    "MASTERY_STATE transitions require governance workflow authorization.",
                    SafetyGrade.CRITICAL,
                    List.of("Enable governance workflow", "Obtain governance authorization", "Resubmit with governance flag")
            );
        }
        
        // Validate proposed state is valid
        Object stateObj = delta.proposedContent().get("state");
        if (stateObj == null) {
            return EvaluationResult.rejected(
                    delta.deltaId(),
                    0.0,
                    ReasonCode.MISSING_REQUIRED_FIELD,
                    "MASTERY_STATE delta missing 'state' field.",
                    SafetyGrade.HIGH_RISK,
                    List.of("Add target state to proposed content", "Specify valid MasteryState", "Resubmit with state field")
            );
        }
        
        try {
            MasteryState.valueOf((String) stateObj);
        } catch (IllegalArgumentException e) {
            return EvaluationResult.rejected(
                    delta.deltaId(),
                    0.0,
                    ReasonCode.TARGET_SPECIFIC_CHECK_FAILED,
                    String.format("Invalid mastery state: %s", stateObj),
                    SafetyGrade.HIGH_RISK,
                    List.of("Use valid MasteryState value", "Check state transition validity", "Resubmit with valid state")
            );
        }
        
        return EvaluationResult.approved(
                delta.deltaId(),
                delta.confidenceAfter(),
                "MASTERY_STATE governance validation passed."
        );
    }

    /**
     * Performs target-specific validation checks.
     *
     * @param delta learning delta to validate
     * @return evaluation result for target-specific checks
     */
    @NotNull
    private EvaluationResult validateTargetSpecific(@NotNull LearningDelta delta) {
        return switch (delta.target()) {
            case EPISODIC_MEMORY -> validateEpisodicMemory(delta);
            case SEMANTIC_FACT -> validateSemanticFact(delta);
            case PROCEDURAL_SKILL -> validateProceduralSkill(delta);
            case NEGATIVE_KNOWLEDGE -> validateNegativeKnowledge(delta);
            case RETRIEVAL_POLICY -> validateRetrievalPolicy(delta);
            case CONFIDENCE_THRESHOLD -> validateConfidenceThreshold(delta);
            case ROUTING_POLICY -> validateRoutingPolicy(delta);
            case PROMPT_TEMPLATE -> validatePromptTemplate(delta);
            case PLANNER_POLICY -> validatePlannerPolicy(delta);
            case MODEL_ADAPTER -> validateModelAdapter(delta);
            case MASTERY_STATE -> validateMasteryState(delta);
        };
    }

    /**
     * Validates episodic memory learning deltas.
     */
    @NotNull
    private EvaluationResult validateEpisodicMemory(@NotNull LearningDelta delta) {
        // Episodic memory is low-risk, minimal validation
        if (delta.proposedContent() == null || delta.proposedContent().isEmpty()) {
            return EvaluationResult.rejected(
                    delta.deltaId(),
                    0.0,
                    ReasonCode.MISSING_REQUIRED_FIELD,
                    "Episodic memory delta has no proposed content.",
                    SafetyGrade.LOW_RISK,
                    List.of("Add episode content", "Document experience", "Resubmit with content")
            );
        }
        return EvaluationResult.approved(
                delta.deltaId(),
                delta.confidenceAfter(),
                "Episodic memory validation passed."
        );
    }

    /**
     * Validates semantic fact learning deltas.
     */
    @NotNull
    private EvaluationResult validateSemanticFact(@NotNull LearningDelta delta) {
        if (delta.semanticFactId() == null) {
            return EvaluationResult.rejected(
                    delta.deltaId(),
                    0.0,
                    ReasonCode.MISSING_REQUIRED_FIELD,
                    "Semantic fact delta requires semanticFactId.",
                    SafetyGrade.LOW_RISK,
                    List.of("Add semanticFactId", "Link to fact storage", "Resubmit with fact ID")
            );
        }
        if (delta.proposedContent() == null || delta.proposedContent().isEmpty()) {
            return EvaluationResult.rejected(
                    delta.deltaId(),
                    0.0,
                    ReasonCode.MISSING_REQUIRED_FIELD,
                    "Semantic fact delta has no proposed content.",
                    SafetyGrade.LOW_RISK,
                    List.of("Add fact content", "Document semantic knowledge", "Resubmit with content")
            );
        }
        return EvaluationResult.approved(
                delta.deltaId(),
                delta.confidenceAfter(),
                "Semantic fact validation passed."
        );
    }

    /**
     * Validates procedural skill learning deltas.
     */
    @NotNull
    private EvaluationResult validateProceduralSkill(@NotNull LearningDelta delta) {
        if (delta.procedureId() == null) {
            return EvaluationResult.rejected(
                    delta.deltaId(),
                    0.0,
                    ReasonCode.MISSING_REQUIRED_FIELD,
                    "Procedural skill delta requires procedureId.",
                    SafetyGrade.MEDIUM_RISK,
                    List.of("Add procedureId", "Link to procedure storage", "Resubmit with procedure ID")
            );
        }
        if (delta.proposedContent() == null || delta.proposedContent().isEmpty()) {
            return EvaluationResult.rejected(
                    delta.deltaId(),
                    0.0,
                    ReasonCode.MISSING_REQUIRED_FIELD,
                    "Procedural skill delta has no proposed content.",
                    SafetyGrade.MEDIUM_RISK,
                    List.of("Add procedure content", "Document skill steps", "Resubmit with content")
            );
        }
        // Procedural skills with confidence below 0.8 require human review when evidence exists.
        // Evidence presence is already guaranteed by the outer evaluate() guard.
        if (delta.confidenceAfter() < 0.8) {
            return EvaluationResult.pendingHumanReview(
                    delta.deltaId(),
                    delta.confidenceAfter(),
                    "Procedural skill confidence " + delta.confidenceAfter() + " < 0.8; human review required before promotion."
            );
        }
        return EvaluationResult.approved(
                delta.deltaId(),
                delta.confidenceAfter(),
                "Procedural skill validation passed."
        );
    }

    /**
     * Validates negative knowledge learning deltas.
     */
    @NotNull
    private EvaluationResult validateNegativeKnowledge(@NotNull LearningDelta delta) {
        if (delta.negativeKnowledgeId() == null) {
            return EvaluationResult.rejected(
                    delta.deltaId(),
                    0.0,
                    ReasonCode.MISSING_REQUIRED_FIELD,
                    "Negative knowledge delta requires negativeKnowledgeId.",
                    SafetyGrade.HIGH_RISK,
                    List.of("Add negativeKnowledgeId", "Link to failure mode storage", "Resubmit with negative knowledge ID")
            );
        }
        if (delta.proposedContent() == null || delta.proposedContent().isEmpty()) {
            return EvaluationResult.rejected(
                    delta.deltaId(),
                    0.0,
                    ReasonCode.MISSING_REQUIRED_FIELD,
                    "Negative knowledge delta has no proposed content.",
                    SafetyGrade.HIGH_RISK,
                    List.of("Add failure mode description", "Document anti-pattern", "Resubmit with content")
            );
        }
        return EvaluationResult.approved(
                delta.deltaId(),
                delta.confidenceAfter(),
                "Negative knowledge validation passed."
        );
    }

    /**
     * Validates retrieval policy learning deltas.
     */
    @NotNull
    private EvaluationResult validateRetrievalPolicy(@NotNull LearningDelta delta) {
        if (delta.proposedContent() == null || delta.proposedContent().isEmpty()) {
            return EvaluationResult.rejected(
                    delta.deltaId(),
                    0.0,
                    ReasonCode.MISSING_REQUIRED_FIELD,
                    "Retrieval policy delta has no proposed content.",
                    SafetyGrade.MEDIUM_RISK,
                    List.of("Add policy content", "Document retrieval rules", "Resubmit with content")
            );
        }
        // Policy changes require evaluation references
        if (delta.evaluationRefs().isEmpty()) {
            return EvaluationResult.rejected(
                    delta.deltaId(),
                    0.0,
                    ReasonCode.EVALUATION_REFS_REQUIRED,
                    "Retrieval policy requires evaluation references.",
                    SafetyGrade.MEDIUM_RISK,
                    List.of("Add evaluation references", "Run policy evaluation tests", "Resubmit with eval refs")
            );
        }
        return EvaluationResult.approved(
                delta.deltaId(),
                delta.confidenceAfter(),
                "Retrieval policy validation passed."
        );
    }

    /**
     * Validates confidence threshold learning deltas.
     */
    @NotNull
    private EvaluationResult validateConfidenceThreshold(@NotNull LearningDelta delta) {
        if (delta.proposedContent() == null || delta.proposedContent().isEmpty()) {
            return EvaluationResult.rejected(
                    delta.deltaId(),
                    0.0,
                    ReasonCode.MISSING_REQUIRED_FIELD,
                    "Confidence threshold delta has no proposed content.",
                    SafetyGrade.LOW_RISK,
                    List.of("Add threshold content", "Document confidence rules", "Resubmit with content")
            );
        }
        // Validate proposed threshold is in valid range [0, 1]
        Object thresholdObj = delta.proposedContent().get("threshold");
        if (thresholdObj == null) {
            return EvaluationResult.rejected(
                    delta.deltaId(),
                    0.0,
                    ReasonCode.MISSING_REQUIRED_FIELD,
                    "Confidence threshold delta missing 'threshold' field.",
                    SafetyGrade.LOW_RISK,
                    List.of("Add threshold value", "Specify confidence threshold", "Resubmit with threshold field")
            );
        }
        double threshold = ((Number) thresholdObj).doubleValue();
        if (threshold < 0.0 || threshold > 1.0) {
            return EvaluationResult.rejected(
                    delta.deltaId(),
                    0.0,
                    ReasonCode.TARGET_SPECIFIC_CHECK_FAILED,
                    String.format("Confidence threshold %f outside valid range [0, 1].", threshold),
                    SafetyGrade.MEDIUM_RISK,
                    List.of("Adjust threshold to valid range", "Use value between 0 and 1", "Resubmit with valid threshold")
            );
        }
        return EvaluationResult.approved(
                delta.deltaId(),
                delta.confidenceAfter(),
                "Confidence threshold validation passed."
        );
    }

    /**
     * Validates routing policy learning deltas.
     */
    @NotNull
    private EvaluationResult validateRoutingPolicy(@NotNull LearningDelta delta) {
        if (delta.proposedContent() == null || delta.proposedContent().isEmpty()) {
            return EvaluationResult.rejected(
                    delta.deltaId(),
                    0.0,
                    ReasonCode.MISSING_REQUIRED_FIELD,
                    "Routing policy delta has no proposed content.",
                    SafetyGrade.MEDIUM_RISK,
                    List.of("Add policy content", "Document routing rules", "Resubmit with content")
            );
        }
        // Policy changes require evaluation references
        if (delta.evaluationRefs().isEmpty()) {
            return EvaluationResult.rejected(
                    delta.deltaId(),
                    0.0,
                    ReasonCode.EVALUATION_REFS_REQUIRED,
                    "Routing policy requires evaluation references.",
                    SafetyGrade.MEDIUM_RISK,
                    List.of("Add evaluation references", "Run routing evaluation tests", "Resubmit with eval refs")
            );
        }
        return EvaluationResult.approved(
                delta.deltaId(),
                delta.confidenceAfter(),
                "Routing policy validation passed."
        );
    }

    /**
     * Validates prompt template learning deltas.
     */
    @NotNull
    private EvaluationResult validatePromptTemplate(@NotNull LearningDelta delta) {
        if (delta.proposedContent() == null || delta.proposedContent().isEmpty()) {
            return EvaluationResult.rejected(
                    delta.deltaId(),
                    0.0,
                    ReasonCode.MISSING_REQUIRED_FIELD,
                    "Prompt template delta has no proposed content.",
                    SafetyGrade.LOW_RISK,
                    List.of("Add template content", "Document prompt structure", "Resubmit with content")
            );
        }
        // Prompt templates require evaluation references
        if (delta.evaluationRefs().isEmpty()) {
            return EvaluationResult.rejected(
                    delta.deltaId(),
                    0.0,
                    ReasonCode.EVALUATION_REFS_REQUIRED,
                    "Prompt template requires evaluation references.",
                    SafetyGrade.LOW_RISK,
                    List.of("Add evaluation references", "Run prompt effectiveness tests", "Resubmit with eval refs")
            );
        }
        return EvaluationResult.approved(
                delta.deltaId(),
                delta.confidenceAfter(),
                "Prompt template validation passed."
        );
    }

    /**
     * Validates planner policy learning deltas.
     */
    @NotNull
    private EvaluationResult validatePlannerPolicy(@NotNull LearningDelta delta) {
        if (delta.proposedContent() == null || delta.proposedContent().isEmpty()) {
            return EvaluationResult.rejected(
                    delta.deltaId(),
                    0.0,
                    ReasonCode.MISSING_REQUIRED_FIELD,
                    "Planner policy delta has no proposed content.",
                    SafetyGrade.HIGH_RISK,
                    List.of("Add policy content", "Document planning rules", "Resubmit with content")
            );
        }
        // Planner policies are execution targets, require higher confidence
        if (delta.confidenceAfter() < 0.85) {
            return EvaluationResult.rejected(
                    delta.deltaId(),
                    delta.confidenceAfter(),
                    ReasonCode.LOW_CONFIDENCE,
                    "Planner policy requires confidence >= 0.85 for safety.",
                    SafetyGrade.HIGH_RISK,
                    List.of("Improve confidence with more evidence", "Request human review", "Resubmit with higher confidence")
            );
        }
        // Policy changes require evaluation references
        if (delta.evaluationRefs().isEmpty()) {
            return EvaluationResult.rejected(
                    delta.deltaId(),
                    0.0,
                    ReasonCode.EVALUATION_REFS_REQUIRED,
                    "Planner policy requires evaluation references.",
                    SafetyGrade.HIGH_RISK,
                    List.of("Add evaluation references", "Run planner evaluation tests", "Resubmit with eval refs")
            );
        }
        return EvaluationResult.approved(
                delta.deltaId(),
                delta.confidenceAfter(),
                "Planner policy validation passed."
        );
    }

    /**
     * Validates model adapter learning deltas.
     */
    @NotNull
    private EvaluationResult validateModelAdapter(@NotNull LearningDelta delta) {
        // Model adapters are offline-only, high-risk
        if (!delta.requiresHumanReview()) {
            return EvaluationResult.rejected(
                    delta.deltaId(),
                    0.0,
                    ReasonCode.OFFLINE_ONLY_TARGET,
                    "Model adapter requires human review flag (offline-only, high-risk target).",
                    SafetyGrade.CRITICAL,
                    List.of("Set requiresHumanReview flag", "Obtain offline authorization", "Resubmit with human review flag")
            );
        }
        if (delta.proposedContent() == null || delta.proposedContent().isEmpty()) {
            return EvaluationResult.rejected(
                    delta.deltaId(),
                    0.0,
                    ReasonCode.MISSING_REQUIRED_FIELD,
                    "Model adapter delta has no proposed content.",
                    SafetyGrade.CRITICAL,
                    List.of("Add adapter content", "Document model integration", "Resubmit with content")
            );
        }
        // Model adapters require very high confidence
        if (delta.confidenceAfter() < 0.9) {
            return EvaluationResult.rejected(
                    delta.deltaId(),
                    delta.confidenceAfter(),
                    ReasonCode.LOW_CONFIDENCE,
                    "Model adapter requires confidence >= 0.9 (offline-only, high-risk).",
                    SafetyGrade.CRITICAL,
                    List.of("Improve confidence with extensive testing", "Request human review", "Resubmit with higher confidence")
            );
        }
        // Require comprehensive evaluation
        if (delta.evaluationRefs().isEmpty()) {
            return EvaluationResult.rejected(
                    delta.deltaId(),
                    0.0,
                    ReasonCode.EVALUATION_REFS_REQUIRED,
                    "Model adapter requires evaluation references.",
                    SafetyGrade.CRITICAL,
                    List.of("Add comprehensive evaluation references", "Run full integration tests", "Resubmit with eval refs")
            );
        }
        return EvaluationResult.approved(
                delta.deltaId(),
                delta.confidenceAfter(),
                "Model adapter validation passed (marked for human review due to high-risk nature)."
        );
    }

    /**
     * Validates mastery state learning deltas.
     */
    @NotNull
    private EvaluationResult validateMasteryState(@NotNull LearningDelta delta) {
        if (delta.proposedContent() == null || delta.proposedContent().isEmpty()) {
            return EvaluationResult.rejected(
                    delta.deltaId(),
                    0.0,
                    ReasonCode.MISSING_REQUIRED_FIELD,
                    "Mastery state delta has no proposed content.",
                    SafetyGrade.HIGH_RISK,
                    List.of("Add state transition content", "Document mastery change", "Resubmit with content")
            );
        }
        // Validate proposed state is valid
        Object stateObj = delta.proposedContent().get("state");
        if (stateObj == null) {
            return EvaluationResult.rejected(
                    delta.deltaId(),
                    0.0,
                    ReasonCode.MISSING_REQUIRED_FIELD,
                    "Mastery state delta missing 'state' field.",
                    SafetyGrade.HIGH_RISK,
                    List.of("Add target state", "Specify valid MasteryState", "Resubmit with state field")
            );
        }
        try {
            MasteryState.valueOf((String) stateObj);
        } catch (IllegalArgumentException e) {
            return EvaluationResult.rejected(
                    delta.deltaId(),
                    0.0,
                    ReasonCode.TARGET_SPECIFIC_CHECK_FAILED,
                    String.format("Invalid mastery state: %s", stateObj),
                    SafetyGrade.HIGH_RISK,
                    List.of("Use valid MasteryState value", "Check state transition validity", "Resubmit with valid state")
            );
        }
        return EvaluationResult.approved(
                delta.deltaId(),
                delta.confidenceAfter(),
                "Mastery state validation passed."
        );
    }
}
