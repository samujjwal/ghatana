package com.ghatana.phr.api.routes;

import com.fasterxml.jackson.databind.JsonNode;
import com.ghatana.phr.application.patient.PatientOperationContext;
import com.ghatana.phr.application.record.RecordService;
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
    private final RecordService recordService;
    private final PhrPolicyEvaluator policyEvaluator;

    public PhrPatientRecordRoutes(
            Eventloop eventloop,
            PatientRecordService patientRecordService,
            RecordService recordService,
            PhrPolicyEvaluator policyEvaluator) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
        this.patientRecordService = Objects.requireNonNull(patientRecordService, "patientRecordService must not be null");
        this.recordService = Objects.requireNonNull(recordService, "recordService must not be null");
        this.policyEvaluator = Objects.requireNonNull(policyEvaluator, "policyEvaluator must not be null");
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
            .with(HttpMethod.GET, "/:patientId/records", this::handleListRecords)
            .with(HttpMethod.GET, "/:patientId/records/:recordId", this::handleGetRecordDetail)
            .build();
    }

    private Promise<HttpResponse> handleCreatePatient(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage(), correlationId);
        }

        return request.loadBody()
            .then(body -> {
                PatientRecordService.Patient patient;
                try {
                    patient = parsePatient(body.getString(StandardCharsets.UTF_8), null);
                } catch (IllegalArgumentException ex) {
                    return PhrRouteSupport.errorResponse(400, "INVALID_PATIENT", ex.getMessage(), correlationId);
                }
                String patientId = patient.getId();
                if (patientId == null) {
                    return createPatient(patient, correlationId);
                }
                return mayAccessPatient(context, patientId, RESOURCE_TYPE, "WRITE")
                    .then(decision -> decision.isAllowed()
                        ? createPatient(patient, correlationId)
                        : PhrRouteSupport.policyDenialResponse(403, context.correlationId(), decision.getReasonCode()));
            });
    }

    private Promise<HttpResponse> handleGetPatient(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        String patientId;
        try {
            context = PhrRouteSupport.requireContext(request);
            patientId = request.getPathParameter("patientId");
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage(), correlationId);
        }

        return requireSelfOrConsent(context, patientId, RESOURCE_TYPE, "READ")
            .then(decision -> {
                if (!decision.isAllowed()) {
                    return PhrRouteSupport.policyDenialResponse(403, context.correlationId(), "POLICY_DENIED");
                }
                return patientRecordService.getPatient(patientId)
                    .then(patient -> patient
                        .<Promise<HttpResponse>>map(value -> PhrRouteSupport.jsonResponse(200, value, correlationId))
                        .orElseGet(() -> PhrRouteSupport.errorResponse(404, "PATIENT_NOT_FOUND", "Patient record not found", correlationId)));
            });
    }

    private Promise<HttpResponse> handleUpdatePatient(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        String patientId;
        try {
            context = PhrRouteSupport.requireContext(request);
            patientId = request.getPathParameter("patientId");
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage(), correlationId);
        }

        return requireSelfOrConsent(context, patientId, RESOURCE_TYPE, "WRITE")
            .then(decision -> {
                if (!decision.isAllowed()) {
                    return PhrRouteSupport.policyDenialResponse(403, context.correlationId(), decision.getReasonCode());
                }
                return request.loadBody()
                    .then(body -> {
                        PatientRecordService.Patient patient;
                        try {
                            patient = parsePatient(body.getString(StandardCharsets.UTF_8), patientId);
                        } catch (IllegalArgumentException ex) {
                            return PhrRouteSupport.errorResponse(400, "INVALID_PATIENT", ex.getMessage(), correlationId);
                        }
                        return patientRecordService.updatePatient(patient)
                            .then(updated -> PhrRouteSupport.jsonResponse(200, updated, correlationId));
                    });
            });
    }

    private Promise<HttpResponse> handleSearchPatients(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        String patientId;
        int limit;
        int offset;
        try {
            context = PhrRouteSupport.requireContext(request);
            patientId = request.getQueryParameter("patientId");
            if (patientId == null || patientId.isBlank()) {
                throw new IllegalArgumentException("Missing required query parameter: patientId");
            }
            limit = PhrRouteSupport.intQuery(request, "limit", 50, 1000);
            offset = PhrRouteSupport.intQuery(request, "offset", 0, 10_000);
        } catch (RuntimeException ex) {
            return PhrRouteSupport.errorResponse(400, "INVALID_SEARCH", ex.getMessage(), correlationId);
        }

        return requireSelfOrConsent(context, patientId, RESOURCE_TYPE, "SEARCH")
            .then(decision -> {
                if (!decision.isAllowed()) {
                    return PhrRouteSupport.policyDenialResponse(403, context.correlationId(), decision.getReasonCode());
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
                    ), correlationId));
            });
    }

    private Promise<HttpResponse> handlePatientHistory(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        String patientId;
        try {
            context = PhrRouteSupport.requireContext(request);
            patientId = request.getPathParameter("patientId");
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage(), correlationId);
        }

        return requireSelfOrConsent(context, patientId, "patient-record-history", "READ")
            .then(decision -> {
                if (!decision.isAllowed()) {
                    return PhrRouteSupport.policyDenialResponse(403, context.correlationId(), decision.getReasonCode());
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
                            return PhrRouteSupport.jsonResponse(200, response, correlationId);
                        })
                        .orElseGet(() -> PhrRouteSupport.errorResponse(404, "PATIENT_NOT_FOUND", "Patient record not found", correlationId)));
            });
    }

    private Promise<HttpResponse> handleListRecords(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        String patientId;
        int limit;
        int offset;
        try {
            context = PhrRouteSupport.requireContext(request);
            patientId = request.getPathParameter("patientId");
            limit = PhrRouteSupport.intQuery(request, "limit", 50, 100);
            offset = PhrRouteSupport.intQuery(request, "offset", 0, 10_000);
        } catch (RuntimeException ex) {
            return PhrRouteSupport.errorResponse(400, "INVALID_RECORD_LIST", ex.getMessage(), correlationId);
        }

        String category = request.getQueryParameter("category");
        String resourceType = request.getQueryParameter("resourceType");
        String dateFrom = request.getQueryParameter("dateFrom");
        String dateTo = request.getQueryParameter("dateTo");

        return requireSelfOrConsent(context, patientId, "patient-record-list", "READ")
            .then(decision -> {
                if (!decision.isAllowed()) {
                    return PhrRouteSupport.policyDenialResponse(403, context.correlationId(), decision.getReasonCode());
                }

                PatientOperationContext opCtx = new PatientOperationContext(
                    context.tenantId(),
                    "default",
                    context.principalId(),
                    patientId,
                    context.correlationId()
                );

                return recordService.getRecordTimeline(opCtx, patientId)
                    .then(timeline -> {
                        List<Map<String, Object>> items = timeline.entries().stream()
                            .map(entry -> {
                                Map<String, Object> item = new java.util.LinkedHashMap<>();
                                item.put("id", entry.entryId());
                                item.put("title", entry.type());
                                item.put("category", entry.category());
                                item.put("resourceType", entry.category());
                                item.put("updatedAt", entry.timestamp());
                                item.put("redacted", false);
                                item.put("provenance", Map.of(
                                    "source", "phr-record-service",
                                    "accessedBy", context.principalId(),
                                    "tenantId", context.tenantId()
                                ));
                                item.put("description", entry.description());
                                item.put("details", entry.details());
                                return item;
                            })
                            .filter(item -> {
                                boolean matchesCategory = category == null || category.isBlank() || category.equals(item.get("category"));
                                boolean matchesResource = resourceType == null || resourceType.isBlank() || resourceType.equals(item.get("resourceType"));
                                boolean matchesFrom = dateFrom == null || dateFrom.isBlank() || item.get("updatedAt").toString().compareTo(dateFrom) >= 0;
                                boolean matchesTo = dateTo == null || dateTo.isBlank() || item.get("updatedAt").toString().compareTo(dateTo) <= 0;
                                return matchesCategory && matchesResource && matchesFrom && matchesTo;
                            })
                            .skip(offset)
                            .limit(limit)
                            .toList();

                        return PhrRouteSupport.jsonResponse(200, Map.of(
                            "items", items,
                            "count", items.size(),
                            "limit", limit,
                            "offset", offset,
                            "patientId", patientId,
                            "generatedAt", timeline.generatedAt()
                        ), correlationId);
                    });
            });
    }

    private Promise<HttpResponse> handleGetRecordDetail(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        String patientId;
        String recordId;
        try {
            context = PhrRouteSupport.requireContext(request);
            patientId = request.getPathParameter("patientId");
            recordId = request.getPathParameter("recordId");
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage(), correlationId);
        }


        if (recordId == null || recordId.isBlank()) {
            return PhrRouteSupport.errorResponse(400, "MISSING_RECORD_ID",
                "recordId path parameter is required", context.correlationId());
        }

        return policyEvaluator.canAccessPhiResourceAsync(
                context, patientId, "patient-record-detail", "READ",
                context.tenantId(), context.facilityId())
            .then(decision -> {
                if (!decision.isAllowed()) {
                    return PhrRouteSupport.policyDenialResponse(403, context.correlationId(), decision.getReasonCode());
                }

                return patientRecordService.getPatient(patientId)
                    .then(patientOpt -> {
                        if (patientOpt.isEmpty()) {
                            return PhrRouteSupport.errorResponse(404, "PATIENT_NOT_FOUND",
                                "Patient not found", context.correlationId());
                        }

                        PatientRecordService.Patient patient = patientOpt.get();
                        Instant accessedAt = Instant.now();
                        PatientRecordService.Demographics demo = patient.getDemographics();
                        String familyName = demo != null && demo.getFamilyName() != null
                            ? demo.getFamilyName() : "";

                        Map<String, Object> record = new java.util.LinkedHashMap<>();
                        record.put("id", recordId);
                        record.put("title", "Patient Record - " + familyName);
                        record.put("resourceType", "Patient");
                        record.put("category", "administrative");
                        record.put("updatedAt", patient.getUpdatedAt() != null
                            ? patient.getUpdatedAt().toString() : accessedAt.toString());
                        record.put("status", patient.isDeleted() ? "inactive" : "active");

                        Map<String, Object> fhirResource = buildFhirPatientResource(patient, context.role());
                        String fhirJson;
                        try {
                            fhirJson = PhrRouteSupport.JSON.writeValueAsString(fhirResource);
                        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
                            fhirJson = "{}";
                        }

                        Map<String, Object> accessAudit = new java.util.LinkedHashMap<>();
                        accessAudit.put("accessedAt", accessedAt.toString());
                        accessAudit.put("accessedBy", context.principalId());
                        accessAudit.put("accessRole", context.role());
                        accessAudit.put("tenantId", context.tenantId());
                        accessAudit.put("correlationId", context.correlationId());
                        accessAudit.put("policyReason", decision.getReasonCode());
                        accessAudit.put("requiresAudit", decision.requiresAudit());

                        Map<String, Object> response = new java.util.LinkedHashMap<>();
                        response.put("record", record);
                        response.put("fhirJson", fhirJson);
                        response.put("accessAudit", accessAudit);

                        return PhrRouteSupport.jsonResponse(200, response, context.correlationId());
                    });
            });
    }

    private static Map<String, Object> buildFhirPatientResource(
            PatientRecordService.Patient patient,
            String role) {
        PatientRecordService.Demographics demo = patient.getDemographics();
        Map<String, Object> resource = new java.util.LinkedHashMap<>();
        resource.put("resourceType", "Patient");
        resource.put("id", patient.getId());

        boolean showFullPhi = "patient".equals(role) || "clinician".equals(role) || "caregiver".equals(role);

        if (showFullPhi && demo != null) {
            Map<String, Object> nameEntry = new java.util.LinkedHashMap<>();
            nameEntry.put("use", "official");
            nameEntry.put("family", demo.getFamilyName() != null ? demo.getFamilyName() : "");
            nameEntry.put("given", demo.getGivenName() != null
                ? List.of(demo.getGivenName()) : List.of());
            resource.put("name", List.of(nameEntry));
            if (demo.getDateOfBirth() != null) {
                resource.put("birthDate", demo.getDateOfBirth());
            }
            if (demo.getGender() != null) {
                resource.put("gender", demo.getGender().toLowerCase());
            }
        } else {
            resource.put("name", List.of(Map.of(
                "use", "official",
                "family", "[REDACTED]",
                "given", List.of("[REDACTED]"))));
            resource.put("birthDate", "[REDACTED]");
        }

        resource.put("active", !patient.isDeleted());
        resource.put("meta", Map.of(
            "lastUpdated", patient.getUpdatedAt() != null
                ? patient.getUpdatedAt().toString() : Instant.now().toString(),
            "source", "phr-patient-record-service"
        ));

        return resource;
    }

    private Promise<HttpResponse> createPatient(PatientRecordService.Patient patient, String correlationId) {
        return patientRecordService.createPatient(patient)
            .then(created -> PhrRouteSupport.jsonResponse(201, created, correlationId));
    }

    private Promise<PhrPolicyEvaluator.PolicyDecision> requireSelfOrConsent(
            PhrRouteSupport.PhrRequestContext context,
            String patientId,
            String resourceType,
            String action) {
        return mayAccessPatient(context, patientId, resourceType, action);
    }

    private Promise<PhrPolicyEvaluator.PolicyDecision> mayAccessPatient(
            PhrRouteSupport.PhrRequestContext context,
            String patientId,
            String resourceType,
            String action) {
        return policyEvaluator.canAccessPhiResourceAsync(
                context,
                patientId,
                resourceType,
                action,
                context.tenantId(),
                context.facilityId());
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
