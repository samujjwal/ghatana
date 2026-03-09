package com.ghatana.aiplatform.observability;

import com.ghatana.platform.observability.NoopMetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for DataDriftDetector with LLM gateway and inference
 * services.
 *
 * Tests validate:
 * - Drift detection in production LLM inference pipeline
 * - Multi-feature drift tracking across requests
 * - Tenant-aware monitoring with isolated baselines
 * - Drift alerts triggering based on thresholds
 * - Integration with observability metrics
 *
 * @see DataDriftDetector
 * @see com.ghatana.aiplatform.gateway.LLMGatewayService
 * @see com.ghatana.aiplatform.serving.OnlineInferenceService
 */
@DisplayName("DataDriftDetector Integration Tests")
class DataDriftDetectorIntegrationTest extends EventloopTestBase {

    private DataDriftDetector driftDetector;

    @BeforeEach
    void setUp() {
        // GIVEN: Drift detector with noop metrics
        driftDetector = new DataDriftDetector(new NoopMetricsCollector());
    }

    /**
     * Verifies drift detection in LLM input features during production serving.
     *
     * GIVEN: LLM model with input features (e.g., user_segment,
     * transaction_history)
     * WHEN: Input distribution shifts during serving
     * THEN: Drift alerts trigger for retraining/rollback decision
     */
    @Test
    @DisplayName("Should detect input drift in LLM serving pipeline")
    void shouldDetectInputDriftInServingPipeline() {
        // GIVEN: Baseline for fraud detection model inputs
        Map<String, Double> userSegmentBaseline = new HashMap<>();
        userSegmentBaseline.put("premium", 0.2);
        userSegmentBaseline.put("standard", 0.6);
        userSegmentBaseline.put("free", 0.2);

        driftDetector.setBaseline("tenant-llm", "user_segment", userSegmentBaseline);

        // WHEN: Serving requests show shifted distribution (more premium users)
        for (int i = 0; i < 1000; i++) {
            String segment;
            if (i < 400)
                segment = "premium"; // 40% (up from 20%)
            else if (i < 800)
                segment = "standard"; // 40% (down from 60%)
            else
                segment = "free"; // 20% (same)

            driftDetector.recordObservation("tenant-llm", "user_segment", segment);
        }

        // THEN: Calculate drift
        double drift = driftDetector.calculatePSI("tenant-llm", "user_segment");

        // PSI calculation: (0.4-0.2)*ln(0.4/0.2) + (0.4-0.6)*ln(0.4/0.6) +
        // (0.2-0.2)*ln(0.2/0.2)
        // = 0.2*ln(2) + (-0.2)*ln(0.667) + 0
        // = 0.2*0.693 + (-0.2)*(-0.405) + 0
        // = 0.139 + 0.081 = 0.220
        assertThat(drift)
                .as("PSI should detect significant user_segment distribution shift")
                .isGreaterThan(0.15)
                .isLessThan(0.30);
    }

    /**
     * Verifies multi-feature drift tracking across different input types.
     *
     * GIVEN: Multiple input features (numerical, categorical)
     * WHEN: Each feature monitored independently
     * THEN: Drift detected per-feature, enabling root cause analysis
     */
    @Test
    @DisplayName("Should track multi-feature drift independently")
    void shouldTrackMultiFeatureDriftIndependently() {
        // GIVEN: Multiple feature baselines
        Map<String, Double> ageBaseline = new HashMap<>();
        ageBaseline.put("young", 0.3);
        ageBaseline.put("middle", 0.5);
        ageBaseline.put("senior", 0.2);

        Map<String, Double> deviceBaseline = new HashMap<>();
        deviceBaseline.put("mobile", 0.7);
        deviceBaseline.put("desktop", 0.3);

        driftDetector.setBaseline("tenant-multi", "user_age", ageBaseline);
        driftDetector.setBaseline("tenant-multi", "device_type", deviceBaseline);

        // WHEN: Age distribution shifts significantly
        for (int i = 0; i < 100; i++) {
            if (i < 60)
                driftDetector.recordObservation("tenant-multi", "user_age", "young");
            else if (i < 85)
                driftDetector.recordObservation("tenant-multi", "user_age", "middle");
            else
                driftDetector.recordObservation("tenant-multi", "user_age", "senior");
        }

        // WHEN: Device distribution remains stable
        for (int i = 0; i < 100; i++) {
            if (i < 70)
                driftDetector.recordObservation("tenant-multi", "device_type", "mobile");
            else
                driftDetector.recordObservation("tenant-multi", "device_type", "desktop");
        }

        // THEN: Age shows drift, device stable
        double ageDrift = driftDetector.calculatePSI("tenant-multi", "user_age");
        double deviceDrift = driftDetector.calculatePSI("tenant-multi", "device_type");

        assertThat(ageDrift)
                .as("Age drift (60% young vs 30% expected) should be high")
                .isGreaterThan(0.15);

        assertThat(deviceDrift)
                .as("Device drift (70% mobile vs 70% expected) should be low")
                .isLessThan(0.05);
    }

    /**
     * Verifies tenant isolation for drift baselines during multi-tenant serving.
     *
     * GIVEN: Multiple tenants with same feature names
     * WHEN: Each tenant's distribution tracked independently
     * THEN: No cross-tenant contamination
     */
    @Test
    @DisplayName("Should enforce tenant isolation for drift baselines")
    void shouldEnforceTenantIsolationForDriftBaselines() {
        // GIVEN: Two tenants with different distributions for same feature
        Map<String, Double> tenantABaseline = new HashMap<>();
        tenantABaseline.put("high", 0.1);
        tenantABaseline.put("medium", 0.3);
        tenantABaseline.put("low", 0.6);

        Map<String, Double> tenantBBaseline = new HashMap<>();
        tenantBBaseline.put("high", 0.5);
        tenantBBaseline.put("medium", 0.3);
        tenantBBaseline.put("low", 0.2);

        driftDetector.setBaseline("tenant-a", "risk_level", tenantABaseline);
        driftDetector.setBaseline("tenant-b", "risk_level", tenantBBaseline);

        // WHEN: Record tenant-a observations (same as baseline)
        for (int i = 0; i < 100; i++) {
            if (i < 10)
                driftDetector.recordObservation("tenant-a", "risk_level", "high");
            else if (i < 40)
                driftDetector.recordObservation("tenant-a", "risk_level", "medium");
            else
                driftDetector.recordObservation("tenant-a", "risk_level", "low");
        }

        // WHEN: Record tenant-b observations (shift: more high-risk)
        for (int i = 0; i < 100; i++) {
            if (i < 70)
                driftDetector.recordObservation("tenant-b", "risk_level", "high");
            else if (i < 85)
                driftDetector.recordObservation("tenant-b", "risk_level", "medium");
            else
                driftDetector.recordObservation("tenant-b", "risk_level", "low");
        }

        // THEN: Tenant-A shows no drift (matched baseline)
        double driftA = driftDetector.calculatePSI("tenant-a", "risk_level");
        assertThat(driftA)
                .as("Tenant-A drift should be minimal (distribution matches baseline)")
                .isLessThan(0.05);

        // THEN: Tenant-B shows significant drift (70% high vs 50% expected)
        double driftB = driftDetector.calculatePSI("tenant-b", "risk_level");
        assertThat(driftB)
                .as("Tenant-B drift should be significant (70% high vs 50% expected)")
                .isGreaterThan(0.1);

        // VERIFY: Tenant-A drift unchanged after tenant-B operations
        double driftAFinal = driftDetector.calculatePSI("tenant-a", "risk_level");
        assertThat(driftAFinal)
                .as("Tenant-A drift must be isolated from tenant-B data")
                .isEqualTo(driftA);
    }

    /**
     * Verifies drift threshold interpretation for alert triggering.
     *
     * GIVEN: PSI thresholds (0.1 warning, 0.25 alert)
     * WHEN: Different levels of distribution shift
     * THEN: Appropriate alerts generated
     */
    @Test
    @DisplayName("Should trigger appropriate alerts at drift thresholds")
    void shouldTriggerAppropriateDriftAlerts() {
        // GIVEN: Baseline for LLM embedding input
        Map<String, Double> tokenTypeBaseline = new HashMap<>();
        tokenTypeBaseline.put("keyword", 0.5);
        tokenTypeBaseline.put("entity", 0.3);
        tokenTypeBaseline.put("other", 0.2);

        driftDetector.setBaseline("alert-test", "token_type", tokenTypeBaseline);

        // Scenario 1: Minimal shift (< 0.1 PSI = no alert)
        for (int i = 0; i < 100; i++) {
            if (i < 52)
                driftDetector.recordObservation("alert-test", "token_type", "keyword");
            else if (i < 82)
                driftDetector.recordObservation("alert-test", "token_type", "entity");
            else
                driftDetector.recordObservation("alert-test", "token_type", "other");
        }
        double drift1 = driftDetector.calculatePSI("alert-test", "token_type");
        assertThat(drift1).isLessThan(0.1);

        // Reset observations for next scenario
        driftDetector.resetObservations("alert-test", "token_type");

        // Scenario 2: Moderate shift (0.1-0.25 PSI = warning)
        for (int i = 0; i < 100; i++) {
            if (i < 65)
                driftDetector.recordObservation("alert-test", "token_type", "keyword");
            else if (i < 80)
                driftDetector.recordObservation("alert-test", "token_type", "entity");
            else
                driftDetector.recordObservation("alert-test", "token_type", "other");
        }
        double drift2 = driftDetector.calculatePSI("alert-test", "token_type");
        assertThat(drift2)
                .as("Moderate shift should be in warning range")
                .isGreaterThanOrEqualTo(0.1)
                .isLessThan(0.25);

        // Reset observations
        driftDetector.resetObservations("alert-test", "token_type");

        // Scenario 3: Severe shift (> 0.25 PSI = alert)
        for (int i = 0; i < 100; i++) {
            if (i < 85)
                driftDetector.recordObservation("alert-test", "token_type", "keyword");
            else if (i < 92)
                driftDetector.recordObservation("alert-test", "token_type", "entity");
            else
                driftDetector.recordObservation("alert-test", "token_type", "other");
        }
        double drift3 = driftDetector.calculatePSI("alert-test", "token_type");
        assertThat(drift3)
                .as("Severe shift (85% vs 50%) should trigger alert")
                .isGreaterThan(0.25);
    }

    /**
     * Verifies observation windowing for time-series drift tracking.
     *
     * GIVEN: Drift detector with observation reset capability
     * WHEN: Observations collected, reset, new observations collected
     * THEN: Independent windows enable time-windowed analysis
     */
    @Test
    @DisplayName("Should support time-windowed drift analysis via reset")
    void shouldSupportTimeWindowedDriftAnalysis() {
        // GIVEN: Baseline
        Map<String, Double> baseline = new HashMap<>();
        baseline.put("a", 0.5);
        baseline.put("b", 0.5);

        driftDetector.setBaseline("window-test", "feature", baseline);

        // WHEN: First time window - stable distribution
        for (int i = 0; i < 50; i++) {
            if (i < 25)
                driftDetector.recordObservation("window-test", "feature", "a");
            else
                driftDetector.recordObservation("window-test", "feature", "b");
        }
        double drift1 = driftDetector.calculatePSI("window-test", "feature");

        // THEN: No drift in first window
        assertThat(drift1).isLessThan(0.05);

        // WHEN: Reset observations for new time window
        driftDetector.resetObservations("window-test", "feature");

        // WHEN: Second time window - shifted distribution (70% a, 30% b)
        for (int i = 0; i < 50; i++) {
            if (i < 35)
                driftDetector.recordObservation("window-test", "feature", "a");
            else
                driftDetector.recordObservation("window-test", "feature", "b");
        }
        double drift2 = driftDetector.calculatePSI("window-test", "feature");

        // THEN: Significant drift detected in second window
        assertThat(drift2)
                .as("Second window shows shift (70% vs 50%)")
                .isGreaterThan(0.08);

        // VERIFY: First window drift remains unchanged
        driftDetector.resetObservations("window-test", "feature");
        for (int i = 0; i < 50; i++) {
            if (i < 25)
                driftDetector.recordObservation("window-test", "feature", "a");
            else
                driftDetector.recordObservation("window-test", "feature", "b");
        }
        double drift1Final = driftDetector.calculatePSI("window-test", "feature");
        assertThat(drift1Final).isLessThan(0.05);
    }

    /**
     * Verifies PSI calculation accuracy with known distributions.
     *
     * GIVEN: Known baseline and observation distributions
     * WHEN: PSI calculated
     * THEN: Result matches mathematical formula
     */
    @Test
    @DisplayName("Should calculate PSI accurately with known distributions")
    void shouldCalculatePSIAccurately() {
        // GIVEN: Simple 2-bucket distribution
        Map<String, Double> baseline = new HashMap<>();
        baseline.put("yes", 0.3);
        baseline.put("no", 0.7);

        driftDetector.setBaseline("psi-test", "boolean_feature", baseline);

        // WHEN: Observations: 60% yes, 40% no (opposite distribution)
        for (int i = 0; i < 100; i++) {
            if (i < 60)
                driftDetector.recordObservation("psi-test", "boolean_feature", "yes");
            else
                driftDetector.recordObservation("psi-test", "boolean_feature", "no");
        }

        double psi = driftDetector.calculatePSI("psi-test", "boolean_feature");

        // Manual calculation:
        // PSI = (0.6-0.3)*ln(0.6/0.3) + (0.4-0.7)*ln(0.4/0.7)
        // = 0.3*ln(2) + (-0.3)*ln(0.571)
        // = 0.3*0.693 + (-0.3)*(-0.559)
        // = 0.208 + 0.168 = 0.376

        assertThat(psi)
                .as("PSI for opposite distribution (0.6 vs 0.3, 0.4 vs 0.7)")
                .isGreaterThan(0.35)
                .isLessThan(0.40);
    }

    /**
     * Verifies no drift alert when baseline undefined.
     *
     * GIVEN: Drift detector with no baseline set
     * WHEN: Observations recorded and PSI calculated
     * THEN: Returns zero (graceful null handling)
     */
    @Test
    @DisplayName("Should handle missing baseline gracefully")
    void shouldHandleMissingBaselineGracefully() {
        // GIVEN: No baseline set
        // WHEN: Record observations without baseline
        for (int i = 0; i < 100; i++) {
            driftDetector.recordObservation("missing-baseline", "feature", "value");
        }

        // WHEN: Calculate PSI
        double psi = driftDetector.calculatePSI("missing-baseline", "feature");

        // THEN: Return zero (no baseline to compare against)
        assertThat(psi)
                .as("PSI with missing baseline should be zero")
                .isEqualTo(0.0);
    }
}
