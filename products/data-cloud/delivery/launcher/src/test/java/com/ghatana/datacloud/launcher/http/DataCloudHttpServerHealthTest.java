package com.ghatana.datacloud.launcher.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
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
    @DisplayName("health deep reports unknown for unconfigured optional subsystems")
    void healthDeepUsesUnknownDefaults() throws Exception { 
        startServer(); 

        HttpResponse<String> response = get("/health/deep");

        assertThat(response.statusCode()).isEqualTo(200); 
        @SuppressWarnings("unchecked")
        Map<String, Object> body = mapper.readValue(response.body(), Map.class); 
        @SuppressWarnings("unchecked")
        Map<String, Object> subsystems = (Map<String, Object>) body.get("subsystems");
        @SuppressWarnings("unchecked")
        Map<String, Object> database = (Map<String, Object>) subsystems.get("database");
        assertThat(database).containsEntry("status", "UNKNOWN"); 
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
        // Overall status may be DEGRADED due to JVM memory usage, but should not be DOWN
        assertThat(body.get("status")).isIn("UP", "DEGRADED");
        @SuppressWarnings("unchecked")
        Map<String, Object> subsystems = (Map<String, Object>) body.get("subsystems");
        @SuppressWarnings("unchecked")
        Map<String, Object> database = (Map<String, Object>) subsystems.get("database");
        assertThat(database).containsEntry("status", "UP"); 
        assertThat(database).containsEntry("latency_ms", 12); 
        assertThat(database).containsEntry("pool_status", "active"); 
        assertThat(database).containsKey("response_time_ms");
    }

    @Test
    @DisplayName("health deep aliases structured health detail endpoint")
    void healthDeepAliasesHealthDetail() throws Exception { 
        server = new DataCloudHttpServer(mockClient, port) 
            .withHealthSubsystem("database", () -> Map.of("status", "UP", "latency_ms", 9)); 
        server.start(); 

        HttpResponse<String> response = get("/health/deep");

        assertThat(response.statusCode()).isEqualTo(200); 
        @SuppressWarnings("unchecked")
        Map<String, Object> body = mapper.readValue(response.body(), Map.class); 
        assertThat(body.get("status")).isIn("UP", "DEGRADED");
        @SuppressWarnings("unchecked")
        Map<String, Object> subsystems = (Map<String, Object>) body.get("subsystems");
        @SuppressWarnings("unchecked")
        Map<String, Object> database = (Map<String, Object>) subsystems.get("database");
        assertThat(database).containsEntry("status", "UP"); 
        assertThat(database).containsKey("response_time_ms");
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

    @Test
    @DisplayName("ready returns 503 when database probe is not configured")
    void readyReturns503WhenDatabaseProbeIsNotConfigured() throws Exception { 
        server = new DataCloudHttpServer(mockClient, port) 
            .withHealthSubsystem("database", () -> Map.of("status", "NOT_CONFIGURED", "note", "db-unconfigured")); 
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
        assertThat(database).containsEntry("status", "NOT_CONFIGURED"); 
    }

    @Test
    @DisplayName("ready returns 200 when required dependencies are up")
    void readyReturns200WhenRequiredDependenciesAreUp() throws Exception {
        server = new DataCloudHttpServer(mockClient, port)
            .withHealthSubsystem("database", () -> Map.of("status", "UP", "latency_ms", 8))
            .withHealthSubsystem("event_store", () -> Map.of("status", "UP", "latency_ms", 11));
        server.start();

        // Use a fresh HttpClient to avoid connection reuse issues from previous tests
        HttpClient freshHttpClient = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/ready"))
            .GET()
            .build();
        HttpResponse<String> response = freshHttpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = mapper.readValue(response.body(), Map.class);
        assertThat(body).containsEntry("status", "READY");
        @SuppressWarnings("unchecked")
        Map<String, Object> subsystems = (Map<String, Object>) body.get("subsystems");
        @SuppressWarnings("unchecked")
        Map<String, Object> database = (Map<String, Object>) subsystems.get("database");
        @SuppressWarnings("unchecked")
        Map<String, Object> eventStore = (Map<String, Object>) subsystems.get("event_store");
        assertThat(database).containsEntry("status", "UP");
        assertThat(eventStore).containsEntry("status", "UP");
    }

    @Test
    @DisplayName("DC-OPS-001 — health detail exposes trace_export as NOT_CONFIGURED when CLICKHOUSE_HOST is absent")
    void healthDetailExposesTraceExportState_notConfigured() throws Exception {
        startServer(); // no withTraceExportService() call → traceExportService is null

        HttpResponse<String> response = get("/health/detail");

        assertThat(response.statusCode()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = mapper.readValue(response.body(), Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> subsystems = (Map<String, Object>) body.get("subsystems");
        assertThat(subsystems).containsKey("trace_export");
        @SuppressWarnings("unchecked")
        Map<String, Object> traceExport = (Map<String, Object>) subsystems.get("trace_export");
        assertThat(traceExport).containsEntry("status", "NOT_CONFIGURED");
    }

    private void startServer() throws Exception { 
        server = new DataCloudHttpServer(mockClient, port); 
        server.start(); 
        waitForServerReady(port); 
    }

    private HttpResponse<String> get(String path) throws Exception { 
        HttpRequest request = HttpRequest.newBuilder() 
            .uri(URI.create("http://localhost:" + port + path)) 
            .GET() 
            .build(); 
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString()); 
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

    private int findFreePort() throws IOException { 
        try (ServerSocket socket = new ServerSocket(0)) { 
            return socket.getLocalPort(); 
        }
    }
}
