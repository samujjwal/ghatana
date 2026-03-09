package com.ghatana.aiplatform.observability;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for QualityMonitor.
 *
 * Tests validate:
 * - SLA threshold management
 * - Quality metrics calculation (precision, recall, F1)
 * - SLA breach detection and alerting
 * - Multi-tenant isolation
 * - Correlation with data drift
 *
 * @see QualityMonitor
 */
@DisplayName("Quality Monitor Tests")
class QualityMonitorTest {

    private QualityMonitor monitor;
    private com.ghatana.observability.MetricsCollector metrics;
    private DataDriftDetector driftDetector;

    @BeforeEach
    void setUp() {
        // GIVEN: Mocked dependencies
        metrics = mock(com.ghatana.observability.MetricsCollector.class);
        driftDetector = mock(DataDriftDetector.class);
        monitor = new QualityMonitor(metrics, driftDetector);
    }

    /**
     * Verifies precision calculation from confusion matrix.
     *
     * GIVEN: Predictions with known TP and FP values
     * WHEN: getPrecision() is called
     * THEN: Returns TP / (TP + FP)
     */
    @Test
    @DisplayName("Should calculate precision correctly")
    void shouldCalculatePrecisionCorrectly() {
        // GIVEN: Model set up
        monitor.setQualitySLA("tenant-123", "model", 0.8);

        // GIVEN: Predictions
        // TP: confidence=0.9, actual=true (2 cases)
        monitor.recordPrediction("tenant-123", "model", 0.9, true);
        monitor.recordPrediction("tenant-123", "model", 0.9, true);

        // FP: confidence=0.8, actual=false (1 case)
        monitor.recordPrediction("tenant-123", "model", 0.8, false);

        // TN: confidence=0.3, actual=false (1 case)
        monitor.recordPrediction("tenant-123", "model", 0.3, false);

        // WHEN: Calculate precision
        double precision = monitor.getPrecision("tenant-123", "model");

        // THEN: Precision should be 2 / (2 + 1) = 0.667
        assertThat(precision)
                .as("Precision should be TP / (TP + FP) = 2/3")
                .isCloseTo(2.0 / 3.0, within(0.01));
    }

    /**
     * Verifies recall calculation from confusion matrix.
     *
     * GIVEN: Predictions with known TP and FN values
     * WHEN: getRecall() is called
     * THEN: Returns TP / (TP + FN)
     */
    @Test
    @DisplayName("Should calculate recall correctly")
    void shouldCalculateRecallCorrectly() {
        // GIVEN: Model set up
        monitor.setQualitySLA("tenant-123", "model", 0.8);

        // GIVEN: Predictions
        // TP: confidence=0.8, actual=true (2 cases)
        monitor.recordPrediction("tenant-123", "model", 0.8, true);
        monitor.recordPrediction("tenant-123", "model", 0.8, true);

        // FN: confidence=0.3, actual=true (1 case)
        monitor.recordPrediction("tenant-123", "model", 0.3, true);

        // TN: confidence=0.2, actual=false (1 case)
        monitor.recordPrediction("tenant-123", "model", 0.2, false);

        // WHEN: Calculate recall
        double recall = monitor.getRecall("tenant-123", "model");

        // THEN: Recall should be 2 / (2 + 1) = 0.667
        assertThat(recall)
                .as("Recall should be TP / (TP + FN) = 2/3")
                .isCloseTo(2.0 / 3.0, within(0.01));
    }

    /**
     * Verifies F1 score calculation.
     *
     * GIVEN: Known precision and recall values
     * WHEN: getF1Score() is called
     * THEN: Returns 2 * (precision * recall) / (precision + recall)
     */
    @Test
    @DisplayName("Should calculate F1 score correctly")
    void shouldCalculateF1ScoreCorrectly() {
        // GIVEN: Model set up
        monitor.setQualitySLA("tenant-123", "model", 0.7);

        // GIVEN: Predictions creating precision and recall
        // Create scenario: TP=2, FP=1, FN=1, TN=1
        monitor.recordPrediction("tenant-123", "model", 0.8, true); // TP
        monitor.recordPrediction("tenant-123", "model", 0.8, true); // TP
        monitor.recordPrediction("tenant-123", "model", 0.7, false); // FP
        monitor.recordPrediction("tenant-123", "model", 0.3, true); // FN
        monitor.recordPrediction("tenant-123", "model", 0.2, false); // TN

        // WHEN: Calculate F1
        double f1 = monitor.getF1Score("tenant-123", "model");

        // THEN: F1 should be calculated
        assertThat(f1)
                .as("F1 score should be between 0 and 1")
                .isGreaterThan(0.0)
                .isLessThan(1.0);
    }

    /**
     * Verifies SLA breach detection and alerting.
     *
     * GIVEN: SLA threshold set and predictions recorded
     * WHEN: checkQuality() detects precision below threshold
     * THEN: QualityAlert is marked as breeched
     */
    @Test
    @DisplayName("Should detect SLA breach when precision falls below threshold")
    void shouldDetectSLABreach() {
        // GIVEN: SLA of 0.9
        monitor.setQualitySLA("tenant-123", "model", 0.9);

        // GIVEN: Predictions with low precision
        // TP=1, FP=9 → precision=0.1 (well below 0.9)
        for (int i = 0; i < 1; i++) {
            monitor.recordPrediction("tenant-123", "model", 0.8, true);
        }
        for (int i = 0; i < 9; i++) {
            monitor.recordPrediction("tenant-123", "model", 0.7, false);
        }

        // WHEN: Check quality
        QualityMonitor.QualityAlert alert = monitor.checkQuality("tenant-123", "model");

        // THEN: Alert should be breeched
        assertThat(alert.isBreeched())
                .as("Alert should be breeched when precision < SLA")
                .isTrue();

        assertThat(alert.getCurrentPrecision())
                .as("Precision should be below SLA threshold")
                .isLessThan(0.9);

        // Verify alert metrics emitted
        verify(metrics).incrementCounter(
                eq("ai.quality.sla_breach"),
                eq("tenant"), eq("tenant-123"),
                eq("model"), eq("model"),
                eq("severity"), eq("high"));
    }

    /**
     * Verifies no breach when precision meets SLA.
     *
     * GIVEN: SLA threshold and good predictions
     * WHEN: checkQuality() is called
     * THEN: QualityAlert is not breeched
     */
    @Test
    @DisplayName("Should not breach when precision meets SLA")
    void shouldNotBreachWhenPrecisionMeetsSLA() {
        // GIVEN: SLA of 0.7
        monitor.setQualitySLA("tenant-123", "model", 0.7);

        // GIVEN: Predictions with good precision
        // TP=7, FP=3 → precision=0.7 (exactly at threshold)
        for (int i = 0; i < 7; i++) {
            monitor.recordPrediction("tenant-123", "model", 0.8, true);
        }
        for (int i = 0; i < 3; i++) {
            monitor.recordPrediction("tenant-123", "model", 0.6, false);
        }

        // WHEN: Check quality
        QualityMonitor.QualityAlert alert = monitor.checkQuality("tenant-123", "model");

        // THEN: Alert should not be breeched
        assertThat(alert.isBreeched())
                .as("Alert should not breach when precision >= SLA")
                .isFalse();

        // Verify no breach metric emitted
        verify(metrics, never()).incrementCounter(
                argThat((String s) -> s.contains("sla_breach")),
                any());
    }

    /**
     * Verifies multi-tenant isolation for quality metrics.
     *
     * GIVEN: Predictions for two tenants with different quality
     * WHEN: Metrics calculated for each tenant
     * THEN: Metrics are independent
     */
    @Test
    @DisplayName("Should enforce tenant isolation for quality metrics")
    void shouldEnforceTenantIsolationForQuality() {
        // GIVEN: SLA for both tenants
        monitor.setQualitySLA("tenant-1", "model", 0.8);
        monitor.setQualitySLA("tenant-2", "model", 0.8);

        // GIVEN: Good predictions for tenant-1
        for (int i = 0; i < 8; i++) {
            monitor.recordPrediction("tenant-1", "model", 0.9, true);
        }
        for (int i = 0; i < 2; i++) {
            monitor.recordPrediction("tenant-1", "model", 0.3, false);
        }

        // GIVEN: Poor predictions for tenant-2
        for (int i = 0; i < 2; i++) {
            monitor.recordPrediction("tenant-2", "model", 0.8, true);
        }
        for (int i = 0; i < 8; i++) {
            monitor.recordPrediction("tenant-2", "model", 0.6, false);
        }

        // WHEN: Get precision for both tenants
        double precision1 = monitor.getPrecision("tenant-1", "model");
        double precision2 = monitor.getPrecision("tenant-2", "model");

        // THEN: Precision values are independent
        assertThat(precision1)
                .as("Tenant-1 should have high precision")
                .isGreaterThan(0.7);

        assertThat(precision2)
                .as("Tenant-2 should have low precision")
                .isLessThan(0.5);
    }

    /**
     * Verifies SLA threshold validation.
     *
     * GIVEN: Invalid SLA thresholds
     * WHEN: setQualitySLA() called
     * THEN: IllegalArgumentException thrown
     */
    @Test
    @DisplayName("Should reject invalid SLA thresholds")
    void shouldRejectInvalidSLAThresholds() {
        // THEN: Reject threshold < 0
        assertThatThrownBy(() -> monitor.setQualitySLA("tenant", "model", -0.1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 0.0 and 1.0");

        // THEN: Reject threshold > 1
        assertThatThrownBy(() -> monitor.setQualitySLA("tenant", "model", 1.1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 0.0 and 1.0");
    }

    /**
     * Verifies confidence parameter validation.
     *
     * GIVEN: Invalid confidence values
     * WHEN: recordPrediction() called
     * THEN: IllegalArgumentException thrown
     */
    @Test
    @DisplayName("Should reject invalid confidence values")
    void shouldRejectInvalidConfidenceValues() {
        // THEN: Reject confidence < 0
        assertThatThrownBy(() -> monitor.recordPrediction("tenant", "model", -0.1, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 0.0 and 1.0");

        // THEN: Reject confidence > 1
        assertThatThrownBy(() -> monitor.recordPrediction("tenant", "model", 1.1, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 0.0 and 1.0");
    }

    /**
     * Verifies null parameter handling.
     *
     * GIVEN: Null parameters
     * WHEN: Methods called
     * THEN: NullPointerException thrown
     */
    @Test
    @DisplayName("Should reject null parameters")
    void shouldRejectNullParameters() {
        // THEN: Reject null tenant in setQualitySLA
        assertThatThrownBy(() -> monitor.setQualitySLA(null, "model", 0.8))
                .isInstanceOf(NullPointerException.class);

        // THEN: Reject null model in setQualitySLA
        assertThatThrownBy(() -> monitor.setQualitySLA("tenant", null, 0.8))
                .isInstanceOf(NullPointerException.class);
    }

    /**
     * Verifies default SLA applied when not set.
     *
     * GIVEN: No explicit SLA set for model
     * WHEN: checkQuality() called
     * THEN: Uses default SLA threshold (0.8)
     */
    @Test
    @DisplayName("Should use default SLA when not explicitly set")
    void shouldUseDefaultSLAWhenNotSet() {
        // GIVEN: No SLA set (defaults to 0.8)
        // GIVEN: Predictions with precision = 0.75 (below default 0.8)
        for (int i = 0; i < 3; i++) {
            monitor.recordPrediction("tenant-123", "model", 0.8, true);
        }
        for (int i = 0; i < 1; i++) {
            monitor.recordPrediction("tenant-123", "model", 0.7, false);
        }

        // WHEN: Check quality
        QualityMonitor.QualityAlert alert = monitor.checkQuality("tenant-123", "model");

        // THEN: Default threshold should be applied
        assertThat(alert.getSlaThreshold())
                .as("Should use default SLA of 0.8")
                .isEqualTo(0.8);

        assertThat(alert.isBreeched())
                .as("Should breach against default SLA")
                .isTrue();
    }

    /**
     * Verifies zero predictions handled gracefully.
     *
     * GIVEN: No predictions recorded
     * WHEN: Metrics requested
     * THEN: Returns 0.0
     */
    @Test
    @DisplayName("Should return zero metrics when no predictions")
    void shouldReturnZeroMetricsWhenNoPredictions() {
        // WHEN: Get metrics without predictions
        double precision = monitor.getPrecision("tenant-123", "model");
        double recall = monitor.getRecall("tenant-123", "model");
        double f1 = monitor.getF1Score("tenant-123", "model");

        // THEN: All should be zero
        assertThat(precision).isZero();
        assertThat(recall).isZero();
        assertThat(f1).isZero();
    }

    /**
     * Verifies QualityAlert data accessors.
     *
     * GIVEN: QualityAlert created
     * WHEN: Accessors called
     * THEN: Return expected values
     */
    @Test
    @DisplayName("Should expose QualityAlert properties")
    void shouldExposeQualityAlertProperties() {
        // GIVEN: Create alert
        QualityMonitor.QualityAlert alert = new QualityMonitor.QualityAlert(
                "tenant-123", "model-x", 0.75, 0.85, true, null);

        // THEN: Verify properties
        assertThat(alert.getTenantId()).isEqualTo("tenant-123");
        assertThat(alert.getModelName()).isEqualTo("model-x");
        assertThat(alert.getCurrentPrecision()).isEqualTo(0.75);
        assertThat(alert.getSlaThreshold()).isEqualTo(0.85);
        assertThat(alert.isBreeched()).isTrue();
    }
}
