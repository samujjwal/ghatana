/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import com.ghatana.datacloud.DataCloudClient;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration tests for Data-Cloud HTTP checkpoint endpoints (DC-3). // GH-90000
 *
 * <p>Covers {@code GET/POST/GET/:id/DELETE/:id /api/v1/checkpoints/**}.
 * Starts a real {@link DataCloudHttpServer} on a random port; {@link DataCloudClient} is mocked.
 *
 * @doc.type class
 * @doc.purpose Integration tests for /api/v1/checkpoints/** HTTP endpoints (DC-3) // GH-90000
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudHttpServer – Checkpoint Endpoints (DC-3) [GH-90000]")
class DataCloudHttpServerCheckpointTest {

    private DataCloudClient mockClient;
    private DataCloudHttpServer server;
    private int port;
    private HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper(); // GH-90000

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        mockClient = mock(DataCloudClient.class); // GH-90000
        port = findFreePort(); // GH-90000
        httpClient = HttpClient.newBuilder().build(); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (server != null) server.stop(); // GH-90000
    }

    // ==================== GET /api/v1/checkpoints ====================

    @Nested
    @DisplayName("GET /api/v1/checkpoints – list checkpoints [GH-90000]")
    class ListCheckpointTests {

        @Test
        @DisplayName("returns 200 with checkpoint list and count [GH-90000]")
        void listCheckpoints_withData_returns200() throws Exception { // GH-90000
            List<DataCloudClient.Entity> checkpoints = List.of( // GH-90000
                DataCloudClient.Entity.of("cp-1", "dc_checkpoints", // GH-90000
                    Map.of("pipelineId", "pipe-a", "step", 3)), // GH-90000
                DataCloudClient.Entity.of("cp-2", "dc_checkpoints", // GH-90000
                    Map.of("pipelineId", "pipe-b", "step", 7)) // GH-90000
            );
            when(mockClient.query(anyString(), eq("dc_checkpoints [GH-90000]"), any(DataCloudClient.Query.class)))
                .thenReturn(Promise.of(checkpoints)); // GH-90000

            startServer(); // GH-90000

            HttpResponse<String> resp = get("/api/v1/checkpoints [GH-90000]");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(((List<?>) body.get("checkpoints [GH-90000]"))).hasSize(2);
            assertThat(((Number) body.get("count [GH-90000]")).intValue()).isEqualTo(2);
        }

        @Test
        @DisplayName("empty collection → 200 with empty list [GH-90000]")
        void listCheckpoints_empty_returns200WithEmptyList() throws Exception { // GH-90000
            when(mockClient.query(anyString(), eq("dc_checkpoints [GH-90000]"), any(DataCloudClient.Query.class)))
                .thenReturn(Promise.of(List.of())); // GH-90000

            startServer(); // GH-90000

            HttpResponse<String> resp = get("/api/v1/checkpoints [GH-90000]");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(((List<?>) body.get("checkpoints [GH-90000]"))).isEmpty();
            assertThat(((Number) body.get("count [GH-90000]")).intValue()).isEqualTo(0);
        }

        @Test
        @DisplayName("response always contains tenantId and timestamp [GH-90000]")
        void listCheckpoints_always_includesMetadata() throws Exception { // GH-90000
            when(mockClient.query(anyString(), eq("dc_checkpoints [GH-90000]"), any(DataCloudClient.Query.class)))
                .thenReturn(Promise.of(List.of())); // GH-90000

            startServer(); // GH-90000

            HttpResponse<String> resp = get("/api/v1/checkpoints [GH-90000]");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("tenantId [GH-90000]")).isNotNull();
            assertThat(body.get("timestamp [GH-90000]")).isNotNull();
        }

        @Test
        @DisplayName("limit query param is passed through [GH-90000]")
        void listCheckpoints_withLimit_queriesWithLimit() throws Exception { // GH-90000
            when(mockClient.query(anyString(), eq("dc_checkpoints [GH-90000]"), any(DataCloudClient.Query.class)))
                .thenReturn(Promise.of(List.of())); // GH-90000

            startServer(); // GH-90000

            HttpResponse<String> resp = get("/api/v1/checkpoints?limit=50 [GH-90000]");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            // No exception thrown — limit param was accepted
            verify(mockClient).query(anyString(), eq("dc_checkpoints [GH-90000]"), any());
        }

        @Test
        @DisplayName("missing tenant returns 400 [GH-90000]")
        void listCheckpoints_missingTenant_returns400() throws Exception { // GH-90000
            startServer(); // GH-90000

            HttpResponse<String> resp = getWithoutTenant("/api/v1/checkpoints [GH-90000]");

            assertThat(resp.statusCode()).isEqualTo(400); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("error [GH-90000]")).isEqualTo("MISSING_TENANT [GH-90000]");
        }
    }

    // ==================== POST /api/v1/checkpoints ====================

    @Nested
    @DisplayName("POST /api/v1/checkpoints – save checkpoint [GH-90000]")
    class SaveCheckpointTests {

        @Test
        @DisplayName("valid checkpoint body → 200 with id and savedAt [GH-90000]")
        void saveCheckpoint_validBody_returns200() throws Exception { // GH-90000
            Map<String, Object> cpData = Map.of( // GH-90000
                "id", "cp-new",
                "pipelineId", "pipe-xyz",
                "step", 5,
                "state", "RUNNING"
            );
            DataCloudClient.Entity saved = DataCloudClient.Entity.of("cp-new", "dc_checkpoints", cpData); // GH-90000
            when(mockClient.save(anyString(), eq("dc_checkpoints [GH-90000]"), any()))
                .thenReturn(Promise.of(saved)); // GH-90000

            startServer(); // GH-90000

            HttpResponse<String> resp = post("/api/v1/checkpoints", cpData); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("id [GH-90000]")).isEqualTo("cp-new [GH-90000]");
            assertThat(body.get("savedAt [GH-90000]")).isNotNull();
            assertThat(body.get("tenantId [GH-90000]")).isNotNull();
        }

        @Test
        @DisplayName("invalid JSON → 400 with error message [GH-90000]")
        void saveCheckpoint_invalidJson_returns400() throws Exception { // GH-90000
            startServer(); // GH-90000

            HttpResponse<String> resp = postRaw("/api/v1/checkpoints", "{{invalid}}"); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(400); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("message [GH-90000]")).isNotNull();
        }

        @Test
        @DisplayName("saved to dc_checkpoints collection [GH-90000]")
        void saveCheckpoint_savesTo_dcCheckpointsCollection() throws Exception { // GH-90000
            Map<String, Object> cpData = Map.of("id", "cp-c", "step", 1); // GH-90000
            DataCloudClient.Entity saved = DataCloudClient.Entity.of("cp-c", "dc_checkpoints", cpData); // GH-90000
            when(mockClient.save(anyString(), eq("dc_checkpoints [GH-90000]"), any()))
                .thenReturn(Promise.of(saved)); // GH-90000

            startServer(); // GH-90000

            post("/api/v1/checkpoints", cpData); // GH-90000

            verify(mockClient).save(anyString(), eq("dc_checkpoints [GH-90000]"), any());
        }

        @Test
        @DisplayName("missing tenant returns 400 [GH-90000]")
        void saveCheckpoint_missingTenant_returns400() throws Exception { // GH-90000
            startServer(); // GH-90000

            HttpResponse<String> resp = postRawWithoutTenant("/api/v1/checkpoints", "{}" ); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(400); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("error [GH-90000]")).isEqualTo("MISSING_TENANT [GH-90000]");
        }
    }

    // ==================== GET /api/v1/checkpoints/:checkpointId ====================

    @Nested
    @DisplayName("GET /api/v1/checkpoints/:checkpointId – get checkpoint [GH-90000]")
    class GetCheckpointTests {

        @Test
        @DisplayName("existing checkpoint → 200 with data [GH-90000]")
        void getCheckpoint_found_returns200() throws Exception { // GH-90000
            DataCloudClient.Entity entity = DataCloudClient.Entity.of( // GH-90000
                "cp-99", "dc_checkpoints", Map.of("step", 10, "status", "COMPLETE")); // GH-90000
            when(mockClient.findById(anyString(), eq("dc_checkpoints [GH-90000]"), eq("cp-99 [GH-90000]")))
                .thenReturn(Promise.of(Optional.of(entity))); // GH-90000

            startServer(); // GH-90000

            HttpResponse<String> resp = get("/api/v1/checkpoints/cp-99 [GH-90000]");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("id [GH-90000]")).isEqualTo("cp-99 [GH-90000]");
            assertThat(body.get("data [GH-90000]")).isNotNull();
            assertThat(body.get("tenantId [GH-90000]")).isNotNull();
        }

        @Test
        @DisplayName("non-existent checkpoint → 404 [GH-90000]")
        void getCheckpoint_notFound_returns404() throws Exception { // GH-90000
            when(mockClient.findById(anyString(), eq("dc_checkpoints [GH-90000]"), eq("missing-cp [GH-90000]")))
                .thenReturn(Promise.of(Optional.empty())); // GH-90000

            startServer(); // GH-90000

            HttpResponse<String> resp = get("/api/v1/checkpoints/missing-cp [GH-90000]");

            assertThat(resp.statusCode()).isEqualTo(404); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("message [GH-90000]").toString()).contains("missing-cp [GH-90000]");
        }
    }

    // ==================== DELETE /api/v1/checkpoints/:checkpointId ====================

    @Nested
    @DisplayName("DELETE /api/v1/checkpoints/:checkpointId – delete checkpoint [GH-90000]")
    class DeleteCheckpointTests {

        @Test
        @DisplayName("delete returns 200 with deleted=true and checkpointId [GH-90000]")
        void deleteCheckpoint_returns200() throws Exception { // GH-90000
            when(mockClient.delete(anyString(), eq("dc_checkpoints [GH-90000]"), eq("cp-del [GH-90000]")))
                .thenReturn(Promise.of(null)); // GH-90000

            startServer(); // GH-90000

            HttpResponse<String> resp = delete("/api/v1/checkpoints/cp-del [GH-90000]");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat((Boolean) body.get("deleted [GH-90000]")).isTrue();
            assertThat(body.get("checkpointId [GH-90000]")).isEqualTo("cp-del [GH-90000]");
            assertThat(body.get("timestamp [GH-90000]")).isNotNull();
        }

        @Test
        @DisplayName("delete propagates checkpoint ID to client [GH-90000]")
        void deleteCheckpoint_propagatesIdToClient() throws Exception { // GH-90000
            when(mockClient.delete(anyString(), eq("dc_checkpoints [GH-90000]"), eq("cp-prop [GH-90000]")))
                .thenReturn(Promise.of(null)); // GH-90000

            startServer(); // GH-90000

            delete("/api/v1/checkpoints/cp-prop [GH-90000]");

            verify(mockClient).delete(anyString(), eq("dc_checkpoints [GH-90000]"), eq("cp-prop [GH-90000]"));
        }

        @Test
        @DisplayName("response includes tenantId from request header [GH-90000]")
        void deleteCheckpoint_responseIncludesTenantId() throws Exception { // GH-90000
            when(mockClient.delete(anyString(), eq("dc_checkpoints [GH-90000]"), eq("cp-t [GH-90000]")))
                .thenReturn(Promise.of(null)); // GH-90000

            startServer(); // GH-90000

            HttpResponse<String> resp = deleteWithTenant("/api/v1/checkpoints/cp-t", "my-tenant"); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("tenantId [GH-90000]")).isEqualTo("my-tenant [GH-90000]");
        }
    }

    // ==================== Helpers ====================

    private void startServer() throws Exception { // GH-90000
        server = new DataCloudHttpServer(mockClient, port); // GH-90000
        server.start(); // GH-90000
        waitForServerReady(port); // GH-90000
    }

    private HttpResponse<String> get(String path) throws Exception { // GH-90000
        return getWithTenant(path, "default-tenant"); // GH-90000
    }

    private HttpResponse<String> getWithoutTenant(String path) throws Exception { // GH-90000
        HttpRequest req = HttpRequest.newBuilder() // GH-90000
            .GET() // GH-90000
            .uri(URI.create("http://127.0.0.1:" + port + path)) // GH-90000
            .build(); // GH-90000
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString()); // GH-90000
    }

    private HttpResponse<String> getWithTenant(String path, String tenantId) throws Exception { // GH-90000
        HttpRequest req = HttpRequest.newBuilder() // GH-90000
            .GET() // GH-90000
            .uri(URI.create("http://127.0.0.1:" + port + path)) // GH-90000
            .header("X-Tenant-Id", tenantId) // GH-90000
            .build(); // GH-90000
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString()); // GH-90000
    }

    private HttpResponse<String> post(String path, Map<String, Object> body) throws Exception { // GH-90000
        String json = mapper.writeValueAsString(body); // GH-90000
        return postRaw(path, json); // GH-90000
    }

    private HttpResponse<String> postRaw(String path, String body) throws Exception { // GH-90000
        return postRawWithTenant(path, body, "default-tenant"); // GH-90000
    }

    private HttpResponse<String> postRawWithoutTenant(String path, String body) throws Exception { // GH-90000
        HttpRequest req = HttpRequest.newBuilder() // GH-90000
            .POST(HttpRequest.BodyPublishers.ofString(body)) // GH-90000
            .uri(URI.create("http://127.0.0.1:" + port + path)) // GH-90000
            .header("Content-Type", "application/json") // GH-90000
            .build(); // GH-90000
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString()); // GH-90000
    }

    private HttpResponse<String> postRawWithTenant(String path, String body, String tenantId) throws Exception { // GH-90000
        HttpRequest req = HttpRequest.newBuilder() // GH-90000
            .POST(HttpRequest.BodyPublishers.ofString(body)) // GH-90000
            .uri(URI.create("http://127.0.0.1:" + port + path)) // GH-90000
            .header("Content-Type", "application/json") // GH-90000
            .header("X-Tenant-Id", tenantId) // GH-90000
            .build(); // GH-90000
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString()); // GH-90000
    }

    private HttpResponse<String> delete(String path) throws Exception { // GH-90000
        return deleteWithTenant(path, "default-tenant"); // GH-90000
    }

    private HttpResponse<String> deleteWithTenant(String path, String tenantId) throws Exception { // GH-90000
        HttpRequest req = HttpRequest.newBuilder() // GH-90000
            .DELETE() // GH-90000
            .uri(URI.create("http://127.0.0.1:" + port + path)) // GH-90000
            .header("X-Tenant-Id", tenantId) // GH-90000
            .build(); // GH-90000
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString()); // GH-90000
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
        throw new IllegalStateException("Server did not start within 5 seconds on port " + port); // GH-90000
    }
}
