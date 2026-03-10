package com.ghatana.yappc.ai.requirements.api.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.security.model.User;
import com.ghatana.platform.http.server.response.ResponseBuilder;
import com.ghatana.platform.http.server.servlet.RoutingServlet;
import com.ghatana.yappc.ai.requirements.api.error.ErrorResponse;
import com.ghatana.yappc.ai.requirements.api.validation.Validation;
import com.ghatana.yappc.ai.requirements.api.rest.dto.CreateWorkspaceRequest;
import com.ghatana.yappc.ai.requirements.api.rest.dto.UpdateWorkspaceRequest;
import com.ghatana.yappc.ai.requirements.api.rest.dto.AddMemberRequest;
import com.ghatana.yappc.ai.requirements.api.rest.dto.WorkspaceResponse;
import com.ghatana.yappc.ai.requirements.application.workspace.WorkspaceService;
import com.ghatana.yappc.ai.requirements.domain.workspace.Workspace;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for workspace operations.
 *
 * <p><b>Purpose:</b> Provides HTTP API endpoints for:
 * - Creating workspaces
 * - Retrieving workspace details
 * - Managing workspace members
 * - Updating workspace settings
 *
 * <p><b>Endpoints:</b>
 * <ul>
 *   <li><b>POST /api/v1/workspaces:</b> Create workspace
 *   <li><b>GET /api/v1/workspaces/{id}:</b> Get workspace
 *   <li><b>PUT /api/v1/workspaces/{id}:</b> Update workspace
 *   <li><b>DELETE /api/v1/workspaces/{id}:</b> Delete workspace
 *   <li><b>GET /api/v1/workspaces/{id}/members:</b> List members
 *   <li><b>POST /api/v1/workspaces/{id}/members:</b> Add member
 *   <li><b>DELETE /api/v1/workspaces/{id}/members/{memberId}:</b> Remove member
 * </ul>
 *
 * @doc.type class
 * @doc.purpose REST controller for workspace management
 * @doc.layer product
 * @doc.pattern REST Controller
 * @since 1.0.0
 */
public final class WorkspaceController {
    private static final Logger logger = LoggerFactory.getLogger(WorkspaceController.class);

    private final WorkspaceService workspaceService;
    private final ObjectMapper objectMapper;
    private final java.util.concurrent.Executor executor;

    public WorkspaceController(WorkspaceService workspaceService, ObjectMapper objectMapper, java.util.concurrent.Executor executor) {
        this.workspaceService = Objects.requireNonNull(workspaceService);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.executor = Objects.requireNonNull(executor);
    }

    /**
     * Create servlet with all workspace routes.
     * 
     * @return RoutingServlet with all workspace routes
     */
    public RoutingServlet createServlet() {
        RoutingServlet servlet = new RoutingServlet();
        servlet.addAsyncRoute(HttpMethod.POST, "/api/v1/workspaces", this::createWorkspace);
        servlet.addAsyncRoute(HttpMethod.GET, "/api/v1/workspaces/:id", this::getWorkspace);
        servlet.addAsyncRoute(HttpMethod.PUT, "/api/v1/workspaces/:id", this::updateWorkspace);
        servlet.addAsyncRoute(HttpMethod.DELETE, "/api/v1/workspaces/:id", this::deleteWorkspace);
        servlet.addAsyncRoute(HttpMethod.POST, "/api/v1/workspaces/:id/members", this::addMember);
        servlet.addAsyncRoute(HttpMethod.DELETE, "/api/v1/workspaces/:id/members/:memberId", this::removeMember);
        return servlet;
    }

    /**
     * POST /api/v1/workspaces
     *
     * <p>Create a new workspace for collaboration.
     *
     * @param request HTTP request with CreateWorkspaceRequest body
     * @return Promise resolving to HTTP response with created workspace
     */
    public Promise<HttpResponse> createWorkspace(HttpRequest request) {
        logger.info("Creating workspace");

        return extractUser(request)
            .then(principal -> parseRequestBody(request, CreateWorkspaceRequest.class)
                .then(req -> workspaceService.createWorkspace(principal, req.name(), req.description())
                    .map(workspace -> ResponseBuilder.created()
                        .json(toWorkspaceResponse(workspace))
                        .build())
                )
            )
            .map(result -> result)
            .whenException(e -> logger.error("Failed to create workspace", e));
    }

    /**
     * GET /api/v1/workspaces/{id}
     *
     * <p>Get workspace details by ID.
     *
     * @param request HTTP request
     * @return Promise resolving to HTTP response with workspace
     */
    public Promise<HttpResponse> getWorkspace(HttpRequest request) {
        try {
            // Validate and extract parameters
            String workspaceId = Validation.requirePathParameter(request, "id");
            UUID workspaceUuid = Validation.requireValidUuid(workspaceId, "workspaceId");
            
            logger.debug("Getting workspace: {}", workspaceUuid);

            return extractUser(request)
                .then(principal -> {
                    logger.debug("Getting workspace: {} for user: {}", workspaceUuid, principal.getUserId());
                    return workspaceService.getWorkspace(workspaceId, principal)
                        .map(workspace -> ResponseBuilder.ok()
                            .json(toWorkspaceResponse(workspace))
                            .build());
                })
                .whenException(e -> {
                    logger.error("Failed to get workspace: {}", workspaceUuid, e);
                    if (e instanceof ErrorResponse.ValidationException) {
                        throw new RuntimeException(e); // Will be caught by ExceptionHandlingFilter
                    }
                    throw new RuntimeException(e);
                });
                
        } catch (ErrorResponse.ValidationException e) {
            // Validation errors are handled by ExceptionHandlingFilter
            throw new RuntimeException(e);
        } catch (Exception e) {
            logger.error("Unexpected error in getWorkspace", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * PUT /api/v1/workspaces/{id}
     *
     * <p>Update workspace settings.
     *
     * @param request HTTP request with UpdateWorkspaceRequest body
     * @return Promise resolving to HTTP response with updated workspace
     */
    public Promise<HttpResponse> updateWorkspace(HttpRequest request) {
        String workspaceId = request.getPathParameter("id");
        logger.debug("Updating workspace: {}", workspaceId);

        return extractUser(request)
            .then(principal -> parseRequestBody(request, UpdateWorkspaceRequest.class)
                .then(req -> workspaceService.getWorkspace(workspaceId, principal)
                    .then(workspace -> {
                        // Create updated workspace with new values using builder
                        Workspace updated = Workspace.builder()
                            .workspaceId(workspace.workspaceId())
                            .name(req.name() != null ? req.name() : workspace.name())
                            .description(req.description() != null ? req.description() : workspace.description())
                            .ownerId(workspace.ownerId())
                            .orgUnitId(workspace.orgUnitId())
                            .status(workspace.status())
                            .settings(workspace.settings())
                            .members(workspace.members())
                            .createdAt(workspace.createdAt())
                            .updatedAt(java.time.Instant.now())
                            .build();
                        return workspaceService.updateWorkspace(updated, principal);
                    })
                    .map(workspace -> ResponseBuilder.ok()
                        .json(toWorkspaceResponse(workspace))
                        .build())
                )
            )
            .map(result -> result)
            .whenException(e -> logger.error("Failed to update workspace", e));
    }

    /**
     * DELETE /api/v1/workspaces/{id}
     *
     * <p>Delete a workspace.
     *
     * @param request HTTP request
     * @return Promise resolving to HTTP response
     */
    public Promise<HttpResponse> deleteWorkspace(HttpRequest request) {
        String workspaceId = request.getPathParameter("id");
        logger.info("Deleting workspace: {}", workspaceId);

        return extractUser(request)
            .then(principal -> workspaceService.deleteWorkspace(workspaceId, principal))
            .map(v -> {
                logger.info("Successfully deleted workspace: {}", workspaceId);
                return ResponseBuilder.noContent().build();
            })
            .whenException(e -> logger.error("Failed to delete workspace", e));
    }

    /**
     * GET /api/v1/workspaces/{id}/members
     *
     * <p>List workspace members.
     *
     * @param request HTTP request
     * @return Promise resolving to HTTP response with member list
     */
    public Promise<HttpResponse> listMembers(HttpRequest request) {
        String workspaceId = request.getPathParameter("id");
        logger.debug("Listing members for workspace: {}", workspaceId);

        return extractUser(request)
            .then(principal -> workspaceService.getWorkspace(workspaceId, principal)
                .map(workspace -> {
                    var members = workspace.members().stream()
                        .map(m -> Map.of(
                            "userId", m.userId(),
                            "role", m.role().name(),
                            "joinedAt", m.joinedAt().toString()
                        ))
                        .collect(Collectors.toList());

                    return ResponseBuilder.ok()
                        .json(Map.of("members", members))
                        .build();
                })
            )
            .map(result -> result)
            .whenException(e -> logger.error("Failed to list members", e));
    }

    /**
     * POST /api/v1/workspaces/{id}/members
     *
     * <p>Add member to workspace.
     *
     * @param request HTTP request with AddMemberRequest body
     * @return Promise resolving to HTTP response
     */
    public Promise<HttpResponse> addMember(HttpRequest request) {
        String workspaceId = request.getPathParameter("id");
        logger.info("Adding member to workspace: {}", workspaceId);

        return extractUser(request)
            .then(principal -> parseRequestBody(request, AddMemberRequest.class)
                .then(req -> workspaceService.addMember(workspaceId, req.userId(), req.role(), principal)
                    .map(workspace -> ResponseBuilder.ok()
                        .json(toWorkspaceResponse(workspace))
                        .build())
                )
            )
            .map(result -> result)
            .whenException(e -> logger.error("Failed to add member", e));
    }

    /**
     * DELETE /api/v1/workspaces/{id}/members/{memberId}
     *
     * <p>Remove member from workspace.
     *
     * @param request HTTP request
     * @return Promise resolving to HTTP response
     */
    public Promise<HttpResponse> removeMember(HttpRequest request) {
        String workspaceId = request.getPathParameter("id");
        String memberId = request.getPathParameter("memberId");
        logger.info("Removing member from workspace: workspace={}, member={}", workspaceId, memberId);

        return extractUser(request)
            .then(principal -> workspaceService.removeMember(workspaceId, memberId, principal)
                .map(v -> ResponseBuilder.noContent().build())
            )
            .map(result -> result)
            .whenException(e -> logger.error("Failed to remove member", e));
    }

    // ============ Helper Methods ============

    private Promise<User> extractUser(HttpRequest request) {
        // Extract from request attribute (set by auth filter)
        Object principal = request.getAttachment("userPrincipal");
        if (principal instanceof User) {
            return Promise.of((User) principal);
        }
        return Promise.ofException(new IllegalStateException("User principal not found in request"));
    }

    private <T> Promise<T> parseRequestBody(HttpRequest request, Class<T> type) {
        return request.loadBody()
            .then(byteBuf -> Promise.ofBlocking(executor, () -> {
                byte[] body = byteBuf.asArray();
                if (body == null || body.length == 0) {
                    throw new IllegalArgumentException("Request body is required");
                }
                return objectMapper.readValue(body, type);
            }));
    }

    private WorkspaceResponse toWorkspaceResponse(Workspace workspace) {
        return new WorkspaceResponse(
            workspace.workspaceId(),
            workspace.name(),
            workspace.description(),
            workspace.ownerId(),
            workspace.status().name(),
            workspace.createdAt().toString(),
            workspace.updatedAt().toString(),
            workspace.members().size()
        );
    }

    private HttpResponse handleError(Throwable e, String context) {
        logger.error("{}: {}", context, e.getMessage(), e);

        if (e instanceof IllegalArgumentException) {
            return ResponseBuilder.badRequest()
                .json(Map.of("error", e.getMessage()))
                .build();
        }

        if (e.getClass().getSimpleName().contains("Unauthorized")) {
            return ResponseBuilder.unauthorized()
                .json(Map.of("error", "Unauthorized"))
                .build();
        }

        return ResponseBuilder.internalServerError()
                .json(Map.of("error", "Internal server error"))
                .build();
    }
}