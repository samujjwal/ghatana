/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.initialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.api.common.ApiResponse;
import com.ghatana.yappc.api.common.TenantContextExtractor;
import com.ghatana.yappc.api.service.ProjectService;
import com.ghatana.yappc.api.service.ProjectService.*;
import io.activej.http.*;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.*;

/**
 * HTTP controller for project management endpoints.
 *
 * @doc.type class
 * @doc.purpose HTTP endpoints for project lifecycle management
 * @doc.layer api
 * @doc.pattern Controller
 */
public class ProjectController {

    private static final Logger logger = LoggerFactory.getLogger(ProjectController.class);

    private final ProjectService service;
    private final ObjectMapper mapper;

    @Inject
    public ProjectController(ProjectService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    /** POST /api/projects */
    public Promise<HttpResponse> createProject(HttpRequest request) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> request.loadBody()
                        .then(body -> {
                            try {
                                CreateProjectRequest req = mapper.readValue(body.getArray(), CreateProjectRequest.class);

                                CreateProjectInput input = new CreateProjectInput(
                                        req.workspaceId(),
                                        null,
                                        req.key(),
                                        req.name(),
                                        req.description(),
                                        ctx.userId(),
                                        req.targetUsers(),
                                        req.techStack(),
                                        req.tags(),
                                        req.settings()
                                );

                                return service.createProject(ctx.tenantId(), input)
                                        .map(ApiResponse::created);
                            } catch (Exception e) {
                                logger.error("Failed to create project", e);
                                return Promise.of(ApiResponse.badRequest(e.getMessage()));
                            }
                        }))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** GET /api/projects/:id */
    public Promise<HttpResponse> getProject(HttpRequest request, String id) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> service.getProject(ctx.tenantId(), UUID.fromString(id))
                        .map(opt -> opt.map(ApiResponse::ok)
                                .orElse(ApiResponse.notFound("Project not found"))))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** GET /api/workspaces/:workspaceId/projects/key/:key */
    public Promise<HttpResponse> getProjectByKey(HttpRequest request, String workspaceId, String key) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> service.getProjectByKey(ctx.tenantId(), workspaceId, key)
                        .map(opt -> opt.map(ApiResponse::ok)
                                .orElse(ApiResponse.notFound("Project not found"))))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** GET /api/workspaces/:workspaceId/projects */
    public Promise<HttpResponse> listWorkspaceProjects(HttpRequest request, String workspaceId) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> service.listWorkspaceProjects(ctx.tenantId(), UUID.fromString(workspaceId))
                        .map(ApiResponse::ok))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** PATCH /api/projects/:id */
    public Promise<HttpResponse> updateProject(HttpRequest request, String id) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> request.loadBody()
                        .then(body -> {
                            try {
                                UpdateProjectRequest req = mapper.readValue(body.getArray(), UpdateProjectRequest.class);
                                UpdateProjectInput input = new UpdateProjectInput(
                                        req.name(),
                                        req.description(),
                                        req.vision(),
                                        req.targetUsers(),
                                        req.techStack(),
                                        req.tags(),
                                        req.settings()
                                );
                                return service.updateProject(ctx.tenantId(), UUID.fromString(id), input)
                                        .map(ApiResponse::ok);
                            } catch (Exception e) {
                                logger.error("Failed to update project", e);
                                return Promise.of(ApiResponse.badRequest(e.getMessage()));
                            }
                        }))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** POST /api/projects/:id/start */
    public Promise<HttpResponse> startProject(HttpRequest request, String id) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> service.startProject(ctx.tenantId(), UUID.fromString(id))
                        .map(ApiResponse::ok))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** POST /api/projects/:id/pause */
    public Promise<HttpResponse> pauseProject(HttpRequest request, String id) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> service.pauseProject(ctx.tenantId(), UUID.fromString(id))
                        .map(ApiResponse::ok))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** POST /api/projects/:id/resume */
    public Promise<HttpResponse> resumeProject(HttpRequest request, String id) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> service.resumeProject(ctx.tenantId(), UUID.fromString(id))
                        .map(ApiResponse::ok))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** POST /api/projects/:id/complete */
    public Promise<HttpResponse> completeProject(HttpRequest request, String id) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> service.completeProject(ctx.tenantId(), UUID.fromString(id))
                        .map(ApiResponse::ok))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** POST /api/projects/:id/archive */
    public Promise<HttpResponse> archiveProject(HttpRequest request, String id) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> service.archiveProject(ctx.tenantId(), UUID.fromString(id))
                        .map(ApiResponse::ok))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** DELETE /api/projects/:id */
    public Promise<HttpResponse> deleteProject(HttpRequest request, String id) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> service.deleteProject(ctx.tenantId(), UUID.fromString(id))
                        .map(deleted -> deleted ? ApiResponse.noContent() : ApiResponse.notFound("Project not found")))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** GET /api/projects/:id/statistics */
    public Promise<HttpResponse> getProjectStatistics(HttpRequest request, String id) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> service.getProjectStatistics(ctx.tenantId(), UUID.fromString(id))
                        .map(ApiResponse::ok))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    // ========== Request DTOs ==========

    public record CreateProjectRequest(
            String workspaceId,
            String key,
            String name,
            String description,
            List<String> targetUsers,
            Map<String, Object> techStack,
            List<String> tags,
            Map<String, Object> settings
    ) {}

    public record UpdateProjectRequest(
            String name,
            String description,
            String vision,
            List<String> targetUsers,
            Map<String, Object> techStack,
            List<String> tags,
            Map<String, Object> settings
    ) {}
}
