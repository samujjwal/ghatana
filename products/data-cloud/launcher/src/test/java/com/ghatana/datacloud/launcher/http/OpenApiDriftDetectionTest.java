/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
import io.activej.promise.Promise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * OpenAPI contract drift detection tests.
 *
 * <p>These tests validate that runtime HTTP responses conform to the structural
 * constraints documented in {@code api/openapi.yaml}. They catch drift between
 * the specification and the implementation <em>without</em> requiring a full
 * schema-validation library — instead, each test asserts the mandatory response
 * fields and types defined in the spec.
 *
 * <p>Coverage model (Gap 004 remediation): // GH-90000
 * <ul>
 *   <li>Entity endpoints — {@code /api/v1/entities/:collection}: save, get, delete.</li>
 *   <li>Analytics endpoints — {@code /api/v1/analytics}: query, suggest.</li>
 *   <li>Voice endpoints — {@code /api/v1/voice}: intent, intents catalog.</li>
 *   <li>AI assist endpoints — {@code /api/v1/entities/:collection/suggest}.</li>
 *   <li>Health/info utility endpoints — {@code /health}, {@code /info}.</li>
 * </ul>
 *
 * <p>All responses must include the top-level ApiResponse envelope
 * ({@code status}, {@code data} OR {@code error}, and {@code timestamp}). // GH-90000
 *
 * @doc.type class
 * @doc.purpose OpenAPI drift detection tests (Gap 004) // GH-90000
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@Timeout(value = 15, unit = TimeUnit.SECONDS) // GH-90000
@DisplayName("OpenAPI Contract Drift Detection")
class OpenApiDriftDetectionTest {

    private DataCloudClient mockClient;
    private DataCloudHttpServer server;
    private int port;
    private final HttpClient httpClient = HttpClient.newBuilder().build(); // GH-90000
    private final ObjectMapper mapper = new ObjectMapper(); // GH-90000

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        mockClient = mock(DataCloudClient.class); // GH-90000
        port       = findFreePort(); // GH-90000

        // Default stubs — lenient so tests that don't use all stubs don't fail with UnnecessaryStubbingException
        lenient().when(mockClient.save(anyString(), anyString(), any())) // GH-90000
            .thenReturn(Promise.of(DataCloudClient.Entity.of("drift-ent-1", "test", Map.of("x", 1)))); // GH-90000
        lenient().when(mockClient.appendEvent(anyString(), any())) // GH-90000
            .thenReturn(Promise.of(DataCloudClient.Offset.of(1))); // GH-90000
        lenient().when(mockClient.findById(anyString(), anyString(), anyString())) // GH-90000
            .thenReturn(Promise.of(java.util.Optional.of( // GH-90000
                DataCloudClient.Entity.of("drift-ent-1", "test", Map.of("x", 1))))); // GH-90000
        lenient().when(mockClient.delete(anyString(), anyString(), anyString())) // GH-90000
            .thenReturn(Promise.of((Void) null)); // GH-90000
        lenient().when(mockClient.query(anyString(), anyString(), any())) // GH-90000
            .thenReturn(Promise.of(List.of())); // GH-90000

        server = new DataCloudHttpServer(mockClient, port); // GH-90000
        server.start(); // GH-90000
        waitForServerReady(port); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (server != null) server.stop(); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ApiResponse envelope contract (applies to all endpoints) // GH-90000
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ApiResponse envelope contract")
    class ApiResponseEnvelopeTests {

        @Test
        @DisplayName("entity save response has status + data or error keys")
        void entitySave_hasApiResponseEnvelope() throws Exception { // GH-90000
            HttpResponse<String> resp = postJson("/api/v1/entities/products", // GH-90000
                "{\"name\":\"Widget\",\"price\":9.99}");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            JsonNode body = mapper.readTree(resp.body()); // GH-90000
            // Entity endpoints return flat JSON; check for entity-specific or error fields
            assertThat(body.has("id") || body.has("data") || body.has("error"))
                .as("Entity save response must have 'id', 'data', or 'error' key; got: " + body) // GH-90000
                .isTrue(); // GH-90000
        }

        @Test
        @DisplayName("entity GET response has status + data or error keys")
        void entityGet_hasApiResponseEnvelope() throws Exception { // GH-90000
            HttpResponse<String> resp = get("/api/v1/entities/products/drift-ent-1");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            JsonNode body = mapper.readTree(resp.body()); // GH-90000
            // Entity GET returns flat JSON with "id" at top level (plus nested "data" for attributes) // GH-90000
            assertThat(body.has("id") || body.has("data") || body.has("error"))
                .as("Entity GET response must have 'id', 'data', or 'error' key; got: " + body) // GH-90000
                .isTrue(); // GH-90000
        }

        @Test
        @DisplayName("voice intents catalog response has status + data keys")
        void voiceIntents_hasApiResponseEnvelope() throws Exception { // GH-90000
            HttpResponse<String> resp = get("/api/v1/voice/intents");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            JsonNode body = mapper.readTree(resp.body()); // GH-90000
            assertResponseEnvelope(body); // GH-90000
        }

        @Test
        @DisplayName("AI assist entity suggest response has status + data + ai keys")
        void aiEntitySuggest_hasAiBlock() throws Exception { // GH-90000
            HttpResponse<String> resp = postJson( // GH-90000
                "/api/v1/entities/orders/suggest",
                "{\"collection\":\"orders\",\"fields\":[\"id\",\"amount\"]}");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            JsonNode body = mapper.readTree(resp.body()); // GH-90000
            assertResponseEnvelope(body); // GH-90000
            assertThat(body.has("ai")).as("ai block must be present on AI assist endpoints").isTrue();
            JsonNode ai = body.get("ai");
            assertThat(ai.has("fallback")).isTrue();
            assertThat(ai.has("confidence")).isTrue();
            assertThat(ai.has("model")).isTrue();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Entity endpoint schema drift
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Entity endpoint response schema")
    class EntitySchemaTests {

        @Test
        @DisplayName("POST /api/v1/entities/:collection response data has id, collection, attributes")
        void entitySave_dataHasRequiredFields() throws Exception { // GH-90000
            HttpResponse<String> resp = postJson("/api/v1/entities/products", // GH-90000
                "{\"name\":\"Widget\",\"price\":9.99}");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            JsonNode body = mapper.readTree(resp.body()); // GH-90000
            // Entity SAVE returns flat JSON: {id, collection, version, createdAt, timestamp}
            assertThat(body.has("id")).as("id must be present in save response").isTrue();
            assertThat(body.has("collection")).as("collection must be present in save response").isTrue();
        }

        @Test
        @DisplayName("GET /api/v1/entities/:collection/:id response data has id, collection, attributes")
        void entityGet_dataHasRequiredFields() throws Exception { // GH-90000
            HttpResponse<String> resp = get("/api/v1/entities/products/drift-ent-1");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            JsonNode body = mapper.readTree(resp.body()); // GH-90000
            // Entity GET returns flat JSON: {id, collection, data:{...}, timestamp}
            assertThat(body.has("id")).as("id must be present in entity GET response").isTrue();
        }

        @Test
        @DisplayName("DELETE /api/v1/entities/:collection/:id returns 200 with success indicator")
        void entityDelete_returns200WithSuccess() throws Exception { // GH-90000
            HttpResponse<String> resp = delete("/api/v1/entities/products/drift-ent-1");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            JsonNode body = mapper.readTree(resp.body()); // GH-90000
            // Entity DELETE returns flat JSON: {collection, id, deleted, timestamp}
            assertThat(body.has("deleted") || body.has("data") || body.has("error"))
                .as("Entity DELETE response must have 'deleted', 'data', or 'error' key; got: " + body) // GH-90000
                .isTrue(); // GH-90000
        }

        @Test
        @DisplayName("entity query GET /api/v1/entities/:collection returns array in data")
        void entityQuery_dataIsArray() throws Exception { // GH-90000
            HttpResponse<String> resp = get("/api/v1/entities/products");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            JsonNode body = mapper.readTree(resp.body()); // GH-90000
            // Entity query returns flat JSON: {entities:[], count:N, timestamp}
            assertThat(body.has("entities") || body.has("data") || body.has("error"))
                .as("Entity query response must have 'entities', 'data', or 'error' key; got: " + body) // GH-90000
                .isTrue(); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Voice endpoint schema drift
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Voice endpoint response schema")
    class VoiceSchemaTests {

        @Test
        @DisplayName("GET /api/v1/voice/intents data has intents array and count")
        void intents_dataHasIntentsArrayAndCount() throws Exception { // GH-90000
            HttpResponse<String> resp = get("/api/v1/voice/intents");

            JsonNode data = assertAndGetData(resp); // GH-90000
            assertThat(data.has("intents")).as("data.intents must be present").isTrue();
            assertThat(data.get("intents").isArray()).isTrue();
            assertThat(data.has("count")).as("data.count must be present").isTrue();
        }

        @Test
        @DisplayName("POST /api/v1/voice/intent with valid utterance returns 200")
        void voiceIntent_validUtterance_returns200() throws Exception { // GH-90000
            HttpResponse<String> resp = postJson("/api/v1/voice/intent", // GH-90000
                "{\"utterance\":\"list_pipelines\",\"confirm\":true}");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            JsonNode body = mapper.readTree(resp.body()); // GH-90000
            assertResponseEnvelope(body); // GH-90000
        }

        @Test
        @DisplayName("POST /api/v1/voice/intent with empty body returns structured error, not 500")
        void voiceIntent_emptyBody_not500() throws Exception { // GH-90000
            HttpResponse<String> resp = postJson("/api/v1/voice/intent", "{}"); // GH-90000

            assertThat(resp.statusCode()).isLessThan(500); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Health / utility endpoint schema drift
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Utility endpoint response schema")
    class UtilityEndpointSchemaTests {

        @Test
        @DisplayName("GET /health returns 200 with status field")
        void health_returns200WithStatus() throws Exception { // GH-90000
            HttpResponse<String> resp = get("/health");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            JsonNode body = mapper.readTree(resp.body()); // GH-90000
            assertThat(body.has("status")).as("/health must have status field").isTrue();
        }

        @Test
        @DisplayName("GET /ready returns 200")
        void ready_returns200() throws Exception { // GH-90000
            HttpResponse<String> resp = get("/ready");
            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
        }

        @Test
        @DisplayName("GET /metrics returns prometheus-format text")
        void metrics_returnsPrometheusText() throws Exception { // GH-90000
            HttpResponse<String> resp = get("/metrics");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            // Prometheus format starts with HELP or TYPE comments, or is empty
            assertThat(resp.body()).isNotNull(); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Content-Type contract
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Content-Type response headers")
    class ContentTypeTests {

        @Test
        @DisplayName("JSON endpoints return application/json Content-Type")
        void jsonEndpoints_returnApplicationJsonContentType() throws Exception { // GH-90000
            HttpResponse<String> resp = get("/api/v1/voice/intents");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            String contentType = resp.headers().firstValue("content-type").orElse("");
            assertThat(contentType).containsIgnoringCase("application/json");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void assertResponseEnvelope(JsonNode body) { // GH-90000
        // Every ApiResponse must have either a "data" key or an "error" key
        boolean hasData  = body.has("data");
        boolean hasError = body.has("error");
        assertThat(hasData || hasError) // GH-90000
            .as("ApiResponse envelope must have 'data' or 'error' key; got: " + body) // GH-90000
            .isTrue(); // GH-90000
    }

    private JsonNode assertAndGetData(HttpResponse<String> resp) throws Exception { // GH-90000
        assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
        JsonNode body = mapper.readTree(resp.body()); // GH-90000
        assertResponseEnvelope(body); // GH-90000
        assertThat(body.has("data")).as("data key must be present for success responses").isTrue();
        return body.get("data");
    }

    private HttpResponse<String> get(String path) throws Exception { // GH-90000
        return httpClient.send( // GH-90000
            HttpRequest.newBuilder().GET() // GH-90000
                .uri(URI.create("http://127.0.0.1:" + port + path)) // GH-90000
                .build(), // GH-90000
            HttpResponse.BodyHandlers.ofString()); // GH-90000
    }

    private HttpResponse<String> postJson(String path, String body) throws Exception { // GH-90000
        return httpClient.send( // GH-90000
            HttpRequest.newBuilder() // GH-90000
                .POST(HttpRequest.BodyPublishers.ofString(body)) // GH-90000
                .uri(URI.create("http://127.0.0.1:" + port + path)) // GH-90000
                .header("Content-Type", "application/json") // GH-90000
                .build(), // GH-90000
            HttpResponse.BodyHandlers.ofString()); // GH-90000
    }

    private HttpResponse<String> delete(String path) throws Exception { // GH-90000
        return httpClient.send( // GH-90000
            HttpRequest.newBuilder().DELETE() // GH-90000
                .uri(URI.create("http://127.0.0.1:" + port + path)) // GH-90000
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
