package com.ghatana.aep;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Focused regression tests for audit remediations implemented in the AEP engine.
 *
 * <p>Covers:
 * <ul>
 *   <li>Consent gating and identity stitching</li>
 *   <li>Pattern matching (THRESHOLD, SEQUENCE)</li> // GH-90000
 *   <li>Anomaly threshold configuration</li>
 *   <li>Event schema validation (AEP-002)</li> // GH-90000
 *   <li>Configuration validation - fail-fast (AEP-004)</li> // GH-90000
 *   <li>Event versioning (ECR-003)</li> // GH-90000
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Verify consent gating, identity stitching, pattern matching, schema validation, config
 *             validation, and event versioning
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Aep audit remediations")
class AepRemediationTest extends EventloopTestBase {

    private static final String TENANT_ID = "tenant-a";

    private AepEngine engine;

    @AfterEach
    void tearDown() { // GH-90000
        if (engine != null) { // GH-90000
            engine.close(); // GH-90000
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Pattern matching
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Pattern matching")
    class PatternMatchingTests {

        @Test
        @DisplayName("process() applies threshold pattern matching")
        void shouldDetectThresholdPattern() { // GH-90000
            engine = Aep.forTesting(); // GH-90000
            AepEngine.Pattern pattern = runPromise(() -> engine.registerPattern( // GH-90000
                TENANT_ID,
                new AepEngine.PatternDefinition( // GH-90000
                    "High value",
                    "Detect value threshold exceedance",
                    AepEngine.PatternType.THRESHOLD,
                    Map.of("field", "value", "threshold", 50.0) // GH-90000
                )
            ));

            AepEngine.ProcessingResult result = runPromise(() -> engine.process( // GH-90000
                TENANT_ID,
                new AepEngine.Event("sensor.metric", Map.of("value", 75.0), Map.of(), Instant.now()) // GH-90000
            ));

            assertThat(result.success()).isTrue(); // GH-90000
            assertThat(result.detections()).hasSize(1); // GH-90000
            assertThat(result.detections().get(0).patternId()).isEqualTo(pattern.id()); // GH-90000
        }

        @Test
        @DisplayName("process() completes a configured sequence pattern")
        void shouldDetectSequencePattern() { // GH-90000
            engine = Aep.forTesting(); // GH-90000
            runPromise(() -> engine.registerPattern( // GH-90000
                TENANT_ID,
                new AepEngine.PatternDefinition( // GH-90000
                    "Login then purchase",
                    "Detect two-step conversion sequence",
                    AepEngine.PatternType.SEQUENCE,
                    Map.of("expectedTypes", List.of("user.login", "order.completed"), // GH-90000
                           "correlationField", "userId")
                )
            ));

            runPromise(() -> engine.process( // GH-90000
                TENANT_ID,
                new AepEngine.Event("user.login", Map.of("userId", "u-1"), Map.of(), Instant.now()) // GH-90000
            ));

            AepEngine.ProcessingResult result = runPromise(() -> engine.process( // GH-90000
                TENANT_ID,
                new AepEngine.Event("order.completed", Map.of("userId", "u-1"), Map.of(), Instant.now()) // GH-90000
            ));

            assertThat(result.detections()).hasSize(1); // GH-90000
            assertThat(result.detections().get(0).patternName()).isEqualTo("Login then purchase");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Consent and identity
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Consent and identity")
    class ConsentAndIdentityTests {

        @Test
        @DisplayName("process() rejects events with denied consent")
        void shouldSkipDeniedConsent() { // GH-90000
            engine = Aep.forTesting(); // GH-90000

            AepEngine.ProcessingResult result = runPromise(() -> engine.process( // GH-90000
                TENANT_ID,
                new AepEngine.Event( // GH-90000
                    "profile.update",
                    Map.of("consentStatus", "DENIED", "allowedPurposes", List.of("event_processing")),
                    Map.of(), // GH-90000
                    Instant.now() // GH-90000
                )
            ));

            assertThat(result.success()).isFalse(); // GH-90000
            assertThat(result.metadata()).containsEntry("skipped", true); // GH-90000
            assertThat(result.metadata()).containsEntry("reason", "Event rejected by consent policy"); // GH-90000
        }

        @Test
        @DisplayName("process() derives stitched identity from headers and payload")
        void shouldResolveStitchedIdentity() { // GH-90000
            engine = Aep.forTesting(); // GH-90000

            AepEngine.ProcessingResult result = runPromise(() -> engine.process( // GH-90000
                TENANT_ID,
                new AepEngine.Event( // GH-90000
                    "session.start",
                    Map.of("anonymousId", "anon-99", "sessionId", "sess-1"), // GH-90000
                    Map.of("x-user-id", "user-7"), // GH-90000
                    Instant.now() // GH-90000
                )
            ));

            assertThat(result.success()).isTrue(); // GH-90000
            assertThat(result.metadata()).containsEntry("stitchedId", "user-7"); // GH-90000
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Anomaly detection
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Anomaly detection")
    class AnomalyDetectionTests {

        @Test
        @DisplayName("detectAnomalies() honors configurable anomaly threshold")
        void shouldUseConfiguredAnomalyThreshold() { // GH-90000
            engine = Aep.create(Aep.AepConfig.builder().anomalyThreshold(0.5).build()); // GH-90000

            List<AepEngine.Anomaly> anomalies = runPromise(() -> engine.detectAnomalies( // GH-90000
                TENANT_ID,
                List.of(new AepEngine.Event("sensor.cpu", Map.of("anomaly_score", 0.6), Map.of(), Instant.now())) // GH-90000
            ));

            assertThat(anomalies).hasSize(1); // GH-90000
            assertThat(anomalies.get(0).score()).isEqualTo(0.6); // GH-90000
        }

        @Test
        @DisplayName("detectAnomalies() ignores events below threshold")
        void shouldIgnoreEventsBelowThreshold() { // GH-90000
            engine = Aep.create(Aep.AepConfig.builder().anomalyThreshold(0.8).build()); // GH-90000

            List<AepEngine.Anomaly> anomalies = runPromise(() -> engine.detectAnomalies( // GH-90000
                TENANT_ID,
                List.of(new AepEngine.Event("sensor.cpu", Map.of("anomaly_score", 0.5), Map.of(), Instant.now())) // GH-90000
            ));

            assertThat(anomalies).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("detectAnomalies() uses model-backed scoring for numeric series without explicit score")
        void shouldUseModelBackedScoringForNumericSeries() { // GH-90000
            engine = Aep.forTesting(); // GH-90000

            List<AepEngine.Anomaly> anomalies = runPromise(() -> engine.detectAnomalies( // GH-90000
                TENANT_ID,
                List.of( // GH-90000
                    new AepEngine.Event("sensor.cpu", Map.of("value", 10.0), Map.of(), Instant.now()), // GH-90000
                    new AepEngine.Event("sensor.cpu", Map.of("value", 10.1), Map.of(), Instant.now()), // GH-90000
                    new AepEngine.Event("sensor.cpu", Map.of("value", 9.9), Map.of(), Instant.now()), // GH-90000
                    new AepEngine.Event("sensor.cpu", Map.of("value", 10.0), Map.of(), Instant.now()), // GH-90000
                    new AepEngine.Event("sensor.cpu", Map.of("value", 10.2), Map.of(), Instant.now()), // GH-90000
                    new AepEngine.Event("sensor.cpu", Map.of("value", 9.8), Map.of(), Instant.now()), // GH-90000
                    new AepEngine.Event("sensor.cpu", Map.of("value", 10.1), Map.of(), Instant.now()), // GH-90000
                    new AepEngine.Event("sensor.cpu", Map.of("value", 10.0), Map.of(), Instant.now()), // GH-90000
                    new AepEngine.Event("sensor.cpu", Map.of("value", 9.9), Map.of(), Instant.now()), // GH-90000
                    new AepEngine.Event("sensor.cpu", Map.of("value", 10.3), Map.of(), Instant.now()), // GH-90000
                    new AepEngine.Event("sensor.cpu", Map.of("value", 75.0), Map.of(), Instant.now()) // GH-90000
                )
            ));

            assertThat(anomalies).hasSize(1); // GH-90000
            assertThat(anomalies.get(0).anomalyType()).isEqualTo("MODEL_DETECTED");
            assertThat(anomalies.get(0).score()).isGreaterThan(3.0); // GH-90000
            assertThat(anomalies.get(0).details()) // GH-90000
                .containsEntry("event_type", "sensor.cpu") // GH-90000
                .containsEntry("detector", "z-score"); // GH-90000
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Event schema validation (AEP-002) // GH-90000
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Event schema validation (AEP-002)")
    class SchemaValidationTests {

        @Test
        @DisplayName("EventSchemaValidator rejects null event")
        void shouldRejectNullEvent() { // GH-90000
            EventSchemaValidator validator = new EventSchemaValidator(); // GH-90000
            EventSchemaValidator.ValidationResult result = validator.validate(null); // GH-90000
            assertThat(result.isValid()).isFalse(); // GH-90000
            assertThat(result.firstError()).contains("null");
            assertThat(result.firstDetail()).isNotNull(); // GH-90000
            assertThat(result.firstDetail().code()).isEqualTo(EventSchemaValidator.ErrorCode.MALFORMED); // GH-90000
            assertThat(result.firstDetail().field()).isEqualTo("event");
        }

        @Test
        @DisplayName("EventSchemaValidator rejects event with oversized type")
        void shouldRejectOversizedType() { // GH-90000
            EventSchemaValidator validator = new EventSchemaValidator(); // GH-90000
            String longType = "a".repeat(257); // GH-90000
            AepEngine.Event event = new AepEngine.Event(longType, Map.of(), Map.of(), Instant.now()); // GH-90000
            EventSchemaValidator.ValidationResult result = validator.validate(event); // GH-90000
            assertThat(result.isValid()).isFalse(); // GH-90000
            assertThat(result.summary()).contains("maximum length");
            assertThat(result.hasCode(EventSchemaValidator.ErrorCode.SIZE_EXCEEDED)).isTrue(); // GH-90000
            assertThat(result.details()) // GH-90000
                .anySatisfy(detail -> { // GH-90000
                    assertThat(detail.field()).isEqualTo("event.type");
                    assertThat(detail.code()).isEqualTo(EventSchemaValidator.ErrorCode.SIZE_EXCEEDED); // GH-90000
                });
        }

        @Test
        @DisplayName("EventSchemaValidator accepts a well-formed event")
        void shouldAcceptWellFormedEvent() { // GH-90000
            EventSchemaValidator validator = new EventSchemaValidator(); // GH-90000
            AepEngine.Event event = new AepEngine.Event( // GH-90000
                "user.clicked",
                Map.of("buttonId", "buy-now"), // GH-90000
                Map.of("x-tenant-id", "t1"), // GH-90000
                Instant.now() // GH-90000
            );
            EventSchemaValidator.ValidationResult result = validator.validate(event); // GH-90000
            assertThat(result.isValid()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("process() short-circuits on invalid event schema")
        void shouldShortCircuitOnInvalidSchema() { // GH-90000
            engine = Aep.forTesting(); // GH-90000
            String oversizedType = "x".repeat(300); // GH-90000
            AepEngine.Event invalid = new AepEngine.Event(oversizedType, Map.of(), Map.of(), Instant.now()); // GH-90000

            AepEngine.ProcessingResult result = runPromise(() -> engine.process(TENANT_ID, invalid)); // GH-90000

            assertThat(result.success()).isFalse(); // GH-90000
            assertThat(result.metadata()).containsEntry("failed", true); // GH-90000
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Configuration validation (AEP-004) // GH-90000
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Configuration validation (AEP-004)")
    class ConfigValidationTests {

        @Test
        @DisplayName("Aep.create() throws on zero anomalyThreshold")
        void shouldThrowOnZeroAnomalyThreshold() { // GH-90000
            // AepConfig compact constructor silently clamps 0.0 to 0.9;
            // AepConfigValidator detects the violation before engine creation.
            assertThatThrownBy(() -> Aep.create( // GH-90000
                new Aep.AepConfig("id", 1, 100, false, false, 0.0, Map.of()) // GH-90000
            ))
            .isInstanceOf(IllegalArgumentException.class) // GH-90000
            .hasMessageContaining("anomalyThreshold");
        }

        @Test
        @DisplayName("Aep.create() throws when maxPipelinesPerTenant exceeds 10 000")
        void shouldThrowOnExcessivePipelineLimit() { // GH-90000
            assertThatThrownBy(() -> Aep.create( // GH-90000
                new Aep.AepConfig("id", 1, 20_000, false, false, 0.9, Map.of()) // GH-90000
            ))
            .isInstanceOf(IllegalArgumentException.class) // GH-90000
            .hasMessageContaining("maxPipelinesPerTenant");
        }

        @Test
        @DisplayName("Aep.create() succeeds with valid configuration")
        void shouldSucceedWithValidConfig() { // GH-90000
            AepEngine eng = Aep.create(Aep.AepConfig.builder() // GH-90000
                .instanceId("my-instance")
                .workerThreads(2) // GH-90000
                .maxPipelinesPerTenant(500) // GH-90000
                .anomalyThreshold(0.7) // GH-90000
                .build()); // GH-90000
            assertThat(eng).isNotNull(); // GH-90000
            eng.close(); // GH-90000
        }

        @Test
        @DisplayName("Aep.create() uses configured consent provider when available")
        void shouldUseConfiguredConsentProvider() { // GH-90000
            AepEngine eng = Aep.create(Aep.AepConfig.builder() // GH-90000
                .consentProvider("deny-all-test")
                .build()); // GH-90000
            try {
                AepEngine.ProcessingResult result = runPromise(() -> eng.process( // GH-90000
                    TENANT_ID,
                    AepEngine.Event.of("user.clicked", Map.of("buttonId", "buy")))); // GH-90000

                assertThat(result.success()).isFalse(); // GH-90000
                assertThat(result.metadata()).containsEntry("skipped", true); // GH-90000
                assertThat(result.metadata()).containsEntry("reason", "Event rejected by consent policy"); // GH-90000
            } finally {
                eng.close(); // GH-90000
            }
        }

        @Test
        @DisplayName("Aep.create() falls back to default consent provider when configured provider is missing")
        void shouldFallbackWhenConsentProviderMissing() { // GH-90000
            AepEngine eng = Aep.create(Aep.AepConfig.builder() // GH-90000
                .consentProvider("missing-provider")
                .build()); // GH-90000
            try {
                AepEngine.ProcessingResult result = runPromise(() -> eng.process( // GH-90000
                    TENANT_ID,
                    AepEngine.Event.of("user.clicked", Map.of("buttonId", "buy")))); // GH-90000

                assertThat(result.success()).isTrue(); // GH-90000
            } finally {
                eng.close(); // GH-90000
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Event versioning (ECR-003) // GH-90000
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Event versioning (ECR-003)")
    class EventVersioningTests {

        @Test
        @DisplayName("Event defaults to version 1.0 when not specified")
        void shouldDefaultToVersion1() { // GH-90000
            AepEngine.Event event = new AepEngine.Event("test.event", Map.of(), Map.of(), Instant.now()); // GH-90000
            assertThat(event.version()).isEqualTo(AepEngine.Event.DEFAULT_VERSION); // GH-90000
        }

        @Test
        @DisplayName("withVersion() creates a copy with the specified version")
        void shouldUpdateVersionWithCopy() { // GH-90000
            AepEngine.Event v1 = AepEngine.Event.of("test.event", Map.of()); // GH-90000
            AepEngine.Event v2 = v1.withVersion("2.0");

            assertThat(v1.version()).isEqualTo("1.0");
            assertThat(v2.version()).isEqualTo("2.0");
            assertThat(v2.type()).isEqualTo(v1.type()); // GH-90000
        }

        @Test
        @DisplayName("Event.of() factory also defaults to version 1.0")
        void factoryShouldDefaultToVersion1() { // GH-90000
            AepEngine.Event event = AepEngine.Event.of("payment.received", Map.of("amount", 100)); // GH-90000
            assertThat(event.version()).isEqualTo("1.0");
        }

        @Test
        @DisplayName("Validator accepts events with non-default version")
        void shouldAcceptNonDefaultVersion() { // GH-90000
            EventSchemaValidator validator = new EventSchemaValidator(); // GH-90000
            AepEngine.Event v2Event = AepEngine.Event.of("payment.received", Map.of()) // GH-90000
                .withVersion("2.0");
            EventSchemaValidator.ValidationResult result = validator.validate(v2Event); // GH-90000
            assertThat(result.isValid()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("process() succeeds with versioned event")
        void shouldProcessVersionedEvent() { // GH-90000
            engine = Aep.forTesting(); // GH-90000
            AepEngine.Event versioned = AepEngine.Event.of("user.action", Map.of("action", "click")) // GH-90000
                .withVersion("2.1");

            AepEngine.ProcessingResult result = runPromise(() -> engine.process(TENANT_ID, versioned)); // GH-90000

            assertThat(result.success()).isTrue(); // GH-90000
        }
    }
}
