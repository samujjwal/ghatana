package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.launcher.http.security.RequestContext;
import com.ghatana.datacloud.launcher.http.security.RequestContextResolver;
import com.ghatana.datacloud.operations.OperationRecord;
import com.ghatana.datacloud.operations.OperationRecorder;
import com.ghatana.datacloud.operations.OperationStatus;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * HTTP handler for the unified Data Cloud operation/job timeline.
 *
 * @doc.type class
 * @doc.purpose Canonical operations job API for connector, media, pipeline, agent, and background work
 * @doc.layer product
 * @doc.pattern Handler
 */
public final class OperationsJobHandler {

    private static final String STORAGE_MODE = "volatile";

    private final HttpHandlerSupport http;
    private final OperationRecorder operationRecorder;

    public OperationsJobHandler(HttpHandlerSupport http, OperationRecorder operationRecorder) {
        this.http = http;
        this.operationRecorder = operationRecorder;
    }

    public Promise<HttpResponse> handleListJobs(HttpRequest request) {
        RequestContextResolver.ResolutionResult permissionResult = http.requirePermission(request, "operations:jobs:read");
        if (!permissionResult.isSuccess()) {
            return Promise.of(http.errorResponse(permissionResult.errorCode(), permissionResult.errorMessage()));
        }

        RequestContextResolver.ResolutionResult contextResult = http.requireRequestContext(request);
        if (!contextResult.isSuccess()) {
            return Promise.of(http.errorResponse(contextResult.errorCode(), contextResult.errorMessage()));
        }

        String tenantId = contextResult.context().map(RequestContext::tenantId).orElse(null);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header or authenticated tenant is required"));
        }

        int limit = parseLimit(request.getQueryParameter("limit"));
        List<Map<String, Object>> jobs = operationRecorder.listRecent(tenantId, limit).stream()
                .map(OperationRecord::toResponse)
                .toList();

        return Promise.of(http.jsonResponse(Map.of(
                "tenantId", tenantId,
                "items", jobs,
                "count", jobs.size(),
                "storageMode", STORAGE_MODE,
                "generatedAt", Instant.now().toString()
        )));
    }

    public Promise<HttpResponse> handleGetJob(HttpRequest request) {
        RequestContextResolver.ResolutionResult permissionResult = http.requirePermission(request, "operations:jobs:read");
        if (!permissionResult.isSuccess()) {
            return Promise.of(http.errorResponse(permissionResult.errorCode(), permissionResult.errorMessage()));
        }

        RequestContextResolver.ResolutionResult contextResult = http.requireRequestContext(request);
        if (!contextResult.isSuccess()) {
            return Promise.of(http.errorResponse(contextResult.errorCode(), contextResult.errorMessage()));
        }

        String tenantId = contextResult.context().map(RequestContext::tenantId).orElse(null);
        String operationId = request.getPathParameter("operationId");
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header or authenticated tenant is required"));
        }
        if (operationId == null || operationId.isBlank()) {
            return Promise.of(http.errorResponse(400, "operationId path parameter is required"));
        }

        return Promise.of(operationRecorder.find(tenantId, operationId)
                .map(record -> http.jsonResponse(record.toResponse()))
                .orElseGet(() -> http.errorResponse(404, "Operation not found: " + operationId)));
    }

    public Promise<HttpResponse> handleCancelJob(HttpRequest request) {
        RequestContextResolver.ResolutionResult permissionResult = http.requirePermission(request, "operations:jobs:cancel");
        if (!permissionResult.isSuccess()) {
            return Promise.of(http.errorResponse(permissionResult.errorCode(), permissionResult.errorMessage()));
        }

        RequestContextResolver.ResolutionResult contextResult = http.requireRequestContext(request);
        if (!contextResult.isSuccess()) {
            return Promise.of(http.errorResponse(contextResult.errorCode(), contextResult.errorMessage()));
        }

        String tenantId = contextResult.context().map(RequestContext::tenantId).orElse(null);
        String operationId = request.getPathParameter("operationId");
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header or authenticated tenant is required"));
        }
        if (operationId == null || operationId.isBlank()) {
            return Promise.of(http.errorResponse(400, "operationId path parameter is required"));
        }

        try {
            OperationRecord current = operationRecorder.find(tenantId, operationId).orElse(null);
            if (current == null) {
                return Promise.of(http.errorResponse(404, "Operation not found: " + operationId));
            }
            if (!current.cancellable() || OperationRecord.terminal(current.status())) {
                return Promise.of(http.errorResponse(409, "Operation is not cancellable"));
            }
            OperationRecord cancelled = operationRecorder.transition(
                    tenantId,
                    operationId,
                    OperationStatus.CANCELLED,
                    "Operation cancellation requested by operator",
                    Map.of("cancelledAt", Instant.now().toString()));
            return Promise.of(http.jsonResponse(cancelled.toResponse()));
        } catch (IllegalArgumentException e) {
            return Promise.of(http.errorResponse(404, e.getMessage()));
        }
    }

    private static int parseLimit(String rawLimit) {
        if (rawLimit == null || rawLimit.isBlank()) {
            return 100;
        }
        try {
            return Math.max(1, Math.min(Integer.parseInt(rawLimit.trim()), 500));
        } catch (NumberFormatException ignored) {
            return 100;
        }
    }
}
