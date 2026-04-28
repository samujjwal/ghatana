package com.ghatana.aep.server.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.aep.Aep;
import com.ghatana.aep.AepEngine;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Integration coverage for marketplace simulation and pinned installs
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AepHttpServer – Marketplace Endpoints")
class AepHttpServerMarketplaceTest {

    private static final String TENANT_ID = "tenant-acme";

    private AepEngine engine;
    private AepHttpServer server;
    private int port;
    private HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        engine = Aep.forTesting();
        port = findFreePort();
        server = new AepHttpServer(engine, port);
        server.start();
        waitForServerReady(port);
        httpClient = HttpClient.newBuilder().build();
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop();
        if (engine != null) engine.close();
    }

    @Test
    @DisplayName("simulate-install returns compatibility and execution-path truth")
    void simulateInstallReturnsExecutionTruth() throws Exception {
        String agentId = publishAgent("tenant-agent", "2.1.0");

        HttpResponse<String> response = post(
                "/api/v1/catalog/marketplace/agents/" + agentId + "/simulate-install",
                mapper.writeValueAsString(Map.of(
                        "targetEnvironment", "production",
                        "expectedVersion", "2.1.0"
                )));

        assertThat(response.statusCode()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = mapper.readValue(response.body(), Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> simulation = (Map<String, Object>) body.get("simulation");
        assertThat(simulation.get("compatibilityStatus")).isEqualTo("REVIEW_REQUIRED");
        assertThat(simulation.get("versionPinned")).isEqualTo(true);
        assertThat(simulation.get("directExecutionMode")).isEqualTo("SANDBOX_ONLY");
        assertThat(simulation.get("productionExecutionMode")).isEqualTo("PIPELINE_HITL_REQUIRED");
    }

    @Test
    @DisplayName("install requires expectedVersion and returns pinned install confirmation")
    void installRequiresExpectedVersionAndReturnsCompatibilityContext() throws Exception {
        String agentId = publishAgent("tenant-agent", "2.1.0");

        HttpResponse<String> missingVersion = post(
                "/api/v1/catalog/marketplace/agents/" + agentId + "/install",
                mapper.writeValueAsString(Map.of("targetEnvironment", "sandbox")));
        assertThat(missingVersion.statusCode()).isEqualTo(400);

        HttpResponse<String> installResponse = post(
                "/api/v1/catalog/marketplace/agents/" + agentId + "/install",
                mapper.writeValueAsString(Map.of(
                        "targetEnvironment", "sandbox",
                        "expectedVersion", "2.1.0"
                )));

        assertThat(installResponse.statusCode()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = mapper.readValue(installResponse.body(), Map.class);
        assertThat(body.get("agentVersion")).isEqualTo("2.1.0");
        assertThat(body.get("compatibilityStatus")).isEqualTo("COMPATIBLE");
        assertThat(body.get("recommendedPath")).isEqualTo("sandbox_direct");
    }

    private String publishAgent(String id, String version) throws Exception {
        HttpResponse<String> response = post(
                "/api/v1/catalog/marketplace/agents",
                mapper.writeValueAsString(Map.of(
                        "id", id,
                        "name", "Tenant Agent",
                        "tenantId", TENANT_ID,
                        "version", version,
                        "domain", "operations",
                        "level", "worker",
                        "capabilities", java.util.List.of("triage", "explain"),
                        "tags", java.util.List.of("trusted")
                )));
        assertThat(response.statusCode()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = mapper.readValue(response.body(), Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> agent = (Map<String, Object>) body.get("agent");
        return String.valueOf(agent.get("id"));
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + path))
                        .header("Content-Type", "application/json")
                        .header("X-Tenant-Id", TENANT_ID)
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void waitForServerReady(int targetPort) throws Exception {
        for (int i = 0; i < 50; i++) {
            try (Socket ignored = new Socket("localhost", targetPort)) {
                return;
            } catch (IOException ignored) {
                Thread.sleep(100);
            }
        }
        throw new IllegalStateException("Server did not start within 5 seconds on port " + targetPort);
    }
}
