package com.ghatana.phr.api.routes;

import com.fasterxml.jackson.databind.JsonNode;
import com.ghatana.phr.kernel.service.PatientRecordService;
import com.ghatana.phr.security.PhrPolicyEvaluator;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.RoutingServlet;
import io.activej.promise.Promise;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
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
    private final PatientRecordService patientRecordService;
    private final PhrPolicyEvaluator policyEvaluator;

    public PhrPatientProfileRoutes(
            Eventloop eventloop,
            PatientRecordService patientRecordService,
            PhrPolicyEvaluator policyEvaluator) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
        this.patientRecordService = Objects.requireNonNull(patientRecordService, "patientRecordService must not be null");
        this.policyEvaluator = Objects.requireNonNull(policyEvaluator, "policyEvaluator must not be null");
    }

    /**
     * Returns the routing servlet for patient profile endpoints.
     *
     * @return routing servlet; never null
     */
    public AsyncServlet getServlet() {
        return RoutingServlet.builder(eventloop)
            .with(HttpMethod.GET, "/", request -> handleGetProfile(request, "phr.profile.access"))
            .with(HttpMethod.PUT, "/", request -> handleUpdateProfile(request, "phr.profile.access"))
            .build();
    }

    /**
     * Returns the routing servlet for profile settings endpoints.
     *
     * @return routing servlet; never null
     */
    public AsyncServlet getSettingsServlet() {
        return RoutingServlet.builder(eventloop)
            .with(HttpMethod.GET, "/", request -> handleGetProfile(request, "phr.settings.access"))
            .with(HttpMethod.PUT, "/", request -> handleUpdateProfile(request, "phr.settings.access"))
            .build();
    }

    private Promise<HttpResponse> handleGetProfile(HttpRequest request, String policyId) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage(), correlationId);
        }
        String patientId = targetPatientId(request, context);

        return requireAccess(context, patientId, policyId, "READ")
            .then(decision -> {
                if (!decision.isAllowed()) {
                    return PhrRouteSupport.policyDenialResponse(403, context.correlationId(), decision.getReasonCode());
                }
                return patientRecordService.getPatient(patientId)
                    .then(patientOpt -> {
                        if (patientOpt.isEmpty()) {
                            return PhrRouteSupport.errorResponse(404, "PATIENT_NOT_FOUND", "Patient not found", context.correlationId());
                        }

                        return PhrRouteSupport.jsonResponseWithCorrelation(
                            200, profileDto(patientOpt.get(), context, List.of()), context.correlationId());
                    });
            });
    }

    private Promise<HttpResponse> handleUpdateProfile(HttpRequest request, String policyId) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        String idempotencyKey;
        try {
            context = PhrRouteSupport.requireContext(request);
            idempotencyKey = PhrRouteSupport.extractIdempotencyKey(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage(), correlationId);
        }

        String patientId = targetPatientId(request, context);
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

                    Map<String, String> validatedUpdates = new LinkedHashMap<>();
                    for (java.util.Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
                        String fieldName = it.next();
                        if (!allowedFields.contains(fieldName)) {
                            return PhrRouteSupport.errorResponse(403, "FIELD_NOT_EDITABLE", "Field '" + fieldName + "' cannot be edited by role: " + context.role(),
                                context.correlationId());
                        }
                        JsonNode fieldNode = node.get(fieldName);
                        if (!fieldNode.isNull() && !fieldNode.isTextual()) {
                            return PhrRouteSupport.errorResponse(400, "INVALID_FIELD",
                                fieldName + ": value must be a string", context.correlationId());
                        }
                        String value = fieldNode.isNull() ? "" : fieldNode.asText();

                        String validationError = validateField(fieldName, value);
                        if (validationError != null) {
                            return PhrRouteSupport.errorResponse(400, "INVALID_FIELD",
                                fieldName + ": " + validationError, context.correlationId());
                        }

                        validatedUpdates.put(fieldName, value);
                    }

                    if (validatedUpdates.isEmpty()) {
                        return PhrRouteSupport.errorResponse(400, "NO_EDITABLE_FIELDS", "At least one editable profile field is required",
                            context.correlationId());
                    }

                    return requireAccess(context, patientId, policyId, "WRITE")
                        .then(decision -> {
                            if (!decision.isAllowed()) {
                                return PhrRouteSupport.policyDenialResponse(403, context.correlationId(), decision.getReasonCode());
                            }
                            return patientRecordService.getPatient(patientId)
                                .then(patientOpt -> {
                                    if (patientOpt.isEmpty()) {
                                        return PhrRouteSupport.errorResponse(404, "PATIENT_NOT_FOUND", "Patient not found", context.correlationId());
                                    }

                                    PatientRecordService.Patient updated = applyUpdates(patientOpt.get(), validatedUpdates);
                                    return patientRecordService.updatePatient(updated)
                                        .then(stored -> PhrRouteSupport.jsonResponseWithCorrelation(
                                            200,
                                            profileDto(stored, context, List.copyOf(validatedUpdates.keySet())),
                                            context.correlationId()));
                                });
                        });
                } catch (Exception ex) {
                    return PhrRouteSupport.errorResponse(400, "INVALID_JSON", "Request body must be valid JSON: " + ex.getMessage(), context.correlationId());
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

    private Promise<PhrPolicyEvaluator.PolicyDecision> requireAccess(
            PhrRouteSupport.PhrRequestContext context,
            String patientId,
            String policyId,
            String action) {
        return policyEvaluator.evaluateByPolicyId(policyId, context, patientId, null)
            .then(decision -> {
                if (!decision.isAllowed()) {
                    return Promise.of(decision);
                }
                return policyEvaluator.canAccessPhiResourceAsync(
                    context,
                    patientId,
                    "profile-settings",
                    action,
                    context.tenantId(),
                    context.facilityId());
            });
    }

    private static String targetPatientId(HttpRequest request, PhrRouteSupport.PhrRequestContext context) {
        String patientId = request.getQueryParameter("patientId");
        return patientId == null || patientId.isBlank() ? context.principalId() : patientId.strip();
    }

    private static PatientRecordService.Patient applyUpdates(
            PatientRecordService.Patient patient,
            Map<String, String> updates) {
        PatientRecordService.Demographics demographics = patient.getDemographics() != null
            ? patient.getDemographics()
            : new PatientRecordService.Demographics(null, null, null, "unknown", null, null);
        PatientRecordService.Contact contact = demographics.getContact() != null
            ? demographics.getContact()
            : new PatientRecordService.Contact(null, null, null, null);
        PatientRecordService.Address address = demographics.getAddress() != null
            ? demographics.getAddress()
            : new PatientRecordService.Address(null, null, null, null, null);
        PatientRecordService.MedicalHistory medicalHistory = patient.getMedicalHistory() != null
            ? patient.getMedicalHistory()
            : new PatientRecordService.MedicalHistory(List.of(), List.of(), List.of(), null);

        PatientRecordService.Demographics updatedDemographics = demographics;
        PatientRecordService.MedicalHistory updatedMedicalHistory = medicalHistory;
        for (Map.Entry<String, String> entry : updates.entrySet()) {
            String value = entry.getValue();
            switch (entry.getKey()) {
                case "emergencyContact" -> updatedDemographics = updatedDemographics.withContact(contact.withEmergencyContact(value));
                case "preferredLanguage" -> updatedDemographics = updatedDemographics.withPreferredLanguage(value);
                case "facilityId" -> updatedDemographics = updatedDemographics.withFacilityId(value);
                case "gender" -> updatedDemographics = updatedDemographics.withGender(value.toLowerCase());
                case "location" -> updatedDemographics = updatedDemographics.withAddress(address.withDistrict(value));
                case "bloodType" -> updatedMedicalHistory = updatedMedicalHistory.withBloodType(value);
                default -> throw new IllegalArgumentException("Unsupported profile field: " + entry.getKey());
            }
            contact = updatedDemographics.getContact() != null ? updatedDemographics.getContact() : contact;
            address = updatedDemographics.getAddress() != null ? updatedDemographics.getAddress() : address;
        }

        return patient.withDemographics(updatedDemographics).withMedicalHistory(updatedMedicalHistory);
    }

    private static Map<String, Object> profileDto(
            PatientRecordService.Patient patient,
            PhrRouteSupport.PhrRequestContext context,
            List<String> updatedFields) {
        PatientRecordService.Demographics demographics = patient.getDemographics();
        PatientRecordService.Contact contact = demographics != null ? demographics.getContact() : null;
        PatientRecordService.Address address = demographics != null ? demographics.getAddress() : null;
        PatientRecordService.MedicalHistory medicalHistory = patient.getMedicalHistory();

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", patient.getId());
        profile.put("tenantId", context.tenantId());
        profile.put("nationalId", value(patient.getNationalId()));
        profile.put("mrn", value(patient.getNationalId()));
        profile.put("name", demographics != null ? value(demographics.getFullName()) : "");
        profile.put("birthDate", demographics != null ? value(demographics.getDateOfBirth()) : "");
        profile.put("age", demographics != null ? numericAge(demographics.getAge()) : 0);
        profile.put("gender", demographics != null ? value(demographics.getGender()) : "");
        profile.put("location", address != null ? value(address.getDistrict()) : "");
        profile.put("emergencyContact", contact != null ? value(contact.getEmergencyContact()) : "");
        profile.put("preferredLanguage", demographics != null ? firstNonBlank(demographics.getPreferredLanguage(), "en") : "en");
        profile.put("facilityId", demographics != null ? value(demographics.getFacilityId()) : "");
        profile.put("bloodType", medicalHistory != null ? value(medicalHistory.getBloodType()) : "");
        profile.put("createdAt", patient.getCreatedAt() != null ? patient.getCreatedAt().toString() : "");
        profile.put("updatedAt", patient.getUpdatedAt() != null ? patient.getUpdatedAt().toString() : "");
        profile.put("updatedFields", updatedFields);
        profile.put("audit", Map.of(
            "action", updatedFields.isEmpty() ? "PROFILE_READ" : "PROFILE_UPDATE",
            "actor", context.principalId(),
            "actorRole", context.role(),
            "timestamp", Instant.now().toString(),
            "correlationId", context.correlationId()
        ));
        profile.put("fieldClassification", fieldClassification());
        return profile;
    }

    private static Map<String, String> fieldClassification() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("nationalId", "direct_identifier");
        fields.put("mrn", "direct_identifier");
        fields.put("name", "direct_identifier");
        fields.put("birthDate", "demographic_phi");
        fields.put("age", "demographic_phi");
        fields.put("gender", "demographic_phi");
        fields.put("location", "demographic_phi");
        fields.put("emergencyContact", "contact_phi");
        fields.put("preferredLanguage", "preference");
        fields.put("facilityId", "care_network");
        fields.put("bloodType", "clinical_phi");
        return fields;
    }

    private static int numericAge(String age) {
        try {
            return Integer.parseInt(age);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String value(String value) {
        return value != null ? value : "";
    }
}
