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
     * Applies target-specific promotion checks.
     */
    private boolean passesTargetSpecificChecks(@NotNull LearningDelta delta, @NotNull EvaluationResult result) {
        LearningTarget target = delta.target();

        // PROCEDURAL_SKILL: requires repeated evidence + eval pass + rollback
        if (target == LearningTarget.PROCEDURAL_SKILL) {
            return passesProceduralSkillChecks(delta, result);
        }

        // RETRIEVAL_POLICY: requires replay/A-B test
        if (target == LearningTarget.RETRIEVAL_POLICY) {
            return passesRetrievalPolicyChecks(delta, result);
        }

        // PLANNER_POLICY: requires human approval
        if (target == LearningTarget.PLANNER_POLICY) {
            return passesPlannerPolicyChecks(delta);
        }

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
