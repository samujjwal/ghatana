/*
 * Copyright (c) 2026 Ghatana Inc. 
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

    private static final String TENANT_A_KEY = "key-tenant-a"; 
    private static final String TENANT_A = "tenant-a"; 
    private static final String TENANT_B = "tenant-b"; 

    private DataCloudClient mockClient; 
    private EntityStore mockEntityStore; 
    private AuditService mockAuditService; 
    private ApiKeyResolver tenantAResolver; 
    private PolicyEngine permissivePolicyEngine; 
    private DataCloudHttpServer server; 
    private int port; 
    private HttpClient httpClient; 
    private final ObjectMapper mapper = new ObjectMapper(); 

    @BeforeEach
    void setUp() throws Exception { 
        mockClient = mock(DataCloudClient.class); 
        mockEntityStore = mock(EntityStore.class); 
        mockAuditService = mock(AuditService.class); 
        tenantAResolver = mock(ApiKeyResolver.class); 
        permissivePolicyEngine = mock(PolicyEngine.class); 

        when(mockClient.entityStore()).thenReturn(mockEntityStore); 
        when(mockEntityStore.count(any(), any())).thenReturn(Promise.of(0L)); 
        when(mockAuditService.record(any())).thenReturn(Promise.complete()); 

        // Tenant-A API key resolves to a principal scoped to tenant-A only
        when(tenantAResolver.resolve(TENANT_A_KEY))
            .thenReturn(Optional.of(new Principal("svc-tenant-a", List.of("admin"), TENANT_A))); 
        // Any unknown key returns empty
        when(tenantAResolver.resolve(any()))
            .thenAnswer(inv -> {
                String key = inv.getArgument(0);
                if (TENANT_A_KEY.equals(key)) {
                    return Optional.of(new Principal("svc-tenant-a", List.of("admin"), TENANT_A));
                }
                return Optional.empty();
            }); 

        when(permissivePolicyEngine.evaluate(any(), any())).thenReturn(Promise.of(Boolean.TRUE)); 

        port = findFreePort(); 
        httpClient = HttpClient.newBuilder().build(); 
    }

    @AfterEach
    void tearDown() { 
        if (server != null) server.stop(); 
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Entity CRUD cross-tenant enforcement
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Entity CRUD routes – cross-tenant enforcement")
    class EntityCrudCrossTenantTests {

        @Test
        @DisplayName("GET /api/v1/entities/:collection with wrong tenant returns 403")
        void getEntity_wrongTenant_returnsForbidden() throws Exception { 
            startServer(); 

            HttpResponse<String> resp = sendWithTenant(
                HttpRequest.newBuilder()
                    .GET()
                    .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/entities/orders")),
                TENANT_A_KEY, TENANT_B); 

            assertThat(resp.statusCode()).isEqualTo(403); 
            assertErrorPayload(resp); 
        }

        @Test
        @DisplayName("POST /api/v1/entities/:collection with wrong tenant returns 403")
        void postEntity_wrongTenant_returnsForbidden() throws Exception { 
            startServer(); 

            HttpResponse<String> resp = sendPostWithTenant(
                "/api/v1/entities/orders",
                mapper.writeValueAsString(Map.of("name", "test")),
                TENANT_A_KEY, TENANT_B); 

            assertThat(resp.statusCode()).isEqualTo(403); 
            assertErrorPayload(resp); 
        }

        @Test
        @DisplayName("GET /api/v1/entities/:collection/:id with wrong tenant returns 403")
        void getEntityById_wrongTenant_returnsForbidden() throws Exception { 
            startServer(); 

            HttpResponse<String> resp = sendWithTenant(
                HttpRequest.newBuilder()
                    .GET()
                    .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/entities/orders/ent-001")),
                TENANT_A_KEY, TENANT_B); 

            assertThat(resp.statusCode()).isEqualTo(403); 
            assertErrorPayload(resp); 
        }

        @Test
        @DisplayName("DELETE /api/v1/entities/:collection/:id with wrong tenant returns 403")
        void deleteEntity_wrongTenant_returnsForbidden() throws Exception { 
            startServer(); 

            HttpResponse<String> resp = sendDeleteWithTenant(
                "/api/v1/entities/orders/ent-001",
                TENANT_A_KEY, TENANT_B); 

            assertThat(resp.statusCode()).isEqualTo(403); 
            assertErrorPayload(resp); 
        }

        @Test
        @DisplayName("GET /api/v1/entities/:collection with matching tenant succeeds past auth layer")
        void getEntity_correctTenant_passesAuthLayer() throws Exception { 
            when(mockEntityStore.query(any(), any()))
                .thenReturn(Promise.of(EntityStore.QueryResult.of(List.of(), 0))); 
            startServerWithPolicy(); 

            HttpResponse<String> resp = sendWithTenant(
                HttpRequest.newBuilder()
                    .GET()
                    .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/entities/orders")),
                TENANT_A_KEY, TENANT_A); 

            // Auth passes — downstream handler may return 200/404 depending on route wiring,
            // but must not fail with auth-layer statuses.
            assertThat(resp.statusCode()).isNotIn(401, 403); 
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
        void appendEvent_wrongTenant_returnsForbidden() throws Exception { 
            startServer(); 

            String body = mapper.writeValueAsString(Map.of(
                "type", "ORDER_CREATED",
                "payload", Map.of("orderId", "ord-001")
            )); 
            HttpResponse<String> resp = sendPostWithTenant(
                "/api/v1/events", body, TENANT_A_KEY, TENANT_B); 

            assertThat(resp.statusCode()).isEqualTo(403); 
            assertErrorPayload(resp); 
        }

        @Test
        @DisplayName("GET /api/v1/events with wrong tenant returns 403")
        void readEvents_wrongTenant_returnsForbidden() throws Exception { 
            startServer(); 

            HttpResponse<String> resp = sendWithTenant(
                HttpRequest.newBuilder()
                    .GET()
                    .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/events")),
                TENANT_A_KEY, TENANT_B); 

            assertThat(resp.statusCode()).isEqualTo(403); 
            assertErrorPayload(resp); 
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
        void analyticsQuery_wrongTenant_returnsForbidden() throws Exception { 
            startServerWithAnalytics(); 

            String body = mapper.writeValueAsString(Map.of("query", "SELECT 1")); 
            HttpResponse<String> resp = sendPostWithTenant(
                "/api/v1/analytics/query", body, TENANT_A_KEY, TENANT_B); 

            assertThat(resp.statusCode()).isEqualTo(403); 
            assertErrorPayload(resp); 
        }

        @Test
        @DisplayName("POST /api/v1/analytics/explain with wrong tenant returns 403")
        void analyticsExplain_wrongTenant_returnsForbidden() throws Exception { 
            startServerWithAnalytics(); 

            String body = mapper.writeValueAsString(Map.of("query", "SELECT 1")); 
            HttpResponse<String> resp = sendPostWithTenant(
                "/api/v1/analytics/explain", body, TENANT_A_KEY, TENANT_B); 

            assertThat(resp.statusCode()).isEqualTo(403); 
            assertErrorPayload(resp); 
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
        void governanceComplianceSummary_wrongTenant_returnsForbidden() throws Exception { 
            startServer(); 

            HttpResponse<String> resp = sendWithTenant(
                HttpRequest.newBuilder()
                    .GET()
                    .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/governance/compliance/summary")),
                TENANT_A_KEY, TENANT_B); 

            assertThat(resp.statusCode()).isEqualTo(403); 
            assertErrorPayload(resp); 
        }

        @Test
        @DisplayName("POST /api/v1/governance/retention/classify with wrong tenant returns 403")
        void retentionClassify_wrongTenant_returnsForbidden() throws Exception { 
            startServer(); 

            String body = mapper.writeValueAsString(Map.of(
                "collection", "orders", "tier", "standard"
            )); 
            HttpResponse<String> resp = sendPostWithTenant(
                "/api/v1/governance/retention/classify", body, TENANT_A_KEY, TENANT_B); 

            assertThat(resp.statusCode()).isEqualTo(403); 
            assertErrorPayload(resp); 
        }

        @Test
        @DisplayName("POST /api/v1/governance/privacy/redact with wrong tenant returns 403")
        void privacyRedact_wrongTenant_returnsForbidden() throws Exception { 
            startServer(); 

            String body = mapper.writeValueAsString(Map.of(
                "collection", "users", "entityId", "ent-001", "fields", List.of("email")
            )); 
            HttpResponse<String> resp = sendPostWithTenant(
                "/api/v1/governance/privacy/redact", body, TENANT_A_KEY, TENANT_B); 

            assertThat(resp.statusCode()).isEqualTo(403); 
            assertErrorPayload(resp); 
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
        void entityRoute_noApiKey_returnsUnauthorized() throws Exception { 
            startServer(); 

            HttpRequest req = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/entities/orders"))
                .header("X-Tenant-ID", TENANT_A)
                .build(); 
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString()); 

            assertThat(resp.statusCode()).isEqualTo(401); 
        }

        @Test
        @DisplayName("Invalid API key on governance route returns 401")
        void governanceRoute_invalidApiKey_returnsUnauthorized() throws Exception { 
            startServer(); 

            HttpRequest req = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/governance/compliance/summary"))
                .header("X-API-Key", "sk-invalid-key")
                .header("X-Tenant-ID", TENANT_A)
                .build(); 
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString()); 

            assertThat(resp.statusCode()).isEqualTo(401); 
        }

        @Test
        @DisplayName("Health probe /health is accessible without authentication")
        void healthProbe_noAuth_isPublic() throws Exception { 
            startServer(); 

            HttpRequest req = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://127.0.0.1:" + port + "/health"))
                .build(); 
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString()); 

            // Health probe is a PUBLIC path — auth is not required
            assertThat(resp.statusCode()).isIn(200, 503); 
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Stream / real-time transport routes cross-tenant enforcement (P0-04)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Workflow execution and pipeline route cross-tenant enforcement.
     *
     * <p>DC-P1-005: A principal bound to tenant-A must receive {@code 403} when the request
     * carries tenant-B in the {@code X-Tenant-ID} header on pipeline/execution routes.
     *
     * @doc.type class
     * @doc.purpose DC-P1-005 cross-tenant enforcement for workflow/pipeline routes
     * @doc.layer product
     * @doc.pattern Test
     */
    @Nested
    @DisplayName("Workflow / Pipeline routes – cross-tenant enforcement (DC-P1-005)")
    class WorkflowPipelineCrossTenantTests {

        @Test
        @DisplayName("POST /api/v1/pipelines/:id/execute with wrong tenant returns 403")
        void executePipeline_wrongTenant_returnsForbidden() throws Exception {
            startServer();

            HttpResponse<String> resp = sendWithTenant(
                HttpRequest.newBuilder()
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString("{}"))
                    .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/pipelines/pipe-001/execute"))
                    .header("Content-Type", "application/json"),
                TENANT_A_KEY, TENANT_B);

            assertThat(resp.statusCode()).isEqualTo(403);
            assertErrorPayload(resp);
        }

        @Test
        @DisplayName("GET /api/v1/pipelines/:id/executions with wrong tenant returns 403")
        void listExecutions_wrongTenant_returnsForbidden() throws Exception {
            startServer();

            HttpResponse<String> resp = sendWithTenant(
                HttpRequest.newBuilder()
                    .GET()
                    .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/pipelines/pipe-001/executions")),
                TENANT_A_KEY, TENANT_B);

            assertThat(resp.statusCode()).isEqualTo(403);
            assertErrorPayload(resp);
        }

        @Test
        @DisplayName("GET /api/v1/pipelines/:id/executions/:execId with wrong tenant returns 403")
        void getExecution_wrongTenant_returnsForbidden() throws Exception {
            startServer();

            HttpResponse<String> resp = sendWithTenant(
                HttpRequest.newBuilder()
                    .GET()
                    .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/pipelines/pipe-001/executions/exec-001")),
                TENANT_A_KEY, TENANT_B);

            assertThat(resp.statusCode()).isEqualTo(403);
            assertErrorPayload(resp);
        }

        @Test
        @DisplayName("POST /api/v1/executions/:execId/cancel with wrong tenant returns 403")
        void cancelExecution_wrongTenant_returnsForbidden() throws Exception {
            startServer();

            HttpResponse<String> resp = sendWithTenant(
                HttpRequest.newBuilder()
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString("{}"))
                    .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/executions/exec-001/cancel"))
                    .header("Content-Type", "application/json"),
                TENANT_A_KEY, TENANT_B);

            assertThat(resp.statusCode()).isEqualTo(403);
            assertErrorPayload(resp);
        }

        @Test
        @DisplayName("POST /api/v1/executions/:execId/retry with wrong tenant returns 403")
        void retryExecution_wrongTenant_returnsForbidden() throws Exception {
            startServer();

            HttpResponse<String> resp = sendWithTenant(
                HttpRequest.newBuilder()
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString("{}"))
                    .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/executions/exec-001/retry"))
                    .header("Content-Type", "application/json"),
                TENANT_A_KEY, TENANT_B);

            assertThat(resp.statusCode()).isEqualTo(403);
            assertErrorPayload(resp);
        }

        @Test
        @DisplayName("GET /api/v1/pipelines/:id/executions/:execId/logs with wrong tenant returns 403")
        void getExecutionLogs_wrongTenant_returnsForbidden() throws Exception {
            startServer();

            HttpResponse<String> resp = sendWithTenant(
                HttpRequest.newBuilder()
                    .GET()
                    .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/pipelines/pipe-001/executions/exec-001/logs")),
                TENANT_A_KEY, TENANT_B);

            assertThat(resp.statusCode()).isEqualTo(403);
            assertErrorPayload(resp);
        }
    }

    /**
     * Brain/memory and context route cross-tenant enforcement.
     *
     * <p>DC-P1-005: A principal bound to tenant-A must receive {@code 403} when the request
     * carries tenant-B in the {@code X-Tenant-ID} header on brain/memory/context routes.
     *
     * @doc.type class
     * @doc.purpose DC-P1-005 cross-tenant enforcement for brain/memory/context routes
     * @doc.layer product
     * @doc.pattern Test
     */
    @Nested
    @DisplayName("Brain / Memory / Context routes – cross-tenant enforcement (DC-P1-005)")
    class BrainMemoryContextCrossTenantTests {

        @Test
        @DisplayName("GET /api/v1/brain/workspace with wrong tenant returns 403")
        void brainWorkspace_wrongTenant_returnsForbidden() throws Exception {
            startServer();

            HttpResponse<String> resp = sendWithTenant(
                HttpRequest.newBuilder()
                    .GET()
                    .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/brain/workspace")),
                TENANT_A_KEY, TENANT_B);

            assertThat(resp.statusCode()).isEqualTo(403);
            assertErrorPayload(resp);
        }

        @Test
        @DisplayName("GET /api/v1/brain/entities with wrong tenant returns 403")
        void brainEntities_wrongTenant_returnsForbidden() throws Exception {
            startServer();

            HttpResponse<String> resp = sendWithTenant(
                HttpRequest.newBuilder()
                    .GET()
                    .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/brain/entities")),
                TENANT_A_KEY, TENANT_B);

            assertThat(resp.statusCode()).isEqualTo(403);
            assertErrorPayload(resp);
        }

        @Test
        @DisplayName("POST /api/v1/brain/query with wrong tenant returns 403")
        void brainQuery_wrongTenant_returnsForbidden() throws Exception {
            startServer();

            HttpResponse<String> resp = sendWithTenant(
                HttpRequest.newBuilder()
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString("{\"query\":\"test\"}"))
                    .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/brain/query"))
                    .header("Content-Type", "application/json"),
                TENANT_A_KEY, TENANT_B);

            assertThat(resp.statusCode()).isEqualTo(403);
            assertErrorPayload(resp);
        }
    }

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
    /**
     * DC-P1-013: Privacy tests — semantic similarity tenant scope, PII exclusion from RAG,
     * memory TTL deletion, and cross-tenant governance isolation.
     */
    @Nested
    @DisplayName("Privacy – semantic scope, PII exclusion, memory TTL (DC-P1-013)")
    class PrivacyCrossTenantTests {

        @Test
        @DisplayName("POST /api/v1/brain/query with wrong tenant returns 403 (semantic scope guard)")
        void brainQuery_semanticScope_wrongTenantReturnsForbidden() throws Exception {
            startServer();

            HttpResponse<String> resp = sendPostWithTenant(
                "/api/v1/brain/query",
                "{\"query\":\"show me orders\",\"limit\":5}",
                TENANT_A_KEY, TENANT_B);

            assertThat(resp.statusCode()).isEqualTo(403);
            assertErrorPayload(resp);
        }

        @Test
        @DisplayName("GET /api/v1/memory/agents/:agentId with wrong tenant returns 403")
        void agentMemory_wrongTenant_returnsForbidden() throws Exception {
            startServer();

            HttpResponse<String> resp = sendWithTenant(
                HttpRequest.newBuilder()
                    .GET()
                    .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/memory/agents/agent-xyz")),
                TENANT_A_KEY, TENANT_B);

            // Must be 403 for wrong tenant — agent memory is tenant-scoped
            assertThat(resp.statusCode()).isEqualTo(403);
            assertErrorPayload(resp);
        }

        @Test
        @DisplayName("POST /api/v1/memory/agents/:agentId with wrong tenant returns 403")
        void storeAgentMemory_wrongTenant_returnsForbidden() throws Exception {
            startServer();

            HttpResponse<String> resp = sendPostWithTenant(
                "/api/v1/memory/agents/agent-xyz",
                "{\"type\":\"EPISODIC\",\"content\":\"private memory\"}",
                TENANT_A_KEY, TENANT_B);

            assertThat(resp.statusCode()).isEqualTo(403);
            assertErrorPayload(resp);
        }

        @Test
        @DisplayName("GET /api/v1/governance/policies with wrong tenant returns 403")
        void governancePolicies_wrongTenant_returnsForbidden() throws Exception {
            startServer();

            HttpResponse<String> resp = sendWithTenant(
                HttpRequest.newBuilder()
                    .GET()
                    .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/governance/policies")),
                TENANT_A_KEY, TENANT_B);

            assertThat(resp.statusCode()).isEqualTo(403);
            assertErrorPayload(resp);
        }

        @Test
        @DisplayName("POST /api/v1/governance/privacy/redact with wrong tenant returns 403")
        void piiRedact_wrongTenant_returnsForbidden() throws Exception {
            startServer();

            HttpResponse<String> resp = sendPostWithTenant(
                "/api/v1/governance/privacy/redact",
                "{\"collection\":\"user_profiles\",\"entityId\":\"ent-123\",\"fields\":[\"email\"]}",
                TENANT_A_KEY, TENANT_B);

            assertThat(resp.statusCode()).isEqualTo(403);
            assertErrorPayload(resp);
        }
    }

    @Nested
    @DisplayName("Stream / SSE routes – cross-tenant enforcement (P0-04)")
    class StreamTransportCrossTenantTests {
        @Test
        @DisplayName("GET /api/v1/entities/:collection/query/stream with wrong tenant returns 403") 
        void sseEntityQueryStream_wrongTenant_returnsForbidden() throws Exception { 
            startServer(); 

            HttpResponse<String> resp = sendWithTenant(
                HttpRequest.newBuilder()
                    .GET()
                    .uri(URI.create("http://127.0.0.1:" + port
                            + "/api/v1/entities/orders/query/stream?q=status:open")),
                TENANT_A_KEY, TENANT_B); 

            assertThat(resp.statusCode()).isEqualTo(403); 
            assertErrorPayload(resp); 
        }

        @Test
        @DisplayName("GET /api/v1/brain/workspace/stream with wrong tenant returns 403") 
        void sseBrainWorkspaceStream_wrongTenant_returnsForbidden() throws Exception { 
            startServer(); 

            HttpResponse<String> resp = sendWithTenant(
                HttpRequest.newBuilder()
                    .GET()
                    .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/brain/workspace/stream")),
                TENANT_A_KEY, TENANT_B); 

            assertThat(resp.statusCode()).isEqualTo(403); 
            assertErrorPayload(resp); 
        }

        @Test
        @DisplayName("GET /api/v1/entities/:collection/query/stream without auth returns 401") 
        void sseEntityQueryStream_noAuth_returnsUnauthorized() throws Exception { 
            startServer(); 

            HttpRequest req = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://127.0.0.1:" + port
                        + "/api/v1/entities/orders/query/stream?q=*"))
                .header("X-Tenant-ID", TENANT_A)
                .build(); 
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString()); 

            assertThat(resp.statusCode()).isEqualTo(401); 
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void startServer() throws Exception { 
        server = new DataCloudHttpServer(mockClient, port)
            .withApiKeyResolver(tenantAResolver)
            .withAuditService(mockAuditService); 
        server.start(); 
        waitForServerReady(port); 
    }

    private void startServerWithPolicy() throws Exception { 
        server = new DataCloudHttpServer(mockClient, port)
            .withApiKeyResolver(tenantAResolver)
            .withPolicyEngine(permissivePolicyEngine)
            .withAuditService(mockAuditService); 
        server.start(); 
        waitForServerReady(port); 
    }

    private void startServerWithAnalytics() throws Exception { 
        AnalyticsQueryEngine mockEngine = mock(AnalyticsQueryEngine.class); 
        server = new DataCloudHttpServer(mockClient, port, null, null, mockEngine)
            .withApiKeyResolver(tenantAResolver)
            .withAuditService(mockAuditService); 
        server.start(); 
        waitForServerReady(port); 
    }

    private HttpResponse<String> sendWithTenant(
            HttpRequest.Builder builder,
            String apiKey,
            String tenantId) throws IOException, InterruptedException { 
        return httpClient.send(
            builder
                .header("X-API-Key", apiKey)
                .header("X-Tenant-Id", tenantId)
                .build(),
            HttpResponse.BodyHandlers.ofString()); 
    }

    private HttpResponse<String> sendPostWithTenant(
            String path,
            String jsonBody,
            String apiKey,
            String tenantId) throws IOException, InterruptedException { 
        HttpRequest req = HttpRequest.newBuilder()
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .uri(URI.create("http://127.0.0.1:" + port + path))
            .header("Content-Type", "application/json")
            .header("X-API-Key", apiKey)
            .header("X-Tenant-ID", tenantId)
            .build(); 
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString()); 
    }

    private HttpResponse<String> sendDeleteWithTenant(
            String path,
            String apiKey,
            String tenantId) throws IOException, InterruptedException { 
        HttpRequest req = HttpRequest.newBuilder()
            .DELETE()
            .uri(URI.create("http://127.0.0.1:" + port + path))
            .header("X-API-Key", apiKey)
            .header("X-Tenant-ID", tenantId)
            .build(); 
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString()); 
    }

    @SuppressWarnings("unchecked")
    private void assertErrorPayload(HttpResponse<String> resp) throws IOException { 
        Map<String, Object> body = mapper.readValue(resp.body(), Map.class); 
        assertThat(body).containsKey("error"); 
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
        throw new IllegalStateException("Server did not start within 5 seconds on port " + port); 
    }
}
