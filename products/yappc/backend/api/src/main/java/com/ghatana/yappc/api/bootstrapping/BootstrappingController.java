/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.bootstrapping;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.api.common.ApiResponse;
import com.ghatana.yappc.api.common.TenantContextExtractor;
import com.ghatana.yappc.api.domain.BootstrappingSession.ConversationTurn;
import com.ghatana.yappc.api.domain.BootstrappingSession.ProjectDefinition;
import com.ghatana.yappc.api.service.BootstrappingService;
import com.ghatana.yappc.api.service.BootstrappingService.*;
import io.activej.http.*;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.*;

/**
 * HTTP controller for bootstrapping session endpoints.
 *
 * @doc.type class
 * @doc.purpose HTTP endpoints for bootstrapping sessions
 * @doc.layer api
 * @doc.pattern Controller
 */
public class BootstrappingController {

    private static final Logger logger = LoggerFactory.getLogger(BootstrappingController.class);

    private final BootstrappingService service;
    private final ObjectMapper mapper;

    @Inject
    public BootstrappingController(BootstrappingService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    /** POST /api/bootstrapping/sessions */
    public Promise<HttpResponse> startSession(HttpRequest request) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> request.loadBody()
                        .then(body -> {
                            try {
                                StartSessionRequest req = mapper.readValue(body.getArray(), StartSessionRequest.class);
                                StartSessionInput input = new StartSessionInput(
                                        req.workspaceId(),
                                        req.initialPrompt(),
                                        ctx.userId()
                                );
                                return service.startSession(ctx.tenantId(), input)
                                        .map(ApiResponse::created);
                            } catch (Exception e) {
                                logger.error("Failed to start bootstrapping session", e);
                                return Promise.of(ApiResponse.badRequest(e.getMessage()));
                            }
                        }))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** GET /api/bootstrapping/sessions/:id */
    public Promise<HttpResponse> getSession(HttpRequest request, String id) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> service.getSession(ctx.tenantId(), UUID.fromString(id))
                        .map(opt -> opt.map(ApiResponse::ok)
                                .orElse(ApiResponse.notFound("Session not found"))))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** GET /api/workspaces/:workspaceId/bootstrapping/sessions */
    public Promise<HttpResponse> listWorkspaceSessions(HttpRequest request, String workspaceId) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> service.listWorkspaceSessions(ctx.tenantId(), workspaceId)
                        .map(ApiResponse::ok))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** POST /api/bootstrapping/sessions/:id/conversation */
    public Promise<HttpResponse> addConversationTurn(HttpRequest request, String id) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> request.loadBody()
                        .then(body -> {
                            try {
                                ConversationTurnRequest req = mapper.readValue(body.getArray(), ConversationTurnRequest.class);
                                return service.addConversationTurn(ctx.tenantId(), UUID.fromString(id), 
                                        req.userMessage(), ctx.userId())
                                        .map(ApiResponse::ok);
                            } catch (Exception e) {
                                logger.error("Failed to add conversation turn", e);
                                return Promise.of(ApiResponse.badRequest(e.getMessage()));
                            }
                        }))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** POST /api/bootstrapping/sessions/:id/definition */
    public Promise<HttpResponse> setProjectDefinition(HttpRequest request, String id) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> request.loadBody()
                        .then(body -> {
                            try {
                                ProjectDefinition definition = mapper.readValue(body.getArray(), ProjectDefinition.class);
                                return service.setProjectDefinition(ctx.tenantId(), UUID.fromString(id), definition)
                                        .map(ApiResponse::ok);
                            } catch (Exception e) {
                                logger.error("Failed to set project definition", e);
                                return Promise.of(ApiResponse.badRequest(e.getMessage()));
                            }
                        }))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** POST /api/bootstrapping/sessions/:id/refine */
    public Promise<HttpResponse> refineProject(HttpRequest request, String id) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> request.loadBody()
                        .then(body -> {
                            try {
                                RefineProjectRequest req = mapper.readValue(body.getArray(), RefineProjectRequest.class);
                                return service.refineProject(ctx.tenantId(), UUID.fromString(id), req.refinementPrompt())
                                        .map(ApiResponse::ok);
                            } catch (Exception e) {
                                logger.error("Failed to refine project", e);
                                return Promise.of(ApiResponse.badRequest(e.getMessage()));
                            }
                        }))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** POST /api/bootstrapping/sessions/:id/graph */
    public Promise<HttpResponse> generateProjectGraph(HttpRequest request, String id) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> service.generateProjectGraph(ctx.tenantId(), UUID.fromString(id))
                        .map(ApiResponse::ok))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** GET /api/bootstrapping/sessions/:id/graph */
    public Promise<HttpResponse> getProjectGraph(HttpRequest request, String id) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> service.getSession(ctx.tenantId(), UUID.fromString(id))
                        .map(opt -> opt.map(s -> ApiResponse.ok(s.getProjectGraph()))
                                .orElse(ApiResponse.notFound("Session not found"))))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** POST /api/bootstrapping/sessions/:id/validate */
    public Promise<HttpResponse> validateProject(HttpRequest request, String id) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> service.validateProject(ctx.tenantId(), UUID.fromString(id))
                        .map(ApiResponse::ok))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** POST /api/bootstrapping/sessions/:id/approve */
    public Promise<HttpResponse> approveProject(HttpRequest request, String id) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> request.loadBody()
                        .then(body -> {
                            try {
                                ApproveProjectRequest req = mapper.readValue(body.getArray(), ApproveProjectRequest.class);
                                ApproveProjectInput input = new ApproveProjectInput(
                                        req.projectName(),
                                        req.projectDescription(),
                                        req.sprintDuration(),
                                        req.teamSize()
                                );
                                return service.approveProject(ctx.tenantId(), UUID.fromString(id), input)
                                        .map(ApiResponse::ok);
                            } catch (Exception e) {
                                logger.error("Failed to approve project", e);
                                return Promise.of(ApiResponse.badRequest(e.getMessage()));
                            }
                        }))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** DELETE /api/bootstrapping/sessions/:id */
    public Promise<HttpResponse> abandonSession(HttpRequest request, String id) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> service.abandonSession(ctx.tenantId(), UUID.fromString(id))
                        .map(deleted -> deleted ? ApiResponse.noContent() : ApiResponse.notFound("Session not found")))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    // ========== Request DTOs ==========

    public record StartSessionRequest(
            String workspaceId,
            String initialPrompt
    ) {}

    public record ConversationTurnRequest(String userMessage) {}

    public record RefineProjectRequest(String refinementPrompt) {}

    public record ApproveProjectRequest(
            String projectName,
            String projectDescription,
            Integer sprintDuration,
            Integer teamSize
    ) {}
}
