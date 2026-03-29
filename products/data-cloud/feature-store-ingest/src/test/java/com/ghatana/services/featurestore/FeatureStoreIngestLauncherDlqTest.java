/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.services.featurestore;

import com.ghatana.aiplatform.featurestore.Feature;
import com.ghatana.aiplatform.featurestore.FeatureStoreService;
import com.ghatana.datacloud.spi.EventLogStore;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.resilience.CircuitBreaker;
import com.ghatana.platform.resilience.DeadLetterQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * DLQ and circuit-breaker routing tests for {@link FeatureStoreIngestLauncher} (DC-007).
 *
 * <p>Verifies that extraction failures and write failures are routed to the
 * {@link DeadLetterQueue} rather than being silently dropped, and that an
 * OPEN circuit causes entries to land in the DLQ instead of producing
 * unhandled exceptions.
 *
 * @doc.type class
 * @doc.purpose DLQ + circuit-breaker routing tests (DC-007)
 * @doc.layer product
 * @doc.pattern Test, Mockito
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FeatureStoreIngestLauncher — DLQ routing (DC-007)")
class FeatureStoreIngestLauncherDlqTest {

    private static final TenantId TENANT = TenantId.of("test-tenant");

    @Mock private EventLogStore eventLogStore;
    @Mock private FeatureStoreService featureStore;
    @Mock private MetricsCollector metrics;

    private DeadLetterQueue dlq;
    private FeatureStoreIngestLauncher launcher;

    @BeforeEach
    void setUp() {
        dlq = DeadLetterQueue.builder()
            .maxSize(1_000)
            .ttl(Duration.ofMinutes(5))
            .enableReplay(false)
            .build();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private FeatureStoreIngestLauncher launcherWithCircuitBreaker(CircuitBreaker cb) {
        return new FeatureStoreIngestLauncher(
            eventLogStore, featureStore, metrics,
            List.of(TENANT), 10, 100L, 200L,
            cb, dlq);
    }

    private static EventLogStore.EventEntry validEntry() {
        // payload is a JSON object with a numeric field so extraction succeeds
        byte[] payload = "{\"amount\":42.0}".getBytes();
        return EventLogStore.EventEntry.builder()
            .eventId(UUID.randomUUID())
            .eventType("test.event")
            .payload(ByteBuffer.wrap(payload))
            .headers(Map.of("entityId", "entity-1"))
            .timestamp(Instant.now())
            .build();
    }

    private static EventLogStore.EventEntry malformedEntry() {
        // payload is not valid JSON — extraction fails deterministically
        return EventLogStore.EventEntry.builder()
            .eventId(UUID.randomUUID())
            .eventType("test.event")
            .payload(ByteBuffer.wrap("NOT_JSON".getBytes()))
            .headers(Map.of())
            .timestamp(Instant.now())
            .build();
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("malformed payload — extraction failure")
    class ExtractionFailureTests {

        @BeforeEach
        void setUp() {
            CircuitBreaker cb = CircuitBreaker.builder("test-cb")
                .failureThreshold(10)
                .resetTimeout(Duration.ofSeconds(30))
                .build();
            launcher = launcherWithCircuitBreaker(cb);
        }

        @Test
        @DisplayName("malformed payload routes entry to DLQ with extraction-failure reason")
        void malformedPayloadRoutesToDlq() {
            EventLogStore.EventEntry entry = malformedEntry();

            launcher.processEntryForTesting(TENANT, entry);

            DeadLetterQueue dlqRef = launcher.getDeadLetterQueueForTesting();
            assertThat(dlqRef.size())
                .as("DLQ must hold the entry that failed extraction")
                .isEqualTo(1);

            var stored = dlqRef.getAll();
            assertThat(stored).hasSize(1);
            assertThat(stored.get(0).getReason())
                .isEqualTo("extraction-failure");
        }

        @Test
        @DisplayName("extraction failure does not write any features to the feature store")
        void extractionFailureDoesNotWriteFeatures() {
            launcher.processEntryForTesting(TENANT, malformedEntry());

            verifyNoInteractions(featureStore);
        }

        @Test
        @DisplayName("extraction failure increments the extraction error counter")
        void extractionFailureIncrementsMetric() {
            launcher.processEntryForTesting(TENANT, malformedEntry());

            verify(metrics, atLeastOnce()).incrementCounter(
                eq("feature.ingest.extraction.errors"), any(String[].class));
        }
    }

    @Nested
    @DisplayName("feature store write failure")
    class WriteFailureTests {

        @BeforeEach
        void setUp() {
            CircuitBreaker closedCb = CircuitBreaker.builder("test-write-cb")
                .failureThreshold(100) // high threshold — stays CLOSED
                .resetTimeout(Duration.ofSeconds(30))
                .build();
            launcher = launcherWithCircuitBreaker(closedCb);
        }

        @Test
        @DisplayName("write failure routes entry to DLQ with write-failure reason")
        void writeFailureRoutesToDlq() throws Exception {
            doThrow(new RuntimeException("DB down")).when(featureStore).ingest(anyString(), any(Feature.class));

            launcher.processEntryForTesting(TENANT, validEntry());

            DeadLetterQueue dlqRef = launcher.getDeadLetterQueueForTesting();
            assertThat(dlqRef.size())
                .as("DLQ must hold the entry(ies) that failed writing")
                .isGreaterThan(0);

            assertThat(dlqRef.getAll())
                .extracting(e -> e.getReason())
                .containsOnly("write-failure");
        }
    }

    @Nested
    @DisplayName("circuit breaker OPEN — DLQ fallback")
    class CircuitOpenTests {

        @Test
        @DisplayName("open circuit routes entry to DLQ with circuit-open reason")
        void openCircuitRoutesToDlq() {
            // Circuit with threshold=1 — trips after one failed call
            CircuitBreaker trippedCb = CircuitBreaker.builder("test-tripped")
                .failureThreshold(1)
                .resetTimeout(Duration.ofHours(1))  // won't reset during test
                .build();

            // Trip it manually by causing one failure through executeSync
            try {
                trippedCb.executeSync(() -> { throw new RuntimeException("trip"); });
            } catch (Exception ignored) { /* expected */ }

            assertThat(trippedCb.getState())
                .as("circuit must be OPEN after threshold exceeded")
                .isEqualTo(CircuitBreaker.State.OPEN);

            launcher = launcherWithCircuitBreaker(trippedCb);

            launcher.processEntryForTesting(TENANT, validEntry());

            DeadLetterQueue dlqRef = launcher.getDeadLetterQueueForTesting();
            assertThat(dlqRef.size())
                .as("DLQ must hold entries rejected by open circuit")
                .isGreaterThan(0);

            assertThat(dlqRef.getAll())
                .extracting(e -> e.getReason())
                .containsOnly("circuit-open");
        }

        @Test
        @DisplayName("open circuit does not invoke featureStore.ingest()")
        void openCircuitDoesNotCallFeatureStore() {
            CircuitBreaker trippedCb = CircuitBreaker.builder("test-tripped-2")
                .failureThreshold(1)
                .resetTimeout(Duration.ofHours(1))
                .build();
            try {
                trippedCb.executeSync(() -> { throw new RuntimeException("trip"); });
            } catch (Exception ignored) {}

            launcher = launcherWithCircuitBreaker(trippedCb);
            launcher.processEntryForTesting(TENANT, validEntry());

            verifyNoInteractions(featureStore);
        }
    }
}
