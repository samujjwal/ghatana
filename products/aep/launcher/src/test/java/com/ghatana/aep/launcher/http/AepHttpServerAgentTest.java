package com.ghatana.aep.launcher.http;

import com.ghatana.aep.Aep;
import com.ghatana.aep.AepEngine;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for AEP HTTP agent endpoints (AEP-P5/P7).
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
@DisplayName("AepHttpServer – Agent Endpoints")
class AepHttpServerAgentTest {

    private AepEngine engine;
    private DataCloudClient mockDc;
    private AepHttpServer server;
    private int port;
    private HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        engine = Aep.forTesting();
        mockDc = mock(DataCloudClient.class);
        port = findFreePort();
        httpClient = HttpClient.newBuilder().build();
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop();
        if (engine != null) engine.close();
    }

    // ==================== GET /api/v1/agents ====================

    @Test
    @DisplayName("listAgents: no DataCloudClient → 200 with empty list and explanatory note")
    void listAgents_whenDcNull_returns200WithEmptyListAndNote() throws Exception {
        server = new AepHttpServer(engine, port);
        server.start();
        waitForServerReady(port);

        HttpResponse<String> resp = get("/api/v1/agents");

        assertThat(resp.statusCode()).isEqualTo(200);
        Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
        assertThat(body.get("count")).isEqualTo(0);
        assertThat((List<?>) body.get("agents")).isEmpty();
        assertThat(body.get("note").toString()).contains("DataCloud not configured");
    }

    @Test
    @DisplayName("listAgents: DC returns 2 entities → 200 with agent summaries")
    void listAgents_withDcReturningEntities_returns200WithCount() throws Exception {
        List<DataCloudClient.Entity> entities = List.of(
            DataCloudClient.Entity.of("agent-alpha", "agent-registry",
                Map.of("name", "Alpha", "type", "LLM", "status", "ACTIVE")),
            DataCloudClient.Entity.of("agent-beta", "agent-registry",
                Map.of("name", "Beta", "type", "RULE", "status", "ACTIVE"))
        );
        when(mockDc.query(anyString(), eq("agent-registry"), any(DataCloudClient.Query.class)))
            .thenReturn(Promise.of(entities));

        server = new AepHttpServer(engine, port, null, mockDc);
        server.start();
        waitForServerReady(port);

        HttpResponse<String> resp = get("/api/v1/agents");

        assertThat(resp.statusCode()).isEqualTo(200);
        Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
        assertThat(body.get("count")).isEqualTo(2);
        List<?> agents = (List<?>) body.get("agents");
        assertThat(agents).hasSize(2);
    }

    @Test
    @DisplayName("listAgents: DC returns empty list → 200 with count=0")
    void listAgents_withEmptyDcResult_returns200WithZeroCount() throws Exception {
        when(mockDc.query(anyString(), eq("agent-registry"), any(DataCloudClient.Query.class)))
            .thenReturn(Promise.of(List.of()));

        server = new AepHttpServer(engine, port, null, mockDc);
        server.start();
        waitForServerReady(port);

        HttpResponse<String> resp = get("/api/v1/agents");

        assertThat(resp.statusCode()).isEqualTo(200);
        Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
        assertThat(body.get("count")).isEqualTo(0);
        assertThat((List<?>) body.get("agents")).isEmpty();
    }

    // ==================== GET /api/v1/agents/:agentId ====================

    @Test
    @DisplayName("getAgent: entity found in DC → 200 with agent detail")
    void getAgent_whenEntityFound_returns200WithData() throws Exception {
        DataCloudClient.Entity entity = DataCloudClient.Entity.of(
            "agent-42", "agent-registry",
            Map.of("name", "Sentinel", "type", "LLM", "status", "ACTIVE"));
        when(mockDc.findById(anyString(), eq("agent-registry"), eq("agent-42")))
            .thenReturn(Promise.of(Optional.of(entity)));

        server = new AepHttpServer(engine, port, null, mockDc);
        server.start();
        waitForServerReady(port);

        HttpResponse<String> resp = get("/api/v1/agents/agent-42");

        assertThat(resp.statusCode()).isEqualTo(200);
        Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
        assertThat(body.get("id")).isEqualTo("agent-42");
        assertThat(body.get("data")).isNotNull();
        assertThat(body.get("tenantId")).isNotNull();
        assertThat(body.get("timestamp")).isNotNull();
    }

    @Test
    @DisplayName("getAgent: entity not found in DC → 404")
    void getAgent_whenEntityNotFound_returns404() throws Exception {
        when(mockDc.findById(anyString(), eq("agent-registry"), eq("missing-agent")))
            .thenReturn(Promise.of(Optional.empty()));

        server = new AepHttpServer(engine, port, null, mockDc);
        server.start();
        waitForServerReady(port);

        HttpResponse<String> resp = get("/api/v1/agents/missing-agent");

        assertThat(resp.statusCode()).isEqualTo(404);
        Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
        assertThat(body.get("error").toString()).contains("Agent not found");
    }

    @Test
    @DisplayName("getAgent: no DataCloudClient → 503 service unavailable")
    void getAgent_whenDcNull_returns503() throws Exception {
        server = new AepHttpServer(engine, port);
        server.start();
        waitForServerReady(port);

        HttpResponse<String> resp = get("/api/v1/agents/any-agent");

        assertThat(resp.statusCode()).isEqualTo(503);
        Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
        assertThat(body.get("error").toString()).contains("DataCloudClient not configured");
    }

    // ==================== POST /api/v1/agents/:agentId/execute ====================

    @Test
    @DisplayName("executeAgent: valid body → 200 with agentId and eventId")
    void executeAgent_withValidBody_returns200WithEventId() throws Exception {
        server = new AepHttpServer(engine, port);
        server.start();
        waitForServerReady(port);

        String reqBody = mapper.writeValueAsString(Map.of(
            "tenantId", "test-tenant",
            "input", Map.of("question", "What is 2+2?")
        ));

        HttpResponse<String> resp = post("/api/v1/agents/agent-99/execute", reqBody);

        assertThat(resp.statusCode()).isEqualTo(200);
        Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
        assertThat(body.get("agentId")).isEqualTo("agent-99");
        assertThat(body.get("tenantId")).isEqualTo("test-tenant");
        assertThat(body.get("eventId")).isNotNull();
        assertThat(body.get("success")).isNotNull();
    }

    @Test
    @DisplayName("executeAgent: empty body → 200 (defaults applied)")
    void executeAgent_withEmptyBody_returns200() throws Exception {
        server = new AepHttpServer(engine, port);
        server.start();
        waitForServerReady(port);

        HttpResponse<String> resp = post("/api/v1/agents/agent-007/execute", "{}");

        assertThat(resp.statusCode()).isEqualTo(200);
        Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
        assertThat(body.get("agentId")).isEqualTo("agent-007");
    }

    // ==================== GET /api/v1/agents/:agentId/memory ====================

    @Test
    @DisplayName("getAgentMemory: DC returns mixed-type items → 200 with byType counts")
    void getAgentMemory_withMixedTypes_returns200WithCounts() throws Exception {
        List<DataCloudClient.Entity> items = List.of(
            DataCloudClient.Entity.of("m1", "dc_memory",
                Map.of("agentId", "agent-1", "type", "EPISODIC")),
            DataCloudClient.Entity.of("m2", "dc_memory",
                Map.of("agentId", "agent-1", "type", "EPISODIC")),
            DataCloudClient.Entity.of("m3", "dc_memory",
                Map.of("agentId", "agent-1", "type", "SEMANTIC")),
            DataCloudClient.Entity.of("m4", "dc_memory",
                Map.of("agentId", "agent-1", "type", "PROCEDURAL"))
        );
        when(mockDc.query(anyString(), eq("dc_memory"), any(DataCloudClient.Query.class)))
            .thenReturn(Promise.of(items));

        server = new AepHttpServer(engine, port, null, mockDc);
        server.start();
        waitForServerReady(port);

        HttpResponse<String> resp = get("/api/v1/agents/agent-1/memory");

        assertThat(resp.statusCode()).isEqualTo(200);
        Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
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
    void getAgentMemory_withNoItems_returns200WithZeroCounts() throws Exception {
        when(mockDc.query(anyString(), eq("dc_memory"), any(DataCloudClient.Query.class)))
            .thenReturn(Promise.of(List.of()));

        server = new AepHttpServer(engine, port, null, mockDc);
        server.start();
        waitForServerReady(port);

        HttpResponse<String> resp = get("/api/v1/agents/agent-empty/memory");

        assertThat(resp.statusCode()).isEqualTo(200);
        Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
        assertThat(((Number) body.get("total")).intValue()).isEqualTo(0);
        Map<?, ?> byType = (Map<?, ?>) body.get("byType");
        assertThat(((Number) byType.get("episodic")).intValue()).isEqualTo(0);
        assertThat(((Number) byType.get("semantic")).intValue()).isEqualTo(0);
    }

    @Test
    @DisplayName("getAgentMemory: no DataCloudClient → 503 service unavailable")
    void getAgentMemory_whenDcNull_returns503() throws Exception {
        server = new AepHttpServer(engine, port);
        server.start();
        waitForServerReady(port);

        HttpResponse<String> resp = get("/api/v1/agents/agent-1/memory");

        assertThat(resp.statusCode()).isEqualTo(503);
        Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
        assertThat(body.get("error").toString()).contains("DataCloudClient not configured");
    }

    // ==================== Helpers ====================

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
            .GET()
            .uri(URI.create("http://127.0.0.1:" + port + path))
            .build();
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .uri(URI.create("http://127.0.0.1:" + port + path))
            .header("Content-Type", "application/json")
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
