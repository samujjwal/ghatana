/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.security;

import com.ghatana.yappc.api.common.ApiResponse;
import com.ghatana.yappc.api.common.TenantContextExtractor;
import com.ghatana.yappc.api.service.SecurityScanService;
import com.ghatana.yappc.api.service.SecurityScanService.*;
import com.ghatana.products.yappc.domain.enums.ScanType;
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
 * HTTP controller for security scan endpoints.
 *
 * @doc.type class
 * @doc.purpose HTTP handler for security scan CRUD and lifecycle
 * @doc.layer controller
 * @doc.pattern Controller
 */
public class SecurityScanController {

    private static final Logger logger = LoggerFactory.getLogger(SecurityScanController.class);

    private final SecurityScanService scanService;
    private final ObjectMapper objectMapper;

    @Inject
    public SecurityScanController(SecurityScanService scanService, ObjectMapper objectMapper) {
        this.scanService = scanService;
        this.objectMapper = objectMapper;
    }

    /** POST /api/security-scans */
    public Promise<HttpResponse> startScan(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    String body = request.getBody().asString(StandardCharsets.UTF_8);
                    StartScanInput input = objectMapper.readValue(body, StartScanInput.class);
                    return scanService.startScan(workspaceId, input)
                        .map(ApiResponse::created);
                })
        );
    }

    /** GET /api/security-scans/:id */
    public Promise<HttpResponse> getScan(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    UUID id = UUID.fromString(request.getPathParameter("id"));
                    return scanService.getScan(workspaceId, id)
                        .map(opt -> opt.map(ApiResponse::ok).orElse(ApiResponse.notFound("Scan not found")));
                })
        );
    }

    /** GET /api/projects/:projectId/security-scans */
    public Promise<HttpResponse> listProjectScans(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    UUID projectId = UUID.fromString(request.getPathParameter("projectId"));
                    return scanService.listProjectScans(workspaceId, projectId)
                        .map(ApiResponse::ok);
                })
        );
    }

    /** GET /api/security-scans/type/:type */
    public Promise<HttpResponse> listScansByType(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    ScanType type = ScanType.valueOf(request.getPathParameter("type").toUpperCase());
                    return scanService.listScansByType(workspaceId, type)
                        .map(ApiResponse::ok);
                })
        );
    }

    /** GET /api/security-scans/running */
    public Promise<HttpResponse> listRunningScans(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    return scanService.listRunningScans(workspaceId)
                        .map(ApiResponse::ok);
                })
        );
    }

    /** POST /api/security-scans/:id/complete */
    public Promise<HttpResponse> completeScan(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    UUID id = UUID.fromString(request.getPathParameter("id"));
                    String body = request.getBody().asString(StandardCharsets.UTF_8);
                    CompleteScanInput input = objectMapper.readValue(body, CompleteScanInput.class);
                    return scanService.completeScan(workspaceId, id, input)
                        .map(ApiResponse::ok);
                })
        );
    }

    /** POST /api/security-scans/:id/fail */
    public Promise<HttpResponse> failScan(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    UUID id = UUID.fromString(request.getPathParameter("id"));
                    String body = request.getBody().asString(StandardCharsets.UTF_8);
                    var node = objectMapper.readTree(body);
                    String reason = node.get("reason").asText();
                    return scanService.failScan(workspaceId, id, reason)
                        .map(ApiResponse::ok);
                })
        );
    }

    /** GET /api/projects/:projectId/security-scans/stats */
    public Promise<HttpResponse> getScanStatistics(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    UUID projectId = UUID.fromString(request.getPathParameter("projectId"));
                    return scanService.getScanStatistics(workspaceId, projectId)
                        .map(ApiResponse::ok);
                })
        );
    }

    /** DELETE /api/security-scans/:id */
    public Promise<HttpResponse> deleteScan(HttpRequest request) {
        return ApiResponse.wrap(
            TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID workspaceId = UUID.fromString(ctx.tenantId());
                    UUID id = UUID.fromString(request.getPathParameter("id"));
                    return scanService.deleteScan(workspaceId, id)
                        .map(v -> ApiResponse.noContent());
                })
        );
    }
}
