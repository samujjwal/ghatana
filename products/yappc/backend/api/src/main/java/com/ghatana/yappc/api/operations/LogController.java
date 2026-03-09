/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.operations;

import com.ghatana.yappc.api.common.ApiResponse;
import com.ghatana.yappc.api.common.TenantContextExtractor;
import com.ghatana.yappc.api.domain.LogEntry;
import com.ghatana.yappc.api.service.LogService;
import com.ghatana.yappc.api.service.LogService.*;
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
 * Controller for log operations.
 *
 * @doc.type class
 * @doc.purpose REST endpoints for log management
 * @doc.layer controller
 * @doc.pattern Controller
 */
public class LogController {

    private static final Logger logger = LoggerFactory.getLogger(LogController.class);

    private final LogService logService;
    private final ObjectMapper objectMapper;

    @Inject
    public LogController(LogService logService, ObjectMapper objectMapper) {
        this.logService = logService;
        this.objectMapper = objectMapper;
    }

    /**
     * POST /api/logs - Record a log entry
     */
    public Promise<HttpResponse> recordLog(HttpRequest request) {
        return TenantContextExtractor.requireAuthenticated(request)
            .then(ctx -> request.loadBody()
                .then(body -> {
                    try {
                        RecordLogInput input = objectMapper.readValue(
                            body.getString(StandardCharsets.UTF_8), 
                            RecordLogInput.class
                        );
                        return logService.recordLog(ctx.tenantId(), input)
                            .map(ApiResponse::created);
                    } catch (Exception e) {
                        return Promise.of(ApiResponse.badRequest(e.getMessage()));
                    }
                }))
            .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /**
     * POST /api/logs/batch - Record multiple log entries
     */
    public Promise<HttpResponse> recordLogs(HttpRequest request) {
        return TenantContextExtractor.requireAuthenticated(request)
            .then(ctx -> request.loadBody()
                .then(body -> {
                    try {
                        List<RecordLogInput> inputs = objectMapper.readValue(
                            body.getString(StandardCharsets.UTF_8),
                            objectMapper.getTypeFactory().constructCollectionType(
                                List.class, RecordLogInput.class)
                        );
                        return logService.recordLogs(ctx.tenantId(), inputs)
                            .map(ApiResponse::created);
                    } catch (Exception e) {
                        return Promise.of(ApiResponse.badRequest(e.getMessage()));
                    }
                }))
            .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /**
     * GET /api/logs/:id - Get a log entry by ID
     */
    public Promise<HttpResponse> getLogEntry(HttpRequest request) {
        String logId = request.getPathParameter("id");
        
        return TenantContextExtractor.requireAuthenticated(request)
            .then(ctx -> logService.getLogEntry(ctx.tenantId(), UUID.fromString(logId))
                .map(opt -> opt.map(ApiResponse::ok)
                    .orElse(ApiResponse.notFound("Log entry not found"))))
            .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /**
     * GET /api/projects/:projectId/logs - List logs for a project
     */
    public Promise<HttpResponse> listProjectLogs(HttpRequest request) {
        String projectId = request.getPathParameter("projectId");
        Integer limit = parseIntParam(request.getQueryParameter("limit"));
        
        return TenantContextExtractor.requireAuthenticated(request)
            .then(ctx -> logService.listProjectLogs(ctx.tenantId(), projectId, limit)
                .map(ApiResponse::ok))
            .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /**
     * GET /api/logs/service/:service - List logs by service
     */
    public Promise<HttpResponse> listServiceLogs(HttpRequest request) {
        String service = request.getPathParameter("service");
        Integer limit = parseIntParam(request.getQueryParameter("limit"));
        
        return TenantContextExtractor.requireAuthenticated(request)
            .then(ctx -> logService.listServiceLogs(ctx.tenantId(), service, limit)
                .map(ApiResponse::ok))
            .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /**
     * GET /api/logs/level/:level - List logs by level
     */
    public Promise<HttpResponse> listLogsByLevel(HttpRequest request) {
        String levelStr = request.getPathParameter("level");
        Integer limit = parseIntParam(request.getQueryParameter("limit"));
        
        return TenantContextExtractor.requireAuthenticated(request)
            .then(ctx -> {
                try {
                    LogEntry.LogLevel level = LogEntry.LogLevel.valueOf(levelStr.toUpperCase());
                    return logService.listLogsByLevel(ctx.tenantId(), level, limit)
                        .map(ApiResponse::ok);
                } catch (IllegalArgumentException e) {
                    return Promise.of(ApiResponse.badRequest("Invalid log level"));
                }
            })
            .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /**
     * GET /api/projects/:projectId/logs/errors - List error logs
     */
    public Promise<HttpResponse> listErrorLogs(HttpRequest request) {
        String projectId = request.getPathParameter("projectId");
        Integer limit = parseIntParam(request.getQueryParameter("limit"));
        
        return TenantContextExtractor.requireAuthenticated(request)
            .then(ctx -> logService.listErrorLogs(ctx.tenantId(), projectId, limit)
                .map(ApiResponse::ok))
            .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /**
     * GET /api/logs/trace/:traceId - Get logs by trace ID
     */
    public Promise<HttpResponse> getLogsByTrace(HttpRequest request) {
        String traceId = request.getPathParameter("traceId");
        
        return TenantContextExtractor.requireAuthenticated(request)
            .then(ctx -> logService.getLogsByTrace(ctx.tenantId(), traceId)
                .map(ApiResponse::ok))
            .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /**
     * GET /api/logs/request/:requestId - Get logs by request ID
     */
    public Promise<HttpResponse> getLogsByRequest(HttpRequest request) {
        String requestId = request.getPathParameter("requestId");
        
        return TenantContextExtractor.requireAuthenticated(request)
            .then(ctx -> logService.getLogsByRequest(ctx.tenantId(), requestId)
                .map(ApiResponse::ok))
            .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /**
     * GET /api/users/:userId/logs - Get logs by user
     */
    public Promise<HttpResponse> getLogsByUser(HttpRequest request) {
        String userId = request.getPathParameter("userId");
        Integer limit = parseIntParam(request.getQueryParameter("limit"));
        
        return TenantContextExtractor.requireAuthenticated(request)
            .then(ctx -> logService.getLogsByUser(ctx.tenantId(), userId, limit)
                .map(ApiResponse::ok))
            .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /**
     * GET /api/logs/search - Search logs by message
     */
    public Promise<HttpResponse> searchLogs(HttpRequest request) {
        String query = request.getQueryParameter("q");
        Integer limit = parseIntParam(request.getQueryParameter("limit"));
        
        return TenantContextExtractor.requireAuthenticated(request)
            .then(ctx -> logService.searchLogs(ctx.tenantId(), query, limit)
                .map(ApiResponse::ok))
            .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /**
     * GET /api/projects/:projectId/logs/range - Get logs in time range
     */
    public Promise<HttpResponse> getLogsByTimeRange(HttpRequest request) {
        String projectId = request.getPathParameter("projectId");
        String startStr = request.getQueryParameter("start");
        String endStr = request.getQueryParameter("end");
        Integer limit = parseIntParam(request.getQueryParameter("limit"));
        
        return TenantContextExtractor.requireAuthenticated(request)
            .then(ctx -> {
                Instant start = startStr != null ? Instant.parse(startStr) : Instant.now().minusSeconds(3600);
                Instant end = endStr != null ? Instant.parse(endStr) : Instant.now();
                
                return logService.getLogsByTimeRange(ctx.tenantId(), projectId, start, end, limit)
                    .map(ApiResponse::ok);
            })
            .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /**
     * GET /api/projects/:projectId/logs/statistics - Get log level statistics
     */
    public Promise<HttpResponse> getLogLevelStats(HttpRequest request) {
        String projectId = request.getPathParameter("projectId");
        
        return TenantContextExtractor.requireAuthenticated(request)
            .then(ctx -> logService.getLogLevelStats(ctx.tenantId(), projectId)
                .map(ApiResponse::ok))
            .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /**
     * DELETE /api/logs/:id - Delete a log entry
     */
    public Promise<HttpResponse> deleteLogEntry(HttpRequest request) {
        String logId = request.getPathParameter("id");
        
        return TenantContextExtractor.requireAuthenticated(request)
            .then(ctx -> logService.deleteLogEntry(ctx.tenantId(), UUID.fromString(logId))
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
