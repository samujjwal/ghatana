/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.services.featurestore;

import com.ghatana.aiplatform.featurestore.MLFeature;
import com.ghatana.aiplatform.featurestore.FeatureStoreService;
import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.resilience.CircuitBreaker;
import com.ghatana.platform.resilience.DeadLetterQueue;
import com.ghatana.services.featurestore.config.FeatureTransformSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * P3.3.1 — Feature transform spec, event-type filtering, derived features, and lag metric.
 *
 * @doc.type class
 * @doc.purpose P3.3.1 YAML-configurable transform spec and lag-metric tests
 * @doc.layer product
 * @doc.pattern Test, Mockito
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("P3.3.1 — FeatureTransformSpec and lag metric")
class FeatureStoreIngestLauncherP331Test {

    private static final TenantId TENANT = TenantId.of("t1");

    @Mock private FeatureStoreService featureStore;
    @Mock private MetricsCollector metrics;

    private FeatureStoreIngestLauncher launcher;
    private DeadLetterQueue dlq;

    @BeforeEach
    void setUp() {
        dlq = DeadLetterQueue.builder()
            .maxSize(100)
            .ttl(Duration.ofMinutes(1))
            .enableReplay(false)
            .build();
        CircuitBreaker cb = CircuitBreaker.builder("p331-cb")
            .failureThreshold(10)
            .resetTimeout(Duration.ofSeconds(30))
            .successThreshold(2)
            .build();
        launcher = new FeatureStoreIngestLauncher(
            null, featureStore, metrics,
            List.of(TENANT), 10, 100L, 200L,
            cb, dlq);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FeatureTransformSpec — unit tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("FeatureTransformSpec — passThrough()")
    class PassThroughTests {

        @Test
        @DisplayName("passThrough accepts any event type")
        void passThroughAcceptsAnyEventType() {
            FeatureTransformSpec spec = FeatureTransformSpec.passThrough();

            assertThat(spec.acceptsEventType("user.created")).isTrue();
            assertThat(spec.acceptsEventType("payment.processed")).isTrue();
            assertThat(spec.acceptsEventType("")).isTrue();
        }

        @Test
        @DisplayName("passThrough accepts any field name")
        void passThroughAcceptsAnyField() {
            FeatureTransformSpec spec = FeatureTransformSpec.passThrough();

            assertThat(spec.acceptsField("amount")).isTrue();
            assertThat(spec.acceptsField("internal_secret")).isTrue();
            assertThat(spec.acceptsField("ignored_field")).isTrue();
        }

        @Test
        @DisplayName("passThrough enables derived time features by default")
        void passThroughEnablesDerivedTimeFeatures() {
            FeatureTransformSpec spec = FeatureTransformSpec.passThrough();

            assertThat(spec.isDerivedTimeFeatures()).isTrue();
        }
    }

    @Nested
    @DisplayName("FeatureTransformSpec — event-type filter")
    class EventTypeFilterTests {

        @Test
        @DisplayName("eventType filter rejects unlisted event types")
        void rejectsUnlistedEventType() {
            FeatureTransformSpec spec = FeatureTransformSpec.builder()
                .eventType("user.created")
                .build();

            assertThat(spec.acceptsEventType("user.created")).isTrue();
            assertThat(spec.acceptsEventType("payment.processed")).isFalse();
        }

        @Test
        @DisplayName("eventType filter accepts multiple configured types")
        void acceptsMultipleConfiguredTypes() {
            FeatureTransformSpec spec = FeatureTransformSpec.builder()
                .eventType("user.created")
                .eventType("payment.processed")
                .build();

            assertThat(spec.acceptsEventType("user.created")).isTrue();
            assertThat(spec.acceptsEventType("payment.processed")).isTrue();
            assertThat(spec.acceptsEventType("order.placed")).isFalse();
        }
    }

    @Nested
    @DisplayName("FeatureTransformSpec — field filter")
    class FieldFilterTests {

        @Test
        @DisplayName("includeFields allows only listed fields")
        void includeFieldsAllowsOnlyListed() {
            FeatureTransformSpec spec = FeatureTransformSpec.builder()
                .includeField("amount")
                .includeField("score")
                .build();

            assertThat(spec.acceptsField("amount")).isTrue();
            assertThat(spec.acceptsField("score")).isTrue();
            assertThat(spec.acceptsField("internal_id")).isFalse();
        }

        @Test
        @DisplayName("excludeFields drops listed fields")
        void excludeFieldsDropsListedFields() {
            FeatureTransformSpec spec = FeatureTransformSpec.builder()
                .excludeField("internal_id")
                .excludeField("raw_password")
                .build();

            assertThat(spec.acceptsField("amount")).isTrue();
            assertThat(spec.acceptsField("internal_id")).isFalse();
            assertThat(spec.acceptsField("raw_password")).isFalse();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // extractFeatures — spec overload
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("extractFeatures — spec overload")
    class ExtractFeaturesWithSpecTests {

        @Test
        @DisplayName("spec with includeField produces only allowed fields")
        void includeFieldsFilterApplied() {
            Map<String, Object> payload = Map.of("amount", 99.0, "score", 5.0, "secret", 42.0);
            Instant ts = Instant.now();

            FeatureTransformSpec spec = FeatureTransformSpec.builder()
                .includeField("amount")
                .derivedTimeFeatures(false)
                .build();

            List<MLFeature> features = FeatureStoreIngestLauncher.extractFeatures("e1", payload, ts, spec);

            assertThat(features).extracting(MLFeature::getName)
                .containsExactly("amount")
                .doesNotContain("score", "secret");
        }

        @Test
        @DisplayName("spec with excludeField removes that field")
        void excludeFieldsFilterApplied() {
            Map<String, Object> payload = Map.of("amount", 99.0, "internal_id", 1.0);
            Instant ts = Instant.now();

            FeatureTransformSpec spec = FeatureTransformSpec.builder()
                .excludeField("internal_id")
                .derivedTimeFeatures(false)
                .build();

            List<MLFeature> features = FeatureStoreIngestLauncher.extractFeatures("e1", payload, ts, spec);

            assertThat(features).extracting(MLFeature::getName)
                .contains("amount")
                .doesNotContain("internal_id");
        }

        @Test
        @DisplayName("derivedTimeFeatures=false suppresses hour_of_day and day_of_week")
        void derivedTimeFeaturesDisabled() {
            Map<String, Object> payload = Map.of("value", 1.0);
            Instant ts = Instant.now();

            FeatureTransformSpec spec = FeatureTransformSpec.builder()
                .derivedTimeFeatures(false)
                .build();

            List<MLFeature> features = FeatureStoreIngestLauncher.extractFeatures("e1", payload, ts, spec);

            assertThat(features).extracting(MLFeature::getName)
                .doesNotContain("hour_of_day", "day_of_week");
        }

        @Test
        @DisplayName("derivedTimeFeatures=true (default) appends hour_of_day and day_of_week")
        void derivedTimeFeaturesEnabled() {
            Map<String, Object> payload = Map.of("value", 1.0);
            Instant ts = Instant.now();

            FeatureTransformSpec spec = FeatureTransformSpec.passThrough();

            List<MLFeature> features = FeatureStoreIngestLauncher.extractFeatures("e1", payload, ts, spec);

            assertThat(features).extracting(MLFeature::getName)
                .contains("hour_of_day", "day_of_week");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // withTransformSpec — validation
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("withTransformSpec — validation")
    class WithTransformSpecTests {

        @Test
        @DisplayName("withTransformSpec rejects null")
        void rejectsNull() {
            assertThatThrownBy(() -> launcher.withTransformSpec(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("transformSpec must not be null");
        }

        @Test
        @DisplayName("withTransformSpec returns launcher for chaining")
        void returnsLauncher() {
            FeatureTransformSpec spec = FeatureTransformSpec.passThrough();

            FeatureStoreIngestLauncher result = launcher.withTransformSpec(spec);

            assertThat(result).isSameAs(launcher);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // processEntry — event-type filtering (via reflective helper)
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("processEntry — event-type filtering increments counter")
    class ProcessEntryFilterTests {

        @Test
        @DisplayName("filtered event type increments filtered counter and skips feature write")
        void filteredEventTypeSkipsIngestion() throws Exception {
            FeatureTransformSpec spec = FeatureTransformSpec.builder()
                .eventType("user.created")
                .build();
            launcher.withTransformSpec(spec);

            EventLogStore.EventEntry entry = entry("payment.processed", "{\"amount\":10.0}");

            invokeProcessEntry(entry);

            verify(metrics).incrementCounter(
                eq("feature.ingest.events.filtered"),
                eq("tenant"), eq("t1"),
                eq("event_type"), eq("payment.processed"));
            verify(featureStore, never()).ingest(anyString(), any());
        }

        @Test
        @DisplayName("accepted event type proceeds to feature ingestion")
        void acceptedEventTypeProceedsToIngestion() throws Exception {
            FeatureTransformSpec spec = FeatureTransformSpec.builder()
                .eventType("user.created")
                .derivedTimeFeatures(false)
                .build();
            launcher.withTransformSpec(spec);

            lenient().doNothing().when(featureStore).ingest(anyString(), any());
            EventLogStore.EventEntry entry = entry("user.created", "{\"score\":7.0}");

            invokeProcessEntry(entry);

            verify(featureStore, atLeastOnce()).ingest(eq("t1"), any());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // processEntry — lag metric
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("processEntry — lag metric")
    class LagMetricTests {

        @Test
        @DisplayName("processEntry emits feature.ingest.lag_ms timer")
        void processEntryEmitsLagMetric() throws Exception {
            lenient().doNothing().when(featureStore).ingest(anyString(), any());

            EventLogStore.EventEntry entry = entry("test.event", "{\"score\":5.0}");

            invokeProcessEntry(entry);

            verify(metrics).recordTimer(
                eq("feature.ingest.lag_ms"),
                any(Long.class),
                eq("tenant"), eq("t1"));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static EventLogStore.EventEntry entry(String eventType, String jsonPayload) {
        byte[] raw = jsonPayload.getBytes();
        return EventLogStore.EventEntry.builder()
            .eventId(UUID.randomUUID())
            .eventType(eventType)
            .payload(ByteBuffer.wrap(raw))
            .headers(Map.of("entityId", "entity-1"))
            .timestamp(Instant.now().minusSeconds(1))
            .build();
    }

    /** Reflective trampoline to call the package-private processEntry() from a test. */
    private void invokeProcessEntry(EventLogStore.EventEntry entry) throws Exception {
        var method = FeatureStoreIngestLauncher.class.getDeclaredMethod(
            "processEntry", TenantId.class, EventLogStore.EventEntry.class);
        method.setAccessible(true);
        method.invoke(launcher, TENANT, entry);
    }
}
