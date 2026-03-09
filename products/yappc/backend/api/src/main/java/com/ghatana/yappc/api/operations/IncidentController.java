/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.operations;

import com.ghatana.yappc.api.common.ApiResponse;
import com.ghatana.yappc.api.common.TenantContextExtractor;
import com.ghatana.yappc.api.service.IncidentService;
import com.ghatana.yappc.api.service.IncidentService.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * HTTP controller for incident management endpoints.
 *
 * @doc.type class
 * @doc.purpose HTTP handler for incident CRUD and lifecycle
 * @doc.layer controller
 * @doc.pattern Controller
 */
public class IncidentController {

    private static final Logger logger = LoggerFactory.getLogger(IncidentController.class);

    private final IncidentService incidentService;
    private final ObjectMapper objectMapper;

    @Inject
    public IncidentController(IncidentService incidentService, ObjectMapper objectMapper) {
        this.incidentService = incidentService;
        this.objectMapper = objectMapper;
    }

    /** POST /api/incidents */
    public Promise<HttpResponse> createIncident(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    String body = request.getBody().asString(StandardCharsets.UTF_8);
                    CreateIncidentInput input = objectMapper.readValue(body, CreateIncidentInput.class);
                    return incidentService.createIncident(workspaceId, input)
                        .map(ApiResponse::created);
                })
        );
    }

    /** GET /api/incidents/:id */
    public Promise<HttpResponse> getIncident(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    UUID id = UUID.fromString(request.getPathParameter("id"));
                    return incidentService.getIncident(workspaceId, id)
                        .map(opt -> opt.map(ApiResponse::ok).orElse(ApiResponse.notFound("Incident not found")));
                })
        );
    }

    /** GET /api/projects/:projectId/incidents */
    public Promise<HttpResponse> listProjectIncidents(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    UUID projectId = UUID.fromString(request.getPathParameter("projectId"));
                    return incidentService.listProjectIncidents(workspaceId, projectId)
                        .map(ApiResponse::ok);
                })
        );
    }

    /** GET /api/incidents/open */
    public Promise<HttpResponse> listOpenIncidents(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    return incidentService.listOpenIncidents(workspaceId)
                        .map(ApiResponse::ok);
                })
        );
    }

    /** GET /api/incidents/severity/:severity */
    public Promise<HttpResponse> listIncidentsBySeverity(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    String severity = request.getPathParameter("severity");
                    return incidentService.listIncidentsBySeverity(workspaceId, severity)
                        .map(ApiResponse::ok);
                })
        );
    }

    /** POST /api/incidents/:id/investigate */
    public Promise<HttpResponse> startInvestigation(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    UUID id = UUID.fromString(request.getPathParameter("id"));
                    return incidentService.startInvestigation(workspaceId, id)
                        .map(ApiResponse::ok);
                })
        );
    }

    /** POST /api/incidents/:id/resolve */
    public Promise<HttpResponse> resolveIncident(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    UUID id = UUID.fromString(request.getPathParameter("id"));
                    String body = request.getBody().asString(StandardCharsets.UTF_8);
                    ResolveInput input = objectMapper.readValue(body, ResolveInput.class);
                    return incidentService.resolveIncident(workspaceId, id, input)
                        .map(ApiResponse::ok);
                })
        );
    }

    /** POST /api/incidents/:id/close */
    public Promise<HttpResponse> closeIncident(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    UUID id = UUID.fromString(request.getPathParameter("id"));
                    return incidentService.closeIncident(workspaceId, id)
                        .map(ApiResponse::ok);
                })
        );
    }

    /** POST /api/incidents/:id/assign */
    public Promise<HttpResponse> assignIncident(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    UUID id = UUID.fromString(request.getPathParameter("id"));
                    String body = request.getBody().asString(StandardCharsets.UTF_8);
                    var node = objectMapper.readTree(body);
                    UUID assigneeId = UUID.fromString(node.get("assigneeId").asText());
                    return incidentService.assignIncident(workspaceId, id, assigneeId)
                        .map(ApiResponse::ok);
                })
        );
    }

    /** GET /api/projects/:projectId/incidents/statistics */
    public Promise<HttpResponse> getIncidentStatistics(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    UUID projectId = UUID.fromString(request.getPathParameter("projectId"));
                    return incidentService.getIncidentStatistics(workspaceId, projectId)
                        .map(ApiResponse::ok);
                })
        );
    }

    /** DELETE /api/incidents/:id */
    public Promise<HttpResponse> deleteIncident(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    UUID id = UUID.fromString(request.getPathParameter("id"));
                    return incidentService.deleteIncident(workspaceId, id)
                        .map(v -> ApiResponse.noContent());
                })
        );
    }
}
