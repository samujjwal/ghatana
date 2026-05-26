package com.ghatana.phr.api.routes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Shared helpers for PHR ActiveJ route adapters.
 *
 * <p>Security contract:
 * <ul>
 *   <li>All inbound context headers are validated for presence, non-blank content,
 *       and role membership before any business logic executes.</li>
 *   <li>Unknown roles throw {@link IllegalArgumentException} with code
 *       {@code INVALID_ROLE}; callers' existing try/catch converts this to a 403.</li>
 *   <li>Header values are trimmed but not further sanitised; routes requiring
 *       domain-specific validation (UUID format etc.) must do so locally.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Common JSON and identity handling for PHR route adapters
 * @doc.layer product
 * @doc.pattern Adapter Helper
 */
final class PhrRouteSupport {

    static final String CONTENT_JSON = "application/json";
    static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    /**
     * Roles permitted to call PHR routes. Any inbound role not in this set is
     * rejected; no implicit default is assigned.
     */
    static final Set<String> ALLOWED_ROLES = Set.of("patient", "caregiver", "clinician", "admin");

    /**
     * Roles that have elevated (privileged) access beyond basic patient operations.
     */
    private static final Set<String> PRIVILEGED_ROLES = Set.of("admin", "clinician");

    private PhrRouteSupport() {}

    /**
     * Extracts and validates the request context from inbound security headers.
     *
     * <p>Validation rules (fail-closed):
     * <ol>
     *   <li>{@code X-Tenant-ID} must be present and non-blank.</li>
     *   <li>{@code X-Principal-ID} must be present and non-blank.</li>
     *   <li>{@code X-Role} must be present, non-blank, and a member of
     *       {@link #ALLOWED_ROLES}.</li>
     * </ol>
     *
     * <p>Throws {@link IllegalArgumentException} on any violation. Callers are
     * expected to catch and convert to an appropriate HTTP error response; the
     * existing route adapter pattern already does this via try/catch blocks.
     *
     * @param request the inbound HTTP request
     * @return a validated request context
     * @throws IllegalArgumentException if any header is absent, blank, or contains
     *                                  an unrecognised role
     */
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
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("X-Role header is required");
        }
        String normalisedRole = role.strip().toLowerCase();
        if (!ALLOWED_ROLES.contains(normalisedRole)) {
            throw new IllegalArgumentException("Unrecognised role: " + role);
        }
        return new PhrRequestContext(tenantId.strip(), principalId.strip(), normalisedRole);
    }

    static boolean isPrivileged(PhrRequestContext context) {
        return PRIVILEGED_ROLES.contains(context.role());
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
