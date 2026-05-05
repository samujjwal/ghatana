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

/**
 * DC-P2-002: Profile endpoint tests for {@code /live}, {@code /info}, and {@code /metrics}.
 *
 * <p>These supplement {@link DataCloudHttpServerHealthTest}, which covers
 * {@code /health/detail}, {@code /health/deep}, and {@code /ready}.
 * Together they provide full coverage of the Data Cloud health/readiness profile
 * as defined in the P2 hardening backlog (DC-P2-002).
 *
 * @doc.type class
 * @doc.purpose DC-P2-002 profile endpoint tests — /live, /info, /metrics
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudHttpServer – Profile Endpoints (DC-P2-002)")
class DataCloudHttpServerProfileTest {

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

    // ── /live ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("/live returns 200 with status LIVE when process is reachable")
    void liveReturns200WithStatusLive() throws Exception {
        startServer();

        HttpResponse<String> response = get("/live");

        assertThat(response.statusCode()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = mapper.readValue(response.body(), Map.class);
        assertThat(body).containsEntry("status", "LIVE");
        assertThat(body).containsKey("timestamp");
    }

    @Test
    @DisplayName("/live is independent of dependency state — always UP when process responds")
    void liveIsIndependentOfDependencyState() throws Exception {
        // Even with a failing database probe the liveness check must pass —
        // liveness tracks process health, not dependency health.
        server = new DataCloudHttpServer(mockClient, port)
                .withHealthSubsystem("database", () -> {
                    throw new IllegalStateException("db is down");
                });
        server.start();
        waitForServerReady(port);

        HttpResponse<String> response = get("/live");

        assertThat(response.statusCode())
                .as("/live must return 200 even when dependencies are unavailable")
                .isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = mapper.readValue(response.body(), Map.class);
        assertThat(body).containsEntry("status", "LIVE");
    }

    // ── /info ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("/info returns 200 with service name and version")
    void infoReturns200WithServiceMetadata() throws Exception {
        startServer();

        HttpResponse<String> response = get("/info");

        assertThat(response.statusCode()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = mapper.readValue(response.body(), Map.class);
        assertThat(body)
                .containsKey("service")
                .containsKey("version")
                .containsKey("timestamp");
        // Service name must not be blank or generic
        assertThat(String.valueOf(body.get("service")))
                .isNotBlank()
                .doesNotContain("unknown");
    }

    @Test
    @DisplayName("/info service field identifies Data Cloud correctly")
    void infoServiceFieldIdentifiesDataCloud() throws Exception {
        startServer();

        HttpResponse<String> response = get("/info");

        @SuppressWarnings("unchecked")
        Map<String, Object> body = mapper.readValue(response.body(), Map.class);
        // Must identify the Data Cloud service (case-insensitive partial match)
        String service = String.valueOf(body.get("service")).toLowerCase();
        assertThat(service).contains("data");
    }

    // ── /metrics ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("/metrics returns 200 with JVM metrics when no Prometheus registry is configured")
    void metricsReturns200WithJvmMetrics() throws Exception {
        startServer();

        HttpResponse<String> response = get("/metrics");

        assertThat(response.statusCode()).isEqualTo(200);
        // Without a PrometheusMeterRegistry the handler falls back to JSON with JVM stats
        String contentType = response.headers().firstValue("content-type").orElse("");
        if (contentType.contains("text/plain")) {
            // Prometheus format — just verify response is non-empty
            assertThat(response.body()).isNotBlank();
        } else {
            // JSON fallback — verify key JVM fields are present
            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(response.body(), Map.class);
            assertThat(body)
                    .containsKey("memory_used_mb")
                    .containsKey("memory_free_mb")
                    .containsKey("processors")
                    .containsKey("timestamp");
        }
    }

    @Test
    @DisplayName("/metrics memory_used_mb is a non-negative integer")
    void metricsMemoryUsedMbIsNonNegative() throws Exception {
        startServer();

        HttpResponse<String> response = get("/metrics");

        assertThat(response.statusCode()).isEqualTo(200);
        String contentType = response.headers().firstValue("content-type").orElse("");
        if (!contentType.contains("text/plain")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(response.body(), Map.class);
            Number memUsed = (Number) body.get("memory_used_mb");
            assertThat(memUsed).isNotNull();
            assertThat(memUsed.longValue()).isGreaterThanOrEqualTo(0L);
        }
    }

    // ── /health basic ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("/health basic probe returns 200 with status UP")
    void healthBasicProbeReturns200() throws Exception {
        startServer();

        HttpResponse<String> response = get("/health");

        assertThat(response.statusCode()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = mapper.readValue(response.body(), Map.class);
        assertThat(body).containsEntry("status", "UP");
        assertThat(body).containsEntry("service", "datacloud");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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
