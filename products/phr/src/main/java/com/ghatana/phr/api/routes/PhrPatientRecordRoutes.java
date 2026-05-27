package com.ghatana.phr.api.routes;

import com.fasterxml.jackson.databind.JsonNode;
import com.ghatana.phr.kernel.service.ConsentManagementService;
import com.ghatana.phr.kernel.service.PatientRecordService;
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
import java.util.Map;
import java.util.Objects;

/**
 * Patient record API routes for the PHR product.
 *
 * @doc.type class
 * @doc.purpose ActiveJ route adapter for patient record CRUD, search, and read-history journeys
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 */
public final class PhrPatientRecordRoutes {

    private static final String RESOURCE_TYPE = "patient-records";

    private final Eventloop eventloop;
    private final PatientRecordService patientRecordService;
    private final ConsentManagementService consentService;

    public PhrPatientRecordRoutes(
            Eventloop eventloop,
            PatientRecordService patientRecordService,
            ConsentManagementService consentService) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
        this.patientRecordService = Objects.requireNonNull(patientRecordService, "patientRecordService must not be null");
        this.consentService = Objects.requireNonNull(consentService, "consentService must not be null");
    }

    /**
     * Returns the routing servlet for patient record endpoints.
     *
     * @return routing servlet; never null
     */
    public AsyncServlet getServlet() {
        return RoutingServlet.builder(eventloop)
            .with(HttpMethod.POST, "/", this::handleCreatePatient)
            .with(HttpMethod.GET, "/", this::handleSearchPatients)
            .with(HttpMethod.GET, "/:patientId/history", this::handlePatientHistory)
            .with(HttpMethod.GET, "/:patientId", this::handleGetPatient)
            .with(HttpMethod.PUT, "/:patientId", this::handleUpdatePatient)
            .with(HttpMethod.GET, "/:patientId/records/:recordId", this::handleGetRecordDetail)
            .build();
    }

    private Promise<HttpResponse> handleCreatePatient(HttpRequest request) {
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }

        return request.loadBody()
            .then(body -> {
                PatientRecordService.Patient patient;
                try {
                    patient = parsePatient(body.getString(StandardCharsets.UTF_8), null);
                } catch (IllegalArgumentException ex) {
                    return PhrRouteSupport.errorResponse(400, "INVALID_PATIENT", ex.getMessage());
                }
                String patientId = patient.getId();
                if (patientId != null && !mayAccessPatient(context, patientId)) {
                    return requireConsent(context, patientId)
                        .then(allowed -> allowed
                            ? createPatient(patient)
                            : PhrRouteSupport.errorResponse(403, "CONSENT_REQUIRED", "Consent is required to create this patient record"));
                }
                return createPatient(patient);
            });
    }

    private Promise<HttpResponse> handleGetPatient(HttpRequest request) {
        PhrRouteSupport.PhrRequestContext context;
        String patientId;
        try {
            context = PhrRouteSupport.requireContext(request);
            patientId = request.getPathParameter("patientId");
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }

        return requireSelfOrConsent(context, patientId)
            .then(allowed -> {
                if (!allowed) {
                    return PhrRouteSupport.errorResponse(403, "CONSENT_REQUIRED", "Consent is required to read this patient record");
                }
                return patientRecordService.getPatient(patientId)
                    .then(patient -> patient
                        .<Promise<HttpResponse>>map(value -> PhrRouteSupport.jsonResponse(200, value))
                        .orElseGet(() -> PhrRouteSupport.errorResponse(404, "PATIENT_NOT_FOUND", "Patient record not found")));
            });
    }

    private Promise<HttpResponse> handleUpdatePatient(HttpRequest request) {
        PhrRouteSupport.PhrRequestContext context;
        String patientId;
        try {
            context = PhrRouteSupport.requireContext(request);
            patientId = request.getPathParameter("patientId");
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }

        return requireSelfOrConsent(context, patientId)
            .then(allowed -> {
                if (!allowed) {
                    return PhrRouteSupport.errorResponse(403, "CONSENT_REQUIRED", "Consent is required to update this patient record");
                }
                return request.loadBody()
                    .then(body -> {
                        PatientRecordService.Patient patient;
                        try {
                            patient = parsePatient(body.getString(StandardCharsets.UTF_8), patientId);
                        } catch (IllegalArgumentException ex) {
                            return PhrRouteSupport.errorResponse(400, "INVALID_PATIENT", ex.getMessage());
                        }
                        return patientRecordService.updatePatient(patient)
                            .then(updated -> PhrRouteSupport.jsonResponse(200, updated));
                    });
            });
    }

    private Promise<HttpResponse> handleSearchPatients(HttpRequest request) {
        PhrRouteSupport.PhrRequestContext context;
        String patientId;
        int limit;
        int offset;
        try {
            context = PhrRouteSupport.requireContext(request);
            patientId = PhrRouteSupport.requiredQuery(request, "patientId");
            limit = PhrRouteSupport.intQuery(request, "limit", 50, 1000);
            offset = PhrRouteSupport.intQuery(request, "offset", 0, 10_000);
        } catch (RuntimeException ex) {
            return PhrRouteSupport.errorResponse(400, "INVALID_SEARCH", ex.getMessage());
        }

        return requireSelfOrConsent(context, patientId)
            .then(allowed -> {
                if (!allowed) {
                    return PhrRouteSupport.errorResponse(403, "CONSENT_REQUIRED", "Consent is required to search this patient record");
                }
                return patientRecordService.searchPatients(
                        "patientId = :patientId",
                        Map.of("patientId", patientId),
                        limit,
                        offset)
                    .then(records -> PhrRouteSupport.jsonResponse(200, Map.of(
                        "items", records,
                        "count", records.size(),
                        "limit", limit,
                        "offset", offset
                    )));
            });
    }

    private Promise<HttpResponse> handlePatientHistory(HttpRequest request) {
        PhrRouteSupport.PhrRequestContext context;
        String patientId;
        try {
            context = PhrRouteSupport.requireContext(request);
            patientId = request.getPathParameter("patientId");
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }

        return requireSelfOrConsent(context, patientId)
            .then(allowed -> {
                if (!allowed) {
                    return PhrRouteSupport.errorResponse(403, "CONSENT_REQUIRED", "Consent is required to read this patient record history");
                }
                return patientRecordService.getPatient(patientId)
                    .then(patient -> patient
                        .<Promise<HttpResponse>>map(value -> {
                            Map<String, Object> historyEntry = new java.util.LinkedHashMap<>();
                            historyEntry.put("version", 1);
                            historyEntry.put("createdAt", value.getCreatedAt());
                            historyEntry.put("updatedAt", value.getUpdatedAt());
                            historyEntry.put("deleted", value.isDeleted());
                            Map<String, Object> response = new java.util.LinkedHashMap<>();
                            response.put("patientId", patientId);
                            response.put("history", List.of(historyEntry));
                            return PhrRouteSupport.jsonResponse(200, response);
                        })
                        .orElseGet(() -> PhrRouteSupport.errorResponse(404, "PATIENT_NOT_FOUND", "Patient record not found")));
            });
    }

    private Promise<HttpResponse> handleGetRecordDetail(HttpRequest request) {
        PhrRouteSupport.PhrRequestContext context;
        String patientId;
        String recordId;
        try {
            context = PhrRouteSupport.requireContext(request);
            patientId = request.getPathParameter("patientId");
            recordId = request.getPathParameter("recordId");
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }

        return requireSelfOrConsent(context, patientId)
            .then(allowed -> {
                if (!allowed) {
                    return PhrRouteSupport.errorResponse(403, "CONSENT_REQUIRED", "Consent is required to read this record detail");
                }
                
                // For now, return a placeholder response - in production this would fetch
                // the specific FHIR resource by recordId and return it with PHI redaction
                Map<String, Object> response = new java.util.LinkedHashMap<>();
                response.put("patientId", patientId);
                response.put("recordId", recordId);
                response.put("resourceType", "Observation");
                response.put("status", "active");
                response.put("accessedAt", Instant.now().toString());
                response.put("accessedBy", context.principalId());
                response.put("accessReason", "Patient record detail view");
                
                return PhrRouteSupport.jsonResponse(200, response);
            });
    }

    private Promise<HttpResponse> createPatient(PatientRecordService.Patient patient) {
        return patientRecordService.createPatient(patient)
            .then(created -> PhrRouteSupport.jsonResponse(201, created));
    }

    private Promise<Boolean> requireSelfOrConsent(PhrRouteSupport.PhrRequestContext context, String patientId) {
        if (mayAccessPatient(context, patientId)) {
            return Promise.of(true);
        }
        return requireConsent(context, patientId);
    }

    private Promise<Boolean> requireConsent(PhrRouteSupport.PhrRequestContext context, String patientId) {
        return consentService.validateAccess(patientId, context.principalId(), RESOURCE_TYPE)
            .map(ConsentManagementService.ConsentValidationResult::isAllowed);
    }

    private static boolean mayAccessPatient(PhrRouteSupport.PhrRequestContext context, String patientId) {
        return PhrRouteSupport.canAccessPatientRecordForRole(context, patientId);
    }

    private static PatientRecordService.Patient parsePatient(String json, String pathPatientId) {
        try {
            JsonNode node = PhrRouteSupport.JSON.readTree(json);
            String id = text(node, "id", pathPatientId);
            if (pathPatientId != null && id != null && !pathPatientId.equals(id)) {
                throw new IllegalArgumentException("body id must match path patientId");
            }
            PatientRecordService.Demographics demographics = new PatientRecordService.Demographics(
                text(node.path("demographics"), "givenName", null),
                text(node.path("demographics"), "familyName", null),
                text(node.path("demographics"), "dateOfBirth", null),
                text(node.path("demographics"), "gender", null),
                null,
                null
            );
            return PatientRecordService.Patient.builder()
                .id(id)
                .nationalId(text(node, "nationalId", null))
                .demographics(demographics)
                .medicalHistory(null)
                .createdAt(parseInstant(node, "createdAt"))
                .updatedAt(parseInstant(node, "updatedAt"))
                .deleted(node.path("deleted").asBoolean(false))
                .build();
        } catch (Exception ex) {
            throw new IllegalArgumentException(ex.getMessage(), ex);
        }
    }

    private static String text(JsonNode node, String fieldName, String defaultValue) {
        JsonNode value = node.path(fieldName);
        return value.isMissingNode() || value.isNull() || value.asText().isBlank() ? defaultValue : value.asText();
    }

    private static Instant parseInstant(JsonNode node, String fieldName) {
        String value = text(node, fieldName, null);
        return value == null ? null : Instant.parse(value);
    }
}
