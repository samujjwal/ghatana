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
 * Integration tests for the agent catalog HTTP endpoints (B3). 
 *
 * <p>Starts a real {@link DataCloudHttpServer} on a random port and issues HTTP requests via the
 * Java standard HttpClient. {@link DataCloudClient} and {@link MetricsCollector} are mocked.
 *
 * @doc.type class
 * @doc.purpose Integration tests for /api/v1/agents/catalog HTTP endpoints (B3) 
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudHttpServer – Agent Catalog Endpoints (B3)")
class DataCloudHttpServerAgentCatalogTest {

    private DataCloudClient mockClient;
    private MetricsCollector mockMetrics;
    private DataCloudHttpServer server;
    private int port;
    private HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper(); 

    @BeforeEach
    void setUp() throws Exception { 
        mockClient  = mock(DataCloudClient.class); 
        mockMetrics = mock(MetricsCollector.class); 
        port        = findFreePort(); 
        httpClient  = HttpClient.newBuilder().build(); 
        lenient().doNothing().when(mockMetrics).incrementCounter(anyString(), anyString(), anyString()); 
    }

    @AfterEach
    void tearDown() { 
        if (server != null) server.stop(); 
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private void startServer() throws Exception { 
        server = new DataCloudHttpServer(mockClient, port) 
                .withMetricsCollector(mockMetrics); 
        server.start(); 
        waitForServerReady(port); 
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException { 
        HttpRequest req = HttpRequest.newBuilder() 
                .uri(URI.create("http://localhost:" + port + path)) 
                .header("X-Tenant-Id", "test-tenant") 
                .GET() 
                .build(); 
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString()); 
    }

    private static int findFreePort() throws IOException { 
        try (ServerSocket s = new ServerSocket(0)) { 
            return s.getLocalPort(); 
        }
    }

    private static void waitForServerReady(int port) throws Exception { 
        for (int i = 0; i < 50; i++) { 
            try (Socket s = new Socket("localhost", port)) { 
                return;
            } catch (IOException e) { 
                Thread.sleep(100); 
            }
        }
        throw new AssertionError("Server did not start on port " + port); 
    }

    // ─── tests ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/agents/catalog")
    class ListCatalogTests {

        @Test
        @DisplayName("returns 200 with agent array")
        void listCatalog_returns200WithAgentArray() throws Exception {
            startServer();

            // Use a fresh HttpClient to avoid connection reuse issues from previous tests
            HttpClient freshHttpClient = HttpClient.newBuilder().build();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/api/v1/agents/catalog"))
                    .header("X-Tenant-Id", "test-tenant")
                    .GET()
                    .build();
            HttpResponse<String> response = freshHttpClient.send(req, HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(200);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(response.body(), Map.class);
            assertThat(body).containsKey("agents");
            assertThat(body).containsKey("total");
            assertThat(((java.util.List<?>) body.get("agents")).size())
                    .isEqualTo(((Number) body.get("total")).intValue());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/agents/catalog/:id")
    class GetCatalogEntryTests {

        @Test
        @DisplayName("returns 404 for unknown agent id")
        void getCatalogEntry_returns404ForUnknown() throws Exception { 
            startServer(); 

            HttpResponse<String> response = get("/api/v1/agents/catalog/definitely-not-exist-xyz");

            assertThat(response.statusCode()).isEqualTo(404); 
            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(response.body(), Map.class); 
            assertThat(body).containsKey("error");
        }
    }
}
