package com.ghatana.aep.server.http;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.aep.Aep;
import com.ghatana.aep.AepEngine;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.spi.EntityStore;
import com.ghatana.datacloud.spi.TenantContext;
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
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Integration coverage for AEP registry create/read canonical routes
 * @doc.layer product
 * @doc.pattern Test
 */
@Tag("local-network")
@DisplayName("AEP Agent Registry Integration")
class AepHttpServerAgentRegistryIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AepEngine engine;
    private DataCloudClient dataCloudClient;
    private EntityStore entityStore;
    private AepHttpServer server;
    private HttpClient httpClient;
    private int port;

    @BeforeEach
    void setUp() throws Exception {
        System.setProperty("AEP_ENV", "test");
        System.setProperty("AEP_AUTH_DISABLED", "true");

        engine = Aep.forTesting();
        dataCloudClient = mock(DataCloudClient.class);
        entityStore = mock(EntityStore.class);
        when(dataCloudClient.entityStore()).thenReturn(entityStore);

        port = findFreePort();
        httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
        if (engine != null) {
            engine.close();
        }
        System.clearProperty("AEP_ENV");
        System.clearProperty("AEP_AUTH_DISABLED");
    }

    @Test
    @DisplayName("AR-1: POST and GET round-trip works for /api/v1/agents")
    void shouldCreateAndReadAgentViaCanonicalRoutes() throws Exception {
        final String tenantId = "tenant-ar-1";
        final String agentId = "agent-ar-1";

        when(entityStore.save(any(TenantContext.class), any(EntityStore.Entity.class)))
            .thenAnswer(invocation -> Promise.of(invocation.getArgument(1)));

        EntityStore.Entity storedEntity = EntityStore.Entity.builder()
            .id(agentId)
            .collection("aep_agents")
            .data(Map.of(
                "id", agentId,
                "name", "Registry AR Agent",
                "tenantId", tenantId,
                "type", "DETERMINISTIC",
                "status", "ACTIVE",
                "version", "1.0.0",
                "registrationMode", "direct",
                "executable", true,
                "createdAt", "2026-04-22T10:00:00Z"
            ))
            .build();

        when(entityStore.findByRef(any(TenantContext.class), any(EntityStore.EntityRef.class)))
            .thenAnswer(invocation -> {
                TenantContext tenant = invocation.getArgument(0);
                EntityStore.EntityRef requestedRef = invocation.getArgument(1);
                if (tenantId.equals(tenant.tenantId()) && agentId.equals(requestedRef.entityId().value())) {
                    return Promise.of(Optional.of(storedEntity));
                }
                return Promise.of(Optional.empty());
            });

        server = new AepHttpServer(engine, port, null, dataCloudClient);
        server.start();
        waitForServerReady(port);

        Map<String, Object> createPayload = Map.of(
            "id", agentId,
            "tenantId", tenantId,
            "name", "Registry AR Agent",
            "type", "DETERMINISTIC",
            "config", Map.of("mode", "strict")
        );
        HttpResponse<String> createResponse = post("/api/v1/agents", MAPPER.writeValueAsString(createPayload));
        Map<String, Object> createBody = MAPPER.readValue(createResponse.body(), new TypeReference<>() {});

        assertThat(createResponse.statusCode()).isEqualTo(200);
        assertThat(createBody).containsEntry("id", agentId);
        assertThat(createBody).containsEntry("tenantId", tenantId);
        assertThat(createBody).containsEntry("status", "ACTIVE");

        HttpResponse<String> getResponse = get("/api/v1/agents/" + agentId + "?tenantId=" + tenantId);
        Map<String, Object> getBody = MAPPER.readValue(getResponse.body(), new TypeReference<>() {});

        assertThat(getResponse.statusCode()).isEqualTo(200);
        assertThat(getBody).containsEntry("id", agentId);
        assertThat(getBody).containsEntry("tenantId", tenantId);
        assertThat(getBody).containsEntry("registrationMode", "direct");
        assertThat(getBody).containsEntry("executable", true);
    }

    @Test
    @DisplayName("AR-2: execute route returns execution envelope for /api/v1/agents/{agentId}/execute")
    void shouldExecuteAgentViaCanonicalExecuteRoute() throws Exception {
        server = new AepHttpServer(engine, port);
        server.start();
        waitForServerReady(port);

        Map<String, Object> executePayload = Map.of(
            "tenantId", "tenant-ar-2",
            "input", Map.of("task", "run")
        );

        HttpResponse<String> response = post(
            "/api/v1/agents/agent-ar-2/execute",
            MAPPER.writeValueAsString(executePayload)
        );

        Map<String, Object> body = MAPPER.readValue(response.body(), new TypeReference<>() {});

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(body).containsEntry("agentId", "agent-ar-2");
        assertThat(body).containsEntry("tenantId", "tenant-ar-2");
        assertThat(body).containsKey("eventId");
        assertThat(body).containsKey("success");
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .GET()
            .uri(URI.create("http://127.0.0.1:" + port + path))
            .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .uri(URI.create("http://127.0.0.1:" + port + path))
            .header("Content-Type", "application/json")
            .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
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
        throw new AssertionError("Server did not start on port " + port + " within 5 seconds");
    }
}

