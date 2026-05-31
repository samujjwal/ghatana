package com.ghatana.datacloud.launcher.http.handlers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.infrastructure.governance.http.dto.ErrorResponse;
import com.ghatana.datacloud.launcher.http.ApiResponse;
import com.ghatana.datacloud.launcher.http.RequestMetadataAttachment;
import com.ghatana.datacloud.launcher.http.RequestTraceSupport;
import com.ghatana.datacloud.launcher.http.security.RequestContext;
import com.ghatana.datacloud.launcher.http.security.RequestContextResolver;
import com.ghatana.datacloud.spi.ErrorEnvelope;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.http.security.filter.TenantExtractor;
import io.activej.http.*;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Shared HTTP helper methods for all handler classes extracted from
 * {@link com.ghatana.datacloud.launcher.http.DataCloudHttpServer}.
 *
 * <p>Provides JSON response builders, tenant resolution, parameter parsing,
 * and access to the CORS policy constants defined by the server.
 * @doc.type class
 * @doc.purpose Shared HTTP response and request utilities for handler classes
 * @doc.layer product
 * @doc.pattern Utility
 */
public class HttpHandlerSupport {

    private static final Pattern TENANT_ID_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9-]{1,62}[A-Za-z0-9]$");

    private final ObjectMapper objectMapper;
    private final String corsAllowOrigin;
    private final String corsAllowMethods;
    private final String corsAllowHeaders;
    private final boolean strictTenantResolution;
    private final String deploymentMode;
    private final Executor sharedBlockingExecutor;
    private static final java.util.Set<String> SAFE_FALLBACK_MODES = java.util.Set.of("local", "test", "development");
    private final RequestContextResolver requestContextResolver;

    public HttpHandlerSupport(ObjectMapper objectMapper,
                              String corsAllowOrigin,
                              String corsAllowMethods,
                              String corsAllowHeaders) {
        this(objectMapper, corsAllowOrigin, corsAllowMethods, corsAllowHeaders, false, "local");
    }

    public HttpHandlerSupport(ObjectMapper objectMapper,
                              String corsAllowOrigin,
                              String corsAllowMethods,
                              String corsAllowHeaders,
                              boolean strictTenantResolution) {
        this(objectMapper, corsAllowOrigin, corsAllowMethods, corsAllowHeaders, strictTenantResolution, "local");
    }

    public HttpHandlerSupport(ObjectMapper objectMapper,
                              String corsAllowOrigin,
                              String corsAllowMethods,
                              String corsAllowHeaders,
                              boolean strictTenantResolution,
                              String deploymentMode) {
        this.objectMapper           = objectMapper;
        this.corsAllowOrigin        = corsAllowOrigin;
        this.corsAllowMethods       = corsAllowMethods;
        this.corsAllowHeaders       = corsAllowHeaders;
        this.strictTenantResolution = strictTenantResolution;
        this.deploymentMode         = (deploymentMode == null || deploymentMode.isBlank()) ? "local" : deploymentMode;
        this.sharedBlockingExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.requestContextResolver = new RequestContextResolver(this.deploymentMode, strictTenantResolution);
    }

    /**
     * Resolves or generates a correlation / request ID for distributed tracing.
     *
     * <p>Checks {@code X-Request-Id} first, then {@code X-Correlation-Id}.
     * If neither is present, a new random UUID is returned.
     *
     * <p>Callers should pass the returned ID to {@link #jsonResponse(Map, String)}
     * and {@link #errorResponse(int, String, String)} so the ID propagates back
     * to clients on every response.
     *
     * @param request inbound HTTP request
     * @return correlation ID guaranteed to be non-null and non-blank
     */
    public String resolveCorrelationId(HttpRequest request) {
        RequestMetadataAttachment metadata = request.getAttachment(RequestMetadataAttachment.class);
        if (metadata != null && metadata.requestId() != null && !metadata.requestId().isBlank()) {
            return metadata.requestId();
        }
        String fromRequestId = request.getHeader(HttpHeaders.of("X-Request-Id"));
        if (fromRequestId != null && !fromRequestId.isBlank()) return fromRequestId.trim();
        String fromCorrelationId = request.getHeader(HttpHeaders.of("X-Correlation-Id"));
        if (fromCorrelationId != null && !fromCorrelationId.isBlank()) return fromCorrelationId.trim();
        return UUID.randomUUID().toString();
    }

    /**
     * Resolves the principal ID from the request headers.
     *
     * @deprecated Use {@link #resolveRequestContext(HttpRequest)} for production-grade authorization.
     * This method reads from headers directly and should not be used for authorization decisions.
     * @param request inbound HTTP request
     * @return principal ID, or null if not present
     */
    @Deprecated
    public String resolvePrincipalId(HttpRequest request) {
        String fromUserId = request.getHeader(HttpHeaders.of("X-User-ID"));
        if (fromUserId != null && !fromUserId.isBlank()) return fromUserId.trim();
        String fromPrincipal = request.getHeader(HttpHeaders.of("X-Principal-ID"));
        if (fromPrincipal != null && !fromPrincipal.isBlank()) return fromPrincipal.trim();
        return null;
    }

    /**
     * DC-P0-01: Resolves the distributed trace context from the request.
     * Extracts the traceparent header (W3C trace context format) or falls back to correlation ID.
     *
     * @param request inbound HTTP request
     * @return trace context ID, or null if not present
     */
    public String resolveTraceContext(HttpRequest request) {
        // W3C traceparent format: trace-id-span-id-trace-flags
        String traceParent = request.getHeader(HttpHeaders.of("traceparent"));
        if (traceParent != null && !traceParent.isBlank()) {
            String[] parts = traceParent.split("-");
            if (parts.length >= 2) {
                return parts[1]; // Return trace-id portion
            }
        }
        // Fallback to X-Trace-Id header
        String fromTraceId = request.getHeader(HttpHeaders.of("X-Trace-Id"));
        if (fromTraceId != null && !fromTraceId.isBlank()) return fromTraceId.trim();
        // Fallback to correlation ID
        return resolveCorrelationId(request);
    }

    /**
     * Builds a 200 OK JSON response with CORS headers.
     */
    public HttpResponse jsonResponse(Map<String, Object> data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            return RequestTraceSupport.applyTo(HttpResponse.ok200())
                .withHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValue.ofContentType(ContentType.of(MediaTypes.JSON)))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"),  HttpHeaderValue.of(corsAllowOrigin))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Methods"), HttpHeaderValue.of(corsAllowMethods))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Headers"), HttpHeaderValue.of(corsAllowHeaders))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Credentials"), HttpHeaderValue.of("true"))
                .withBody(json.getBytes(StandardCharsets.UTF_8))
                .build();
        } catch (Exception e) {
            return RequestTraceSupport.applyTo(HttpResponse.ofCode(500))
                .withBody(("{\"error\":\"" + e.getMessage() + "\"}").getBytes(StandardCharsets.UTF_8))
                .build();
        }
    }

    /**
     * Builds a 200 OK JSON response with CORS headers and an {@code X-Request-Id}
     * header set to {@code correlationId}.
     */
    public HttpResponse jsonResponse(Map<String, Object> data, String correlationId) {
        try {
            String json = objectMapper.writeValueAsString(data);
            return RequestTraceSupport.applyTo(HttpResponse.ok200())
                .withHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValue.ofContentType(ContentType.of(MediaTypes.JSON)))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"),  HttpHeaderValue.of(corsAllowOrigin))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Methods"), HttpHeaderValue.of(corsAllowMethods))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Headers"), HttpHeaderValue.of(corsAllowHeaders))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Credentials"), HttpHeaderValue.of("true"))
                .withHeader(HttpHeaders.of("X-Request-Id"), HttpHeaderValue.of(correlationId))
                .withBody(json.getBytes(StandardCharsets.UTF_8))
                .build();
        } catch (Exception e) {
            return RequestTraceSupport.applyTo(HttpResponse.ofCode(500))
                .withHeader(HttpHeaders.of("X-Request-Id"), HttpHeaderValue.of(correlationId))
                .withBody(("{\"error\":\"" + e.getMessage() + "\"}").getBytes(StandardCharsets.UTF_8))
                .build();
        }
    }

    /**
     * Builds a 201 Created JSON response with CORS headers.
     */
    public HttpResponse createdResponse(Map<String, Object> data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            return RequestTraceSupport.applyTo(HttpResponse.ofCode(201))
                .withHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValue.ofContentType(ContentType.of(MediaTypes.JSON)))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"),  HttpHeaderValue.of(corsAllowOrigin))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Methods"), HttpHeaderValue.of(corsAllowMethods))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Headers"), HttpHeaderValue.of(corsAllowHeaders))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Credentials"), HttpHeaderValue.of("true"))
                .withBody(json.getBytes(StandardCharsets.UTF_8))
                .build();
        } catch (Exception e) {
            return RequestTraceSupport.applyTo(HttpResponse.ofCode(500))
                .withBody(("{\"error\":\"" + e.getMessage() + "\"}").getBytes(StandardCharsets.UTF_8))
                .build();
        }
    }

    /**
     * Builds a 204 No Content response with CORS headers.
     */
    public HttpResponse noContentResponse() {
        return RequestTraceSupport.applyTo(HttpResponse.ofCode(204))
            .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"),  HttpHeaderValue.of(corsAllowOrigin))
            .withHeader(HttpHeaders.of("Access-Control-Allow-Methods"), HttpHeaderValue.of(corsAllowMethods))
            .withHeader(HttpHeaders.of("Access-Control-Allow-Headers"), HttpHeaderValue.of(corsAllowHeaders))
            .withHeader(HttpHeaders.of("Access-Control-Allow-Credentials"), HttpHeaderValue.of("true"))
            .build();
    }

    /**
     * Builds a JSON response with the given HTTP status code and data.
     *
     * @param statusCode the HTTP status code
     * @param data the response data to serialize as JSON
     * @return HTTP response with JSON body
     */
    public HttpResponse jsonResponse(int statusCode, Map<String, Object> data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            return RequestTraceSupport.applyTo(HttpResponse.ofCode(statusCode))
                .withHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValue.ofContentType(ContentType.of(MediaTypes.JSON)))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"),  HttpHeaderValue.of(corsAllowOrigin))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Methods"), HttpHeaderValue.of(corsAllowMethods))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Headers"), HttpHeaderValue.of(corsAllowHeaders))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Credentials"), HttpHeaderValue.of("true"))
                .withBody(json.getBytes(StandardCharsets.UTF_8))
                .build();
        } catch (Exception e) {
            return RequestTraceSupport.applyTo(HttpResponse.ofCode(500))
                .withBody(("{\"error\":\"json serialization failed\"}").getBytes(StandardCharsets.UTF_8))
                .build();
        }
    }

    public HttpResponse jsonBodyResponse(Object data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            return RequestTraceSupport.applyTo(HttpResponse.ok200())
                .withHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValue.ofContentType(ContentType.of(MediaTypes.JSON)))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"),  HttpHeaderValue.of(corsAllowOrigin))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Methods"), HttpHeaderValue.of(corsAllowMethods))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Headers"), HttpHeaderValue.of(corsAllowHeaders))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Credentials"), HttpHeaderValue.of("true"))
                .withBody(json.getBytes(StandardCharsets.UTF_8))
                .build();
        } catch (Exception e) {
            return RequestTraceSupport.applyTo(HttpResponse.ofCode(500))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"),  HttpHeaderValue.of(corsAllowOrigin))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Methods"), HttpHeaderValue.of(corsAllowMethods))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Headers"), HttpHeaderValue.of(corsAllowHeaders))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Credentials"), HttpHeaderValue.of("true"))
                .withBody(("{\"error\":\"json serialization failed\"}").getBytes(StandardCharsets.UTF_8))
                .build();
        }
    }

    /**
     * Returns true if the current deployment mode allows the default-tenant fallback.
     */
    public boolean isFallbackTenantAllowed() {
        return SAFE_FALLBACK_MODES.contains(deploymentMode);
    }

    /**
     * Returns the deployment mode label configured for this HTTP surface.
     */
    public String getDeploymentMode() {
        return deploymentMode;
    }

    /**
     * Returns the deployment profile label configured for this HTTP surface.
     */
    public String deploymentProfile() {
        return deploymentMode;
    }

    /**
     * Adds a {@code X-Fallback-Tenant-Warning} header to an existing response builder
     * when the tenant was resolved via the default fallback.
     */
    public HttpResponse.Builder applyFallbackBannerIfNeeded(HttpResponse.Builder builder, String tenantId) {
        if ("default".equals(tenantId) && isFallbackTenantAllowed()) {
            return builder.withHeader(HttpHeaders.of("X-Fallback-Tenant-Warning"), HttpHeaderValue.of("true"));
        }
        return builder;
    }

    /**
     * Returns a blocking executor for async operations.
     *
     * @return Executor for blocking operations
     */
    public java.util.concurrent.Executor blockingExecutor() {
        return sharedBlockingExecutor;
    }

    /**
     * Builds an error response with the given HTTP status code and message.
     * Uses data-cloud ErrorResponse for consistent error semantics.
     */
    public HttpResponse errorResponse(int code, String message) {
        return errorResponse(code, message, null);
    }

    /**
     * Builds an error response with the given HTTP status code, message, and
     * an {@code X-Request-Id} header set to {@code correlationId}.
     * Uses data-cloud ErrorResponse for consistent error semantics.
     */
    public HttpResponse errorResponse(int code, String message, String correlationId) {
        String traceId = correlationId != null && !correlationId.isBlank()
            ? correlationId
            : UUID.randomUUID().toString();
        try {
            String errorCode;
            switch (code) {
                case 400:
                    errorCode = "BAD_REQUEST";
                    break;
                case 401:
                    errorCode = "UNAUTHORIZED";
                    break;
                case 403:
                    errorCode = "FORBIDDEN";
                    break;
                case 404:
                    errorCode = "NOT_FOUND";
                    break;
                case 409:
                    errorCode = "CONFLICT";
                    break;
                case 429:
                    errorCode = "RATE_LIMIT_EXCEEDED";
                    break;
                case 500:
                    errorCode = "INTERNAL_SERVER_ERROR";
                    break;
                case 501:
                    errorCode = "NOT_IMPLEMENTED";
                    break;
                case 503:
                    errorCode = "SERVICE_UNAVAILABLE";
                    break;
                case 504:
                    errorCode = "GATEWAY_TIMEOUT";
                    break;
                default:
                    errorCode = "ERROR";
            }
            ErrorResponse errorResponse = ErrorResponse.builder()
                .status(code)
                .error(errorCode)
                .message(message)
                .timestamp(System.currentTimeMillis())
                .traceId(traceId)
                .build();
            String json = objectMapper.writeValueAsString(errorResponse);
            return RequestTraceSupport.applyTo(HttpResponse.ofCode(code))
                .withHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValue.ofContentType(ContentType.of(MediaTypes.JSON)))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"),  HttpHeaderValue.of(corsAllowOrigin))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Methods"), HttpHeaderValue.of(corsAllowMethods))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Headers"), HttpHeaderValue.of(corsAllowHeaders))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Credentials"), HttpHeaderValue.of("true"))
                .withHeader(HttpHeaders.of("X-Request-Id"), HttpHeaderValue.of(traceId))
                .withBody(json.getBytes(StandardCharsets.UTF_8))
                .build();
        } catch (JsonProcessingException e) {
            return RequestTraceSupport.applyTo(HttpResponse.ofCode(code))
                .withHeader(HttpHeaders.of("X-Request-Id"), HttpHeaderValue.of(traceId))
                .withBody(("{\"error\":\"" + message + "\", \"traceId\":\"" + traceId + "\"}").getBytes(StandardCharsets.UTF_8))
                .build();
        }
    }

    /**
     * Builds a 503 Service Unavailable response with a Retry-After header.
     *
     * @param message error message exposed to callers
     * @param retryAfterSeconds retry delay in seconds for RFC 7231 compliant backoff
     * @return HTTP 503 response with Retry-After and standard error envelope
     */
    public HttpResponse serviceUnavailableResponse(String message, int retryAfterSeconds) {
        String traceId = UUID.randomUUID().toString();
        try {
            ErrorResponse errorResponse = ErrorResponse.builder()
                .status(503)
                .error("SERVICE_UNAVAILABLE")
                .message(message)
                .timestamp(System.currentTimeMillis())
                .traceId(traceId)
                .build();
            String json = objectMapper.writeValueAsString(errorResponse);
            return RequestTraceSupport.applyTo(HttpResponse.ofCode(503))
                .withHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValue.ofContentType(ContentType.of(MediaTypes.JSON)))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"),  HttpHeaderValue.of(corsAllowOrigin))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Methods"), HttpHeaderValue.of(corsAllowMethods))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Headers"), HttpHeaderValue.of(corsAllowHeaders))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Credentials"), HttpHeaderValue.of("true"))
                .withHeader(HttpHeaders.of("X-Request-Id"), HttpHeaderValue.of(traceId))
                .withHeader(HttpHeaders.of("Retry-After"), HttpHeaderValue.of(Integer.toString(Math.max(1, retryAfterSeconds))))
                .withBody(json.getBytes(StandardCharsets.UTF_8))
                .build();
        } catch (JsonProcessingException e) {
            return RequestTraceSupport.applyTo(HttpResponse.ofCode(503))
                .withHeader(HttpHeaders.of("X-Request-Id"), HttpHeaderValue.of(traceId))
                .withHeader(HttpHeaders.of("Retry-After"), HttpHeaderValue.of(Integer.toString(Math.max(1, retryAfterSeconds))))
                .withBody(("{\"error\":\"" + message + "\", \"traceId\":\"" + traceId + "\"}").getBytes(StandardCharsets.UTF_8))
                .build();
        }
    }

    /**
     * Resolves tenant from authenticated identity only in production profiles.
     * In non-production profiles, falls back to X-Tenant-Id header or query parameter.
     *
     * <p><strong>Production Behavior:</strong>
     * <ul>
     *   <li>X-Tenant-Id header is REJECTED (returns null, triggers 403)</li>
     *   <li>tenantId query parameter is REJECTED (returns null, triggers 403)</li>
     *   <li>Tenant MUST come from authenticated Principal (JWT/API key)</li>
     * </ul>
     *
     * <p><strong>Recommended:</strong> Use {@link #resolveRequestContext(HttpRequest)} for new code
     * to get the full authenticated context including roles and audit trail.
     *
     * @param request inbound HTTP request
     * @return Resolution result with either tenant ID or error code/message
     */
    public TenantResolutionResult requireTenantIdWithError(HttpRequest request) {
        RequestContextResolver.ResolutionResult result = requestContextResolver.resolve(request);

        if (result.isSuccess()) {
            String tenantId = result.context()
                .map(RequestContext::tenantId)
                .orElse(null);

            if (tenantId == null) {
                return TenantResolutionResult.error(401,
                    "Unable to resolve tenant from authenticated request. " +
                    "Ensure JWT/API key includes tenant claim.");
            }

            return TenantResolutionResult.success(tenantId, result.context().get());
        }

        return TenantResolutionResult.error(result.errorCode(), result.errorMessage());
    }

    /**
     * Result of tenant resolution with optional full context.
     */
    public static final class TenantResolutionResult {
        private final String tenantId;
        private final RequestContext context;
        private final int errorCode;
        private final String errorMessage;
        private final boolean success;

        private TenantResolutionResult(String tenantId, RequestContext context, int errorCode, String errorMessage, boolean success) {
            this.tenantId = tenantId;
            this.context = context;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
            this.success = success;
        }

        public static TenantResolutionResult success(String tenantId, RequestContext context) {
            return new TenantResolutionResult(tenantId, context, 0, null, true);
        }

        public static TenantResolutionResult error(int code, String message) {
            return new TenantResolutionResult(null, null, code, message, false);
        }

        public boolean isSuccess() { return success; }
        public String tenantId() { return tenantId; }
        public RequestContext context() { return context; }
        public int errorCode() { return errorCode; }
        public String errorMessage() { return errorMessage; }
    }

    /**
     * Returns the tenant identifier when present without applying the default fallback tenant.
     */
    public String peekTenantId(HttpRequest request) {
        RequestMetadataAttachment metadata = request.getAttachment(RequestMetadataAttachment.class);
        if (metadata != null && metadata.tenantId() != null && !metadata.tenantId().isBlank()) {
            return sanitizeTenantId(metadata.tenantId());
        }
        return resolveTenantId(request);
    }

    /**
     * Returns whether strict tenant resolution is enabled for this HTTP surface.
     */
    public boolean isStrictTenantResolution() {
        return strictTenantResolution;
    }

    /**
     * Returns whether the request carries an explicit tenant identifier candidate before validation.
     * @deprecated Use {@link #resolveRequestContext(HttpRequest)} for production-grade resolution
     */
    @Deprecated
    public boolean hasExplicitTenantCandidate(HttpRequest request) {
        RequestMetadataAttachment metadata = request.getAttachment(RequestMetadataAttachment.class);
        if (metadata != null && metadata.tenantId() != null && !metadata.tenantId().isBlank()) {
            return true;
        }
        return findRawTenantCandidate(request) != null;
    }

    /**
     * Resolves the canonical authenticated RequestContext for the HTTP request.
     *
     * <p>This is the production-grade method for obtaining tenant/scope/authorization context.
     * It enforces:
     * <ul>
     *   <li>Rejection of X-Tenant-Id header in production profiles</li>
     *   <li>Rejection of tenantId query parameter in production profiles</li>
     *   <li>Tenant MUST come from authenticated identity in production</li>
     *   <li>Full audit trail for support/delegated access</li>
     * </ul>
     *
     * @param request the HTTP request
     * @return the resolved RequestContext, or null if resolution failed
     */
    public RequestContext resolveRequestContext(HttpRequest request) {
        RequestContextResolver.ResolutionResult result = requestContextResolver.resolve(request);
        if (result.isSuccess()) {
            return result.context().orElse(null);
        }
        return null;
    }

    /**
     * Resolves the canonical RequestContext or returns an error response if resolution fails.
     *
     * @param request the HTTP request
     * @return ResolutionResult containing either context or error details
     */
    public RequestContextResolver.ResolutionResult resolveRequestContextWithError(HttpRequest request) {
        return requestContextResolver.resolve(request);
    }

    /**
     * Requires the RequestContext to be present and valid for the request.
     * Returns an error response if resolution fails.
     *
     * @param request the HTTP request
     * @return ResolutionResult containing either context or error details
     */
    public RequestContextResolver.ResolutionResult requireRequestContext(HttpRequest request) {
        return requestContextResolver.resolve(request);
    }

    /**
     * Requires a specific permission to be present in the RequestContext.
     * Returns an error response if the permission is missing or context resolution fails.
     *
     * @param request the HTTP request
     * @param permission the required permission
     * @return ResolutionResult containing context if authorized, or error details if not
     */
    public RequestContextResolver.ResolutionResult requirePermission(HttpRequest request, String permission) {
        RequestContextResolver.ResolutionResult result = requestContextResolver.resolve(request);
        if (!result.isSuccess()) {
            return result;
        }

        RequestContext context = result.context().orElse(null);
        if (context == null || !context.hasPermission(permission)) {
            return RequestContextResolver.ResolutionResult.error(403,
                "Permission required: " + permission + ". Access denied.");
        }

        return result;
    }

    /**
     * Requires at least one of the specified permissions to be present in the RequestContext.
     * Returns an error response if none of the permissions are present or context resolution fails.
     *
     * @param request the HTTP request
     * @param permissions the set of required permissions (any one is sufficient)
     * @return ResolutionResult containing context if authorized, or error details if not
     */
    public RequestContextResolver.ResolutionResult requireAnyPermission(HttpRequest request, java.util.Set<String> permissions) {
        RequestContextResolver.ResolutionResult result = requestContextResolver.resolve(request);
        if (!result.isSuccess()) {
            return result;
        }

        RequestContext context = result.context().orElse(null);
        if (context == null) {
            return RequestContextResolver.ResolutionResult.error(403, "Unable to resolve request context. Access denied.");
        }

        if (!RequestContextResolver.hasAnyPermission(context, permissions)) {
            return RequestContextResolver.ResolutionResult.error(403,
                "One of the following permissions required: " + permissions + ". Access denied.");
        }

        return result;
    }

    /**
     * Requires all of the specified permissions to be present in the RequestContext.
     * Returns an error response if any permission is missing or context resolution fails.
     *
     * @param request the HTTP request
     * @param permissions the set of required permissions (all must be present)
     * @return ResolutionResult containing context if authorized, or error details if not
     */
    public RequestContextResolver.ResolutionResult requireAllPermissions(HttpRequest request, java.util.Set<String> permissions) {
        RequestContextResolver.ResolutionResult result = requestContextResolver.resolve(request);
        if (!result.isSuccess()) {
            return result;
        }

        RequestContext context = result.context().orElse(null);
        if (context == null) {
            return RequestContextResolver.ResolutionResult.error(403, "Unable to resolve request context. Access denied.");
        }

        if (!RequestContextResolver.hasAllPermissions(context, permissions)) {
            return RequestContextResolver.ResolutionResult.error(403,
                "All of the following permissions required: " + permissions + ". Access denied.");
        }

        return result;
    }

    /**
     * Requires a specific role to be present in the RequestContext.
     * Returns an error response if the role is missing or context resolution fails.
     *
     * @param request the HTTP request
     * @param role the required role
     * @return ResolutionResult containing context if authorized, or error details if not
     */
    public RequestContextResolver.ResolutionResult requireRole(HttpRequest request, String role) {
        RequestContextResolver.ResolutionResult result = requestContextResolver.resolve(request);
        if (!result.isSuccess()) {
            return result;
        }

        RequestContext context = result.context().orElse(null);
        if (context == null || !context.hasRole(role)) {
            return RequestContextResolver.ResolutionResult.error(403,
                "Role required: " + role + ". Access denied.");
        }

        return result;
    }

    /**
     * Requires at least one of the specified roles to be present in the RequestContext.
     * Returns an error response if none of the roles are present or context resolution fails.
     *
     * @param request the HTTP request
     * @param roles the set of required roles (any one is sufficient)
     * @return ResolutionResult containing context if authorized, or error details if not
     */
    public RequestContextResolver.ResolutionResult requireAnyRole(HttpRequest request, java.util.Set<String> roles) {
        RequestContextResolver.ResolutionResult result = requestContextResolver.resolve(request);
        if (!result.isSuccess()) {
            return result;
        }

        RequestContext context = result.context().orElse(null);
        if (context == null) {
            return RequestContextResolver.ResolutionResult.error(403, "Unable to resolve request context. Access denied.");
        }

        if (!context.hasAnyRole(roles.toArray(new String[0]))) {
            return RequestContextResolver.ResolutionResult.error(403,
                "One of the following roles required: " + roles + ". Access denied.");
        }

        return result;
    }

    /**
     * Requires admin-level access (ADMIN or PLATFORM_ADMIN role).
     * Returns an error response if admin role is missing or context resolution fails.
     *
     * @param request the HTTP request
     * @return ResolutionResult containing context if authorized, or error details if not
     */
    public RequestContextResolver.ResolutionResult requireAdminAccess(HttpRequest request) {
        RequestContextResolver.ResolutionResult result = requestContextResolver.resolve(request);
        if (!result.isSuccess()) {
            return result;
        }

        RequestContext context = result.context().orElse(null);
        if (context == null || !context.hasAnyRole("ADMIN", "PLATFORM_ADMIN")) {
            return RequestContextResolver.ResolutionResult.error(403,
                "Admin access required. Access denied.");
        }

        return result;
    }

    /**
     * Checks if the current request has admin-level access without returning an error response.
     * Useful for conditional logic within handlers.
     *
     * @param request the HTTP request
     * @return true if the request has admin access, false otherwise
     */
    public boolean hasAdminAccess(HttpRequest request) {
        RequestContext context = resolveRequestContext(request);
        if (context == null) {
            return false;
        }
        return context.hasAnyRole("ADMIN", "PLATFORM_ADMIN");
    }

    /**
     * Checks if the current request has a specific permission without returning an error response.
     * Useful for conditional logic within handlers.
     *
     * @param request the HTTP request
     * @param permission the permission to check
     * @return true if the request has the permission, false otherwise
     */
    public boolean hasPermission(HttpRequest request, String permission) {
        RequestContext context = resolveRequestContext(request);
        if (context == null) {
            return false;
        }
        return RequestContextResolver.hasPermission(context, permission);
    }

    /**
     * Builds a 401 Unauthorized response with CORS headers.
     *
     * @param message error message exposed to callers
     * @param correlationId request correlation ID for tracing
     * @return HTTP 401 response with standard error envelope
     */
    public HttpResponse unauthorizedResponse(String message, String correlationId) {
        return errorResponse(401, message, correlationId);
    }

    /**
     * Builds a 403 Forbidden response with CORS headers.
     *
     * @param message error message exposed to callers
     * @param correlationId request correlation ID for tracing
     * @return HTTP 403 response with standard error envelope
     */
    public HttpResponse forbiddenResponse(String message, String correlationId) {
        return errorResponse(403, message, correlationId);
    }

    /**
     * Builds a 403 Forbidden response for policy denial with CORS headers.
     *
     * @param message error message exposed to callers
     * @param correlationId request correlation ID for tracing
     * @param policyId the policy that denied access
     * @return HTTP 403 response with standard error envelope and policy information
     */
    public HttpResponse policyDeniedResponse(String message, String correlationId, String policyId) {
        String traceId = correlationId != null && !correlationId.isBlank()
            ? correlationId
            : UUID.randomUUID().toString();
        try {
            ErrorResponse errorResponse = ErrorResponse.builder()
                .status(403)
                .error("POLICY_DENIED")
                .message(message)
                .timestamp(System.currentTimeMillis())
                .traceId(traceId)
                .build();
            String json = objectMapper.writeValueAsString(errorResponse);
            return RequestTraceSupport.applyTo(HttpResponse.ofCode(403))
                .withHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValue.ofContentType(ContentType.of(MediaTypes.JSON)))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"),  HttpHeaderValue.of(corsAllowOrigin))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Methods"), HttpHeaderValue.of(corsAllowMethods))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Headers"), HttpHeaderValue.of(corsAllowHeaders))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Credentials"), HttpHeaderValue.of("true"))
                .withHeader(HttpHeaders.of("X-Request-Id"), HttpHeaderValue.of(traceId))
                .withHeader(HttpHeaders.of("X-Policy-Id"), HttpHeaderValue.of(policyId))
                .withBody(json.getBytes(StandardCharsets.UTF_8))
                .build();
        } catch (JsonProcessingException e) {
            return RequestTraceSupport.applyTo(HttpResponse.ofCode(403))
                .withHeader(HttpHeaders.of("X-Request-Id"), HttpHeaderValue.of(traceId))
                .withHeader(HttpHeaders.of("X-Policy-Id"), HttpHeaderValue.of(policyId))
                .withBody(("{\"error\":\"" + message + "\", \"traceId\":\"" + traceId + "\"}").getBytes(StandardCharsets.UTF_8))
                .build();
        }
    }

    // ─── Canonical Security Error Responses ─────────────────────────────────────

    /**
     * Builds a 403 Forbidden response with PERMISSION_DENIED error code.
     *
     * @param permission the required permission that was denied
     * @param correlationId request correlation ID for tracing
     * @param routeId the route ID for context
     * @return HTTP 403 response with PERMISSION_DENIED error code
     */
    public HttpResponse permissionDeniedResponse(String permission, String correlationId, String routeId) {
        String traceId = correlationId != null && !correlationId.isBlank()
            ? correlationId
            : UUID.randomUUID().toString();
        String route = routeId != null ? routeId : "unknown";
        try {
            ErrorResponse errorResponse = ErrorResponse.builder()
                .status(403)
                .error("PERMISSION_DENIED")
                .message("Permission denied: " + permission)
                .timestamp(System.currentTimeMillis())
                .traceId(traceId)
                .build();
            String json = objectMapper.writeValueAsString(errorResponse);
            return RequestTraceSupport.applyTo(HttpResponse.ofCode(403))
                .withHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValue.ofContentType(ContentType.of(MediaTypes.JSON)))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"), HttpHeaderValue.of(corsAllowOrigin))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Methods"), HttpHeaderValue.of(corsAllowMethods))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Headers"), HttpHeaderValue.of(corsAllowHeaders))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Credentials"), HttpHeaderValue.of("true"))
                .withHeader(HttpHeaders.of("X-Request-Id"), HttpHeaderValue.of(traceId))
                .withHeader(HttpHeaders.of("X-Route-Id"), HttpHeaderValue.of(route))
                .withBody(json.getBytes(StandardCharsets.UTF_8))
                .build();
        } catch (JsonProcessingException e) {
            return RequestTraceSupport.applyTo(HttpResponse.ofCode(403))
                .withHeader(HttpHeaders.of("X-Request-Id"), HttpHeaderValue.of(traceId))
                .withHeader(HttpHeaders.of("X-Route-Id"), HttpHeaderValue.of(route))
                .withBody(("{\"error\":\"Permission denied\", \"traceId\":\"" + traceId + "\"}").getBytes(StandardCharsets.UTF_8))
                .build();
        }
    }

    /**
     * Builds a 401 Unauthorized response with AUTHENTICATION_REQUIRED error code.
     *
     * @param message error message exposed to callers
     * @param correlationId request correlation ID for tracing
     * @return HTTP 401 response with AUTHENTICATION_REQUIRED error code
     */
    public HttpResponse authenticationRequiredResponse(String message, String correlationId) {
        String traceId = correlationId != null && !correlationId.isBlank()
            ? correlationId
            : UUID.randomUUID().toString();
        try {
            ErrorResponse errorResponse = ErrorResponse.builder()
                .status(401)
                .error("AUTHENTICATION_REQUIRED")
                .message(message != null ? message : "Authentication required")
                .timestamp(System.currentTimeMillis())
                .traceId(traceId)
                .build();
            String json = objectMapper.writeValueAsString(errorResponse);
            return RequestTraceSupport.applyTo(HttpResponse.ofCode(401))
                .withHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValue.ofContentType(ContentType.of(MediaTypes.JSON)))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"), HttpHeaderValue.of(corsAllowOrigin))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Methods"), HttpHeaderValue.of(corsAllowMethods))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Headers"), HttpHeaderValue.of(corsAllowHeaders))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Credentials"), HttpHeaderValue.of("true"))
                .withHeader(HttpHeaders.of("X-Request-Id"), HttpHeaderValue.of(traceId))
                .withBody(json.getBytes(StandardCharsets.UTF_8))
                .build();
        } catch (JsonProcessingException e) {
            return RequestTraceSupport.applyTo(HttpResponse.ofCode(401))
                .withHeader(HttpHeaders.of("X-Request-Id"), HttpHeaderValue.of(traceId))
                .withBody(("{\"error\":\"Authentication required\", \"traceId\":\"" + traceId + "\"}").getBytes(StandardCharsets.UTF_8))
                .build();
        }
    }

    /**
     * Builds a 400 Bad Request response with TENANT_REQUIRED error code.
     *
     * @param message error message exposed to callers
     * @param correlationId request correlation ID for tracing
     * @return HTTP 400 response with TENANT_REQUIRED error code
     */
    public HttpResponse tenantRequiredResponse(String message, String correlationId) {
        String traceId = correlationId != null && !correlationId.isBlank()
            ? correlationId
            : UUID.randomUUID().toString();
        try {
            ErrorResponse errorResponse = ErrorResponse.builder()
                .status(400)
                .error("TENANT_REQUIRED")
                .message(message != null ? message : "Tenant identifier required")
                .timestamp(System.currentTimeMillis())
                .traceId(traceId)
                .build();
            String json = objectMapper.writeValueAsString(errorResponse);
            return RequestTraceSupport.applyTo(HttpResponse.ofCode(400))
                .withHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValue.ofContentType(ContentType.of(MediaTypes.JSON)))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"), HttpHeaderValue.of(corsAllowOrigin))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Methods"), HttpHeaderValue.of(corsAllowMethods))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Headers"), HttpHeaderValue.of(corsAllowHeaders))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Credentials"), HttpHeaderValue.of("true"))
                .withHeader(HttpHeaders.of("X-Request-Id"), HttpHeaderValue.of(traceId))
                .withBody(json.getBytes(StandardCharsets.UTF_8))
                .build();
        } catch (JsonProcessingException e) {
            return RequestTraceSupport.applyTo(HttpResponse.ofCode(400))
                .withHeader(HttpHeaders.of("X-Request-Id"), HttpHeaderValue.of(traceId))
                .withBody(("{\"error\":\"Tenant required\", \"traceId\":\"" + traceId + "\"}").getBytes(StandardCharsets.UTF_8))
                .build();
        }
    }

    /**
     * Builds a 403 Forbidden response with SUPPORT_DELEGATION_REQUIRED error code.
     *
     * @param message error message exposed to callers
     * @param correlationId request correlation ID for tracing
     * @return HTTP 403 response with SUPPORT_DELEGATION_REQUIRED error code
     */
    public HttpResponse supportDelegationRequiredResponse(String message, String correlationId) {
        String traceId = correlationId != null && !correlationId.isBlank()
            ? correlationId
            : UUID.randomUUID().toString();
        try {
            ErrorResponse errorResponse = ErrorResponse.builder()
                .status(403)
                .error("SUPPORT_DELEGATION_REQUIRED")
                .message(message != null ? message : "Support delegation token required")
                .timestamp(System.currentTimeMillis())
                .traceId(traceId)
                .build();
            String json = objectMapper.writeValueAsString(errorResponse);
            return RequestTraceSupport.applyTo(HttpResponse.ofCode(403))
                .withHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValue.ofContentType(ContentType.of(MediaTypes.JSON)))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"), HttpHeaderValue.of(corsAllowOrigin))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Methods"), HttpHeaderValue.of(corsAllowMethods))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Headers"), HttpHeaderValue.of(corsAllowHeaders))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Credentials"), HttpHeaderValue.of("true"))
                .withHeader(HttpHeaders.of("X-Request-Id"), HttpHeaderValue.of(traceId))
                .withBody(json.getBytes(StandardCharsets.UTF_8))
                .build();
        } catch (JsonProcessingException e) {
            return RequestTraceSupport.applyTo(HttpResponse.ofCode(403))
                .withHeader(HttpHeaders.of("X-Request-Id"), HttpHeaderValue.of(traceId))
                .withBody(("{\"error\":\"Support delegation required\", \"traceId\":\"" + traceId + "\"}").getBytes(StandardCharsets.UTF_8))
                .build();
        }
    }

    /**
     * @deprecated Use RequestContextResolver for production-grade tenant resolution
     */
    @Deprecated
    private String resolveTenantId(HttpRequest request) {
        // Delegate to canonical resolver
        RequestContextResolver.ResolutionResult result = requestContextResolver.resolve(request);
        if (result.isSuccess()) {
            return result.context().map(RequestContext::tenantId).orElse(null);
        }
        return null;
    }

    private static String findRawTenantCandidate(HttpRequest request) {
        String fromHeader = TenantExtractor.fromHttp(request).orElse(null);
        if (fromHeader != null) {
            return fromHeader;
        }

        String fromQuery = request.getQueryParameter("tenantId");
        if (fromQuery != null && !fromQuery.isBlank()) {
            return fromQuery;
        }

        return null;
    }

    private static String sanitizeTenantId(String rawTenantId) {
        String candidate = rawTenantId == null ? null : rawTenantId.trim();
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        if (candidate.length() < 3 || candidate.length() > 64) {
            return null;
        }
        if (!TENANT_ID_PATTERN.matcher(candidate).matches()) {
            return null;
        }
        return candidate;
    }

    /**
     * Parses an integer query param, returning {@code defaultValue} on null or parse failure.
     */
    public static int parseIntParam(String raw, int defaultValue) {
        if (raw == null || raw.isBlank()) return defaultValue;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Parses a limit parameter, returning {@code defaultValue} on null or parse failure.
     */
    public static int parseLimitParam(String param, int defaultValue) {
        if (param == null || param.isBlank()) return defaultValue;
        try {
            return Integer.parseInt(param.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Parses a long parameter, returning {@code defaultValue} on null or parse failure.
     */
    public static long parseLongParam(String param, long defaultValue) {
        if (param == null || param.isBlank()) return defaultValue;
        try {
            return Long.parseLong(param.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public ObjectMapper objectMapper() {
        return objectMapper;
    }

    public String corsAllowOrigin() {
        return corsAllowOrigin;
    }

    /**
     * Serialises an {@link ApiResponse} canonical envelope and returns it as an
     * HTTP response with appropriate status code: 200 for success, 400 for client errors.
     * CORS and Content-Type headers are set.
     *
     * @param envelope  the response envelope to serialise
     * @param mapper    Jackson ObjectMapper
     * @return HTTP response with JSON body (200 for success, 400 for errors)
     */
    public HttpResponse envelopeResponse(ApiResponse envelope, ObjectMapper mapper) {
        int statusCode = envelope.isSuccess() ? 200 : resolveEnvelopeErrorStatusCode(envelope);
        return errorEnvelopeResponse(envelope, mapper, statusCode);
    }

    private static int resolveEnvelopeErrorStatusCode(ApiResponse envelope) {
        ApiResponse.ErrorBody errorBody = envelope.getError();
        if (errorBody == null || errorBody.getCode() == null || errorBody.getCode().isBlank()) {
            return 400;
        }

        String code = errorBody.getCode().toUpperCase(Locale.ROOT);

        if (code.contains("RATE_LIMIT") || code.contains("TOO_MANY_REQUEST")) {
            return 429;
        }
        if (code.contains("UNAUTHORIZED") || code.contains("AUTHENTICATION") || code.contains("AUTH_REQUIRED")) {
            return 401;
        }
        if (code.contains("FORBIDDEN") || code.contains("PERMISSION") || code.contains("ACCESS_DENIED")
            || code.contains("INVALID_CONFIRMATION_TOKEN")) {
            return 403;
        }
        if (code.contains("NOT_FOUND") || code.contains("UNKNOWN_INTENT") || code.contains("MISSING_RESOURCE")) {
            return 404;
        }
        if (code.contains("CONFLICT") || code.contains("ALREADY_EXISTS") || code.contains("DUPLICATE")) {
            return 409;
        }
        if (code.contains("TIMEOUT")) {
            return 504;
        }
        if (code.contains("UNAVAILABLE") || code.contains("DEPENDENCY") || code.contains("MISCONFIGURED")
            || code.contains("PURGE_TOKEN_SECRET_REQUIRED")) {
            return 503;
        }
        if (code.contains("INTERNAL") || code.contains("EXCEPTION") || code.contains("SERVER_ERROR")) {
            return 500;
        }
        return 400;
    }

    /**
     * Serialises an {@link ApiResponse} canonical envelope and returns it as an
     * HTTP response with the specified status code. This method allows different
     * error types to return appropriate HTTP status codes (400, 404, 429, 500, etc.)
     * while preserving the structured error envelope.
     *
     * @param envelope  the response envelope to serialise
     * @param mapper    Jackson ObjectMapper
     * @param statusCode the HTTP status code to return
     * @return HTTP response with JSON body and specified status code
     */
    public HttpResponse errorEnvelopeResponse(ApiResponse envelope, ObjectMapper mapper, int statusCode) {
        try {
            String json = envelope.toJson(mapper);
            return RequestTraceSupport.applyTo(HttpResponse.ofCode(statusCode))
                .withHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValue.ofContentType(ContentType.of(MediaTypes.JSON)))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"),  HttpHeaderValue.of(corsAllowOrigin))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Methods"), HttpHeaderValue.of(corsAllowMethods))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Headers"), HttpHeaderValue.of(corsAllowHeaders))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Credentials"), HttpHeaderValue.of("true"))
                .withHeader(HttpHeaders.of("X-Request-Id"), HttpHeaderValue.of(envelope.getMeta().getRequestId()))
                .withBody(json.getBytes(StandardCharsets.UTF_8))
                .build();
        } catch (Exception e) {
            return RequestTraceSupport.applyTo(HttpResponse.ofCode(500))
                .withBody(("{\"error\":\"envelope serialisation failed\"}").getBytes(StandardCharsets.UTF_8))
                .build();
        }
    }

    /**
     * Extracts the workspace identifier from an HTTP request.
     *
     * <p>Reads the {@code X-Workspace-Id} header. Returns {@code null} when the
     * header is absent, blank, or fails the workspace-id length/format check
     * (1–64 non-whitespace characters).
     *
     * <p>Workspace is an optional sub-partition inside a tenant. Callers should
     * propagate the returned value into {@link com.ghatana.datacloud.spi.TenantContext}
     * via {@code withWorkspace(workspaceId)} when non-null.
     *
     * @param request inbound HTTP request
     * @return workspace ID if present and valid; {@code null} otherwise
     */
    public String extractWorkspaceId(HttpRequest request) {
        String raw = request.getHeader(HttpHeaders.of("X-Workspace-Id"));
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String candidate = raw.trim();
        if (candidate.length() < 1 || candidate.length() > 64) {
            return null;
        }
        return candidate;
    }

    // ─── P1-11: Canonical Error Envelope Methods ───────────────────────────────

    /**
     * P1-11: Builds an error response using the canonical error envelope.
     *
     * <p>This method uses the canonical ErrorEnvelope structure to ensure
     * consistent error reporting across all handlers.
     *
     * @param code HTTP status code
     * @param errorCode canonical error code (e.g., SURFACE_UNAVAILABLE)
     * @param message human-readable error message
     * @param correlationId request correlation ID for tracing
     * @param tenantId tenant ID for multi-tenant context
     * @param surface surface identifier (e.g., "data.entities.write")
     * @param retryable whether the error is retryable
     * @param details additional error details
     * @return HTTP response with canonical error envelope
     */
    public HttpResponse canonicalErrorResponse(int code, String errorCode, String message,
                                                String correlationId, String tenantId, String surface,
                                                boolean retryable, Map<String, Object> details) {
        String traceId = correlationId != null && !correlationId.isBlank()
            ? correlationId
            : UUID.randomUUID().toString();

        ErrorEnvelope envelope = ErrorEnvelope.builder()
            .code(errorCode)
            .message(message)
            .correlationId(traceId)
            .tenantId(tenantId)
            .surface(surface)
            .retryable(retryable)
            .details(details)
            .build();

        try {
            String json = objectMapper.writeValueAsString(envelope);
            return RequestTraceSupport.applyTo(HttpResponse.ofCode(code))
                .withHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValue.ofContentType(ContentType.of(MediaTypes.JSON)))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"), HttpHeaderValue.of(corsAllowOrigin))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Methods"), HttpHeaderValue.of(corsAllowMethods))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Headers"), HttpHeaderValue.of(corsAllowHeaders))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Credentials"), HttpHeaderValue.of("true"))
                .withHeader(HttpHeaders.of("X-Request-Id"), HttpHeaderValue.of(traceId))
                .withBody(json.getBytes(StandardCharsets.UTF_8))
                .build();
        } catch (JsonProcessingException e) {
            return RequestTraceSupport.applyTo(HttpResponse.ofCode(code))
                .withHeader(HttpHeaders.of("X-Request-Id"), HttpHeaderValue.of(traceId))
                .withBody(("{\"error\":\"" + message + "\", \"traceId\":\"" + traceId + "\"}").getBytes(StandardCharsets.UTF_8))
                .build();
        }
    }

    /**
     * P1-11: Builds a canonical error response with minimal parameters.
     *
     * @param code HTTP status code
     * @param errorCode canonical error code
     * @param message human-readable error message
     * @return HTTP response with canonical error envelope
     */
    public HttpResponse canonicalErrorResponse(int code, String errorCode, String message) {
        return canonicalErrorResponse(code, errorCode, message, null, null, null, false, null);
    }

    /**
     * P1-11: Builds a canonical error response with context.
     *
     * @param code HTTP status code
     * @param errorCode canonical error code
     * @param message human-readable error message
     * @param correlationId request correlation ID
     * @param tenantId tenant ID
     * @return HTTP response with canonical error envelope
     */
    public HttpResponse canonicalErrorResponse(int code, String errorCode, String message,
                                                String correlationId, String tenantId) {
        return canonicalErrorResponse(code, errorCode, message, correlationId, tenantId, null, false, null);
    }

    /**
     * Parses JSON body from HTTP request.
     *
     * @param request inbound HTTP request
     * @return parsed JSON as Map
     * @throws JsonProcessingException if JSON parsing fails
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> parseJsonBody(HttpRequest request) throws JsonProcessingException {
        var bodyBuf = request.getBody();
        if (bodyBuf == null || bodyBuf.readRemaining() == 0) {
            return Map.of();
        }
        return objectMapper.readValue(bodyBuf.getString(StandardCharsets.UTF_8), Map.class);
    }
}
