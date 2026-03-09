/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.operations;

import com.ghatana.yappc.api.common.ApiResponse;
import com.ghatana.yappc.api.common.TenantContextExtractor;
import com.ghatana.yappc.api.domain.Trace;
import com.ghatana.yappc.api.service.TraceService;
import com.ghatana.yappc.api.service.TraceService.*;
import io.activej.http.*;
import io.activej.promise.Promise;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/**
 * Controller for trace operations.
 *
 * @doc.type class
 * @doc.purpose REST endpoints for distributed tracing
 * @doc.layer controller
 * @doc.pattern Controller
 */
public class TraceController {

    private static final Logger logger = LoggerFactory.getLogger(TraceController.class);

    private final TraceService traceService;
    private final ObjectMapper objectMapper;

    @Inject
    public TraceController(TraceService traceService, ObjectMapper objectMapper) {
        this.traceService = traceService;
        this.objectMapper = objectMapper;
    }

    /**
     * POST /api/traces - Start a new trace
     */
    public Promise<HttpResponse> startTrace(HttpRequest request) {
        return TenantContextExtractor.requireAuthenticated(request)
            .then(ctx -> request.loadBody()
                .then(body -> {
                    try {
                        StartTraceInput input = objectMapper.readValue(
                            body.getString(StandardCharsets.UTF_8), 
                            StartTraceInput.class
                        );
                        return traceService.startTrace(ctx.tenantId(), input)
                            .map(ApiResponse::created);
                    } catch (Exception e) {
                        return Promise.of(ApiResponse.badRequest(e.getMessage()));
                    }
                }))
            .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /**
     * POST /api/traces/:traceId/spans - Add a span to a trace
     */
    public Promise<HttpResponse> addSpan(HttpRequest request) {
        String traceId = request.getPathParameter("traceId");
        
        return TenantContextExtractor.requireAuthenticated(request)
            .then(ctx -> request.loadBody()
                .then(body -> {
                    try {
                        AddSpanInput input = objectMapper.readValue(
                            body.getString(StandardCharsets.UTF_8), 
                            AddSpanInput.class
                        );
                        return traceService.addSpan(ctx.tenantId(), traceId, input)
                            .map(ApiResponse::ok);
                    } catch (Exception e) {
                        return Promise.of(ApiResponse.badRequest(e.getMessage()));
                    }
                }))
            .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /**
     * POST /api/traces/:traceId/spans/:spanId/complete - Complete a span
     */
    public Promise<HttpResponse> completeSpan(HttpRequest request) {
        String traceId = request.getPathParameter("traceId");
        String spanId = request.getPathParameter("spanId");
        
        return TenantContextExtractor.requireAuthenticated(request)
            .then(ctx -> request.loadBody()
                .then(body -> {
                    try {
                        CompleteSpanInput input = objectMapper.readValue(
                            body.getString(StandardCharsets.UTF_8), 
                            CompleteSpanInput.class
                        );
                        return traceService.completeSpan(ctx.tenantId(), traceId, spanId, input)
                            .map(ApiResponse::ok);
                    } catch (Exception e) {
                        return Promise.of(ApiResponse.badRequest(e.getMessage()));
                    }
                }))
            .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /**
     * POST /api/traces/:traceId/complete - Complete a trace
     */
    public Promise<HttpResponse> completeTrace(HttpRequest request) {
        String traceId = request.getPathParameter("traceId");
        
        return TenantContextExtractor.requireAuthenticated(request)
            .then(ctx -> traceService.completeTrace(ctx.tenantId(), traceId)
                .map(ApiResponse::ok))
            .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /**
     * GET /api/traces/:id - Get a trace by internal ID
     */
    public Promise<HttpResponse> getTrace(HttpRequest request) {
        String traceId = request.getPathParameter("id");
        
        return TenantContextExtractor.requireAuthenticated(request)
            .then(ctx -> traceService.getTrace(ctx.tenantId(), UUID.fromString(traceId))
                .map(opt -> opt.map(ApiResponse::ok)
                    .orElse(ApiResponse.notFound("Trace not found"))))
            .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /**
     * GET /api/traces/traceid/:traceId - Get a trace by trace ID string
     */
    public Promise<HttpResponse> getTraceByTraceId(HttpRequest request) {
        String traceId = request.getPathParameter("traceId");
        
        return TenantContextExtractor.requireAuthenticated(request)
            .then(ctx -> traceService.getTraceByTraceId(ctx.tenantId(), traceId)
                .map(opt -> opt.map(ApiResponse::ok)
                    .orElse(ApiResponse.notFound("Trace not found"))))
            .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /**
     * GET /api/projects/:projectId/traces - List traces for a project
     */
    public Promise<HttpResponse> listProjectTraces(HttpRequest request) {
        String projectId = request.getPathParameter("projectId");
        Integer limit = parseIntParam(request.getQueryParameter("limit"));
        
        return TenantContextExtractor.requireAuthenticated(request)
            .then(ctx -> traceService.listProjectTraces(ctx.tenantId(), projectId, limit)
                .map(ApiResponse::ok))
            .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /**
     * GET /api/traces/service/:service - List traces by service
     */
    public Promise<HttpResponse> listServiceTraces(HttpRequest request) {
        String service = request.getPathParameter("service");
        Integer limit = parseIntParam(request.getQueryParameter("limit"));
        
        return TenantContextExtractor.requireAuthenticated(request)
            .then(ctx -> traceService.listServiceTraces(ctx.tenantId(), service, limit)
                .map(ApiResponse::ok))
            .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /**
     * GET /api/traces/service/:service/operation/:operation - List traces by operation
     */
    public Promise<HttpResponse> listOperationTraces(HttpRequest request) {
        String service = request.getPathParameter("service");
        String operation = request.getPathParameter("operation");
        Integer limit = parseIntParam(request.getQueryParameter("limit"));
        
        return TenantContextExtractor.requireAuthenticated(request)
            .then(ctx -> traceService.listOperationTraces(ctx.tenantId(), service, operation, limit)
                .map(ApiResponse::ok))
            .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /**
     * GET /api/projects/:projectId/traces/errors - List error traces
     */
    public Promise<HttpResponse> listErrorTraces(HttpRequest request) {
        String projectId = request.getPathParameter("projectId");
        Integer limit = parseIntParam(request.getQueryParameter("limit"));
        
        return TenantContextExtractor.requireAuthenticated(request)
            .then(ctx -> traceService.listErrorTraces(ctx.tenantId(), projectId, limit)
                .map(ApiResponse::ok))
            .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /**
     * GET /api/projects/:projectId/traces/slow - List slow traces
     */
    public Promise<HttpResponse> listSlowTraces(HttpRequest request) {
        String projectId = request.getPathParameter("projectId");
        String minDurationStr = request.getQueryParameter("minDuration");
        Integer limit = parseIntParam(request.getQueryParameter("limit"));
        
        return TenantContextExtractor.requireAuthenticated(request)
            .then(ctx -> {
                long minDurationMs = minDurationStr != null ? Long.parseLong(minDurationStr) : 1000;
                return traceService.listSlowTraces(ctx.tenantId(), projectId, minDurationMs, limit)
                    .map(ApiResponse::ok);
            })
            .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /**
     * GET /api/users/:userId/traces - List traces by user
     */
    public Promise<HttpResponse> listUserTraces(HttpRequest request) {
        String userId = request.getPathParameter("userId");
        Integer limit = parseIntParam(request.getQueryParameter("limit"));
        
        return TenantContextExtractor.requireAuthenticated(request)
            .then(ctx -> traceService.listUserTraces(ctx.tenantId(), userId, limit)
                .map(ApiResponse::ok))
            .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /**
     * GET /api/traces/request/:requestId - Get traces by request ID
     */
    public Promise<HttpResponse> getTracesByRequest(HttpRequest request) {
        String requestId = request.getPathParameter("requestId");
        
        return TenantContextExtractor.requireAuthenticated(request)
            .then(ctx -> traceService.getTracesByRequest(ctx.tenantId(), requestId)
                .map(ApiResponse::ok))
            .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /**
     * GET /api/projects/:projectId/traces/range - Get traces in time range
     */
    public Promise<HttpResponse> getTracesByTimeRange(HttpRequest request) {
        String projectId = request.getPathParameter("projectId");
        String startStr = request.getQueryParameter("start");
        String endStr = request.getQueryParameter("end");
        Integer limit = parseIntParam(request.getQueryParameter("limit"));
        
        return TenantContextExtractor.requireAuthenticated(request)
            .then(ctx -> {
                Instant start = startStr != null ? Instant.parse(startStr) : Instant.now().minusSeconds(3600);
                Instant end = endStr != null ? Instant.parse(endStr) : Instant.now();
                
                return traceService.getTracesByTimeRange(ctx.tenantId(), projectId, start, end, limit)
                    .map(ApiResponse::ok);
            })
            .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /**
     * GET /api/projects/:projectId/traces/statistics - Get trace statistics
     */
    public Promise<HttpResponse> getTraceStatistics(HttpRequest request) {
        String projectId = request.getPathParameter("projectId");
        String service = request.getQueryParameter("service");
        
        return TenantContextExtractor.requireAuthenticated(request)
            .then(ctx -> traceService.getTraceStatistics(ctx.tenantId(), projectId, service)
                .map(ApiResponse::ok))
            .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /**
     * DELETE /api/traces/:id - Delete a trace
     */
    public Promise<HttpResponse> deleteTrace(HttpRequest request) {
        String traceId = request.getPathParameter("id");
        
        return TenantContextExtractor.requireAuthenticated(request)
            .then(ctx -> traceService.deleteTrace(ctx.tenantId(), UUID.fromString(traceId))
                .map(v -> ApiResponse.noContent()))
            .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    private Integer parseIntParam(String value) {
        if (value == null || value.isEmpty()) return null;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
