package com.ghatana.aep.server.http;

import com.ghatana.aep.Aep;
import com.ghatana.aep.AepEngine;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.spi.EntityStore;
import com.ghatana.datacloud.spi.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.promise.Promise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
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
import static org.mockito.Mockito.when;

/**
 * Integration tests for AEP HTTP agent endpoints (AEP-P5/P7). // GH-90000
 *
 * <p>Starts a real {@link AepHttpServer} on a random port for each test and
 * makes HTTP requests via the Java standard HttpClient.  The
 * {@link DataCloudClient} is mocked so tests never touch real storage.
 *
 * @doc.type class
 * @doc.purpose Integration tests for /api/v1/agents/** HTTP endpoints
 * @doc.layer product
 * @doc.pattern Test
 */
@Tag("local-network")
@DisplayName("AepHttpServer – Agent Endpoints")
class AepHttpServerAgentTest {

    private AepEngine engine;
    private DataCloudClient mockDc;
    private EntityStore mockEntityStore;
    private AepHttpServer server;
    private int port;
    private HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper(); // GH-90000

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        System.setProperty("AEP_ENV", "test"); // GH-90000
        System.setProperty("AEP_AUTH_DISABLED", "true"); // GH-90000
        System.clearProperty("AEP_JWT_SECRET");
        engine = Aep.forTesting(); // GH-90000
        mockDc = mock(DataCloudClient.class); // GH-90000
        mockEntityStore = mock(EntityStore.class); // GH-90000
        when(mockDc.entityStore()).thenReturn(mockEntityStore); // GH-90000
        port = findFreePort(); // GH-90000
        httpClient = HttpClient.newBuilder().build(); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (server != null) server.stop(); // GH-90000
        if (engine != null) engine.close(); // GH-90000
        System.clearProperty("AEP_ENV");
        System.clearProperty("AEP_AUTH_DISABLED");
        System.clearProperty("AEP_JWT_SECRET");
    }

    // ==================== GET /api/v1/agents ====================

    @Test
    @DisplayName("listAgents: no DataCloudClient → 200 truthful unconfigured response")
    void listAgents_whenDcNull_returnsTruthfulUnconfiguredResponse() throws Exception { // GH-90000
        server = new AepHttpServer(engine, port); // GH-90000
        server.start(); // GH-90000
        waitForServerReady(port); // GH-90000

        HttpResponse<String> resp = get("/api/v1/agents");

        assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
        Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
        assertThat(body.get("configured")).isEqualTo(false);
        assertThat(body.get("message").toString()).contains("Agent registry not available");
        assertThat(body.get("tenantId")).isNotNull();
    }

    @Test
    @DisplayName("listAgents: DC returns 2 entities → 200 with agent summaries")
    void listAgents_withDcReturningEntities_returns200WithCount() throws Exception { // GH-90000
        List<EntityStore.Entity> entities = List.of( // GH-90000
            EntityStore.Entity.builder().id("agent-alpha").collection("aep_agents")
                .data(Map.of("name", "Alpha", "type", "LLM", "status", "ACTIVE")).build(), // GH-90000
            EntityStore.Entity.builder().id("agent-beta").collection("aep_agents")
                .data(Map.of("name", "Beta", "type", "RULE", "status", "ACTIVE")).build() // GH-90000
        );
        when(mockEntityStore.query(any(TenantContext.class), any(EntityStore.QuerySpec.class))) // GH-90000
            .thenReturn(Promise.of(EntityStore.QueryResult.of(entities))); // GH-90000

        server = new AepHttpServer(engine, port, null, mockDc); // GH-90000
        server.start(); // GH-90000
        waitForServerReady(port); // GH-90000

        HttpResponse<String> resp = get("/api/v1/agents");

        assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
        Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
        assertThat(body.get("count")).isEqualTo(2);
        List<?> agents = (List<?>) body.get("agents");
        assertThat(agents).hasSize(2); // GH-90000
    }

    @Test
    @DisplayName("listAgents: DC returns empty list → 200 with count=0")
    void listAgents_withEmptyDcResult_returns200WithZeroCount() throws Exception { // GH-90000
        when(mockEntityStore.query(any(TenantContext.class), any(EntityStore.QuerySpec.class))) // GH-90000
            .thenReturn(Promise.of(EntityStore.QueryResult.empty())); // GH-90000

        server = new AepHttpServer(engine, port, null, mockDc); // GH-90000
        server.start(); // GH-90000
        waitForServerReady(port); // GH-90000

        HttpResponse<String> resp = get("/api/v1/agents");

        assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
        Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
        assertThat(body.get("count")).isEqualTo(0);
        assertThat((List<?>) body.get("agents")).isEmpty();
    }

    // ==================== GET /api/v1/agents/:agentId ====================

    @Test
    @DisplayName("getAgent: entity found in DC → 200 with agent detail")
    void getAgent_whenEntityFound_returns200WithData() throws Exception { // GH-90000
        EntityStore.Entity entity = EntityStore.Entity.builder() // GH-90000
            .id("agent-42").collection("aep_agents")
            .data(Map.of("name", "Sentinel", "type", "LLM", "status", "ACTIVE")).build(); // GH-90000
        when(mockEntityStore.findById(any(TenantContext.class), eq(EntityStore.EntityId.of("agent-42"))))
            .thenReturn(Promise.of(Optional.of(entity))); // GH-90000

        server = new AepHttpServer(engine, port, null, mockDc); // GH-90000
        server.start(); // GH-90000
        waitForServerReady(port); // GH-90000

        HttpResponse<String> resp = get("/api/v1/agents/agent-42");

        assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
        Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
        assertThat(body.get("id")).isEqualTo("agent-42");
        assertThat(body.get("config")).isNotNull();
        assertThat(body.get("tenantId")).isNotNull();
        assertThat(body.get("timestamp")).isNotNull();
    }

    @Test
    @DisplayName("getAgent: entity not found in DC → 404")
    void getAgent_whenEntityNotFound_returns404() throws Exception { // GH-90000
        when(mockEntityStore.findById(any(TenantContext.class), eq(EntityStore.EntityId.of("missing-agent"))))
            .thenReturn(Promise.of(Optional.empty())); // GH-90000

        server = new AepHttpServer(engine, port, null, mockDc); // GH-90000
        server.start(); // GH-90000
        waitForServerReady(port); // GH-90000

        HttpResponse<String> resp = get("/api/v1/agents/missing-agent");

        assertThat(resp.statusCode()).isEqualTo(404); // GH-90000
        Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
        assertThat(body.get("message")).isNotNull();
        assertThat(body.get("message").toString()).contains("Agent not found");
    }

    @Test
    @DisplayName("getAgent: no DataCloudClient → 503 service unavailable")
    void getAgent_whenDcNull_returns503() throws Exception { // GH-90000
        server = new AepHttpServer(engine, port); // GH-90000
        server.start(); // GH-90000
        waitForServerReady(port); // GH-90000

        HttpResponse<String> resp = get("/api/v1/agents/any-agent");

        assertThat(resp.statusCode()).isEqualTo(503); // GH-90000
        Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
        assertThat(body.get("message")).isNotNull();
        assertThat(body.get("message").toString()).contains("DataCloudClient not configured");
    }

    // ==================== POST /api/v1/agents/:agentId/execute ====================

    @Test
    @DisplayName("executeAgent: valid body → 200 with agentId and eventId")
    void executeAgent_withValidBody_returns200WithEventId() throws Exception { // GH-90000
        server = new AepHttpServer(engine, port); // GH-90000
        server.start(); // GH-90000
        waitForServerReady(port); // GH-90000

        String reqBody = mapper.writeValueAsString(Map.of( // GH-90000
            "tenantId", "test-tenant",
            "input", Map.of("question", "What is 2+2?") // GH-90000
        ));

        HttpResponse<String> resp = post("/api/v1/agents/agent-99/execute", reqBody); // GH-90000

        assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
        Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
        assertThat(body.get("agentId")).isEqualTo("agent-99");
        assertThat(body.get("tenantId")).isEqualTo("test-tenant");
        assertThat(body.get("eventId")).isNotNull();
        assertThat(body.get("success")).isNotNull();
    }

    @Test
    @DisplayName("executeAgent: empty body → 200 (defaults applied)")
    void executeAgent_withEmptyBody_returns200() throws Exception { // GH-90000
        server = new AepHttpServer(engine, port); // GH-90000
        server.start(); // GH-90000
        waitForServerReady(port); // GH-90000

        HttpResponse<String> resp = post("/api/v1/agents/agent-007/execute", "{}"); // GH-90000

        assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
        Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
        assertThat(body.get("agentId")).isEqualTo("agent-007");
    }

    // ==================== GET /api/v1/agents/:agentId/memory ====================

    @Test
    @DisplayName("getAgentMemory: DC returns mixed-type items → 200 with byType counts")
    void getAgentMemory_withMixedTypes_returns200WithCounts() throws Exception { // GH-90000
        List<DataCloudClient.Entity> items = List.of( // GH-90000
            DataCloudClient.Entity.of("m1", "dc_memory", // GH-90000
                Map.of("agentId", "agent-1", "type", "EPISODIC")), // GH-90000
            DataCloudClient.Entity.of("m2", "dc_memory", // GH-90000
                Map.of("agentId", "agent-1", "type", "EPISODIC")), // GH-90000
            DataCloudClient.Entity.of("m3", "dc_memory", // GH-90000
                Map.of("agentId", "agent-1", "type", "SEMANTIC")), // GH-90000
            DataCloudClient.Entity.of("m4", "dc_memory", // GH-90000
                Map.of("agentId", "agent-1", "type", "PROCEDURAL")) // GH-90000
        );
        when(mockDc.query(anyString(), eq("dc_memory"), any(DataCloudClient.Query.class)))
            .thenReturn(Promise.of(items)); // GH-90000

        server = new AepHttpServer(engine, port, null, mockDc); // GH-90000
        server.start(); // GH-90000
        waitForServerReady(port); // GH-90000

        HttpResponse<String> resp = get("/api/v1/agents/agent-1/memory");

        assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
        Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
        assertThat(body.get("agentId")).isEqualTo("agent-1");
        assertThat(((Number) body.get("total")).intValue()).isEqualTo(4);

        Map<?, ?> byType = (Map<?, ?>) body.get("byType");
        assertThat(((Number) byType.get("episodic")).intValue()).isEqualTo(2);
        assertThat(((Number) byType.get("semantic")).intValue()).isEqualTo(1);
        assertThat(((Number) byType.get("procedural")).intValue()).isEqualTo(1);
        assertThat(((Number) byType.get("preference")).intValue()).isEqualTo(0);
        assertThat(((Number) byType.get("other")).intValue()).isEqualTo(0);
    }

    @Test
    @DisplayName("getAgentMemory: DC returns empty list → 200 with all-zero counts")
    void getAgentMemory_withNoItems_returns200WithZeroCounts() throws Exception { // GH-90000
        when(mockDc.query(anyString(), eq("dc_memory"), any(DataCloudClient.Query.class)))
            .thenReturn(Promise.of(List.of())); // GH-90000

        server = new AepHttpServer(engine, port, null, mockDc); // GH-90000
        server.start(); // GH-90000
        waitForServerReady(port); // GH-90000

        HttpResponse<String> resp = get("/api/v1/agents/agent-empty/memory");

        assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
        Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
        assertThat(((Number) body.get("total")).intValue()).isEqualTo(0);
        Map<?, ?> byType = (Map<?, ?>) body.get("byType");
        assertThat(((Number) byType.get("episodic")).intValue()).isEqualTo(0);
        assertThat(((Number) byType.get("semantic")).intValue()).isEqualTo(0);
    }

    @Test
    @DisplayName("getAgentMemory: no DataCloudClient → 503 service unavailable")
    void getAgentMemory_whenDcNull_returns503() throws Exception { // GH-90000
        server = new AepHttpServer(engine, port); // GH-90000
        server.start(); // GH-90000
        waitForServerReady(port); // GH-90000

        HttpResponse<String> resp = get("/api/v1/agents/agent-1/memory");

        assertThat(resp.statusCode()).isEqualTo(503); // GH-90000
        Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
        assertThat(body.get("message")).isNotNull();
        assertThat(body.get("message").toString()).contains("DataCloudClient not configured");
    }

    // ==================== Helpers ====================

    private HttpResponse<String> get(String path) throws Exception { // GH-90000
        HttpRequest req = HttpRequest.newBuilder() // GH-90000
            .GET() // GH-90000
            .uri(URI.create("http://127.0.0.1:" + port + path)) // GH-90000
            .build(); // GH-90000
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString()); // GH-90000
    }

    private HttpResponse<String> post(String path, String body) throws Exception { // GH-90000
        HttpRequest req = HttpRequest.newBuilder() // GH-90000
            .POST(HttpRequest.BodyPublishers.ofString(body)) // GH-90000
            .uri(URI.create("http://127.0.0.1:" + port + path)) // GH-90000
            .header("Content-Type", "application/json") // GH-90000
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
