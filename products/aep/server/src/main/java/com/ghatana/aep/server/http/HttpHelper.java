/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.platform.http.server.response.ErrorResponse;
import com.ghatana.platform.http.server.response.ResponseBuilder;
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
        String fromHeader = request.getHeader(io.activej.http.HttpHeaders.of("X-Tenant-Id"));
        if (fromHeader != null && !fromHeader.isBlank()) return fromHeader;
        String fromQuery = request.getQueryParameter("tenantId");
        return (fromQuery != null && !fromQuery.isBlank()) ? fromQuery : "default";
    }

    /**
     * Resolves tenant ID from request header/query, falling back to a payload map, then "default".
     */
    public static String resolveTenantId(HttpRequest request, Map<String, Object> payload) {
        String tenantId = request.getQueryParameter("tenantId");
        if ((tenantId == null || tenantId.isBlank()) && payload != null) {
            Object v = payload.get("tenantId");
            tenantId = v != null ? String.valueOf(v) : null;
        }
        return (tenantId == null || tenantId.isBlank()) ? "default" : tenantId;
    }
}
