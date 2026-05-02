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
 *   <li>Pattern matching (THRESHOLD, SEQUENCE)</li> 
 *   <li>Anomaly threshold configuration</li>
 *   <li>Event schema validation (AEP-002)</li> 
 *   <li>Configuration validation - fail-fast (AEP-004)</li> 
 *   <li>Event versioning (ECR-003)</li> 
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
    void tearDown() { 
        if (engine != null) { 
            engine.close(); 
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
        @DisplayName("process() completes a configured sequence pattern")
        void shouldDetectSequencePattern() { 
            engine = Aep.forTesting(); 
            runPromise(() -> engine.registerPattern( 
                TENANT_ID,
                new AepEngine.PatternDefinition( 
                    "Login then purchase",
                    "Detect two-step conversion sequence",
                    AepEngine.PatternType.SEQUENCE,
                    Map.of("expectedTypes", List.of("user.login", "order.completed"), 
                           "correlationField", "userId")
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
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Consent and identity
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Consent and identity")
    class ConsentAndIdentityTests {

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
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Anomaly detection
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Anomaly detection")
    class AnomalyDetectionTests {

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

        @Test
        @DisplayName("detectAnomalies() ignores events below threshold")
        void shouldIgnoreEventsBelowThreshold() { 
            engine = Aep.create(Aep.AepConfig.builder().anomalyThreshold(0.8).build()); 

            List<AepEngine.Anomaly> anomalies = runPromise(() -> engine.detectAnomalies( 
                TENANT_ID,
                List.of(new AepEngine.Event("sensor.cpu", Map.of("anomaly_score", 0.5), Map.of(), Instant.now())) 
            ));

            assertThat(anomalies).isEmpty(); 
        }

        @Test
        @DisplayName("detectAnomalies() uses model-backed scoring for numeric series without explicit score")
        void shouldUseModelBackedScoringForNumericSeries() { 
            engine = Aep.forTesting(); 

            List<AepEngine.Anomaly> anomalies = runPromise(() -> engine.detectAnomalies( 
                TENANT_ID,
                List.of( 
                    new AepEngine.Event("sensor.cpu", Map.of("value", 10.0), Map.of(), Instant.now()), 
                    new AepEngine.Event("sensor.cpu", Map.of("value", 10.1), Map.of(), Instant.now()), 
                    new AepEngine.Event("sensor.cpu", Map.of("value", 9.9), Map.of(), Instant.now()), 
                    new AepEngine.Event("sensor.cpu", Map.of("value", 10.0), Map.of(), Instant.now()), 
                    new AepEngine.Event("sensor.cpu", Map.of("value", 10.2), Map.of(), Instant.now()), 
                    new AepEngine.Event("sensor.cpu", Map.of("value", 9.8), Map.of(), Instant.now()), 
                    new AepEngine.Event("sensor.cpu", Map.of("value", 10.1), Map.of(), Instant.now()), 
                    new AepEngine.Event("sensor.cpu", Map.of("value", 10.0), Map.of(), Instant.now()), 
                    new AepEngine.Event("sensor.cpu", Map.of("value", 9.9), Map.of(), Instant.now()), 
                    new AepEngine.Event("sensor.cpu", Map.of("value", 10.3), Map.of(), Instant.now()), 
                    new AepEngine.Event("sensor.cpu", Map.of("value", 75.0), Map.of(), Instant.now()) 
                )
            ));

            assertThat(anomalies).hasSize(1); 
            assertThat(anomalies.get(0).anomalyType()).isEqualTo("MODEL_DETECTED");
            assertThat(anomalies.get(0).score()).isGreaterThan(3.0); 
            assertThat(anomalies.get(0).details()) 
                .containsEntry("event_type", "sensor.cpu") 
                .containsEntry("detector", "z-score"); 
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Event schema validation (AEP-002) 
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Event schema validation (AEP-002)")
    class SchemaValidationTests {

        @Test
        @DisplayName("EventSchemaValidator rejects null event")
        void shouldRejectNullEvent() { 
            EventSchemaValidator validator = new EventSchemaValidator(); 
            EventSchemaValidator.ValidationResult result = validator.validate(null); 
            assertThat(result.isValid()).isFalse(); 
            assertThat(result.firstError()).contains("null");
            assertThat(result.firstDetail()).isNotNull(); 
            assertThat(result.firstDetail().code()).isEqualTo(EventSchemaValidator.ErrorCode.MALFORMED); 
            assertThat(result.firstDetail().field()).isEqualTo("event");
        }

        @Test
        @DisplayName("EventSchemaValidator rejects event with oversized type")
        void shouldRejectOversizedType() { 
            EventSchemaValidator validator = new EventSchemaValidator(); 
            String longType = "a".repeat(257); 
            AepEngine.Event event = new AepEngine.Event(longType, Map.of(), Map.of(), Instant.now()); 
            EventSchemaValidator.ValidationResult result = validator.validate(event); 
            assertThat(result.isValid()).isFalse(); 
            assertThat(result.summary()).contains("maximum length");
            assertThat(result.hasCode(EventSchemaValidator.ErrorCode.SIZE_EXCEEDED)).isTrue(); 
            assertThat(result.details()) 
                .anySatisfy(detail -> { 
                    assertThat(detail.field()).isEqualTo("event.type");
                    assertThat(detail.code()).isEqualTo(EventSchemaValidator.ErrorCode.SIZE_EXCEEDED); 
                });
        }

        @Test
        @DisplayName("EventSchemaValidator accepts a well-formed event")
        void shouldAcceptWellFormedEvent() { 
            EventSchemaValidator validator = new EventSchemaValidator(); 
            AepEngine.Event event = new AepEngine.Event( 
                "user.clicked",
                Map.of("buttonId", "buy-now"), 
                Map.of("x-tenant-id", "t1"), 
                Instant.now() 
            );
            EventSchemaValidator.ValidationResult result = validator.validate(event); 
            assertThat(result.isValid()).isTrue(); 
        }

        @Test
        @DisplayName("process() short-circuits on invalid event schema")
        void shouldShortCircuitOnInvalidSchema() { 
            engine = Aep.forTesting(); 
            String oversizedType = "x".repeat(300); 
            AepEngine.Event invalid = new AepEngine.Event(oversizedType, Map.of(), Map.of(), Instant.now()); 

            AepEngine.ProcessingResult result = runPromise(() -> engine.process(TENANT_ID, invalid)); 

            assertThat(result.success()).isFalse(); 
            assertThat(result.metadata()).containsEntry("failed", true); 
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Configuration validation (AEP-004) 
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Configuration validation (AEP-004)")
    class ConfigValidationTests {

        @Test
        @DisplayName("Aep.create() throws on zero anomalyThreshold")
        void shouldThrowOnZeroAnomalyThreshold() { 
            // AepConfig compact constructor silently clamps 0.0 to 0.9;
            // AepConfigValidator detects the violation before engine creation.
            assertThatThrownBy(() -> Aep.create( 
                new Aep.AepConfig("id", 1, 100, false, false, 0.0, Map.of()) 
            ))
            .isInstanceOf(IllegalArgumentException.class) 
            .hasMessageContaining("anomalyThreshold");
        }

        @Test
        @DisplayName("Aep.create() throws when maxPipelinesPerTenant exceeds 10 000")
        void shouldThrowOnExcessivePipelineLimit() { 
            assertThatThrownBy(() -> Aep.create( 
                new Aep.AepConfig("id", 1, 20_000, false, false, 0.9, Map.of()) 
            ))
            .isInstanceOf(IllegalArgumentException.class) 
            .hasMessageContaining("maxPipelinesPerTenant");
        }

        @Test
        @DisplayName("Aep.create() succeeds with valid configuration")
        void shouldSucceedWithValidConfig() { 
            AepEngine eng = Aep.create(Aep.AepConfig.builder() 
                .instanceId("my-instance")
                .workerThreads(2) 
                .maxPipelinesPerTenant(500) 
                .anomalyThreshold(0.7) 
                .build()); 
            assertThat(eng).isNotNull(); 
            eng.close(); 
        }

        @Test
        @DisplayName("Aep.create() uses configured consent provider when available")
        void shouldUseConfiguredConsentProvider() { 
            AepEngine eng = Aep.create(Aep.AepConfig.builder() 
                .consentProvider("deny-all-test")
                .build()); 
            try {
                AepEngine.ProcessingResult result = runPromise(() -> eng.process( 
                    TENANT_ID,
                    AepEngine.Event.of("user.clicked", Map.of("buttonId", "buy")))); 

                assertThat(result.success()).isFalse(); 
                assertThat(result.metadata()).containsEntry("skipped", true); 
                assertThat(result.metadata()).containsEntry("reason", "Event rejected by consent policy"); 
            } finally {
                eng.close(); 
            }
        }

        @Test
        @DisplayName("Aep.create() falls back to default consent provider when configured provider is missing")
        void shouldFallbackWhenConsentProviderMissing() { 
            AepEngine eng = Aep.create(Aep.AepConfig.builder() 
                .consentProvider("missing-provider")
                .build()); 
            try {
                AepEngine.ProcessingResult result = runPromise(() -> eng.process( 
                    TENANT_ID,
                    AepEngine.Event.of("user.clicked", Map.of("buttonId", "buy")))); 

                assertThat(result.success()).isTrue(); 
            } finally {
                eng.close(); 
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Event versioning (ECR-003) 
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Event versioning (ECR-003)")
    class EventVersioningTests {

        @Test
        @DisplayName("Event defaults to version 1.0 when not specified")
        void shouldDefaultToVersion1() { 
            AepEngine.Event event = new AepEngine.Event("test.event", Map.of(), Map.of(), Instant.now()); 
            assertThat(event.version()).isEqualTo(AepEngine.Event.DEFAULT_VERSION); 
        }

        @Test
        @DisplayName("withVersion() creates a copy with the specified version")
        void shouldUpdateVersionWithCopy() { 
            AepEngine.Event v1 = AepEngine.Event.of("test.event", Map.of()); 
            AepEngine.Event v2 = v1.withVersion("2.0");

            assertThat(v1.version()).isEqualTo("1.0");
            assertThat(v2.version()).isEqualTo("2.0");
            assertThat(v2.type()).isEqualTo(v1.type()); 
        }

        @Test
        @DisplayName("Event.of() factory also defaults to version 1.0")
        void factoryShouldDefaultToVersion1() { 
            AepEngine.Event event = AepEngine.Event.of("payment.received", Map.of("amount", 100)); 
            assertThat(event.version()).isEqualTo("1.0");
        }

        @Test
        @DisplayName("Validator accepts events with non-default version")
        void shouldAcceptNonDefaultVersion() { 
            EventSchemaValidator validator = new EventSchemaValidator(); 
            AepEngine.Event v2Event = AepEngine.Event.of("payment.received", Map.of()) 
                .withVersion("2.0");
            EventSchemaValidator.ValidationResult result = validator.validate(v2Event); 
            assertThat(result.isValid()).isTrue(); 
        }

        @Test
        @DisplayName("process() succeeds with versioned event")
        void shouldProcessVersionedEvent() { 
            engine = Aep.forTesting(); 
            AepEngine.Event versioned = AepEngine.Event.of("user.action", Map.of("action", "click")) 
                .withVersion("2.1");

            AepEngine.ProcessingResult result = runPromise(() -> engine.process(TENANT_ID, versioned)); 

            assertThat(result.success()).isTrue(); 
        }
    }
}
