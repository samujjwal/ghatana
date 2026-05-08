package com.ghatana.datacloud.launcher.http.handlers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.infrastructure.governance.http.dto.ErrorResponse;
import com.ghatana.datacloud.launcher.http.ApiResponse;
import com.ghatana.datacloud.launcher.http.RequestMetadataAttachment;
import com.ghatana.datacloud.launcher.http.RequestTraceSupport;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.http.security.filter.TenantExtractor;
import io.activej.http.*;

import java.nio.charset.StandardCharsets;
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
    private static final java.util.Set<String> SAFE_FALLBACK_MODES = java.util.Set.of("local", "test", "development");

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
        return java.util.concurrent.Executors.newCachedThreadPool();
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
    * Resolves tenant from {@code X-Tenant-Id} header or query parameter.
    *
    * Resolves the tenant ID from the request, returning {@code null} when none is present or
    * the supplied value fails the shared tenant identifier format validation.
     *
    * <p>Handlers in non-local profiles should prefer this method and reject {@code null}
    * results. Example usage:
     * <pre>{@code
     * String tenantId = http.requireTenantIdOrFail(request);
     * if (tenantId == null) {
     *     return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
     * }
     * }</pre>
     *
     * @param request inbound HTTP request
     * @return tenant ID if present; {@code null} if absent
     */
    public String requireTenantIdOrFail(HttpRequest request) {
        RequestMetadataAttachment metadata = request.getAttachment(RequestMetadataAttachment.class);
        if (metadata != null && metadata.tenantId() != null && !metadata.tenantId().isBlank()) {
            return sanitizeTenantId(metadata.tenantId());
        }
        return resolveTenantId(request);
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
     */
    public boolean hasExplicitTenantCandidate(HttpRequest request) {
        RequestMetadataAttachment metadata = request.getAttachment(RequestMetadataAttachment.class);
        if (metadata != null && metadata.tenantId() != null && !metadata.tenantId().isBlank()) {
            return true;
        }
        return findRawTenantCandidate(request) != null;
    }

    private String resolveTenantId(HttpRequest request) {
        Principal principal = request.getAttachment(Principal.class);
        if (principal != null && principal.getTenantId() != null && !principal.getTenantId().isBlank()) {
            return sanitizeTenantId(principal.getTenantId());
        }
        String candidate = findRawTenantCandidate(request);
        if (candidate != null) {
            return sanitizeTenantId(candidate);
        }
        // DC-AUD-014: Non-strict mode falls back to default tenant ONLY in safe modes.
        // Production / staging must never silently fall back.
        if (strictTenantResolution) return null;
        if (SAFE_FALLBACK_MODES.contains(deploymentMode)) return "default";
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
}
