/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.operations;

import com.ghatana.yappc.api.common.ApiResponse;
import com.ghatana.yappc.api.common.TenantContextExtractor;
import com.ghatana.yappc.api.service.AlertService;
import com.ghatana.yappc.api.service.AlertService.*;
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
 * HTTP controller for alert management endpoints.
 *
 * @doc.type class
 * @doc.purpose HTTP handler for alert CRUD and lifecycle
 * @doc.layer controller
 * @doc.pattern Controller
 */
public class AlertController {

    private static final Logger logger = LoggerFactory.getLogger(AlertController.class);

    private final AlertService alertService;
    private final ObjectMapper objectMapper;

    @Inject
    public AlertController(AlertService alertService, ObjectMapper objectMapper) {
        this.alertService = alertService;
        this.objectMapper = objectMapper;
    }

    /** POST /api/alerts */
    public Promise<HttpResponse> createAlert(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    String body = request.getBody().asString(StandardCharsets.UTF_8);
                    CreateAlertInput input = objectMapper.readValue(body, CreateAlertInput.class);
                    return alertService.createAlert(workspaceId, input)
                        .map(ApiResponse::created);
                })
        );
    }

    /** GET /api/alerts/:id */
    public Promise<HttpResponse> getAlert(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    UUID id = UUID.fromString(request.getPathParameter("id"));
                    return alertService.getAlert(workspaceId, id)
                        .map(opt -> opt.map(ApiResponse::ok).orElse(ApiResponse.notFound("Alert not found")));
                })
        );
    }

    /** GET /api/projects/:projectId/alerts */
    public Promise<HttpResponse> listProjectAlerts(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    UUID projectId = UUID.fromString(request.getPathParameter("projectId"));
                    return alertService.listProjectAlerts(workspaceId, projectId)
                        .map(ApiResponse::ok);
                })
        );
    }

    /** GET /api/alerts/open */
    public Promise<HttpResponse> listOpenAlerts(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    return alertService.listOpenAlerts(workspaceId)
                        .map(ApiResponse::ok);
                })
        );
    }

    /** GET /api/alerts/severity/:severity */
    public Promise<HttpResponse> listAlertsBySeverity(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    String severity = request.getPathParameter("severity");
                    return alertService.listAlertsBySeverity(workspaceId, severity)
                        .map(ApiResponse::ok);
                })
        );
    }

    /** POST /api/alerts/:id/acknowledge */
    public Promise<HttpResponse> acknowledgeAlert(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    UUID id = UUID.fromString(request.getPathParameter("id"));
                    UUID userId = UUID.fromString(ctx.userId());
                    return alertService.acknowledgeAlert(workspaceId, id, userId)
                        .map(ApiResponse::ok);
                })
        );
    }

    /** POST /api/alerts/:id/resolve */
    public Promise<HttpResponse> resolveAlert(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    UUID id = UUID.fromString(request.getPathParameter("id"));
                    UUID userId = UUID.fromString(ctx.userId());
                    return alertService.resolveAlert(workspaceId, id, userId)
                        .map(ApiResponse::ok);
                })
        );
    }

    /** GET /api/projects/:projectId/alerts/statistics */
    public Promise<HttpResponse> getAlertStatistics(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    UUID projectId = UUID.fromString(request.getPathParameter("projectId"));
                    return alertService.getAlertStatistics(workspaceId, projectId)
                        .map(ApiResponse::ok);
                })
        );
    }

    /** DELETE /api/alerts/:id */
    public Promise<HttpResponse> deleteAlert(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    UUID id = UUID.fromString(request.getPathParameter("id"));
                    return alertService.deleteAlert(workspaceId, id)
                        .map(v -> ApiResponse.noContent());
                })
        );
    }
}
