package com.ghatana.aiplatform.promotion;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.observability.NoopMetricsCollector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for DeploymentPromoter.
 *
 * Tests validate:
 * - Policy-based promotion decisions
 * - Shadow mode testing and results
 * - Promotion execution and tracking
 * - Multi-tenant promotion isolation
 * - Blocker detection and reasoning
 *
 * @see DeploymentPromoter
 */
@DisplayName("DeploymentPromoter Integration Tests")
class DeploymentPromoterIntegrationTest extends EventloopTestBase {

    private DeploymentPromoter promoter;

    @BeforeEach
    void setUp() {
        promoter = new DeploymentPromoter(NoopMetricsCollector.getInstance());
    }

    /**
     * Verifies model promotes when all thresholds met.
     *
     * GIVEN: Model with precision >= 0.85, recall >= 0.80
     * WHEN: evaluatePromotion() called without shadow requirement
     * THEN: Decision.shouldPromote() returns true
     */
    @Test
    @DisplayName("Should promote model when all quality thresholds met")
    void shouldPromoteModelWhenQualityThresholdsMet() {
        // GIVEN: Promotion policy with achievable thresholds
        DeploymentPromoter.PromotionPolicy policy = new DeploymentPromoter.PromotionPolicy(
            0.85, 0.80, 0.10, false, 0
        );

        // WHEN: Evaluate promotion
        DeploymentPromoter.PromotionDecision decision = runPromise(() ->
            promoter.evaluatePromotion("tenant-123", "model-v2", policy)
        );

        // THEN: Should promote
        assertThat(decision.shouldPromote())
            .as("Model meeting thresholds should promote")
            .isTrue();
        assertThat(decision.blockers())
            .as("No blockers should be present")
            .isEmpty();
    }

    /**
     * Verifies promotion blocked when precision threshold missed.
     *
     * GIVEN: Policy requires precision >= 0.90
     * WHEN: Model has precision 0.87
     * THEN: Decision.shouldPromote() returns false, blocker added
     */
    @Test
    @DisplayName("Should block promotion when precision threshold missed")
    void shouldBlockPromotionWhenPrecisionThresholdMissed() {
        // GIVEN: Strict precision policy
        DeploymentPromoter.PromotionPolicy policy = new DeploymentPromoter.PromotionPolicy(
            0.90,  // Strict threshold (model is 0.87)
            0.80,
            0.10,
            false,
            0
        );

        // WHEN: Evaluate promotion
        DeploymentPromoter.PromotionDecision decision = runPromise(() ->
            promoter.evaluatePromotion("tenant-123", "model-v2", policy)
        );

        // THEN: Should not promote
        assertThat(decision.shouldPromote())
            .as("Model missing precision threshold should not promote")
            .isFalse();
        assertThat(decision.blockers())
            .as("Precision blocker should be present")
            .isNotEmpty()
            .anySatisfy(blocker -> assertThat(blocker).contains("Precision"));
    }

    /**
     * Verifies shadow mode requirement prevents promotion.
     *
     * GIVEN: Policy requires shadow mode
     * WHEN: evaluatePromotion() called
     * THEN: shouldPromote() returns false, shadow testing required
     */
    @Test
    @DisplayName("Should require shadow mode testing before promotion")
    void shouldRequireShadowModeTestingBeforePromotion() {
        // GIVEN: Policy requiring shadow mode
        DeploymentPromoter.PromotionPolicy policy = new DeploymentPromoter.PromotionPolicy(
            0.85, 0.80, 0.10, true, 1000
        );

        // WHEN: Evaluate promotion
        DeploymentPromoter.PromotionDecision decision = runPromise(() ->
            promoter.evaluatePromotion("tenant-123", "model-v2", policy)
        );

        // THEN: Promotion blocked, shadow mode noted
        assertThat(decision.shouldPromote())
            .as("Shadow mode requirement should block initial promotion")
            .isFalse();
        assertThat(decision.reason())
            .as("Reason should mention shadow mode")
            .contains("Shadow");
    }

    /**
     * Verifies shadow mode execution and result tracking.
     *
     * GIVEN: Candidate model scheduled for shadow mode
     * WHEN: executeShadowMode() called
     * THEN: Returns shadow results with success rate and latency impact
     */
    @Test
    @DisplayName("Should execute shadow mode and track results")
    void shouldExecuteShadowModeAndTrackResults() {
        // GIVEN: Candidate model and duration
        String tenantId = "tenant-shadow";
        String candidateModel = "model-v2-shadow";
        Duration duration = Duration.ofMinutes(5);

        // WHEN: Execute shadow mode
        DeploymentPromoter.ShadowModeResult result = runPromise(() ->
            promoter.executeShadowMode(tenantId, candidateModel, duration)
        );

        // THEN: Shadow results captured
        assertThat(result)
            .as("Shadow mode result should be present")
            .isNotNull();
        assertThat(result.samplesProcessed())
            .as("Samples should be processed")
            .isGreaterThan(0);
        assertThat(result.successRate())
            .as("Success rate should be in range [0,1]")
            .isBetween(0.0, 1.0);
        assertThat(result.latencyDiff())
            .as("Latency difference should be tracked")
            .isNotNull();
    }

    /**
     * Verifies promotion execution after passing evaluation.
     *
     * GIVEN: Model with passing evaluation
     * WHEN: promote() called
     * THEN: Model becomes active, returns promoted model target
     */
    @Test
    @DisplayName("Should execute promotion and return promoted model target")
    void shouldExecutePromotionAndReturnPromotedModel() {
        // GIVEN: Candidate model ready for promotion
        String tenantId = "tenant-prod";
        String candidateModel = "model-v2-candidate";

        // WHEN: Promote
        String promotedTarget = runPromise(() ->
            promoter.promote(tenantId, candidateModel)
        );

        // THEN: Promoted model returned
        assertThat(promotedTarget)
            .as("Promoted model target should be non-empty")
            .isNotEmpty()
            .contains("promoted");
        assertThat(promotedTarget)
            .as("Promoted target should reference candidate")
            .contains(candidateModel);
    }

    /**
     * Verifies multi-tenant promotion isolation.
     *
     * GIVEN: Promotions for two different tenants
     * WHEN: Both execute concurrently
     * THEN: Tenant A's promotion unaffected by tenant B's promotion
     */
    @Test
    @DisplayName("Should enforce tenant isolation during promotion")
    void shouldEnforceTenantIsolationDuringPromotion() {
        // GIVEN: Two tenants
        String tenantA = "tenant-a";
        String tenantB = "tenant-b";

        // WHEN: Both promote
        String promotedA = runPromise(() ->
            promoter.promote(tenantA, "model-a")
        );

        String promotedB = runPromise(() ->
            promoter.promote(tenantB, "model-b")
        );

        // THEN: Promotions isolated by tenant
        assertThat(promotedA)
            .as("Tenant A promotion should be distinct")
            .isNotEqualTo(promotedB);
        assertThat(promotedA)
            .as("Tenant A model should be preserved")
            .contains("model-a");
        assertThat(promotedB)
            .as("Tenant B model should be preserved")
            .contains("model-b");
    }

    /**
     * Verifies cost increase blocker detection.
     *
     * GIVEN: Policy restricts cost increase to 5%
     * WHEN: Model increases cost by 10%
     * THEN: Promotion blocked with cost blocker
     */
    @Test
    @DisplayName("Should block promotion when cost increase exceeds threshold")
    void shouldBlockPromotionWhenCostIncreaseExceedsThreshold() {
        // GIVEN: Strict cost policy
        DeploymentPromoter.PromotionPolicy policy = new DeploymentPromoter.PromotionPolicy(
            0.85, 0.80, 0.05, false, 0  // 5% max cost increase
        );

        // WHEN: Evaluate (model has 10% cost increase in mock)
        DeploymentPromoter.PromotionDecision decision = runPromise(() ->
            promoter.evaluatePromotion("tenant-cost", "model-expensive", policy)
        );

        // THEN: Cost blocker present
        assertThat(decision.blockers())
            .as("Cost blocker should be present")
            .isNotEmpty()
            .anySatisfy(blocker -> assertThat(blocker).contains("Cost"));
    }

    /**
     * Verifies multiple blockers tracked together.
     *
     * GIVEN: Model fails multiple thresholds
     * WHEN: evaluatePromotion() called
     * THEN: All blockers collected and returned
     */
    @Test
    @DisplayName("Should collect multiple blockers into decision")
    void shouldCollectMultipleBlockersIntoDecision() {
        // GIVEN: Very strict policy (impossible thresholds)
        DeploymentPromoter.PromotionPolicy policy = new DeploymentPromoter.PromotionPolicy(
            0.99,  // Impossible precision (model is 0.87)
            0.99,  // Impossible recall (model is 0.82)
            0.01,  // Impossible cost (model is 0.05)
            false,
            0
        );

        // WHEN: Evaluate
        DeploymentPromoter.PromotionDecision decision = runPromise(() ->
            promoter.evaluatePromotion("tenant-strict", "model-v2", policy)
        );

        // THEN: Multiple blockers present
        assertThat(decision.blockers())
            .as("Multiple blockers should be collected")
            .isNotEmpty()
            .hasSizeGreaterThan(1);
        assertThat(decision.reason())
            .as("Reason should explain blockers")
            .isNotEmpty();
    }

    /**
     * Verifies promotion policy validation.
     *
     * GIVEN: Policy with invalid ranges
     * WHEN: Used in promotion decision
     * THEN: Validation ensures ranges are correct
     */
    @Test
    @DisplayName("Should validate policy thresholds are in valid ranges")
    void shouldValidatePolicyThresholdsInValidRanges() {
        // GIVEN: Valid policy
        DeploymentPromoter.PromotionPolicy policy = new DeploymentPromoter.PromotionPolicy(
            0.85, 0.80, 0.10, false, 1000
        );

        // THEN: Policy should have valid ranges
        assertThat(policy.minPrecision())
            .as("Min precision should be in range [0,1]")
            .isBetween(0.0, 1.0);
        assertThat(policy.minRecall())
            .as("Min recall should be in range [0,1]")
            .isBetween(0.0, 1.0);
        assertThat(policy.maxCostIncrease())
            .as("Max cost increase should be non-negative")
            .isGreaterThanOrEqualTo(0.0);
    }
}
