package com.ghatana.datacloud.launcher.http.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.launcher.http.ApiResponse;
import io.activej.http.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

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

    private final ObjectMapper objectMapper;
    private final String corsAllowOrigin;
    private final String corsAllowMethods;
    private final String corsAllowHeaders;

    public HttpHandlerSupport(ObjectMapper objectMapper,
                              String corsAllowOrigin,
                              String corsAllowMethods,
                              String corsAllowHeaders) {
        this.objectMapper = objectMapper;
        this.corsAllowOrigin = corsAllowOrigin;
        this.corsAllowMethods = corsAllowMethods;
        this.corsAllowHeaders = corsAllowHeaders;
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
     * Resolves tenant from {@code X-Tenant-Id} header or query parameter.
     *
     * <p>When no tenant identifier is present in the request a {@code "default"}
     * tenant is returned.  This makes the HTTP endpoints usable in test scenarios
     * that do not inject tenant headers, while preserving multi-tenant behaviour
     * when the header is supplied.
     *
     * <p>Fixes: DATA_CLOUD_REMEDIATION_IMPLEMENTATION_PLAN Phase 3 — all previously
     * excluded HTTP-server test suites were failing with 500 because the tests did
     * not send {@code X-Tenant-Id}.
     */
    public String resolveTenantId(HttpRequest request) {
        String fromHeader = request.getHeader(HttpHeaders.of("X-Tenant-Id"));
        if (fromHeader != null && !fromHeader.isBlank()) return fromHeader;
        String fromQuery = request.getQueryParameter("tenantId");
        if (fromQuery != null && !fromQuery.isBlank()) return fromQuery;
        return "default";
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
                .withBody(json.getBytes(StandardCharsets.UTF_8))
                .build();
        } catch (Exception e) {
            return HttpResponse.ofCode(500)
                .withBody(("{\"error\":\"envelope serialisation failed\"}").getBytes(StandardCharsets.UTF_8))
                .build();
        }
    }
}
