package com.ghatana.digitalmarketing.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.digitalmarketing.application.workspace.WorkspaceService;
import com.ghatana.digitalmarketing.api.security.DmosHttpContextFactory;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.workspace.Workspace;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.RoutingServlet;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

/**
 * ActiveJ HTTP servlet exposing the DMOS workspace management API.
 *
 * <h2>Endpoints</h2>
 * <pre>
 *   POST   /v1/workspaces                            - Create workspace
 *   GET    /v1/workspaces                            - List workspaces for tenant
 *   GET    /v1/workspaces/:workspaceId               - Get a workspace
 *   POST   /v1/workspaces/:workspaceId/suspend       - Suspend a workspace
 *   POST   /v1/workspaces/:workspaceId/reactivate    - Reactivate a workspace
 *   GET    /v1/workspaces/:workspaceId/capabilities  - Get workspace capabilities (P0-002, P0-004)
 * </pre>
 *
 * <p>Tenant isolation is enforced through the {@code X-Tenant-ID} header.
 * All mutating operations require an {@code X-Idempotency-Key} header.
 * The {@code X-Correlation-ID} header is propagated; a new ID is generated if absent.</p>
 *
 * P2-025: Uses DmosHttpContextFactory for server-side identity derivation to prevent
 * spoofed identity attacks (P0-015). Client-provided X-Roles/X-Permissions headers are
 * ignored in production mode.
 *
 * P0-002, P0-004: Added capabilities endpoint for runtime feature gating.
 *
 * @doc.type class
 * @doc.purpose DMOS HTTP API servlet for workspace management endpoints
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 */
public final class DmosWorkspaceServlet {

    private static final Logger LOG = LoggerFactory.getLogger(DmosWorkspaceServlet.class);
    private static final String CONTENT_JSON = "application/json; charset=utf-8";

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private final WorkspaceService workspaceService;
    private final Eventloop eventloop;
    private final DmosHttpContextFactory httpContextFactory;

    public DmosWorkspaceServlet(WorkspaceService workspaceService, Eventloop eventloop, DmosHttpContextFactory httpContextFactory) {
        this.workspaceService = Objects.requireNonNull(workspaceService, "workspaceService must not be null");
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
        this.httpContextFactory = Objects.requireNonNull(httpContextFactory, "httpContextFactory must not be null");
    }

    public DmosWorkspaceServlet(WorkspaceService workspaceService, Eventloop eventloop) {
        this(workspaceService, eventloop, new DmosHttpContextFactory(false, null));
    }

    public AsyncServlet getServlet() {
        return DmosApiRateLimiter.wrap(
            RoutingServlet.builder(eventloop)
                .with(HttpMethod.POST, "/v1/workspaces", this::handleCreateWorkspace)
                .with(HttpMethod.GET, "/v1/workspaces", this::handleListWorkspaces)
                .with(HttpMethod.GET, "/v1/workspaces/:workspaceId", this::handleGetWorkspace)
                .with(HttpMethod.POST, "/v1/workspaces/:workspaceId/suspend", this::handleSuspendWorkspace)
                .with(HttpMethod.POST, "/v1/workspaces/:workspaceId/reactivate", this::handleReactivateWorkspace)
                .with(HttpMethod.GET, "/v1/workspaces/:workspaceId/capabilities", this::handleGetWorkspaceCapabilities)
                .build()
        );
    }

    private Promise<HttpResponse> handleCreateWorkspace(HttpRequest request) {
        return request.loadBody().then(__ -> {
            try {
                DmOperationContext ctx = httpContextFactory.buildContext(request, "root", true);
                CreateWorkspaceRequest body = MAPPER.readValue(
                    request.getBody().getString(StandardCharsets.UTF_8),
                    CreateWorkspaceRequest.class);

                return workspaceService.createWorkspace(
                        ctx,
                        new WorkspaceService.CreateWorkspaceCommand(body.name(), body.description()))
                    .map(ws -> jsonResponse(201, WorkspaceResponse.from(ws)))
                    .then(r -> Promise.of(r), e -> handleError("create workspace", e, request));
            } catch (IllegalArgumentException e) {
                return Promise.of(DmosApiErrorResponses.error(400, e.getMessage(), resolveCorrelationId(request), Map.of("request", e.getMessage())));
            } catch (Exception e) {
                LOG.error("[DMOS] Unexpected error creating workspace", e);
                return Promise.of(DmosApiErrorResponses.error(500, "Internal error", resolveCorrelationId(request), Map.of()));
            }
        });
    }

    private Promise<HttpResponse> handleListWorkspaces(HttpRequest request) {
        try {
            DmOperationContext ctx = httpContextFactory.buildContext(request, "root", false);

            return workspaceService.listWorkspaces(ctx)
                .map(list -> jsonResponse(200, list.stream().map(WorkspaceResponse::from).toList()))
                .then(r -> Promise.of(r), e -> handleError("list workspaces", e, request));
        } catch (IllegalArgumentException e) {
            return Promise.of(DmosApiErrorResponses.error(400, e.getMessage(), resolveCorrelationId(request), Map.of("request", e.getMessage())));
        } catch (Exception e) {
            LOG.error("[DMOS] Unexpected error listing workspaces", e);
            return Promise.of(DmosApiErrorResponses.error(500, "Internal error", resolveCorrelationId(request), Map.of()));
        }
    }

    private Promise<HttpResponse> handleGetWorkspace(HttpRequest request) {
        try {
            String workspaceId = request.getPathParameter("workspaceId");
            DmOperationContext ctx = httpContextFactory.buildContext(request, "root", false);

            return workspaceService.getWorkspace(ctx, workspaceId)
                .map(ws -> jsonResponse(200, WorkspaceResponse.from(ws)))
                .then(r -> Promise.of(r), e -> {
                    if (e instanceof NoSuchElementException) {
                        return Promise.of(DmosApiErrorResponses.error(404, "Workspace not found: " + workspaceId, resolveCorrelationId(request), Map.of()));
                    }
                    return handleError("get workspace", e, request);
                });
        } catch (IllegalArgumentException e) {
            return Promise.of(DmosApiErrorResponses.error(400, e.getMessage(), resolveCorrelationId(request), Map.of("request", e.getMessage())));
        } catch (Exception e) {
            LOG.error("[DMOS] Unexpected error getting workspace", e);
            return Promise.of(DmosApiErrorResponses.error(500, "Internal error", resolveCorrelationId(request), Map.of()));
        }
    }

    private Promise<HttpResponse> handleSuspendWorkspace(HttpRequest request) {
        try {
            String workspaceId = request.getPathParameter("workspaceId");
            DmOperationContext ctx = httpContextFactory.buildContext(request, workspaceId, true);

            return workspaceService.suspendWorkspace(ctx, workspaceId)
                .map(ws -> jsonResponse(200, WorkspaceResponse.from(ws)))
                .then(r -> Promise.of(r), e -> {
                    if (e instanceof NoSuchElementException) {
                        return Promise.of(DmosApiErrorResponses.error(404, "Workspace not found: " + workspaceId, resolveCorrelationId(request), Map.of()));
                    }
                    if (e instanceof IllegalStateException) {
                        return Promise.of(DmosApiErrorResponses.error(409, e.getMessage(), resolveCorrelationId(request), Map.of()));
                    }
                    return handleError("suspend workspace", e, request);
                });
        } catch (IllegalArgumentException e) {
            return Promise.of(DmosApiErrorResponses.error(400, e.getMessage(), resolveCorrelationId(request), Map.of("request", e.getMessage())));
        } catch (Exception e) {
            LOG.error("[DMOS] Unexpected error suspending workspace", e);
            return Promise.of(DmosApiErrorResponses.error(500, "Internal error", resolveCorrelationId(request), Map.of()));
        }
    }

    private Promise<HttpResponse> handleReactivateWorkspace(HttpRequest request) {
        try {
            String workspaceId = request.getPathParameter("workspaceId");
            DmOperationContext ctx = httpContextFactory.buildContext(request, workspaceId, true);

            return workspaceService.reactivateWorkspace(ctx, workspaceId)
                .map(ws -> jsonResponse(200, WorkspaceResponse.from(ws)))
                .then(r -> Promise.of(r), e -> {
                    if (e instanceof NoSuchElementException) {
                        return Promise.of(DmosApiErrorResponses.error(404, "Workspace not found: " + workspaceId, resolveCorrelationId(request), Map.of()));
                    }
                    if (e instanceof IllegalStateException) {
                        return Promise.of(DmosApiErrorResponses.error(409, e.getMessage(), resolveCorrelationId(request), Map.of()));
                    }
                    return handleError("reactivate workspace", e, request);
                });
        } catch (IllegalArgumentException e) {
            return Promise.of(DmosApiErrorResponses.error(400, e.getMessage(), resolveCorrelationId(request), Map.of("request", e.getMessage())));
        } catch (Exception e) {
            LOG.error("[DMOS] Unexpected error reactivating workspace", e);
            return Promise.of(DmosApiErrorResponses.error(500, "Internal error", resolveCorrelationId(request), Map.of()));
        }
    }

    private Promise<HttpResponse> handleGetWorkspaceCapabilities(HttpRequest request) {
        try {
            String workspaceId = request.getPathParameter("workspaceId");
            DmOperationContext ctx = httpContextFactory.buildContext(request, "root", false);

            return workspaceService.getWorkspaceCapabilities(ctx, workspaceId)
                .map(capabilities -> {
                    WorkspaceCapabilitiesResponse response = new WorkspaceCapabilitiesResponse(
                        workspaceId,
                        java.time.Instant.now().toString(),
                        capabilities
                    );
                    return jsonResponse(200, response);
                })
                .then(r -> Promise.of(r), e -> {
                    if (e instanceof NoSuchElementException) {
                        return Promise.of(DmosApiErrorResponses.error(404, "Workspace not found: " + workspaceId, resolveCorrelationId(request), Map.of()));
                    }
                    return handleError("get workspace capabilities", e, request);
                });
        } catch (IllegalArgumentException e) {
            return Promise.of(DmosApiErrorResponses.error(400, e.getMessage(), resolveCorrelationId(request), Map.of("request", e.getMessage())));
        } catch (Exception e) {
            LOG.error("[DMOS] Unexpected error getting workspace capabilities", e);
            return Promise.of(DmosApiErrorResponses.error(500, "Internal error", resolveCorrelationId(request), Map.of()));
        }
    }

    private Promise<HttpResponse> handleError(String operation, Throwable e, HttpRequest request) {
        String correlationId = resolveCorrelationId(request);
        if (e instanceof SecurityException) {
            return Promise.of(DmosApiErrorResponses.error(403, e.getMessage(), correlationId, Map.of()));
        }
        LOG.error("[DMOS] Failed to {}", operation, e);
        return Promise.of(DmosApiErrorResponses.error(500, "Internal error", correlationId, Map.of()));
    }

    private HttpResponse jsonResponse(int code, Object body) {
        try {
            return HttpResponse.ofCode(code)
                .withHeader(HttpHeaders.CONTENT_TYPE, CONTENT_JSON)
                .withBody(MAPPER.writeValueAsBytes(body))
                .build();
        } catch (Exception e) {
            LOG.error("[DMOS] Serialization failure", e);
            return HttpResponse.ofCode(500).build();
        }
    }

    private static String resolveCorrelationId(HttpRequest request) {
        String header = request.getHeader(HttpHeaders.of("X-Correlation-ID"));
        if (header != null && !header.isBlank()) {
            return header;
        }
        return UUID.randomUUID().toString();
    }

    record CreateWorkspaceRequest(String name, String description) { }

    record WorkspaceResponse(
        String id,
        String tenantId,
        String name,
        String description,
        String status,
        String createdBy,
        String createdAt,
        String updatedAt
    ) {
        static WorkspaceResponse from(Workspace w) {
            return new WorkspaceResponse(
                w.getId().getValue(),
                w.getTenantId().getValue(),
                w.getName(),
                w.getDescription(),
                w.getStatus().name(),
                w.getCreatedBy(),
                w.getCreatedAt().toString(),
                w.getUpdatedAt().toString()
            );
        }
    }

    record WorkspaceCapabilitiesResponse(
        String workspaceId,
        String lastUpdated,
        java.util.List<WorkspaceService.WorkspaceCapability> capabilities
    ) { }
}
