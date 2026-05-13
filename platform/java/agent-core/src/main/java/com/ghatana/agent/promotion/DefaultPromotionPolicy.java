/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.promotion;

import com.ghatana.agent.evaluation.EvaluationResult;
import com.ghatana.agent.evaluation.EvaluationType;
import com.ghatana.agent.learning.LearningDelta;
import com.ghatana.agent.learning.LearningTarget;
import com.ghatana.agent.mastery.MasteryState;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Default implementation of PromotionPolicy.
 *
 * <p>Promotion rules:
 * <ul>
 *   <li>All tests must pass for promotion to MASTERED</li>
 *   <li>Regression and safety tests must pass for COMPETENT</li>
 *   <li>Failed safety tests trigger QUARANTINE</li>
 *   <li>Failed regression tests trigger rejection</li>
 * </ul>
 *
 * <p>Target-specific checks:
 * <ul>
 *   <li>PROCEDURAL_SKILL: requires repeated evidence + eval pass + rollback</li>
 *   <li>RETRIEVAL_POLICY: requires replay/A-B test</li>
 *   <li>PLANNER_POLICY: requires human approval</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Default implementation of PromotionPolicy
 * @doc.layer agent-core
 * @doc.pattern Policy
 */
public final class DefaultPromotionPolicy implements PromotionPolicy {

    @Override
    public boolean canPromote(@NotNull LearningDelta delta, @NotNull EvaluationResult result) {
        // Apply target-specific checks
        if (!passesTargetSpecificChecks(delta, result)) {
            return false;
        }

        // Safety test failures always block promotion
        if (hasFailedSafetyTest(result)) {
            return false;
        }

        // Regression test failures block promotion
        if (hasFailedRegressionTest(result)) {
            return false;
        }

        // Overall pass rate must be at least 80%
        if (result.passRate() < 80.0) {
            return false;
        }

        return true;
    }

    @Override
    @NotNull
    public MasteryState targetState(@NotNull LearningDelta delta, @NotNull EvaluationResult result) {
        LearningTarget target = delta.target();

        // NEGATIVE_KNOWLEDGE: Always promote to MASTERED if safety passes (avoid failure modes)
        if (target == LearningTarget.NEGATIVE_KNOWLEDGE) {
            if (hasFailedSafetyTest(result)) {
                return MasteryState.QUARANTINED;
            }
            // Negative knowledge should be immediately available to avoid failures
            return MasteryState.MASTERED;
        }

        // SEMANTIC_FACT: Promote to MASTERED if consistency passes
        if (target == LearningTarget.SEMANTIC_FACT) {
            if (hasFailedSafetyTest(result)) {
                return MasteryState.QUARANTINED;
            }
            if (result.allPassed()) {
                return MasteryState.MASTERED;
            }
            return MasteryState.COMPETENT;
        }

        // PROCEDURAL_SKILL: Requires evidence-based progression
        if (target == LearningTarget.PROCEDURAL_SKILL) {
            return determineProceduralSkillTargetState(delta, result);
        }

        // Policy targets: Require human approval for higher states
        if (target.isPolicyTarget()) {
            return determinePolicyTargetState(delta, result);
        }

        // Safety test failure → QUARANTINE only when safety evidence actually exists
        if (hasFailedSafetyTest(result)) {
            if (!hasSafetyTestCase(result)) {
                // Cannot quarantine without safety evidence — insufficient basis
                return MasteryState.PRACTICED;
            }
            return MasteryState.QUARANTINED;
        }

        // Regression test failure → reject (no state change)
        if (hasFailedRegressionTest(result)) {
            return null;
        }

        // All tests passed → MASTERED only when the delta carries eval refs
        if (result.allPassed()) {
            if (delta.evaluationRefs() == null || delta.evaluationRefs().isEmpty()) {
                // Cannot promote to MASTERED without evaluation refs
                return MasteryState.COMPETENT;
            }
            return MasteryState.MASTERED;
        }

        // Regression and safety passed, some other tests failed → COMPETENT
        if (hasPassedRegressionAndSafety(result)) {
            return MasteryState.COMPETENT;
        }

        // Otherwise → PRACTICED
        return MasteryState.PRACTICED;
    }

    /**
     * Determines the target state for PROCEDURAL_SKILL based on evaluation results.
     * Evidence-based progression: OBSERVED → PRACTICED → COMPETENT → MASTERED
     */
    @NotNull
    private MasteryState determineProceduralSkillTargetState(@NotNull LearningDelta delta, @NotNull EvaluationResult result) {
        // Safety test failure → QUARANTINED
        if (hasFailedSafetyTest(result)) {
            return MasteryState.QUARANTINED;
        }

        // Regression test failure → reject
        if (hasFailedRegressionTest(result)) {
            return null;
        }

        // All tests passed with eval refs → MASTERED
        if (result.allPassed()) {
            if (delta.evaluationRefs() == null || delta.evaluationRefs().isEmpty()) {
                return MasteryState.COMPETENT;
            }
            return MasteryState.MASTERED;
        }

        // Regression and safety passed, other tests failed → COMPETENT
        if (hasPassedRegressionAndSafety(result)) {
            return MasteryState.COMPETENT;
        }

        return MasteryState.PRACTICED;
    }

    /**
     * Determines the target state for policy targets based on evidence and approval.
     * Policy targets require human approval for MASTERED state.
     */
    @NotNull
    private MasteryState determinePolicyTargetState(@NotNull LearningDelta delta, @NotNull EvaluationResult result) {
        // Safety test failure → QUARANTINED
        if (hasFailedSafetyTest(result)) {
            return MasteryState.QUARANTINED;
        }

        // Regression test failure → reject
        if (hasFailedRegressionTest(result)) {
            return null;
        }

        // All tests passed with human approval → MASTERED
        if (result.allPassed() && delta.requiresHumanReview()) {
            return MasteryState.MASTERED;
        }

        // All tests passed without human approval → COMPETENT
        if (result.allPassed()) {
            return MasteryState.COMPETENT;
        }

        // Regression and safety passed → COMPETENT
        if (hasPassedRegressionAndSafety(result)) {
            return MasteryState.COMPETENT;
        }

        return MasteryState.PRACTICED;
    }

    /**
     * Applies target-specific promotion checks.
     */
    private boolean passesTargetSpecificChecks(@NotNull LearningDelta delta, @NotNull EvaluationResult result) {
        LearningTarget target = delta.target();

        // PROCEDURAL_SKILL: requires repeated evidence + eval pass + rollback
        if (target == LearningTarget.PROCEDURAL_SKILL) {
            return passesProceduralSkillChecks(delta, result);
        }

        // NEGATIVE_KNOWLEDGE: requires failure mode documentation + safety validation
        if (target == LearningTarget.NEGATIVE_KNOWLEDGE) {
            return passesNegativeKnowledgeChecks(delta, result);
        }

        // SEMANTIC_FACT: requires source validation + consistency check
        if (target == LearningTarget.SEMANTIC_FACT) {
            return passesSemanticFactChecks(delta, result);
        }

        // RETRIEVAL_POLICY: requires replay/A-B test
        if (target == LearningTarget.RETRIEVAL_POLICY) {
            return passesRetrievalPolicyChecks(delta, result);
        }

        // CONFIDENCE_THRESHOLD: requires calibration test
        if (target == LearningTarget.CONFIDENCE_THRESHOLD) {
            return passesConfidenceThresholdChecks(delta, result);
        }

        // ROUTING_POLICY: requires routing validation test
        if (target == LearningTarget.ROUTING_POLICY) {
            return passesRoutingPolicyChecks(delta, result);
        }

        // PROMPT_TEMPLATE: requires prompt effectiveness test
        if (target == LearningTarget.PROMPT_TEMPLATE) {
            return passesPromptTemplateChecks(delta, result);
        }

        // PLANNER_POLICY: requires human approval
        if (target == LearningTarget.PLANNER_POLICY) {
            return passesPlannerPolicyChecks(delta);
        }

        // EPISODIC_MEMORY: no special requirements
        // MODEL_ADAPTER: requires offline-only validation
        // MASTERY_STATE: requires governance validation
        return true;
    }

    /**
     * Checks if PROCEDURAL_SKILL promotion requirements are met.
     * Requires: repeated evidence + regression/safety pass + rollback.
     */
    private boolean passesProceduralSkillChecks(@NotNull LearningDelta delta, @NotNull EvaluationResult result) {
        // Check for repeated evidence (at least 3 occurrences)
        if (delta.evidenceRefs().size() < 3) {
            return false;
        }

        // Check regression and safety pass (not all tests)
        if (!hasPassedRegressionAndSafety(result)) {
            return false;
        }

        // Check rollback capability
        if (delta.rollbackRef() == null || delta.rollbackRef().isBlank()) {
            return false;
        }

        return true;
    }

    /**
     * Checks if RETRIEVAL_POLICY promotion requirements are met.
     * Requires: replay/A-B test.
     */
    private boolean passesRetrievalPolicyChecks(@NotNull LearningDelta delta, @NotNull EvaluationResult result) {
        // Check for replay/A-B test result
        boolean hasReplayTest = result.caseResults().stream()
                .anyMatch(cr -> cr.name().toLowerCase().contains("replay") 
                        || cr.name().toLowerCase().contains("a-b"));

        if (!hasReplayTest) {
            return false;
        }

        // Replay/A-B test must pass
        boolean replayPassed = result.caseResults().stream()
                .filter(cr -> cr.name().toLowerCase().contains("replay") 
                        || cr.name().toLowerCase().contains("a-b"))
                .allMatch(cr -> cr.passed());

        return replayPassed;
    }

    /**
     * Checks if PLANNER_POLICY promotion requirements are met.
     * Requires: human approval.
     */
    private boolean passesPlannerPolicyChecks(@NotNull LearningDelta delta) {
        // Check for human approval
        return delta.requiresHumanReview();
    }

    /**
     * Checks if NEGATIVE_KNOWLEDGE promotion requirements are met.
     * Requires: failure mode documentation + safety validation.
     */
    private boolean passesNegativeKnowledgeChecks(@NotNull LearningDelta delta, @NotNull EvaluationResult result) {
        // Check for negative knowledge ID
        if (delta.negativeKnowledgeId() == null || delta.negativeKnowledgeId().isBlank()) {
            return false;
        }

        // Check for failure mode documentation in labels
        if (!delta.labels().containsKey("failureMode")) {
            return false;
        }

        // Safety test must pass for negative knowledge
        if (!hasSafetyTestCase(result)) {
            return false;
        }

        boolean safetyPassed = result.caseResults().stream()
                .filter(cr -> cr.name().toLowerCase().contains("safety"))
                .allMatch(cr -> cr.passed());

        return safetyPassed;
    }

    /**
     * Checks if SEMANTIC_FACT promotion requirements are met.
     * Requires: source validation + consistency check.
     */
    private boolean passesSemanticFactChecks(@NotNull LearningDelta delta, @NotNull EvaluationResult result) {
        // Check for semantic fact ID
        if (delta.semanticFactId() == null || delta.semanticFactId().isBlank()) {
            return false;
        }

        // Check for source validation
        if (!delta.labels().containsKey("source")) {
            return false;
        }

        // Consistency test must pass
        boolean hasConsistencyTest = result.caseResults().stream()
                .anyMatch(cr -> cr.name().toLowerCase().contains("consistency"));

        if (!hasConsistencyTest) {
            return false;
        }

        boolean consistencyPassed = result.caseResults().stream()
                .filter(cr -> cr.name().toLowerCase().contains("consistency"))
                .allMatch(cr -> cr.passed());

        return consistencyPassed;
    }

    /**
     * Checks if CONFIDENCE_THRESHOLD promotion requirements are met.
     * Requires: calibration test.
     */
    private boolean passesConfidenceThresholdChecks(@NotNull LearningDelta delta, @NotNull EvaluationResult result) {
        // Calibration test must exist and pass
        boolean hasCalibrationTest = result.caseResults().stream()
                .anyMatch(cr -> cr.name().toLowerCase().contains("calibration"));

        if (!hasCalibrationTest) {
            return false;
        }

        boolean calibrationPassed = result.caseResults().stream()
                .filter(cr -> cr.name().toLowerCase().contains("calibration"))
                .allMatch(cr -> cr.passed());

        return calibrationPassed;
    }

    /**
     * Checks if ROUTING_POLICY promotion requirements are met.
     * Requires: routing validation test.
     */
    private boolean passesRoutingPolicyChecks(@NotNull LearningDelta delta, @NotNull EvaluationResult result) {
        // Routing validation test must exist and pass
        boolean hasRoutingTest = result.caseResults().stream()
                .anyMatch(cr -> cr.name().toLowerCase().contains("routing"));

        if (!hasRoutingTest) {
            return false;
        }

        boolean routingPassed = result.caseResults().stream()
                .filter(cr -> cr.name().toLowerCase().contains("routing"))
                .allMatch(cr -> cr.passed());

        return routingPassed;
    }

    /**
     * Checks if PROMPT_TEMPLATE promotion requirements are met.
     * Requires: prompt effectiveness test.
     */
    private boolean passesPromptTemplateChecks(@NotNull LearningDelta delta, @NotNull EvaluationResult result) {
        // Prompt effectiveness test must exist and pass
        boolean hasEffectivenessTest = result.caseResults().stream()
                .anyMatch(cr -> cr.name().toLowerCase().contains("effectiveness"));

        if (!hasEffectivenessTest) {
            return false;
        }

        boolean effectivenessPassed = result.caseResults().stream()
                .filter(cr -> cr.name().toLowerCase().contains("effectiveness"))
                .allMatch(cr -> cr.passed());

        return effectivenessPassed;
    }

    private boolean hasSafetyTestCase(@NotNull EvaluationResult result) {
        return result.caseResults().stream()
                .anyMatch(cr -> cr.name().toLowerCase().contains("safety"));
    }

    private boolean hasFailedSafetyTest(@NotNull EvaluationResult result) {
        return result.caseResults().stream()
                .filter(cr -> cr.name().toLowerCase().contains("safety"))
                .anyMatch(cr -> !cr.passed());
    }

    private boolean hasFailedRegressionTest(@NotNull EvaluationResult result) {
        return result.caseResults().stream()
                .filter(cr -> cr.name().toLowerCase().contains("regression"))
                .anyMatch(cr -> !cr.passed());
    }

    private boolean hasPassedRegressionAndSafety(@NotNull EvaluationResult result) {
        boolean regressionPassed = result.caseResults().stream()
                .filter(cr -> cr.name().toLowerCase().contains("regression"))
                .allMatch(cr -> cr.passed());

        boolean safetyPassed = result.caseResults().stream()
                .filter(cr -> cr.name().toLowerCase().contains("safety"))
                .allMatch(cr -> cr.passed());

        return regressionPassed && safetyPassed;
    }
}
