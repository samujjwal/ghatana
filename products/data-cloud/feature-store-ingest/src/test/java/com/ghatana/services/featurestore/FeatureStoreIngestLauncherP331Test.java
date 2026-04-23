/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("P3.3.1 — FeatureTransformSpec and lag metric")
class FeatureStoreIngestLauncherP331Test {

    private static final TenantId TENANT = TenantId.of("t1");

    @Mock private FeatureStoreService featureStore;
    @Mock private MetricsCollector metrics;

    private FeatureStoreIngestLauncher launcher;
    private DeadLetterQueue dlq;

    @BeforeEach
    void setUp() { // GH-90000
        dlq = DeadLetterQueue.builder() // GH-90000
            .maxSize(100) // GH-90000
            .ttl(Duration.ofMinutes(1)) // GH-90000
            .enableReplay(false) // GH-90000
            .build(); // GH-90000
        CircuitBreaker cb = CircuitBreaker.builder("p331-cb")
            .failureThreshold(10) // GH-90000
            .resetTimeout(Duration.ofSeconds(30)) // GH-90000
            .successThreshold(2) // GH-90000
            .build(); // GH-90000
        launcher = new FeatureStoreIngestLauncher( // GH-90000
            null, featureStore, metrics,
            List.of(TENANT), 10, 100L, 200L, // GH-90000
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
        void passThroughAcceptsAnyEventType() { // GH-90000
            FeatureTransformSpec spec = FeatureTransformSpec.passThrough(); // GH-90000

            assertThat(spec.acceptsEventType("user.created")).isTrue();
            assertThat(spec.acceptsEventType("payment.processed")).isTrue();
            assertThat(spec.acceptsEventType("")).isTrue();
        }

        @Test
        @DisplayName("passThrough accepts any field name")
        void passThroughAcceptsAnyField() { // GH-90000
            FeatureTransformSpec spec = FeatureTransformSpec.passThrough(); // GH-90000

            assertThat(spec.acceptsField("amount")).isTrue();
            assertThat(spec.acceptsField("internal_secret")).isTrue();
            assertThat(spec.acceptsField("ignored_field")).isTrue();
        }

        @Test
        @DisplayName("passThrough enables derived time features by default")
        void passThroughEnablesDerivedTimeFeatures() { // GH-90000
            FeatureTransformSpec spec = FeatureTransformSpec.passThrough(); // GH-90000

            assertThat(spec.isDerivedTimeFeatures()).isTrue(); // GH-90000
        }
    }

    @Nested
    @DisplayName("FeatureTransformSpec — event-type filter")
    class EventTypeFilterTests {

        @Test
        @DisplayName("eventType filter rejects unlisted event types")
        void rejectsUnlistedEventType() { // GH-90000
            FeatureTransformSpec spec = FeatureTransformSpec.builder() // GH-90000
                .eventType("user.created")
                .build(); // GH-90000

            assertThat(spec.acceptsEventType("user.created")).isTrue();
            assertThat(spec.acceptsEventType("payment.processed")).isFalse();
        }

        @Test
        @DisplayName("eventType filter accepts multiple configured types")
        void acceptsMultipleConfiguredTypes() { // GH-90000
            FeatureTransformSpec spec = FeatureTransformSpec.builder() // GH-90000
                .eventType("user.created")
                .eventType("payment.processed")
                .build(); // GH-90000

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
        void includeFieldsAllowsOnlyListed() { // GH-90000
            FeatureTransformSpec spec = FeatureTransformSpec.builder() // GH-90000
                .includeField("amount")
                .includeField("score")
                .build(); // GH-90000

            assertThat(spec.acceptsField("amount")).isTrue();
            assertThat(spec.acceptsField("score")).isTrue();
            assertThat(spec.acceptsField("internal_id")).isFalse();
        }

        @Test
        @DisplayName("excludeFields drops listed fields")
        void excludeFieldsDropsListedFields() { // GH-90000
            FeatureTransformSpec spec = FeatureTransformSpec.builder() // GH-90000
                .excludeField("internal_id")
                .excludeField("raw_password")
                .build(); // GH-90000

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
        void includeFieldsFilterApplied() { // GH-90000
            Map<String, Object> payload = Map.of("amount", 99.0, "score", 5.0, "secret", 42.0); // GH-90000
            Instant ts = Instant.now(); // GH-90000

            FeatureTransformSpec spec = FeatureTransformSpec.builder() // GH-90000
                .includeField("amount")
                .derivedTimeFeatures(false) // GH-90000
                .build(); // GH-90000

            List<MLFeature> features = FeatureStoreIngestLauncher.extractFeatures("e1", payload, ts, spec); // GH-90000

            assertThat(features).extracting(MLFeature::getName) // GH-90000
                .containsExactly("amount")
                .doesNotContain("score", "secret"); // GH-90000
        }

        @Test
        @DisplayName("spec with excludeField removes that field")
        void excludeFieldsFilterApplied() { // GH-90000
            Map<String, Object> payload = Map.of("amount", 99.0, "internal_id", 1.0); // GH-90000
            Instant ts = Instant.now(); // GH-90000

            FeatureTransformSpec spec = FeatureTransformSpec.builder() // GH-90000
                .excludeField("internal_id")
                .derivedTimeFeatures(false) // GH-90000
                .build(); // GH-90000

            List<MLFeature> features = FeatureStoreIngestLauncher.extractFeatures("e1", payload, ts, spec); // GH-90000

            assertThat(features).extracting(MLFeature::getName) // GH-90000
                .contains("amount")
                .doesNotContain("internal_id");
        }

        @Test
        @DisplayName("derivedTimeFeatures=false suppresses hour_of_day and day_of_week")
        void derivedTimeFeaturesDisabled() { // GH-90000
            Map<String, Object> payload = Map.of("value", 1.0); // GH-90000
            Instant ts = Instant.now(); // GH-90000

            FeatureTransformSpec spec = FeatureTransformSpec.builder() // GH-90000
                .derivedTimeFeatures(false) // GH-90000
                .build(); // GH-90000

            List<MLFeature> features = FeatureStoreIngestLauncher.extractFeatures("e1", payload, ts, spec); // GH-90000

            assertThat(features).extracting(MLFeature::getName) // GH-90000
                .doesNotContain("hour_of_day", "day_of_week"); // GH-90000
        }

        @Test
        @DisplayName("derivedTimeFeatures=true (default) appends hour_of_day and day_of_week")
        void derivedTimeFeaturesEnabled() { // GH-90000
            Map<String, Object> payload = Map.of("value", 1.0); // GH-90000
            Instant ts = Instant.now(); // GH-90000

            FeatureTransformSpec spec = FeatureTransformSpec.passThrough(); // GH-90000

            List<MLFeature> features = FeatureStoreIngestLauncher.extractFeatures("e1", payload, ts, spec); // GH-90000

            assertThat(features).extracting(MLFeature::getName) // GH-90000
                .contains("hour_of_day", "day_of_week"); // GH-90000
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
        void rejectsNull() { // GH-90000
            assertThatThrownBy(() -> launcher.withTransformSpec(null)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("transformSpec must not be null");
        }

        @Test
        @DisplayName("withTransformSpec returns launcher for chaining")
        void returnsLauncher() { // GH-90000
            FeatureTransformSpec spec = FeatureTransformSpec.passThrough(); // GH-90000

            FeatureStoreIngestLauncher result = launcher.withTransformSpec(spec); // GH-90000

            assertThat(result).isSameAs(launcher); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // processEntry — event-type filtering (via reflective helper) // GH-90000
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("processEntry — event-type filtering increments counter")
    class ProcessEntryFilterTests {

        @Test
        @DisplayName("filtered event type increments filtered counter and skips feature write")
        void filteredEventTypeSkipsIngestion() throws Exception { // GH-90000
            FeatureTransformSpec spec = FeatureTransformSpec.builder() // GH-90000
                .eventType("user.created")
                .build(); // GH-90000
            launcher.withTransformSpec(spec); // GH-90000

            EventLogStore.EventEntry entry = entry("payment.processed", "{\"amount\":10.0}"); // GH-90000

            invokeProcessEntry(entry); // GH-90000

            verify(metrics).incrementCounter( // GH-90000
                eq("feature.ingest.events.filtered"),
                eq("tenant"), eq("t1"),
                eq("event_type"), eq("payment.processed"));
            verify(featureStore, never()).ingest(anyString(), any()); // GH-90000
        }

        @Test
        @DisplayName("accepted event type proceeds to feature ingestion")
        void acceptedEventTypeProceedsToIngestion() throws Exception { // GH-90000
            FeatureTransformSpec spec = FeatureTransformSpec.builder() // GH-90000
                .eventType("user.created")
                .derivedTimeFeatures(false) // GH-90000
                .build(); // GH-90000
            launcher.withTransformSpec(spec); // GH-90000

            lenient().doNothing().when(featureStore).ingest(anyString(), any()); // GH-90000
            EventLogStore.EventEntry entry = entry("user.created", "{\"score\":7.0}"); // GH-90000

            invokeProcessEntry(entry); // GH-90000

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
        void processEntryEmitsLagMetric() throws Exception { // GH-90000
            lenient().doNothing().when(featureStore).ingest(anyString(), any()); // GH-90000

            EventLogStore.EventEntry entry = entry("test.event", "{\"score\":5.0}"); // GH-90000

            invokeProcessEntry(entry); // GH-90000

            verify(metrics).recordTimer( // GH-90000
                eq("feature.ingest.lag_ms"),
                any(Long.class), // GH-90000
                eq("tenant"), eq("t1"));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static EventLogStore.EventEntry entry(String eventType, String jsonPayload) { // GH-90000
        byte[] raw = jsonPayload.getBytes(); // GH-90000
        return EventLogStore.EventEntry.builder() // GH-90000
            .eventId(UUID.randomUUID()) // GH-90000
            .eventType(eventType) // GH-90000
            .payload(ByteBuffer.wrap(raw)) // GH-90000
            .headers(Map.of("entityId", "entity-1")) // GH-90000
            .timestamp(Instant.now().minusSeconds(1)) // GH-90000
            .build(); // GH-90000
    }

    /** Reflective trampoline to call the package-private processEntry() from a test. */ // GH-90000
    private void invokeProcessEntry(EventLogStore.EventEntry entry) throws Exception { // GH-90000
        var method = FeatureStoreIngestLauncher.class.getDeclaredMethod( // GH-90000
            "processEntry", TenantId.class, EventLogStore.EventEntry.class);
        method.setAccessible(true); // GH-90000
        method.invoke(launcher, TENANT, entry); // GH-90000
    }
}
