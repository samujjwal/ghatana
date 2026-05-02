/*
 * Copyright (c) 2026 Ghatana Inc. 
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
@DisplayName("Assurance & Promotion (WP7)")
class PromotionGateTest {

    // =========================================================================
    // EvaluationResult
    // =========================================================================

    @Nested
    @DisplayName("EvaluationResult")
    class EvaluationResultTests {

        @Test
        @DisplayName("should construct with all fields")
        void shouldConstructWithAllFields() { 
            EvaluationResult result = new EvaluationResult( 
                    "run-1", "eval.safety.v1", "2.1.0",
                    0.95, 100, 95, 5,
                    true,
                    Map.of("accuracy", 0.96, "latency", 0.90), 
                    List.of("scenario-42: timeout"),
                    Instant.now(), 12345L); 

            assertThat(result.passRate()).isEqualTo(0.95); 
            assertThat(result.promotionEligible()).isTrue(); 
            assertThat(result.dimensionScores()).hasSize(2); 
            assertThat(result.failureSummaries()).hasSize(1); 
        }

        @Test
        @DisplayName("collections should default to empty when null")
        void collectionsShouldDefaultToEmpty() { 
            EvaluationResult result = new EvaluationResult( 
                    "run-1", "eval.v1", "1.0",
                    1.0, 10, 10, 0,
                    true, null, null,
                    Instant.now(), 0L); 

            assertThat(result.dimensionScores()).isEmpty(); 
            assertThat(result.failureSummaries()).isEmpty(); 
        }
    }

    // =========================================================================
    // EvaluationPackRef
    // =========================================================================

    @Nested
    @DisplayName("EvaluationPackRef")
    class EvaluationPackRefTests {

        @Test
        @DisplayName("should construct with required fields")
        void shouldConstructWithRequiredFields() { 
            EvaluationPackRef ref = new EvaluationPackRef( 
                    "eval.procurement.regression.v1",
                    "1.0.0",
                    "agent.procurement-assistant",
                    "2.0.0",
                    List.of("regression", "safety"), 
                    50,
                    0.90,
                    Instant.now()); 

            assertThat(ref.packId()).isEqualTo("eval.procurement.regression.v1");
            assertThat(ref.requiredPassRate()).isEqualTo(0.90); 
            assertThat(ref.categories()).hasSize(2); 
            assertThat(ref.scenarioCount()).isEqualTo(50); 
        }

        @Test
        @DisplayName("should reject invalid pass rate")
        void shouldRejectInvalidPassRate() { 
            assertThatThrownBy(() -> new EvaluationPackRef( 
                    "eval.v1", "1.0", "agent-1", "1.0",
                    List.of(), 10, 1.5, Instant.now())) 
                    .isInstanceOf(IllegalArgumentException.class); 
        }
    }

    // =========================================================================
    // PromotionGate
    // =========================================================================

    @Nested
    @DisplayName("PromotionGate evaluation")
    class PromotionGateEvaluationTests {

        @Test
        @DisplayName("should approve when all required packs pass above threshold")
        void shouldApproveWhenAllPacksPass() { 
            PromotionGate gate = new PromotionGate( 
                    "gate-prod", "production",
                    List.of("eval.safety.v1", "eval.regression.v1"), 
                    0.90, false, Duration.ofHours(24), 5.0); 

            List<EvaluationResult> results = List.of( 
                    new EvaluationResult("r1", "eval.safety.v1", "2.0", 
                            0.95, 100, 95, 5, true, Map.of(), List.of(), 
                            Instant.now(), 1000L), 
                    new EvaluationResult("r2", "eval.regression.v1", "2.0", 
                            0.92, 50, 46, 4, true, Map.of(), List.of(), 
                            Instant.now(), 2000L)); 

            PromotionGate.PromotionDecision decision = gate.evaluate(results); 

            assertThat(decision.approved()).isTrue(); 
            assertThat(decision.reason()).contains("All checks passed");
        }

        @Test
        @DisplayName("should reject when required pack is missing")
        void shouldRejectWhenPackMissing() { 
            PromotionGate gate = new PromotionGate( 
                    "gate-prod", "production",
                    List.of("eval.safety.v1", "eval.regression.v1"), 
                    0.90, false, Duration.ofHours(24), 5.0); 

            // Only providing one of the two required packs
            List<EvaluationResult> results = List.of( 
                    new EvaluationResult("r1", "eval.safety.v1", "2.0", 
                            0.95, 100, 95, 5, true, Map.of(), List.of(), 
                            Instant.now(), 1000L)); 

            PromotionGate.PromotionDecision decision = gate.evaluate(results); 

            assertThat(decision.approved()).isFalse(); 
            assertThat(decision.reason()).contains("Missing required evaluation pack");
        }

        @Test
        @DisplayName("should reject when pass rate is below threshold")
        void shouldRejectWhenPassRateBelowThreshold() { 
            PromotionGate gate = new PromotionGate( 
                    "gate-prod", "production",
                    List.of("eval.safety.v1"),
                    0.90, false, Duration.ofHours(24), 5.0); 

            List<EvaluationResult> results = List.of( 
                    new EvaluationResult("r1", "eval.safety.v1", "2.0", 
                            0.75, 100, 75, 25, false, Map.of(), List.of(), 
                            Instant.now(), 1000L)); 

            PromotionGate.PromotionDecision decision = gate.evaluate(results); 

            assertThat(decision.approved()).isFalse(); 
            assertThat(decision.reason()).contains("pass rate");
        }

        @Test
        @DisplayName("should block when human signoff is required")
        void shouldBlockWhenHumanSignoffRequired() { 
            PromotionGate gate = new PromotionGate( 
                    "gate-prod", "production",
                    List.of("eval.safety.v1"),
                    0.90, true, // requiresHumanSignoff
                    Duration.ofHours(24), 5.0); 

            List<EvaluationResult> results = List.of( 
                    new EvaluationResult("r1", "eval.safety.v1", "2.0", 
                            0.98, 100, 98, 2, true, Map.of(), List.of(), 
                            Instant.now(), 1000L)); 

            PromotionGate.PromotionDecision decision = gate.evaluate(results); 

            assertThat(decision.approved()).isFalse(); 
            assertThat(decision.reason()).contains("human sign-off");
        }

        @Test
        @DisplayName("should reject invalid minimumPassRate out of range")
        void shouldRejectInvalidPassRate() { 
            assertThatThrownBy(() -> new PromotionGate( 
                    "gate-1", "staging", List.of(), 1.5, false, 
                    Duration.ofHours(1), 5.0)) 
                    .isInstanceOf(IllegalArgumentException.class) 
                    .hasMessageContaining("minimumPassRate");
        }

        @Test
        @DisplayName("should approve with empty required pack list")
        void shouldApproveWithEmptyRequiredPacks() { 
            PromotionGate gate = new PromotionGate( 
                    "gate-dev", "development",
                    List.of(), 0.0, false, 
                    Duration.ZERO, 0.0);

            PromotionGate.PromotionDecision decision = gate.evaluate(List.of()); 

            assertThat(decision.approved()).isTrue(); 
        }
    }
}
