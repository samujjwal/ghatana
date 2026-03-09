package com.ghatana.eventprocessing.observability;

import com.ghatana.platform.observability.MetricsCollector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

/**
 * Observability handler for HTTP controller operations.
 *
 * <p>
 * <b>Purpose</b><br>
 * Centralizes metrics collection, structured logging, and context management
 * for HTTP controller endpoints (pattern CRUD, pipeline CRUD, health checks).
 * Provides request/response tracking with tenant isolation and distributed
 * tracing support.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * HttpControllerObservability obs = new HttpControllerObservability(metricsCollector);
 * obs.recordRequestStart("tenant-123", "POST", "/api/v1/patterns", "user-1");
 * try {
 *   // Controller logic
 *   obs.recordRequestSuccess("POST", "/api/v1/patterns", 150, 201);
 * } catch (Exception e) {
 *   obs.recordRequestError("POST", "/api/v1/patterns", e, 500);
 * } finally {
 *   obs.clearHttpContext();
 * }
 * }</pre>
 *
 * <p>
 * <b>Metrics Emitted</b><br>
 * - aep.http.request.count (tags: tenant_id, method, endpoint, status) - HTTP
 * request count - aep.http.request.errors (tags: tenant_id, method, endpoint,
 * error_type) - HTTP request failures - aep.http.request.latency (tags:
 * tenant_id, method, endpoint, status) - Request processing time -
 * aep.http.request.size (tags: tenant_id, method, endpoint) - Request body size
 * (bytes) - aep.http.response.size (tags: tenant_id, method, endpoint, status)
 * - Response body size (bytes)
 *
 * <p>
 * <b>Logging</b><br>
 * Uses SLF4J with Log4j2 backend. MDC context includes: - tenantId: Tenant
 * identifier - httpMethod: HTTP method (GET, POST, PUT, DELETE) - httpPath:
 * Request path - httpStatus: Response HTTP status - userId: User making request
 * (optional) - requestId: Unique request identifier for tracing - traceId:
 * Distributed trace identifier
 *
 * <p>
 * <b>Thread Safety</b><br>
 * Thread-safe via MetricsCollector abstraction. MDC is thread-local.
 *
 * <p>
 * <b>Architecture Role</b><br>
 * This is an observability adapter for HTTP layer. Consumed by
 * PatternController, PipelineController, and other REST endpoints. Integrates
 * with core/observability abstractions for metrics collection.
 *
 * @see MetricsCollector
 * @see PatternController
 * @see PipelineController
 *
 * @doc.type class
 * @doc.purpose Centralized observability handler for HTTP controller operations
 * @doc.layer product
 * @doc.pattern Observability Adapter
 */
@Slf4j
@RequiredArgsConstructor
public class HttpControllerObservability {

    private final MetricsCollector metricsCollector;

    private static final String HTTP_LAYER = "http";

    // ==================== HTTP Request Lifecycle ====================
    /**
     * Records the start of an HTTP request.
     *
     * <p>
     * Sets MDC context for distributed tracing across async boundaries. Must be
     * paired with recordRequestSuccess() or recordRequestError().
     *
     * @param tenantId the tenant identifier
     * @param method the HTTP method (GET, POST, PUT, DELETE)
     * @param path the request path (e.g., /api/v1/patterns)
     * @param userId the user making the request (optional, nullable)
     */
    public void recordRequestStart(String tenantId, String method, String path, String userId) {
        setHttpContext(tenantId, method, path, userId);
        log.debug(
                "HTTP request started: method={}, path={}, tenantId={}",
                method, path, tenantId);
    }

    /**
     * Records successful HTTP request completion.
     *
     * @param tenantId the tenant identifier
     * @param method the HTTP method
     * @param path the request path
     * @param durationMs the time taken to process request in milliseconds
     * @param statusCode the HTTP response status code (200, 201, etc.)
     */
    public void recordRequestSuccess(
            String tenantId, String method, String path, long durationMs, int statusCode) {
        metricsCollector.incrementCounter(
                "aep.http.request.count",
                "tenant_id", tenantId,
                "method", method,
                "endpoint", path,
                "status", String.valueOf(statusCode));
        metricsCollector.recordTimer(
                "aep.http.request.latency",
                durationMs,
                "tenant_id", tenantId,
                "method", method,
                "endpoint", path,
                "status", String.valueOf(statusCode));

        log.info(
                "HTTP request completed: method={}, path={}, status={}, duration={}ms",
                method, path, statusCode, durationMs);
    }

    /**
     * Records failed HTTP request.
     *
     * @param tenantId the tenant identifier
     * @param method the HTTP method
     * @param path the request path
     * @param error the exception that occurred
     * @param statusCode the HTTP error status code (400, 500, etc.)
     */
    public void recordRequestError(
            String tenantId, String method, String path, Exception error, int statusCode) {
        String errorType = error.getClass().getSimpleName();
        metricsCollector.incrementCounter(
                "aep.http.request.errors",
                "tenant_id", tenantId,
                "method", method,
                "endpoint", path,
                "error_type", errorType,
                "status", String.valueOf(statusCode));

        log.warn(
                "HTTP request failed: method={}, path={}, status={}, error={}",
                method, path, statusCode, errorType, error);
    }

    /**
     * Records HTTP request payload size.
     *
     * @param tenantId the tenant identifier
     * @param method the HTTP method
     * @param path the request path
     * @param sizeBytes the request body size in bytes
     */
    public void recordRequestSize(
            String tenantId, String method, String path, long sizeBytes) {
        metricsCollector.getMeterRegistry().gauge(
                "aep.http.request.size",
                io.micrometer.core.instrument.Tags.of(
                        "tenant_id", tenantId,
                        "method", method,
                        "endpoint", path),
                sizeBytes);
    }

    /**
     * Records HTTP response payload size.
     *
     * @param tenantId the tenant identifier
     * @param method the HTTP method
     * @param path the request path
     * @param statusCode the HTTP response status
     * @param sizeBytes the response body size in bytes
     */
    public void recordResponseSize(
            String tenantId, String method, String path, int statusCode, long sizeBytes) {
        metricsCollector.getMeterRegistry().gauge(
                "aep.http.response.size",
                io.micrometer.core.instrument.Tags.of(
                        "tenant_id", tenantId,
                        "method", method,
                        "endpoint", path,
                        "status", String.valueOf(statusCode)),
                sizeBytes);
    }

    // ==================== Pattern-Specific Endpoints ====================
    /**
     * Records pattern endpoint access (GET, POST, PUT, DELETE).
     *
     * @param tenantId the tenant identifier
     * @param operation the operation type (create, read, update, delete, list)
     * @param durationMs the operation time in milliseconds
     * @param statusCode the HTTP status
     */
    public void recordPatternEndpointAccess(
            String tenantId, String operation, long durationMs, int statusCode) {
        metricsCollector.incrementCounter(
                "aep.http.pattern.operations",
                "tenant_id", tenantId,
                "operation", operation,
                "status", String.valueOf(statusCode));
        metricsCollector.recordTimer(
                "aep.http.pattern.operation.latency",
                durationMs,
                "tenant_id", tenantId,
                "operation", operation);

        log.info(
                "Pattern endpoint accessed: operation={}, status={}, duration={}ms",
                operation, statusCode, durationMs);
    }

    // ==================== Pipeline-Specific Endpoints ====================
    /**
     * Records pipeline endpoint access (GET, POST, PUT, DELETE).
     *
     * @param tenantId the tenant identifier
     * @param operation the operation type (create, read, update, delete, list)
     * @param durationMs the operation time in milliseconds
     * @param statusCode the HTTP status
     */
    public void recordPipelineEndpointAccess(
            String tenantId, String operation, long durationMs, int statusCode) {
        metricsCollector.incrementCounter(
                "aep.http.pipeline.operations",
                "tenant_id", tenantId,
                "operation", operation,
                "status", String.valueOf(statusCode));
        metricsCollector.recordTimer(
                "aep.http.pipeline.operation.latency",
                durationMs,
                "tenant_id", tenantId,
                "operation", operation);

        log.info(
                "Pipeline endpoint accessed: operation={}, status={}, duration={}ms",
                operation, statusCode, durationMs);
    }

    // ==================== Error Categorization ====================
    /**
     * Records validation error (400 Bad Request).
     *
     * @param tenantId the tenant identifier
     * @param endpoint the endpoint that failed
     * @param validationError the validation error message
     */
    public void recordValidationError(String tenantId, String endpoint, String validationError) {
        metricsCollector.incrementCounter(
                "aep.http.validation.errors",
                "tenant_id", tenantId,
                "endpoint", endpoint);

        log.warn(
                "Validation error on endpoint {}: {}",
                endpoint, validationError);
    }

    /**
     * Records authentication/authorization error (401/403).
     *
     * @param tenantId the tenant identifier
     * @param endpoint the endpoint that failed
     * @param reason the reason (missing tenant, unauthorized access, etc.)
     */
    public void recordAuthError(String tenantId, String endpoint, String reason) {
        metricsCollector.incrementCounter(
                "aep.http.auth.errors",
                "tenant_id", tenantId,
                "endpoint", endpoint,
                "reason", reason);

        log.warn(
                "Auth error on endpoint {}: {}",
                endpoint, reason);
    }

    // ==================== MDC Context Management ====================
    /**
     * Sets HTTP context for MDC and distributed tracing.
     *
     * @param tenantId the tenant identifier
     * @param method the HTTP method
     * @param path the request path
     * @param userId the user making request (optional)
     */
    private void setHttpContext(String tenantId, String method, String path, String userId) {
        MDC.put("layer", HTTP_LAYER);
        MDC.put("tenantId", tenantId);
        MDC.put("httpMethod", method);
        MDC.put("httpPath", path);
        if (userId != null && !userId.isEmpty()) {
            MDC.put("userId", userId);
        }
    }

    /**
     * Clears HTTP context from MDC. Must be called in finally block to prevent
     * context leakage.
     */
    public void clearHttpContext() {
        MDC.remove("layer");
        MDC.remove("tenantId");
        MDC.remove("httpMethod");
        MDC.remove("httpPath");
        MDC.remove("userId");
        MDC.remove("httpStatus");
    }
}
