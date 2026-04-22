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
 * DLQ and circuit-breaker routing tests for {@link FeatureStoreIngestLauncher} (DC-007). // GH-90000
 *
 * <p>Verifies that extraction failures and write failures are routed to the
 * {@link DeadLetterQueue} rather than being silently dropped, and that an
 * OPEN circuit causes entries to land in the DLQ instead of producing
 * unhandled exceptions.
 *
 * @doc.type class
 * @doc.purpose DLQ + circuit-breaker routing tests (DC-007) // GH-90000
 * @doc.layer product
 * @doc.pattern Test, Mockito
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("FeatureStoreIngestLauncher — DLQ routing (DC-007) [GH-90000]")
class FeatureStoreIngestLauncherDlqTest {

    private static final TenantId TENANT = TenantId.of("test-tenant [GH-90000]");

    @Mock private EventLogStore eventLogStore;
    @Mock private FeatureStoreService featureStore;
    @Mock private MetricsCollector metrics;

    private DeadLetterQueue dlq;
    private FeatureStoreIngestLauncher launcher;

    @BeforeEach
    void setUp() { // GH-90000
        dlq = DeadLetterQueue.builder() // GH-90000
            .maxSize(1_000) // GH-90000
            .ttl(Duration.ofMinutes(5)) // GH-90000
            .enableReplay(false) // GH-90000
            .build(); // GH-90000
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private FeatureStoreIngestLauncher launcherWithCircuitBreaker(CircuitBreaker cb) { // GH-90000
        return new FeatureStoreIngestLauncher( // GH-90000
            eventLogStore, featureStore, metrics,
            List.of(TENANT), 10, 100L, 200L, // GH-90000
            cb, dlq);
    }

    private static EventLogStore.EventEntry validEntry() { // GH-90000
        // payload is a JSON object with a numeric field so extraction succeeds
        byte[] payload = "{\"amount\":42.0}".getBytes(); // GH-90000
        return EventLogStore.EventEntry.builder() // GH-90000
            .eventId(UUID.randomUUID()) // GH-90000
            .eventType("test.event [GH-90000]")
            .payload(ByteBuffer.wrap(payload)) // GH-90000
            .headers(Map.of("entityId", "entity-1")) // GH-90000
            .timestamp(Instant.now()) // GH-90000
            .build(); // GH-90000
    }

    private static EventLogStore.EventEntry malformedEntry() { // GH-90000
        // payload is not valid JSON — extraction fails deterministically
        return EventLogStore.EventEntry.builder() // GH-90000
            .eventId(UUID.randomUUID()) // GH-90000
            .eventType("test.event [GH-90000]")
            .payload(ByteBuffer.wrap("NOT_JSON".getBytes())) // GH-90000
            .headers(Map.of()) // GH-90000
            .timestamp(Instant.now()) // GH-90000
            .build(); // GH-90000
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("malformed payload — extraction failure [GH-90000]")
    class ExtractionFailureTests {

        @BeforeEach
        void setUp() { // GH-90000
            CircuitBreaker cb = CircuitBreaker.builder("test-cb [GH-90000]")
                .failureThreshold(10) // GH-90000
                .resetTimeout(Duration.ofSeconds(30)) // GH-90000
                .build(); // GH-90000
            launcher = launcherWithCircuitBreaker(cb); // GH-90000
        }

        @Test
        @DisplayName("malformed payload routes entry to DLQ with extraction-failure reason [GH-90000]")
        void malformedPayloadRoutesToDlq() { // GH-90000
            EventLogStore.EventEntry entry = malformedEntry(); // GH-90000

            launcher.processEntryForTesting(TENANT, entry); // GH-90000

            DeadLetterQueue dlqRef = launcher.getDeadLetterQueueForTesting(); // GH-90000
            assertThat(dlqRef.size()) // GH-90000
                .as("DLQ must hold the entry that failed extraction [GH-90000]")
                .isEqualTo(1); // GH-90000

            var stored = dlqRef.getAll(); // GH-90000
            assertThat(stored).hasSize(1); // GH-90000
            assertThat(stored.get(0).getReason()) // GH-90000
                .isEqualTo("extraction-failure [GH-90000]");
        }

        @Test
        @DisplayName("extraction failure does not write any features to the feature store [GH-90000]")
        void extractionFailureDoesNotWriteFeatures() { // GH-90000
            launcher.processEntryForTesting(TENANT, malformedEntry()); // GH-90000

            verifyNoInteractions(featureStore); // GH-90000
        }

        @Test
        @DisplayName("extraction failure increments the extraction error counter [GH-90000]")
        void extractionFailureIncrementsMetric() { // GH-90000
            launcher.processEntryForTesting(TENANT, malformedEntry()); // GH-90000

            verify(metrics, atLeastOnce()).incrementCounter( // GH-90000
                eq("feature.ingest.extraction.errors [GH-90000]"), any(String[].class));
        }
    }

    @Nested
    @DisplayName("feature store write failure [GH-90000]")
    class WriteFailureTests {

        @BeforeEach
        void setUp() { // GH-90000
            CircuitBreaker closedCb = CircuitBreaker.builder("test-write-cb [GH-90000]")
                .failureThreshold(100) // high threshold — stays CLOSED // GH-90000
                .resetTimeout(Duration.ofSeconds(30)) // GH-90000
                .build(); // GH-90000
            launcher = launcherWithCircuitBreaker(closedCb); // GH-90000
        }

        @Test
        @DisplayName("write failure routes entry to DLQ with write-failure reason [GH-90000]")
        void writeFailureRoutesToDlq() throws Exception { // GH-90000
            doThrow(new RuntimeException("DB down [GH-90000]")).when(featureStore).ingest(anyString(), any(MLFeature.class));

            launcher.processEntryForTesting(TENANT, validEntry()); // GH-90000

            DeadLetterQueue dlqRef = launcher.getDeadLetterQueueForTesting(); // GH-90000
            assertThat(dlqRef.size()) // GH-90000
                .as("DLQ must hold the entry(ies) that failed writing [GH-90000]")
                .isGreaterThan(0); // GH-90000

            assertThat(dlqRef.getAll()) // GH-90000
                .extracting(e -> e.getReason()) // GH-90000
                .containsOnly("write-failure [GH-90000]");
        }
    }

    @Nested
    @DisplayName("circuit breaker OPEN — DLQ fallback [GH-90000]")
    class CircuitOpenTests {

        @Test
        @DisplayName("open circuit routes entry to DLQ with circuit-open reason [GH-90000]")
        void openCircuitRoutesToDlq() { // GH-90000
            // Circuit with threshold=1 — trips after one failed call
            CircuitBreaker trippedCb = CircuitBreaker.builder("test-tripped [GH-90000]")
                .failureThreshold(1) // GH-90000
                .resetTimeout(Duration.ofHours(1))  // won't reset during test // GH-90000
                .build(); // GH-90000

            // Trip it manually by causing one failure through executeSync
            try {
                trippedCb.executeSync(() -> { throw new RuntimeException("trip [GH-90000]"); });
            } catch (Exception ignored) { /* expected */ } // GH-90000

            assertThat(trippedCb.getState()) // GH-90000
                .as("circuit must be OPEN after threshold exceeded [GH-90000]")
                .isEqualTo(CircuitBreaker.State.OPEN); // GH-90000

            launcher = launcherWithCircuitBreaker(trippedCb); // GH-90000

            launcher.processEntryForTesting(TENANT, validEntry()); // GH-90000

            DeadLetterQueue dlqRef = launcher.getDeadLetterQueueForTesting(); // GH-90000
            assertThat(dlqRef.size()) // GH-90000
                .as("DLQ must hold entries rejected by open circuit [GH-90000]")
                .isGreaterThan(0); // GH-90000

            assertThat(dlqRef.getAll()) // GH-90000
                .extracting(e -> e.getReason()) // GH-90000
                .containsOnly("circuit-open [GH-90000]");
        }

        @Test
        @DisplayName("open circuit does not invoke featureStore.ingest() [GH-90000]")
        void openCircuitDoesNotCallFeatureStore() { // GH-90000
            CircuitBreaker trippedCb = CircuitBreaker.builder("test-tripped-2 [GH-90000]")
                .failureThreshold(1) // GH-90000
                .resetTimeout(Duration.ofHours(1)) // GH-90000
                .build(); // GH-90000
            try {
                trippedCb.executeSync(() -> { throw new RuntimeException("trip [GH-90000]"); });
            } catch (Exception ignored) {} // GH-90000

            launcher = launcherWithCircuitBreaker(trippedCb); // GH-90000
            launcher.processEntryForTesting(TENANT, validEntry()); // GH-90000

            verifyNoInteractions(featureStore); // GH-90000
        }
    }
}
