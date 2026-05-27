package com.ghatana.phr.api.routes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.phr.api.validation.PhrRequestValidator;
import com.ghatana.phr.security.PhrPolicyEvaluator;
import io.activej.bytebuf.ByteBuf;
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
public final class PhrRouteSupport {

    static final String CONTENT_JSON = "application/json";
    static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    /** Roles permitted to call PHR routes. */
    static final Set<String> ALLOWED_ROLES = Set.of("patient", "caregiver", "clinician", "admin", "fchv");

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
     * Returns true if the context holder has clinical read access (clinician or admin).
     *
     * <p>Deprecated: Use PhrPolicyEvaluator for policy-based access control.
     * This method is a role-based shortcut and should be replaced with proper policy checks.</p>
     *
     * @param context the validated request context
     * @return true for clinician or admin roles
     * @deprecated Use PhrPolicyEvaluator.canAccessPatientRecord with proper consent/treatment relationship checks
     */
    @Deprecated
    static boolean hasClinicalRole(PhrRequestContext context) {
        return PhrPolicyEvaluator.canAccessPatientRecord(context, null);
    }

    /**
     * Returns true if the context holder may perform administrative operations.
     *
     * <p>Deprecated: Use PhrPolicyEvaluator for policy-based access control.
     * This method is a role-based shortcut and should be replaced with proper policy checks.</p>
     *
     * @param context the validated request context
     * @return true only for admin role
     * @deprecated Use PhrPolicyEvaluator.canViewAuditTrail or specific policy methods
     */
    @Deprecated
    static boolean canPerformAdminOperation(PhrRequestContext context) {
        return PhrPolicyEvaluator.canViewAuditTrail(context);
    }

    /**
     * Determines whether the context holder may access a given patient record.
     *
     * <p>Delegates to PhrPolicyEvaluator for Kernel-backed policy evaluation.
     * Patients may only access their own record. Clinicians and admins may access any record.
     * Caregivers are provisionally granted access; consent must be verified in the service layer.
     *
     * @param context   the validated request context
     * @param patientId the target patient ID
     * @return true if access is provisionally allowed
     */
    static boolean canAccessPatientRecordForRole(PhrRequestContext context, String patientId) {
        return PhrPolicyEvaluator.canAccessPatientRecord(context, patientId);
    }

    /**
     * Validates that a tenant ID is properly formatted.
     * 
     * @param tenantId the tenant ID to validate
     * @throws IllegalArgumentException if the tenant ID is invalid
     */
    static void validateTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("Tenant ID cannot be blank");
        }
        // Tenant IDs should be alphanumeric with hyphens, 3-50 characters
        if (!tenantId.matches("^[a-zA-Z0-9-]{3,50}$")) {
            throw new IllegalArgumentException("Tenant ID must be 3-50 alphanumeric characters with hyphens");
        }
    }

    /**
     * Validates that a principal ID is properly formatted.
     * 
     * @param principalId the principal ID to validate
     * @throws IllegalArgumentException if the principal ID is invalid
     */
    static void validatePrincipalId(String principalId) {
        if (principalId == null || principalId.isBlank()) {
            throw new IllegalArgumentException("Principal ID cannot be blank");
        }
        // Principal IDs should be alphanumeric with hyphens/underscores, 3-100 characters
        if (!principalId.matches("^[a-zA-Z0-9_-]{3,100}$")) {
            throw new IllegalArgumentException("Principal ID must be 3-100 alphanumeric characters with hyphens/underscores");
        }
    }

    /**
     * Validates that a facility ID is properly formatted (if provided).
     * 
     * @param facilityId the facility ID to validate (may be null/blank for non-facility users)
     * @throws IllegalArgumentException if the facility ID is provided but invalid
     */
    static void validateFacilityId(String facilityId) {
        if (facilityId == null || facilityId.isBlank()) {
            return; // Optional for non-facility users
        }
        // Facility IDs should be alphanumeric with hyphens, 3-50 characters
        if (!facilityId.matches("^[a-zA-Z0-9-]{3,50}$")) {
            throw new IllegalArgumentException("Facility ID must be 3-50 alphanumeric characters with hyphens");
        }
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

    static Promise<HttpResponse> errorResponse(int statusCode, String code, String message, String correlationId, Map<String, Object> details) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", code);
        body.put("message", message);
        body.put("correlationId", correlationId);
        if (details != null && !details.isEmpty()) {
            body.put("details", details);
        }
        return jsonResponse(statusCode, body, correlationId);
    }

    static Promise<HttpResponse> textResponse(int statusCode, String text, String contentType) {
        return Promise.of(HttpResponse.ofCode(statusCode)
            .withHeader(HttpHeaders.of("Content-Type"), contentType)
            .withBody(ByteBuf.wrapForReading(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
            .build());
    }

    /**
     * Extracts and validates an idempotency key from the request headers.
     *
     * @param request the HTTP request
     * @return the idempotency key, or null if not present
     * @throws IllegalArgumentException if the key format is invalid
     */
    static String extractIdempotencyKey(HttpRequest request) {
        String key = request.getHeader(HttpHeaders.of("X-Idempotency-Key"));
        if (key == null || key.isBlank()) {
            return null;
        }
        // Validate format: UUID or alphanumeric string 8-64 chars
        if (!key.matches("^[a-zA-Z0-9\\-]{8,64}$")) {
            throw new IllegalArgumentException("Idempotency key must be 8-64 alphanumeric characters or UUID format");
        }
        return key;
    }

    /**
     * Parses and validates a request body into a DTO using Bean Validation.
     *
     * @param json the JSON string to parse
     * @param type the target DTO class
     * @param dtoName the name of the DTO for error messages
     * @param <T> the DTO type
     * @return the parsed and validated DTO
     * @throws IllegalArgumentException if parsing or validation fails
     */
    static <T> T parseAndValidate(String json, Class<T> type, String dtoName) {
        try {
            T dto = JSON.readValue(json, type);
            PhrRequestValidator.validate(dto, dtoName);
            return dto;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid " + dtoName + ": " + ex.getMessage(), ex);
        }
    }

    /**
     * Extracts the idempotency key from request headers.
     *
     * @param request the HTTP request
     * @return the idempotency key, or null if not present
     */
    static String getIdempotencyKey(HttpRequest request) {
        String key = request.getHeader(io.activej.http.HttpHeaders.of("X-Idempotency-Key"));
        if (key != null && !key.isBlank()) {
            return key.trim();
        }
        return null;
    }

    /**
     * Validates an idempotency key format.
     *
     * @param key the idempotency key to validate
     * @return true if valid, false otherwise
     */
    static boolean isValidIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        // Idempotency keys should be at least 16 characters and at most 255 characters
        // They should be URL-safe (alphanumeric, hyphen, underscore)
        return key.length() >= 16 && key.length() <= 255 
            && key.matches("^[a-zA-Z0-9_-]+$");
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

    public record PhrRequestContext(String tenantId, String principalId, String role, String correlationId) {}
}
