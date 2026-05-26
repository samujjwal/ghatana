package com.ghatana.phr.api.routes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared helpers for PHR ActiveJ route adapters.
 *
 * @doc.type class
 * @doc.purpose Common JSON and identity handling for PHR route adapters
 * @doc.layer product
 * @doc.pattern Adapter Helper
 */
final class PhrRouteSupport {

    static final String CONTENT_JSON = "application/json";
    static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    private PhrRouteSupport() {}

    static PhrRequestContext requireContext(HttpRequest request) {
        String tenantId = firstHeader(request, "X-Tenant-ID", "X-Tenant-Id");
        String principalId = firstHeader(request, "X-Principal-ID", "X-Principal-Id");
        String role = firstHeader(request, "X-Role");
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("X-Tenant-ID header is required");
        }
        if (principalId == null || principalId.isBlank()) {
            throw new IllegalArgumentException("X-Principal-ID header is required");
        }
        return new PhrRequestContext(tenantId, principalId, role == null || role.isBlank() ? "patient" : role);
    }

    static boolean isPrivileged(PhrRequestContext context) {
        return "admin".equalsIgnoreCase(context.role());
    }

    static Promise<HttpResponse> jsonResponse(int statusCode, Object body) {
        try {
            String json = body instanceof String stringBody ? stringBody : JSON.writeValueAsString(body);
            return Promise.of(HttpResponse.ofCode(statusCode)
                .withHeader(HttpHeaders.CONTENT_TYPE, CONTENT_JSON)
                .withJson(json)
                .build());
        } catch (JsonProcessingException ex) {
            return Promise.of(HttpResponse.ofCode(500)
                .withHeader(HttpHeaders.CONTENT_TYPE, CONTENT_JSON)
                .withJson("{\"error\":\"SERIALIZATION_ERROR\",\"message\":\"Failed to serialize response\"}")
                .build());
        }
    }

    static Promise<HttpResponse> errorResponse(int statusCode, String code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", code);
        body.put("message", message);
        return jsonResponse(statusCode, body);
    }

    static String requiredQuery(HttpRequest request, String name) {
        String value = request.getQueryParameter(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " query parameter is required");
        }
        return value;
    }

    static int intQuery(HttpRequest request, String name, int defaultValue, int maxValue) {
        String value = request.getQueryParameter(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        int parsed = Integer.parseInt(value);
        if (parsed < 0 || parsed > maxValue) {
            throw new IllegalArgumentException(name + " must be between 0 and " + maxValue);
        }
        return parsed;
    }

    private static String firstHeader(HttpRequest request, String... names) {
        for (String name : names) {
            String value = request.getHeader(HttpHeaders.of(name));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    record PhrRequestContext(String tenantId, String principalId, String role) {}
}
