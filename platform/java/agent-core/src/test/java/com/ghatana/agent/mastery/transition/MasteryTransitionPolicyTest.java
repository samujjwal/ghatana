/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.mastery.transition;

import com.ghatana.agent.mastery.MasteryState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Tests for DefaultMasteryTransitionPolicy
 * @doc.layer agent-core
 * @doc.pattern Test
 */
@DisplayName("DefaultMasteryTransitionPolicy Tests")
class MasteryTransitionPolicyTest {

    private final DefaultMasteryTransitionPolicy policy = new DefaultMasteryTransitionPolicy();

    @Test
    @DisplayName("UNKNOWN to MASTERED is not allowed")
    void unknownToMasteredNotAllowed() {
        var result = policy.canTransition(
                MasteryState.UNKNOWN,
                MasteryState.MASTERED,
                Map.of()
        );

        assertThat(result.allowed()).isFalse();
        assertThat(result.errorMessage()).isPresent();
    }

    @Test
    @DisplayName("UNKNOWN to OBSERVED requires evidence")
    void unknownToObservedRequiresEvidence() {
        var result = policy.canTransition(
                MasteryState.UNKNOWN,
                MasteryState.OBSERVED,
                Map.of()
        );

        assertThat(result.allowed()).isFalse();
        assertThat(result.errorMessage()).isPresent();
    }

    @Test
    @DisplayName("UNKNOWN to OBSERVED allowed with evidence")
    void unknownToObservedAllowedWithEvidence() {
        var result = policy.canTransition(
                MasteryState.UNKNOWN,
                MasteryState.OBSERVED,
                Map.of("trace_id", "trace-123")
        );

        assertThat(result.allowed()).isTrue();
    }

    @Test
    @DisplayName("PRACTICED to COMPETENT requires procedure and evaluation")
    void practicedToCompetentRequiresProcedureAndEval() {
        var result = policy.canTransition(
                MasteryState.PRACTICED,
                MasteryState.COMPETENT,
                Map.of()
        );

        assertThat(result.allowed()).isFalse();
    }

    @Test
    @DisplayName("PRACTICED to COMPETENT allowed with procedure and passing eval")
    void practicedToCompetentAllowedWithProcedureAndEval() {
        var result = policy.canTransition(
                MasteryState.PRACTICED,
                MasteryState.COMPETENT,
                Map.of(
                        "procedure_id", "proc-123",
                        "basic_eval_passed", "true"
                )
        );

        assertThat(result.allowed()).isTrue();
    }

    @Test
    @DisplayName("COMPETENT to MASTERED requires comprehensive evaluation")
    void competentToMasteredRequiresComprehensiveEval() {
        var result = policy.canTransition(
                MasteryState.COMPETENT,
                MasteryState.MASTERED,
                Map.of()
        );

        assertThat(result.allowed()).isFalse();
    }

    @Test
    @DisplayName("COMPETENT to MASTERED allowed with all tests passing")
    void competentToMasteredAllowedWithAllTestsPassing() {
        var result = policy.canTransition(
                MasteryState.COMPETENT,
                MasteryState.MASTERED,
                Map.of(
                        "regression_passed", "true",
                        "safety_passed", "true",
                        "recovery_passed", "true",
                        "compatibility_passed", "true"
                )
        );

        assertThat(result.allowed()).isTrue();
    }

    @Test
    @DisplayName("QUARANTINED can be reached from any state with safety violation")
    void quarantinedFromAnyStateWithSafetyViolation() {
        var result = policy.canTransition(
                MasteryState.MASTERED,
                MasteryState.QUARANTINED,
                Map.of("safety_violation", "unsafe action detected")
        );

        assertThat(result.allowed()).isTrue();
    }
}
