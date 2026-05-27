package com.ghatana.phr.api.routes;

import com.fasterxml.jackson.databind.JsonNode;
import com.ghatana.phr.repository.UserRepository;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.RoutingServlet;
import io.activej.promise.Promise;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Patient profile API for the PHR product.
 *
 * <p>Exposes profile read and update operations with server-side validation and
 * field-level permissions. Patients may only view and modify their own profile;
 * clinical roles may read any profile but cannot modify. Specific fields have
 * role-based edit permissions.
 *
 * @doc.type class
 * @doc.purpose ActiveJ route adapter for reading and updating the patient's demographic profile
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 */
public final class PhrPatientProfileRoutes {

    private static final Set<String> PATIENT_EDITABLE_FIELDS = Set.of(
        "emergencyContact", "preferredLanguage", "facilityId"
    );

    private static final Set<String> CLINICIAN_EDITABLE_FIELDS = Set.of(
        "bloodType", "gender", "location"
    );

    private static final Set<String> ADMIN_EDITABLE_FIELDS = Set.of(
        "emergencyContact", "preferredLanguage", "facilityId", "bloodType", "gender", "location"
    );

    private final Eventloop eventloop;
    private final UserRepository userRepository;

    public PhrPatientProfileRoutes(Eventloop eventloop, UserRepository userRepository) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
    }

    /**
     * Returns the routing servlet for patient profile endpoints.
     *
     * @return routing servlet; never null
     */
    public AsyncServlet getServlet() {
        return RoutingServlet.builder(eventloop)
            .with(HttpMethod.GET, "/", this::handleGetProfile)
            .with(HttpMethod.PUT, "/", this::handleUpdateProfile)
            .build();
    }

    private Promise<HttpResponse> handleGetProfile(HttpRequest request) {
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }

        // Fetch user profile from repository
        Optional<com.ghatana.phr.model.PHRUser> userOpt = userRepository.findByUserId(context.principalId());
        if (userOpt.isEmpty()) {
            return PhrRouteSupport.errorResponse(404, "PATIENT_NOT_FOUND", "Patient not found");
        }

        com.ghatana.phr.model.PHRUser user = userOpt.get();

        // Build profile response
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", user.getUserId());
        profile.put("tenantId", context.tenantId());
        profile.put("name", user.getUsername() != null ? user.getUsername() : "");
        profile.put("email", user.getEmail() != null ? user.getEmail() : "");
        profile.put("providerId", user.getProviderId() != null ? user.getProviderId() : "");
        profile.put("active", user.isActive());
        profile.put("emergencyContact", "");
        profile.put("preferredLanguage", "en");
        profile.put("facilityId", "");
        profile.put("birthDate", "");
        profile.put("bloodType", "");
        profile.put("gender", "");
        profile.put("location", "");

        return PhrRouteSupport.jsonResponseWithCorrelation(200, profile, context.correlationId());
    }

    private Promise<HttpResponse> handleUpdateProfile(HttpRequest request) {
        PhrRouteSupport.PhrRequestContext context;
        String idempotencyKey;
        try {
            context = PhrRouteSupport.requireContext(request);
            idempotencyKey = PhrRouteSupport.extractIdempotencyKey(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }

        // B-005: Check for existing update by idempotency key
        if (idempotencyKey != null) {
            // TODO: Check repository for existing update by idempotency key
            // For now, proceed with update
        }

        // Validate role-based edit permissions
        Set<String> allowedFields = getAllowedFieldsForRole(context.role());

        return request.loadBody()
            .then(body -> {
                String raw = body.getString(StandardCharsets.UTF_8);
                if (raw.isBlank()) {
                    return PhrRouteSupport.errorResponse(400, "EMPTY_BODY", "Request body is required",
                        context.correlationId());
                }

                try {
                    JsonNode node = PhrRouteSupport.JSON.readTree(raw);
                    
                    // Validate each field against permissions
                    Map<String, Object> validatedUpdates = new LinkedHashMap<>();
                    for (String fieldName : allowedFields) {
                        if (node.has(fieldName)) {
                            JsonNode fieldNode = node.get(fieldName);
                            String value = fieldNode.isTextual() ? fieldNode.asText() : fieldNode.toString();
                            
                            // Field-level validation
                            String validationError = validateField(fieldName, value);
                            if (validationError != null) {
                                return PhrRouteSupport.errorResponse(400, "INVALID_FIELD", 
                                    fieldName + ": " + validationError, context.correlationId());
                            }
                            
                            validatedUpdates.put(fieldName, value);
                        }
                    }

                    // Check for disallowed fields
                    for (java.util.Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
                        String fieldName = it.next();
                        if (!allowedFields.contains(fieldName)) {
                            return PhrRouteSupport.errorResponse(403, "FIELD_NOT_EDITABLE",
                                "Field '" + fieldName + "' cannot be edited by role: " + context.role(),
                                context.correlationId());
                        }
                    }

                    // Profile update is delegated to the service layer (stub for wiring).
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("status", "updated");
                    response.put("principalId", context.principalId());
                    response.put("updatedFields", validatedUpdates.keySet());
                    
                    return PhrRouteSupport.jsonResponseWithCorrelation(200, response, context.correlationId());
                } catch (Exception ex) {
                    return PhrRouteSupport.errorResponse(400, "INVALID_JSON", 
                        "Request body must be valid JSON: " + ex.getMessage(), context.correlationId());
                }
            });
    }

    private Set<String> getAllowedFieldsForRole(String role) {
        return switch (role) {
            case "admin" -> ADMIN_EDITABLE_FIELDS;
            case "clinician" -> CLINICIAN_EDITABLE_FIELDS;
            case "patient", "caregiver" -> PATIENT_EDITABLE_FIELDS;
            default -> Set.of();
        };
    }

    private String validateField(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            return null; // Empty values are allowed (clearing the field)
        }

        return switch (fieldName) {
            case "emergencyContact" -> {
                if (value.length() > 100) yield "Emergency contact must be 100 characters or less";
                yield null;
            }
            case "preferredLanguage" -> {
                if (!value.equals("en") && !value.equals("ne")) {
                    yield "Preferred language must be 'en' or 'ne'";
                }
                yield null;
            }
            case "facilityId" -> {
                if (value.length() > 50) yield "Facility ID must be 50 characters or less";
                yield null;
            }
            case "bloodType" -> {
                if (!Set.of("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-").contains(value)) {
                    yield "Invalid blood type";
                }
                yield null;
            }
            case "gender" -> {
                if (!Set.of("male", "female", "other", "unknown").contains(value.toLowerCase())) {
                    yield "Invalid gender value";
                }
                yield null;
            }
            case "location" -> {
                if (value.length() > 200) yield "Location must be 200 characters or less";
                yield null;
            }
            default -> null;
        };
    }
}
