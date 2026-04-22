/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.analytics.anomaly.StatisticalAnomalyDetector;
import com.ghatana.datacloud.spi.ai.AnomalyDetectionCapability.Anomaly;
import com.ghatana.datacloud.spi.ai.AnomalyDetectionCapability.Severity;
import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.platform.domain.eventstore.EventLogStore.EventEntry;
import com.ghatana.platform.domain.eventstore.TenantContext;
import com.ghatana.platform.types.identity.Offset;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for P3.6.1 enhancements in {@link EntityAnomalyHandler}:
 * durable anomaly persistence, event-store-backed query
 * ({@code GET /api/v1/anomalies}), and 501 response when no store is wired. // GH-90000
 *
 * @doc.type    class
 * @doc.purpose Unit tests for EntityAnomalyHandler durable persistence and query (P3.6.1) // GH-90000
 * @doc.layer   product
 * @doc.pattern Test
 */
@DisplayName("EntityAnomalyHandler – durable anomaly persistence and query (P3.6.1) [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
class EntityAnomalyHandlerP361Test {

    @Mock private HttpHandlerSupport http;
    @Mock private StatisticalAnomalyDetector anomalyDetector;
    @Mock private EventLogStore eventLogStore;
    @Mock private HttpRequest sharedRequest;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules(); // GH-90000

    // ─── handler with event store ─────────────────────────────────────────────

    @Nested
    @DisplayName("persistAnomalies() [GH-90000]")
    class PersistAnomaliesTests {

        private EntityAnomalyHandler handlerWithStore;

        @BeforeEach
        void setUp() { // GH-90000
            handlerWithStore = new EntityAnomalyHandler(anomalyDetector, http, eventLogStore, objectMapper); // GH-90000
        }

        @Test
        @DisplayName("returns immediately when anomaly list is empty [GH-90000]")
        void returnsImmediatelyForEmptyList() { // GH-90000
            Promise<Void> result = handlerWithStore.persistAnomalies("t1", "col1", List.of()); // GH-90000

            // appendBatch must never be called for an empty list
            verify(eventLogStore, never()).appendBatch(any(), any()); // GH-90000
            assertThat(result).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("calls appendBatch with ANOMALY_DETECTED event type for each anomaly [GH-90000]")
        void callsAppendBatchWithCorrectEventType() { // GH-90000
            Anomaly anomaly = Anomaly.builder() // GH-90000
                    .anomalyId("a1 [GH-90000]")
                    .severity(Severity.HIGH) // GH-90000
                    .confidence(0.9) // GH-90000
                    .anomalyScore(3.5) // GH-90000
                    .title("Outlier detected [GH-90000]")
                    .affectedEntity("entity-99 [GH-90000]")
                    .detectedAt(Instant.now()) // GH-90000
                    .build(); // GH-90000

            List<Offset> fakeOffsets = List.of(Offset.of(42L), Offset.of(43L)); // GH-90000
            when(eventLogStore.appendBatch(any(TenantContext.class), any())).thenReturn(Promise.of(fakeOffsets)); // GH-90000

            handlerWithStore.persistAnomalies("tenant1", "orders", List.of(anomaly)); // GH-90000

            @SuppressWarnings("unchecked [GH-90000]")
            ArgumentCaptor<List<EventEntry>> entriesCaptor = ArgumentCaptor.forClass(List.class); // GH-90000
            verify(eventLogStore).appendBatch(any(TenantContext.class), entriesCaptor.capture()); // GH-90000

            List<EventEntry> captured = entriesCaptor.getValue(); // GH-90000
            assertThat(captured).hasSize(1); // GH-90000
            assertThat(captured.get(0).eventType()).isEqualTo(EntityAnomalyHandler.ANOMALY_EVENT_TYPE); // GH-90000
        }

        @Test
        @DisplayName("includes collection and tenantId in event headers [GH-90000]")
        void setsCollectionAndTenantInHeaders() { // GH-90000
            Anomaly anomaly = Anomaly.builder() // GH-90000
                    .anomalyId("a2 [GH-90000]")
                    .severity(Severity.LOW) // GH-90000
                    .confidence(0.7) // GH-90000
                    .anomalyScore(2.1) // GH-90000
                    .title("Minor drift [GH-90000]")
                    .affectedEntity("entity-5 [GH-90000]")
                    .detectedAt(Instant.now()) // GH-90000
                    .build(); // GH-90000

            when(eventLogStore.appendBatch(any(TenantContext.class), any())).thenReturn(Promise.of(List.of())); // GH-90000

            handlerWithStore.persistAnomalies("myTenant", "products", List.of(anomaly)); // GH-90000

            @SuppressWarnings("unchecked [GH-90000]")
            ArgumentCaptor<List<EventEntry>> entriesCaptor = ArgumentCaptor.forClass(List.class); // GH-90000
            verify(eventLogStore).appendBatch(any(TenantContext.class), entriesCaptor.capture()); // GH-90000

            EventEntry entry = entriesCaptor.getValue().get(0); // GH-90000
            assertThat(entry.headers()).containsEntry("collection", "products"); // GH-90000
            assertThat(entry.headers()).containsEntry("tenantId", "myTenant"); // GH-90000
        }

        @Test
        @DisplayName("returns immediately when eventLogStore is null (no-store constructor) [GH-90000]")
        void returnsImmediatelyWhenNoStore() { // GH-90000
            EntityAnomalyHandler noStoreHandler = new EntityAnomalyHandler(anomalyDetector, http); // GH-90000

            Anomaly anomaly = Anomaly.builder() // GH-90000
                    .anomalyId("a3 [GH-90000]")
                    .severity(Severity.MEDIUM) // GH-90000
                    .confidence(0.8) // GH-90000
                    .anomalyScore(2.8) // GH-90000
                    .title("Test anomaly [GH-90000]")
                    .affectedEntity("e-42 [GH-90000]")
                    .detectedAt(Instant.now()) // GH-90000
                    .build(); // GH-90000

            Promise<Void> result = noStoreHandler.persistAnomalies("t1", "c1", List.of(anomaly)); // GH-90000

            verify(eventLogStore, never()).appendBatch(any(), any()); // GH-90000
            assertThat(result).isNotNull(); // GH-90000
        }
    }

    // ─── handleQueryAnomalies: no store ──────────────────────────────────────

    @Nested
    @DisplayName("handleQueryAnomalies() — no event store wired [GH-90000]")
    class QueryAnomaliesNoStoreTests {

        private EntityAnomalyHandler noStoreHandler;

        @BeforeEach
        void setUp() { // GH-90000
            noStoreHandler = new EntityAnomalyHandler(anomalyDetector, http); // GH-90000
        }

        @Test
        @DisplayName("returns 501 when event store is not configured [GH-90000]")
        void returns501WhenNoStore() { // GH-90000
            HttpResponse expected501 = HttpResponse.ofCode(501).build(); // GH-90000
            when(http.errorResponse(eq(501), any())).thenReturn(expected501); // GH-90000

            Promise<HttpResponse> result = noStoreHandler.handleQueryAnomalies(sharedRequest); // GH-90000

            HttpResponse response = result.getResult(); // GH-90000
            assertThat(response).isNotNull(); // GH-90000
            assertThat(response.getCode()).isEqualTo(501); // GH-90000
        }
    }

    // ─── handleQueryAnomalies: with store ────────────────────────────────────

    @Nested
    @DisplayName("handleQueryAnomalies() — event store available [GH-90000]")
    class QueryAnomaliesWithStoreTests {

        private EntityAnomalyHandler handlerWithStore;

        @BeforeEach
        void setUp() { // GH-90000
            handlerWithStore = new EntityAnomalyHandler(anomalyDetector, http, eventLogStore, objectMapper); // GH-90000
            lenient().when(http.requireTenantIdOrFail(any())).thenReturn("tenant42 [GH-90000]");
            // No 'since' param — return null to trigger 24h default
            lenient().when(sharedRequest.getQueryParameter("since [GH-90000]")).thenReturn(null);
            lenient().when(sharedRequest.getQueryParameter("limit [GH-90000]")).thenReturn(null);
            lenient().when(sharedRequest.getQueryParameter("collection [GH-90000]")).thenReturn(null);
            lenient().when(sharedRequest.getPathParameter("collection [GH-90000]")).thenReturn(null);
        }

        @Test
        @DisplayName("returns JSON with anomaly entries from readByTimeRange [GH-90000]")
        void returnsAnomalyEntries() { // GH-90000
            EventEntry entry = EventEntry.builder() // GH-90000
                    .eventType(EntityAnomalyHandler.ANOMALY_EVENT_TYPE) // GH-90000
                    .payload("{\"anomalyId\":\"xyz\",\"title\":\"test\"}".getBytes(StandardCharsets.UTF_8)) // GH-90000
                    .headers(java.util.Map.of("collection", "orders", "tenantId", "tenant42")) // GH-90000
                    .build(); // GH-90000

            when(sharedRequest.getQueryParameter("collection [GH-90000]")).thenReturn("orders [GH-90000]");
            when(eventLogStore.readByTimeRange( // GH-90000
                    any(TenantContext.class), any(Instant.class), any(Instant.class), any(int.class))) // GH-90000
                    .thenReturn(Promise.of(List.of(entry))); // GH-90000

            HttpResponse fakeOk = HttpResponse.ok200().build(); // GH-90000
            when(http.jsonResponse(any())).thenReturn(fakeOk); // GH-90000

            Promise<HttpResponse> result = handlerWithStore.handleQueryAnomalies(sharedRequest); // GH-90000
            HttpResponse response = result.getResult(); // GH-90000

            assertThat(response).isNotNull(); // GH-90000
            verify(eventLogStore).readByTimeRange( // GH-90000
                    argThat(ctx -> "tenant42".equals(ctx.tenantId())), // GH-90000
                    any(Instant.class), any(Instant.class), any(int.class)); // GH-90000
        }

        @Test
        @DisplayName("returns 400 when tenant header is missing [GH-90000]")
        void returns400WhenTenantMissing() { // GH-90000
            when(http.requireTenantIdOrFail(any())).thenReturn(null); // GH-90000
            HttpResponse badRequest = HttpResponse.ofCode(400).build(); // GH-90000
            when(http.errorResponse(400, "X-Tenant-Id header is required")).thenReturn(badRequest); // GH-90000

            HttpResponse response = handlerWithStore.handleQueryAnomalies(sharedRequest).getResult(); // GH-90000

            assertThat(response).isSameAs(badRequest); // GH-90000
            verify(eventLogStore, never()).readByTimeRange(any(), any(), any(), any(int.class)); // GH-90000
        }

        @Test
        @DisplayName("defaults to last 24h when since parameter is absent [GH-90000]")
        void defaultsTo24HoursAgoWhenSinceAbsent() { // GH-90000
            when(eventLogStore.readByTimeRange(any(), any(Instant.class), any(Instant.class), any(int.class))) // GH-90000
                    .thenReturn(Promise.of(List.of())); // GH-90000
            when(http.jsonResponse(any())).thenReturn(HttpResponse.ok200().build()); // GH-90000

            handlerWithStore.handleQueryAnomalies(sharedRequest); // GH-90000

            ArgumentCaptor<Instant> sinceCaptor = ArgumentCaptor.forClass(Instant.class); // GH-90000
            verify(eventLogStore).readByTimeRange(any(), sinceCaptor.capture(), any(Instant.class), any(int.class)); // GH-90000

            Instant captured = sinceCaptor.getValue(); // GH-90000
            assertThat(captured).isBefore(Instant.now().minusSeconds(23 * 3600)); // GH-90000
        }

        @Test
        @DisplayName("returns 400 for invalid since parameter [GH-90000]")
        void returns400ForInvalidSinceParam() { // GH-90000
            HttpResponse fake400 = HttpResponse.ofCode(400).build(); // GH-90000
            when(http.errorResponse(eq(400), any())).thenReturn(fake400); // GH-90000
            when(sharedRequest.getQueryParameter("since [GH-90000]")).thenReturn("not-a-date [GH-90000]");

            Promise<HttpResponse> result = handlerWithStore.handleQueryAnomalies(sharedRequest); // GH-90000
            HttpResponse response = result.getResult(); // GH-90000
            assertThat(response.getCode()).isEqualTo(400); // GH-90000
        }
    }

    // ─── Constants ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Constants [GH-90000]")
    class ConstantsTests {

        @Test
        @DisplayName("ANOMALY_STREAM is '__anomalies' [GH-90000]")
        void anomalyStreamConstant() { // GH-90000
            assertThat(EntityAnomalyHandler.ANOMALY_STREAM).isEqualTo("__anomalies [GH-90000]");
        }

        @Test
        @DisplayName("ANOMALY_EVENT_TYPE is 'ANOMALY_DETECTED' [GH-90000]")
        void eventTypeConstant() { // GH-90000
            assertThat(EntityAnomalyHandler.ANOMALY_EVENT_TYPE).isEqualTo("ANOMALY_DETECTED [GH-90000]");
        }

        @Test
        @DisplayName("MAX_ANOMALY_QUERY_LIMIT is a positive integer [GH-90000]")
        void queryLimitConstant() { // GH-90000
            assertThat(EntityAnomalyHandler.MAX_ANOMALY_QUERY_LIMIT).isGreaterThan(0); // GH-90000
        }
    }
}
