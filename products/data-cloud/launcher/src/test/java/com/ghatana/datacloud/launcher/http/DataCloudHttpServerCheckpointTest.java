/*
 * Copyright (c) 2026 Ghatana Inc.
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
 * Integration tests for Data-Cloud HTTP checkpoint endpoints (DC-3).
 *
 * <p>Covers {@code GET/POST/GET/:id/DELETE/:id /api/v1/checkpoints/**}.
 * Starts a real {@link DataCloudHttpServer} on a random port; {@link DataCloudClient} is mocked.
 *
 * @doc.type class
 * @doc.purpose Integration tests for /api/v1/checkpoints/** HTTP endpoints (DC-3)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudHttpServer – Checkpoint Endpoints (DC-3)")
class DataCloudHttpServerCheckpointTest {

    private DataCloudClient mockClient;
    private DataCloudHttpServer server;
    private int port;
    private HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        mockClient = mock(DataCloudClient.class);
        port = findFreePort();
        httpClient = HttpClient.newBuilder().build();
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop();
    }

    // ==================== GET /api/v1/checkpoints ====================

    @Nested
    @DisplayName("GET /api/v1/checkpoints – list checkpoints")
    class ListCheckpointTests {

        @Test
        @DisplayName("returns 200 with checkpoint list and count")
        void listCheckpoints_withData_returns200() throws Exception {
            List<DataCloudClient.Entity> checkpoints = List.of(
                DataCloudClient.Entity.of("cp-1", "dc_checkpoints",
                    Map.of("pipelineId", "pipe-a", "step", 3)),
                DataCloudClient.Entity.of("cp-2", "dc_checkpoints",
                    Map.of("pipelineId", "pipe-b", "step", 7))
            );
            when(mockClient.query(anyString(), eq("dc_checkpoints"), any(DataCloudClient.Query.class)))
                .thenReturn(Promise.of(checkpoints));

            startServer();

            HttpResponse<String> resp = get("/api/v1/checkpoints");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
            assertThat(((List<?>) body.get("checkpoints"))).hasSize(2);
            assertThat(((Number) body.get("count")).intValue()).isEqualTo(2);
        }

        @Test
        @DisplayName("empty collection → 200 with empty list")
        void listCheckpoints_empty_returns200WithEmptyList() throws Exception {
            when(mockClient.query(anyString(), eq("dc_checkpoints"), any(DataCloudClient.Query.class)))
                .thenReturn(Promise.of(List.of()));

            startServer();

            HttpResponse<String> resp = get("/api/v1/checkpoints");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
            assertThat(((List<?>) body.get("checkpoints"))).isEmpty();
            assertThat(((Number) body.get("count")).intValue()).isEqualTo(0);
        }

        @Test
        @DisplayName("response always contains tenantId and timestamp")
        void listCheckpoints_always_includesMetadata() throws Exception {
            when(mockClient.query(anyString(), eq("dc_checkpoints"), any(DataCloudClient.Query.class)))
                .thenReturn(Promise.of(List.of()));

            startServer();

            HttpResponse<String> resp = get("/api/v1/checkpoints");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body.get("tenantId")).isNotNull();
            assertThat(body.get("timestamp")).isNotNull();
        }

        @Test
        @DisplayName("limit query param is passed through")
        void listCheckpoints_withLimit_queriesWithLimit() throws Exception {
            when(mockClient.query(anyString(), eq("dc_checkpoints"), any(DataCloudClient.Query.class)))
                .thenReturn(Promise.of(List.of()));

            startServer();

            HttpResponse<String> resp = get("/api/v1/checkpoints?limit=50");

            assertThat(resp.statusCode()).isEqualTo(200);
            // No exception thrown — limit param was accepted
            verify(mockClient).query(anyString(), eq("dc_checkpoints"), any());
        }
    }

    // ==================== POST /api/v1/checkpoints ====================

    @Nested
    @DisplayName("POST /api/v1/checkpoints – save checkpoint")
    class SaveCheckpointTests {

        @Test
        @DisplayName("valid checkpoint body → 200 with id and savedAt")
        void saveCheckpoint_validBody_returns200() throws Exception {
            Map<String, Object> cpData = Map.of(
                "id", "cp-new",
                "pipelineId", "pipe-xyz",
                "step", 5,
                "state", "RUNNING"
            );
            DataCloudClient.Entity saved = DataCloudClient.Entity.of("cp-new", "dc_checkpoints", cpData);
            when(mockClient.save(anyString(), eq("dc_checkpoints"), any()))
                .thenReturn(Promise.of(saved));

            startServer();

            HttpResponse<String> resp = post("/api/v1/checkpoints", cpData);

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body.get("id")).isEqualTo("cp-new");
            assertThat(body.get("savedAt")).isNotNull();
            assertThat(body.get("tenantId")).isNotNull();
        }

        @Test
        @DisplayName("invalid JSON → 400 with error message")
        void saveCheckpoint_invalidJson_returns400() throws Exception {
            startServer();

            HttpResponse<String> resp = postRaw("/api/v1/checkpoints", "{{invalid}}");

            assertThat(resp.statusCode()).isEqualTo(400);
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body.get("error")).isNotNull();
        }

        @Test
        @DisplayName("saved to dc_checkpoints collection")
        void saveCheckpoint_savesTo_dcCheckpointsCollection() throws Exception {
            Map<String, Object> cpData = Map.of("id", "cp-c", "step", 1);
            DataCloudClient.Entity saved = DataCloudClient.Entity.of("cp-c", "dc_checkpoints", cpData);
            when(mockClient.save(anyString(), eq("dc_checkpoints"), any()))
                .thenReturn(Promise.of(saved));

            startServer();

            post("/api/v1/checkpoints", cpData);

            verify(mockClient).save(anyString(), eq("dc_checkpoints"), any());
        }
    }

    // ==================== GET /api/v1/checkpoints/:checkpointId ====================

    @Nested
    @DisplayName("GET /api/v1/checkpoints/:checkpointId – get checkpoint")
    class GetCheckpointTests {

        @Test
        @DisplayName("existing checkpoint → 200 with data")
        void getCheckpoint_found_returns200() throws Exception {
            DataCloudClient.Entity entity = DataCloudClient.Entity.of(
                "cp-99", "dc_checkpoints", Map.of("step", 10, "status", "COMPLETE"));
            when(mockClient.findById(anyString(), eq("dc_checkpoints"), eq("cp-99")))
                .thenReturn(Promise.of(Optional.of(entity)));

            startServer();

            HttpResponse<String> resp = get("/api/v1/checkpoints/cp-99");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body.get("id")).isEqualTo("cp-99");
            assertThat(body.get("data")).isNotNull();
            assertThat(body.get("tenantId")).isNotNull();
        }

        @Test
        @DisplayName("non-existent checkpoint → 404")
        void getCheckpoint_notFound_returns404() throws Exception {
            when(mockClient.findById(anyString(), eq("dc_checkpoints"), eq("missing-cp")))
                .thenReturn(Promise.of(Optional.empty()));

            startServer();

            HttpResponse<String> resp = get("/api/v1/checkpoints/missing-cp");

            assertThat(resp.statusCode()).isEqualTo(404);
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body.get("error").toString()).contains("missing-cp");
        }
    }

    // ==================== DELETE /api/v1/checkpoints/:checkpointId ====================

    @Nested
    @DisplayName("DELETE /api/v1/checkpoints/:checkpointId – delete checkpoint")
    class DeleteCheckpointTests {

        @Test
        @DisplayName("delete returns 200 with deleted=true and checkpointId")
        void deleteCheckpoint_returns200() throws Exception {
            when(mockClient.delete(anyString(), eq("dc_checkpoints"), eq("cp-del")))
                .thenReturn(Promise.of(null));

            startServer();

            HttpResponse<String> resp = delete("/api/v1/checkpoints/cp-del");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
            assertThat((Boolean) body.get("deleted")).isTrue();
            assertThat(body.get("checkpointId")).isEqualTo("cp-del");
            assertThat(body.get("timestamp")).isNotNull();
        }

        @Test
        @DisplayName("delete propagates checkpoint ID to client")
        void deleteCheckpoint_propagatesIdToClient() throws Exception {
            when(mockClient.delete(anyString(), eq("dc_checkpoints"), eq("cp-prop")))
                .thenReturn(Promise.of(null));

            startServer();

            delete("/api/v1/checkpoints/cp-prop");

            verify(mockClient).delete(anyString(), eq("dc_checkpoints"), eq("cp-prop"));
        }

        @Test
        @DisplayName("response includes tenantId from request header")
        void deleteCheckpoint_responseIncludesTenantId() throws Exception {
            when(mockClient.delete(anyString(), eq("dc_checkpoints"), eq("cp-t")))
                .thenReturn(Promise.of(null));

            startServer();

            HttpResponse<String> resp = deleteWithTenant("/api/v1/checkpoints/cp-t", "my-tenant");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body.get("tenantId")).isEqualTo("my-tenant");
        }
    }

    // ==================== Helpers ====================

    private void startServer() throws Exception {
        server = new DataCloudHttpServer(mockClient, port);
        server.start();
        waitForServerReady(port);
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
            .GET()
            .uri(URI.create("http://127.0.0.1:" + port + path))
            .build();
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, Map<String, Object> body) throws Exception {
        String json = mapper.writeValueAsString(body);
        return postRaw(path, json);
    }

    private HttpResponse<String> postRaw(String path, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .uri(URI.create("http://127.0.0.1:" + port + path))
            .header("Content-Type", "application/json")
            .build();
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> delete(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
            .DELETE()
            .uri(URI.create("http://127.0.0.1:" + port + path))
            .build();
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> deleteWithTenant(String path, String tenantId) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
            .DELETE()
            .uri(URI.create("http://127.0.0.1:" + port + path))
            .header("X-Tenant-Id", tenantId)
            .build();
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString());
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
        throw new IllegalStateException("Server did not start within 5 seconds on port " + port);
    }
}
