/*
 * Copyright (c) 2026 Ghatana Inc. 
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
 * <p>Covers: POST /api/v1/events (append), GET /api/v1/events (query), 
 * event-type filtering, tenant propagation, and negative paths.
 *
 * @doc.type class
 * @doc.purpose Integration tests for /api/v1/events HTTP endpoints
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudHttpServer – Event Endpoints")
class DataCloudHttpServerEventTest {

    private DataCloudClient mockClient;
    private DataCloudHttpServer server;
    private int port;
    private final HttpClient httpClient = HttpClient.newBuilder().build(); 
    private final ObjectMapper mapper = new ObjectMapper(); 

    @BeforeEach
    void setUp() throws Exception { 
        mockClient = mock(DataCloudClient.class); 
        port = findFreePort(); 
    }

    @AfterEach
    void tearDown() { 
        if (server != null) server.stop(); 
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/v1/events  — append event
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/events – append event")
    class AppendEventTests {

        @Test
        @DisplayName("returns 200 with event id when client appends successfully")
        void appendEvent_validRequest_returns200() throws Exception { 
            when(mockClient.appendEvent(anyString(), any())) 
                    .thenReturn(Promise.of(DataCloudClient.Offset.of(1))); 

            startServer(); 

            HttpResponse<String> resp = postJson("/api/v1/events", 
                    Map.of("type", "order.placed", 
                            "payload", Map.of("orderId", "ORD-001", "amount", 99.99))); 

            assertThat(resp.statusCode()).isEqualTo(200); 
            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class); 
            assertThat(body.get("eventType")).isEqualTo("order.placed");
            assertThat(body.get("offset")).isNotNull();
        }

        @Test
        @DisplayName("returns 400 when event type is missing")
        void appendEvent_missingType_returns400() throws Exception { 
            startServer(); 

            HttpResponse<String> resp = postJson("/api/v1/events", 
                    Map.of("payload", Map.of("orderId", "ORD-001"))); 

            assertThat(resp.statusCode()).isEqualTo(400); 
            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class); 
            assertThat(body.get("message")).isNotNull();
        }

        @Test
        @DisplayName("returns 400 when event type is blank")
        void appendEvent_blankType_returns400() throws Exception { 
            startServer(); 

            HttpResponse<String> resp = postJson("/api/v1/events", 
                    Map.of("type", "  ", "payload", Map.of())); 

            assertThat(resp.statusCode()).isEqualTo(400); 
        }

        @Test
        @DisplayName("propagates tenant from X-Tenant-ID header to client call")
        void appendEvent_withTenantHeader_calledWithTenant() throws Exception { 
            when(mockClient.appendEvent(anyString(), any())) 
                    .thenReturn(Promise.of(DataCloudClient.Offset.of(2))); 

            startServer(); 

            HttpRequest req = HttpRequest.newBuilder() 
                    .POST(HttpRequest.BodyPublishers.ofString( 
                            "{\"type\":\"user.login\",\"payload\":{\"userId\":\"u1\"}}"))
                    .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/events")) 
                    .header("Content-Type", "application/json") 
                    .header("X-Tenant-ID", "acme-corp") 
                    .build(); 
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString()); 

            assertThat(resp.statusCode()).isEqualTo(200); 
            verify(mockClient).appendEvent(anyString(), any()); 
        }

        @Test
        @DisplayName("returns 415 when Content-Type is not application/json")
        void appendEvent_wrongContentType_returns415() throws Exception { 
            startServer(); 

            HttpRequest req = HttpRequest.newBuilder() 
                    .POST(HttpRequest.BodyPublishers.ofString("type=order.placed"))
                    .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/events")) 
                    .header("Content-Type", "application/x-www-form-urlencoded") 
                    .build(); 
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString()); 

            assertThat(resp.statusCode()).isEqualTo(415); 
        }

    @Test
    @DisplayName("returns 401 before content-type validation when API key enforcement is enabled")
    void appendEvent_missingApiKey_returns401BeforeContentTypeValidation() throws Exception { 
        ApiKeyResolver resolver =
            apiKey ->
                Optional.ofNullable(apiKey) 
                    .filter("valid-key"::equals) 
                    .map(key -> new Principal("event-service", List.of("writer"), "tenant-1"));

        server = new DataCloudHttpServer(mockClient, port) 
            .withApiKeyResolver(resolver); 
        server.start(); 

        HttpRequest req = HttpRequest.newBuilder() 
            .POST(HttpRequest.BodyPublishers.ofString("type=order.placed"))
            .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/events")) 
            .header("Content-Type", "application/x-www-form-urlencoded") 
            .build(); 
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString()); 

        assertThat(resp.statusCode()).isEqualTo(401); 
    }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/events  — query events
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/events – query events")
    class QueryEventsTests {

        @Test
        @DisplayName("returns 200 with event list when events exist")
        void queryEvents_withEvents_returns200WithList() throws Exception { 
            List<DataCloudClient.Event> events = List.of( 
                    DataCloudClient.Event.of("order.placed", Map.of("orderId", "ORD-1")), 
                    DataCloudClient.Event.of("order.shipped", Map.of("orderId", "ORD-1"))); 
            when(mockClient.queryEvents(anyString(), any())) 
                    .thenReturn(Promise.of(events)); 

            startServer(); 

            HttpResponse<String> resp = get("/api/v1/events");

            assertThat(resp.statusCode()).isEqualTo(200); 
            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class); 
            @SuppressWarnings("unchecked")
            List<?> items = (List<?>) body.get("events");
            assertThat(items).hasSize(2); 
            assertThat(((Number) body.get("count")).intValue()).isEqualTo(2);
        }

        @Test
        @DisplayName("returns 200 with empty list when no events present")
        void queryEvents_noEvents_returnsEmptyList() throws Exception { 
            when(mockClient.queryEvents(anyString(), any())) 
                    .thenReturn(Promise.of(List.of())); 

            startServer(); 

            HttpResponse<String> resp = get("/api/v1/events");

            assertThat(resp.statusCode()).isEqualTo(200); 
            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class); 
            assertThat((List<?>) body.get("events")).isEmpty();
            assertThat(((Number) body.get("count")).intValue()).isEqualTo(0);
        }

        @Test
        @DisplayName("passes event-type filter to client when ?type= is specified")
        void queryEvents_withTypeFilter_queriesClient() throws Exception { 
            List<DataCloudClient.Event> filtered = List.of( 
                    DataCloudClient.Event.of("order.placed", Map.of("orderId", "ORD-X"))); 
            when(mockClient.queryEvents(anyString(), any())) 
                    .thenReturn(Promise.of(filtered)); 

            startServer(); 

            HttpResponse<String> resp = get("/api/v1/events?type=order.placed");

            assertThat(resp.statusCode()).isEqualTo(200); 
            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class); 
            assertThat((List<?>) body.get("events")).hasSize(1);
            verify(mockClient).queryEvents(anyString(), any(DataCloudClient.EventQuery.class)); 
        }

        @Test
        @DisplayName("returns 200 with tenant metadata when X-Tenant-ID header provided")
        void queryEvents_withTenant_includesMetadata() throws Exception { 
            when(mockClient.queryEvents(anyString(), any())) 
                    .thenReturn(Promise.of(List.of())); 

            startServer(); 

            HttpRequest req = HttpRequest.newBuilder() 
                    .GET() 
                    .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/events")) 
                    .header("X-Tenant-ID", "beta-tenant") 
                    .build(); 
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString()); 

            assertThat(resp.statusCode()).isEqualTo(200); 
            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class); 
            assertThat(body.get("tenantId")).isNotNull();
        }

        @Test
        @DisplayName("response includes timestamp metadata")
        void queryEvents_always_includesTimestamp() throws Exception { 
            when(mockClient.queryEvents(anyString(), any())) 
                    .thenReturn(Promise.of(List.of())); 

            startServer(); 

            HttpResponse<String> resp = get("/api/v1/events");

            assertThat(resp.statusCode()).isEqualTo(200); 
            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class); 
            assertThat(body.get("timestamp")).isNotNull();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void startServer() throws Exception { 
        server = new DataCloudHttpServer(mockClient, port); 
        server.start(); 
        waitForServerReady(port); 
    }

    private HttpResponse<String> get(String path) throws Exception { 
        return httpClient.send( 
                HttpRequest.newBuilder().GET() 
                        .uri(URI.create("http://127.0.0.1:" + port + path)) 
                        .build(), 
                HttpResponse.BodyHandlers.ofString()); 
    }

    private HttpResponse<String> postJson(String path, Object body) throws Exception { 
        String json = mapper.writeValueAsString(body); 
        return httpClient.send( 
                HttpRequest.newBuilder() 
                        .POST(HttpRequest.BodyPublishers.ofString(json)) 
                        .uri(URI.create("http://127.0.0.1:" + port + path)) 
                        .header("Content-Type", "application/json") 
                        .build(), 
                HttpResponse.BodyHandlers.ofString()); 
    }

    private static int findFreePort() throws IOException { 
        try (ServerSocket ss = new ServerSocket(0)) { 
            return ss.getLocalPort(); 
        }
    }

    private static void waitForServerReady(int port) throws Exception { 
        long deadline = System.currentTimeMillis() + 5_000; 
        while (System.currentTimeMillis() < deadline) { 
            try {
                new Socket("127.0.0.1", port).close(); 
                return;
            } catch (IOException ignored) { 
                Thread.sleep(50); 
            }
        }
        throw new IllegalStateException("Server did not start on port " + port + " within 5 s"); 
    }
}
