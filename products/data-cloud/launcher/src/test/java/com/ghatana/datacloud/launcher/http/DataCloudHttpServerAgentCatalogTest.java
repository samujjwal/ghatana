package com.ghatana.datacloud.launcher.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.platform.observability.MetricsCollector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;

/**
 * Integration tests for the agent catalog HTTP endpoints (B3). // GH-90000
 *
 * <p>Starts a real {@link DataCloudHttpServer} on a random port and issues HTTP requests via the
 * Java standard HttpClient. {@link DataCloudClient} and {@link MetricsCollector} are mocked.
 *
 * @doc.type class
 * @doc.purpose Integration tests for /api/v1/agents/catalog HTTP endpoints (B3) // GH-90000
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudHttpServer – Agent Catalog Endpoints (B3) [GH-90000]")
class DataCloudHttpServerAgentCatalogTest {

    private DataCloudClient mockClient;
    private MetricsCollector mockMetrics;
    private DataCloudHttpServer server;
    private int port;
    private HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper(); // GH-90000

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        mockClient  = mock(DataCloudClient.class); // GH-90000
        mockMetrics = mock(MetricsCollector.class); // GH-90000
        port        = findFreePort(); // GH-90000
        httpClient  = HttpClient.newBuilder().build(); // GH-90000
        lenient().doNothing().when(mockMetrics).incrementCounter(anyString(), anyString(), anyString()); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (server != null) server.stop(); // GH-90000
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private void startServer() throws Exception { // GH-90000
        server = new DataCloudHttpServer(mockClient, port) // GH-90000
                .withMetricsCollector(mockMetrics); // GH-90000
        server.start(); // GH-90000
        waitForServerReady(port); // GH-90000
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException { // GH-90000
        HttpRequest req = HttpRequest.newBuilder() // GH-90000
                .uri(URI.create("http://localhost:" + port + path)) // GH-90000
                .header("X-Tenant-Id", "test-tenant") // GH-90000
                .GET() // GH-90000
                .build(); // GH-90000
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString()); // GH-90000
    }

    private static int findFreePort() throws IOException { // GH-90000
        try (ServerSocket s = new ServerSocket(0)) { // GH-90000
            return s.getLocalPort(); // GH-90000
        }
    }

    private static void waitForServerReady(int port) throws Exception { // GH-90000
        for (int i = 0; i < 50; i++) { // GH-90000
            try (Socket s = new Socket("localhost", port)) { // GH-90000
                return;
            } catch (IOException e) { // GH-90000
                Thread.sleep(100); // GH-90000
            }
        }
        throw new AssertionError("Server did not start on port " + port); // GH-90000
    }

    // ─── tests ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/agents/catalog [GH-90000]")
    class ListCatalogTests {

        @Test
        @DisplayName("returns 200 with agent array [GH-90000]")
        void listCatalog_returns200WithAgentArray() throws Exception { // GH-90000
            startServer(); // GH-90000

            HttpResponse<String> response = get("/api/v1/agents/catalog [GH-90000]");

            assertThat(response.statusCode()).isEqualTo(200); // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> body = mapper.readValue(response.body(), Map.class); // GH-90000
            assertThat(body).containsKey("agents [GH-90000]");
            assertThat(body).containsKey("total [GH-90000]");
            assertThat(((java.util.List<?>) body.get("agents [GH-90000]")).size())
                    .isEqualTo(((Number) body.get("total [GH-90000]")).intValue());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/agents/catalog/:id [GH-90000]")
    class GetCatalogEntryTests {

        @Test
        @DisplayName("returns 404 for unknown agent id [GH-90000]")
        void getCatalogEntry_returns404ForUnknown() throws Exception { // GH-90000
            startServer(); // GH-90000

            HttpResponse<String> response = get("/api/v1/agents/catalog/definitely-not-exist-xyz [GH-90000]");

            assertThat(response.statusCode()).isEqualTo(404); // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> body = mapper.readValue(response.body(), Map.class); // GH-90000
            assertThat(body).containsKey("error [GH-90000]");
        }
    }
}
