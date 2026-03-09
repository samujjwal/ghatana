/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.operations;

import com.ghatana.yappc.api.common.ApiResponse;
import com.ghatana.yappc.api.common.TenantContextExtractor;
import com.ghatana.yappc.api.domain.Metric;
import com.ghatana.yappc.api.service.MetricService;
import com.ghatana.yappc.api.service.MetricService.*;
import io.activej.http.*;
import io.activej.promise.Promise;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Controller for metric operations.
 *
 * @doc.type class
 * @doc.purpose REST endpoints for metric management
 * @doc.layer controller
 * @doc.pattern Controller
 */
public class MetricController {

    private static final Logger logger = LoggerFactory.getLogger(MetricController.class);

    private final MetricService metricService;
    private final ObjectMapper objectMapper;

    @Inject
    public MetricController(MetricService metricService, ObjectMapper objectMapper) {
        this.metricService = metricService;
        this.objectMapper = objectMapper;
    }

    /**
     * POST /api/metrics - Create a new metric
     */
    public Promise<HttpResponse> createMetric(HttpRequest request) {
        return TenantContextExtractor.requireAuthenticated(request)
            .then(ctx -> request.loadBody()
                .then(body -> {
                    try {
                        CreateMetricInput input = objectMapper.readValue(
                            body.getString(StandardCharsets.UTF_8), 
                            CreateMetricInput.class
                        );
                        return metricService.createMetric(ctx.tenantId(), input)
                            .map(ApiResponse::created);
                    } catch (Exception e) {
                        return Promise.of(ApiResponse.badRequest(e.getMessage()));
                    }
                }))
            .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /**
     * GET /api/metrics/:id - Get a metric by ID
     */
    public Promise<HttpResponse> getMetric(HttpRequest request) {
        String metricId = request.getPathParameter("id");
        
        return TenantContextExtractor.requireAuthenticated(request)
            .then(ctx -> metricService.getMetric(ctx.tenantId(), UUID.fromString(metricId))
                .map(opt -> opt.map(ApiResponse::ok)
                    .orElse(ApiResponse.notFound("Metric not found"))))
            .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /**
     * GET /api/projects/:projectId/metrics - List metrics for a project
     */
    public Promise<HttpResponse> listProjectMetrics(HttpRequest request) {
        String projectId = request.getPathParameter("projectId");
        
        return TenantContextExtractor.requireAuthenticated(request)
            .then(ctx -> metricService.listProjectMetrics(ctx.tenantId(), projectId)
                .map(ApiResponse::ok))
            .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /**
     * GET /api/metrics/search - Search metrics by name pattern
     */
    public Promise<HttpResponse> searchMetrics(HttpRequest request) {
        String pattern = request.getQueryParameter("pattern");
        
        return TenantContextExtractor.requireAuthenticated(request)
            .then(ctx -> metricService.searchMetrics(ctx.tenantId(), pattern)
                .map(ApiResponse::ok))
            .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /**
     * GET /api/metrics/type/:type - List metrics by type
     */
    public Promise<HttpResponse> listMetricsByType(HttpRequest request) {
        String typeStr = request.getPathParameter("type");
        
        return TenantContextExtractor.requireAuthenticated(request)
            .then(ctx -> {
                try {
                    Metric.MetricType type = Metric.MetricType.valueOf(typeStr.toUpperCase());
                    return metricService.listMetricsByType(ctx.tenantId(), type)
                        .map(ApiResponse::ok);
                } catch (IllegalArgumentException e) {
                    return Promise.of(ApiResponse.badRequest("Invalid metric type"));
                }
            })
            .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /**
     * POST /api/metrics/:id/datapoints - Record data points
     */
    public Promise<HttpResponse> recordDataPoints(HttpRequest request) {
        String metricId = request.getPathParameter("id");
        
        return TenantContextExtractor.requireAuthenticated(request)
            .then(ctx -> request.loadBody()
                .then(body -> {
                    try {
                        List<DataPointInput> inputs = objectMapper.readValue(
                            body.getString(StandardCharsets.UTF_8),
                            objectMapper.getTypeFactory().constructCollectionType(
                                List.class, DataPointInput.class)
                        );
                        return metricService.recordDataPoints(
                            ctx.tenantId(), 
                            UUID.fromString(metricId), 
                            inputs
                        ).map(ApiResponse::ok);
                    } catch (Exception e) {
                        return Promise.of(ApiResponse.badRequest(e.getMessage()));
                    }
                }))
            .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /**
     * GET /api/metrics/:id/datapoints - Query data points
     */
    public Promise<HttpResponse> queryDataPoints(HttpRequest request) {
        String metricId = request.getPathParameter("id");
        String startStr = request.getQueryParameter("start");
        String endStr = request.getQueryParameter("end");
        
        return TenantContextExtractor.requireAuthenticated(request)
            .then(ctx -> {
                Instant start = startStr != null ? Instant.parse(startStr) : Instant.now().minusSeconds(3600);
                Instant end = endStr != null ? Instant.parse(endStr) : Instant.now();
                
                return metricService.queryDataPoints(
                    ctx.tenantId(), 
                    UUID.fromString(metricId), 
                    start, 
                    end
                ).map(ApiResponse::ok);
            })
            .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /**
     * GET /api/metrics/:id/summary - Get metric summary
     */
    public Promise<HttpResponse> getMetricSummary(HttpRequest request) {
        String metricId = request.getPathParameter("id");
        
        return TenantContextExtractor.requireAuthenticated(request)
            .then(ctx -> metricService.getMetricSummary(ctx.tenantId(), UUID.fromString(metricId))
                .map(summary -> summary != null 
                    ? ApiResponse.ok(summary) 
                    : ApiResponse.notFound("Metric not found")))
            .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /**
     * GET /api/projects/:projectId/metrics/statistics - Get project metrics stats
     */
    public Promise<HttpResponse> getProjectStatistics(HttpRequest request) {
        String projectId = request.getPathParameter("projectId");
        
        return TenantContextExtractor.requireAuthenticated(request)
            .then(ctx -> metricService.getProjectStatistics(ctx.tenantId(), projectId)
                .map(ApiResponse::ok))
            .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /**
     * DELETE /api/metrics/:id - Delete a metric
     */
    public Promise<HttpResponse> deleteMetric(HttpRequest request) {
        String metricId = request.getPathParameter("id");
        
        return TenantContextExtractor.requireAuthenticated(request)
            .then(ctx -> metricService.deleteMetric(ctx.tenantId(), UUID.fromString(metricId))
                .map(v -> ApiResponse.noContent()))
            .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }
}
