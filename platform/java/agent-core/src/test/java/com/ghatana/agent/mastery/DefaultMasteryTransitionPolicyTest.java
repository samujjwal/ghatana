/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.mastery;

import com.ghatana.agent.mastery.transition.DefaultMasteryTransitionPolicy;
import com.ghatana.agent.mastery.transition.MasteryTransitionPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DefaultMasteryTransitionPolicy.
 *
 * @doc.type class
 * @doc.purpose Tests for mastery transition policy
 * @doc.layer agent-core
 * @doc.pattern Test
 */
@DisplayName("DefaultMasteryTransitionPolicy Tests")
class DefaultMasteryTransitionPolicyTest {

    private final MasteryTransitionPolicy policy = new DefaultMasteryTransitionPolicy();

    @Nested
    @DisplayName("Valid transitions with evidence")
    class ValidTransitionTests {

        @Test
        @DisplayName("UNKNOWN → OBSERVED is valid with trace evidence")
        void unknownToObservedIsValidWithEvidence() {
            var result = policy.canTransition(
                    MasteryState.UNKNOWN,
                    MasteryState.OBSERVED,
                    Map.of("trace", "episode-123")
            );
            assertTrue(result.allowed());
        }

        @Test
        @DisplayName("UNKNOWN → OBSERVED is invalid without evidence")
        void unknownToObservedInvalidWithoutEvidence() {
            var result = policy.canTransition(
                    MasteryState.UNKNOWN,
                    MasteryState.OBSERVED,
                    Map.of()
            );
            assertFalse(result.allowed());
            assertTrue(result.errorMessage().isPresent());
        }

        @Test
        @DisplayName("OBSERVED → PRACTICED is valid with episodes")
        void observedToPracticedIsValidWithEpisodes() {
            var result = policy.canTransition(
                    MasteryState.OBSERVED,
                    MasteryState.PRACTICED,
                    Map.of("episodes", "10")
            );
            assertTrue(result.allowed());
        }

        @Test
        @DisplayName("PRACTICED → COMPETENT is valid with procedure and eval")
        void practicedToCompetentIsValidWithProcedureAndEval() {
            var result = policy.canTransition(
                    MasteryState.PRACTICED,
                    MasteryState.COMPETENT,
                    Map.of("procedure_id", "proc-123", "basic_eval_passed", "true")
            );
            assertTrue(result.allowed());
        }

        @Test
        @DisplayName("COMPETENT → MASTERED is valid with full eval suite")
        void competentToMasteredIsValidWithFullEval() {
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
            assertTrue(result.allowed());
        }

        @Test
        @DisplayName("MASTERED → MAINTENANCE_ONLY is valid with new version")
        void masteredToMaintenanceOnlyIsValidWithNewVersion() {
            var result = policy.canTransition(
                    MasteryState.MASTERED,
                    MasteryState.MAINTENANCE_ONLY,
                    Map.of("new_active_version_id", "v2.0.0")
            );
            assertTrue(result.allowed());
        }

        @Test
        @DisplayName("MAINTENANCE_ONLY → OBSOLETE is valid with end-of-life")
        void maintenanceOnlyToObsoleteIsValidWithEOL() {
            var result = policy.canTransition(
                    MasteryState.MAINTENANCE_ONLY,
                    MasteryState.OBSOLETE,
                    Map.of("end_of_life", "true")
            );
            assertTrue(result.allowed());
        }

        @Test
        @DisplayName("Any state → QUARANTINED is valid with safety violation")
        void anyStateToQuarantinedIsValidWithSafetyViolation() {
            var result = policy.canTransition(
                    MasteryState.MASTERED,
                    MasteryState.QUARANTINED,
                    Map.of("safety_violation", "buffer_overflow")
            );
            assertTrue(result.allowed());
        }

        @Test
        @DisplayName("OBSOLETE → RETIRED is valid with no use case")
        void obsoleteToRetiredIsValidWithNoUseCase() {
            var result = policy.canTransition(
                    MasteryState.OBSOLETE,
                    MasteryState.RETIRED,
                    Map.of("no_active_use_case", "true")
            );
            assertTrue(result.allowed());
        }

        @Test
        @DisplayName("QUARANTINED → OBSERVED is valid with human review")
        void quarantinedToObservedIsValidWithHumanReview() {
            var result = policy.canTransition(
                    MasteryState.QUARANTINED,
                    MasteryState.OBSERVED,
                    Map.of("human_review_approved", "true")
            );
            assertTrue(result.allowed());
        }
    }

    @Nested
    @DisplayName("Invalid transitions")
    class InvalidTransitionTests {

        @Test
        @DisplayName("UNKNOWN → MASTERED is invalid (skip levels)")
        void unknownToMasteredIsInvalid() {
            var result = policy.canTransition(
                    MasteryState.UNKNOWN,
                    MasteryState.MASTERED,
                    Map.of("evidence", "some")
            );
            assertFalse(result.allowed());
            assertTrue(result.errorMessage().isPresent());
        }

        @Test
        @DisplayName("OBSERVED → MASTERED is invalid (skip levels)")
        void observedToMasteredIsInvalid() {
            var result = policy.canTransition(
                    MasteryState.OBSERVED,
                    MasteryState.MASTERED,
                    Map.of("evidence", "some")
            );
            assertFalse(result.allowed());
        }

        @Test
        @DisplayName("MASTERED → OBSOLETE is invalid (must go through MAINTENANCE_ONLY)")
        void masteredToObsoleteIsInvalid() {
            var result = policy.canTransition(
                    MasteryState.MASTERED,
                    MasteryState.OBSOLETE,
                    Map.of("contradiction", "true")
            );
            assertFalse(result.allowed());
        }

        @Test
        @DisplayName("OBSOLETE → MASTERED is invalid (cannot revive obsolete)")
        void obsoleteToMasteredIsInvalid() {
            var result = policy.canTransition(
                    MasteryState.OBSOLETE,
                    MasteryState.MASTERED,
                    Map.of("evidence", "some")
            );
            assertFalse(result.allowed());
        }

        @Test
        @DisplayName("RETIRED → any state is invalid (terminal)")
        void retiredToAnyStateIsInvalid() {
            var result1 = policy.canTransition(MasteryState.RETIRED, MasteryState.UNKNOWN, Map.of());
            var result2 = policy.canTransition(MasteryState.RETIRED, MasteryState.OBSERVED, Map.of());
            var result3 = policy.canTransition(MasteryState.RETIRED, MasteryState.MASTERED, Map.of());
            assertFalse(result1.allowed());
            assertFalse(result2.allowed());
            assertFalse(result3.allowed());
        }

        @Test
        @DisplayName("QUARANTINED → OBSERVED is invalid without human review")
        void quarantinedToObservedInvalidWithoutReview() {
            var result = policy.canTransition(
                    MasteryState.QUARANTINED,
                    MasteryState.OBSERVED,
                    Map.of("evidence", "some")
            );
            assertFalse(result.allowed());
            assertTrue(result.errorMessage().isPresent());
        }

        @Test
        @DisplayName("QUARANTINED → MASTERED is invalid (wrong target state)")
        void quarantinedToMasteredInvalid() {
            var result = policy.canTransition(
                    MasteryState.QUARANTINED,
                    MasteryState.MASTERED,
                    Map.of("human_review_approved", "true")
            );
            assertFalse(result.allowed());
        }
    }

    @Nested
    @DisplayName("Evidence requirements enforcement")
    class EvidenceRequirementTests {

        @Test
        @DisplayName("OBSERVED → PRACTICED requires episodes or sandbox")
        void observedToPracticedRequiresEpisodesOrSandbox() {
            var result1 = policy.canTransition(
                    MasteryState.OBSERVED,
                    MasteryState.PRACTICED,
                    Map.of("evidence", "some")
            );
            assertFalse(result1.allowed());

            var result2 = policy.canTransition(
                    MasteryState.OBSERVED,
                    MasteryState.PRACTICED,
                    Map.of("episodes", "10")
            );
            assertTrue(result2.allowed());
        }

        @Test
        @DisplayName("PRACTICED → COMPETENT requires procedure and eval")
        void practicedToCompetentRequiresProcedureAndEval() {
            var result1 = policy.canTransition(
                    MasteryState.PRACTICED,
                    MasteryState.COMPETENT,
                    Map.of("procedure_id", "proc-123")
            );
            assertFalse(result1.allowed());

            var result2 = policy.canTransition(
                    MasteryState.PRACTICED,
                    MasteryState.COMPETENT,
                    Map.of("procedure_id", "proc-123", "basic_eval_passed", "false")
            );
            assertFalse(result2.allowed());
        }

        @Test
        @DisplayName("COMPETENT → MASTERED requires full eval suite")
        void competentToMasteredRequiresFullEvalSuite() {
            var result1 = policy.canTransition(
                    MasteryState.COMPETENT,
                    MasteryState.MASTERED,
                    Map.of("regression_passed", "true", "safety_passed", "true")
            );
            assertFalse(result1.allowed());
        }
    }

    @Nested
    @DisplayName("Terminal states")
    class TerminalStateTests {

        @Test
        @DisplayName("RETIRED is terminal")
        void retiredIsTerminal() {
            assertTrue(MasteryState.RETIRED.isTerminal());
        }

        @Test
        @DisplayName("OBSOLETE is terminal")
        void obsoleteIsTerminal() {
            assertTrue(MasteryState.OBSOLETE.isTerminal());
        }

        @Test
        @DisplayName("QUARANTINED is terminal")
        void quarantinedIsTerminal() {
            assertTrue(MasteryState.QUARANTINED.isTerminal());
        }
    }
}
