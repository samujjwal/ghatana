/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import io.activej.http.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

/**
 * Shared HTTP helper methods for AEP controllers.
 *
 * @doc.type class
 * @doc.purpose Shared JSON response and tenant resolution utilities
 * @doc.layer product
 * @doc.pattern Utility
 */
public final class HttpHelper {

    private static final ObjectMapper MAPPER = JsonUtils.getDefaultMapper();

    private HttpHelper() {}

    /**
     * Creates a 200 JSON response from a Map payload.
     */
    public static HttpResponse jsonResponse(Map<String, Object> data) {
        try {
            String json = MAPPER.writeValueAsString(data);
            return HttpResponse.ok200()
                .withHeader(HttpHeaders.CONTENT_TYPE,
                    HttpHeaderValue.ofContentType(ContentType.of(MediaTypes.JSON)))
                .withHeader(HttpHeaders.of("X-Content-Type-Options"), HttpHeaderValue.of("nosniff"))
                .withBody(json.getBytes(StandardCharsets.UTF_8))
                .build();
        } catch (Exception e) {
            return HttpResponse.ofCode(500)
                .withBody(("{\"error\":\"" + e.getMessage() + "\"}").getBytes(StandardCharsets.UTF_8))
                .build();
        }
    }

    /**
     * Creates an error JSON response with the given HTTP status code.
     */
    public static HttpResponse errorResponse(int code, String message) {
        try {
            String safeMessage = message != null
                ? message.replace("\\", "\\\\").replace("\"", "\\\"")
                : "error";
            String json = MAPPER.writeValueAsString(Map.of(
                "error", safeMessage,
                "code", code,
                "timestamp", Instant.now().toString()
            ));
            return HttpResponse.ofCode(code)
                .withHeader(HttpHeaders.CONTENT_TYPE,
                    HttpHeaderValue.ofContentType(ContentType.of(MediaTypes.JSON)))
                .withHeader(HttpHeaders.of("X-Content-Type-Options"), HttpHeaderValue.of("nosniff"))
                .withBody(json.getBytes(StandardCharsets.UTF_8))
                .build();
        } catch (Exception e) {
            return HttpResponse.ofCode(code)
                .withBody(("{\"error\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8))
                .build();
        }
    }

    /**
     * Resolves tenant ID from X-Tenant-Id header or tenantId query parameter.
     */
    public static String resolveTenantId(HttpRequest request) {
        String fromHeader = request.getHeader(HttpHeaders.of("X-Tenant-Id"));
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

    /**
     * Returns the shared ObjectMapper instance.
     */
    public static ObjectMapper mapper() {
        return MAPPER;
    }
}
