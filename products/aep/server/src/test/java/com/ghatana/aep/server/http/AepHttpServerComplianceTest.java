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
 * Integration tests for AEP HTTP compliance endpoints (GDPR, CCPA, SOC2).
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
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        engine = Aep.forTesting();
        mockDc = mock(DataCloudClient.class);
        port = findFreePort();
        httpClient = HttpClient.newBuilder().build();
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop();
        if (engine != null) engine.close();
    }

    // ==================== POST /api/v1/compliance/gdpr/access ====================

    @Nested
    @DisplayName("POST /api/v1/compliance/gdpr/access")
    class GdprAccessTests {

        @Test
        @DisplayName("returns 503 when compliance service not configured (no DC)")
        void gdprAccess_whenNoDc_returns503() throws Exception {
            server = new AepHttpServer(engine, port);
            server.start();
            waitForServerReady(port);

            String body = mapper.writeValueAsString(Map.of(
                "subjectId", "user-123",
                "tenantId", "tenant-1"
            ));
            HttpResponse<String> resp = post("/api/v1/compliance/gdpr/access", body);

            assertThat(resp.statusCode()).isEqualTo(503);
            Map<?, ?> respBody = mapper.readValue(resp.body(), Map.class);
            assertThat(respBody.get("error").toString()).contains("not available");
        }

        @Test
        @DisplayName("returns 200 with access report when DC configured")
        void gdprAccess_withDc_returns200() throws Exception {
            when(mockDc.query(anyString(), anyString(), any(DataCloudClient.Query.class)))
                .thenReturn(Promise.of(List.of()));

            server = new AepHttpServer(engine, port, null, mockDc);
            server.start();
            waitForServerReady(port);

            String body = mapper.writeValueAsString(Map.of(
                "subjectId", "user-456",
                "tenantId", "tenant-1"
            ));
            HttpResponse<String> resp = post("/api/v1/compliance/gdpr/access", body);

            assertThat(resp.statusCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("returns 400 when subjectId is missing")
        void gdprAccess_withoutSubjectId_returns400() throws Exception {
            server = new AepHttpServer(engine, port, null, mockDc);
            server.start();
            waitForServerReady(port);

            String body = mapper.writeValueAsString(Map.of("tenantId", "t1"));
            HttpResponse<String> resp = post("/api/v1/compliance/gdpr/access", body);

            assertThat(resp.statusCode()).isEqualTo(400);
        }

        @Test
        @DisplayName("returns 400 on malformed JSON body")
        void gdprAccess_withMalformedJson_returns400() throws Exception {
            server = new AepHttpServer(engine, port, null, mockDc);
            server.start();
            waitForServerReady(port);

            HttpResponse<String> resp = post("/api/v1/compliance/gdpr/access", "{bad json");
            assertThat(resp.statusCode()).isEqualTo(400);
        }
    }

    // ==================== POST /api/v1/compliance/gdpr/erasure ====================

    @Nested
    @DisplayName("POST /api/v1/compliance/gdpr/erasure")
    class GdprErasureTests {

        @Test
        @DisplayName("returns 503 when compliance service not configured")
        void gdprErasure_whenNoDc_returns503() throws Exception {
            server = new AepHttpServer(engine, port);
            server.start();
            waitForServerReady(port);

            String body = mapper.writeValueAsString(Map.of(
                "subjectId", "user-789",
                "tenantId", "tenant-1"
            ));
            HttpResponse<String> resp = post("/api/v1/compliance/gdpr/erasure", body);

            assertThat(resp.statusCode()).isEqualTo(503);
        }

        @Test
        @DisplayName("returns 200 with erasure report when DC configured")
        void gdprErasure_withDc_returns200() throws Exception {
            when(mockDc.query(anyString(), anyString(), any(DataCloudClient.Query.class)))
                .thenReturn(Promise.of(List.of()));
            when(mockDc.delete(anyString(), anyString(), anyString()))
                .thenReturn(Promise.of((Void) null));

            server = new AepHttpServer(engine, port, null, mockDc);
            server.start();
            waitForServerReady(port);

            String body = mapper.writeValueAsString(Map.of(
                "subjectId", "user-789",
                "tenantId", "tenant-1"
            ));
            HttpResponse<String> resp = post("/api/v1/compliance/gdpr/erasure", body);

            assertThat(resp.statusCode()).isEqualTo(200);
        }
    }

    // ==================== POST /api/v1/compliance/gdpr/portability ====================

    @Nested
    @DisplayName("POST /api/v1/compliance/gdpr/portability")
    class GdprPortabilityTests {

        @Test
        @DisplayName("returns 503 when compliance service not configured")
        void gdprPortability_whenNoDc_returns503() throws Exception {
            server = new AepHttpServer(engine, port);
            server.start();
            waitForServerReady(port);

            String body = mapper.writeValueAsString(Map.of(
                "subjectId", "user-export",
                "tenantId", "tenant-1"
            ));
            HttpResponse<String> resp = post("/api/v1/compliance/gdpr/portability", body);

            assertThat(resp.statusCode()).isEqualTo(503);
        }

        @Test
        @DisplayName("returns 200 with export data when DC configured")
        void gdprPortability_withDc_returns200() throws Exception {
            when(mockDc.query(anyString(), anyString(), any(DataCloudClient.Query.class)))
                .thenReturn(Promise.of(List.of()));

            server = new AepHttpServer(engine, port, null, mockDc);
            server.start();
            waitForServerReady(port);

            String body = mapper.writeValueAsString(Map.of(
                "subjectId", "user-export",
                "tenantId", "tenant-1"
            ));
            HttpResponse<String> resp = post("/api/v1/compliance/gdpr/portability", body);

            assertThat(resp.statusCode()).isEqualTo(200);
        }
    }

    // ==================== POST /api/v1/compliance/ccpa/opt-out ====================

    @Nested
    @DisplayName("POST /api/v1/compliance/ccpa/opt-out")
    class CcpaOptOutTests {

        @Test
        @DisplayName("returns 503 when compliance service not configured")
        void ccpaOptOut_whenNoDc_returns503() throws Exception {
            server = new AepHttpServer(engine, port);
            server.start();
            waitForServerReady(port);

            String body = mapper.writeValueAsString(Map.of(
                "consumerId", "consumer-1",
                "tenantId", "tenant-1"
            ));
            HttpResponse<String> resp = post("/api/v1/compliance/ccpa/opt-out", body);

            assertThat(resp.statusCode()).isEqualTo(503);
        }

        @Test
        @DisplayName("returns 200 with opt-out confirmation when DC configured")
        void ccpaOptOut_withDc_returns200() throws Exception {
            when(mockDc.query(anyString(), anyString(), any(DataCloudClient.Query.class)))
                .thenReturn(Promise.of(List.of()));
            when(mockDc.save(anyString(), anyString(), any()))
                .thenReturn(Promise.of(DataCloudClient.Entity.of(
                        "consumer-1", "aep_ccpa_opt_out",
                        java.util.Map.of("id", "consumer-1", "_ccpaOptOut", true))));

            server = new AepHttpServer(engine, port, null, mockDc);
            server.start();
            waitForServerReady(port);

            String body = mapper.writeValueAsString(Map.of(
                "consumerId", "consumer-1",
                "tenantId", "tenant-1"
            ));
            HttpResponse<String> resp = post("/api/v1/compliance/ccpa/opt-out", body);

            assertThat(resp.statusCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("returns 400 when consumerId is missing")
        void ccpaOptOut_withoutConsumerId_returns400() throws Exception {
            server = new AepHttpServer(engine, port, null, mockDc);
            server.start();
            waitForServerReady(port);

            String body = mapper.writeValueAsString(Map.of("tenantId", "t1"));
            HttpResponse<String> resp = post("/api/v1/compliance/ccpa/opt-out", body);

            assertThat(resp.statusCode()).isEqualTo(400);
        }
    }

    // ==================== GET /api/v1/compliance/soc2 ====================

    @Nested
    @DisplayName("GET /api/v1/compliance/soc2")
    class Soc2Tests {

        @Test
        @DisplayName("returns 200 with SOC2 report (always available)")
        void soc2Report_returns200() throws Exception {
            server = new AepHttpServer(engine, port);
            server.start();
            waitForServerReady(port);

            HttpResponse<String> resp = get("/api/v1/compliance/soc2/report");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body).isNotEmpty();
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

    private static int findFreePort() throws IOException {
        try (ServerSocket ss = new ServerSocket(0)) {
            return ss.getLocalPort();
        }
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
        throw new AssertionError("Server did not start on port " + port + " within 5 s");
    }
}
