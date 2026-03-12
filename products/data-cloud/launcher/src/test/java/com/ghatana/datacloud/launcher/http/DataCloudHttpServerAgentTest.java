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
import java.util.HashMap;
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
 * Integration tests for Data-Cloud HTTP agent-registry endpoints (DC-3).
 *
 * <p>Starts a real {@link DataCloudHttpServer} on a random port and makes
 * HTTP calls via the Java standard {@link java.net.http.HttpClient}.
 * The {@link DataCloudClient} is mocked so tests do not touch real storage.
 *
 * @doc.type class
 * @doc.purpose Integration tests for /api/v1/agents/** HTTP endpoints (DC-3)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudHttpServer – Agent Registry Endpoints (DC-3)")
class DataCloudHttpServerAgentTest {

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

    // ==================== GET /api/v1/agents ====================

    @Nested
    @DisplayName("GET /api/v1/agents – list agents")
    class ListAgentTests {

        @Test
        @DisplayName("returns 200 with agents list when agents exist")
        void listAgents_withAgents_returns200WithList() throws Exception {
            List<DataCloudClient.Entity> agents = List.of(
                DataCloudClient.Entity.of("agent-1", "dc_agents",
                    Map.of("name", "code-reviewer", "domain", "code")),
                DataCloudClient.Entity.of("agent-2", "dc_agents",
                    Map.of("name", "test-generator", "domain", "testing"))
            );
            when(mockClient.query(anyString(), eq("dc_agents"), any(DataCloudClient.Query.class)))
                .thenReturn(Promise.of(agents));

            startServer();

            HttpResponse<String> resp = get("/api/v1/agents");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body.get("agents")).isNotNull();
            assertThat(((List<?>) body.get("agents"))).hasSize(2);
            assertThat(((Number) body.get("count")).intValue()).isEqualTo(2);
        }

        @Test
        @DisplayName("returns 200 with empty list when no agents registered")
        void listAgents_noAgents_returnsEmptyList() throws Exception {
            when(mockClient.query(anyString(), eq("dc_agents"), any(DataCloudClient.Query.class)))
                .thenReturn(Promise.of(List.of()));

            startServer();

            HttpResponse<String> resp = get("/api/v1/agents");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
            assertThat(((List<?>) body.get("agents"))).isEmpty();
            assertThat(((Number) body.get("count")).intValue()).isEqualTo(0);
        }

        @Test
        @DisplayName("response includes tenantId and timestamp metadata")
        void listAgents_always_includesMetadata() throws Exception {
            when(mockClient.query(anyString(), eq("dc_agents"), any(DataCloudClient.Query.class)))
                .thenReturn(Promise.of(List.of()));

            startServer();

            HttpResponse<String> resp = get("/api/v1/agents");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body.get("tenantId")).isNotNull();
            assertThat(body.get("timestamp")).isNotNull();
            assertThat(body.get("count")).isNotNull();
        }

        @Test
        @DisplayName("X-Tenant-Id header is propagated to query")
        void listAgents_withTenantHeader_passesTenantToQuery() throws Exception {
            when(mockClient.query(eq("acme-corp"), eq("dc_agents"), any(DataCloudClient.Query.class)))
                .thenReturn(Promise.of(List.of()));

            startServer();

            HttpResponse<String> resp = getWithTenant("/api/v1/agents", "acme-corp");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body.get("tenantId")).isEqualTo("acme-corp");
            verify(mockClient).query(eq("acme-corp"), eq("dc_agents"), any());
        }

        @Test
        @DisplayName("each agent entry has id, collection, and data fields")
        void listAgents_entities_haveRequiredFields() throws Exception {
            List<DataCloudClient.Entity> agents = List.of(
                DataCloudClient.Entity.of("ag-x", "dc_agents",
                    Map.of("name", "my-agent", "version", "1.0"))
            );
            when(mockClient.query(anyString(), eq("dc_agents"), any(DataCloudClient.Query.class)))
                .thenReturn(Promise.of(agents));

            startServer();

            HttpResponse<String> resp = get("/api/v1/agents");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
            List<?> agentList = (List<?>) body.get("agents");
            assertThat(agentList).hasSize(1);
            Map<?, ?> agent = (Map<?, ?>) agentList.get(0);
            assertThat(agent.get("id")).isEqualTo("ag-x");
            assertThat(agent.get("collection")).isNotNull();
            assertThat(agent.get("data")).isNotNull();
        }
    }

    // ==================== POST /api/v1/agents ====================

    @Nested
    @DisplayName("POST /api/v1/agents – register agent")
    class RegisterAgentTests {

        @Test
        @DisplayName("valid agent body → 200 with id and registeredAt")
        void registerAgent_validBody_returns200WithId() throws Exception {
            Map<String, Object> agentRequest = Map.of(
                "id", "my-agent-1",
                "name", "Code Reviewer",
                "domain", "engineering"
            );
            DataCloudClient.Entity saved = DataCloudClient.Entity.of(
                "my-agent-1", "dc_agents", agentRequest);
            when(mockClient.save(anyString(), eq("dc_agents"), any()))
                .thenReturn(Promise.of(saved));

            startServer();

            HttpResponse<String> resp = post("/api/v1/agents", agentRequest);

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body.get("id")).isEqualTo("my-agent-1");
            assertThat(body.get("registeredAt")).isNotNull();
            assertThat(body.get("tenantId")).isNotNull();
        }

        @Test
        @DisplayName("invalid JSON body → 400")
        void registerAgent_invalidJson_returns400() throws Exception {
            startServer();

            HttpResponse<String> resp = postRaw("/api/v1/agents", "not-valid-json{");

            assertThat(resp.statusCode()).isEqualTo(400);
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body.get("error")).isNotNull();
        }

        @Test
        @DisplayName("agent saved to dc_agents collection")
        void registerAgent_savesTo_dcAgentsCollection() throws Exception {
            Map<String, Object> agentData = Map.of("id", "ag-save", "name", "test-agent");
            DataCloudClient.Entity saved = DataCloudClient.Entity.of("ag-save", "dc_agents", agentData);
            when(mockClient.save(anyString(), eq("dc_agents"), any()))
                .thenReturn(Promise.of(saved));

            startServer();

            post("/api/v1/agents", agentData);

            verify(mockClient).save(anyString(), eq("dc_agents"), any());
        }
    }

    // ==================== GET /api/v1/agents/:agentId ====================

    @Nested
    @DisplayName("GET /api/v1/agents/:agentId – get agent by ID")
    class GetAgentTests {

        @Test
        @DisplayName("existing agent → 200 with data")
        void getAgent_exists_returns200WithData() throws Exception {
            DataCloudClient.Entity entity = DataCloudClient.Entity.of(
                "agent-42", "dc_agents", Map.of("name", "bug-fixer", "version", "2.1"));
            when(mockClient.findById(anyString(), eq("dc_agents"), eq("agent-42")))
                .thenReturn(Promise.of(Optional.of(entity)));

            startServer();

            HttpResponse<String> resp = get("/api/v1/agents/agent-42");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body.get("id")).isEqualTo("agent-42");
            assertThat(body.get("data")).isNotNull();
            assertThat(body.get("tenantId")).isNotNull();
        }

        @Test
        @DisplayName("non-existent agent → 404")
        void getAgent_notFound_returns404() throws Exception {
            when(mockClient.findById(anyString(), eq("dc_agents"), eq("ghost-agent")))
                .thenReturn(Promise.of(Optional.empty()));

            startServer();

            HttpResponse<String> resp = get("/api/v1/agents/ghost-agent");

            assertThat(resp.statusCode()).isEqualTo(404);
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body.get("error").toString()).contains("ghost-agent");
        }

        @Test
        @DisplayName("tenantId in response matches header")
        void getAgent_tenantIdInResponse_matchesHeader() throws Exception {
            DataCloudClient.Entity entity = DataCloudClient.Entity.of(
                "ag-t", "dc_agents", Map.of("name", "tenant-agent"));
            when(mockClient.findById(eq("tenant-x"), eq("dc_agents"), eq("ag-t")))
                .thenReturn(Promise.of(Optional.of(entity)));

            startServer();

            HttpResponse<String> resp = getWithTenant("/api/v1/agents/ag-t", "tenant-x");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body.get("tenantId")).isEqualTo("tenant-x");
        }
    }

    // ==================== DELETE /api/v1/agents/:agentId ====================

    @Nested
    @DisplayName("DELETE /api/v1/agents/:agentId – delete agent")
    class DeleteAgentTests {

        @Test
        @DisplayName("existing agent → 200 with deleted=true")
        void deleteAgent_exists_returns200() throws Exception {
            when(mockClient.delete(anyString(), eq("dc_agents"), eq("agent-del")))
                .thenReturn(Promise.of(null));

            startServer();

            HttpResponse<String> resp = delete("/api/v1/agents/agent-del");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
            assertThat((Boolean) body.get("deleted")).isTrue();
            assertThat(body.get("agentId")).isEqualTo("agent-del");
            assertThat(body.get("timestamp")).isNotNull();
        }

        @Test
        @DisplayName("delete propagates agentId to client")
        void deleteAgent_propagatesIdToClient() throws Exception {
            when(mockClient.delete(anyString(), eq("dc_agents"), eq("del-me")))
                .thenReturn(Promise.of(null));

            startServer();

            delete("/api/v1/agents/del-me");

            verify(mockClient).delete(anyString(), eq("dc_agents"), eq("del-me"));
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

    private HttpResponse<String> getWithTenant(String path, String tenantId) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
            .GET()
            .uri(URI.create("http://127.0.0.1:" + port + path))
            .header("X-Tenant-Id", tenantId)
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
