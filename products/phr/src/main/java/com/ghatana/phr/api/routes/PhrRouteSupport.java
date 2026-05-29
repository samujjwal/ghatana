package com.ghatana.phr.api.routes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.kernel.security.PolicyValidationHelper;
import com.ghatana.phr.api.validation.PhrRequestValidator;
import com.ghatana.phr.security.PhrPolicyEvaluator;
import com.ghatana.platform.http.server.activej.ActiveJHttpExchangeSupport;
import io.activej.bytebuf.ByteBuf;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;

import java.nio.charset.StandardCharsets;
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
        String persona = firstHeader(request, "X-Persona");
        String tier = firstHeader(request, "X-Tier");
        String facilityId = firstHeader(request, "X-Facility-ID", "X-Facility-Id");

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

        PolicyValidationHelper.validateTenantId(tenantId.strip());
        PolicyValidationHelper.validatePrincipalId(principalId.strip());
        if (facilityId != null && !facilityId.isBlank()) {
            PolicyValidationHelper.validateFacilityId(facilityId.strip());
        }

        String correlationId = extractCorrelationId(request);
        return new PhrRequestContext(
            tenantId.strip(),
            principalId.strip(),
            normalisedRole,
            correlationId,
            persona != null ? persona.strip() : normalisedRole, // Default persona to role if not provided
            tier != null ? tier.strip() : "core", // Default tier to core if not provided
            facilityId != null ? facilityId.strip() : null
        );
    }

    /**
     * Extracts the correlation ID from the inbound request headers.
     * Generates a server-side UUID if no correlation header is present.
     *
     * @param request the inbound HTTP request
     * @return the correlation ID string; never null
     */
    static String extractCorrelationId(HttpRequest request) {
        return ActiveJHttpExchangeSupport.correlationId(request);
    }



    static Promise<HttpResponse> jsonResponseWithCorrelation(int statusCode, Object body, String correlationId) {
        return Promise.of(ActiveJHttpExchangeSupport.jsonResponse(JSON, statusCode, body, correlationId));
    }

    static Promise<HttpResponse> jsonResponse(int statusCode, Object body) {
        return jsonResponseWithCorrelation(statusCode, body, null);
    }

    static Promise<HttpResponse> jsonResponse(int statusCode, Object body, String correlationId) {
        return jsonResponseWithCorrelation(statusCode, body, correlationId);
    }

    static Promise<HttpResponse> errorResponse(int statusCode, String code, String message) {
        return errorResponse(statusCode, code, message, null);
    }

    static Promise<HttpResponse> errorResponse(int statusCode, String code, String message, String correlationId) {
        return Promise.of(ActiveJHttpExchangeSupport.errorResponse(JSON, statusCode, code, message, correlationId));
    }

    static Promise<HttpResponse> errorResponse(int statusCode, String code, String message, String correlationId, Map<String, Object> details) {
        return Promise.of(ActiveJHttpExchangeSupport.errorResponse(JSON, statusCode, code, message, correlationId, details));
    }

    /**
     * Returns a safe policy denial response with only safe information.
     * Internal details are logged but not exposed to the client.
     *
     * @param statusCode the HTTP status code
     * @param correlationId the correlation ID for tracing
     * @return Promise containing the error response
     */
    static Promise<HttpResponse> policyDenialResponse(int statusCode, String correlationId) {
        return errorResponse(statusCode, "POLICY_DENIED", "Access denied by policy", correlationId);
    }

    /**
     * Validates facility scope for the request context.
     * Ensures the principal has access to the specified facility.
     *
     * @param context the request context
     * @param facilityId the facility ID to validate
     * @throws IllegalArgumentException if facility access is not permitted
     */
    static void validateFacilityScope(PhrRequestContext context, String facilityId) {
        // Admin and FCHV roles may have broader facility access
        String role = context.role();
        PolicyValidationHelper.validateFacilityScope(facilityId, role, "admin", "fchv");
        
        // For clinicians and caregivers, route handlers defer the final facility-scoped
        // PHI decision to PhrPolicyEvaluator.
    }

    /**
     * Validates tenant scope for the request context.
     * Ensures the principal belongs to the specified tenant.
     *
     * @param context the request context
     * @param tenantId the tenant ID to validate
     * @throws IllegalArgumentException if tenant access is not permitted
     */
    static void validateTenantScope(PhrRequestContext context, String tenantId) {
        PolicyValidationHelper.validateTenantScope(tenantId, context.tenantId());
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
        return ActiveJHttpExchangeSupport.idempotencyKey(request).orElse(null);
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
     * Validates an idempotency key format.
     *
     * @param key the idempotency key to validate
     * @return true if valid, false otherwise
     */
    static boolean isValidIdempotencyKey(String key) {
        return ActiveJHttpExchangeSupport.isValidIdempotencyKey(key);
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

    /**
     * B-018: Parses JSON body to a DTO and validates it using Bean Validation.
     *
     * @param body the HTTP request body as ByteBuf
     * @param dtoClass the target DTO class
     * @param dtoName the name of the DTO for error messages
     * @param <T> the DTO type
     * @return the validated DTO
     * @throws IllegalArgumentException if JSON parsing or validation fails
     */
    static <T> T parseAndValidateJson(ByteBuf body, Class<T> dtoClass, String dtoName) {
        String json = body.getString(StandardCharsets.UTF_8);
        try {
            T dto = JSON.readValue(json, dtoClass);
            PhrRequestValidator.validate(dto, dtoName);
            return dto;
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Invalid JSON in " + dtoName + ": " + ex.getMessage(), ex);
        }
    }

    /**
     * B-018: Parses JSON string to a DTO and validates it using Bean Validation.
     *
     * @param json the JSON string
     * @param dtoClass the target DTO class
     * @param dtoName the name of the DTO for error messages
     * @param <T> the DTO type
     * @return the validated DTO
     * @throws IllegalArgumentException if JSON parsing or validation fails
     */
    static <T> T parseAndValidateJson(String json, Class<T> dtoClass, String dtoName) {
        try {
            T dto = JSON.readValue(json, dtoClass);
            PhrRequestValidator.validate(dto, dtoName);
            return dto;
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Invalid JSON in " + dtoName + ": " + ex.getMessage(), ex);
        }
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
    public record PhrRequestContext(
        String tenantId,
        String principalId,
        String role,
        String correlationId,
        String persona,
        String tier,
        String facilityId
    ) {}
}
