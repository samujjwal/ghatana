package com.ghatana.datacloud.launcher.http;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.spi.EntityStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for mastery endpoints exposed through DataCloudHttpServer.
 *
 * @doc.type class
 * @doc.purpose Verify mastery routes are wired through launcher startup and enforce request validation
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudHttpServer – Mastery Endpoints")
class DataCloudHttpServerMasteryTest {

    private DataCloudClient mockClient;
    private DataCloudHttpServer server;
    private HttpClient httpClient;
    private int port;

    @BeforeEach
    void setUp() throws Exception {
        mockClient = mock(DataCloudClient.class);
        when(mockClient.entityStore()).thenReturn(mock(EntityStore.class));

        httpClient = HttpClient.newHttpClient();
        port = randomPort();
        server = new DataCloudHttpServer(mockClient, port);
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    @DisplayName("GET /api/v1/mastery/preview/decision is reachable and validates tenantId")
    void previewDecisionRouteReachableAndValidatesTenant() throws Exception {
        HttpResponse<String> response = get("/api/v1/mastery/preview/decision?agentId=a-1&skillId=s-1");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("tenantId is required");
    }

    @Test
    @DisplayName("GET /api/v1/mastery/preview/retrieval is reachable and validates tenantId")
    void previewRetrievalRouteReachableAndValidatesTenant() throws Exception {
        HttpResponse<String> response = get("/api/v1/mastery/preview/retrieval?agentId=a-1&skillId=s-1&limit=5");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("tenantId is required");
    }

    @Test
    @DisplayName("POST /api/v1/mastery/learning-deltas/:deltaId/dry-run-promotion is reachable and validates tenantId")
    void dryRunPromotionRouteReachableAndValidatesTenant() throws Exception {
        HttpResponse<String> response = post(
                "/api/v1/mastery/learning-deltas/delta-1/dry-run-promotion",
                "{}");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("tenantId is required");
    }

    @Test
    @DisplayName("POST /api/v1/mastery/obsolescence-events/process is reachable and validates tenantId")
    void obsolescenceProcessRouteReachableAndValidatesTenant() throws Exception {
        HttpResponse<String> response = post("/api/v1/mastery/obsolescence-events/process", "{}");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("tenantId is required");
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private int randomPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
