package com.ghatana.aep.server.http;

import com.ghatana.aep.Aep;
import com.ghatana.aep.AepEngine;
import com.ghatana.datacloud.DataCloudClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.promise.Promise;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for AEP HTTP compliance endpoints (GDPR, CCPA, SOC2). // GH-90000
 *
 * @doc.type class
 * @doc.purpose Integration tests for /api/v1/compliance/** HTTP endpoints
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AepHttpServer – Compliance Endpoints")
class AepHttpServerComplianceTest {

    private AepEngine engine;
    private DataCloudClient mockDc;
    private AepHttpServer server;
    private int port;
    private HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper(); // GH-90000

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        engine = Aep.forTesting(); // GH-90000
        mockDc = mock(DataCloudClient.class); // GH-90000
        port = findFreePort(); // GH-90000
        httpClient = HttpClient.newBuilder().build(); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (server != null) server.stop(); // GH-90000
        if (engine != null) engine.close(); // GH-90000
    }

    // ==================== POST /api/v1/compliance/gdpr/access ====================

    @Nested
    @DisplayName("POST /api/v1/compliance/gdpr/access")
    class GdprAccessTests {

        @Test
        @DisplayName("returns 503 when compliance service not configured (no DC)")
        void gdprAccess_whenNoDc_returns503() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            String body = mapper.writeValueAsString(Map.of( // GH-90000
                "subjectId", "user-123",
                "tenantId", "tenant-1"
            ));
            HttpResponse<String> resp = post("/api/v1/compliance/gdpr/access", body); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(503); // GH-90000
            Map<?, ?> respBody = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(respBody.get("message").toString()).contains("not available");
        }

        @Test
        @DisplayName("returns 200 with access report when DC configured")
        void gdprAccess_withDc_returns200() throws Exception { // GH-90000
            when(mockDc.query(anyString(), anyString(), any(DataCloudClient.Query.class))) // GH-90000
                .thenReturn(Promise.of(List.of())); // GH-90000

            server = new AepHttpServer(engine, port, null, mockDc); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            String body = mapper.writeValueAsString(Map.of( // GH-90000
                "subjectId", "user-456",
                "tenantId", "tenant-1"
            ));
            HttpResponse<String> resp = post("/api/v1/compliance/gdpr/access", body); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
        }

        @Test
        @DisplayName("returns 400 when subjectId is missing")
        void gdprAccess_withoutSubjectId_returns400() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port, null, mockDc); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            String body = mapper.writeValueAsString(Map.of("tenantId", "t1")); // GH-90000
            HttpResponse<String> resp = post("/api/v1/compliance/gdpr/access", body); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(400); // GH-90000
        }

        @Test
        @DisplayName("returns 400 on malformed JSON body")
        void gdprAccess_withMalformedJson_returns400() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port, null, mockDc); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = post("/api/v1/compliance/gdpr/access", "{bad json"); // GH-90000
            assertThat(resp.statusCode()).isEqualTo(400); // GH-90000
        }
    }

    // ==================== POST /api/v1/compliance/gdpr/erasure ====================

    @Nested
    @DisplayName("POST /api/v1/compliance/gdpr/erasure")
    class GdprErasureTests {

        @Test
        @DisplayName("returns 503 when compliance service not configured")
        void gdprErasure_whenNoDc_returns503() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            String body = mapper.writeValueAsString(Map.of( // GH-90000
                "subjectId", "user-789",
                "tenantId", "tenant-1"
            ));
            HttpResponse<String> resp = post("/api/v1/compliance/gdpr/erasure", body); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(503); // GH-90000
        }

        @Test
        @DisplayName("returns 200 with erasure report when DC configured")
        void gdprErasure_withDc_returns200() throws Exception { // GH-90000
            when(mockDc.query(anyString(), anyString(), any(DataCloudClient.Query.class))) // GH-90000
                .thenReturn(Promise.of(List.of())); // GH-90000
            when(mockDc.delete(anyString(), anyString(), anyString())) // GH-90000
                .thenReturn(Promise.of((Void) null)); // GH-90000

            server = new AepHttpServer(engine, port, null, mockDc); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            String body = mapper.writeValueAsString(Map.of( // GH-90000
                "subjectId", "user-789",
                "tenantId", "tenant-1"
            ));
            HttpResponse<String> resp = post("/api/v1/compliance/gdpr/erasure", body); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
        }
    }

    // ==================== POST /api/v1/compliance/gdpr/portability ====================

    @Nested
    @DisplayName("POST /api/v1/compliance/gdpr/portability")
    class GdprPortabilityTests {

        @Test
        @DisplayName("returns 503 when compliance service not configured")
        void gdprPortability_whenNoDc_returns503() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            String body = mapper.writeValueAsString(Map.of( // GH-90000
                "subjectId", "user-export",
                "tenantId", "tenant-1"
            ));
            HttpResponse<String> resp = post("/api/v1/compliance/gdpr/portability", body); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(503); // GH-90000
        }

        @Test
        @DisplayName("returns 200 with export data when DC configured")
        void gdprPortability_withDc_returns200() throws Exception { // GH-90000
            when(mockDc.query(anyString(), anyString(), any(DataCloudClient.Query.class))) // GH-90000
                .thenReturn(Promise.of(List.of())); // GH-90000

            server = new AepHttpServer(engine, port, null, mockDc); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            String body = mapper.writeValueAsString(Map.of( // GH-90000
                "subjectId", "user-export",
                "tenantId", "tenant-1"
            ));
            HttpResponse<String> resp = post("/api/v1/compliance/gdpr/portability", body); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
        }
    }

    // ==================== POST /api/v1/compliance/ccpa/opt-out ====================

    @Nested
    @DisplayName("POST /api/v1/compliance/ccpa/opt-out")
    class CcpaOptOutTests {

        @Test
        @DisplayName("returns 503 when compliance service not configured")
        void ccpaOptOut_whenNoDc_returns503() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            String body = mapper.writeValueAsString(Map.of( // GH-90000
                "consumerId", "consumer-1",
                "tenantId", "tenant-1"
            ));
            HttpResponse<String> resp = post("/api/v1/compliance/ccpa/opt-out", body); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(503); // GH-90000
        }

        @Test
        @DisplayName("returns 200 with opt-out confirmation when DC configured")
        void ccpaOptOut_withDc_returns200() throws Exception { // GH-90000
            when(mockDc.query(anyString(), anyString(), any(DataCloudClient.Query.class))) // GH-90000
                .thenReturn(Promise.of(List.of())); // GH-90000
            when(mockDc.save(anyString(), anyString(), any())) // GH-90000
                .thenReturn(Promise.of(DataCloudClient.Entity.of( // GH-90000
                        "consumer-1", "aep_ccpa_opt_out",
                        java.util.Map.of("id", "consumer-1", "_ccpaOptOut", true)))); // GH-90000

            server = new AepHttpServer(engine, port, null, mockDc); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            String body = mapper.writeValueAsString(Map.of( // GH-90000
                "consumerId", "consumer-1",
                "tenantId", "tenant-1"
            ));
            HttpResponse<String> resp = post("/api/v1/compliance/ccpa/opt-out", body); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
        }

        @Test
        @DisplayName("returns 400 when consumerId is missing")
        void ccpaOptOut_withoutConsumerId_returns400() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port, null, mockDc); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            String body = mapper.writeValueAsString(Map.of("tenantId", "t1")); // GH-90000
            HttpResponse<String> resp = post("/api/v1/compliance/ccpa/opt-out", body); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(400); // GH-90000
        }
    }

    // ==================== GET /api/v1/compliance/soc2 ====================

    @Nested
    @DisplayName("GET /api/v1/compliance/soc2")
    class Soc2Tests {

        @Test
        @DisplayName("returns 200 with SOC2 report (always available)")
        void soc2Report_returns200() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = get("/api/v1/compliance/soc2/report");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body).isNotEmpty(); // GH-90000
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
