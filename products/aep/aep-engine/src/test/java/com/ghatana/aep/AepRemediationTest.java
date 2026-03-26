package com.ghatana.aep;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Focused regression tests for audit remediations implemented in the AEP engine.
 *
 * @doc.type class
 * @doc.purpose Verify consent gating, identity stitching, pattern matching, and anomaly threshold configuration
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Aep audit remediations")
class AepRemediationTest extends EventloopTestBase {

    private static final String TENANT_ID = "tenant-a";

    private AepEngine engine;

    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.close();
        }
    }

    @Test
    @DisplayName("process() applies threshold pattern matching")
    void shouldDetectThresholdPattern() {
        engine = Aep.forTesting();
        AepEngine.Pattern pattern = runPromise(() -> engine.registerPattern(
            TENANT_ID,
            new AepEngine.PatternDefinition(
                "High value",
                "Detect value threshold exceedance",
                AepEngine.PatternType.THRESHOLD,
                Map.of("field", "value", "threshold", 50.0)
            )
        ));

        AepEngine.ProcessingResult result = runPromise(() -> engine.process(
            TENANT_ID,
            new AepEngine.Event("sensor.metric", Map.of("value", 75.0), Map.of(), Instant.now())
        ));

        assertThat(result.success()).isTrue();
        assertThat(result.detections()).hasSize(1);
        assertThat(result.detections().get(0).patternId()).isEqualTo(pattern.id());
    }

    @Test
    @DisplayName("process() rejects events with denied consent")
    void shouldSkipDeniedConsent() {
        engine = Aep.forTesting();

        AepEngine.ProcessingResult result = runPromise(() -> engine.process(
            TENANT_ID,
            new AepEngine.Event(
                "profile.update",
                Map.of("consentStatus", "DENIED", "allowedPurposes", List.of("event_processing")),
                Map.of(),
                Instant.now()
            )
        ));

        assertThat(result.success()).isFalse();
        assertThat(result.metadata()).containsEntry("skipped", true);
        assertThat(result.metadata()).containsEntry("reason", "Event rejected by consent policy");
    }

    @Test
    @DisplayName("process() derives stitched identity from headers and payload")
    void shouldResolveStitchedIdentity() {
        engine = Aep.forTesting();

        AepEngine.ProcessingResult result = runPromise(() -> engine.process(
            TENANT_ID,
            new AepEngine.Event(
                "session.start",
                Map.of("anonymousId", "anon-99", "sessionId", "sess-1"),
                Map.of("x-user-id", "user-7"),
                Instant.now()
            )
        ));

        assertThat(result.success()).isTrue();
        assertThat(result.metadata()).containsEntry("stitchedId", "user-7");
    }

    @Test
    @DisplayName("process() completes a configured sequence pattern")
    void shouldDetectSequencePattern() {
        engine = Aep.forTesting();
        runPromise(() -> engine.registerPattern(
            TENANT_ID,
            new AepEngine.PatternDefinition(
                "Login then purchase",
                "Detect two-step conversion sequence",
                AepEngine.PatternType.SEQUENCE,
                Map.of("expectedTypes", List.of("user.login", "order.completed"), "correlationField", "userId")
            )
        ));

        runPromise(() -> engine.process(
            TENANT_ID,
            new AepEngine.Event("user.login", Map.of("userId", "u-1"), Map.of(), Instant.now())
        ));

        AepEngine.ProcessingResult result = runPromise(() -> engine.process(
            TENANT_ID,
            new AepEngine.Event("order.completed", Map.of("userId", "u-1"), Map.of(), Instant.now())
        ));

        assertThat(result.detections()).hasSize(1);
        assertThat(result.detections().get(0).patternName()).isEqualTo("Login then purchase");
    }

    @Test
    @DisplayName("detectAnomalies() honors configurable anomaly threshold")
    void shouldUseConfiguredAnomalyThreshold() {
        engine = Aep.create(Aep.AepConfig.builder().anomalyThreshold(0.5).build());

        List<AepEngine.Anomaly> anomalies = runPromise(() -> engine.detectAnomalies(
            TENANT_ID,
            List.of(new AepEngine.Event("sensor.cpu", Map.of("anomaly_score", 0.6), Map.of(), Instant.now()))
        ));

        assertThat(anomalies).hasSize(1);
        assertThat(anomalies.get(0).score()).isEqualTo(0.6);
    }
}