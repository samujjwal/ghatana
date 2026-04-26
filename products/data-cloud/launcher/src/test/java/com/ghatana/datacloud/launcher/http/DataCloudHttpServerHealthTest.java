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
    private final ObjectMapper mapper = new ObjectMapper(); // GH-90000

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        mockClient = mock(DataCloudClient.class); // GH-90000
        httpClient = HttpClient.newHttpClient(); // GH-90000
        port = findFreePort(); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (server != null) { // GH-90000
            server.stop(); // GH-90000
        }
    }

    @Test
    @DisplayName("health detail reports not configured for optional subsystems without probes")
    void healthDetailUsesNotConfiguredDefaults() throws Exception { // GH-90000
        startServer(); // GH-90000

        HttpResponse<String> response = get("/health/detail");

        assertThat(response.statusCode()).isEqualTo(200); // GH-90000
        @SuppressWarnings("unchecked")
        Map<String, Object> body = mapper.readValue(response.body(), Map.class); // GH-90000
        @SuppressWarnings("unchecked")
        Map<String, Object> subsystems = (Map<String, Object>) body.get("subsystems");
        @SuppressWarnings("unchecked")
        Map<String, Object> database = (Map<String, Object>) subsystems.get("database");
        assertThat(database).containsEntry("status", "NOT_CONFIGURED"); // GH-90000
        assertThat(database).containsEntry("note", "dependency-not-configured"); // GH-90000
    }

    @Test
    @DisplayName("health deep reports unknown for unconfigured optional subsystems")
    void healthDeepUsesUnknownDefaults() throws Exception { // GH-90000
        startServer(); // GH-90000

        HttpResponse<String> response = get("/health/deep");

        assertThat(response.statusCode()).isEqualTo(200); // GH-90000
        @SuppressWarnings("unchecked")
        Map<String, Object> body = mapper.readValue(response.body(), Map.class); // GH-90000
        @SuppressWarnings("unchecked")
        Map<String, Object> subsystems = (Map<String, Object>) body.get("subsystems");
        @SuppressWarnings("unchecked")
        Map<String, Object> database = (Map<String, Object>) subsystems.get("database");
        assertThat(database).containsEntry("status", "UNKNOWN"); // GH-90000
        assertThat(database).containsEntry("note", "dependency-not-configured"); // GH-90000
    }

    @Test
    @DisplayName("health detail merges injected database subsystem snapshot")
    void healthDetailMergesInjectedSubsystem() throws Exception { // GH-90000
        server = new DataCloudHttpServer(mockClient, port) // GH-90000
            .withHealthSubsystem("database", () -> Map.of( // GH-90000
                "status", "UP",
                "latency_ms", 12,
                "pool_status", "active"
            ));
        server.start(); // GH-90000

        HttpResponse<String> response = get("/health/detail");

        assertThat(response.statusCode()).isEqualTo(200); // GH-90000
        @SuppressWarnings("unchecked")
        Map<String, Object> body = mapper.readValue(response.body(), Map.class); // GH-90000
        assertThat(body).containsEntry("status", "UP"); // GH-90000
        @SuppressWarnings("unchecked")
        Map<String, Object> subsystems = (Map<String, Object>) body.get("subsystems");
        @SuppressWarnings("unchecked")
        Map<String, Object> database = (Map<String, Object>) subsystems.get("database");
        assertThat(database).containsEntry("status", "UP"); // GH-90000
        assertThat(database).containsEntry("latency_ms", 12); // GH-90000
        assertThat(database).containsEntry("pool_status", "active"); // GH-90000
        assertThat(database).containsKey("response_time_ms");
    }

    @Test
    @DisplayName("health deep aliases structured health detail endpoint")
    void healthDeepAliasesHealthDetail() throws Exception { // GH-90000
        server = new DataCloudHttpServer(mockClient, port) // GH-90000
            .withHealthSubsystem("database", () -> Map.of("status", "UP", "latency_ms", 9)); // GH-90000
        server.start(); // GH-90000

        HttpResponse<String> response = get("/health/deep");

        assertThat(response.statusCode()).isEqualTo(200); // GH-90000
        @SuppressWarnings("unchecked")
        Map<String, Object> body = mapper.readValue(response.body(), Map.class); // GH-90000
        assertThat(body).containsEntry("status", "UP"); // GH-90000
        @SuppressWarnings("unchecked")
        Map<String, Object> subsystems = (Map<String, Object>) body.get("subsystems");
        @SuppressWarnings("unchecked")
        Map<String, Object> database = (Map<String, Object>) subsystems.get("database");
        assertThat(database).containsEntry("status", "UP"); // GH-90000
        assertThat(database).containsKey("response_time_ms");
    }

    @Test
    @DisplayName("health detail marks overall status down when subsystem probe fails")
    void healthDetailMarksDownWhenProbeFails() throws Exception { // GH-90000
        server = new DataCloudHttpServer(mockClient, port) // GH-90000
            .withHealthSubsystem("database", () -> { // GH-90000
                throw new IllegalStateException("database probe failed");
            });
        server.start(); // GH-90000

        HttpResponse<String> response = get("/health/detail");

        assertThat(response.statusCode()).isEqualTo(200); // GH-90000
        @SuppressWarnings("unchecked")
        Map<String, Object> body = mapper.readValue(response.body(), Map.class); // GH-90000
        assertThat(body).containsEntry("status", "DOWN"); // GH-90000
        @SuppressWarnings("unchecked")
        Map<String, Object> subsystems = (Map<String, Object>) body.get("subsystems");
        @SuppressWarnings("unchecked")
        Map<String, Object> database = (Map<String, Object>) subsystems.get("database");
        assertThat(database).containsEntry("status", "DOWN"); // GH-90000
        assertThat(database).containsEntry("error", "IllegalStateException"); // GH-90000
    }

    @Test
    @DisplayName("ready returns 503 when database probe is down")
    void readyReturns503WhenDatabaseProbeIsDown() throws Exception { // GH-90000
        server = new DataCloudHttpServer(mockClient, port) // GH-90000
            .withHealthSubsystem("database", () -> Map.of("status", "DOWN", "message", "db unreachable")); // GH-90000
        server.start(); // GH-90000

        HttpResponse<String> response = get("/ready");

        assertThat(response.statusCode()).isEqualTo(503); // GH-90000
        @SuppressWarnings("unchecked")
        Map<String, Object> body = mapper.readValue(response.body(), Map.class); // GH-90000
        assertThat(body).containsEntry("status", "NOT_READY"); // GH-90000
        assertThat(body).containsEntry("message", "Critical dependencies are not ready"); // GH-90000
        @SuppressWarnings("unchecked")
        Map<String, Object> subsystems = (Map<String, Object>) body.get("subsystems");
        @SuppressWarnings("unchecked")
        Map<String, Object> database = (Map<String, Object>) subsystems.get("database");
        assertThat(database).containsEntry("status", "DOWN"); // GH-90000
    }

    @Test
    @DisplayName("ready returns 503 when event store probe is down")
    void readyReturns503WhenEventStoreProbeIsDown() throws Exception { // GH-90000
        server = new DataCloudHttpServer(mockClient, port) // GH-90000
            .withHealthSubsystem("event_store", () -> Map.of("status", "DOWN", "message", "event store unavailable")); // GH-90000
        server.start(); // GH-90000

        HttpResponse<String> response = get("/ready");

        assertThat(response.statusCode()).isEqualTo(503); // GH-90000
        @SuppressWarnings("unchecked")
        Map<String, Object> body = mapper.readValue(response.body(), Map.class); // GH-90000
        assertThat(body).containsEntry("status", "NOT_READY"); // GH-90000
        assertThat(body).containsEntry("message", "Critical dependencies are not ready"); // GH-90000
        @SuppressWarnings("unchecked")
        Map<String, Object> subsystems = (Map<String, Object>) body.get("subsystems");
        @SuppressWarnings("unchecked")
        Map<String, Object> eventStore = (Map<String, Object>) subsystems.get("event_store");
        assertThat(eventStore).containsEntry("status", "DOWN"); // GH-90000
    }

    @Test
    @DisplayName("ready returns 503 when database probe is not configured")
    void readyReturns503WhenDatabaseProbeIsNotConfigured() throws Exception { // GH-90000
        server = new DataCloudHttpServer(mockClient, port) // GH-90000
            .withHealthSubsystem("database", () -> Map.of("status", "NOT_CONFIGURED", "note", "db-unconfigured")); // GH-90000
        server.start(); // GH-90000

        HttpResponse<String> response = get("/ready");

        assertThat(response.statusCode()).isEqualTo(503); // GH-90000
        @SuppressWarnings("unchecked")
        Map<String, Object> body = mapper.readValue(response.body(), Map.class); // GH-90000
        assertThat(body).containsEntry("status", "NOT_READY"); // GH-90000
        assertThat(body).containsEntry("message", "Critical dependencies are not ready"); // GH-90000
        @SuppressWarnings("unchecked")
        Map<String, Object> subsystems = (Map<String, Object>) body.get("subsystems");
        @SuppressWarnings("unchecked")
        Map<String, Object> database = (Map<String, Object>) subsystems.get("database");
        assertThat(database).containsEntry("status", "NOT_CONFIGURED"); // GH-90000
    }

    @Test
    @DisplayName("ready returns 200 when required dependencies are up")
    void readyReturns200WhenRequiredDependenciesAreUp() throws Exception { // GH-90000
        server = new DataCloudHttpServer(mockClient, port) // GH-90000
            .withHealthSubsystem("database", () -> Map.of("status", "UP", "latency_ms", 8)) // GH-90000
            .withHealthSubsystem("event_store", () -> Map.of("status", "UP", "latency_ms", 11)); // GH-90000
        server.start(); // GH-90000

        HttpResponse<String> response = get("/ready");

        assertThat(response.statusCode()).isEqualTo(200); // GH-90000
        @SuppressWarnings("unchecked")
        Map<String, Object> body = mapper.readValue(response.body(), Map.class); // GH-90000
        assertThat(body).containsEntry("status", "READY"); // GH-90000
        @SuppressWarnings("unchecked")
        Map<String, Object> subsystems = (Map<String, Object>) body.get("subsystems");
        @SuppressWarnings("unchecked")
        Map<String, Object> database = (Map<String, Object>) subsystems.get("database");
        @SuppressWarnings("unchecked")
        Map<String, Object> eventStore = (Map<String, Object>) subsystems.get("event_store");
        assertThat(database).containsEntry("status", "UP"); // GH-90000
        assertThat(eventStore).containsEntry("status", "UP"); // GH-90000
    }

    private void startServer() throws Exception { // GH-90000
        server = new DataCloudHttpServer(mockClient, port); // GH-90000
        server.start(); // GH-90000
    }

    private HttpResponse<String> get(String path) throws Exception { // GH-90000
        HttpRequest request = HttpRequest.newBuilder() // GH-90000
            .uri(URI.create("http://localhost:" + port + path)) // GH-90000
            .GET() // GH-90000
            .build(); // GH-90000
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString()); // GH-90000
    }

    private int findFreePort() throws IOException { // GH-90000
        try (ServerSocket socket = new ServerSocket(0)) { // GH-90000
            return socket.getLocalPort(); // GH-90000
        }
    }
}
