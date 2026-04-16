package com.ghatana.datacloud.launcher.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;


@DisplayName("DataCloudHttpServer – Health Endpoints")
class DataCloudHttpServerHealthTest {

    private DataCloudClient mockClient;
    private DataCloudHttpServer server;
    private HttpClient httpClient;
    private int port;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        mockClient = mock(DataCloudClient.class);
        httpClient = HttpClient.newHttpClient();
        port = findFreePort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    @DisplayName("health detail reports not configured for optional subsystems without probes")
    void healthDetailUsesNotConfiguredDefaults() throws Exception {
        startServer();

        HttpResponse<String> response = get("/health/detail");

        assertThat(response.statusCode()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = mapper.readValue(response.body(), Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> subsystems = (Map<String, Object>) body.get("subsystems");
        @SuppressWarnings("unchecked")
        Map<String, Object> database = (Map<String, Object>) subsystems.get("database");
        assertThat(database).containsEntry("status", "NOT_CONFIGURED");
        assertThat(database).containsEntry("note", "dependency-not-configured");
    }

    @Test
    @DisplayName("health detail merges injected database subsystem snapshot")
    void healthDetailMergesInjectedSubsystem() throws Exception {
        server = new DataCloudHttpServer(mockClient, port)
            .withHealthSubsystem("database", () -> Map.of(
                "status", "UP",
                "latency_ms", 12,
                "pool_status", "active"
            ));
        server.start();

        HttpResponse<String> response = get("/health/detail");

        assertThat(response.statusCode()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = mapper.readValue(response.body(), Map.class);
        assertThat(body).containsEntry("status", "UP");
        @SuppressWarnings("unchecked")
        Map<String, Object> subsystems = (Map<String, Object>) body.get("subsystems");
        @SuppressWarnings("unchecked")
        Map<String, Object> database = (Map<String, Object>) subsystems.get("database");
        assertThat(database).containsEntry("status", "UP");
        assertThat(database).containsEntry("latency_ms", 12);
        assertThat(database).containsEntry("pool_status", "active");
    }

    @Test
    @DisplayName("health detail marks overall status down when subsystem probe fails")
    void healthDetailMarksDownWhenProbeFails() throws Exception {
        server = new DataCloudHttpServer(mockClient, port)
            .withHealthSubsystem("database", () -> {
                throw new IllegalStateException("database probe failed");
            });
        server.start();

        HttpResponse<String> response = get("/health/detail");

        assertThat(response.statusCode()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = mapper.readValue(response.body(), Map.class);
        assertThat(body).containsEntry("status", "DOWN");
        @SuppressWarnings("unchecked")
        Map<String, Object> subsystems = (Map<String, Object>) body.get("subsystems");
        @SuppressWarnings("unchecked")
        Map<String, Object> database = (Map<String, Object>) subsystems.get("database");
        assertThat(database).containsEntry("status", "DOWN");
        assertThat(database).containsEntry("error", "IllegalStateException");
    }

    @Test
    @DisplayName("ready returns 503 when database probe is down")
    void readyReturns503WhenDatabaseProbeIsDown() throws Exception {
        server = new DataCloudHttpServer(mockClient, port)
            .withHealthSubsystem("database", () -> Map.of("status", "DOWN", "message", "db unreachable"));
        server.start();

        HttpResponse<String> response = get("/ready");

        assertThat(response.statusCode()).isEqualTo(503);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = mapper.readValue(response.body(), Map.class);
        assertThat(body).containsEntry("status", "NOT_READY");
        assertThat(body).containsEntry("message", "Critical dependencies are not ready");
        @SuppressWarnings("unchecked")
        Map<String, Object> subsystems = (Map<String, Object>) body.get("subsystems");
        @SuppressWarnings("unchecked")
        Map<String, Object> database = (Map<String, Object>) subsystems.get("database");
        assertThat(database).containsEntry("status", "DOWN");
    }

    @Test
    @DisplayName("ready returns 503 when event store probe is down")
    void readyReturns503WhenEventStoreProbeIsDown() throws Exception {
        server = new DataCloudHttpServer(mockClient, port)
            .withHealthSubsystem("event_store", () -> Map.of("status", "DOWN", "message", "event store unavailable"));
        server.start();

        HttpResponse<String> response = get("/ready");

        assertThat(response.statusCode()).isEqualTo(503);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = mapper.readValue(response.body(), Map.class);
        assertThat(body).containsEntry("status", "NOT_READY");
        assertThat(body).containsEntry("message", "Critical dependencies are not ready");
        @SuppressWarnings("unchecked")
        Map<String, Object> subsystems = (Map<String, Object>) body.get("subsystems");
        @SuppressWarnings("unchecked")
        Map<String, Object> eventStore = (Map<String, Object>) subsystems.get("event_store");
        assertThat(eventStore).containsEntry("status", "DOWN");
    }

    private void startServer() throws Exception {
        server = new DataCloudHttpServer(mockClient, port);
        server.start();
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + path))
            .GET()
            .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
