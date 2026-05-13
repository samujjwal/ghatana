/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.learning;

import com.ghatana.agent.mastery.MasteryState;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * Default implementation of LearningDeltaEvaluator with target-specific validation checks.
 *
 * <p>Evaluates learning deltas based on:
 * <ul>
 *   <li>Evidence completeness and quality</li>
 *   <li>Target-specific validation rules</li>
 *   <li>Confidence thresholds</li>
 *   <li>Safety and compatibility checks</li>
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
        // Check for required evidence
        if (delta.evidenceRefs().isEmpty()) {
            return Promise.of(EvaluationResult.rejected(
                    delta.deltaId(),
                    0.0,
                    "No evidence references provided. Learning deltas require evidence for evaluation."
            ));
        }

        // Check minimum evidence count
        if (delta.evidenceRefs().size() < minimumEvidenceCount) {
            return Promise.of(EvaluationResult.rejected(
                    delta.deltaId(),
                    0.0,
                    String.format("Insufficient evidence: %d evidence references provided, minimum %d required.",
                            delta.evidenceRefs().size(), minimumEvidenceCount)
            ));
        }

        // Target-specific validation
        EvaluationResult targetCheck = validateTargetSpecific(delta);
        if (!targetCheck.approved()) {
            return Promise.of(targetCheck);
        }

        // Check confidence threshold - route to human review instead of rejecting
        if (delta.confidenceAfter() < defaultConfidenceThreshold && !delta.requiresHumanReview()) {
            return Promise.of(EvaluationResult.pendingHumanReview(
                    delta.deltaId(),
                    delta.confidenceAfter(),
                    String.format("Confidence %f below threshold %f. Requires human review.",
                            delta.confidenceAfter(), defaultConfidenceThreshold)
            ));
        }

        // Check for rollback reference for execution targets
        if (delta.target().isExecutionTarget() && delta.rollbackRef() == null) {
            return Promise.of(EvaluationResult.rejected(
                    delta.deltaId(),
                    delta.confidenceAfter(),
                    "Execution targets require a rollback reference for safety."
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
            return EvaluationResult.rejected(delta.deltaId(), 0.0, "Episodic memory delta has no proposed content.");
        }
        return EvaluationResult.approved(delta.deltaId(), delta.confidenceAfter(), "Episodic memory validation passed.");
    }

    /**
     * Validates semantic fact learning deltas.
     */
    @NotNull
    private EvaluationResult validateSemanticFact(@NotNull LearningDelta delta) {
        if (delta.semanticFactId() == null) {
            return EvaluationResult.rejected(delta.deltaId(), 0.0, "Semantic fact delta requires semanticFactId.");
        }
        // Proposed content can be empty for semantic facts
        return EvaluationResult.approved(delta.deltaId(), delta.confidenceAfter(), "Semantic fact validation passed.");
    }

    /**
     * Validates procedural skill learning deltas.
     */
    @NotNull
    private EvaluationResult validateProceduralSkill(@NotNull LearningDelta delta) {
        if (delta.procedureId() == null) {
            return EvaluationResult.rejected(delta.deltaId(), 0.0, "Procedural skill delta requires procedureId.");
        }
        // Proposed content can be empty for procedural skills
        // Procedural skills require higher confidence
        if (delta.confidenceAfter() < 0.8) {
            return EvaluationResult.rejected(delta.deltaId(), delta.confidenceAfter(),
                    "Procedural skill requires confidence >= 0.8 for safety.");
        }
        return EvaluationResult.approved(delta.deltaId(), delta.confidenceAfter(), "Procedural skill validation passed.");
    }

    /**
     * Validates negative knowledge learning deltas.
     */
    @NotNull
    private EvaluationResult validateNegativeKnowledge(@NotNull LearningDelta delta) {
        if (delta.negativeKnowledgeId() == null) {
            return EvaluationResult.rejected(delta.deltaId(), 0.0, "Negative knowledge delta requires negativeKnowledgeId.");
        }
        if (delta.proposedContent() == null || delta.proposedContent().isEmpty()) {
            return EvaluationResult.rejected(delta.deltaId(), 0.0, "Negative knowledge delta has no proposed content.");
        }
        return EvaluationResult.approved(delta.deltaId(), delta.confidenceAfter(), "Negative knowledge validation passed.");
    }

    /**
     * Validates retrieval policy learning deltas.
     */
    @NotNull
    private EvaluationResult validateRetrievalPolicy(@NotNull LearningDelta delta) {
        if (delta.proposedContent() == null || delta.proposedContent().isEmpty()) {
            return EvaluationResult.rejected(delta.deltaId(), 0.0, "Retrieval policy delta has no proposed content.");
        }
        // Policy changes require evaluation references
        if (delta.evaluationRefs().isEmpty()) {
            return EvaluationResult.rejected(delta.deltaId(), 0.0, "Retrieval policy requires evaluation references.");
        }
        return EvaluationResult.approved(delta.deltaId(), delta.confidenceAfter(), "Retrieval policy validation passed.");
    }

    /**
     * Validates confidence threshold learning deltas.
     */
    @NotNull
    private EvaluationResult validateConfidenceThreshold(@NotNull LearningDelta delta) {
        if (delta.proposedContent() == null || delta.proposedContent().isEmpty()) {
            return EvaluationResult.rejected(delta.deltaId(), 0.0, "Confidence threshold delta has no proposed content.");
        }
        // Validate proposed threshold is in valid range [0, 1]
        Object thresholdObj = delta.proposedContent().get("threshold");
        if (thresholdObj == null) {
            return EvaluationResult.rejected(delta.deltaId(), 0.0, "Confidence threshold delta missing 'threshold' field.");
        }
        double threshold = ((Number) thresholdObj).doubleValue();
        if (threshold < 0.0 || threshold > 1.0) {
            return EvaluationResult.rejected(delta.deltaId(), 0.0,
                    String.format("Confidence threshold %f outside valid range [0, 1].", threshold));
        }
        return EvaluationResult.approved(delta.deltaId(), delta.confidenceAfter(), "Confidence threshold validation passed.");
    }

    /**
     * Validates routing policy learning deltas.
     */
    @NotNull
    private EvaluationResult validateRoutingPolicy(@NotNull LearningDelta delta) {
        if (delta.proposedContent() == null || delta.proposedContent().isEmpty()) {
            return EvaluationResult.rejected(delta.deltaId(), 0.0, "Routing policy delta has no proposed content.");
        }
        // Policy changes require evaluation references
        if (delta.evaluationRefs().isEmpty()) {
            return EvaluationResult.rejected(delta.deltaId(), 0.0, "Routing policy requires evaluation references.");
        }
        return EvaluationResult.approved(delta.deltaId(), delta.confidenceAfter(), "Routing policy validation passed.");
    }

    /**
     * Validates prompt template learning deltas.
     */
    @NotNull
    private EvaluationResult validatePromptTemplate(@NotNull LearningDelta delta) {
        if (delta.proposedContent() == null || delta.proposedContent().isEmpty()) {
            return EvaluationResult.rejected(delta.deltaId(), 0.0, "Prompt template delta has no proposed content.");
        }
        // Prompt templates require evaluation references
        if (delta.evaluationRefs().isEmpty()) {
            return EvaluationResult.rejected(delta.deltaId(), 0.0, "Prompt template requires evaluation references.");
        }
        return EvaluationResult.approved(delta.deltaId(), delta.confidenceAfter(), "Prompt template validation passed.");
    }

    /**
     * Validates planner policy learning deltas.
     */
    @NotNull
    private EvaluationResult validatePlannerPolicy(@NotNull LearningDelta delta) {
        if (delta.proposedContent() == null || delta.proposedContent().isEmpty()) {
            return EvaluationResult.rejected(delta.deltaId(), 0.0, "Planner policy delta has no proposed content.");
        }
        // Planner policies are execution targets, require higher confidence
        if (delta.confidenceAfter() < 0.85) {
            return EvaluationResult.rejected(delta.deltaId(), delta.confidenceAfter(),
                    "Planner policy requires confidence >= 0.85 for safety.");
        }
        // Policy changes require evaluation references
        if (delta.evaluationRefs().isEmpty()) {
            return EvaluationResult.rejected(delta.deltaId(), 0.0, "Planner policy requires evaluation references.");
        }
        return EvaluationResult.approved(delta.deltaId(), delta.confidenceAfter(), "Planner policy validation passed.");
    }

    /**
     * Validates model adapter learning deltas.
     */
    @NotNull
    private EvaluationResult validateModelAdapter(@NotNull LearningDelta delta) {
        // Model adapters are offline-only, high-risk
        if (!delta.requiresHumanReview()) {
            return EvaluationResult.rejected(delta.deltaId(), 0.0,
                    "Model adapter requires human review flag (offline-only, high-risk target).");
        }
        if (delta.proposedContent() == null || delta.proposedContent().isEmpty()) {
            return EvaluationResult.rejected(delta.deltaId(), 0.0, "Model adapter delta has no proposed content.");
        }
        // Model adapters require very high confidence
        if (delta.confidenceAfter() < 0.9) {
            return EvaluationResult.rejected(delta.deltaId(), delta.confidenceAfter(),
                    "Model adapter requires confidence >= 0.9 (offline-only, high-risk).");
        }
        // Require comprehensive evaluation
        if (delta.evaluationRefs().isEmpty()) {
            return EvaluationResult.rejected(delta.deltaId(), 0.0, "Model adapter requires evaluation references.");
        }
        return EvaluationResult.approved(delta.deltaId(), delta.confidenceAfter(),
                "Model adapter validation passed (marked for human review due to high-risk nature).");
    }

    /**
     * Validates mastery state learning deltas.
     */
    @NotNull
    private EvaluationResult validateMasteryState(@NotNull LearningDelta delta) {
        if (delta.proposedContent() == null || delta.proposedContent().isEmpty()) {
            return EvaluationResult.rejected(delta.deltaId(), 0.0, "Mastery state delta has no proposed content.");
        }
        // Validate proposed state is valid
        Object stateObj = delta.proposedContent().get("state");
        if (stateObj == null) {
            return EvaluationResult.rejected(delta.deltaId(), 0.0, "Mastery state delta missing 'state' field.");
        }
        try {
            MasteryState.valueOf((String) stateObj);
        } catch (IllegalArgumentException e) {
            return EvaluationResult.rejected(delta.deltaId(), 0.0,
                    String.format("Invalid mastery state: %s", stateObj));
        }
        return EvaluationResult.approved(delta.deltaId(), delta.confidenceAfter(), "Mastery state validation passed.");
    }
}
