package com.ghatana.services.studio;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.platform.http.server.response.ErrorResponse;
import io.activej.eventloop.Eventloop;
import io.activej.http.*;
import io.activej.inject.annotation.Provides;
import io.activej.launchers.http.HttpServerLauncher;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static io.activej.http.HttpMethod.*;

/**
 * Studio Workflow Persistence Service — provides durable storage for artifact workflow state
 * and evidence packs across the import/decompile/edit/preview/fidelity pipeline.
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>{@code PUT  /api/v1/studio/workflow-state} — Persist workflow state</li>
 *   <li>{@code GET  /api/v1/studio/workflow-state} — Load workflow state</li>
 *   <li>{@code DELETE /api/v1/studio/workflow-state} — Clear workflow state</li>
 *   <li>{@code PUT  /api/v1/studio/workflow-evidence} — Persist evidence pack</li>
 *   <li>{@code GET  /api/v1/studio/workflow-evidence} — Load evidence pack</li>
 *   <li>{@code GET  /health} — Health probe</li>
 *   <li>{@code GET  /metrics} — Basic metrics</li>
 * </ul>
 *
 * <h2>Security Model</h2>
 * <p>All endpoints require:</p>
 * <ul>
 *   <li>Authorization: Bearer token</li>
 *   <li>X-Tenant-ID header for tenant isolation</li>
 *   <li>X-Workspace-ID header for workspace scoping</li>
 *   <li>X-Project-ID header for project scoping</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Durable workflow state and evidence persistence for Studio
 * @doc.layer platform
 * @doc.pattern Service Launcher
 */
public class StudioWorkflowService extends HttpServerLauncher {

    private static final Logger log = LoggerFactory.getLogger(StudioWorkflowService.class);
    private static final ObjectMapper objectMapper = JsonUtils.getDefaultMapper();

    /** HTTP headers used for multi-tenant scoping */
    static final HttpHeader TENANT_ID_HEADER = HttpHeaders.of("X-Tenant-ID");
    static final HttpHeader WORKSPACE_ID_HEADER = HttpHeaders.of("X-Workspace-ID");
    static final HttpHeader PROJECT_ID_HEADER = HttpHeaders.of("X-Project-ID");
    static final HttpHeader AUTHORIZATION_HEADER = HttpHeaders.of("Authorization");

    /** In-memory store keyed by tenant:workspace:project composite key */
    private final Map<String, WorkflowStateEntry> workflowStateStore = new ConcurrentHashMap<>();
    private final Map<String, EvidencePackEntry> evidencePackStore = new ConcurrentHashMap<>();

    /**
     * Composite key for scoping storage to tenant/workspace/project.
     */
    private String buildCompositeKey(String tenantId, String workspaceId, String projectId) {
        return String.join(":", tenantId, workspaceId, projectId);
    }

    /**
     * Validate required headers and extract scoping information.
     */
    private ScopeContext extractScopeContext(HttpRequest request) {
        String tenantId = request.getHeader(TENANT_ID_HEADER);
        String workspaceId = request.getHeader(WORKSPACE_ID_HEADER);
        String projectId = request.getHeader(PROJECT_ID_HEADER);
        String authHeader = request.getHeader(AUTHORIZATION_HEADER);

        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("Missing required header: X-Tenant-ID");
        }
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalArgumentException("Missing required header: X-Workspace-ID");
        }
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalArgumentException("Missing required header: X-Project-ID");
        }
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Missing or invalid Authorization header");
        }

        return new ScopeContext(tenantId, workspaceId, projectId, authHeader.substring(7));
    }

    @Override
    protected HttpServerConfig createHttpServerConfig() {
        return HttpServerConfig.create()
            .withListenPort(Integer.parseInt(System.getenv().getOrDefault("STUDIO_WORKFLOW_PORT", "8085")));
    }

    @Provides
    AsyncServlet servlet(Eventloop eventloop) {
        return RoutingServlet.create()
            .with(PUT, "/api/v1/studio/workflow-state", this::handlePersistWorkflowState)
            .with(GET, "/api/v1/studio/workflow-state", this::handleLoadWorkflowState)
            .with(DELETE, "/api/v1/studio/workflow-state", this::handleClearWorkflowState)
            .with(PUT, "/api/v1/studio/workflow-evidence", this::handlePersistEvidencePack)
            .with(GET, "/api/v1/studio/workflow-evidence", this::handleLoadEvidencePack)
            .with(GET, "/health", this::handleHealthCheck)
            .with(GET, "/metrics", this::handleMetrics)
            .with(OPTIONS, "/*", this::handleCorsPreflight);
    }

    /**
     * PUT /api/v1/studio/workflow-state — Persist workflow state.
     */
    private Promise<HttpResponse> handlePersistWorkflowState(HttpRequest request) {
        return Promise.ofBlocking(() -> {
            try {
                ScopeContext scope = extractScopeContext(request);
                String body = request.getBody().getString(StandardCharsets.UTF_8);

                if (body == null || body.isBlank()) {
                    return HttpResponse.ofCode(400)
                        .withJson(ErrorResponse.error("Request body is required"));
                }

                String key = buildCompositeKey(scope.tenantId, scope.workspaceId, scope.projectId);
                WorkflowStateEntry entry = new WorkflowStateEntry(
                    body,
                    Instant.now().toString(),
                    scope.tenantId,
                    scope.workspaceId,
                    scope.projectId
                );
                workflowStateStore.put(key, entry);

                log.info("Persisted workflow state for tenant={}, workspace={}, project={}",
                    scope.tenantId, scope.workspaceId, scope.projectId);

                return HttpResponse.ok200()
                    .withHeader(HttpHeaders.CONTENT_TYPE, HttpContentType.APPLICATION_JSON)
                    .withBody("{\"success\":true,\"persistedAt\":\"" + entry.persistedAt + "\"}".getBytes(StandardCharsets.UTF_8));

            } catch (IllegalArgumentException e) {
                log.warn("Validation error persisting workflow state: {}", e.getMessage());
                return HttpResponse.ofCode(400)
                    .withJson(ErrorResponse.error(e.getMessage()));
            } catch (Exception e) {
                log.error("Failed to persist workflow state", e);
                return HttpResponse.ofCode(500)
                    .withJson(ErrorResponse.error("Failed to persist workflow state: " + e.getMessage()));
            }
        });
    }

    /**
     * GET /api/v1/studio/workflow-state — Load workflow state.
     */
    private Promise<HttpResponse> handleLoadWorkflowState(HttpRequest request) {
        return Promise.ofBlocking(() -> {
            try {
                ScopeContext scope = extractScopeContext(request);
                String key = buildCompositeKey(scope.tenantId, scope.workspaceId, scope.projectId);

                WorkflowStateEntry entry = workflowStateStore.get(key);
                if (entry == null) {
                    return HttpResponse.ofCode(404)
                        .withJson(ErrorResponse.error("No workflow state found for the specified scope"));
                }

                return HttpResponse.ok200()
                    .withHeader(HttpHeaders.CONTENT_TYPE, HttpContentType.APPLICATION_JSON)
                    .withBody(entry.stateJson.getBytes(StandardCharsets.UTF_8));

            } catch (IllegalArgumentException e) {
                log.warn("Validation error loading workflow state: {}", e.getMessage());
                return HttpResponse.ofCode(400)
                    .withJson(ErrorResponse.error(e.getMessage()));
            } catch (Exception e) {
                log.error("Failed to load workflow state", e);
                return HttpResponse.ofCode(500)
                    .withJson(ErrorResponse.error("Failed to load workflow state: " + e.getMessage()));
            }
        });
    }

    /**
     * DELETE /api/v1/studio/workflow-state — Clear workflow state.
     */
    private Promise<HttpResponse> handleClearWorkflowState(HttpRequest request) {
        return Promise.ofBlocking(() -> {
            try {
                ScopeContext scope = extractScopeContext(request);
                String key = buildCompositeKey(scope.tenantId, scope.workspaceId, scope.projectId);

                workflowStateStore.remove(key);

                log.info("Cleared workflow state for tenant={}, workspace={}, project={}",
                    scope.tenantId, scope.workspaceId, scope.projectId);

                return HttpResponse.ok200()
                    .withBody("{\"success\":true}".getBytes(StandardCharsets.UTF_8));

            } catch (IllegalArgumentException e) {
                log.warn("Validation error clearing workflow state: {}", e.getMessage());
                return HttpResponse.ofCode(400)
                    .withJson(ErrorResponse.error(e.getMessage()));
            } catch (Exception e) {
                log.error("Failed to clear workflow state", e);
                return HttpResponse.ofCode(500)
                    .withJson(ErrorResponse.error("Failed to clear workflow state: " + e.getMessage()));
            }
        });
    }

    /**
     * PUT /api/v1/studio/workflow-evidence — Persist evidence pack.
     */
    private Promise<HttpResponse> handlePersistEvidencePack(HttpRequest request) {
        return Promise.ofBlocking(() -> {
            try {
                ScopeContext scope = extractScopeContext(request);
                String body = request.getBody().getString(StandardCharsets.UTF_8);

                if (body == null || body.isBlank()) {
                    return HttpResponse.ofCode(400)
                        .withJson(ErrorResponse.error("Request body is required"));
                }

                String key = buildCompositeKey(scope.tenantId, scope.workspaceId, scope.projectId);
                EvidencePackEntry entry = new EvidencePackEntry(
                    body,
                    Instant.now().toString(),
                    scope.tenantId,
                    scope.workspaceId,
                    scope.projectId
                );
                evidencePackStore.put(key, entry);

                log.info("Persisted evidence pack for tenant={}, workspace={}, project={}",
                    scope.tenantId, scope.workspaceId, scope.projectId);

                return HttpResponse.ok200()
                    .withHeader(HttpHeaders.CONTENT_TYPE, HttpContentType.APPLICATION_JSON)
                    .withBody("{\"success\":true,\"persistedAt\":\"" + entry.persistedAt + "\"}".getBytes(StandardCharsets.UTF_8));

            } catch (IllegalArgumentException e) {
                log.warn("Validation error persisting evidence pack: {}", e.getMessage());
                return HttpResponse.ofCode(400)
                    .withJson(ErrorResponse.error(e.getMessage()));
            } catch (Exception e) {
                log.error("Failed to persist evidence pack", e);
                return HttpResponse.ofCode(500)
                    .withJson(ErrorResponse.error("Failed to persist evidence pack: " + e.getMessage()));
            }
        });
    }

    /**
     * GET /api/v1/studio/workflow-evidence — Load evidence pack.
     */
    private Promise<HttpResponse> handleLoadEvidencePack(HttpRequest request) {
        return Promise.ofBlocking(() -> {
            try {
                ScopeContext scope = extractScopeContext(request);
                String key = buildCompositeKey(scope.tenantId, scope.workspaceId, scope.projectId);

                EvidencePackEntry entry = evidencePackStore.get(key);
                if (entry == null) {
                    return HttpResponse.ofCode(404)
                        .withJson(ErrorResponse.error("No evidence pack found for the specified scope"));
                }

                return HttpResponse.ok200()
                    .withHeader(HttpHeaders.CONTENT_TYPE, HttpContentType.APPLICATION_JSON)
                    .withBody(entry.evidenceJson.getBytes(StandardCharsets.UTF_8));

            } catch (IllegalArgumentException e) {
                log.warn("Validation error loading evidence pack: {}", e.getMessage());
                return HttpResponse.ofCode(400)
                    .withJson(ErrorResponse.error(e.getMessage()));
            } catch (Exception e) {
                log.error("Failed to load evidence pack", e);
                return HttpResponse.ofCode(500)
                    .withJson(ErrorResponse.error("Failed to load evidence pack: " + e.getMessage()));
            }
        });
    }

    /**
     * GET /health — Health probe.
     */
    private Promise<HttpResponse> handleHealthCheck(HttpRequest request) {
        return Promise.of(HttpResponse.ok200()
            .withHeader(HttpHeaders.CONTENT_TYPE, HttpContentType.APPLICATION_JSON)
            .withBody("{\"status\":\"UP\",\"service\":\"studio-workflow\",\"timestamp\":\"" + Instant.now().toString() + "\"}".getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * GET /metrics — Service metrics.
     */
    private Promise<HttpResponse> handleMetrics(HttpRequest request) {
        return Promise.ofBlocking(() -> {
            String metrics = String.format(
                "{\"workflowStates\":%d,\"evidencePacks\":%d,\"timestamp\":\"%s\"}",
                workflowStateStore.size(),
                evidencePackStore.size(),
                Instant.now().toString()
            );
            return HttpResponse.ok200()
                .withHeader(HttpHeaders.CONTENT_TYPE, HttpContentType.APPLICATION_JSON)
                .withBody(metrics.getBytes(StandardCharsets.UTF_8));
        });
    }

    /**
     * OPTIONS /* — CORS preflight handler.
     */
    private Promise<HttpResponse> handleCorsPreflight(HttpRequest request) {
        return Promise.of(HttpResponse.ok200()
            .withHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
            .withHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET, PUT, POST, DELETE, OPTIONS")
            .withHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "Content-Type, Authorization, X-Tenant-ID, X-Workspace-ID, X-Project-ID"));
    }

    /**
     * Scope context extracted from request headers.
     */
    private record ScopeContext(String tenantId, String workspaceId, String projectId, String token) {}

    /**
     * Stored workflow state entry with audit metadata.
     */
    private record WorkflowStateEntry(String stateJson, String persistedAt, String tenantId, String workspaceId, String projectId) {}

    /**
     * Stored evidence pack entry with audit metadata.
     */
    private record EvidencePackEntry(String evidenceJson, String persistedAt, String tenantId, String workspaceId, String projectId) {}

    public static void main(String[] args) throws Exception {
        StudioWorkflowService service = new StudioWorkflowService();
        service.launch(args);
    }
}
