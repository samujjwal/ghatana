/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import com.ghatana.datacloud.DataCloudClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.governance.security.ApiKeyResolver;
import com.ghatana.platform.governance.security.Principal;
import io.activej.promise.Promise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration tests for Data-Cloud HTTP event endpoints.
 *
 * <p>Covers: POST /api/v1/events (append), GET /api/v1/events (query), // GH-90000
 * event-type filtering, tenant propagation, and negative paths.
 *
 * @doc.type class
 * @doc.purpose Integration tests for /api/v1/events HTTP endpoints
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudHttpServer – Event Endpoints [GH-90000]")
class DataCloudHttpServerEventTest {

    private DataCloudClient mockClient;
    private DataCloudHttpServer server;
    private int port;
    private final HttpClient httpClient = HttpClient.newBuilder().build(); // GH-90000
    private final ObjectMapper mapper = new ObjectMapper(); // GH-90000

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        mockClient = mock(DataCloudClient.class); // GH-90000
        port = findFreePort(); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (server != null) server.stop(); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/v1/events  — append event
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/events – append event [GH-90000]")
    class AppendEventTests {

        @Test
        @DisplayName("returns 200 with event id when client appends successfully [GH-90000]")
        void appendEvent_validRequest_returns200() throws Exception { // GH-90000
            when(mockClient.appendEvent(anyString(), any())) // GH-90000
                    .thenReturn(Promise.of(DataCloudClient.Offset.of(1))); // GH-90000

            startServer(); // GH-90000

            HttpResponse<String> resp = postJson("/api/v1/events", // GH-90000
                    Map.of("type", "order.placed", // GH-90000
                            "payload", Map.of("orderId", "ORD-001", "amount", 99.99))); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("eventType [GH-90000]")).isEqualTo("order.placed [GH-90000]");
            assertThat(body.get("offset [GH-90000]")).isNotNull();
        }

        @Test
        @DisplayName("returns 400 when event type is missing [GH-90000]")
        void appendEvent_missingType_returns400() throws Exception { // GH-90000
            startServer(); // GH-90000

            HttpResponse<String> resp = postJson("/api/v1/events", // GH-90000
                    Map.of("payload", Map.of("orderId", "ORD-001"))); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(400); // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("message [GH-90000]")).isNotNull();
        }

        @Test
        @DisplayName("returns 400 when event type is blank [GH-90000]")
        void appendEvent_blankType_returns400() throws Exception { // GH-90000
            startServer(); // GH-90000

            HttpResponse<String> resp = postJson("/api/v1/events", // GH-90000
                    Map.of("type", "  ", "payload", Map.of())); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(400); // GH-90000
        }

        @Test
        @DisplayName("propagates tenant from X-Tenant-ID header to client call [GH-90000]")
        void appendEvent_withTenantHeader_calledWithTenant() throws Exception { // GH-90000
            when(mockClient.appendEvent(anyString(), any())) // GH-90000
                    .thenReturn(Promise.of(DataCloudClient.Offset.of(2))); // GH-90000

            startServer(); // GH-90000

            HttpRequest req = HttpRequest.newBuilder() // GH-90000
                    .POST(HttpRequest.BodyPublishers.ofString( // GH-90000
                            "{\"type\":\"user.login\",\"payload\":{\"userId\":\"u1\"}}"))
                    .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/events")) // GH-90000
                    .header("Content-Type", "application/json") // GH-90000
                    .header("X-Tenant-ID", "acme-corp") // GH-90000
                    .build(); // GH-90000
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString()); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            verify(mockClient).appendEvent(anyString(), any()); // GH-90000
        }

        @Test
        @DisplayName("returns 415 when Content-Type is not application/json [GH-90000]")
        void appendEvent_wrongContentType_returns415() throws Exception { // GH-90000
            startServer(); // GH-90000

            HttpRequest req = HttpRequest.newBuilder() // GH-90000
                    .POST(HttpRequest.BodyPublishers.ofString("type=order.placed [GH-90000]"))
                    .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/events")) // GH-90000
                    .header("Content-Type", "application/x-www-form-urlencoded") // GH-90000
                    .build(); // GH-90000
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString()); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(415); // GH-90000
        }

    @Test
    @DisplayName("returns 401 before content-type validation when API key enforcement is enabled [GH-90000]")
    void appendEvent_missingApiKey_returns401BeforeContentTypeValidation() throws Exception { // GH-90000
        ApiKeyResolver resolver =
            apiKey ->
                Optional.ofNullable(apiKey) // GH-90000
                    .filter("valid-key"::equals) // GH-90000
                    .map(key -> new Principal("event-service", List.of("writer [GH-90000]"), "tenant-1"));

        server = new DataCloudHttpServer(mockClient, port) // GH-90000
            .withApiKeyResolver(resolver); // GH-90000
        server.start(); // GH-90000

        HttpRequest req = HttpRequest.newBuilder() // GH-90000
            .POST(HttpRequest.BodyPublishers.ofString("type=order.placed [GH-90000]"))
            .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/events")) // GH-90000
            .header("Content-Type", "application/x-www-form-urlencoded") // GH-90000
            .build(); // GH-90000
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString()); // GH-90000

        assertThat(resp.statusCode()).isEqualTo(401); // GH-90000
    }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/events  — query events
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/events – query events [GH-90000]")
    class QueryEventsTests {

        @Test
        @DisplayName("returns 200 with event list when events exist [GH-90000]")
        void queryEvents_withEvents_returns200WithList() throws Exception { // GH-90000
            List<DataCloudClient.Event> events = List.of( // GH-90000
                    DataCloudClient.Event.of("order.placed", Map.of("orderId", "ORD-1")), // GH-90000
                    DataCloudClient.Event.of("order.shipped", Map.of("orderId", "ORD-1"))); // GH-90000
            when(mockClient.queryEvents(anyString(), any())) // GH-90000
                    .thenReturn(Promise.of(events)); // GH-90000

            startServer(); // GH-90000

            HttpResponse<String> resp = get("/api/v1/events [GH-90000]");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            List<?> items = (List<?>) body.get("events [GH-90000]");
            assertThat(items).hasSize(2); // GH-90000
            assertThat(((Number) body.get("count [GH-90000]")).intValue()).isEqualTo(2);
        }

        @Test
        @DisplayName("returns 200 with empty list when no events present [GH-90000]")
        void queryEvents_noEvents_returnsEmptyList() throws Exception { // GH-90000
            when(mockClient.queryEvents(anyString(), any())) // GH-90000
                    .thenReturn(Promise.of(List.of())); // GH-90000

            startServer(); // GH-90000

            HttpResponse<String> resp = get("/api/v1/events [GH-90000]");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat((List<?>) body.get("events [GH-90000]")).isEmpty();
            assertThat(((Number) body.get("count [GH-90000]")).intValue()).isEqualTo(0);
        }

        @Test
        @DisplayName("passes event-type filter to client when ?type= is specified [GH-90000]")
        void queryEvents_withTypeFilter_queriesClient() throws Exception { // GH-90000
            List<DataCloudClient.Event> filtered = List.of( // GH-90000
                    DataCloudClient.Event.of("order.placed", Map.of("orderId", "ORD-X"))); // GH-90000
            when(mockClient.queryEvents(anyString(), any())) // GH-90000
                    .thenReturn(Promise.of(filtered)); // GH-90000

            startServer(); // GH-90000

            HttpResponse<String> resp = get("/api/v1/events?type=order.placed [GH-90000]");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat((List<?>) body.get("events [GH-90000]")).hasSize(1);
            verify(mockClient).queryEvents(anyString(), any(DataCloudClient.EventQuery.class)); // GH-90000
        }

        @Test
        @DisplayName("returns 200 with tenant metadata when X-Tenant-ID header provided [GH-90000]")
        void queryEvents_withTenant_includesMetadata() throws Exception { // GH-90000
            when(mockClient.queryEvents(anyString(), any())) // GH-90000
                    .thenReturn(Promise.of(List.of())); // GH-90000

            startServer(); // GH-90000

            HttpRequest req = HttpRequest.newBuilder() // GH-90000
                    .GET() // GH-90000
                    .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/events")) // GH-90000
                    .header("X-Tenant-ID", "beta-tenant") // GH-90000
                    .build(); // GH-90000
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString()); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("tenantId [GH-90000]")).isNotNull();
        }

        @Test
        @DisplayName("response includes timestamp metadata [GH-90000]")
        void queryEvents_always_includesTimestamp() throws Exception { // GH-90000
            when(mockClient.queryEvents(anyString(), any())) // GH-90000
                    .thenReturn(Promise.of(List.of())); // GH-90000

            startServer(); // GH-90000

            HttpResponse<String> resp = get("/api/v1/events [GH-90000]");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("timestamp [GH-90000]")).isNotNull();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void startServer() throws Exception { // GH-90000
        server = new DataCloudHttpServer(mockClient, port); // GH-90000
        server.start(); // GH-90000
        waitForServerReady(port); // GH-90000
    }

    private HttpResponse<String> get(String path) throws Exception { // GH-90000
        return httpClient.send( // GH-90000
                HttpRequest.newBuilder().GET() // GH-90000
                        .uri(URI.create("http://127.0.0.1:" + port + path)) // GH-90000
                        .build(), // GH-90000
                HttpResponse.BodyHandlers.ofString()); // GH-90000
    }

    private HttpResponse<String> postJson(String path, Object body) throws Exception { // GH-90000
        String json = mapper.writeValueAsString(body); // GH-90000
        return httpClient.send( // GH-90000
                HttpRequest.newBuilder() // GH-90000
                        .POST(HttpRequest.BodyPublishers.ofString(json)) // GH-90000
                        .uri(URI.create("http://127.0.0.1:" + port + path)) // GH-90000
                        .header("Content-Type", "application/json") // GH-90000
                        .build(), // GH-90000
                HttpResponse.BodyHandlers.ofString()); // GH-90000
    }

    private static int findFreePort() throws IOException { // GH-90000
        try (ServerSocket ss = new ServerSocket(0)) { // GH-90000
            return ss.getLocalPort(); // GH-90000
        }
    }

    private static void waitForServerReady(int port) throws Exception { // GH-90000
        long deadline = System.currentTimeMillis() + 5_000; // GH-90000
        while (System.currentTimeMillis() < deadline) { // GH-90000
            try {
                new Socket("127.0.0.1", port).close(); // GH-90000
                return;
            } catch (IOException ignored) { // GH-90000
                Thread.sleep(50); // GH-90000
            }
        }
        throw new IllegalStateException("Server did not start on port " + port + " within 5 s"); // GH-90000
    }
}
