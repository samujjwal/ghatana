/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.agent.assurance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for WP7: Assurance and release governance — PromotionGate,
 * EvaluationResult, EvaluationPackRef.
 */
@DisplayName("Assurance & Promotion (WP7) [GH-90000]")
class PromotionGateTest {

    // =========================================================================
    // EvaluationResult
    // =========================================================================

    @Nested
    @DisplayName("EvaluationResult [GH-90000]")
    class EvaluationResultTests {

        @Test
        @DisplayName("should construct with all fields [GH-90000]")
        void shouldConstructWithAllFields() { // GH-90000
            EvaluationResult result = new EvaluationResult( // GH-90000
                    "run-1", "eval.safety.v1", "2.1.0",
                    0.95, 100, 95, 5,
                    true,
                    Map.of("accuracy", 0.96, "latency", 0.90), // GH-90000
                    List.of("scenario-42: timeout [GH-90000]"),
                    Instant.now(), 12345L); // GH-90000

            assertThat(result.passRate()).isEqualTo(0.95); // GH-90000
            assertThat(result.promotionEligible()).isTrue(); // GH-90000
            assertThat(result.dimensionScores()).hasSize(2); // GH-90000
            assertThat(result.failureSummaries()).hasSize(1); // GH-90000
        }

        @Test
        @DisplayName("collections should default to empty when null [GH-90000]")
        void collectionsShouldDefaultToEmpty() { // GH-90000
            EvaluationResult result = new EvaluationResult( // GH-90000
                    "run-1", "eval.v1", "1.0",
                    1.0, 10, 10, 0,
                    true, null, null,
                    Instant.now(), 0L); // GH-90000

            assertThat(result.dimensionScores()).isEmpty(); // GH-90000
            assertThat(result.failureSummaries()).isEmpty(); // GH-90000
        }
    }

    // =========================================================================
    // EvaluationPackRef
    // =========================================================================

    @Nested
    @DisplayName("EvaluationPackRef [GH-90000]")
    class EvaluationPackRefTests {

        @Test
        @DisplayName("should construct with required fields [GH-90000]")
        void shouldConstructWithRequiredFields() { // GH-90000
            EvaluationPackRef ref = new EvaluationPackRef( // GH-90000
                    "eval.procurement.regression.v1",
                    "1.0.0",
                    "agent.procurement-assistant",
                    "2.0.0",
                    List.of("regression", "safety"), // GH-90000
                    50,
                    0.90,
                    Instant.now()); // GH-90000

            assertThat(ref.packId()).isEqualTo("eval.procurement.regression.v1 [GH-90000]");
            assertThat(ref.requiredPassRate()).isEqualTo(0.90); // GH-90000
            assertThat(ref.categories()).hasSize(2); // GH-90000
            assertThat(ref.scenarioCount()).isEqualTo(50); // GH-90000
        }

        @Test
        @DisplayName("should reject invalid pass rate [GH-90000]")
        void shouldRejectInvalidPassRate() { // GH-90000
            assertThatThrownBy(() -> new EvaluationPackRef( // GH-90000
                    "eval.v1", "1.0", "agent-1", "1.0",
                    List.of(), 10, 1.5, Instant.now())) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }
    }

    // =========================================================================
    // PromotionGate
    // =========================================================================

    @Nested
    @DisplayName("PromotionGate evaluation [GH-90000]")
    class PromotionGateEvaluationTests {

        @Test
        @DisplayName("should approve when all required packs pass above threshold [GH-90000]")
        void shouldApproveWhenAllPacksPass() { // GH-90000
            PromotionGate gate = new PromotionGate( // GH-90000
                    "gate-prod", "production",
                    List.of("eval.safety.v1", "eval.regression.v1"), // GH-90000
                    0.90, false, Duration.ofHours(24), 5.0); // GH-90000

            List<EvaluationResult> results = List.of( // GH-90000
                    new EvaluationResult("r1", "eval.safety.v1", "2.0", // GH-90000
                            0.95, 100, 95, 5, true, Map.of(), List.of(), // GH-90000
                            Instant.now(), 1000L), // GH-90000
                    new EvaluationResult("r2", "eval.regression.v1", "2.0", // GH-90000
                            0.92, 50, 46, 4, true, Map.of(), List.of(), // GH-90000
                            Instant.now(), 2000L)); // GH-90000

            PromotionGate.PromotionDecision decision = gate.evaluate(results); // GH-90000

            assertThat(decision.approved()).isTrue(); // GH-90000
            assertThat(decision.reason()).contains("All checks passed [GH-90000]");
        }

        @Test
        @DisplayName("should reject when required pack is missing [GH-90000]")
        void shouldRejectWhenPackMissing() { // GH-90000
            PromotionGate gate = new PromotionGate( // GH-90000
                    "gate-prod", "production",
                    List.of("eval.safety.v1", "eval.regression.v1"), // GH-90000
                    0.90, false, Duration.ofHours(24), 5.0); // GH-90000

            // Only providing one of the two required packs
            List<EvaluationResult> results = List.of( // GH-90000
                    new EvaluationResult("r1", "eval.safety.v1", "2.0", // GH-90000
                            0.95, 100, 95, 5, true, Map.of(), List.of(), // GH-90000
                            Instant.now(), 1000L)); // GH-90000

            PromotionGate.PromotionDecision decision = gate.evaluate(results); // GH-90000

            assertThat(decision.approved()).isFalse(); // GH-90000
            assertThat(decision.reason()).contains("Missing required evaluation pack [GH-90000]");
        }

        @Test
        @DisplayName("should reject when pass rate is below threshold [GH-90000]")
        void shouldRejectWhenPassRateBelowThreshold() { // GH-90000
            PromotionGate gate = new PromotionGate( // GH-90000
                    "gate-prod", "production",
                    List.of("eval.safety.v1 [GH-90000]"),
                    0.90, false, Duration.ofHours(24), 5.0); // GH-90000

            List<EvaluationResult> results = List.of( // GH-90000
                    new EvaluationResult("r1", "eval.safety.v1", "2.0", // GH-90000
                            0.75, 100, 75, 25, false, Map.of(), List.of(), // GH-90000
                            Instant.now(), 1000L)); // GH-90000

            PromotionGate.PromotionDecision decision = gate.evaluate(results); // GH-90000

            assertThat(decision.approved()).isFalse(); // GH-90000
            assertThat(decision.reason()).contains("pass rate [GH-90000]");
        }

        @Test
        @DisplayName("should block when human signoff is required [GH-90000]")
        void shouldBlockWhenHumanSignoffRequired() { // GH-90000
            PromotionGate gate = new PromotionGate( // GH-90000
                    "gate-prod", "production",
                    List.of("eval.safety.v1 [GH-90000]"),
                    0.90, true, // requiresHumanSignoff
                    Duration.ofHours(24), 5.0); // GH-90000

            List<EvaluationResult> results = List.of( // GH-90000
                    new EvaluationResult("r1", "eval.safety.v1", "2.0", // GH-90000
                            0.98, 100, 98, 2, true, Map.of(), List.of(), // GH-90000
                            Instant.now(), 1000L)); // GH-90000

            PromotionGate.PromotionDecision decision = gate.evaluate(results); // GH-90000

            assertThat(decision.approved()).isFalse(); // GH-90000
            assertThat(decision.reason()).contains("human sign-off [GH-90000]");
        }

        @Test
        @DisplayName("should reject invalid minimumPassRate out of range [GH-90000]")
        void shouldRejectInvalidPassRate() { // GH-90000
            assertThatThrownBy(() -> new PromotionGate( // GH-90000
                    "gate-1", "staging", List.of(), 1.5, false, // GH-90000
                    Duration.ofHours(1), 5.0)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("minimumPassRate [GH-90000]");
        }

        @Test
        @DisplayName("should approve with empty required pack list [GH-90000]")
        void shouldApproveWithEmptyRequiredPacks() { // GH-90000
            PromotionGate gate = new PromotionGate( // GH-90000
                    "gate-dev", "development",
                    List.of(), 0.0, false, // GH-90000
                    Duration.ZERO, 0.0);

            PromotionGate.PromotionDecision decision = gate.evaluate(List.of()); // GH-90000

            assertThat(decision.approved()).isTrue(); // GH-90000
        }
    }
}
