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
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration tests for Data-Cloud HTTP agent-memory endpoints (DC-4). // GH-90000
 *
 * <p>Starts a real {@link DataCloudHttpServer} on a random port and makes
 * HTTP calls via the Java standard HttpClient.  The {@link DataCloudClient}
 * is mocked so tests do not touch real storage.
 *
 * @doc.type class
 * @doc.purpose Integration tests for /api/v1/memory/** HTTP endpoints (DC-4) // GH-90000
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudHttpServer – Memory Plane Endpoints (DC-4) [GH-90000]")
class DataCloudHttpServerMemoryTest {

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

    // ==================== GET /api/v1/memory/:agentId ====================

    @Test
    @DisplayName("storeMemory: persists AGENT_MEMORY item with ttl-derived expiresAt [GH-90000]")
    void storeMemory_persistsAgentMemoryItem() throws Exception { // GH-90000
        when(mockClient.save(anyString(), eq("dc_memory [GH-90000]"), any(Map.class)))
            .thenAnswer(invocation -> Promise.of(DataCloudClient.Entity.of( // GH-90000
                "mem-100",
                "dc_memory",
                invocation.getArgument(2, Map.class)))); // GH-90000

        startServer(); // GH-90000

        HttpResponse<String> resp = post( // GH-90000
            "/api/v1/memory/bot-writer",
            """
            {
              "type": "episodic",
              "content": "User asked for a short summary",
              "ttlSeconds": 600,
              "tags": ["summary", "user-request"],
              "salience": 0.9,
              "metadata": {"source": "chat"}
            }
            """);

        assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
        Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
        assertThat(body.get("agentId [GH-90000]")).isEqualTo("bot-writer [GH-90000]");
        assertThat(body.get("type [GH-90000]")).isEqualTo("EPISODIC [GH-90000]");
        assertThat(body.get("content [GH-90000]")).isEqualTo("User asked for a short summary [GH-90000]");
        assertThat(body.get("expiresAt [GH-90000]")).isNotNull();
    }

    @Test
    @DisplayName("getAgentMemory: returns context-window items ordered by salience then recency [GH-90000]")
    void getAgentMemory_returnsContextWindowItemsOrdered() throws Exception { // GH-90000
        List<DataCloudClient.Entity> items = List.of( // GH-90000
            DataCloudClient.Entity.of("m-low", "dc_memory", Map.of( // GH-90000
                "recordType", "AGENT_MEMORY",
                "agentId", "bot-order",
                "type", "EPISODIC",
                "content", "low salience",
                "salience", 0.1,
                "createdAt", "2026-04-14T10:00:00Z")),
            DataCloudClient.Entity.of("m-high-old", "dc_memory", Map.of( // GH-90000
                "recordType", "AGENT_MEMORY",
                "agentId", "bot-order",
                "type", "EPISODIC",
                "content", "high but older",
                "salience", 0.9,
                "createdAt", "2026-04-14T11:00:00Z")),
            DataCloudClient.Entity.of("m-high-new", "dc_memory", Map.of( // GH-90000
                "recordType", "AGENT_MEMORY",
                "agentId", "bot-order",
                "type", "SEMANTIC",
                "content", "high and newer",
                "salience", 0.9,
                "createdAt", "2026-04-14T12:00:00Z"))
        );
        when(mockClient.query(anyString(), eq("dc_memory [GH-90000]"), any(DataCloudClient.Query.class)))
            .thenReturn(Promise.of(items)); // GH-90000

        startServer(); // GH-90000

        HttpResponse<String> resp = get("/api/v1/memory/bot-order?limit=2 [GH-90000]");

        assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
        Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
        List<?> returnedItems = (List<?>) body.get("items [GH-90000]");
        assertThat(returnedItems).hasSize(2); // GH-90000
        assertThat(((Map<?, ?>) returnedItems.get(0)).get("id [GH-90000]")).isEqualTo("m-high-new [GH-90000]");
        assertThat(((Map<?, ?>) returnedItems.get(1)).get("id [GH-90000]")).isEqualTo("m-high-old [GH-90000]");
    }

    @Test
    @DisplayName("getAgentMemory: expired items are deleted and excluded from response [GH-90000]")
    void getAgentMemory_cleansUpExpiredItems() throws Exception { // GH-90000
        List<DataCloudClient.Entity> items = List.of( // GH-90000
            DataCloudClient.Entity.of("m-expired", "dc_memory", Map.of( // GH-90000
                "recordType", "AGENT_MEMORY",
                "agentId", "bot-exp",
                "type", "EPISODIC",
                "content", "expired",
                "expiresAt", Instant.now().minusSeconds(60).toString())), // GH-90000
            DataCloudClient.Entity.of("m-active", "dc_memory", Map.of( // GH-90000
                "recordType", "AGENT_MEMORY",
                "agentId", "bot-exp",
                "type", "EPISODIC",
                "content", "active",
                "expiresAt", Instant.now().plusSeconds(60).toString())) // GH-90000
        );
        when(mockClient.query(anyString(), eq("dc_memory [GH-90000]"), any(DataCloudClient.Query.class)))
            .thenReturn(Promise.of(items)); // GH-90000
        when(mockClient.delete(anyString(), eq("dc_memory [GH-90000]"), eq("m-expired [GH-90000]")))
            .thenReturn(Promise.complete()); // GH-90000

        startServer(); // GH-90000

        HttpResponse<String> resp = get("/api/v1/memory/bot-exp [GH-90000]");

        assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
        Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
        assertThat(((Number) body.get("total [GH-90000]")).intValue()).isEqualTo(1);
        List<?> returnedItems = (List<?>) body.get("items [GH-90000]");
        assertThat(returnedItems).hasSize(1); // GH-90000
        assertThat(((Map<?, ?>) returnedItems.get(0)).get("id [GH-90000]")).isEqualTo("m-active [GH-90000]");
        verify(mockClient).delete(anyString(), eq("dc_memory [GH-90000]"), eq("m-expired [GH-90000]"));
    }

    @Test
    @DisplayName("listMemory root: returns filtered items for UI consumption [GH-90000]")
    void listMemoryRoot_returnsItemsWrapper() throws Exception { // GH-90000
        List<DataCloudClient.Entity> items = List.of( // GH-90000
            DataCloudClient.Entity.of("m-root-1", "dc_memory", Map.of( // GH-90000
                "recordType", "AGENT_MEMORY",
                "agentId", "bot-root",
                "type", "SEMANTIC",
                "content", "remember this",
                "salience", 0.7,
                "createdAt", "2026-04-14T12:00:00Z"))
        );
        when(mockClient.query(anyString(), eq("dc_memory [GH-90000]"), any(DataCloudClient.Query.class)))
            .thenReturn(Promise.of(items)); // GH-90000

        startServer(); // GH-90000

        HttpResponse<String> resp = get("/api/v1/memory?agentId=bot-root&type=semantic&limit=10 [GH-90000]");

        assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
        Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
        assertThat(((Number) body.get("total [GH-90000]")).intValue()).isEqualTo(1);
        List<?> returnedItems = (List<?>) body.get("items [GH-90000]");
        assertThat(returnedItems).hasSize(1); // GH-90000
        assertThat(((Map<?, ?>) returnedItems.get(0)).get("id [GH-90000]")).isEqualTo("m-root-1 [GH-90000]");
    }

    @Test
    @DisplayName("getAgentMemory: mixed-type items → 200 with correct byType counts [GH-90000]")
    void getAgentMemory_withMixedTypes_returnsByTypeCounts() throws Exception { // GH-90000
        List<DataCloudClient.Entity> items = List.of( // GH-90000
            DataCloudClient.Entity.of("m1", "dc_memory", // GH-90000
                Map.of("agentId", "bot-1", "type", "EPISODIC")), // GH-90000
            DataCloudClient.Entity.of("m2", "dc_memory", // GH-90000
                Map.of("agentId", "bot-1", "type", "EPISODIC")), // GH-90000
            DataCloudClient.Entity.of("m3", "dc_memory", // GH-90000
                Map.of("agentId", "bot-1", "type", "EPISODIC")), // GH-90000
            DataCloudClient.Entity.of("m4", "dc_memory", // GH-90000
                Map.of("agentId", "bot-1", "type", "SEMANTIC")), // GH-90000
            DataCloudClient.Entity.of("m5", "dc_memory", // GH-90000
                Map.of("agentId", "bot-1", "type", "PROCEDURAL")), // GH-90000
            DataCloudClient.Entity.of("m6", "dc_memory", // GH-90000
                Map.of("agentId", "bot-1", "type", "PREFERENCE")), // GH-90000
            DataCloudClient.Entity.of("m7", "dc_memory", // GH-90000
                Map.of("agentId", "bot-1", "type", "CUSTOM"))   // → other // GH-90000
        );
        when(mockClient.query(anyString(), eq("dc_memory [GH-90000]"), any(DataCloudClient.Query.class)))
            .thenReturn(Promise.of(items)); // GH-90000

        startServer(); // GH-90000

        HttpResponse<String> resp = get("/api/v1/memory/bot-1 [GH-90000]");

        assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
        Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
        assertThat(body.get("agentId [GH-90000]")).isEqualTo("bot-1 [GH-90000]");
        assertThat(((Number) body.get("total [GH-90000]")).intValue()).isEqualTo(7);

        Map<?, ?> byType = (Map<?, ?>) body.get("byType [GH-90000]");
        assertThat(((Number) byType.get("episodic [GH-90000]")).intValue()).isEqualTo(3);
        assertThat(((Number) byType.get("semantic [GH-90000]")).intValue()).isEqualTo(1);
        assertThat(((Number) byType.get("procedural [GH-90000]")).intValue()).isEqualTo(1);
        assertThat(((Number) byType.get("preference [GH-90000]")).intValue()).isEqualTo(1);
        assertThat(((Number) byType.get("other [GH-90000]")).intValue()).isEqualTo(1);
    }

    @Test
    @DisplayName("getAgentMemory: no items for agent → 200 with all-zero counts [GH-90000]")
    void getAgentMemory_whenEmpty_returnsAllZeros() throws Exception { // GH-90000
        when(mockClient.query(anyString(), eq("dc_memory [GH-90000]"), any(DataCloudClient.Query.class)))
            .thenReturn(Promise.of(List.of())); // GH-90000

        startServer(); // GH-90000

        HttpResponse<String> resp = get("/api/v1/memory/bot-empty [GH-90000]");

        assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
        Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
        assertThat(body.get("agentId [GH-90000]")).isEqualTo("bot-empty [GH-90000]");
        assertThat(((Number) body.get("total [GH-90000]")).intValue()).isEqualTo(0);

        Map<?, ?> byType = (Map<?, ?>) body.get("byType [GH-90000]");
        assertThat(((Number) byType.get("episodic [GH-90000]")).intValue()).isEqualTo(0);
        assertThat(((Number) byType.get("semantic [GH-90000]")).intValue()).isEqualTo(0);
        assertThat(((Number) byType.get("procedural [GH-90000]")).intValue()).isEqualTo(0);
        assertThat(((Number) byType.get("preference [GH-90000]")).intValue()).isEqualTo(0);
        assertThat(((Number) byType.get("other [GH-90000]")).intValue()).isEqualTo(0);
    }

    @Test
    @DisplayName("getAgentMemory: response contains required metadata fields [GH-90000]")
    void getAgentMemory_always_includesMetadataFields() throws Exception { // GH-90000
        when(mockClient.query(anyString(), eq("dc_memory [GH-90000]"), any(DataCloudClient.Query.class)))
            .thenReturn(Promise.of(List.of())); // GH-90000

        startServer(); // GH-90000

        HttpResponse<String> resp = get("/api/v1/memory/bot-meta [GH-90000]");

        assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
        Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
        assertThat(body.get("agentId [GH-90000]")).isNotNull();
        assertThat(body.get("tenantId [GH-90000]")).isNotNull();
        assertThat(body.get("total [GH-90000]")).isNotNull();
        assertThat(body.get("byType [GH-90000]")).isNotNull();
        assertThat(body.get("timestamp [GH-90000]")).isNotNull();
    }

    // ==================== GET /api/v1/memory/:agentId/:tier ====================

    @Test
    @DisplayName("getAgentMemoryByTier(episodic): returns only EPISODIC items [GH-90000]")
    void getAgentMemoryByTier_episodic_returnsFilteredItems() throws Exception { // GH-90000
        List<DataCloudClient.Entity> episodicItems = List.of( // GH-90000
            DataCloudClient.Entity.of("ep1", "dc_memory", // GH-90000
                Map.of("agentId", "bot-2", "type", "EPISODIC", "content", "I helped user X")), // GH-90000
            DataCloudClient.Entity.of("ep2", "dc_memory", // GH-90000
                Map.of("agentId", "bot-2", "type", "EPISODIC", "content", "I helped user Y")) // GH-90000
        );
        when(mockClient.query(anyString(), eq("dc_memory [GH-90000]"), any(DataCloudClient.Query.class)))
            .thenReturn(Promise.of(episodicItems)); // GH-90000

        startServer(); // GH-90000

        HttpResponse<String> resp = get("/api/v1/memory/bot-2/episodic [GH-90000]");

        assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
        Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
        assertThat(body.get("agentId [GH-90000]")).isEqualTo("bot-2 [GH-90000]");
        assertThat(body.get("tier [GH-90000]")).isEqualTo("episodic [GH-90000]");
        assertThat(((Number) body.get("count [GH-90000]")).intValue()).isEqualTo(2);

        List<?> items = (List<?>) body.get("items [GH-90000]");
        assertThat(items).hasSize(2); // GH-90000
    }

    @Test
    @DisplayName("getAgentMemoryByTier(SEMANTIC): tier name is case-insensitive [GH-90000]")
    void getAgentMemoryByTier_tierNameIsCaseInsensitive() throws Exception { // GH-90000
        when(mockClient.query(anyString(), eq("dc_memory [GH-90000]"), any(DataCloudClient.Query.class)))
            .thenReturn(Promise.of(List.of())); // GH-90000

        startServer(); // GH-90000

        // All four casing variants should return 200
        for (String tier : List.of("SEMANTIC", "semantic", "Semantic")) { // GH-90000
            HttpResponse<String> resp = get("/api/v1/memory/bot-3/" + tier); // GH-90000
            assertThat(resp.statusCode()) // GH-90000
                .as("tier='%s' should be accepted", tier) // GH-90000
                .isEqualTo(200); // GH-90000
        }
    }

    @Test
    @DisplayName("getAgentMemoryByTier: invalid tier → 400 with error message [GH-90000]")
    void getAgentMemoryByTier_invalidTier_returns400() throws Exception { // GH-90000
        startServer(); // GH-90000

        HttpResponse<String> resp = get("/api/v1/memory/bot-4/FLASHBACK [GH-90000]");

        assertThat(resp.statusCode()).isEqualTo(400); // GH-90000
        Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
        assertThat(body.get("message [GH-90000]").toString()).containsIgnoringCase("invalid tier [GH-90000]");
    }

    @Test
    @DisplayName("getAgentMemoryByTier: response contains pagination metadata [GH-90000]")
    void getAgentMemoryByTier_always_includesPaginationFields() throws Exception { // GH-90000
        when(mockClient.query(anyString(), eq("dc_memory [GH-90000]"), any(DataCloudClient.Query.class)))
            .thenReturn(Promise.of(List.of())); // GH-90000

        startServer(); // GH-90000

        HttpResponse<String> resp = get("/api/v1/memory/bot-5/procedural?limit=20&offset=10 [GH-90000]");

        assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
        Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
        assertThat(body.get("count [GH-90000]")).isNotNull();
        assertThat(body.get("offset [GH-90000]")).isNotNull();
        assertThat(body.get("limit [GH-90000]")).isNotNull();
        assertThat(body.get("items [GH-90000]")).isNotNull();
        assertThat(((Number) body.get("offset [GH-90000]")).intValue()).isEqualTo(10);
        assertThat(((Number) body.get("limit [GH-90000]")).intValue()).isEqualTo(20);
    }

    @Test
    @DisplayName("getAgentMemoryByTier: limit capped at 1000 [GH-90000]")
    void getAgentMemoryByTier_limitCappedAt1000() throws Exception { // GH-90000
        when(mockClient.query(anyString(), eq("dc_memory [GH-90000]"), any(DataCloudClient.Query.class)))
            .thenReturn(Promise.of(List.of())); // GH-90000

        startServer(); // GH-90000

        HttpResponse<String> resp = get("/api/v1/memory/bot-6/preference?limit=9999 [GH-90000]");

        assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
        Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
        // Server caps at 1000 — must not exceed that
        assertThat(((Number) body.get("limit [GH-90000]")).intValue()).isLessThanOrEqualTo(1000);
    }

    @Test
    @DisplayName("getAgentMemoryByTier: response items contain expected fields [GH-90000]")
    void getAgentMemoryByTier_items_haveExpectedFields() throws Exception { // GH-90000
        List<DataCloudClient.Entity> items = List.of( // GH-90000
            DataCloudClient.Entity.of("pref-1", "dc_memory", // GH-90000
                Map.of("agentId", "bot-7", "type", "PREFERENCE", "content", "Prefers short replies")) // GH-90000
        );
        when(mockClient.query(anyString(), eq("dc_memory [GH-90000]"), any(DataCloudClient.Query.class)))
            .thenReturn(Promise.of(items)); // GH-90000

        startServer(); // GH-90000

        HttpResponse<String> resp = get("/api/v1/memory/bot-7/preference [GH-90000]");

        assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
        Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
        List<?> returnedItems = (List<?>) body.get("items [GH-90000]");
        assertThat(returnedItems).hasSize(1); // GH-90000

        Map<?, ?> item = (Map<?, ?>) returnedItems.get(0); // GH-90000
        assertThat(item.get("id [GH-90000]")).isNotNull();
        assertThat(item.get("agentId [GH-90000]")).isNotNull();
        assertThat(item.get("type [GH-90000]")).isNotNull();
        assertThat(item.get("content [GH-90000]")).isNotNull();
        assertThat(item.get("createdAt [GH-90000]")).isNotNull();
    }

    // ==================== Helpers ====================

    private void startServer() throws Exception { // GH-90000
        server = new DataCloudHttpServer(mockClient, port); // GH-90000
        server.start(); // GH-90000
        waitForServerReady(port); // GH-90000
    }

    private HttpResponse<String> get(String path) throws Exception { // GH-90000
        HttpRequest req = HttpRequest.newBuilder() // GH-90000
            .GET() // GH-90000
            .uri(URI.create("http://127.0.0.1:" + port + path)) // GH-90000
            .build(); // GH-90000
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString()); // GH-90000
    }

    private HttpResponse<String> post(String path, String body) throws Exception { // GH-90000
        HttpRequest req = HttpRequest.newBuilder() // GH-90000
            .uri(URI.create("http://127.0.0.1:" + port + path)) // GH-90000
            .header("Content-Type", "application/json") // GH-90000
            .POST(HttpRequest.BodyPublishers.ofString(body)) // GH-90000
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
        throw new AssertionError("Server did not start on port " + port + " within 5 s"); // GH-90000
    }
}
