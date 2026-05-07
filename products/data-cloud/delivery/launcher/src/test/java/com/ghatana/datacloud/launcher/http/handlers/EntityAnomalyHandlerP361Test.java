/*
 * Copyright (c) 2026 Ghatana Inc. 
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
 * ({@code GET /api/v1/anomalies}), and 501 response when no store is wired. 
 *
 * @doc.type    class
 * @doc.purpose Unit tests for EntityAnomalyHandler durable persistence and query (P3.6.1) 
 * @doc.layer   product
 * @doc.pattern Test
 */
@DisplayName("EntityAnomalyHandler – durable anomaly persistence and query (P3.6.1)")
@ExtendWith(MockitoExtension.class) 
class EntityAnomalyHandlerP361Test {

    @Mock private HttpHandlerSupport http;
    @Mock private StatisticalAnomalyDetector anomalyDetector;
    @Mock private EventLogStore eventLogStore;
    @Mock private HttpRequest sharedRequest;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules(); 

    // ─── handler with event store ─────────────────────────────────────────────

    @Nested
    @DisplayName("persistAnomalies()")
    class PersistAnomaliesTests {

        private EntityAnomalyHandler handlerWithStore;

        @BeforeEach
        void setUp() { 
            handlerWithStore = new EntityAnomalyHandler(anomalyDetector, http, eventLogStore, objectMapper); 
        }

        @Test
        @DisplayName("returns immediately when anomaly list is empty")
        void returnsImmediatelyForEmptyList() { 
            Promise<Void> result = handlerWithStore.persistAnomalies("t1", "col1", List.of()); 

            // appendBatch must never be called for an empty list
            verify(eventLogStore, never()).appendBatch(any(), any()); 
            assertThat(result).isNotNull(); 
        }

        @Test
        @DisplayName("calls appendBatch with ANOMALY_DETECTED event type for each anomaly")
        void callsAppendBatchWithCorrectEventType() { 
            Anomaly anomaly = Anomaly.builder() 
                    .anomalyId("a1")
                    .severity(Severity.HIGH) 
                    .confidence(0.9) 
                    .anomalyScore(3.5) 
                    .title("Outlier detected")
                    .affectedEntity("entity-99")
                    .detectedAt(Instant.now()) 
                    .build(); 

            List<Offset> fakeOffsets = List.of(Offset.of(42L), Offset.of(43L)); 
            when(eventLogStore.appendBatch(any(TenantContext.class), any())).thenReturn(Promise.of(fakeOffsets)); 

            handlerWithStore.persistAnomalies("tenant1", "orders", List.of(anomaly)); 

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<EventEntry>> entriesCaptor = ArgumentCaptor.forClass(List.class); 
            verify(eventLogStore).appendBatch(any(TenantContext.class), entriesCaptor.capture()); 

            List<EventEntry> captured = entriesCaptor.getValue(); 
            assertThat(captured).hasSize(1); 
            assertThat(captured.get(0).eventType()).isEqualTo(EntityAnomalyHandler.ANOMALY_EVENT_TYPE); 
        }

        @Test
        @DisplayName("includes collection and tenantId in event headers")
        void setsCollectionAndTenantInHeaders() { 
            Anomaly anomaly = Anomaly.builder() 
                    .anomalyId("a2")
                    .severity(Severity.LOW) 
                    .confidence(0.7) 
                    .anomalyScore(2.1) 
                    .title("Minor drift")
                    .affectedEntity("entity-5")
                    .detectedAt(Instant.now()) 
                    .build(); 

            when(eventLogStore.appendBatch(any(TenantContext.class), any())).thenReturn(Promise.of(List.of())); 

            handlerWithStore.persistAnomalies("myTenant", "products", List.of(anomaly)); 

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<EventEntry>> entriesCaptor = ArgumentCaptor.forClass(List.class); 
            verify(eventLogStore).appendBatch(any(TenantContext.class), entriesCaptor.capture()); 

            EventEntry entry = entriesCaptor.getValue().get(0); 
            assertThat(entry.headers()).containsEntry("collection", "products"); 
            assertThat(entry.headers()).containsEntry("tenantId", "myTenant"); 
        }

        @Test
        @DisplayName("returns immediately when eventLogStore is null (no-store constructor)")
        void returnsImmediatelyWhenNoStore() { 
            EntityAnomalyHandler noStoreHandler = new EntityAnomalyHandler(anomalyDetector, http); 

            Anomaly anomaly = Anomaly.builder() 
                    .anomalyId("a3")
                    .severity(Severity.MEDIUM) 
                    .confidence(0.8) 
                    .anomalyScore(2.8) 
                    .title("Test anomaly")
                    .affectedEntity("e-42")
                    .detectedAt(Instant.now()) 
                    .build(); 

            Promise<Void> result = noStoreHandler.persistAnomalies("t1", "c1", List.of(anomaly)); 

            verify(eventLogStore, never()).appendBatch(any(), any()); 
            assertThat(result).isNotNull(); 
        }
    }

    // ─── handleQueryAnomalies: no store ──────────────────────────────────────

    @Nested
    @DisplayName("handleQueryAnomalies() — no event store wired")
    class QueryAnomaliesNoStoreTests {

        private EntityAnomalyHandler noStoreHandler;

        @BeforeEach
        void setUp() { 
            noStoreHandler = new EntityAnomalyHandler(anomalyDetector, http); 
        }

        @Test
        @DisplayName("returns 501 when event store is not configured")
        void returns501WhenNoStore() { 
            HttpResponse expected501 = HttpResponse.ofCode(501).build(); 
            when(http.errorResponse(eq(501), any())).thenReturn(expected501); 

            Promise<HttpResponse> result = noStoreHandler.handleQueryAnomalies(sharedRequest); 

            HttpResponse response = result.getResult(); 
            assertThat(response).isNotNull(); 
            assertThat(response.getCode()).isEqualTo(501); 
        }
    }

    // ─── handleQueryAnomalies: with store ────────────────────────────────────

    @Nested
    @DisplayName("handleQueryAnomalies() — event store available")
    class QueryAnomaliesWithStoreTests {

        private EntityAnomalyHandler handlerWithStore;

        @BeforeEach
        void setUp() { 
            handlerWithStore = new EntityAnomalyHandler(anomalyDetector, http, eventLogStore, objectMapper); 
            lenient().when(http.requireTenantIdOrFail(any())).thenReturn("tenant42");
            // No 'since' param — return null to trigger 24h default
            lenient().when(sharedRequest.getQueryParameter("since")).thenReturn(null);
            lenient().when(sharedRequest.getQueryParameter("limit")).thenReturn(null);
            lenient().when(sharedRequest.getQueryParameter("collection")).thenReturn(null);
            lenient().when(sharedRequest.getPathParameter("collection")).thenReturn(null);
        }

        @Test
        @DisplayName("returns JSON with anomaly entries from readByTimeRange")
        void returnsAnomalyEntries() { 
            EventEntry entry = EventEntry.builder() 
                    .eventType(EntityAnomalyHandler.ANOMALY_EVENT_TYPE) 
                    .payload("{\"anomalyId\":\"xyz\",\"title\":\"test\"}".getBytes(StandardCharsets.UTF_8)) 
                    .headers(java.util.Map.of("collection", "orders", "tenantId", "tenant42")) 
                    .build(); 

            when(sharedRequest.getQueryParameter("collection")).thenReturn("orders");
            when(eventLogStore.readByTimeRange( 
                    any(TenantContext.class), any(Instant.class), any(Instant.class), any(int.class))) 
                    .thenReturn(Promise.of(List.of(entry))); 

            HttpResponse fakeOk = HttpResponse.ok200().build(); 
            when(http.jsonResponse(any())).thenReturn(fakeOk); 

            Promise<HttpResponse> result = handlerWithStore.handleQueryAnomalies(sharedRequest); 
            HttpResponse response = result.getResult(); 

            assertThat(response).isNotNull(); 
            verify(eventLogStore).readByTimeRange( 
                    argThat(ctx -> "tenant42".equals(ctx.tenantId())), 
                    any(Instant.class), any(Instant.class), any(int.class)); 
        }

        @Test
        @DisplayName("returns 400 when tenant header is missing")
        void returns400WhenTenantMissing() { 
            when(http.requireTenantIdOrFail(any())).thenReturn(null); 
            HttpResponse badRequest = HttpResponse.ofCode(400).build(); 
            when(http.errorResponse(400, "X-Tenant-Id header is required")).thenReturn(badRequest); 

            HttpResponse response = handlerWithStore.handleQueryAnomalies(sharedRequest).getResult(); 

            assertThat(response).isSameAs(badRequest); 
            verify(eventLogStore, never()).readByTimeRange(any(), any(), any(), any(int.class)); 
        }

        @Test
        @DisplayName("defaults to last 24h when since parameter is absent")
        void defaultsTo24HoursAgoWhenSinceAbsent() { 
            when(eventLogStore.readByTimeRange(any(), any(Instant.class), any(Instant.class), any(int.class))) 
                    .thenReturn(Promise.of(List.of())); 
            when(http.jsonResponse(any())).thenReturn(HttpResponse.ok200().build()); 

            handlerWithStore.handleQueryAnomalies(sharedRequest); 

            ArgumentCaptor<Instant> sinceCaptor = ArgumentCaptor.forClass(Instant.class); 
            verify(eventLogStore).readByTimeRange(any(), sinceCaptor.capture(), any(Instant.class), any(int.class)); 

            Instant captured = sinceCaptor.getValue(); 
            assertThat(captured).isBefore(Instant.now().minusSeconds(23 * 3600)); 
        }

        @Test
        @DisplayName("returns 400 for invalid since parameter")
        void returns400ForInvalidSinceParam() { 
            HttpResponse fake400 = HttpResponse.ofCode(400).build(); 
            when(http.errorResponse(eq(400), any())).thenReturn(fake400); 
            when(sharedRequest.getQueryParameter("since")).thenReturn("not-a-date");

            Promise<HttpResponse> result = handlerWithStore.handleQueryAnomalies(sharedRequest); 
            HttpResponse response = result.getResult(); 
            assertThat(response.getCode()).isEqualTo(400); 
        }
    }

    // ─── Constants ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Constants")
    class ConstantsTests {

        @Test
        @DisplayName("ANOMALY_STREAM is '__anomalies'")
        void anomalyStreamConstant() { 
            assertThat(EntityAnomalyHandler.ANOMALY_STREAM).isEqualTo("__anomalies");
        }

        @Test
        @DisplayName("ANOMALY_EVENT_TYPE is 'ANOMALY_DETECTED'")
        void eventTypeConstant() { 
            assertThat(EntityAnomalyHandler.ANOMALY_EVENT_TYPE).isEqualTo("ANOMALY_DETECTED");
        }

        @Test
        @DisplayName("MAX_ANOMALY_QUERY_LIMIT is a positive integer")
        void queryLimitConstant() { 
            assertThat(EntityAnomalyHandler.MAX_ANOMALY_QUERY_LIMIT).isGreaterThan(0); 
        }
    }
}
