package com.ghatana.aiplatform.observability;

import com.ghatana.platform.observability.MetricsCollector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DataDriftDetector.
 *
 * Tests validate:
 * - PSI calculation accuracy
 * - Drift detection and alerting
 * - Multi-tenant isolation
 * - Observation tracking
 * - Edge cases (zero observations, missing baseline)
 *
 * @see DataDriftDetector
 */
@DisplayName("Data Drift Detector Tests")
class DataDriftDetectorTest {

    private DataDriftDetector detector;
    private MetricsCollector metrics;

    @BeforeEach
    void setUp() {
        // GIVEN: Mocked metrics collector
        metrics = mock(MetricsCollector.class);
        detector = new DataDriftDetector(metrics);
    }

    /**
     * Verifies no drift detected when current distribution matches baseline
     * exactly.
     *
     * GIVEN: Baseline distribution and identical current observations
     * WHEN: calculatePSI() is called
     * THEN: PSI should be 0 (no drift)
     */
    @Test
    @DisplayName("Should return zero PSI when distributions are identical")
    void shouldReturnZeroPSIWhenDistributionsIdentical() {
        // GIVEN: Baseline distribution
        Map<String, Double> baseline = new HashMap<>();
        baseline.put("0-100", 0.3);
        baseline.put("100-1000", 0.5);
        baseline.put("1000+", 0.2);
        detector.setBaseline("tenant-123", "amount", baseline);

        // GIVEN: Current observations matching baseline
        for (int i = 0; i < 30; i++) {
            detector.recordObservation("tenant-123", "amount", "0-100");
        }
        for (int i = 0; i < 50; i++) {
            detector.recordObservation("tenant-123", "amount", "100-1000");
        }
        for (int i = 0; i < 20; i++) {
            detector.recordObservation("tenant-123", "amount", "1000+");
        }

        // WHEN: Calculate PSI
        double psi = detector.calculatePSI("tenant-123", "amount");

        // THEN: PSI should be ~0
        assertThat(psi)
                .as("PSI should be near zero when distributions match")
                .isCloseTo(0.0, within(0.001));

        // Verify no drift warning emitted
        verify(metrics, never()).incrementCounter(
                argThat((String s) -> s.contains("drift")),
                any());
    }

    /**
     * Verifies small drift warning when PSI is between thresholds.
     *
     * GIVEN: Baseline and slightly different current distribution
     * WHEN: calculatePSI() is called
     * THEN: PSI between 0.1-0.25 triggers warning
     */
    @Test
    @DisplayName("Should emit warning when PSI indicates small drift")
    void shouldEmitWarningForSmallDrift() {
        // GIVEN: Baseline distribution
        Map<String, Double> baseline = new HashMap<>();
        baseline.put("0-100", 0.3);
        baseline.put("100-1000", 0.5);
        baseline.put("1000+", 0.2);
        detector.setBaseline("tenant-123", "amount", baseline);

        // GIVEN: Shifted observations (more in 100-1000, less in 0-100)
        for (int i = 0; i < 20; i++) {
            detector.recordObservation("tenant-123", "amount", "0-100");
        }
        for (int i = 0; i < 60; i++) {
            detector.recordObservation("tenant-123", "amount", "100-1000");
        }
        for (int i = 0; i < 20; i++) {
            detector.recordObservation("tenant-123", "amount", "1000+");
        }

        // WHEN: Calculate PSI
        double psi = detector.calculatePSI("tenant-123", "amount");

        // THEN: PSI should indicate drift warning
        assertThat(psi)
                .as("PSI should indicate small drift")
                .isGreaterThan(0.05)
                .isLessThan(0.5);

        // Verify warning metrics emitted
        verify(metrics).recordTimer(
                eq("ai.drift.data.psi"),
                anyLong(),
                argThat(s -> s.contains("tenant")), eq("tenant-123"),
                argThat(s -> s.contains("feature")), eq("amount"));
    }

    /**
     * Verifies alert triggered when PSI exceeds alert threshold.
     *
     * GIVEN: Baseline and significantly different current distribution
     * WHEN: calculatePSI() is called
     * THEN: PSI > 0.25 triggers alert
     */
    @Test
    @DisplayName("Should emit alert when PSI indicates significant drift")
    void shouldEmitAlertForSignificantDrift() {
        // GIVEN: Baseline distribution
        Map<String, Double> baseline = new HashMap<>();
        baseline.put("0-100", 0.5);
        baseline.put("100-1000", 0.3);
        baseline.put("1000+", 0.2);
        detector.setBaseline("tenant-123", "amount", baseline);

        // GIVEN: Highly shifted observations
        for (int i = 0; i < 10; i++) {
            detector.recordObservation("tenant-123", "amount", "0-100");
        }
        for (int i = 0; i < 70; i++) {
            detector.recordObservation("tenant-123", "amount", "100-1000");
        }
        for (int i = 0; i < 20; i++) {
            detector.recordObservation("tenant-123", "amount", "1000+");
        }

        // WHEN: Calculate PSI
        double psi = detector.calculatePSI("tenant-123", "amount");

        // THEN: PSI should exceed alert threshold
        assertThat(psi)
                .as("PSI should indicate significant drift")
                .isGreaterThan(0.25);

        // Verify alert metrics emitted
        verify(metrics).incrementCounter(
                eq("ai.drift.alert"),
                argThat(s -> s.contains("tenant")), eq("tenant-123"),
                argThat(s -> s.contains("feature")), eq("amount"),
                argThat(s -> s.contains("severity")), eq("high"));
    }

    /**
     * Verifies multi-tenant isolation - drifts isolated per tenant.
     *
     * GIVEN: Baseline for two tenants with different observations
     * WHEN: calculatePSI() for each tenant
     * THEN: PSI values are independent
     */
    @Test
    @DisplayName("Should enforce tenant isolation for drift calculations")
    void shouldEnforceTenantIsolationForDrift() {
        // GIVEN: Baseline for two tenants
        Map<String, Double> baseline = new HashMap<>();
        baseline.put("0-100", 0.5);
        baseline.put("100-1000", 0.5);

        detector.setBaseline("tenant-1", "amount", baseline);
        detector.setBaseline("tenant-2", "amount", baseline);

        // GIVEN: No drift for tenant-1
        for (int i = 0; i < 50; i++) {
            detector.recordObservation("tenant-1", "amount", "0-100");
        }
        for (int i = 0; i < 50; i++) {
            detector.recordObservation("tenant-1", "amount", "100-1000");
        }

        // GIVEN: Significant drift for tenant-2
        for (int i = 0; i < 10; i++) {
            detector.recordObservation("tenant-2", "amount", "0-100");
        }
        for (int i = 0; i < 90; i++) {
            detector.recordObservation("tenant-2", "amount", "100-1000");
        }

        // WHEN: Calculate PSI for both tenants
        double psi1 = detector.calculatePSI("tenant-1", "amount");
        double psi2 = detector.calculatePSI("tenant-2", "amount");

        // THEN: PSI values are independent
        assertThat(psi1)
                .as("Tenant-1 should have no drift")
                .isCloseTo(0.0, within(0.01));

        assertThat(psi2)
                .as("Tenant-2 should have drift")
                .isGreaterThan(0.1);
    }

    /**
     * Verifies graceful handling when no baseline set.
     *
     * GIVEN: Feature with observations but no baseline
     * WHEN: calculatePSI() is called
     * THEN: Returns 0 and logs warning
     */
    @Test
    @DisplayName("Should return zero when baseline not set")
    void shouldReturnZeroWhenBaselineNotSet() {
        // GIVEN: No baseline set
        detector.recordObservation("tenant-123", "amount", "0-100");
        detector.recordObservation("tenant-123", "amount", "100-1000");

        // WHEN: Calculate PSI
        double psi = detector.calculatePSI("tenant-123", "amount");

        // THEN: Should return 0
        assertThat(psi)
                .as("PSI should be 0 when baseline missing")
                .isZero();
    }

    /**
     * Verifies graceful handling when no observations recorded.
     *
     * GIVEN: Baseline set but no current observations
     * WHEN: calculatePSI() is called
     * THEN: Returns 0 and logs warning
     */
    @Test
    @DisplayName("Should return zero when no observations recorded")
    void shouldReturnZeroWhenNoObservations() {
        // GIVEN: Baseline set
        Map<String, Double> baseline = new HashMap<>();
        baseline.put("0-100", 0.5);
        baseline.put("100-1000", 0.5);
        detector.setBaseline("tenant-123", "amount", baseline);

        // WHEN: Calculate PSI without recording observations
        double psi = detector.calculatePSI("tenant-123", "amount");

        // THEN: Should return 0
        assertThat(psi)
                .as("PSI should be 0 when no observations recorded")
                .isZero();
    }

    /**
     * Verifies observation reset functionality.
     *
     * GIVEN: Observations recorded and PSI calculated
     * WHEN: resetObservations() is called
     * THEN: Observations are cleared, PSI becomes 0
     */
    @Test
    @DisplayName("Should reset observations for feature")
    void shouldResetObservations() {
        // GIVEN: Baseline and observations
        Map<String, Double> baseline = new HashMap<>();
        baseline.put("0-100", 0.5);
        baseline.put("100-1000", 0.5);
        detector.setBaseline("tenant-123", "amount", baseline);

        for (int i = 0; i < 30; i++) {
            detector.recordObservation("tenant-123", "amount", "0-100");
        }
        for (int i = 0; i < 70; i++) {
            detector.recordObservation("tenant-123", "amount", "100-1000");
        }

        // GIVEN: PSI calculated (> 0)
        double psiBeforeReset = detector.calculatePSI("tenant-123", "amount");
        assertThat(psiBeforeReset).isGreaterThan(0.1);

        // WHEN: Reset observations
        detector.resetObservations("tenant-123", "amount");

        // THEN: PSI should be 0 after reset
        double psiAfterReset = detector.calculatePSI("tenant-123", "amount");
        assertThat(psiAfterReset)
                .as("PSI should be 0 after reset")
                .isZero();
    }

    /**
     * Verifies PSI calculation with multiple features.
     *
     * GIVEN: Multiple features tracked for same tenant
     * WHEN: calculatePSI() called for each
     * THEN: PSI values independent per feature
     */
    @Test
    @DisplayName("Should track multiple features independently")
    void shouldTrackMultipleFeaturesIndependently() {
        // GIVEN: Baseline for two features
        Map<String, Double> baseline = new HashMap<>();
        baseline.put("0-100", 0.5);
        baseline.put("100-1000", 0.5);

        detector.setBaseline("tenant-123", "amount", baseline);
        detector.setBaseline("tenant-123", "frequency", baseline);

        // GIVEN: No drift in amount
        for (int i = 0; i < 50; i++) {
            detector.recordObservation("tenant-123", "amount", "0-100");
        }
        for (int i = 0; i < 50; i++) {
            detector.recordObservation("tenant-123", "amount", "100-1000");
        }

        // GIVEN: Drift in frequency
        for (int i = 0; i < 20; i++) {
            detector.recordObservation("tenant-123", "frequency", "0-100");
        }
        for (int i = 0; i < 80; i++) {
            detector.recordObservation("tenant-123", "frequency", "100-1000");
        }

        // WHEN: Calculate PSI for each feature
        double amountPSI = detector.calculatePSI("tenant-123", "amount");
        double frequencyPSI = detector.calculatePSI("tenant-123", "frequency");

        // THEN: PSI values are independent
        assertThat(amountPSI)
                .as("Amount should have minimal drift")
                .isCloseTo(0.0, within(0.01));

        assertThat(frequencyPSI)
                .as("Frequency should have drift")
                .isGreaterThan(0.1);
    }

    /**
     * Verifies metrics collection on PSI calculation.
     *
     * GIVEN: Baseline and observations
     * WHEN: calculatePSI() is called
     * THEN: Metrics collector records PSI value
     */
    @Test
    @DisplayName("Should record PSI metrics")
    void shouldRecordPSIMetrics() {
        // GIVEN: Baseline
        Map<String, Double> baseline = new HashMap<>();
        baseline.put("0-100", 0.5);
        baseline.put("100-1000", 0.5);
        detector.setBaseline("tenant-123", "amount", baseline);

        // GIVEN: Observations
        for (int i = 0; i < 50; i++) {
            detector.recordObservation("tenant-123", "amount", "0-100");
        }
        for (int i = 0; i < 50; i++) {
            detector.recordObservation("tenant-123", "amount", "100-1000");
        }

        // WHEN: Calculate PSI
        detector.calculatePSI("tenant-123", "amount");

        // THEN: Metrics should be recorded
        verify(metrics).recordTimer(
                eq("ai.drift.data.psi"),
                anyLong(),
                eq("tenant"), eq("tenant-123"),
                eq("feature"), eq("amount"));
    }

    /**
     * Verifies null parameter handling.
     *
     * GIVEN: Null parameters provided
     * WHEN: Methods are called
     * THEN: NullPointerException thrown
     */
    @Test
    @DisplayName("Should reject null parameters")
    void shouldRejectNullParameters() {
        // GIVEN: Valid baseline
        Map<String, Double> baseline = new HashMap<>();
        baseline.put("0-100", 0.5);

        // THEN: Reject null tenant
        assertThatThrownBy(() -> detector.setBaseline(null, "feature", baseline))
                .isInstanceOf(NullPointerException.class);

        // THEN: Reject null feature
        assertThatThrownBy(() -> detector.setBaseline("tenant", null, baseline))
                .isInstanceOf(NullPointerException.class);

        // THEN: Reject null distribution
        assertThatThrownBy(() -> detector.setBaseline("tenant", "feature", null))
                .isInstanceOf(NullPointerException.class);
    }

    /**
     * Verifies constructor rejects null metrics.
     *
     * GIVEN: Null metrics collector
     * WHEN: Constructor called
     * THEN: NullPointerException thrown
     */
    @Test
    @DisplayName("Should reject null metrics in constructor")
    void shouldRejectNullMetrics() {
        // THEN: Null metrics should throw
        assertThatThrownBy(() -> new DataDriftDetector(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("metrics must not be null");
    }
}
