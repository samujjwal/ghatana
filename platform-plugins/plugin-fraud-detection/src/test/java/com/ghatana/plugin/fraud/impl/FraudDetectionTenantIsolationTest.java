package com.ghatana.plugin.fraud.impl;

import com.ghatana.platform.plugin.PluginContext;
import com.ghatana.platform.plugin.PluginState;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.plugin.fraud.FraudDetectionPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * KP-FRD: Tenant isolation, deterministic fixture, and explainability conformance tests
 * for {@link StandardFraudDetectionPlugin}.
 *
 * <p>Covers:</p>
 * <ul>
 *   <li>KP-FRD-001: fraud score isolation — rules registered for product A must not apply to product B</li>
 *   <li>KP-FRD-002: high-value amount deterministic fixture — amounts above threshold must flag HIGH risk</li>
 *   <li>KP-FRD-003: known-clean fixture — typical amounts must not produce HIGH risk score</li>
 *   <li>KP-FRD-004: explainability — assessment must include non-empty explanation list</li>
 *   <li>KP-FRD-005: pattern detection — multiple assessments within a window generate a detectable pattern</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose KP-FRD fraud detection tenant isolation and deterministic fixture tests
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("FraudDetectionPlugin — tenant isolation and deterministic fixture tests")
@ExtendWith(MockitoExtension.class)
class FraudDetectionTenantIsolationTest extends EventloopTestBase {

    @Mock
    private PluginContext mockContext;

    private StandardFraudDetectionPlugin plugin;

    @BeforeEach
    void setUp() {
        plugin = new StandardFraudDetectionPlugin();
        runPromise(() -> plugin.initialize(mockContext).then(v -> plugin.start()));
    }

    // ── KP-FRD-001: Tenant isolation ─────────────────────────────────────────

    @Test
    @DisplayName("KP-FRD-001: custom rule registered for product-A must not trigger for product-B assessment")
    void testRuleIsolationBetweenProducts() {
        // Register a rule that flags all transactions for product-A
        FraudDetectionPlugin.FraudRule flagAllRule = new FraudDetectionPlugin.FraudRule(
                "flag-all-rule", "ALWAYS_HIGH", "> 0", 100.0);
        runPromise(() -> plugin.registerRule("product-A", flagAllRule));

        // Assess for product-A — should trigger the rule
        FraudDetectionPlugin.FraudDetectionRequest requestA = new FraudDetectionPlugin.FraudDetectionRequest(
                UUID.randomUUID().toString(), "TRANSACTION", Map.of("amount", 50, "product", "product-A"), "model_v1");
        FraudDetectionPlugin.FraudAssessment assessmentA =
                runPromise(() -> plugin.assessTransaction(requestA.entityId(), requestA));

        // Assess for product-B — the rule registered for product-A must NOT apply
        FraudDetectionPlugin.FraudDetectionRequest requestB = new FraudDetectionPlugin.FraudDetectionRequest(
                UUID.randomUUID().toString(), "TRANSACTION", Map.of("amount", 50, "product", "product-B"), "model_v1");
        FraudDetectionPlugin.FraudAssessment assessmentB =
                runPromise(() -> plugin.assessTransaction(requestB.entityId(), requestB));

        assertThat(assessmentA.riskLevel()).as("product-A assessment should be elevated by its rule")
                .isNotNull();
        // The key invariant: product-B should NOT see the same escalated risk from product-A's rule
        assertThat(assessmentB.riskScore())
                .as("product-B score must not be contaminated by product-A rule")
                .isLessThan(assessmentA.riskScore());
    }

    // ── KP-FRD-002: High-value deterministic fixture ──────────────────────────

    @Test
    @DisplayName("KP-FRD-002: transaction with amount > 50000 must produce HIGH risk level")
    void testHighValueAmountProducesHighRisk() {
        FraudDetectionPlugin.FraudDetectionRequest request = new FraudDetectionPlugin.FraudDetectionRequest(
                UUID.randomUUID().toString(), "TRANSACTION",
                Map.of("amount", 100_000, "currency", "USD", "mcc", 6011), "model_v1");

        FraudDetectionPlugin.FraudAssessment assessment =
                runPromise(() -> plugin.assessTransaction(request.entityId(), request));

        assertThat(assessment.riskScore())
                .as("high-value amount must produce a fraud score >= 0.6")
                .isGreaterThanOrEqualTo(0.6);
        assertThat(assessment.riskLevel())
                .as("high-value amount must produce HIGH risk level")
                .isEqualTo(FraudDetectionPlugin.FraudAssessment.RiskLevel.HIGH);
    }

    // ── KP-FRD-003: Known-clean deterministic fixture ────────────────────────

    @Test
    @DisplayName("KP-FRD-003: typical low-value transaction must not produce HIGH risk")
    void testTypicalAmountProducesLowRisk() {
        FraudDetectionPlugin.FraudDetectionRequest request = new FraudDetectionPlugin.FraudDetectionRequest(
                UUID.randomUUID().toString(), "TRANSACTION",
                Map.of("amount", 15, "currency", "USD", "mcc", 5411), "model_v1");

        FraudDetectionPlugin.FraudAssessment assessment =
                runPromise(() -> plugin.assessTransaction(request.entityId(), request));

        assertThat(assessment.riskLevel())
                .as("small grocery purchase must not produce HIGH risk")
                .isNotEqualTo(FraudDetectionPlugin.FraudAssessment.RiskLevel.HIGH);
    }

    // ── KP-FRD-004: Explainability ────────────────────────────────────────────

    @Test
    @DisplayName("KP-FRD-004: every fraud assessment must include a non-empty explanation list")
    void testExplainabilityPresent() {
        FraudDetectionPlugin.FraudDetectionRequest request = new FraudDetectionPlugin.FraudDetectionRequest(
                UUID.randomUUID().toString(), "TRANSACTION",
                Map.of("amount", 500, "mcc", 5411), "model_v1");

        FraudDetectionPlugin.FraudAssessment assessment =
                runPromise(() -> plugin.assessTransaction(request.entityId(), request));

        assertThat(assessment.explanation())
                .as("fraud assessment must carry explanation factors for audit purposes")
                .isNotNull()
                .isNotEmpty();
    }

    // ── KP-FRD-005: Pattern detection ─────────────────────────────────────────

    @Test
    @DisplayName("KP-FRD-005: pattern detection over a time window must return a result")
    void testPatternDetectionReturnsResult() {
        // Register a rule that always produces HIGH risk for pattern detection
        FraudDetectionPlugin.FraudRule highRiskRule = new FraudDetectionPlugin.FraudRule(
                "pattern-high-rule", "ALWAYS_HIGH", "Always high for pattern test", 0.7);
        runPromise(() -> plugin.registerRule("TRANSACTION", highRiskRule));

        // Generate several assessments first to build up history
        for (int i = 0; i < 5; i++) {
            FraudDetectionPlugin.FraudDetectionRequest req = new FraudDetectionPlugin.FraudDetectionRequest(
                    UUID.randomUUID().toString(), "TRANSACTION",
                    Map.of("amount", 100_000 + i * 1000, "mcc", 5411), "model_v1");
            runPromise(() -> plugin.assessTransaction(req.entityId(), req));
        }

        FraudDetectionPlugin.TimeWindow window = new FraudDetectionPlugin.TimeWindow(
                Instant.now().minusSeconds(3600), Instant.now());

        FraudDetectionPlugin.FraudPattern pattern =
                runPromise(() -> plugin.detectPatterns("TRANSACTION", window));

        assertThat(pattern).isNotNull();
        assertThat(pattern.detectedAt()).isNotNull();
    }
}
