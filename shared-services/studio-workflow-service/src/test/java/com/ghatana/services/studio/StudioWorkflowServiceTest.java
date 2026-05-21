package com.ghatana.services.studio;

import io.activej.eventloop.Eventloop;
import io.activej.http.HttpClient;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import io.activej.test.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for StudioWorkflowService.
 *
 * Verifies that:
 * - Workflow state can be persisted and loaded with proper scoping
 * - Evidence packs can be persisted and loaded
 * - Tenant/workspace/project isolation is enforced
 * - Missing headers result in 400 errors
 * - Unauthorized access returns 400
 * - CORS preflight requests are handled
 *
 * @doc.type test
 * @doc.purpose StudioWorkflowService unit tests
 * @doc.layer platform
 * @doc.pattern UnitTest
 */
@DisplayName("Studio Workflow Service Tests")
class StudioWorkflowServiceTest extends EventloopTestBase {

    private StudioWorkflowService service;
    private HttpClient httpClient;

    @BeforeEach
    void setUp() {
        service = new StudioWorkflowService();
        httpClient = HttpClient.create(eventloop);
    }

    @Test
    @DisplayName("Health endpoint returns UP status")
    void healthEndpointReturnsUpStatus() {
        HttpRequest request = HttpRequest.get("http://localhost:8085/health");

        HttpResponse response = runPromise(() ->
            service.getAsyncServlet().serve(request)
        );

        assertThat(response.getCode()).isEqualTo(200);
        String body = response.getBody().getString();
        assertThat(body).contains("\"status\":\"UP\"");
        assertThat(body).contains("\"service\":\"studio-workflow\"");
    }

    @Test
    @DisplayName("Metrics endpoint returns store statistics")
    void metricsEndpointReturnsStoreStatistics() {
        HttpRequest request = HttpRequest.get("http://localhost:8085/metrics");

        HttpResponse response = runPromise(() ->
            service.getAsyncServlet().serve(request)
        );

        assertThat(response.getCode()).isEqualTo(200);
        String body = response.getBody().getString();
        assertThat(body).contains("workflowStates");
        assertThat(body).contains("evidencePacks");
    }

    @Test
    @DisplayName("CORS preflight returns correct headers")
    void corsPreflightReturnsCorrectHeaders() {
        HttpRequest request = HttpRequest.options("http://localhost:8085/api/v1/studio/workflow-state");

        HttpResponse response = runPromise(() ->
            service.getAsyncServlet().serve(request)
        );

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo("*");
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS)).contains("PUT");
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS)).contains("GET");
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS)).contains("DELETE");
    }

    @Test
    @DisplayName("Persist workflow state requires tenant header")
    void persistWorkflowStateRequiresTenantHeader() {
        HttpRequest request = HttpRequest.put("http://localhost:8085/api/v1/studio/workflow-state")
            .withHeader(HttpHeaders.CONTENT_TYPE, HttpContentType.APPLICATION_JSON)
            .withHeader("X-Workspace-ID", "workspace-a")
            .withHeader("X-Project-ID", "project-a")
            .withHeader("Authorization", "Bearer valid-token")
            .withBody("{}".getBytes());

        HttpResponse response = runPromise(() ->
            service.getAsyncServlet().serve(request)
        );

        assertThat(response.getCode()).isEqualTo(400);
        String body = response.getBody().getString();
        assertThat(body).containsIgnoringCase("tenant");
    }

    @Test
    @DisplayName("Persist workflow state requires workspace header")
    void persistWorkflowStateRequiresWorkspaceHeader() {
        HttpRequest request = HttpRequest.put("http://localhost:8085/api/v1/studio/workflow-state")
            .withHeader(HttpHeaders.CONTENT_TYPE, HttpContentType.APPLICATION_JSON)
            .withHeader("X-Tenant-ID", "tenant-a")
            .withHeader("X-Project-ID", "project-a")
            .withHeader("Authorization", "Bearer valid-token")
            .withBody("{}".getBytes());

        HttpResponse response = runPromise(() ->
            service.getAsyncServlet().serve(request)
        );

        assertThat(response.getCode()).isEqualTo(400);
        String body = response.getBody().getString();
        assertThat(body).containsIgnoringCase("workspace");
    }

    @Test
    @DisplayName("Persist workflow state requires project header")
    void persistWorkflowStateRequiresProjectHeader() {
        HttpRequest request = HttpRequest.put("http://localhost:8085/api/v1/studio/workflow-state")
            .withHeader(HttpHeaders.CONTENT_TYPE, HttpContentType.APPLICATION_JSON)
            .withHeader("X-Tenant-ID", "tenant-a")
            .withHeader("X-Workspace-ID", "workspace-a")
            .withHeader("Authorization", "Bearer valid-token")
            .withBody("{}".getBytes());

        HttpResponse response = runPromise(() ->
            service.getAsyncServlet().serve(request)
        );

        assertThat(response.getCode()).isEqualTo(400);
        String body = response.getBody().getString();
        assertThat(body).containsIgnoringCase("project");
    }

    @Test
    @DisplayName("Persist workflow state requires authorization header")
    void persistWorkflowStateRequiresAuthorizationHeader() {
        HttpRequest request = HttpRequest.put("http://localhost:8085/api/v1/studio/workflow-state")
            .withHeader(HttpHeaders.CONTENT_TYPE, HttpContentType.APPLICATION_JSON)
            .withHeader("X-Tenant-ID", "tenant-a")
            .withHeader("X-Workspace-ID", "workspace-a")
            .withHeader("X-Project-ID", "project-a")
            .withBody("{}".getBytes());

        HttpResponse response = runPromise(() ->
            service.getAsyncServlet().serve(request)
        );

        assertThat(response.getCode()).isEqualTo(400);
        String body = response.getBody().getString();
        assertThat(body).containsIgnoringCase("authorization");
    }

    @Test
    @DisplayName("Full workflow state round-trip with scoping")
    void fullWorkflowStateRoundTripWithScoping() {
        String testState = "{\"jobId\":\"job-123\",\"status\":\"complete\",\"nodes\":5}";

        // Persist
        HttpRequest persistRequest = HttpRequest.put("http://localhost:8085/api/v1/studio/workflow-state")
            .withHeader(HttpHeaders.CONTENT_TYPE, HttpContentType.APPLICATION_JSON)
            .withHeader("X-Tenant-ID", "tenant-test")
            .withHeader("X-Workspace-ID", "workspace-test")
            .withHeader("X-Project-ID", "project-test")
            .withHeader("Authorization", "Bearer test-token")
            .withBody(testState.getBytes());

        HttpResponse persistResponse = runPromise(() ->
            service.getAsyncServlet().serve(persistRequest)
        );

        assertThat(persistResponse.getCode()).isEqualTo(200);
        String persistBody = persistResponse.getBody().getString();
        assertThat(persistBody).contains("\"success\":true");

        // Load
        HttpRequest loadRequest = HttpRequest.get("http://localhost:8085/api/v1/studio/workflow-state")
            .withHeader("X-Tenant-ID", "tenant-test")
            .withHeader("X-Workspace-ID", "workspace-test")
            .withHeader("X-Project-ID", "project-test")
            .withHeader("Authorization", "Bearer test-token");

        HttpResponse loadResponse = runPromise(() ->
            service.getAsyncServlet().serve(loadRequest)
        );

        assertThat(loadResponse.getCode()).isEqualTo(200);
        String loadBody = loadResponse.getBody().getString();
        assertThat(loadBody).isEqualTo(testState);
    }

    @Test
    @DisplayName("Workflow state isolation across tenants")
    void workflowStateIsolationAcrossTenants() {
        String tenantAState = "{\"tenant\":\"A\",\"jobId\":\"job-a\"}";
        String tenantBState = "{\"tenant\":\"B\",\"jobId\":\"job-b\"}";

        // Persist for tenant A
        HttpRequest persistA = HttpRequest.put("http://localhost:8085/api/v1/studio/workflow-state")
            .withHeader(HttpHeaders.CONTENT_TYPE, HttpContentType.APPLICATION_JSON)
            .withHeader("X-Tenant-ID", "tenant-a")
            .withHeader("X-Workspace-ID", "workspace-shared")
            .withHeader("X-Project-ID", "project-shared")
            .withHeader("Authorization", "Bearer token-a")
            .withBody(tenantAState.getBytes());

        runPromise(() -> service.getAsyncServlet().serve(persistA));

        // Persist for tenant B (same workspace/project, different tenant)
        HttpRequest persistB = HttpRequest.put("http://localhost:8085/api/v1/studio/workflow-state")
            .withHeader(HttpHeaders.CONTENT_TYPE, HttpContentType.APPLICATION_JSON)
            .withHeader("X-Tenant-ID", "tenant-b")
            .withHeader("X-Workspace-ID", "workspace-shared")
            .withHeader("X-Project-ID", "project-shared")
            .withHeader("Authorization", "Bearer token-b")
            .withBody(tenantBState.getBytes());

        runPromise(() -> service.getAsyncServlet().serve(persistB));

        // Load for tenant A - should get tenant A's state
        HttpRequest loadA = HttpRequest.get("http://localhost:8085/api/v1/studio/workflow-state")
            .withHeader("X-Tenant-ID", "tenant-a")
            .withHeader("X-Workspace-ID", "workspace-shared")
            .withHeader("X-Project-ID", "project-shared")
            .withHeader("Authorization", "Bearer token-a");

        HttpResponse responseA = runPromise(() -> service.getAsyncServlet().serve(loadA));
        assertThat(responseA.getBody().getString()).contains("\"tenant\":\"A\"");

        // Load for tenant B - should get tenant B's state
        HttpRequest loadB = HttpRequest.get("http://localhost:8085/api/v1/studio/workflow-state")
            .withHeader("X-Tenant-ID", "tenant-b")
            .withHeader("X-Workspace-ID", "workspace-shared")
            .withHeader("X-Project-ID", "project-shared")
            .withHeader("Authorization", "Bearer token-b");

        HttpResponse responseB = runPromise(() -> service.getAsyncServlet().serve(loadB));
        assertThat(responseB.getBody().getString()).contains("\"tenant\":\"B\"");
    }

    @Test
    @DisplayName("Clear workflow state removes data")
    void clearWorkflowStateRemovesData() {
        String testState = "{\"jobId\":\"job-to-clear\"}";

        // Persist
        HttpRequest persistRequest = HttpRequest.put("http://localhost:8085/api/v1/studio/workflow-state")
            .withHeader(HttpHeaders.CONTENT_TYPE, HttpContentType.APPLICATION_JSON)
            .withHeader("X-Tenant-ID", "tenant-clear")
            .withHeader("X-Workspace-ID", "workspace-clear")
            .withHeader("X-Project-ID", "project-clear")
            .withHeader("Authorization", "Bearer token-clear")
            .withBody(testState.getBytes());

        runPromise(() -> service.getAsyncServlet().serve(persistRequest));

        // Clear
        HttpRequest clearRequest = HttpRequest.delete("http://localhost:8085/api/v1/studio/workflow-state")
            .withHeader("X-Tenant-ID", "tenant-clear")
            .withHeader("X-Workspace-ID", "workspace-clear")
            .withHeader("X-Project-ID", "project-clear")
            .withHeader("Authorization", "Bearer token-clear");

        HttpResponse clearResponse = runPromise(() -> service.getAsyncServlet().serve(clearRequest));
        assertThat(clearResponse.getCode()).isEqualTo(200);

        // Load should return 404
        HttpRequest loadRequest = HttpRequest.get("http://localhost:8085/api/v1/studio/workflow-state")
            .withHeader("X-Tenant-ID", "tenant-clear")
            .withHeader("X-Workspace-ID", "workspace-clear")
            .withHeader("X-Project-ID", "project-clear")
            .withHeader("Authorization", "Bearer token-clear");

        HttpResponse loadResponse = runPromise(() -> service.getAsyncServlet().serve(loadRequest));
        assertThat(loadResponse.getCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("Evidence pack round-trip with scoping")
    void evidencePackRoundTripWithScoping() {
        String testEvidence = "{\"evidenceId\":\"ev-456\",\"stage\":\"decompile\"}";

        // Persist
        HttpRequest persistRequest = HttpRequest.put("http://localhost:8085/api/v1/studio/workflow-evidence")
            .withHeader(HttpHeaders.CONTENT_TYPE, HttpContentType.APPLICATION_JSON)
            .withHeader("X-Tenant-ID", "tenant-evidence")
            .withHeader("X-Workspace-ID", "workspace-evidence")
            .withHeader("X-Project-ID", "project-evidence")
            .withHeader("Authorization", "Bearer evidence-token")
            .withBody(testEvidence.getBytes());

        HttpResponse persistResponse = runPromise(() ->
            service.getAsyncServlet().serve(persistRequest)
        );

        assertThat(persistResponse.getCode()).isEqualTo(200);

        // Load
        HttpRequest loadRequest = HttpRequest.get("http://localhost:8085/api/v1/studio/workflow-evidence")
            .withHeader("X-Tenant-ID", "tenant-evidence")
            .withHeader("X-Workspace-ID", "workspace-evidence")
            .withHeader("X-Project-ID", "project-evidence")
            .withHeader("Authorization", "Bearer evidence-token");

        HttpResponse loadResponse = runPromise(() ->
            service.getAsyncServlet().serve(loadRequest)
        );

        assertThat(loadResponse.getCode()).isEqualTo(200);
        assertThat(loadResponse.getBody().getString()).isEqualTo(testEvidence);
    }

    @Test
    @DisplayName("Load non-existent workflow state returns 404")
    void loadNonExistentWorkflowStateReturns404() {
        HttpRequest loadRequest = HttpRequest.get("http://localhost:8085/api/v1/studio/workflow-state")
            .withHeader("X-Tenant-ID", "tenant-nonexistent")
            .withHeader("X-Workspace-ID", "workspace-nonexistent")
            .withHeader("X-Project-ID", "project-nonexistent")
            .withHeader("Authorization", "Bearer token-nonexistent");

        HttpResponse loadResponse = runPromise(() ->
            service.getAsyncServlet().serve(loadRequest)
        );

        assertThat(loadResponse.getCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("Load non-existent evidence pack returns 404")
    void loadNonExistentEvidencePackReturns404() {
        HttpRequest loadRequest = HttpRequest.get("http://localhost:8085/api/v1/studio/workflow-evidence")
            .withHeader("X-Tenant-ID", "tenant-no-evidence")
            .withHeader("X-Workspace-ID", "workspace-no-evidence")
            .withHeader("X-Project-ID", "project-no-evidence")
            .withHeader("Authorization", "Bearer token-no-evidence");

        HttpResponse loadResponse = runPromise(() ->
            service.getAsyncServlet().serve(loadRequest)
        );

        assertThat(loadResponse.getCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("Empty request body returns 400 for persist")
    void emptyRequestBodyReturns400ForPersist() {
        HttpRequest persistRequest = HttpRequest.put("http://localhost:8085/api/v1/studio/workflow-state")
            .withHeader(HttpHeaders.CONTENT_TYPE, HttpContentType.APPLICATION_JSON)
            .withHeader("X-Tenant-ID", "tenant-empty")
            .withHeader("X-Workspace-ID", "workspace-empty")
            .withHeader("X-Project-ID", "project-empty")
            .withHeader("Authorization", "Bearer token-empty")
            .withBody("".getBytes());

        HttpResponse response = runPromise(() ->
            service.getAsyncServlet().serve(persistRequest)
        );

        assertThat(response.getCode()).isEqualTo(400);
    }
}
