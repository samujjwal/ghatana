package com.ghatana.plugin.risk.impl;

import com.ghatana.platform.plugin.PluginContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.plugin.risk.RiskManagementPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Explainability tests for {@link StandardRiskManagementPlugin}.
 *
 * <p>Verifies that risk scores carry component-level breakdown for audit,
 * that risk level thresholds are calibrated correctly, that reports include
 * the summary statistics needed for downstream decision-making, and that
 * limit breach detection generates actionable alerts.
 *
 * @doc.type class
 * @doc.purpose Explainability and assessment quality tests for risk management plugin
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Risk management â€“ explainability")
@ExtendWith(MockitoExtension.class)
class RiskManagementExplainabilityTest extends EventloopTestBase {

    @Mock
    private PluginContext mockContext;

    private StandardRiskManagementPlugin plugin;

    @BeforeEach
    void setUp() {
        plugin = new StandardRiskManagementPlugin();
        runPromise(() -> plugin.initialize(mockContext));
        runPromise(() -> plugin.start());
    }

    // =========================================================================
    // Component score breakdown (explainability)
    // =========================================================================

    @Nested
    @DisplayName("Component score breakdown")
    class ComponentScoreBreakdown {

        @Test
        @DisplayName("credit risk score includes component breakdown for all input factors")
        void creditRiskScoreHasComponentBreakdown() {
            Map<String, Object> factors = Map.of(
                "credit_score", 620.0,
                "debt_ratio", 0.45,
                "payment_history", 0.70
            );
            RiskManagementPlugin.RiskScore score =
                runPromise(() -> plugin.calculateRisk("customer-1",
                    RiskManagementPlugin.RiskModelId.CREDIT, factors));

            assertThat(score.componentScores())
                .as("component scores must include each input factor")
                .containsKeys("credit_score", "debt_ratio", "payment_history");

            // All component scores must be within [0, 1]
            score.componentScores().values().forEach(v ->
                assertThat(v)
                    .as("component score must be within [0, 1]")
                    .isGreaterThanOrEqualTo(0.0)
                    .isLessThanOrEqualTo(1.0)
            );
        }

        @Test
        @DisplayName("market risk score includes component breakdown for all input factors")
        void marketRiskScoreHasComponentBreakdown() {
            Map<String, Object> factors = Map.of(
                "volatility", 0.6,
                "position_size", 500_000.0,
                "concentration", 0.3,
                "liquidity", 0.8
            );
            RiskManagementPlugin.RiskScore score =
                runPromise(() -> plugin.calculateRisk("portfolio-1",
                    RiskManagementPlugin.RiskModelId.MARKET, factors));

            assertThat(score.componentScores())
                .containsKeys("volatility", "position_size", "concentration", "liquidity");
        }

                @Test
        @DisplayName("custom model score with generic numeric factors")
        void customModelRiskScoreWithGenericFactors() {
            Map<String, Object> factors = Map.of(
                "factor_a", 75.0,
                "severity_score", 7.0,
                "factor_b", 3.0
            );
            RiskManagementPlugin.RiskScore score =
                runPromise(() -> plugin.calculateRisk("entity-custom-1",
                    new RiskManagementPlugin.RiskModelId("OPERATIONAL"), factors));

            assertThat(score).isNotNull();
        }

        @Test
        @DisplayName("empty factors produce zero aggregate score")
        void emptyFactorsProduceZeroScore() {
            RiskManagementPlugin.RiskScore score =
                runPromise(() -> plugin.calculateRisk("entity-empty",
                    RiskManagementPlugin.RiskModelId.COMPLIANCE, Map.of()));

            assertThat(score.score()).isEqualTo(0.0);
            assertThat(score.level()).isEqualTo(RiskManagementPlugin.RiskScore.RiskLevel.LOW);
            assertThat(score.componentScores()).isEmpty();
        }
    }

    // =========================================================================
    // Risk level calibration
    // =========================================================================

    @Nested
    @DisplayName("Risk level calibration")
    class RiskLevelCalibration {

        @Test
        @DisplayName("very poor credit score produces HIGH or CRITICAL risk")
        void poorCreditScoreProducesHighRisk() {
            // credit_score=300 â†’ 1-(300/850)â‰ˆ0.647 â†’ HIGH
            Map<String, Object> factors = Map.of("credit_score", 300.0);
            RiskManagementPlugin.RiskScore score =
                runPromise(() -> plugin.calculateRisk("poor-credit",
                    RiskManagementPlugin.RiskModelId.CREDIT, factors));

            assertThat(score.level())
                .isIn(
                    RiskManagementPlugin.RiskScore.RiskLevel.HIGH,
                    RiskManagementPlugin.RiskScore.RiskLevel.CRITICAL
                );
        }

        @Test
        @DisplayName("excellent credit score produces LOW risk")
        void excellentCreditProducesLowRisk() {
            // credit_score=800 â†’ 1-(800/850)â‰ˆ0.059 â†’ LOW
            Map<String, Object> factors = Map.of("credit_score", 800.0);
            RiskManagementPlugin.RiskScore score =
                runPromise(() -> plugin.calculateRisk("excellent-credit",
                    RiskManagementPlugin.RiskModelId.CREDIT, factors));

            assertThat(score.level()).isEqualTo(RiskManagementPlugin.RiskScore.RiskLevel.LOW);
        }

        @Test
        @DisplayName("score at boundary thresholds is categorized correctly")
        void boundaryScoresAreCategorizeable() {
            // Operational: error_rate=0.8 â†’ score=0.8 â†’ CRITICAL
            Map<String, Object> highError = Map.of("error_rate", 0.8);
            RiskManagementPlugin.RiskScore high =
                runPromise(() -> plugin.calculateRisk("ops-high",
                    RiskManagementPlugin.RiskModelId.OPERATIONAL, highError));
            assertThat(high.level()).isEqualTo(RiskManagementPlugin.RiskScore.RiskLevel.CRITICAL);

            // Operational: error_rate=0.1 â†’ score=0.1 â†’ LOW
            Map<String, Object> lowError = Map.of("error_rate", 0.1);
            RiskManagementPlugin.RiskScore low =
                runPromise(() -> plugin.calculateRisk("ops-low",
                    RiskManagementPlugin.RiskModelId.OPERATIONAL, lowError));
            assertThat(low.level()).isEqualTo(RiskManagementPlugin.RiskScore.RiskLevel.LOW);
        }
    }

    // =========================================================================
    // Limit breach alerts
    // =========================================================================

    @Nested
    @DisplayName("Limit breach alerts")
    class LimitBreachAlerts {

        @Test
        @DisplayName("high market risk with limits set produces an alert")
        void highMarketRiskWithLimitsGeneratesAlert() {
            Map<String, BigDecimal> limits = Map.of(
                "position_size", BigDecimal.valueOf(100_000),
                "max_exposure",  BigDecimal.valueOf(500_000)
            );
            runPromise(() -> plugin.setRiskLimits("trader-1", limits));

            // volatility=1.0 â†’ component score=0.3; position_size=5M â†’ 1.0; â†’ avg ~0.65 â†’ HIGH
            Map<String, Object> factors = Map.of("volatility", 1.0, "position_size", 5_000_000.0);
            runPromise(() -> plugin.calculateRisk("trader-1",
                RiskManagementPlugin.RiskModelId.MARKET, factors));

            List<RiskManagementPlugin.RiskAlert> activeAlerts =
                runPromise(() -> plugin.getActiveAlerts("trader-1"));

            assertThat(activeAlerts)
                .as("breach of market risk threshold must generate at least one alert")
                .isNotEmpty();

            RiskManagementPlugin.RiskAlert alert = activeAlerts.getFirst();
            assertThat(alert.entityId()).isEqualTo("trader-1");
            assertThat(alert.alertType()).isIn("LIMIT_BREACH", "HIGH_RISK_SCORE");
            assertThat(alert.message()).isNotBlank();
            assertThat(alert.severity()).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("low market risk does not generate any alert")
        void lowMarketRiskNoAlert() {
            Map<String, BigDecimal> limits = Map.of(
                "position_size", BigDecimal.valueOf(100_000)
            );
            runPromise(() -> plugin.setRiskLimits("trader-2", limits));

            Map<String, Object> factors = Map.of("volatility", 0.1, "liquidity", 0.9);
            runPromise(() -> plugin.calculateRisk("trader-2",
                RiskManagementPlugin.RiskModelId.MARKET, factors));

            List<RiskManagementPlugin.RiskAlert> activeAlerts =
                runPromise(() -> plugin.getActiveAlerts("trader-2"));

            assertThat(activeAlerts)
                .as("low market risk should not trigger any alerts")
                .isEmpty();
        }

        @Test
        @DisplayName("alerts without limits set are not generated")
        void noLimitsNoAlerts() {
            Map<String, Object> factors = Map.of("volatility", 1.0, "position_size", 5_000_000.0);
            runPromise(() -> plugin.calculateRisk("unlim-trader",
                RiskManagementPlugin.RiskModelId.MARKET, factors));

            List<RiskManagementPlugin.RiskAlert> alerts =
                runPromise(() -> plugin.getActiveAlerts("unlim-trader"));

            assertThat(alerts)
                .as("risk calculation without limits must not auto-generate alerts")
                .isEmpty();
        }
    }

    // =========================================================================
    // Report summary explainability
    // =========================================================================

    @Nested
    @DisplayName("Report summary explainability")
    class ReportSummary {

        @Test
        @DisplayName("report includes averageRiskScore, criticalRiskCount, highRiskCount, activeAlertCount")
        void reportSummaryContainsRequiredFields() {
            Instant start = Instant.now().minusSeconds(5);

            runPromise(() -> plugin.calculateRisk("ent-rpt",
                RiskManagementPlugin.RiskModelId.CREDIT, Map.of("credit_score", 200.0)));
            runPromise(() -> plugin.calculateRisk("ent-rpt",
                RiskManagementPlugin.RiskModelId.MARKET, Map.of("volatility", 0.9)));

            Instant end = Instant.now().plusSeconds(5);
            RiskManagementPlugin.RiskReport report =
                runPromise(() -> plugin.generateReport("ent-rpt",
                    new RiskManagementPlugin.TimeRange(start, end)));

            assertThat(report).isNotNull();
            assertThat(report.entityId()).isEqualTo("ent-rpt");
            assertThat(report.summary())
                .containsKey("averageRiskScore")
                .containsKey("criticalRiskCount")
                .containsKey("highRiskCount")
                .containsKey("activeAlertCount");

            assertThat(report.scores())
                .as("report must include the scores within the time range")
                .isNotEmpty();
        }

        @Test
        @DisplayName("report for entity with no history returns empty scores list")
        void reportForUnknownEntityReturnsEmpty() {
            Instant start = Instant.now().minusSeconds(60);
            Instant end = Instant.now().plusSeconds(60);

            RiskManagementPlugin.RiskReport report =
                runPromise(() -> plugin.generateReport("unknown-entity",
                    new RiskManagementPlugin.TimeRange(start, end)));

            assertThat(report).isNotNull();
            assertThat(report.scores()).isEmpty();
            assertThat(report.alerts()).isEmpty();
        }

        @Test
        @DisplayName("report scores are ordered within the requested time range")
        void reportFiltersToTimeRange() {
            Instant before = Instant.now();

            runPromise(() -> plugin.calculateRisk("filtered-ent",
                RiskManagementPlugin.RiskModelId.FRAUD, Map.of("anomaly_score", 0.5)));

            Instant after = Instant.now();

            // Query a range that ends before the score was recorded â€” should return zero scores
            RiskManagementPlugin.RiskReport reportBefore =
                runPromise(() -> plugin.generateReport("filtered-ent",
                    new RiskManagementPlugin.TimeRange(
                        before.minusSeconds(120), before.minusSeconds(1))));

            assertThat(reportBefore.scores())
                .as("scores before the calculation window should not appear in the report")
                .isEmpty();

            // Query a range that includes the score
            RiskManagementPlugin.RiskReport reportAfter =
                runPromise(() -> plugin.generateReport("filtered-ent",
                    new RiskManagementPlugin.TimeRange(before, after.plusSeconds(1))));

            assertThat(reportAfter.scores())
                .as("score within the range must appear in the report")
                .hasSize(1);
        }

        @Test
        @DisplayName("report has a non-null generatedAt timestamp")
        void reportHasGeneratedAtTimestamp() {
            Instant start = Instant.now().minusSeconds(1);
            Instant end = Instant.now().plusSeconds(1);

            RiskManagementPlugin.RiskReport report =
                runPromise(() -> plugin.generateReport("ts-ent",
                    new RiskManagementPlugin.TimeRange(start, end)));

            assertThat(report.generatedAt())
                .isNotNull()
                .isBeforeOrEqualTo(Instant.now());
        }
    }

    // =========================================================================
    // Score history
    // =========================================================================

    @Nested
    @DisplayName("Score history")
    class ScoreHistory {

        @Test
        @DisplayName("multiple calculations produce a growing history reflected in the report")
        void multipleCalculationsAccumulateHistory() {
            Instant start = Instant.now();

            for (int i = 0; i < 3; i++) {
                double score = 0.1 * (i + 1);
                runPromise(() -> plugin.calculateRisk("ent-hist",
                    RiskManagementPlugin.RiskModelId.COMPLIANCE,
                    Map.of("violation_count", score * 10)));
            }

            Instant end = Instant.now().plusSeconds(1);
            RiskManagementPlugin.RiskReport report =
                runPromise(() -> plugin.generateReport("ent-hist",
                    new RiskManagementPlugin.TimeRange(start, end)));

            assertThat(report.scores())
                .as("all 3 calculations must appear in the report")
                .hasSize(3);
        }
    }
}
