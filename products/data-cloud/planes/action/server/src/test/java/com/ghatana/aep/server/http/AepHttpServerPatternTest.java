package com.ghatana.aep.server.http;

import com.ghatana.aep.Aep;
import com.ghatana.aep.AepEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
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
@Tag("local-network")
@DisplayName("AepHttpServer – Pattern Endpoints")
class AepHttpServerPatternTest {

    private AepEngine engine;
    private AepHttpServer server;
    private int port;
    private HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper(); 

    @BeforeEach
    void setUp() throws Exception { 
        engine = Aep.forTesting(); 
        httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build(); 
        startServerWithRetries();
    }

    @AfterEach
    void tearDown() { 
        if (server != null) server.stop(); 
        if (engine != null) engine.close(); 
    }

    // ==================== GET /api/v1/patterns ====================

    @Nested
    @DisplayName("GET /api/v1/patterns")
    class ListPatternsTests {

        @Test
        @DisplayName("returns 200 with empty list when no patterns registered")
        void listPatterns_whenEmpty_returns200WithEmptyList() throws Exception { 
            HttpResponse<String> resp = get("/api/v1/patterns");

            assertThat(resp.statusCode()).isEqualTo(200); 
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); 
            assertThat(body.get("count")).isEqualTo(0);
            assertThat((List<?>) body.get("patterns")).isEmpty();
            assertThat(body.get("timestamp")).isNotNull();
        }

        @Test
        @DisplayName("returns 200 with patterns after registration")
        void listPatterns_afterRegistration_returns200WithPatterns() throws Exception { 
            // Register a pattern first
            String reqBody = mapper.writeValueAsString(Map.of( 
                "name", "TestPattern",
                "description", "A test anomaly pattern",
                "type", "CUSTOM",
                "tenantId", "test-tenant"
            ));
            post("/api/v1/patterns", reqBody); 

            HttpResponse<String> resp = get("/api/v1/patterns?tenantId=test-tenant");

            assertThat(resp.statusCode()).isEqualTo(200); 
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); 
            int count = ((Number) body.get("count")).intValue();
            assertThat(count).isGreaterThanOrEqualTo(1); 
        }

        @Test
        @DisplayName("uses default tenant when tenantId not specified")
        void listPatterns_withoutTenantId_usesDefault() throws Exception { 
            HttpResponse<String> resp = get("/api/v1/patterns");

            assertThat(resp.statusCode()).isEqualTo(200); 
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); 
            assertThat(body.get("timestamp")).isNotNull();
        }
    }

    // ==================== POST /api/v1/patterns ====================

    @Nested
    @DisplayName("POST /api/v1/patterns")
    class RegisterPatternTests {

        @Test
        @DisplayName("returns 200 with pattern details on valid registration")
        void registerPattern_withValidData_returns200() throws Exception { 
            String reqBody = mapper.writeValueAsString(Map.of( 
                "name", "HighCpuAnomaly",
                "description", "Detects high CPU usage patterns",
                "type", "CUSTOM",
                "tenantId", "t1",
                "config", Map.of("threshold", 90) 
            ));

            HttpResponse<String> resp = post("/api/v1/patterns", reqBody); 

            assertThat(resp.statusCode()).isEqualTo(200); 
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); 
            Map<?, ?> pattern = (Map<?, ?>) body.get("pattern");
            assertThat(pattern.get("name")).isEqualTo("HighCpuAnomaly");
            assertThat(pattern.get("id")).isNotNull();
            assertThat(body.get("timestamp")).isNotNull();
        }

        @Test
        @DisplayName("returns 400 on malformed JSON body")
        void registerPattern_withMalformedJson_returns400() throws Exception { 
            HttpResponse<String> resp = post("/api/v1/patterns", "{not valid json"); 

            assertThat(resp.statusCode()).isEqualTo(400); 
        }

        @Test
        @DisplayName("returns 400 on invalid pattern type")
        void registerPattern_withInvalidType_returns400() throws Exception { 
            String reqBody = mapper.writeValueAsString(Map.of( 
                "name", "BadType",
                "type", "NONEXISTENT_TYPE"
            ));

            HttpResponse<String> resp = post("/api/v1/patterns", reqBody); 

            assertThat(resp.statusCode()).isEqualTo(400); 
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); 
            assertThat(body.get("message")).isNotNull();
        }
    }

    // ==================== GET /api/v1/patterns/:patternId ====================

    @Nested
    @DisplayName("GET /api/v1/patterns/:patternId")
    class GetPatternTests {

        @Test
        @DisplayName("returns 200 with pattern detail when found")
        void getPattern_whenFound_returns200() throws Exception { 
            // Register a pattern first
            String reqBody = mapper.writeValueAsString(Map.of( 
                "name", "FindMe",
                "type", "CUSTOM",
                "tenantId", "t1"
            ));
            HttpResponse<String> createResp = post("/api/v1/patterns", reqBody); 
            Map<?, ?> created = mapper.readValue(createResp.body(), Map.class); 
            String patternId = (String) ((Map<?, ?>) created.get("pattern")).get("id");

            HttpResponse<String> resp = get("/api/v1/patterns/" + patternId + "?tenantId=t1"); 

            assertThat(resp.statusCode()).isEqualTo(200); 
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); 
            Map<?, ?> pattern = (Map<?, ?>) body.get("pattern");
            assertThat(pattern.get("id")).isEqualTo(patternId);
            assertThat(pattern.get("name")).isEqualTo("FindMe");
        }

        @Test
        @DisplayName("returns 404 when pattern not found")
        void getPattern_whenNotFound_returns404() throws Exception { 
            HttpResponse<String> resp = get("/api/v1/patterns/nonexistent-id?tenantId=t1");

            assertThat(resp.statusCode()).isEqualTo(404); 
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); 
            assertThat(body.get("message").toString()).contains("not found");
        }
    }

    // ==================== DELETE /api/v1/patterns/:patternId ====================

    @Nested
    @DisplayName("DELETE /api/v1/patterns/:patternId")
    class DeletePatternTests {

        @Test
        @DisplayName("returns 200 with deleted=true on successful deletion")
        void deletePattern_returnsDeletedTrue() throws Exception { 
            // Register a pattern first
            String reqBody = mapper.writeValueAsString(Map.of( 
                "name", "DeleteMe",
                "type", "CUSTOM",
                "tenantId", "t1"
            ));
            HttpResponse<String> createResp = post("/api/v1/patterns", reqBody); 
            Map<?, ?> created = mapper.readValue(createResp.body(), Map.class); 
            String patternId = (String) ((Map<?, ?>) created.get("pattern")).get("id");

            HttpResponse<String> resp = delete("/api/v1/patterns/" + patternId + "?tenantId=t1"); 

            assertThat(resp.statusCode()).isEqualTo(200); 
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); 
            assertThat(body.get("deleted")).isEqualTo(true);
            assertThat(body.get("patternId")).isEqualTo(patternId);
        }
    }

    // ==================== Helpers ====================

    private HttpResponse<String> get(String path) throws Exception { 
        HttpRequest req = HttpRequest.newBuilder() 
            .GET() 
            .uri(URI.create("http://127.0.0.1:" + port + path)) 
            .build(); 
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString()); 
    }

    private HttpResponse<String> post(String path, String body) throws Exception { 
        HttpRequest req = HttpRequest.newBuilder() 
            .POST(HttpRequest.BodyPublishers.ofString(body)) 
            .uri(URI.create("http://127.0.0.1:" + port + path)) 
            .header("Content-Type", "application/json") 
            .build(); 
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString()); 
    }

    private HttpResponse<String> delete(String path) throws Exception { 
        HttpRequest req = HttpRequest.newBuilder() 
            .DELETE() 
            .uri(URI.create("http://127.0.0.1:" + port + path)) 
            .build(); 
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString()); 
    }

    private void startServerWithRetries() throws Exception {
        Exception lastFailure = null;
        for (int attempt = 1; attempt <= 5; attempt++) {
            int candidatePort = findFreePort();
            AepHttpServer candidate = new AepHttpServer(engine, candidatePort);
            try {
                candidate.start();
                waitForServerReady(candidatePort, httpClient);
                server = candidate;
                port = candidatePort;
                return;
            } catch (Exception e) {
                lastFailure = e;
                try {
                    candidate.stop();
                } catch (Exception ignored) {
                    // best-effort cleanup before retrying on a new port
                }
            }
        }
        throw new AssertionError("Server did not start after 5 attempts", lastFailure);
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket ss = new ServerSocket(0)) {
            return ss.getLocalPort();
        }
    }

    private static void waitForServerReady(int port, HttpClient httpClient) throws Exception {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) { 
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .GET()
                    .uri(URI.create("http://127.0.0.1:" + port + "/health"))
                    .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return;
                }
            } catch (IOException ignored) {
                // Server may still be binding.
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Server did not become ready on port " + port + " within 5 s");
    }
}
