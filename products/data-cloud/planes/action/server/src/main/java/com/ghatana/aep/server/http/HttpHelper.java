/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.platform.http.security.filter.TenantExtractor;
import com.ghatana.platform.http.server.response.ErrorResponse;
import com.ghatana.platform.http.server.response.ResponseBuilder;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;

import java.util.Map;

/**
 * Shared HTTP helper methods for AEP controllers.
 *
 * <p>Delegates to platform ResponseBuilder and ErrorResponse for JSON response construction.
 * Retains AEP-specific tenant resolution utilities.
 *
 * @doc.type class
 * @doc.purpose Shared JSON response and tenant resolution utilities (delegates to platform HTTP)
 * @doc.layer product
 * @doc.pattern Utility
 */
public final class HttpHelper {

    private static final ObjectMapper OBJECT_MAPPER = JsonUtils.getDefaultMapper();

    private HttpHelper() {}

    /**
     * Shared ObjectMapper for AEP HTTP controllers.
     */
    public static ObjectMapper mapper() {
        return OBJECT_MAPPER;
    }

    /**
     * Creates a 200 JSON response from a Map payload.
     * Delegates to platform ResponseBuilder.
     */
    public static HttpResponse jsonResponse(Map<String, Object> data) {
        return RequestTraceSupport.applyTo(ResponseBuilder.ok())
            .json(data)
            .build();
    }

    /**
     * Creates a JSON response with an explicit HTTP status code.
     * Use for non-200 success responses (e.g. 503 readiness failure with body).
     */
    public static HttpResponse jsonResponse(int status, Map<String, Object> data) {
        return RequestTraceSupport.applyTo(ResponseBuilder.status(status))
            .json(data)
            .build();
    }

    /**
     * Creates an error JSON response with the given HTTP status code.
     * Delegates to platform ResponseBuilder and ErrorResponse.
     */
    public static HttpResponse errorResponse(int code, String message) {
        return RequestTraceSupport.applyTo(ResponseBuilder.status(code))
            .json(ErrorResponse.of(code, message))
            .build();
    }

    /**
     * Creates an error JSON response with additional payload fields.
     * Delegates to platform ResponseBuilder and ErrorResponse.
     */
    public static HttpResponse errorResponse(int code, String message, Map<String, Object> fields) {
        ErrorResponse.ErrorResponseBuilder builder = ErrorResponse.builder()
            .status(code)
            .message(message);
        
        if (fields != null && !fields.isEmpty()) {
            builder.detailsMap(fields);
        }
        
        return RequestTraceSupport.applyTo(ResponseBuilder.status(code))
            .json(builder.build())
            .build();
    }

    /**
     * Resolves tenant ID from X-Tenant-Id header or tenantId query parameter.
     */
    public static String resolveTenantId(HttpRequest request) {
        String fromHeader = TenantExtractor.fromHttp(request).orElse(null);
        if (fromHeader != null) return fromHeader;
        String fromQuery = request.getQueryParameter("tenantId");
        return (fromQuery != null && !fromQuery.isBlank()) ? fromQuery : "default";
    }

    /**
     * Resolves tenant ID from request header/query, falling back to a payload map, then "default".
     */
    public static String resolveTenantId(HttpRequest request, Map<String, Object> payload) {
        String tenantId = TenantExtractor.fromHttp(request).orElse(null);
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = request.getQueryParameter("tenantId");
        }
        if ((tenantId == null || tenantId.isBlank()) && payload != null) {
            Object v = payload.get("tenantId");
            tenantId = v != null ? String.valueOf(v) : null;
        }
        return (tenantId == null || tenantId.isBlank()) ? "default" : tenantId;
    }

    // =========================================================================
    // T-17: Trusted Gateway Detection
    // =========================================================================

    /**
     * Checks if the request is coming from the trusted gateway (internal auth).
     * Gateway adds x-gateway-trusted: true header to all proxied requests.
     */
    public static boolean isTrustedGatewayRequest(HttpRequest request) {
        String trustedHeader = request.getHeader(HttpHeaders.of("x-gateway-trusted"));
        String sourceHeader = request.getHeader(HttpHeaders.of("x-gateway-source"));
        return "true".equalsIgnoreCase(trustedHeader) && "aep-gateway".equals(sourceHeader);
    }

    // =========================================================================
    // T-33: Standard Response Envelopes
    // =========================================================================

    /**
     * Standard list response envelope.
     *
     * @param items the list items
     * @param total total count (may be estimated as -1 if unknown)
     * @param page current page number (0-indexed)
     * @param pageSize items per page
     * @param nextCursor cursor for next page (null if no more items)
     */
    public static HttpResponse listEnvelope(
            Object items, long total, int page, int pageSize, String nextCursor) {
        Map<String, Object> envelope = new java.util.HashMap<>();
        envelope.put("items", items);
        envelope.put("total", total);
        envelope.put("page", page);
        envelope.put("pageSize", pageSize);
        if (nextCursor != null) {
            envelope.put("nextCursor", nextCursor);
        }
        return RequestTraceSupport.applyTo(ResponseBuilder.ok())
            .json(envelope)
            .build();
    }

    /**
     * Standard mutation response envelope.
     *
     * @param operationId unique operation identifier
     * @param status mutation status (SUCCESS, PENDING, FAILED)
     * @param resource resource type that was mutated
     * @param auditId audit trail entry ID
     */
    public static HttpResponse mutationEnvelope(
            String operationId, String status, String resource, String auditId) {
        Map<String, Object> envelope = new java.util.HashMap<>();
        envelope.put("operationId", operationId);
        envelope.put("status", status);
        envelope.put("resource", resource);
        if (auditId != null) {
            envelope.put("auditId", auditId);
        }
        return RequestTraceSupport.applyTo(ResponseBuilder.ok())
            .json(envelope)
            .build();
    }

    /**
     * Standard error response envelope with full structured details.
     *
     * @param code HTTP status code
     * @param message human-readable error message
     * @param errorCode application-specific error code
     * @param details additional error details
     * @param correlationId request correlation ID
     * @param retryable whether the operation can be retried
     */
    public static HttpResponse errorEnvelope(
            int code, String message, String errorCode,
            Map<String, Object> details, String correlationId, boolean retryable) {
        Map<String, Object> envelope = new java.util.HashMap<>();
        envelope.put("code", errorCode != null ? errorCode : String.valueOf(code));
        envelope.put("message", message);
        envelope.put("correlationId", correlationId != null ? correlationId : java.util.UUID.randomUUID().toString());
        envelope.put("retryable", retryable);
        if (details != null && !details.isEmpty()) {
            envelope.put("details", details);
        }
        return RequestTraceSupport.applyTo(ResponseBuilder.status(code))
            .json(envelope)
            .build();
    }
}
