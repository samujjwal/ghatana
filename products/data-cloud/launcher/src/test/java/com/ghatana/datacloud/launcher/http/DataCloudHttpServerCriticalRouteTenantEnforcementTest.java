/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.analytics.AnalyticsQueryEngine;
import com.ghatana.datacloud.spi.EntityStore;
import com.ghatana.platform.audit.AuditService;
import com.ghatana.platform.governance.security.ApiKeyResolver;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.governance.PolicyEngine;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * P0-04: Cross-tenant HTTP integration enforcement across CRITICAL routes.
 *
 * <p>Verifies that the security filter rejects API-key principals from accessing
 * resources scoped to a different tenant across:
 * <ul>
 *   <li>Entity CRUD routes ({@code /api/v1/entities/...})</li>
 *   <li>Event append routes ({@code /api/v1/events})</li>
 *   <li>Analytics routes ({@code /api/v1/analytics/...})</li>
 *   <li>Governance routes ({@code /api/v1/governance/...})</li>
 * </ul>
 *
 * <p>Each test starts a real {@link DataCloudHttpServer} with an
 * {@link ApiKeyResolver} that authenticates TENANT_A_KEY for tenant-A only.
 * Requests carrying {@code X-Tenant-ID: tenant-B} must receive {@code 403}.
 * Same-tenant requests must pass to the underlying handler.
 *
 * @doc.type class
 * @doc.purpose P0-04 cross-tenant enforcement integration tests across CRITICAL routes
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Cross-tenant enforcement – CRITICAL route negative-path coverage (P0-04)")
class DataCloudHttpServerCriticalRouteTenantEnforcementTest {

    private static final String TENANT_A_KEY = "key-tenant-a"; // GH-90000
    private static final String TENANT_A = "tenant-a"; // GH-90000
    private static final String TENANT_B = "tenant-b"; // GH-90000

    private DataCloudClient mockClient; // GH-90000
    private EntityStore mockEntityStore; // GH-90000
    private AuditService mockAuditService; // GH-90000
    private ApiKeyResolver tenantAResolver; // GH-90000
    private PolicyEngine permissivePolicyEngine; // GH-90000
    private DataCloudHttpServer server; // GH-90000
    private int port; // GH-90000
    private HttpClient httpClient; // GH-90000
    private final ObjectMapper mapper = new ObjectMapper(); // GH-90000

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        mockClient = mock(DataCloudClient.class); // GH-90000
        mockEntityStore = mock(EntityStore.class); // GH-90000
        mockAuditService = mock(AuditService.class); // GH-90000
        tenantAResolver = mock(ApiKeyResolver.class); // GH-90000
        permissivePolicyEngine = mock(PolicyEngine.class); // GH-90000

        when(mockClient.entityStore()).thenReturn(mockEntityStore); // GH-90000
        when(mockEntityStore.count(any(), any())).thenReturn(Promise.of(0L)); // GH-90000
        when(mockAuditService.record(any())).thenReturn(Promise.complete()); // GH-90000

        // Tenant-A API key resolves to a principal scoped to tenant-A only
        when(tenantAResolver.resolve(TENANT_A_KEY))
            .thenReturn(Optional.of(new Principal("svc-tenant-a", List.of("admin"), TENANT_A))); // GH-90000
        // Any unknown key returns empty
        when(tenantAResolver.resolve(any()))
            .thenAnswer(inv -> {
                String key = inv.getArgument(0);
                if (TENANT_A_KEY.equals(key)) {
                    return Optional.of(new Principal("svc-tenant-a", List.of("admin"), TENANT_A));
                }
                return Optional.empty();
            }); // GH-90000

        when(permissivePolicyEngine.evaluate(any(), any())).thenReturn(Promise.of(Boolean.TRUE)); // GH-90000

        port = findFreePort(); // GH-90000
        httpClient = HttpClient.newBuilder().build(); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (server != null) server.stop(); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Entity CRUD cross-tenant enforcement
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Entity CRUD routes – cross-tenant enforcement")
    class EntityCrudCrossTenantTests {

        @Test
        @DisplayName("GET /api/v1/entities/:collection with wrong tenant returns 403")
        void getEntity_wrongTenant_returnsForbidden() throws Exception { // GH-90000
            startServer(); // GH-90000

            HttpResponse<String> resp = sendWithTenant(
                HttpRequest.newBuilder()
                    .GET()
                    .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/entities/orders")),
                TENANT_A_KEY, TENANT_B); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(403); // GH-90000
            assertErrorPayload(resp); // GH-90000
        }

        @Test
        @DisplayName("POST /api/v1/entities/:collection with wrong tenant returns 403")
        void postEntity_wrongTenant_returnsForbidden() throws Exception { // GH-90000
            startServer(); // GH-90000

            HttpResponse<String> resp = sendPostWithTenant(
                "/api/v1/entities/orders",
                mapper.writeValueAsString(Map.of("name", "test")),
                TENANT_A_KEY, TENANT_B); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(403); // GH-90000
            assertErrorPayload(resp); // GH-90000
        }

        @Test
        @DisplayName("GET /api/v1/entities/:collection/:id with wrong tenant returns 403")
        void getEntityById_wrongTenant_returnsForbidden() throws Exception { // GH-90000
            startServer(); // GH-90000

            HttpResponse<String> resp = sendWithTenant(
                HttpRequest.newBuilder()
                    .GET()
                    .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/entities/orders/ent-001")),
                TENANT_A_KEY, TENANT_B); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(403); // GH-90000
            assertErrorPayload(resp); // GH-90000
        }

        @Test
        @DisplayName("DELETE /api/v1/entities/:collection/:id with wrong tenant returns 403")
        void deleteEntity_wrongTenant_returnsForbidden() throws Exception { // GH-90000
            startServer(); // GH-90000

            HttpResponse<String> resp = sendDeleteWithTenant(
                "/api/v1/entities/orders/ent-001",
                TENANT_A_KEY, TENANT_B); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(403); // GH-90000
            assertErrorPayload(resp); // GH-90000
        }

        @Test
        @DisplayName("GET /api/v1/entities/:collection with matching tenant succeeds past auth layer")
        void getEntity_correctTenant_passesAuthLayer() throws Exception { // GH-90000
            when(mockEntityStore.query(any(), any()))
                .thenReturn(Promise.of(EntityStore.QueryResult.of(List.of(), 0))); // GH-90000
            startServerWithPolicy(); // GH-90000

            HttpResponse<String> resp = sendWithTenant(
                HttpRequest.newBuilder()
                    .GET()
                    .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/entities/orders")),
                TENANT_A_KEY, TENANT_A); // GH-90000

            // Auth passes — downstream handler may return 200/404 depending on route wiring,
            // but must not fail with auth-layer statuses.
            assertThat(resp.statusCode()).isNotIn(401, 403); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Event routes cross-tenant enforcement
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Event routes – cross-tenant enforcement")
    class EventCrossTenantTests {

        @Test
        @DisplayName("POST /api/v1/events with wrong tenant returns 403")
        void appendEvent_wrongTenant_returnsForbidden() throws Exception { // GH-90000
            startServer(); // GH-90000

            String body = mapper.writeValueAsString(Map.of(
                "type", "ORDER_CREATED",
                "payload", Map.of("orderId", "ord-001")
            )); // GH-90000
            HttpResponse<String> resp = sendPostWithTenant(
                "/api/v1/events", body, TENANT_A_KEY, TENANT_B); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(403); // GH-90000
            assertErrorPayload(resp); // GH-90000
        }

        @Test
        @DisplayName("GET /api/v1/events with wrong tenant returns 403")
        void readEvents_wrongTenant_returnsForbidden() throws Exception { // GH-90000
            startServer(); // GH-90000

            HttpResponse<String> resp = sendWithTenant(
                HttpRequest.newBuilder()
                    .GET()
                    .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/events")),
                TENANT_A_KEY, TENANT_B); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(403); // GH-90000
            assertErrorPayload(resp); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Analytics routes cross-tenant enforcement
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Analytics routes – cross-tenant enforcement")
    class AnalyticsCrossTenantTests {

        @Test
        @DisplayName("POST /api/v1/analytics/query with wrong tenant returns 403")
        void analyticsQuery_wrongTenant_returnsForbidden() throws Exception { // GH-90000
            startServerWithAnalytics(); // GH-90000

            String body = mapper.writeValueAsString(Map.of("query", "SELECT 1")); // GH-90000
            HttpResponse<String> resp = sendPostWithTenant(
                "/api/v1/analytics/query", body, TENANT_A_KEY, TENANT_B); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(403); // GH-90000
            assertErrorPayload(resp); // GH-90000
        }

        @Test
        @DisplayName("POST /api/v1/analytics/explain with wrong tenant returns 403")
        void analyticsExplain_wrongTenant_returnsForbidden() throws Exception { // GH-90000
            startServerWithAnalytics(); // GH-90000

            String body = mapper.writeValueAsString(Map.of("query", "SELECT 1")); // GH-90000
            HttpResponse<String> resp = sendPostWithTenant(
                "/api/v1/analytics/explain", body, TENANT_A_KEY, TENANT_B); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(403); // GH-90000
            assertErrorPayload(resp); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Governance routes cross-tenant enforcement
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Governance routes – cross-tenant enforcement")
    class GovernanceCrossTenantTests {

        @Test
        @DisplayName("GET /api/v1/governance/compliance/summary with wrong tenant returns 403")
        void governanceComplianceSummary_wrongTenant_returnsForbidden() throws Exception { // GH-90000
            startServer(); // GH-90000

            HttpResponse<String> resp = sendWithTenant(
                HttpRequest.newBuilder()
                    .GET()
                    .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/governance/compliance/summary")),
                TENANT_A_KEY, TENANT_B); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(403); // GH-90000
            assertErrorPayload(resp); // GH-90000
        }

        @Test
        @DisplayName("POST /api/v1/governance/retention/classify with wrong tenant returns 403")
        void retentionClassify_wrongTenant_returnsForbidden() throws Exception { // GH-90000
            startServer(); // GH-90000

            String body = mapper.writeValueAsString(Map.of(
                "collection", "orders", "tier", "standard"
            )); // GH-90000
            HttpResponse<String> resp = sendPostWithTenant(
                "/api/v1/governance/retention/classify", body, TENANT_A_KEY, TENANT_B); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(403); // GH-90000
            assertErrorPayload(resp); // GH-90000
        }

        @Test
        @DisplayName("POST /api/v1/governance/privacy/redact with wrong tenant returns 403")
        void privacyRedact_wrongTenant_returnsForbidden() throws Exception { // GH-90000
            startServer(); // GH-90000

            String body = mapper.writeValueAsString(Map.of(
                "collection", "users", "entityId", "ent-001", "fields", List.of("email")
            )); // GH-90000
            HttpResponse<String> resp = sendPostWithTenant(
                "/api/v1/governance/privacy/redact", body, TENANT_A_KEY, TENANT_B); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(403); // GH-90000
            assertErrorPayload(resp); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Unauthenticated request enforcement
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Unauthenticated requests – no API key")
    class UnauthenticatedRequestTests {

        @Test
        @DisplayName("Missing API key on entity route returns 401")
        void entityRoute_noApiKey_returnsUnauthorized() throws Exception { // GH-90000
            startServer(); // GH-90000

            HttpRequest req = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/entities/orders"))
                .header("X-Tenant-ID", TENANT_A)
                .build(); // GH-90000
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString()); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(401); // GH-90000
        }

        @Test
        @DisplayName("Invalid API key on governance route returns 401")
        void governanceRoute_invalidApiKey_returnsUnauthorized() throws Exception { // GH-90000
            startServer(); // GH-90000

            HttpRequest req = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/governance/compliance/summary"))
                .header("X-API-Key", "sk-invalid-key")
                .header("X-Tenant-ID", TENANT_A)
                .build(); // GH-90000
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString()); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(401); // GH-90000
        }

        @Test
        @DisplayName("Health probe /health is accessible without authentication")
        void healthProbe_noAuth_isPublic() throws Exception { // GH-90000
            startServer(); // GH-90000

            HttpRequest req = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://127.0.0.1:" + port + "/health"))
                .build(); // GH-90000
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString()); // GH-90000

            // Health probe is a PUBLIC path — auth is not required
            assertThat(resp.statusCode()).isIn(200, 503); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Stream / real-time transport routes cross-tenant enforcement (P0-04)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Stream and SSE route tenant isolation guardrails.
     *
     * <p>The security filter runs before every handler, including SSE endpoints. A
     * principal bound to tenant-A must receive {@code 403} when the request carries
     * tenant-B in the {@code X-Tenant-ID} header, regardless of whether the underlying
     * capability (OpenSearch, brain) is wired or not.
     *
     * @doc.type class
     * @doc.purpose Cross-tenant denial assertions for SSE / streaming transport routes
     * @doc.layer product
     * @doc.pattern Test
     */
    @Nested
    @DisplayName("Stream / SSE routes – cross-tenant enforcement (P0-04)")
    class StreamTransportCrossTenantTests {

        @Test
        @DisplayName("GET /api/v1/entities/:collection/query/stream with wrong tenant returns 403") // GH-90000
        void sseEntityQueryStream_wrongTenant_returnsForbidden() throws Exception { // GH-90000
            startServer(); // GH-90000

            HttpResponse<String> resp = sendWithTenant(
                HttpRequest.newBuilder()
                    .GET()
                    .uri(URI.create("http://127.0.0.1:" + port
                            + "/api/v1/entities/orders/query/stream?q=status:open")),
                TENANT_A_KEY, TENANT_B); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(403); // GH-90000
            assertErrorPayload(resp); // GH-90000
        }

        @Test
        @DisplayName("GET /api/v1/brain/workspace/stream with wrong tenant returns 403") // GH-90000
        void sseBrainWorkspaceStream_wrongTenant_returnsForbidden() throws Exception { // GH-90000
            startServer(); // GH-90000

            HttpResponse<String> resp = sendWithTenant(
                HttpRequest.newBuilder()
                    .GET()
                    .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/brain/workspace/stream")),
                TENANT_A_KEY, TENANT_B); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(403); // GH-90000
            assertErrorPayload(resp); // GH-90000
        }

        @Test
        @DisplayName("GET /api/v1/entities/:collection/query/stream without auth returns 401") // GH-90000
        void sseEntityQueryStream_noAuth_returnsUnauthorized() throws Exception { // GH-90000
            startServer(); // GH-90000

            HttpRequest req = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://127.0.0.1:" + port
                        + "/api/v1/entities/orders/query/stream?q=*"))
                .header("X-Tenant-ID", TENANT_A)
                .build(); // GH-90000
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString()); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(401); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void startServer() throws Exception { // GH-90000
        server = new DataCloudHttpServer(mockClient, port)
            .withApiKeyResolver(tenantAResolver)
            .withAuditService(mockAuditService); // GH-90000
        server.start(); // GH-90000
        waitForServerReady(port); // GH-90000
    }

    private void startServerWithPolicy() throws Exception { // GH-90000
        server = new DataCloudHttpServer(mockClient, port)
            .withApiKeyResolver(tenantAResolver)
            .withPolicyEngine(permissivePolicyEngine)
            .withAuditService(mockAuditService); // GH-90000
        server.start(); // GH-90000
        waitForServerReady(port); // GH-90000
    }

    private void startServerWithAnalytics() throws Exception { // GH-90000
        AnalyticsQueryEngine mockEngine = mock(AnalyticsQueryEngine.class); // GH-90000
        server = new DataCloudHttpServer(mockClient, port, null, null, mockEngine)
            .withApiKeyResolver(tenantAResolver)
            .withAuditService(mockAuditService); // GH-90000
        server.start(); // GH-90000
        waitForServerReady(port); // GH-90000
    }

    private HttpResponse<String> sendWithTenant(
            HttpRequest.Builder builder,
            String apiKey,
            String tenantId) throws IOException, InterruptedException { // GH-90000
        return httpClient.send(
            builder
                .header("X-API-Key", apiKey)
                .header("X-Tenant-ID", tenantId)
                .build(),
            HttpResponse.BodyHandlers.ofString()); // GH-90000
    }

    private HttpResponse<String> sendPostWithTenant(
            String path,
            String jsonBody,
            String apiKey,
            String tenantId) throws IOException, InterruptedException { // GH-90000
        HttpRequest req = HttpRequest.newBuilder()
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .uri(URI.create("http://127.0.0.1:" + port + path))
            .header("Content-Type", "application/json")
            .header("X-API-Key", apiKey)
            .header("X-Tenant-ID", tenantId)
            .build(); // GH-90000
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString()); // GH-90000
    }

    private HttpResponse<String> sendDeleteWithTenant(
            String path,
            String apiKey,
            String tenantId) throws IOException, InterruptedException { // GH-90000
        HttpRequest req = HttpRequest.newBuilder()
            .DELETE()
            .uri(URI.create("http://127.0.0.1:" + port + path))
            .header("X-API-Key", apiKey)
            .header("X-Tenant-ID", tenantId)
            .build(); // GH-90000
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString()); // GH-90000
    }

    @SuppressWarnings("unchecked")
    private void assertErrorPayload(HttpResponse<String> resp) throws IOException { // GH-90000
        Map<String, Object> body = mapper.readValue(resp.body(), Map.class); // GH-90000
        assertThat(body).containsKey("error"); // GH-90000
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
        throw new IllegalStateException("Server did not start within 5 seconds on port " + port); // GH-90000
    }
}
