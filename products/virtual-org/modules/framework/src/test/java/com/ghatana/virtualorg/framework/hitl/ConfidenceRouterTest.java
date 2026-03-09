package com.ghatana.virtualorg.framework.hitl;

import com.ghatana.virtualorg.framework.runtime.AgentDecision;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for ConfidenceRouter.
 *
 * @doc.type class
 * @doc.purpose Unit tests for confidence routing
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("ConfidenceRouter Tests")
class ConfidenceRouterTest {

    @Test
    @DisplayName("Should create router with default thresholds")
    void shouldCreateRouterWithDefaultThresholds() {
        // WHEN
        ConfidenceRouter router = ConfidenceRouter.defaults();

        // THEN
        assertThat(router.getAutoApproveThreshold()).isEqualTo(0.9);
        assertThat(router.getHumanReviewThreshold()).isEqualTo(0.7);
        assertThat(router.getEscalateThreshold()).isEqualTo(0.4);
        assertThat(router.getRejectThreshold()).isEqualTo(0.2);
    }

    @ParameterizedTest
    @CsvSource({
        "0.95, AUTO_APPROVE",
        "0.90, AUTO_APPROVE",
        "0.85, HUMAN_REVIEW",
        "0.70, HUMAN_REVIEW",
        "0.60, ESCALATE",
        "0.40, ESCALATE",
        "0.30, REJECT",
        "0.20, REJECT",
        "0.10, REJECT"
    })
    @DisplayName("Should route based on confidence level")
    void shouldRouteBasedOnConfidenceLevel(double confidence, String expectedAction) {
        // GIVEN
        ConfidenceRouter router = ConfidenceRouter.defaults();
        AgentDecision decision = createDecisionWithConfidence(confidence);

        // WHEN
        ConfidenceRouter.RoutingDecision routing = router.route(decision);

        // THEN
        assertThat(routing.getAction().name()).isEqualTo(expectedAction);
    }

    @Test
    @DisplayName("Should auto-approve high confidence decisions")
    void shouldAutoApproveHighConfidenceDecisions() {
        // GIVEN
        ConfidenceRouter router = ConfidenceRouter.defaults();
        AgentDecision decision = createDecisionWithConfidence(0.95);

        // WHEN
        ConfidenceRouter.RoutingDecision routing = router.route(decision);

        // THEN
        assertThat(routing.isAutoApprove()).isTrue();
        assertThat(routing.getReason()).contains("auto-approve");
    }

    @Test
    @DisplayName("Should require human review for medium confidence")
    void shouldRequireHumanReviewForMediumConfidence() {
        // GIVEN
        ConfidenceRouter router = ConfidenceRouter.defaults();
        AgentDecision decision = createDecisionWithConfidence(0.75);

        // WHEN
        ConfidenceRouter.RoutingDecision routing = router.route(decision);

        // THEN
        assertThat(routing.needsHumanReview()).isTrue();
    }

    @Test
    @DisplayName("Should escalate low confidence decisions")
    void shouldEscalateLowConfidenceDecisions() {
        // GIVEN
        ConfidenceRouter router = ConfidenceRouter.defaults();
        AgentDecision decision = createDecisionWithConfidence(0.5);

        // WHEN
        ConfidenceRouter.RoutingDecision routing = router.route(decision);

        // THEN
        assertThat(routing.needsEscalation()).isTrue();
    }

    @Test
    @DisplayName("Should reject very low confidence decisions")
    void shouldRejectVeryLowConfidenceDecisions() {
        // GIVEN
        ConfidenceRouter router = ConfidenceRouter.defaults();
        AgentDecision decision = createDecisionWithConfidence(0.15);

        // WHEN
        ConfidenceRouter.RoutingDecision routing = router.route(decision);

        // THEN
        assertThat(routing.isRejected()).isTrue();
    }

    @Test
    @DisplayName("Should allow custom thresholds")
    void shouldAllowCustomThresholds() {
        // GIVEN
        ConfidenceRouter router = ConfidenceRouter.builder()
                .autoApproveThreshold(0.95)
                .humanReviewThreshold(0.8)
                .escalateThreshold(0.5)
                .rejectThreshold(0.3)
                .build();

        // WHEN - 0.82 confidence
        AgentDecision decision = createDecisionWithConfidence(0.82);
        ConfidenceRouter.RoutingDecision routing = router.route(decision);

        // THEN - With default thresholds this would be auto-approve, but with custom it's human review
        assertThat(routing.needsHumanReview()).isTrue();
    }

    @Test
    @DisplayName("Should reject invalid threshold order")
    void shouldRejectInvalidThresholdOrder() {
        assertThatThrownBy(() -> ConfidenceRouter.builder()
                .autoApproveThreshold(0.5) // Lower than humanReview
                .humanReviewThreshold(0.7)
                .build()
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("autoApproveThreshold must be >= humanReviewThreshold");
    }

    @Test
    @DisplayName("Should downgrade auto-approve for critical risk")
    void shouldDowngradeAutoApproveForCriticalRisk() {
        // GIVEN
        ConfidenceRouter router = ConfidenceRouter.defaults();
        AgentDecision decision = createDecisionWithConfidence(0.95);

        // WHEN - routing with critical risk
        ConfidenceRouter.RoutingDecision routing = router.routeWithContext(
                decision,
                ApprovalContext.RiskLevel.CRITICAL,
                true // reversible
        );

        // THEN - should require human review despite high confidence
        assertThat(routing.needsHumanReview()).isTrue();
        assertThat(routing.getReason()).contains("Critical risk");
    }

    @Test
    @DisplayName("Should downgrade auto-approve for high risk irreversible actions")
    void shouldDowngradeAutoApproveForHighRiskIrreversible() {
        // GIVEN
        ConfidenceRouter router = ConfidenceRouter.defaults();
        AgentDecision decision = createDecisionWithConfidence(0.95);

        // WHEN - routing with high risk and not reversible
        ConfidenceRouter.RoutingDecision routing = router.routeWithContext(
                decision,
                ApprovalContext.RiskLevel.HIGH,
                false // not reversible
        );

        // THEN - should require human review
        assertThat(routing.needsHumanReview()).isTrue();
        assertThat(routing.getReason()).contains("irreversible");
    }

    // ========== Helpers ==========
    private AgentDecision createDecisionWithConfidence(double confidence) {
        return AgentDecision.builder()
                .reasoning("Test reasoning")
                .decision("Test decision")
                .confidence(confidence)
                .build();
    }
}
