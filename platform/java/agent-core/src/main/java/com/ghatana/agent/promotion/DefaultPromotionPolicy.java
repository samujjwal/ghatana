/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.promotion;

import com.ghatana.agent.evaluation.EvaluationResult;
import com.ghatana.agent.evaluation.EvaluationType;
import com.ghatana.agent.learning.LearningDelta;
import com.ghatana.agent.mastery.MasteryState;
import org.jetbrains.annotations.NotNull;

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
 * @doc.type class
 * @doc.purpose Default implementation of PromotionPolicy
 * @doc.layer agent-core
 * @doc.pattern Policy
 */
public final class DefaultPromotionPolicy implements PromotionPolicy {

    @Override
    public boolean canPromote(@NotNull LearningDelta delta, @NotNull EvaluationResult result) {
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
        // Safety test failure → QUARANTINE
        if (hasFailedSafetyTest(result)) {
            return MasteryState.QUARANTINED;
        }

        // Regression test failure → reject (no state change)
        if (hasFailedRegressionTest(result)) {
            return null;
        }

        // All tests passed → MASTERED
        if (result.allPassed()) {
            return MasteryState.MASTERED;
        }

        // Regression and safety passed, some other tests failed → COMPETENT
        if (hasPassedRegressionAndSafety(result)) {
            return MasteryState.COMPETENT;
        }

        // Otherwise → PRACTICED
        return MasteryState.PRACTICED;
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
