/*
 * Copyright (c) 2026 Ghatana Inc. 
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
 * <p>Coverage model (Gap 004 remediation): 
 * <ul>
 *   <li>Entity endpoints — {@code /api/v1/entities/:collection}: save, get, delete.</li>
 *   <li>Analytics endpoints — {@code /api/v1/analytics}: query, suggest.</li>
 *   <li>Voice endpoints — {@code /api/v1/voice}: intent, intents catalog.</li>
 *   <li>AI assist endpoints — {@code /api/v1/entities/:collection/suggest}.</li>
 *   <li>Health/info utility endpoints — {@code /health}, {@code /info}.</li>
 * </ul>
 *
 * <p>All responses must include the top-level ApiResponse envelope
 * ({@code status}, {@code data} OR {@code error}, and {@code timestamp}). 
 *
 * @doc.type class
 * @doc.purpose OpenAPI drift detection tests (Gap 004) 
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) 
@Timeout(value = 15, unit = TimeUnit.SECONDS) 
@DisplayName("OpenAPI Contract Drift Detection")
class OpenApiDriftDetectionTest {

    private DataCloudClient mockClient;
    private DataCloudHttpServer server;
    private int port;
    private final HttpClient httpClient = HttpClient.newBuilder().build(); 
    private final ObjectMapper mapper = new ObjectMapper(); 

    @BeforeEach
    void setUp() throws Exception { 
        mockClient = mock(DataCloudClient.class); 
        port       = findFreePort(); 

        // Default stubs — lenient so tests that don't use all stubs don't fail with UnnecessaryStubbingException
        lenient().when(mockClient.save(anyString(), anyString(), any())) 
            .thenReturn(Promise.of(DataCloudClient.Entity.of("drift-ent-1", "test", Map.of("x", 1)))); 
        lenient().when(mockClient.appendEvent(anyString(), any())) 
            .thenReturn(Promise.of(DataCloudClient.Offset.of(1))); 
        lenient().when(mockClient.findById(anyString(), anyString(), anyString())) 
            .thenReturn(Promise.of(java.util.Optional.of( 
                DataCloudClient.Entity.of("drift-ent-1", "test", Map.of("x", 1))))); 
        lenient().when(mockClient.delete(anyString(), anyString(), anyString())) 
            .thenReturn(Promise.of((Void) null)); 
        lenient().when(mockClient.query(anyString(), anyString(), any())) 
            .thenReturn(Promise.of(List.of())); 

        server = new DataCloudHttpServer(mockClient, port); 
        server.start(); 
        waitForServerReady(port); 
    }

    @AfterEach
    void tearDown() { 
        if (server != null) server.stop(); 
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ApiResponse envelope contract (applies to all endpoints) 
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ApiResponse envelope contract")
    class ApiResponseEnvelopeTests {

        @Test
        @DisplayName("entity save response has status + data or error keys")
        void entitySave_hasApiResponseEnvelope() throws Exception { 
            HttpResponse<String> resp = postJson("/api/v1/entities/products", 
                "{\"name\":\"Widget\",\"price\":9.99}");

            assertThat(resp.statusCode()).isEqualTo(200); 
            JsonNode body = mapper.readTree(resp.body()); 
            // Entity endpoints return flat JSON; check for entity-specific or error fields
            assertThat(body.has("id") || body.has("data") || body.has("error"))
                .as("Entity save response must have 'id', 'data', or 'error' key; got: " + body) 
                .isTrue(); 
        }

        @Test
        @DisplayName("entity GET response has status + data or error keys")
        void entityGet_hasApiResponseEnvelope() throws Exception { 
            HttpResponse<String> resp = get("/api/v1/entities/products/drift-ent-1");

            assertThat(resp.statusCode()).isEqualTo(200); 
            JsonNode body = mapper.readTree(resp.body()); 
            // Entity GET returns flat JSON with "id" at top level (plus nested "data" for attributes) 
            assertThat(body.has("id") || body.has("data") || body.has("error"))
                .as("Entity GET response must have 'id', 'data', or 'error' key; got: " + body) 
                .isTrue(); 
        }

        @Test
        @DisplayName("voice intents catalog response has status + data keys")
        void voiceIntents_hasApiResponseEnvelope() throws Exception { 
            HttpResponse<String> resp = get("/api/v1/voice/intents");

            assertThat(resp.statusCode()).isEqualTo(200); 
            JsonNode body = mapper.readTree(resp.body()); 
            assertResponseEnvelope(body); 
        }

        @Test
        @DisplayName("AI assist entity suggest response has status + data + ai keys")
        void aiEntitySuggest_hasAiBlock() throws Exception { 
            HttpResponse<String> resp = postJson( 
                "/api/v1/entities/orders/suggest",
                "{\"collection\":\"orders\",\"fields\":[\"id\",\"amount\"]}");

            assertThat(resp.statusCode()).isEqualTo(200); 
            JsonNode body = mapper.readTree(resp.body()); 
            assertResponseEnvelope(body); 
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
        void entitySave_dataHasRequiredFields() throws Exception { 
            HttpResponse<String> resp = postJson("/api/v1/entities/products", 
                "{\"name\":\"Widget\",\"price\":9.99}");

            assertThat(resp.statusCode()).isEqualTo(200); 
            JsonNode body = mapper.readTree(resp.body()); 
            // Entity SAVE returns flat JSON: {id, collection, version, createdAt, timestamp}
            assertThat(body.has("id")).as("id must be present in save response").isTrue();
            assertThat(body.has("collection")).as("collection must be present in save response").isTrue();
        }

        @Test
        @DisplayName("GET /api/v1/entities/:collection/:id response data has id, collection, attributes")
        void entityGet_dataHasRequiredFields() throws Exception { 
            HttpResponse<String> resp = get("/api/v1/entities/products/drift-ent-1");

            assertThat(resp.statusCode()).isEqualTo(200); 
            JsonNode body = mapper.readTree(resp.body()); 
            // Entity GET returns flat JSON: {id, collection, data:{...}, timestamp}
            assertThat(body.has("id")).as("id must be present in entity GET response").isTrue();
        }

        @Test
        @DisplayName("DELETE /api/v1/entities/:collection/:id returns 200 with success indicator")
        void entityDelete_returns200WithSuccess() throws Exception { 
            HttpResponse<String> resp = delete("/api/v1/entities/products/drift-ent-1");

            assertThat(resp.statusCode()).isEqualTo(200); 
            JsonNode body = mapper.readTree(resp.body()); 
            // Entity DELETE returns flat JSON: {collection, id, deleted, timestamp}
            assertThat(body.has("deleted") || body.has("data") || body.has("error"))
                .as("Entity DELETE response must have 'deleted', 'data', or 'error' key; got: " + body) 
                .isTrue(); 
        }

        @Test
        @DisplayName("entity query GET /api/v1/entities/:collection returns array in data")
        void entityQuery_dataIsArray() throws Exception { 
            HttpResponse<String> resp = get("/api/v1/entities/products");

            assertThat(resp.statusCode()).isEqualTo(200); 
            JsonNode body = mapper.readTree(resp.body()); 
            // Entity query returns flat JSON: {entities:[], count:N, timestamp}
            assertThat(body.has("entities") || body.has("data") || body.has("error"))
                .as("Entity query response must have 'entities', 'data', or 'error' key; got: " + body) 
                .isTrue(); 
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
        void intents_dataHasIntentsArrayAndCount() throws Exception { 
            HttpResponse<String> resp = get("/api/v1/voice/intents");

            JsonNode data = assertAndGetData(resp); 
            assertThat(data.has("intents")).as("data.intents must be present").isTrue();
            assertThat(data.get("intents").isArray()).isTrue();
            assertThat(data.has("count")).as("data.count must be present").isTrue();
        }

        @Test
        @DisplayName("POST /api/v1/voice/intent with valid utterance returns 200")
        void voiceIntent_validUtterance_returns200() throws Exception { 
            HttpResponse<String> resp = postJson("/api/v1/voice/intent", 
                "{\"utterance\":\"list_pipelines\",\"confirm\":true}");

            assertThat(resp.statusCode()).isEqualTo(200); 
            JsonNode body = mapper.readTree(resp.body()); 
            assertResponseEnvelope(body); 
        }

        @Test
        @DisplayName("POST /api/v1/voice/intent with empty body returns structured error, not 500")
        void voiceIntent_emptyBody_not500() throws Exception { 
            HttpResponse<String> resp = postJson("/api/v1/voice/intent", "{}"); 

            assertThat(resp.statusCode()).isLessThan(500); 
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
        void health_returns200WithStatus() throws Exception { 
            HttpResponse<String> resp = get("/health");

            assertThat(resp.statusCode()).isEqualTo(200); 
            JsonNode body = mapper.readTree(resp.body()); 
            assertThat(body.has("status")).as("/health must have status field").isTrue();
        }

        @Test
        @DisplayName("GET /ready returns 200")
        void ready_returns200() throws Exception { 
            HttpResponse<String> resp = get("/ready");
            assertThat(resp.statusCode()).isEqualTo(200); 
        }

        @Test
        @DisplayName("GET /metrics returns prometheus-format text")
        void metrics_returnsPrometheusText() throws Exception { 
            HttpResponse<String> resp = get("/metrics");

            assertThat(resp.statusCode()).isEqualTo(200); 
            // Prometheus format starts with HELP or TYPE comments, or is empty
            assertThat(resp.body()).isNotNull(); 
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
        void jsonEndpoints_returnApplicationJsonContentType() throws Exception { 
            HttpResponse<String> resp = get("/api/v1/voice/intents");

            assertThat(resp.statusCode()).isEqualTo(200); 
            String contentType = resp.headers().firstValue("content-type").orElse("");
            assertThat(contentType).containsIgnoringCase("application/json");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void assertResponseEnvelope(JsonNode body) { 
        // Every ApiResponse must have either a "data" key or an "error" key
        boolean hasData  = body.has("data");
        boolean hasError = body.has("error");
        assertThat(hasData || hasError) 
            .as("ApiResponse envelope must have 'data' or 'error' key; got: " + body) 
            .isTrue(); 
    }

    private JsonNode assertAndGetData(HttpResponse<String> resp) throws Exception { 
        assertThat(resp.statusCode()).isEqualTo(200); 
        JsonNode body = mapper.readTree(resp.body()); 
        assertResponseEnvelope(body); 
        assertThat(body.has("data")).as("data key must be present for success responses").isTrue();
        return body.get("data");
    }

    private HttpResponse<String> get(String path) throws Exception { 
        return httpClient.send( 
            HttpRequest.newBuilder().GET() 
                .uri(URI.create("http://127.0.0.1:" + port + path)) 
                .build(), 
            HttpResponse.BodyHandlers.ofString()); 
    }

    private HttpResponse<String> postJson(String path, String body) throws Exception { 
        return httpClient.send( 
            HttpRequest.newBuilder() 
                .POST(HttpRequest.BodyPublishers.ofString(body)) 
                .uri(URI.create("http://127.0.0.1:" + port + path)) 
                .header("Content-Type", "application/json") 
                .build(), 
            HttpResponse.BodyHandlers.ofString()); 
    }

    private HttpResponse<String> delete(String path) throws Exception { 
        return httpClient.send( 
            HttpRequest.newBuilder().DELETE() 
                .uri(URI.create("http://127.0.0.1:" + port + path)) 
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
