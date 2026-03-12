package com.ghatana.datacloud.launcher.http;

import com.ghatana.datacloud.DataCloudClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.promise.Promise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for Data-Cloud HTTP agent-memory endpoints (DC-4).
 *
 * <p>Starts a real {@link DataCloudHttpServer} on a random port and makes
 * HTTP calls via the Java standard HttpClient.  The {@link DataCloudClient}
 * is mocked so tests do not touch real storage.
 *
 * @doc.type class
 * @doc.purpose Integration tests for /api/v1/memory/** HTTP endpoints (DC-4)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudHttpServer – Memory Plane Endpoints (DC-4)")
class DataCloudHttpServerMemoryTest {

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

    // ==================== GET /api/v1/memory/:agentId ====================

    @Test
    @DisplayName("getAgentMemory: mixed-type items → 200 with correct byType counts")
    void getAgentMemory_withMixedTypes_returnsByTypeCounts() throws Exception {
        List<DataCloudClient.Entity> items = List.of(
            DataCloudClient.Entity.of("m1", "dc_memory",
                Map.of("agentId", "bot-1", "type", "EPISODIC")),
            DataCloudClient.Entity.of("m2", "dc_memory",
                Map.of("agentId", "bot-1", "type", "EPISODIC")),
            DataCloudClient.Entity.of("m3", "dc_memory",
                Map.of("agentId", "bot-1", "type", "EPISODIC")),
            DataCloudClient.Entity.of("m4", "dc_memory",
                Map.of("agentId", "bot-1", "type", "SEMANTIC")),
            DataCloudClient.Entity.of("m5", "dc_memory",
                Map.of("agentId", "bot-1", "type", "PROCEDURAL")),
            DataCloudClient.Entity.of("m6", "dc_memory",
                Map.of("agentId", "bot-1", "type", "PREFERENCE")),
            DataCloudClient.Entity.of("m7", "dc_memory",
                Map.of("agentId", "bot-1", "type", "CUSTOM"))   // → other
        );
        when(mockClient.query(anyString(), eq("dc_memory"), any(DataCloudClient.Query.class)))
            .thenReturn(Promise.of(items));

        startServer();

        HttpResponse<String> resp = get("/api/v1/memory/bot-1");

        assertThat(resp.statusCode()).isEqualTo(200);
        Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
        assertThat(body.get("agentId")).isEqualTo("bot-1");
        assertThat(((Number) body.get("total")).intValue()).isEqualTo(7);

        Map<?, ?> byType = (Map<?, ?>) body.get("byType");
        assertThat(((Number) byType.get("episodic")).intValue()).isEqualTo(3);
        assertThat(((Number) byType.get("semantic")).intValue()).isEqualTo(1);
        assertThat(((Number) byType.get("procedural")).intValue()).isEqualTo(1);
        assertThat(((Number) byType.get("preference")).intValue()).isEqualTo(1);
        assertThat(((Number) byType.get("other")).intValue()).isEqualTo(1);
    }

    @Test
    @DisplayName("getAgentMemory: no items for agent → 200 with all-zero counts")
    void getAgentMemory_whenEmpty_returnsAllZeros() throws Exception {
        when(mockClient.query(anyString(), eq("dc_memory"), any(DataCloudClient.Query.class)))
            .thenReturn(Promise.of(List.of()));

        startServer();

        HttpResponse<String> resp = get("/api/v1/memory/bot-empty");

        assertThat(resp.statusCode()).isEqualTo(200);
        Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
        assertThat(body.get("agentId")).isEqualTo("bot-empty");
        assertThat(((Number) body.get("total")).intValue()).isEqualTo(0);

        Map<?, ?> byType = (Map<?, ?>) body.get("byType");
        assertThat(((Number) byType.get("episodic")).intValue()).isEqualTo(0);
        assertThat(((Number) byType.get("semantic")).intValue()).isEqualTo(0);
        assertThat(((Number) byType.get("procedural")).intValue()).isEqualTo(0);
        assertThat(((Number) byType.get("preference")).intValue()).isEqualTo(0);
        assertThat(((Number) byType.get("other")).intValue()).isEqualTo(0);
    }

    @Test
    @DisplayName("getAgentMemory: response contains required metadata fields")
    void getAgentMemory_always_includesMetadataFields() throws Exception {
        when(mockClient.query(anyString(), eq("dc_memory"), any(DataCloudClient.Query.class)))
            .thenReturn(Promise.of(List.of()));

        startServer();

        HttpResponse<String> resp = get("/api/v1/memory/bot-meta");

        assertThat(resp.statusCode()).isEqualTo(200);
        Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
        assertThat(body.get("agentId")).isNotNull();
        assertThat(body.get("tenantId")).isNotNull();
        assertThat(body.get("total")).isNotNull();
        assertThat(body.get("byType")).isNotNull();
        assertThat(body.get("timestamp")).isNotNull();
    }

    // ==================== GET /api/v1/memory/:agentId/:tier ====================

    @Test
    @DisplayName("getAgentMemoryByTier(episodic): returns only EPISODIC items")
    void getAgentMemoryByTier_episodic_returnsFilteredItems() throws Exception {
        List<DataCloudClient.Entity> episodicItems = List.of(
            DataCloudClient.Entity.of("ep1", "dc_memory",
                Map.of("agentId", "bot-2", "type", "EPISODIC", "content", "I helped user X")),
            DataCloudClient.Entity.of("ep2", "dc_memory",
                Map.of("agentId", "bot-2", "type", "EPISODIC", "content", "I helped user Y"))
        );
        when(mockClient.query(anyString(), eq("dc_memory"), any(DataCloudClient.Query.class)))
            .thenReturn(Promise.of(episodicItems));

        startServer();

        HttpResponse<String> resp = get("/api/v1/memory/bot-2/episodic");

        assertThat(resp.statusCode()).isEqualTo(200);
        Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
        assertThat(body.get("agentId")).isEqualTo("bot-2");
        assertThat(body.get("tier")).isEqualTo("episodic");
        assertThat(((Number) body.get("count")).intValue()).isEqualTo(2);

        List<?> items = (List<?>) body.get("items");
        assertThat(items).hasSize(2);
    }

    @Test
    @DisplayName("getAgentMemoryByTier(SEMANTIC): tier name is case-insensitive")
    void getAgentMemoryByTier_tierNameIsCaseInsensitive() throws Exception {
        when(mockClient.query(anyString(), eq("dc_memory"), any(DataCloudClient.Query.class)))
            .thenReturn(Promise.of(List.of()));

        startServer();

        // All four casing variants should return 200
        for (String tier : List.of("SEMANTIC", "semantic", "Semantic")) {
            HttpResponse<String> resp = get("/api/v1/memory/bot-3/" + tier);
            assertThat(resp.statusCode())
                .as("tier='%s' should be accepted", tier)
                .isEqualTo(200);
        }
    }

    @Test
    @DisplayName("getAgentMemoryByTier: invalid tier → 400 with error message")
    void getAgentMemoryByTier_invalidTier_returns400() throws Exception {
        startServer();

        HttpResponse<String> resp = get("/api/v1/memory/bot-4/FLASHBACK");

        assertThat(resp.statusCode()).isEqualTo(400);
        Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
        assertThat(body.get("error").toString()).containsIgnoringCase("invalid tier");
    }

    @Test
    @DisplayName("getAgentMemoryByTier: response contains pagination metadata")
    void getAgentMemoryByTier_always_includesPaginationFields() throws Exception {
        when(mockClient.query(anyString(), eq("dc_memory"), any(DataCloudClient.Query.class)))
            .thenReturn(Promise.of(List.of()));

        startServer();

        HttpResponse<String> resp = get("/api/v1/memory/bot-5/procedural?limit=20&offset=10");

        assertThat(resp.statusCode()).isEqualTo(200);
        Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
        assertThat(body.get("count")).isNotNull();
        assertThat(body.get("offset")).isNotNull();
        assertThat(body.get("limit")).isNotNull();
        assertThat(body.get("items")).isNotNull();
        assertThat(((Number) body.get("offset")).intValue()).isEqualTo(10);
        assertThat(((Number) body.get("limit")).intValue()).isEqualTo(20);
    }

    @Test
    @DisplayName("getAgentMemoryByTier: limit capped at 1000")
    void getAgentMemoryByTier_limitCappedAt1000() throws Exception {
        when(mockClient.query(anyString(), eq("dc_memory"), any(DataCloudClient.Query.class)))
            .thenReturn(Promise.of(List.of()));

        startServer();

        HttpResponse<String> resp = get("/api/v1/memory/bot-6/preference?limit=9999");

        assertThat(resp.statusCode()).isEqualTo(200);
        Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
        // Server caps at 1000 — must not exceed that
        assertThat(((Number) body.get("limit")).intValue()).isLessThanOrEqualTo(1000);
    }

    @Test
    @DisplayName("getAgentMemoryByTier: response items contain expected fields")
    void getAgentMemoryByTier_items_haveExpectedFields() throws Exception {
        List<DataCloudClient.Entity> items = List.of(
            DataCloudClient.Entity.of("pref-1", "dc_memory",
                Map.of("agentId", "bot-7", "type", "PREFERENCE", "content", "Prefers short replies"))
        );
        when(mockClient.query(anyString(), eq("dc_memory"), any(DataCloudClient.Query.class)))
            .thenReturn(Promise.of(items));

        startServer();

        HttpResponse<String> resp = get("/api/v1/memory/bot-7/preference");

        assertThat(resp.statusCode()).isEqualTo(200);
        Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
        List<?> returnedItems = (List<?>) body.get("items");
        assertThat(returnedItems).hasSize(1);

        Map<?, ?> item = (Map<?, ?>) returnedItems.get(0);
        assertThat(item.get("id")).isNotNull();
        assertThat(item.get("agentId")).isNotNull();
        assertThat(item.get("type")).isNotNull();
        assertThat(item.get("content")).isNotNull();
        assertThat(item.get("createdAt")).isNotNull();
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
        throw new AssertionError("Server did not start on port " + port + " within 5 s");
    }
}
