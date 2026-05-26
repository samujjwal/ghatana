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
 *   <li>Unknown roles throw {@link IllegalArgumentException};</li>
 *   <li>Header values are trimmed but not further sanitised.</li>
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

    /** Roles permitted to call PHR routes. */
    static final Set<String> ALLOWED_ROLES = Set.of("patient", "caregiver", "clinician", "admin");

    /** Roles with elevated access beyond basic patient operations. */
    private static final Set<String> PRIVILEGED_ROLES = Set.of("admin", "clinician");

    private PhrRouteSupport() {}

    /**
     * Extracts and validates the request context from inbound security headers.
     *
     * @param request the inbound HTTP request
     * @return a validated request context
     * @throws IllegalArgumentException if any required header is absent, blank, or unrecognised
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
        String correlationId = extractCorrelationId(request);
        return new PhrRequestContext(tenantId.strip(), principalId.strip(), normalisedRole, correlationId);
    }

    /**
     * Extracts the correlation ID from the inbound request headers.
     * Returns a fallback string if no correlation header is present.
     *
     * @param request the inbound HTTP request
     * @return the correlation ID string; never null
     */
    static String extractCorrelationId(HttpRequest request) {
        String value = firstHeader(request, "X-Correlation-ID", "X-Correlation-Id", "X-Request-ID");
        return (value != null && !value.isBlank()) ? value.strip() : "no-correlation-id";
    }

    /**
     * Returns true if the context holder has elevated (clinician/admin) access.
     *
     * @deprecated Prefer explicit policy methods such as hasClinicalRole.
     */
    @Deprecated(forRemoval = true)
    static boolean isPrivileged(PhrRequestContext context) {
        return PRIVILEGED_ROLES.contains(context.role());
    }

    /**
     * Returns true if the context holder has clinical read access (clinician or admin).
     *
     * @param context the validated request context
     * @return true for clinician or admin roles
     */
    static boolean hasClinicalRole(PhrRequestContext context) {
        return "clinician".equals(context.role()) || "admin".equals(context.role());
    }

    /**
     * Returns true if the context holder may perform administrative operations.
     *
     * @param context the validated request context
     * @return true only for admin role
     */
    static boolean canPerformAdminOperation(PhrRequestContext context) {
        return "admin".equals(context.role());
    }

    /**
     * Determines whether the context holder may access a given patient record.
     *
     * <p>Patients may only access their own record. Clinicians and admins may access any record.
     * Caregivers are provisionally granted access; consent must be verified in the service layer.
     *
     * @param context   the validated request context
     * @param patientId the target patient ID
     * @return true if access is provisionally allowed
     */
    static boolean canAccessPatientRecordForRole(PhrRequestContext context, String patientId) {
        return switch (context.role()) {
            case "admin", "clinician" -> true;
            case "caregiver" -> true; // consent verified by service layer
            case "patient" -> context.principalId().equals(patientId);
            default -> false;
        };
    }

    static Promise<HttpResponse> jsonResponse(int statusCode, Object body) {
        try {
            String json = body instanceof String s ? s : JSON.writeValueAsString(body);
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

    static Promise<HttpResponse> jsonResponse(int statusCode, Object body, String correlationId) {
        try {
            String json = body instanceof String s ? s : JSON.writeValueAsString(body);
            return Promise.of(HttpResponse.ofCode(statusCode)
                .withHeader(HttpHeaders.CONTENT_TYPE, CONTENT_JSON)
                .withHeader(HttpHeaders.of("X-Correlation-ID"), correlationId)
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

    static Promise<HttpResponse> errorResponse(int statusCode, String code, String message, String correlationId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", code);
        body.put("message", message);
        body.put("correlationId", correlationId);
        return jsonResponse(statusCode, body, correlationId);
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

    record PhrRequestContext(String tenantId, String principalId, String role, String correlationId) {}
}
