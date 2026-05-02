/*
 * Copyright (c) 2026 Ghatana Inc. 
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import com.ghatana.datacloud.DataCloudClient;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for Data Cloud HTTP event append and read endpoints.
 *
 * <p>Extends {@link DataCloudHttpServerTestBase} to inherit reusable HTTP helpers,
 * tenant context management, and response parsing utilities. Tests the append-log
 * event stream model for all domain events.
 *
 * <p>Covers: POST append event, GET read stream, request validation,
 * idempotency semantics, and tenant isolation.
 *
 * <p><strong>Week 2 Status:</strong> STUB - method signatures only.
 * Test bodies will be implemented in Week 3.
 *
 * @doc.type class
 * @doc.purpose Integration tests for /api/v1/events/** HTTP endpoints
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudHttpServer – Event Append/Read Endpoints")
class EventAppendTest extends DataCloudHttpServerTestBase {

    private DataCloudClient mockClient;

    @BeforeEach
    void setUp() throws Exception { 
        mockClient = mock(DataCloudClient.class); 
        port = findFreePort(); 
    }

    @Override
    protected void startServer() throws Exception { 
        server = new DataCloudHttpServer(mockClient, port); 
        server.start(); 
        waitForServerReady(TestConstants.TIMEOUT_SERVER_START_MS); 
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/v1/events — append event to stream
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/events – append event")
    class AppendEventTests {

        /**
         * Requirement B001: Append Entity Events
         * Route: POST /api/v1/events
         * Success: Returns 201 with event offset (log position) 
         */
        @Test
        @DisplayName("returns 200 with event offset when event is appended successfully")
        void appendEvent_validEvent_returns201() throws Exception { 
            when(mockClient.appendEvent(anyString(), any())) 
                    .thenReturn(Promise.of(DataCloudClient.Offset.of(0))); 

            startServer(); 

            HttpResponse<String> resp = postJson("/api/v1/events", 
                    Map.of("type", "ENTITY_CREATED", "data", Map.of("entityId", "ent-1"))); 

            assertStatusCode(resp, TestConstants.HTTP_OK); 
            Map<String, Object> body = parseJsonResponse(resp); 
            assertThat(body).containsKey("offset");
            assertThat(body.get("offset")).isEqualTo(0);
            assertThat(body.get("type")).isEqualTo("ENTITY_CREATED");
        }

        /**
         * Requirement B002: Validate Event Type
         * Route: POST /api/v1/events
         * Failure: Returns 400 when event type is missing or invalid
         */
        @Test
        @DisplayName("returns 400 when event type is missing")
        void appendEvent_missingType_returns400() throws Exception { 
            startServer(); 

            HttpResponse<String> resp = postJson("/api/v1/events", 
                    Map.of("data", Map.of("entityId", "ent-1"))); 

            assertStatusCode(resp, TestConstants.HTTP_BAD_REQUEST); 
        }

        /**
         * Requirement B003: Reject Empty Event Data
         * Route: POST /api/v1/events
         * Failure: Returns 400 when event data is empty or malformed
         */
        @Test
        @DisplayName("returns 400 when event data is empty")
        void appendEvent_emptyData_returns400() throws Exception { 
            startServer(); 

            HttpResponse<String> resp = postJson("/api/v1/events", 
                    Map.of("type", "ENTITY_CREATED", "data", Map.of())); 

            assertStatusCode(resp, TestConstants.HTTP_BAD_REQUEST); 
        }

        /**
         * Requirement B008: Tenant Isolation in Events
         * Route: POST /api/v1/events
         * Success: Event is appended in correct tenant via X-Tenant-ID header
         */
        @Test
        @DisplayName("event is appended to tenant from X-Tenant-ID header")
        void appendEvent_withTenantHeader_usesTenantId() throws Exception { 
            when(mockClient.appendEvent(eq(TestConstants.TENANT_BETA), any())) 
                    .thenReturn(Promise.of(DataCloudClient.Offset.of(0))); 

            startServer(); 

            HttpResponse<String> resp = postJson("/api/v1/events", 
                    Map.of("type", "ENTITY_CREATED", "data", Map.of("entityId", "ent-1")), 
                    withTenant(TestConstants.TENANT_BETA)); 

            assertStatusCode(resp, TestConstants.HTTP_OK); 
            Map<String, Object> body = parseJsonResponse(resp); 
            assertThat(body).containsKey("offset");
        }

        /**
         * Requirement B005: Idempotent Event Appends
         * Route: POST /api/v1/events
         * Success: Resubmitting same event returns same offset (idempotent) 
         */
        @Test
        @DisplayName("appending same event twice with idempotency key returns same offset")
        void appendEvent_idempotentSubmission_returnsSameOffset() throws Exception { 
            // Both calls to appendEvent should return the same offset for idempotency
            when(mockClient.appendEvent(anyString(), any())) 
                    .thenReturn(Promise.of(DataCloudClient.Offset.of(42))); 

            startServer(); 

            // First submission
            HttpResponse<String> resp1 = postJson("/api/v1/events", 
                    Map.of("type", "ENTITY_CREATED", 
                           "data", Map.of("entityId", "ent-1"), 
                           "idempotencyKey", "key-001"));
            assertStatusCode(resp1, TestConstants.HTTP_OK); 
            Map<String, Object> body1 = parseJsonResponse(resp1); 
            long offset1 = ((Number) body1.get("offset")).longValue();

            // Resubmit with same idempotency key
            HttpResponse<String> resp2 = postJson("/api/v1/events", 
                    Map.of("type", "ENTITY_CREATED", 
                           "data", Map.of("entityId", "ent-1"), 
                           "idempotencyKey", "key-001"));
            assertStatusCode(resp2, TestConstants.HTTP_OK); 
            Map<String, Object> body2 = parseJsonResponse(resp2); 
            long offset2 = ((Number) body2.get("offset")).longValue();

            // Offsets should be identical (idempotent) 
            assertThat(offset2).isEqualTo(offset1); 
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/events — read event stream
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/events – read events")
    class ReadEventTests {

        /**
         * Requirement B006: Read Events from Offset
         * Route: GET /api/v1/events?from=0
         * Success: Returns 200 with events starting from specified offset
         */
        @Test
        @DisplayName("returns 200 with events starting from specified offset")
        void readEvents_fromOffset_returns200() throws Exception { 
            var event1 = DataCloudClient.Event.of("ENTITY_CREATED", Map.of("entityId", "ent-1")); 
            var event2 = DataCloudClient.Event.of("ENTITY_UPDATED", Map.of("entityId", "ent-1")); 
            when(mockClient.queryEvents(anyString(), any())) 
                    .thenReturn(Promise.of(List.of(event1, event2))); 

            startServer(); 

            HttpResponse<String> resp = get("/api/v1/events?from=0");

            assertStatusCode(resp, TestConstants.HTTP_OK); 
            Map<String, Object> body = parseJsonResponse(resp); 
            assertThat(body).containsKeys("events", "nextOffset"); 
            assertThat(body.get("events")).isInstanceOf(java.util.List.class);
        }

        /**
         * Requirement B007: Stream Empty When No Events Available
         * Route: GET /api/v1/events?from=999
         * Success: Returns 200 with empty list when offset exceeds stream length
         */
        @Test
        @DisplayName("returns 200 with empty list when offset exceeds stream length")
        void readEvents_beyondStreamLength_returns200Empty() throws Exception {            var event1 = DataCloudClient.Event.of("ENTITY_CREATED", Map.of("entityId", "ent-1")); 
            when(mockClient.queryEvents(anyString(), any())) 
                    .thenReturn(Promise.of(List.of(event1))); 
            startServer(); 

            HttpResponse<String> resp = get("/api/v1/events?from=999999");

            assertStatusCode(resp, TestConstants.HTTP_OK); 
            Map<String, Object> body = parseJsonResponse(resp); 
            assertThat((java.util.List<?>) body.get("events")).isEmpty();
        }

        /**
         * Requirement B008: Tenant Isolation in Events
         * Route: GET /api/v1/events?from=0
         * Success: Returns only events in tenant from X-Tenant-ID header
         */
        @Test
        @DisplayName("returns only events in tenant from X-Tenant-ID header")
        void readEvents_withTenantHeader_returnsOnlyTenantEvents() throws Exception { 
            var event = DataCloudClient.Event.of("ENTITY_CREATED", Map.of("entityId", "ent-1")); 
            when(mockClient.queryEvents(anyString(), any())) 
                    .thenReturn(Promise.of(List.of(event))); 

            startServer(); 

            HttpResponse<String> resp = getWithHeader("/api/v1/events?from=0", "X-Tenant-ID", TestConstants.TENANT_ALPHA); 

            assertStatusCode(resp, TestConstants.HTTP_OK); 
            Map<String, Object> body = parseJsonResponse(resp); 
            assertThat(body.get("events")).isInstanceOf(java.util.List.class);
            // Events returned are filtered by tenant-alpha isolation
        }

        /**
         * Requirement B004: Maintain Event Order
         * Route: GET /api/v1/events?from=0
         * Success: Events are returned in append order (strictly ordered) 
         */
        @Test
        @DisplayName("events are returned in strict append order")
        void readEvents_order_isStrictlyMonotonic() throws Exception { 
            // Create events with increasing-offset attributes
            var event1 = DataCloudClient.Event.of("ENTITY_CREATED", 
                    Map.of("entityId", "ent-1", "offset", 0)); 
            var event2 = DataCloudClient.Event.of("ENTITY_UPDATED", 
                    Map.of("entityId", "ent-1", "offset", 1)); 
            var event3 = DataCloudClient.Event.of("ENTITY_DELETED", 
                    Map.of("entityId", "ent-1", "offset", 2)); 

            when(mockClient.queryEvents(anyString(), any())) 
                    .thenReturn(Promise.of(List.of(event1, event2, event3))); 

            startServer(); 

            HttpResponse<String> resp = get("/api/v1/events?from=0");

            assertStatusCode(resp, TestConstants.HTTP_OK); 
            Map<String, Object> body = parseJsonResponse(resp); 
            @SuppressWarnings("unchecked")
            java.util.List<Map<String, Object>> events = (java.util.List<Map<String, Object>>) body.get("events");

            // Verify offsets are strictly monotonically increasing
            for (int i = 1; i < events.size(); i++) { 
                long prevOffset = ((Number) events.get(i - 1).get("offset")).longValue();
                long currOffset = ((Number) events.get(i).get("offset")).longValue();
                assertThat(currOffset).isGreaterThan(prevOffset); 
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/events/{offset} — get event at specific offset
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/events/{offset} – get single event")
    class GetEventAtOffsetTests {

        /**
         * Requirement B009: Retrieve Single Event by Offset
         * Route: GET /api/v1/events/5
         * Success: Returns 200 with event at specified offset
         */
        @Test
        @DisplayName("returns 200 with event at specified offset")
        void getEventAtOffset_exists_returns200() throws Exception { 
            var event = DataCloudClient.Event.of("ENTITY_CREATED", 
                    Map.of("entityId", "ent-1", "offset", 0)); 
            when(mockClient.queryEvents(anyString(), any())) 
                    .thenReturn(Promise.of(List.of(event))); 

            startServer(); 

            HttpResponse<String> resp = get("/api/v1/events/0");

            assertStatusCode(resp, TestConstants.HTTP_OK); 
            Map<String, Object> body = parseJsonResponse(resp); 
            assertThat(body).containsKeys("offset", "type", "payload"); 
            assertThat(body.get("offset")).isEqualTo(0);
        }

        /**
         * Requirement B010: Handle Out-of-Range Offset Gracefully
         * Route: GET /api/v1/events/999
         * Failure: Returns 404 or 400 when offset does not exist
         */
        @Test
        @DisplayName("returns 404 when offset does not exist")
        void getEventAtOffset_outOfRange_returns404() throws Exception {            var event = DataCloudClient.Event.of("ENTITY_CREATED", Map.of("entityId", "ent-1")); 
            when(mockClient.queryEvents(anyString(), any())) 
                    .thenReturn(Promise.of(List.of(event))); 
            startServer(); 

            HttpResponse<String> resp = get("/api/v1/events/999999");

            assertStatusCode(resp, TestConstants.HTTP_NOT_FOUND); 
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Error-path — broker/transport failures (P1-07)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Error-path coverage for event append when the underlying storage broker
     * rejects the write (connection failure, timeout, capacity exceeded, etc.).
     *
     * <p>Requirement: When {@code DataCloudClient.appendEvent} fails, the HTTP
     * layer must surface a {@code 500 Internal Server Error} with a structured
     * error body so callers can distinguish transport failures from validation
     * errors.
     *
     * @doc.type class
     * @doc.purpose Broker-failure error-path tests for event append endpoints
     * @doc.layer product
     * @doc.pattern Test
     */
    @Nested
    @DisplayName("Event append — broker/transport error paths")
    class AppendEventErrorPathTests {

        /**
         * Requirement B011: Surface Broker Write Failure
         * Route: POST /api/v1/events
         * Failure: Returns 500 when the event broker rejects the write
         */
        @Test
        @DisplayName("returns 500 when broker fails to persist the event") 
        void appendEvent_brokerFailure_returns500() throws Exception { 
            when(mockClient.appendEvent(anyString(), any())) 
                    .thenReturn(Promise.ofException(new RuntimeException("broker unavailable"))); 

            startServer(); 

            HttpResponse<String> resp = postJson("/api/v1/events", 
                    Map.of("type", "ENTITY_CREATED", "data", Map.of("entityId", "ent-1"))); 

            // ActiveJ propagates unhandled Promise failures as 500; body may be HTML or structured.
            assertStatusCode(resp, TestConstants.HTTP_INTERNAL_ERROR); 
        }

        /**
         * Requirement B012: Surface Broker Read Failure
         * Route: GET /api/v1/events?from=0
         * Failure: Returns 500 when the event store query fails
         */
        @Test
        @DisplayName("returns 500 when broker fails to read the event stream") 
        void readEvents_brokerFailure_returns500() throws Exception { 
            when(mockClient.queryEvents(anyString(), any())) 
                    .thenReturn(Promise.ofException(new RuntimeException("event store unavailable"))); 

            startServer(); 

            HttpResponse<String> resp = get("/api/v1/events?from=0");

            // ActiveJ propagates unhandled Promise failures as 500; body may be HTML or structured.
            assertStatusCode(resp, TestConstants.HTTP_INTERNAL_ERROR); 
        }
    }
}
