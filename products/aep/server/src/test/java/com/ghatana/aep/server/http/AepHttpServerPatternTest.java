package com.ghatana.aep.server.http;

import com.ghatana.aep.Aep;
import com.ghatana.aep.AepEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for AEP HTTP pattern endpoints.
 *
 * @doc.type class
 * @doc.purpose Integration tests for /api/v1/patterns/** HTTP endpoints
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AepHttpServer – Pattern Endpoints")
class AepHttpServerPatternTest {

    private AepEngine engine;
    private AepHttpServer server;
    private int port;
    private HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper(); // GH-90000

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        engine = Aep.forTesting(); // GH-90000
        port = findFreePort(); // GH-90000
        httpClient = HttpClient.newBuilder().build(); // GH-90000
        server = new AepHttpServer(engine, port); // GH-90000
        server.start(); // GH-90000
        waitForServerReady(port); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (server != null) server.stop(); // GH-90000
        if (engine != null) engine.close(); // GH-90000
    }

    // ==================== GET /api/v1/patterns ====================

    @Nested
    @DisplayName("GET /api/v1/patterns")
    class ListPatternsTests {

        @Test
        @DisplayName("returns 200 with empty list when no patterns registered")
        void listPatterns_whenEmpty_returns200WithEmptyList() throws Exception { // GH-90000
            HttpResponse<String> resp = get("/api/v1/patterns");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("count")).isEqualTo(0);
            assertThat((List<?>) body.get("patterns")).isEmpty();
            assertThat(body.get("timestamp")).isNotNull();
        }

        @Test
        @DisplayName("returns 200 with patterns after registration")
        void listPatterns_afterRegistration_returns200WithPatterns() throws Exception { // GH-90000
            // Register a pattern first
            String reqBody = mapper.writeValueAsString(Map.of( // GH-90000
                "name", "TestPattern",
                "description", "A test anomaly pattern",
                "type", "CUSTOM",
                "tenantId", "test-tenant"
            ));
            post("/api/v1/patterns", reqBody); // GH-90000

            HttpResponse<String> resp = get("/api/v1/patterns?tenantId=test-tenant");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            int count = ((Number) body.get("count")).intValue();
            assertThat(count).isGreaterThanOrEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("uses default tenant when tenantId not specified")
        void listPatterns_withoutTenantId_usesDefault() throws Exception { // GH-90000
            HttpResponse<String> resp = get("/api/v1/patterns");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("timestamp")).isNotNull();
        }
    }

    // ==================== POST /api/v1/patterns ====================

    @Nested
    @DisplayName("POST /api/v1/patterns")
    class RegisterPatternTests {

        @Test
        @DisplayName("returns 200 with pattern details on valid registration")
        void registerPattern_withValidData_returns200() throws Exception { // GH-90000
            String reqBody = mapper.writeValueAsString(Map.of( // GH-90000
                "name", "HighCpuAnomaly",
                "description", "Detects high CPU usage patterns",
                "type", "CUSTOM",
                "tenantId", "t1",
                "config", Map.of("threshold", 90) // GH-90000
            ));

            HttpResponse<String> resp = post("/api/v1/patterns", reqBody); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            Map<?, ?> pattern = (Map<?, ?>) body.get("pattern");
            assertThat(pattern.get("name")).isEqualTo("HighCpuAnomaly");
            assertThat(pattern.get("id")).isNotNull();
            assertThat(body.get("timestamp")).isNotNull();
        }

        @Test
        @DisplayName("returns 400 on malformed JSON body")
        void registerPattern_withMalformedJson_returns400() throws Exception { // GH-90000
            HttpResponse<String> resp = post("/api/v1/patterns", "{not valid json"); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(400); // GH-90000
        }

        @Test
        @DisplayName("returns 400 on invalid pattern type")
        void registerPattern_withInvalidType_returns400() throws Exception { // GH-90000
            String reqBody = mapper.writeValueAsString(Map.of( // GH-90000
                "name", "BadType",
                "type", "NONEXISTENT_TYPE"
            ));

            HttpResponse<String> resp = post("/api/v1/patterns", reqBody); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(400); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("message")).isNotNull();
        }
    }

    // ==================== GET /api/v1/patterns/:patternId ====================

    @Nested
    @DisplayName("GET /api/v1/patterns/:patternId")
    class GetPatternTests {

        @Test
        @DisplayName("returns 200 with pattern detail when found")
        void getPattern_whenFound_returns200() throws Exception { // GH-90000
            // Register a pattern first
            String reqBody = mapper.writeValueAsString(Map.of( // GH-90000
                "name", "FindMe",
                "type", "CUSTOM",
                "tenantId", "t1"
            ));
            HttpResponse<String> createResp = post("/api/v1/patterns", reqBody); // GH-90000
            Map<?, ?> created = mapper.readValue(createResp.body(), Map.class); // GH-90000
            String patternId = (String) ((Map<?, ?>) created.get("pattern")).get("id");

            HttpResponse<String> resp = get("/api/v1/patterns/" + patternId + "?tenantId=t1"); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            Map<?, ?> pattern = (Map<?, ?>) body.get("pattern");
            assertThat(pattern.get("id")).isEqualTo(patternId);
            assertThat(pattern.get("name")).isEqualTo("FindMe");
        }

        @Test
        @DisplayName("returns 404 when pattern not found")
        void getPattern_whenNotFound_returns404() throws Exception { // GH-90000
            HttpResponse<String> resp = get("/api/v1/patterns/nonexistent-id?tenantId=t1");

            assertThat(resp.statusCode()).isEqualTo(404); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("message").toString()).contains("not found");
        }
    }

    // ==================== DELETE /api/v1/patterns/:patternId ====================

    @Nested
    @DisplayName("DELETE /api/v1/patterns/:patternId")
    class DeletePatternTests {

        @Test
        @DisplayName("returns 200 with deleted=true on successful deletion")
        void deletePattern_returnsDeletedTrue() throws Exception { // GH-90000
            // Register a pattern first
            String reqBody = mapper.writeValueAsString(Map.of( // GH-90000
                "name", "DeleteMe",
                "type", "CUSTOM",
                "tenantId", "t1"
            ));
            HttpResponse<String> createResp = post("/api/v1/patterns", reqBody); // GH-90000
            Map<?, ?> created = mapper.readValue(createResp.body(), Map.class); // GH-90000
            String patternId = (String) ((Map<?, ?>) created.get("pattern")).get("id");

            HttpResponse<String> resp = delete("/api/v1/patterns/" + patternId + "?tenantId=t1"); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("deleted")).isEqualTo(true);
            assertThat(body.get("patternId")).isEqualTo(patternId);
        }
    }

    // ==================== Helpers ====================

    private HttpResponse<String> get(String path) throws Exception { // GH-90000
        HttpRequest req = HttpRequest.newBuilder() // GH-90000
            .GET() // GH-90000
            .uri(URI.create("http://127.0.0.1:" + port + path)) // GH-90000
            .build(); // GH-90000
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString()); // GH-90000
    }

    private HttpResponse<String> post(String path, String body) throws Exception { // GH-90000
        HttpRequest req = HttpRequest.newBuilder() // GH-90000
            .POST(HttpRequest.BodyPublishers.ofString(body)) // GH-90000
            .uri(URI.create("http://127.0.0.1:" + port + path)) // GH-90000
            .header("Content-Type", "application/json") // GH-90000
            .build(); // GH-90000
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString()); // GH-90000
    }

    private HttpResponse<String> delete(String path) throws Exception { // GH-90000
        HttpRequest req = HttpRequest.newBuilder() // GH-90000
            .DELETE() // GH-90000
            .uri(URI.create("http://127.0.0.1:" + port + path)) // GH-90000
            .build(); // GH-90000
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString()); // GH-90000
    }

    private static int findFreePort() throws IOException { // GH-90000
        try (ServerSocket ss = new ServerSocket(0)) { // GH-90000
            return ss.getLocalPort(); // GH-90000
        }
    }

    private static void waitForServerReady(int port) throws Exception { // GH-90000
        long deadline = System.currentTimeMillis() + 5_000; // GH-90000
        while (System.currentTimeMillis() < deadline) { // GH-90000
            try {
                new Socket("127.0.0.1", port).close(); // GH-90000
                return;
            } catch (IOException ignored) { // GH-90000
                Thread.sleep(50); // GH-90000
            }
        }
        throw new AssertionError("Server did not start on port " + port + " within 5 s"); // GH-90000
    }
}
