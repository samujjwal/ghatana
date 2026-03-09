/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.security;

import com.ghatana.yappc.api.common.ApiResponse;
import com.ghatana.yappc.api.common.TenantContextExtractor;
import com.ghatana.yappc.api.service.ComplianceService;
import com.ghatana.yappc.api.service.ComplianceService.*;
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
 * HTTP controller for compliance assessment endpoints.
 *
 * @doc.type class
 * @doc.purpose HTTP handler for compliance assessment CRUD and lifecycle
 * @doc.layer controller
 * @doc.pattern Controller
 */
public class ComplianceController {

    private static final Logger logger = LoggerFactory.getLogger(ComplianceController.class);

    private final ComplianceService complianceService;
    private final ObjectMapper objectMapper;

    @Inject
    public ComplianceController(ComplianceService complianceService, ObjectMapper objectMapper) {
        this.complianceService = complianceService;
        this.objectMapper = objectMapper;
    }

    /** POST /api/compliance */
    public Promise<HttpResponse> createAssessment(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    String body = request.getBody().asString(StandardCharsets.UTF_8);
                    CreateAssessmentInput input = objectMapper.readValue(body, CreateAssessmentInput.class);
                    return complianceService.createAssessment(workspaceId, input)
                        .map(ApiResponse::created);
                })
        );
    }

    /** GET /api/compliance/:id */
    public Promise<HttpResponse> getAssessment(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    UUID id = UUID.fromString(request.getPathParameter("id"));
                    return complianceService.getAssessment(workspaceId, id)
                        .map(opt -> opt.map(ApiResponse::ok).orElse(ApiResponse.notFound("Assessment not found")));
                })
        );
    }

    /** GET /api/projects/:projectId/compliance */
    public Promise<HttpResponse> listProjectAssessments(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    UUID projectId = UUID.fromString(request.getPathParameter("projectId"));
                    return complianceService.listProjectAssessments(workspaceId, projectId)
                        .map(ApiResponse::ok);
                })
        );
    }

    /** POST /api/compliance/:id/start */
    public Promise<HttpResponse> startAssessment(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    UUID id = UUID.fromString(request.getPathParameter("id"));
                    String body = request.getBody().asString(StandardCharsets.UTF_8);
                    var node = objectMapper.readTree(body);
                    String assessorName = node.get("assessorName").asText();
                    return complianceService.startAssessment(workspaceId, id, assessorName)
                        .map(ApiResponse::ok);
                })
        );
    }

    /** POST /api/compliance/:id/complete */
    public Promise<HttpResponse> completeAssessment(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    UUID id = UUID.fromString(request.getPathParameter("id"));
                    String body = request.getBody().asString(StandardCharsets.UTF_8);
                    CompleteAssessmentInput input = objectMapper.readValue(body, CompleteAssessmentInput.class);
                    return complianceService.completeAssessment(workspaceId, id, input)
                        .map(ApiResponse::ok);
                })
        );
    }

    /** GET /api/projects/:projectId/compliance/statistics */
    public Promise<HttpResponse> getComplianceStatistics(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    UUID projectId = UUID.fromString(request.getPathParameter("projectId"));
                    return complianceService.getComplianceStatistics(workspaceId, projectId)
                        .map(ApiResponse::ok);
                })
        );
    }

    /** DELETE /api/compliance/:id */
    public Promise<HttpResponse> deleteAssessment(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    UUID id = UUID.fromString(request.getPathParameter("id"));
                    return complianceService.deleteAssessment(workspaceId, id)
                        .map(v -> ApiResponse.noContent());
                })
        );
    }
}
