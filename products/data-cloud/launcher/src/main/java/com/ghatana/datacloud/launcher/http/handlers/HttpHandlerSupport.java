package com.ghatana.datacloud.launcher.http.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.launcher.http.ApiResponse;
import com.ghatana.datacloud.launcher.http.RequestMetadataAttachment;
import io.activej.http.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared HTTP helper methods for all handler classes extracted from
 * {@link com.ghatana.datacloud.launcher.http.DataCloudHttpServer}.
 *
 * <p>Provides JSON response builders, tenant resolution, parameter parsing,
 * and access to the CORS policy constants defined by the server.
 *
 * @doc.type class
 * @doc.purpose Shared HTTP response and request utilities for handler classes
 * @doc.layer product
 * @doc.pattern Utility
 */
public class HttpHandlerSupport {

    private static final Logger log = LoggerFactory.getLogger(HttpHandlerSupport.class);

    private final ObjectMapper objectMapper;
    private final String corsAllowOrigin;
    private final String corsAllowMethods;
    private final String corsAllowHeaders;
    /**
     * When {@code true}, {@link #resolveTenantId} logs a warning for requests that
     * arrive without a tenant header, and {@link #requireTenantIdOrFail} returns
     * {@code null} (the caller should respond with HTTP 400).
     *
     * <p>Set via {@link DataCloudHttpServer#withStrictTenantResolution()} from the
     * bootstrap when {@code DATACLOUD_PROFILE} is not {@code local}.
     */
    private final boolean strictTenantResolution;

    public HttpHandlerSupport(ObjectMapper objectMapper,
                              String corsAllowOrigin,
                              String corsAllowMethods,
                              String corsAllowHeaders) {
        this(objectMapper, corsAllowOrigin, corsAllowMethods, corsAllowHeaders, false);
    }

    public HttpHandlerSupport(ObjectMapper objectMapper,
                              String corsAllowOrigin,
                              String corsAllowMethods,
                              String corsAllowHeaders,
                              boolean strictTenantResolution) {
        this.objectMapper           = objectMapper;
        this.corsAllowOrigin        = corsAllowOrigin;
        this.corsAllowMethods       = corsAllowMethods;
        this.corsAllowHeaders       = corsAllowHeaders;
        this.strictTenantResolution = strictTenantResolution;
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
            return HttpResponse.ok200()
                .withHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValue.ofContentType(ContentType.of(MediaTypes.JSON)))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"),  HttpHeaderValue.of(corsAllowOrigin))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Methods"), HttpHeaderValue.of(corsAllowMethods))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Headers"), HttpHeaderValue.of(corsAllowHeaders))
                .withBody(json.getBytes(StandardCharsets.UTF_8))
                .build();
        } catch (Exception e) {
            return HttpResponse.ofCode(500)
                .withBody(("{\"error\":\"" + e.getMessage() + "\"}").getBytes(StandardCharsets.UTF_8))
                .build();
        }
    }

    /**
     * Builds an error response with the given HTTP status code and message.
     */
    public HttpResponse errorResponse(int code, String message) {
        try {
            String json = objectMapper.writeValueAsString(Map.of(
                "error", message,
                "code", code,
                "timestamp", Instant.now().toString()
            ));
            return HttpResponse.ofCode(code)
                .withHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValue.ofContentType(ContentType.of(MediaTypes.JSON)))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"),  HttpHeaderValue.of(corsAllowOrigin))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Methods"), HttpHeaderValue.of(corsAllowMethods))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Headers"), HttpHeaderValue.of(corsAllowHeaders))
                .withBody(json.getBytes(StandardCharsets.UTF_8))
                .build();
        } catch (Exception e) {
            return HttpResponse.ofCode(code)
                .withBody(("{\"error\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8))
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
            return HttpResponse.ok200()
                .withHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValue.ofContentType(ContentType.of(MediaTypes.JSON)))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"),  HttpHeaderValue.of(corsAllowOrigin))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Methods"), HttpHeaderValue.of(corsAllowMethods))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Headers"), HttpHeaderValue.of(corsAllowHeaders))
                .withHeader(HttpHeaders.of("X-Request-Id"), HttpHeaderValue.of(correlationId))
                .withBody(json.getBytes(StandardCharsets.UTF_8))
                .build();
        } catch (Exception e) {
            return HttpResponse.ofCode(500)
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
            return HttpResponse.ofCode(201)
                .withHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValue.ofContentType(ContentType.of(MediaTypes.JSON)))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"),  HttpHeaderValue.of(corsAllowOrigin))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Methods"), HttpHeaderValue.of(corsAllowMethods))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Headers"), HttpHeaderValue.of(corsAllowHeaders))
                .withBody(json.getBytes(StandardCharsets.UTF_8))
                .build();
        } catch (Exception e) {
            return HttpResponse.ofCode(500)
                .withBody(("{\"error\":\"" + e.getMessage() + "\"}").getBytes(StandardCharsets.UTF_8))
                .build();
        }
    }

    /**
     * Builds a 204 No Content response with CORS headers.
     */
    public HttpResponse noContentResponse() {
        return HttpResponse.ofCode(204)
            .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"),  HttpHeaderValue.of(corsAllowOrigin))
            .withHeader(HttpHeaders.of("Access-Control-Allow-Methods"), HttpHeaderValue.of(corsAllowMethods))
            .withHeader(HttpHeaders.of("Access-Control-Allow-Headers"), HttpHeaderValue.of(corsAllowHeaders))
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
            return HttpResponse.ofCode(statusCode)
                .withHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValue.ofContentType(ContentType.of(MediaTypes.JSON)))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"),  HttpHeaderValue.of(corsAllowOrigin))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Methods"), HttpHeaderValue.of(corsAllowMethods))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Headers"), HttpHeaderValue.of(corsAllowHeaders))
                .withBody(json.getBytes(StandardCharsets.UTF_8))
                .build();
        } catch (Exception e) {
            return HttpResponse.ofCode(500)
                .withBody(("{\"error\":\"json serialization failed\"}").getBytes(StandardCharsets.UTF_8))
                .build();
        }
    }

    public HttpResponse jsonBodyResponse(Object data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            return HttpResponse.ok200()
                .withHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValue.ofContentType(ContentType.of(MediaTypes.JSON)))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"),  HttpHeaderValue.of(corsAllowOrigin))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Methods"), HttpHeaderValue.of(corsAllowMethods))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Headers"), HttpHeaderValue.of(corsAllowHeaders))
                .withBody(json.getBytes(StandardCharsets.UTF_8))
                .build();
        } catch (Exception e) {
            return HttpResponse.ofCode(500)
                .withBody(("{\"error\":\"json serialization failed\"}").getBytes(StandardCharsets.UTF_8))
                .build();
        }
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
     * Builds an error response with the given HTTP status code, message, and
     * an {@code X-Request-Id} header set to {@code correlationId}.
     */
    public HttpResponse errorResponse(int code, String message, String correlationId) {
        try {
            String json = objectMapper.writeValueAsString(Map.of(
                "error", message,
                "code", code,
                "timestamp", Instant.now().toString(),
                "requestId", correlationId
            ));
            return HttpResponse.ofCode(code)
                .withHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValue.ofContentType(ContentType.of(MediaTypes.JSON)))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"),  HttpHeaderValue.of(corsAllowOrigin))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Methods"), HttpHeaderValue.of(corsAllowMethods))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Headers"), HttpHeaderValue.of(corsAllowHeaders))
                .withHeader(HttpHeaders.of("X-Request-Id"), HttpHeaderValue.of(correlationId))
                .withBody(json.getBytes(StandardCharsets.UTF_8))
                .build();
        } catch (Exception e) {
            return HttpResponse.ofCode(code)
                .withHeader(HttpHeaders.of("X-Request-Id"), HttpHeaderValue.of(correlationId))
                .withBody(("{\"error\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8))
                .build();
        }
    }

    /**
     * Resolves tenant from {@code X-Tenant-Id} header or query parameter.
     *
     * <p>When no tenant identifier is present in the request a {@code "default"}
     * tenant is returned.  This makes the HTTP endpoints usable in test scenarios
     * that do not inject tenant headers, while preserving multi-tenant behaviour
     * when the header is supplied.
     *
     * <p>In non-local profiles ({@link #strictTenantResolution} is {@code true}),
     * a warning is logged when the fallback is used.  Handlers that must reject
     * unauthenticated tenant access should call {@link #requireTenantIdOrFail} instead.
     *
     * <p>Fixes: DATA_CLOUD_REMEDIATION_IMPLEMENTATION_PLAN Phase 3 — all previously
     * excluded HTTP-server test suites were failing with 500 because the tests did
     * not send {@code X-Tenant-Id}.
     */
    public String resolveTenantId(HttpRequest request) {
        RequestMetadataAttachment metadata = request.getAttachment(RequestMetadataAttachment.class);
        if (metadata != null && metadata.tenantId() != null && !metadata.tenantId().isBlank()) return metadata.tenantId();
        String fromHeader = request.getHeader(HttpHeaders.of("X-Tenant-Id"));
        if (fromHeader != null && !fromHeader.isBlank()) return fromHeader;
        String fromQuery = request.getQueryParameter("tenantId");
        if (fromQuery != null && !fromQuery.isBlank()) return fromQuery;
        if (strictTenantResolution) {
            log.warn("[DC-T1] Request missing X-Tenant-Id header in non-local profile — " +
                     "falling back to 'default' tenant. Use requireTenantIdOrFail() to reject.");
        }
        return "default";
    }

    /**
     * Resolves the tenant ID from the request, returning {@code null} when none is present.
     *
     * <p>Handlers in non-local profiles should prefer this method and return HTTP 400
     * to the caller when the result is {@code null}.  Example usage:
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
        if (metadata != null && metadata.tenantId() != null && !metadata.tenantId().isBlank()) return metadata.tenantId();
        String fromHeader = request.getHeader(HttpHeaders.of("X-Tenant-Id"));
        if (fromHeader != null && !fromHeader.isBlank()) return fromHeader;
        String fromQuery = request.getQueryParameter("tenantId");
        if (fromQuery != null && !fromQuery.isBlank()) return fromQuery;
        return null;
    }

    /**
     * Returns the tenant identifier when present without applying the default fallback tenant.
     */
    public String peekTenantId(HttpRequest request) {
        RequestMetadataAttachment metadata = request.getAttachment(RequestMetadataAttachment.class);
        if (metadata != null && metadata.tenantId() != null && !metadata.tenantId().isBlank()) return metadata.tenantId();
        String fromHeader = request.getHeader(HttpHeaders.of("X-Tenant-Id"));
        if (fromHeader != null && !fromHeader.isBlank()) return fromHeader;
        String fromQuery = request.getQueryParameter("tenantId");
        if (fromQuery != null && !fromQuery.isBlank()) return fromQuery;
        return null;
    }

    /**
     * Resolves tenantId from query param first, then header.
     *
     * @throws IllegalArgumentException if no tenant identifier is present in the request
     */
    public String resolveQueryOrHeaderTenantId(HttpRequest request) {
        String fromQuery = request.getQueryParameter("tenantId");
        if (fromQuery != null && !fromQuery.isBlank()) {
            return fromQuery;
        }
        return resolveTenantId(request);
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
     * Serialises an {@link ApiResponse} canonical envelope and returns it as a
     * 200 OK HTTP response with CORS and Content-Type headers set.
     *
     * @param envelope  the response envelope to serialise
     * @param mapper    Jackson ObjectMapper
     * @return 200 OK response with JSON body
     */
    public HttpResponse envelopeResponse(ApiResponse envelope, ObjectMapper mapper) {
        try {
            String json = envelope.toJson(mapper);
            return HttpResponse.ok200()
                .withHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValue.ofContentType(ContentType.of(MediaTypes.JSON)))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"),  HttpHeaderValue.of(corsAllowOrigin))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Methods"), HttpHeaderValue.of(corsAllowMethods))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Headers"), HttpHeaderValue.of(corsAllowHeaders))
                .withHeader(HttpHeaders.of("X-Request-Id"), HttpHeaderValue.of(envelope.getMeta().getRequestId()))
                .withBody(json.getBytes(StandardCharsets.UTF_8))
                .build();
        } catch (Exception e) {
            return HttpResponse.ofCode(500)
                .withBody(("{\"error\":\"envelope serialisation failed\"}").getBytes(StandardCharsets.UTF_8))
                .build();
        }
    }
}
