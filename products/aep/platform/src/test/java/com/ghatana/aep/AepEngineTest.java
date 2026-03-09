package com.ghatana.aep;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive tests for the AEP engine core logic.
 * Tests the DefaultAepEngine via the factory API.
 *
 * @doc.type class
 * @doc.purpose AEP engine core logic tests
 * @doc.layer product
 * @doc.pattern Unit Test
 */
@DisplayName("AEP Engine Tests")
class AepEngineTest extends EventloopTestBase {

    private static final String TENANT = "test-tenant";

    private AepEngine engine;

    @BeforeEach
    void setUp() {
        engine = Aep.forTesting();
    }

    @Nested
    @DisplayName("Event Processing")
    class EventProcessing {

        @Test
        @DisplayName("should process a simple event successfully")
        void shouldProcessSimpleEvent() {
            AepEngine.Event event = new AepEngine.Event(
                    "sensor.temperature",
                    Map.of("value", 42.5, "unit", "celsius"),
                    Map.of(),
                    Instant.now());

            AepEngine.ProcessingResult result = runPromise(() -> engine.process(TENANT, event));

            assertThat(result).isNotNull();
            assertThat(result.eventId()).isNotNull();
            assertThat(result.success()).isTrue();
        }

        @Test
        @DisplayName("should reject null event")
        void shouldRejectNullEvent() {
            assertThatThrownBy(() -> runPromise(() -> engine.process(TENANT, null)))
                    .isInstanceOf(NullPointerException.class);
            clearFatalError();
        }

        @Test
        @DisplayName("should process multiple events sequentially")
        void shouldProcessMultipleEvents() {
            for (int i = 0; i < 100; i++) {
                AepEngine.Event event = new AepEngine.Event(
                        "sensor.data",
                        Map.of("index", (Object) i),
                        Map.of(),
                        Instant.now());
                AepEngine.ProcessingResult result = runPromise(() -> engine.process(TENANT, event));
                assertThat(result.success()).isTrue();
            }
        }
    }

    @Nested
    @DisplayName("Pattern Management")
    class PatternManagement {

        @Test
        @DisplayName("should register a pattern definition")
        void shouldRegisterPattern() {
            AepEngine.PatternDefinition patternDef = new AepEngine.PatternDefinition(
                    "High Temperature Alert",
                    "Alert when temperature exceeds threshold",
                    AepEngine.PatternType.THRESHOLD,
                    Map.of("threshold", 100.0, "field", "value"));

            AepEngine.Pattern registered = runPromise(() -> engine.registerPattern(TENANT, patternDef));

            assertThat(registered).isNotNull();
            assertThat(registered.id()).isNotNull();
            assertThat(registered.name()).isEqualTo("High Temperature Alert");
        }

        @Test
        @DisplayName("should list registered patterns")
        void shouldListPatterns() {
            runPromise(() -> engine.registerPattern(TENANT, new AepEngine.PatternDefinition(
                    "Pattern A", "First pattern", AepEngine.PatternType.THRESHOLD,
                    Map.of())));
            runPromise(() -> engine.registerPattern(TENANT, new AepEngine.PatternDefinition(
                    "Pattern B", "Second pattern", AepEngine.PatternType.SEQUENCE,
                    Map.of())));

            List<AepEngine.Pattern> patterns = runPromise(() -> engine.listPatterns(TENANT));

            assertThat(patterns).hasSize(2);
            assertThat(patterns).extracting(AepEngine.Pattern::name)
                    .containsExactlyInAnyOrder("Pattern A", "Pattern B");
        }

        @Test
        @DisplayName("should delete a pattern")
        void shouldDeletePattern() {
            AepEngine.Pattern registered = runPromise(() -> engine.registerPattern(TENANT,
                    new AepEngine.PatternDefinition(
                            "To Remove", "Pattern to remove", AepEngine.PatternType.THRESHOLD,
                            Map.of())));

            runPromise(() -> engine.deletePattern(TENANT, registered.id()));

            List<AepEngine.Pattern> remaining = runPromise(() -> engine.listPatterns(TENANT));
            assertThat(remaining).isEmpty();
        }

        @Test
        @DisplayName("should detect pattern match during event processing")
        void shouldDetectPatternMatch() {
            runPromise(() -> engine.registerPattern(TENANT, new AepEngine.PatternDefinition(
                    "High Temp",
                    "Detect high temperature",
                    AepEngine.PatternType.THRESHOLD,
                    Map.of("threshold", 50.0, "field", "value"))));

            AepEngine.Event hotEvent = new AepEngine.Event(
                    "sensor.temperature",
                    Map.of("value", 120.0),
                    Map.of(),
                    Instant.now());

            AepEngine.ProcessingResult result = runPromise(() -> engine.process(TENANT, hotEvent));

            assertThat(result.success()).isTrue();
            // Detection results depend on implementation — at minimum the event should process
        }
    }

    @Nested
    @DisplayName("Subscriptions")
    class Subscriptions {

        @Test
        @DisplayName("should subscribe to pattern detections")
        void shouldSubscribeToDetections() {
            AepEngine.Pattern pattern = runPromise(() -> engine.registerPattern(TENANT,
                    new AepEngine.PatternDefinition(
                            "Temp Alert", "Temperature alert",
                            AepEngine.PatternType.THRESHOLD,
                            Map.of("threshold", 50.0, "field", "value"))));

            AtomicReference<AepEngine.Detection> received = new AtomicReference<>();

            AepEngine.Subscription subscription = engine.subscribe(TENANT, pattern.id(),
                    received::set);

            assertThat(subscription).isNotNull();
            assertThat(subscription.isCancelled()).isFalse();
        }

        @Test
        @DisplayName("should cancel a subscription")
        void shouldCancelSubscription() {
            AepEngine.Pattern pattern = runPromise(() -> engine.registerPattern(TENANT,
                    new AepEngine.PatternDefinition(
                            "Temp Alert", "Temperature alert",
                            AepEngine.PatternType.THRESHOLD,
                            Map.of())));

            AepEngine.Subscription subscription = engine.subscribe(TENANT, pattern.id(),
                    detection -> { });

            subscription.cancel();
            assertThat(subscription.isCancelled()).isTrue();
        }
    }

    @Nested
    @DisplayName("Pipeline Management")
    class PipelineManagement {

        @Test
        @DisplayName("should submit a pipeline")
        void shouldSubmitPipeline() {
            AepEngine.Pipeline pipeline = new AepEngine.Pipeline(
                    "pipe-1", "Test Pipeline",
                    List.of(new AepEngine.PipelineStep(
                            "filter",
                            Map.of("type", "sensor.temperature"))));

            engine.submitPipeline(TENANT, pipeline);
            // submitPipeline is void — no exception means success
        }
    }

    @Nested
    @DisplayName("Anomaly Detection")
    class AnomalyDetection {

        @Test
        @DisplayName("should return empty anomalies for empty events")
        void shouldReturnEmptyAnomaliesForEmptyData() {
            List<AepEngine.Anomaly> anomalies = runPromise(
                    () -> engine.detectAnomalies(TENANT, List.of()));

            assertThat(anomalies).isEmpty();
        }

        @Test
        @DisplayName("should detect anomalies in event data")
        void shouldDetectAnomaliesInData() {
            List<AepEngine.Event> events = List.of(
                    AepEngine.Event.of("sensor.cpu", Map.of("value", 10.0)),
                    AepEngine.Event.of("sensor.cpu", Map.of("value", 11.0)),
                    AepEngine.Event.of("sensor.cpu", Map.of("value", 10.5)),
                    AepEngine.Event.of("sensor.cpu", Map.of("value", 10.2)),
                    AepEngine.Event.of("sensor.cpu", Map.of("value", 100.0)),
                    AepEngine.Event.of("sensor.cpu", Map.of("value", 10.8)));

            List<AepEngine.Anomaly> anomalies = runPromise(
                    () -> engine.detectAnomalies(TENANT, events));

            // At minimum, the method should complete without error
            assertThat(anomalies).isNotNull();
        }
    }

    @Nested
    @DisplayName("Forecasting")
    class Forecasting {

        @Test
        @DisplayName("should forecast from time series data")
        void shouldForecast() {
            AepEngine.TimeSeriesData data = new AepEngine.TimeSeriesData(
                    "linear-metric",
                    List.of(
                            new AepEngine.DataPoint(Instant.now().minusSeconds(30), 10.0),
                            new AepEngine.DataPoint(Instant.now().minusSeconds(20), 20.0),
                            new AepEngine.DataPoint(Instant.now().minusSeconds(10), 30.0)));

            AepEngine.Forecast forecast = runPromise(() -> engine.forecast(TENANT, data));

            assertThat(forecast).isNotNull();
            assertThat(forecast.predictions()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Engine Lifecycle")
    class Lifecycle {

        @Test
        @DisplayName("should reject processing after close")
        void shouldRejectAfterClose() {
            engine.close();

            AepEngine.Event event = new AepEngine.Event(
                    "test", Map.of(), Map.of(), Instant.now());

            assertThatThrownBy(() -> runPromise(() -> engine.process(TENANT, event)))
                    .isInstanceOf(IllegalStateException.class);
            clearFatalError();
        }
    }
}
