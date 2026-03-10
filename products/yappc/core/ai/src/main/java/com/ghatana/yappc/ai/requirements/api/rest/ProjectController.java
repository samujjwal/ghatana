package com.ghatana.yappc.ai.requirements.api.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.security.model.User;
import com.ghatana.platform.http.server.response.ResponseBuilder;
import com.ghatana.platform.http.server.servlet.RoutingServlet;
import com.ghatana.yappc.ai.requirements.api.rest.dto.CreateProjectRequest;
import com.ghatana.yappc.ai.requirements.api.rest.dto.UpdateProjectRequest;
import com.ghatana.yappc.ai.requirements.api.rest.dto.ProjectResponse;
import com.ghatana.yappc.ai.requirements.application.project.ProjectService;
import com.ghatana.yappc.ai.requirements.domain.project.Project;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * REST controller for project operations.
 *
 * <p><b>Purpose:</b> Provides HTTP API endpoints for:
 * - Creating projects
 * - Retrieving project details
 * - Managing project settings
 * - Archiving projects
 *
 * <p><b>Endpoints:</b>
 * <ul>
 *   <li><b>POST /api/v1/projects:</b> Create project
 *   <li><b>GET /api/v1/projects/{id}:</b> Get project
 *   <li><b>PUT /api/v1/projects/{id}:</b> Update project
 *   <li><b>DELETE /api/v1/projects/{id}:</b> Archive project
 *   <li><b>GET /api/v1/workspaces/{id}/projects:</b> List projects in workspace
 * </ul>
 *
 * @doc.type class
 * @doc.purpose REST controller for project management
 * @doc.layer product
 * @doc.pattern REST Controller
 * @since 1.0.0
 */
public final class ProjectController {
    private static final Logger logger = LoggerFactory.getLogger(ProjectController.class);

    private final ProjectService projectService;
    private final ObjectMapper objectMapper;
    private final java.util.concurrent.Executor executor;

    public ProjectController(ProjectService projectService, ObjectMapper objectMapper, java.util.concurrent.Executor executor) {
        this.projectService = Objects.requireNonNull(projectService);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.executor = Objects.requireNonNull(executor);
    }

    /**
     * Create servlet with all project routes.
     * 
     * @return RoutingServlet with all project routes
     */
    public RoutingServlet createServlet() {
        RoutingServlet servlet = new RoutingServlet();
        servlet.addAsyncRoute(HttpMethod.POST, "/api/v1/projects", this::createProject);
        servlet.addAsyncRoute(HttpMethod.GET, "/api/v1/projects/:id", this::getProject);
        servlet.addAsyncRoute(HttpMethod.PUT, "/api/v1/projects/:id", this::updateProject);
        servlet.addAsyncRoute(HttpMethod.DELETE, "/api/v1/projects/:id", this::archiveProject);
        servlet.addAsyncRoute(HttpMethod.GET, "/api/v1/workspaces/:workspaceId/projects", this::listProjects);
        return servlet;
    }

    /**
     * POST /api/v1/projects
     *
     * <p>Create a new project.
     *
     * @param request HTTP request with CreateProjectRequest body
     * @return Promise resolving to HTTP response with created project
     */
    public Promise<HttpResponse> createProject(HttpRequest request) {
        logger.info("Creating project");

        return extractUser(request)
            .then(principal -> parseRequestBody(request, CreateProjectRequest.class)
                .then(req -> projectService.createProject(
                        principal,
                        req.workspaceId(),
                        req.name(),
                        req.description(),
                        req.template()
                    )
                    .map(project -> ResponseBuilder.created()
                        .json(toProjectResponse(project))
                        .build())
                )
            )
            .map(result -> result)
            .whenException(e -> logger.error("Failed to create project", e));
    }

    /**
     * GET /api/v1/projects/{id}
     *
     * <p>Get project details by ID.
     *
     * @param request HTTP request
     * @return Promise resolving to HTTP response with project
     */
    public Promise<HttpResponse> getProject(HttpRequest request) {
        String projectId = request.getPathParameter("id");
        logger.debug("Getting project: {}", projectId);

        return extractUser(request)
            .then(principal -> projectService.getProject(principal, projectId)
                .map(project -> ResponseBuilder.ok()
                    .json(toProjectResponse(project))
                    .build())
            )
            .map(result -> result)
            .whenException(e -> logger.error("Failed to get project", e));
    }

    /**
     * PUT /api/v1/projects/{id}
     *
     * <p>Update project settings.
     *
     * @param request HTTP request with UpdateProjectRequest body
     * @return Promise resolving to HTTP response with updated project
     */
    public Promise<HttpResponse> updateProject(HttpRequest request) {
        String projectId = request.getPathParameter("id");
        logger.debug("Updating project: {}", projectId);

        return extractUser(request)
            .then(principal -> parseRequestBody(request, UpdateProjectRequest.class)
                .then(req -> {
                    // Update status if provided
                    if (req.status() != null) {
                        return projectService.updateStatus(principal, projectId, req.status())
                            .map(project -> ResponseBuilder.ok()
                                .json(toProjectResponse(project))
                                .build());
                    }
                    // Update name and/or description if provided
                    if (req.name() != null || req.description() != null) {
                        Project updateData = Project.builder()
                            .projectId(projectId)
                            .name(req.name())
                            .description(req.description())
                            .build();
                        return projectService.updateProject(updateData, principal)
                            .map(project -> ResponseBuilder.ok()
                                .json(toProjectResponse(project))
                                .build());
                    }
                    // No changes requested
                    return Promise.of(ResponseBuilder.ok()
                        .json(Map.of("message", "No changes requested"))
                        .build());
                })
            )
            .map(result -> result)
            .whenException(e -> logger.error("Failed to update project", e));
    }

    /**
     * DELETE /api/v1/projects/{id}
     *
     * <p>Archive a project.
     *
     * @param request HTTP request
     * @return Promise resolving to HTTP response
     */
    public Promise<HttpResponse> archiveProject(HttpRequest request) {
        String projectId = request.getPathParameter("id");
        logger.info("Archiving project: {}", projectId);

        return extractUser(request)
            .then(principal -> projectService.deleteProject(principal, projectId)
                .map(v -> ResponseBuilder.noContent().build())
            )
            .map(result -> result)
            .whenException(e -> logger.error("Failed to archive project", e));
    }

    /**
     * GET /api/v1/workspaces/{workspaceId}/projects
     *
     * <p>List all projects in a workspace.
     *
     * @param request HTTP request
     * @return Promise resolving to HTTP response with project list
     */
    public Promise<HttpResponse> listProjects(HttpRequest request) {
        String workspaceId = request.getPathParameter("workspaceId");
        logger.debug("Listing projects in workspace: {}", workspaceId);

        return extractUser(request)
            .then(principal -> projectService.listWorkspaceProjects(principal, workspaceId)
                .map(projects -> {
                    var responses = projects.stream()
                        .map(this::toProjectResponse)
                        .collect(Collectors.toList());

                    return ResponseBuilder.ok()
                        .json(Map.of("projects", responses))
                        .build();
                })
            )
            .map(result -> result)
            .whenException(e -> logger.error("Failed to list projects", e));
    }

    // ============ Helper Methods ============

    private Promise<User> extractUser(HttpRequest request) {
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

    private ProjectResponse toProjectResponse(Project project) {
        return new ProjectResponse(
            project.getProjectId(),
            project.getWorkspaceId(),
            project.getName(),
            project.getDescription(),
            project.getStatus().name(),
            project.getCreatedAt().toString(),
            project.getUpdatedAt().toString()
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