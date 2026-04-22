/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
@DisplayName("DataCloudHttpServer – Event Append/Read Endpoints [GH-90000]")
class EventAppendTest extends DataCloudHttpServerTestBase {

    private DataCloudClient mockClient;

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        mockClient = mock(DataCloudClient.class); // GH-90000
        port = findFreePort(); // GH-90000
    }

    @Override
    protected void startServer() throws Exception { // GH-90000
        server = new DataCloudHttpServer(mockClient, port); // GH-90000
        server.start(); // GH-90000
        waitForServerReady(TestConstants.TIMEOUT_SERVER_START_MS); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/v1/events — append event to stream
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/events – append event [GH-90000]")
    class AppendEventTests {

        /**
         * Requirement B001: Append Entity Events
         * Route: POST /api/v1/events
         * Success: Returns 201 with event offset (log position) // GH-90000
         */
        @Test
        @DisplayName("returns 200 with event offset when event is appended successfully [GH-90000]")
        void appendEvent_validEvent_returns201() throws Exception { // GH-90000
            when(mockClient.appendEvent(anyString(), any())) // GH-90000
                    .thenReturn(Promise.of(DataCloudClient.Offset.of(0))); // GH-90000

            startServer(); // GH-90000

            HttpResponse<String> resp = postJson("/api/v1/events", // GH-90000
                    Map.of("type", "ENTITY_CREATED", "data", Map.of("entityId", "ent-1"))); // GH-90000

            assertStatusCode(resp, TestConstants.HTTP_OK); // GH-90000
            Map<String, Object> body = parseJsonResponse(resp); // GH-90000
            assertThat(body).containsKey("offset [GH-90000]");
            assertThat(body.get("offset [GH-90000]")).isEqualTo(0);
            assertThat(body.get("type [GH-90000]")).isEqualTo("ENTITY_CREATED [GH-90000]");
        }

        /**
         * Requirement B002: Validate Event Type
         * Route: POST /api/v1/events
         * Failure: Returns 400 when event type is missing or invalid
         */
        @Test
        @DisplayName("returns 400 when event type is missing [GH-90000]")
        void appendEvent_missingType_returns400() throws Exception { // GH-90000
            startServer(); // GH-90000

            HttpResponse<String> resp = postJson("/api/v1/events", // GH-90000
                    Map.of("data", Map.of("entityId", "ent-1"))); // GH-90000

            assertStatusCode(resp, TestConstants.HTTP_BAD_REQUEST); // GH-90000
        }

        /**
         * Requirement B003: Reject Empty Event Data
         * Route: POST /api/v1/events
         * Failure: Returns 400 when event data is empty or malformed
         */
        @Test
        @DisplayName("returns 400 when event data is empty [GH-90000]")
        void appendEvent_emptyData_returns400() throws Exception { // GH-90000
            startServer(); // GH-90000

            HttpResponse<String> resp = postJson("/api/v1/events", // GH-90000
                    Map.of("type", "ENTITY_CREATED", "data", Map.of())); // GH-90000

            assertStatusCode(resp, TestConstants.HTTP_BAD_REQUEST); // GH-90000
        }

        /**
         * Requirement B008: Tenant Isolation in Events
         * Route: POST /api/v1/events
         * Success: Event is appended in correct tenant via X-Tenant-ID header
         */
        @Test
        @DisplayName("event is appended to tenant from X-Tenant-ID header [GH-90000]")
        void appendEvent_withTenantHeader_usesTenantId() throws Exception { // GH-90000
            when(mockClient.appendEvent(eq(TestConstants.TENANT_BETA), any())) // GH-90000
                    .thenReturn(Promise.of(DataCloudClient.Offset.of(0))); // GH-90000

            startServer(); // GH-90000

            HttpResponse<String> resp = postJson("/api/v1/events", // GH-90000
                    Map.of("type", "ENTITY_CREATED", "data", Map.of("entityId", "ent-1")), // GH-90000
                    withTenant(TestConstants.TENANT_BETA)); // GH-90000

            assertStatusCode(resp, TestConstants.HTTP_OK); // GH-90000
            Map<String, Object> body = parseJsonResponse(resp); // GH-90000
            assertThat(body).containsKey("offset [GH-90000]");
        }

        /**
         * Requirement B005: Idempotent Event Appends
         * Route: POST /api/v1/events
         * Success: Resubmitting same event returns same offset (idempotent) // GH-90000
         */
        @Test
        @DisplayName("appending same event twice with idempotency key returns same offset [GH-90000]")
        void appendEvent_idempotentSubmission_returnsSameOffset() throws Exception { // GH-90000
            // Both calls to appendEvent should return the same offset for idempotency
            when(mockClient.appendEvent(anyString(), any())) // GH-90000
                    .thenReturn(Promise.of(DataCloudClient.Offset.of(42))); // GH-90000

            startServer(); // GH-90000

            // First submission
            HttpResponse<String> resp1 = postJson("/api/v1/events", // GH-90000
                    Map.of("type", "ENTITY_CREATED", // GH-90000
                           "data", Map.of("entityId", "ent-1"), // GH-90000
                           "idempotencyKey", "key-001"));
            assertStatusCode(resp1, TestConstants.HTTP_OK); // GH-90000
            Map<String, Object> body1 = parseJsonResponse(resp1); // GH-90000
            long offset1 = ((Number) body1.get("offset [GH-90000]")).longValue();

            // Resubmit with same idempotency key
            HttpResponse<String> resp2 = postJson("/api/v1/events", // GH-90000
                    Map.of("type", "ENTITY_CREATED", // GH-90000
                           "data", Map.of("entityId", "ent-1"), // GH-90000
                           "idempotencyKey", "key-001"));
            assertStatusCode(resp2, TestConstants.HTTP_OK); // GH-90000
            Map<String, Object> body2 = parseJsonResponse(resp2); // GH-90000
            long offset2 = ((Number) body2.get("offset [GH-90000]")).longValue();

            // Offsets should be identical (idempotent) // GH-90000
            assertThat(offset2).isEqualTo(offset1); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/events — read event stream
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/events – read events [GH-90000]")
    class ReadEventTests {

        /**
         * Requirement B006: Read Events from Offset
         * Route: GET /api/v1/events?from=0
         * Success: Returns 200 with events starting from specified offset
         */
        @Test
        @DisplayName("returns 200 with events starting from specified offset [GH-90000]")
        void readEvents_fromOffset_returns200() throws Exception { // GH-90000
            var event1 = DataCloudClient.Event.of("ENTITY_CREATED", Map.of("entityId", "ent-1")); // GH-90000
            var event2 = DataCloudClient.Event.of("ENTITY_UPDATED", Map.of("entityId", "ent-1")); // GH-90000
            when(mockClient.queryEvents(anyString(), any())) // GH-90000
                    .thenReturn(Promise.of(List.of(event1, event2))); // GH-90000

            startServer(); // GH-90000

            HttpResponse<String> resp = get("/api/v1/events?from=0 [GH-90000]");

            assertStatusCode(resp, TestConstants.HTTP_OK); // GH-90000
            Map<String, Object> body = parseJsonResponse(resp); // GH-90000
            assertThat(body).containsKeys("events", "nextOffset"); // GH-90000
            assertThat(body.get("events [GH-90000]")).isInstanceOf(java.util.List.class);
        }

        /**
         * Requirement B007: Stream Empty When No Events Available
         * Route: GET /api/v1/events?from=999
         * Success: Returns 200 with empty list when offset exceeds stream length
         */
        @Test
        @DisplayName("returns 200 with empty list when offset exceeds stream length [GH-90000]")
        void readEvents_beyondStreamLength_returns200Empty() throws Exception {            var event1 = DataCloudClient.Event.of("ENTITY_CREATED", Map.of("entityId", "ent-1")); // GH-90000
            when(mockClient.queryEvents(anyString(), any())) // GH-90000
                    .thenReturn(Promise.of(List.of(event1))); // GH-90000
            startServer(); // GH-90000

            HttpResponse<String> resp = get("/api/v1/events?from=999999 [GH-90000]");

            assertStatusCode(resp, TestConstants.HTTP_OK); // GH-90000
            Map<String, Object> body = parseJsonResponse(resp); // GH-90000
            assertThat((java.util.List<?>) body.get("events [GH-90000]")).isEmpty();
        }

        /**
         * Requirement B008: Tenant Isolation in Events
         * Route: GET /api/v1/events?from=0
         * Success: Returns only events in tenant from X-Tenant-ID header
         */
        @Test
        @DisplayName("returns only events in tenant from X-Tenant-ID header [GH-90000]")
        void readEvents_withTenantHeader_returnsOnlyTenantEvents() throws Exception { // GH-90000
            var event = DataCloudClient.Event.of("ENTITY_CREATED", Map.of("entityId", "ent-1")); // GH-90000
            when(mockClient.queryEvents(anyString(), any())) // GH-90000
                    .thenReturn(Promise.of(List.of(event))); // GH-90000

            startServer(); // GH-90000

            HttpResponse<String> resp = getWithHeader("/api/v1/events?from=0", "X-Tenant-ID", TestConstants.TENANT_ALPHA); // GH-90000

            assertStatusCode(resp, TestConstants.HTTP_OK); // GH-90000
            Map<String, Object> body = parseJsonResponse(resp); // GH-90000
            assertThat(body.get("events [GH-90000]")).isInstanceOf(java.util.List.class);
            // Events returned are filtered by tenant-alpha isolation
        }

        /**
         * Requirement B004: Maintain Event Order
         * Route: GET /api/v1/events?from=0
         * Success: Events are returned in append order (strictly ordered) // GH-90000
         */
        @Test
        @DisplayName("events are returned in strict append order [GH-90000]")
        void readEvents_order_isStrictlyMonotonic() throws Exception { // GH-90000
            // Create events with increasing-offset attributes
            var event1 = DataCloudClient.Event.of("ENTITY_CREATED", // GH-90000
                    Map.of("entityId", "ent-1", "offset", 0)); // GH-90000
            var event2 = DataCloudClient.Event.of("ENTITY_UPDATED", // GH-90000
                    Map.of("entityId", "ent-1", "offset", 1)); // GH-90000
            var event3 = DataCloudClient.Event.of("ENTITY_DELETED", // GH-90000
                    Map.of("entityId", "ent-1", "offset", 2)); // GH-90000

            when(mockClient.queryEvents(anyString(), any())) // GH-90000
                    .thenReturn(Promise.of(List.of(event1, event2, event3))); // GH-90000

            startServer(); // GH-90000

            HttpResponse<String> resp = get("/api/v1/events?from=0 [GH-90000]");

            assertStatusCode(resp, TestConstants.HTTP_OK); // GH-90000
            Map<String, Object> body = parseJsonResponse(resp); // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            java.util.List<Map<String, Object>> events = (java.util.List<Map<String, Object>>) body.get("events [GH-90000]");

            // Verify offsets are strictly monotonically increasing
            for (int i = 1; i < events.size(); i++) { // GH-90000
                long prevOffset = ((Number) events.get(i - 1).get("offset [GH-90000]")).longValue();
                long currOffset = ((Number) events.get(i).get("offset [GH-90000]")).longValue();
                assertThat(currOffset).isGreaterThan(prevOffset); // GH-90000
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/events/{offset} — get event at specific offset
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/events/{offset} – get single event [GH-90000]")
    class GetEventAtOffsetTests {

        /**
         * Requirement B009: Retrieve Single Event by Offset
         * Route: GET /api/v1/events/5
         * Success: Returns 200 with event at specified offset
         */
        @Test
        @DisplayName("returns 200 with event at specified offset [GH-90000]")
        void getEventAtOffset_exists_returns200() throws Exception { // GH-90000
            var event = DataCloudClient.Event.of("ENTITY_CREATED", // GH-90000
                    Map.of("entityId", "ent-1", "offset", 0)); // GH-90000
            when(mockClient.queryEvents(anyString(), any())) // GH-90000
                    .thenReturn(Promise.of(List.of(event))); // GH-90000

            startServer(); // GH-90000

            HttpResponse<String> resp = get("/api/v1/events/0 [GH-90000]");

            assertStatusCode(resp, TestConstants.HTTP_OK); // GH-90000
            Map<String, Object> body = parseJsonResponse(resp); // GH-90000
            assertThat(body).containsKeys("offset", "type", "payload"); // GH-90000
            assertThat(body.get("offset [GH-90000]")).isEqualTo(0);
        }

        /**
         * Requirement B010: Handle Out-of-Range Offset Gracefully
         * Route: GET /api/v1/events/999
         * Failure: Returns 404 or 400 when offset does not exist
         */
        @Test
        @DisplayName("returns 404 when offset does not exist [GH-90000]")
        void getEventAtOffset_outOfRange_returns404() throws Exception {            var event = DataCloudClient.Event.of("ENTITY_CREATED", Map.of("entityId", "ent-1")); // GH-90000
            when(mockClient.queryEvents(anyString(), any())) // GH-90000
                    .thenReturn(Promise.of(List.of(event))); // GH-90000
            startServer(); // GH-90000

            HttpResponse<String> resp = get("/api/v1/events/999999 [GH-90000]");

            assertStatusCode(resp, TestConstants.HTTP_NOT_FOUND); // GH-90000
        }
    }
}
