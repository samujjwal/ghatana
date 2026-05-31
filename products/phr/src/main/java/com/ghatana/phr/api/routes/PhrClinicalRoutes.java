package com.ghatana.phr.api.routes;

import com.fasterxml.jackson.databind.JsonNode;
import com.ghatana.phr.kernel.service.ConsentManagementService;
import com.ghatana.phr.kernel.service.ImmunizationService;
import com.ghatana.phr.kernel.service.LabResultService;
import com.ghatana.phr.kernel.service.MedicationService;
import com.ghatana.phr.security.PhrPolicyEvaluator;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.RoutingServlet;
import io.activej.promise.Promise;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Clinical journey API routes for PHR labs, medications, and immunizations.
 *
 * @doc.type class
 * @doc.purpose ActiveJ route adapter for sensitive clinical PHR API journeys
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 */
public final class PhrClinicalRoutes {

    private final Eventloop eventloop;
    private final LabResultService labResultService;
    private final MedicationService medicationService;
    private final ImmunizationService immunizationService;
    private final PhrPolicyEvaluator policyEvaluator;

    public PhrClinicalRoutes(
            Eventloop eventloop,
            LabResultService labResultService,
            MedicationService medicationService,
            ImmunizationService immunizationService,
            ConsentManagementService consentService,
            PhrPolicyEvaluator policyEvaluator) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
        this.labResultService = Objects.requireNonNull(labResultService, "labResultService must not be null");
        this.medicationService = Objects.requireNonNull(medicationService, "medicationService must not be null");
        this.immunizationService = Objects.requireNonNull(immunizationService, "immunizationService must not be null");
        Objects.requireNonNull(consentService, "consentService must not be null");
        this.policyEvaluator = Objects.requireNonNull(policyEvaluator, "policyEvaluator must not be null");
    }

    /**
     * Returns the routing servlet for clinical endpoints.
     *
     * @return routing servlet; never null
     */
    public AsyncServlet getServlet() {
        return RoutingServlet.builder(eventloop)
            .with("/labs/*", labServlet())
            .with("/observations/*", observationsServlet())
            .with("/medications/*", medicationServlet())
            .with("/immunizations/*", immunizationServlet())
            .build();
    }

    private AsyncServlet labServlet() {
        return RoutingServlet.builder(eventloop)
            .with(HttpMethod.POST, "/observations", this::handleRecordLabObservation)
            .with(HttpMethod.GET, "/observations/:observationId", this::handleGetLabObservation)
            .with(HttpMethod.GET, "/trends", this::handleGetLabTrend)
            .with(HttpMethod.GET, "/", this::handleListLabObservations)
            .build();
    }


    private AsyncServlet observationsServlet() {
        return RoutingServlet.builder(eventloop)
            .with(HttpMethod.GET, "/", this::handleListLabObservations)
            .with(HttpMethod.GET, "/:observationId", this::handleGetLabObservation)
            .with(HttpMethod.GET, "/trends", this::handleGetLabTrend)
            .build();
    }

    private AsyncServlet medicationServlet() {
        return RoutingServlet.builder(eventloop)
            .with(HttpMethod.POST, "/prescriptions", this::handlePrescribeMedication)
            .with(HttpMethod.GET, "/prescriptions/:prescriptionId", this::handleGetPrescription)
            .with(HttpMethod.POST, "/prescriptions/:prescriptionId/discontinue", this::handleDiscontinuePrescription)
            .with(HttpMethod.POST, "/prescriptions/:prescriptionId/refill", this::handleRefillPrescription)
            .with(HttpMethod.GET, "/prescriptions", this::handleListPrescriptionHistory)
            .with(HttpMethod.GET, "/prescriptions/:prescriptionId/interactions", this::handleGetDrugInteractions)
            .with(HttpMethod.GET, "/prescriptions/:prescriptionId/allergy-check", this::handleGetAllergyInteractions)
            .with(HttpMethod.GET, "/", this::handleListActivePrescriptions)
            .build();
    }

    private AsyncServlet immunizationServlet() {
        return RoutingServlet.builder(eventloop)
            .with(HttpMethod.POST, "/", this::handleRecordImmunization)
            .with(HttpMethod.GET, "/:immunizationId", this::handleGetImmunization)
            .with(HttpMethod.GET, "/", this::handleListImmunizations)
            .build();
    }

    private Promise<HttpResponse> handleRecordLabObservation(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        return withBodyAndConsent(request, "lab-results", "WRITE", LabResultService.LabObservation.class,
            observation -> labResultService.recordObservation(observation)
                .then(stored -> PhrRouteSupport.jsonResponse(201, stored, correlationId)));
    }

    private Promise<HttpResponse> handleGetLabObservation(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage(), correlationId);
        }
        String observationId = request.getPathParameter("observationId");
        return labResultService.getObservation(observationId)
            .then(observation -> {
                if (observation.isEmpty()) {
                    return PhrRouteSupport.errorResponse(404, "LAB_OBSERVATION_NOT_FOUND", "Lab observation not found", correlationId);
                }
                return requireAccess(context, observation.get().patientId(), "lab-results", "READ")
                    .then(decision -> decision.isAllowed()
                        ? PhrRouteSupport.jsonResponse(200, labObservationDto(observation.get()), correlationId)
                        : PhrRouteSupport.policyDenialResponse(403, context.correlationId(), decision.getReasonCode()));
            });
    }

    private Promise<HttpResponse> handleListLabObservations(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        return withPatientAccess(request, "lab-results", "READ", patientId -> labResultService.getPatientObservations(patientId)
            .then(items -> {
                List<Map<String, Object>> dtos = items.stream()
                    .map(PhrClinicalRoutes::labObservationDto)
                    .toList();
                return PhrRouteSupport.jsonResponse(200, Map.of("patientId", patientId, "items", dtos, "count", dtos.size()), correlationId);
            }));
    }

    private Promise<HttpResponse> handleGetLabTrend(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        String loincCode;
        try {
            loincCode = PhrRouteSupport.requiredQuery(request, "loincCode");
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "INVALID_TREND", ex.getMessage(), correlationId);
        }
        return withPatientAccess(request, "lab-results", "READ", patientId -> labResultService.getTrend(patientId, loincCode)
            .then(items -> {
                List<Map<String, Object>> dtos = items.stream()
                    .map(PhrClinicalRoutes::labObservationDto)
                    .toList();
                Map<String, Object> response = new java.util.HashMap<>();
                response.put("patientId", patientId);
                response.put("loincCode", loincCode);
                response.put("items", dtos);
                response.put("count", dtos.size());
                return PhrRouteSupport.jsonResponse(200, response, correlationId);
            }));
    }

    private Promise<HttpResponse> handlePrescribeMedication(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        return withBodyAndConsent(request, "medications", "WRITE", MedicationService.Prescription.class,
            prescription -> medicationService.prescribe(prescription)
                .then(stored -> PhrRouteSupport.jsonResponse(201, stored, correlationId)));
    }

    private Promise<HttpResponse> handleGetPrescription(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage(), correlationId);
        }
        String prescriptionId = request.getPathParameter("prescriptionId");
        return medicationService.getPrescription(prescriptionId)
            .then(prescription -> {
                if (prescription.isEmpty()) {
                    return PhrRouteSupport.errorResponse(404, "PRESCRIPTION_NOT_FOUND", "Prescription not found", correlationId);
                }
                return requireAccess(context, prescription.get().patientId(), "medications", "READ")
                    .then(decision -> decision.isAllowed()
                        ? medicationService.checkDrugInteractions(prescription.get().patientId())
                            .then(interactions -> PhrRouteSupport.jsonResponse(200, medicationDto(prescription.get(), interactions), correlationId))
                        : PhrRouteSupport.policyDenialResponse(403, context.correlationId(), decision.getReasonCode()));
            });
    }

    private Promise<HttpResponse> handleListActivePrescriptions(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        return withPatientAccess(request, "medications", "READ", patientId -> medicationService.getActivePrescriptions(patientId)
            .then(items -> medicationService.checkDrugInteractions(patientId)
                .then(interactions -> {
                    List<Map<String, Object>> dtos = items.stream()
                        .map(item -> medicationDto(item, interactions))
                        .toList();
                    return PhrRouteSupport.jsonResponse(200, Map.of("patientId", patientId, "items", dtos, "count", dtos.size()), correlationId);
                })));
    }

    private Promise<HttpResponse> handleDiscontinuePrescription(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        String reason = request.getQueryParameter("reason");
        String patientId = request.getQueryParameter("patientId");
        if (patientId == null || patientId.isBlank()) {
            return PhrRouteSupport.errorResponse(400, "INVALID_DISCONTINUE", "patientId query parameter is required", correlationId);
        }
        return withPatientAccess(request, "medications", "WRITE", ignored -> medicationService.discontinue(
                request.getPathParameter("prescriptionId"),
                reason == null || reason.isBlank() ? "not specified" : reason)
            .then(updated -> PhrRouteSupport.jsonResponse(200, updated, correlationId)));
    }

    private Promise<HttpResponse> handleRefillPrescription(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        String patientId = request.getQueryParameter("patientId");
        if (patientId == null || patientId.isBlank()) {
            return PhrRouteSupport.errorResponse(400, "INVALID_REFILL", "patientId query parameter is required", correlationId);
        }
        return withPatientAccess(request, "medications", "WRITE", ignored -> medicationService.refill(request.getPathParameter("prescriptionId"))
            .then(updated -> PhrRouteSupport.jsonResponse(200, updated, correlationId)));
    }

    private Promise<HttpResponse> handleListPrescriptionHistory(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        String patientId = request.getQueryParameter("patientId");
        if (patientId == null || patientId.isBlank()) {
            return PhrRouteSupport.errorResponse(400, "INVALID_HISTORY", "patientId query parameter is required", correlationId);
        }
        return withPatientAccess(request, "medications", "READ", ignored -> medicationService.getPrescriptionHistory(patientId)
            .then(items -> medicationService.checkDrugInteractions(patientId)
                .then(interactions -> {
                    List<Map<String, Object>> dtos = items.stream()
                        .map(item -> medicationDto(item, interactions))
                        .toList();
                    return PhrRouteSupport.jsonResponse(200, Map.of("patientId", patientId, "items", dtos, "count", dtos.size()), correlationId);
                })));
    }

    private Promise<HttpResponse> handleGetDrugInteractions(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        String patientId = request.getQueryParameter("patientId");
        if (patientId == null || patientId.isBlank()) {
            return PhrRouteSupport.errorResponse(400, "INVALID_INTERACTION_CHECK", "patientId query parameter is required", correlationId);
        }
        return withPatientAccess(request, "medications", "READ", ignored -> medicationService.checkDrugInteractions(patientId)
            .then(warnings -> PhrRouteSupport.jsonResponse(200, Map.of("patientId", patientId, "warnings", warnings, "count", warnings.size()), correlationId)));
    }

    private Promise<HttpResponse> handleGetAllergyInteractions(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        String patientId = request.getQueryParameter("patientId");
        String medicationCode = request.getQueryParameter("medicationCode");
        if (patientId == null || patientId.isBlank()) {
            return PhrRouteSupport.errorResponse(400, "INVALID_ALLERGY_CHECK", "patientId query parameter is required", correlationId);
        }
        if (medicationCode == null || medicationCode.isBlank()) {
            return PhrRouteSupport.errorResponse(400, "INVALID_ALLERGY_CHECK", "medicationCode query parameter is required", correlationId);
        }
        return withPatientAccess(request, "medications", "READ", ignored -> medicationService.checkAllergyInteractions(patientId, medicationCode)
            .then(warnings -> PhrRouteSupport.jsonResponse(200, Map.of("patientId", patientId, "medicationCode", medicationCode, "warnings", warnings, "count", warnings.size()), correlationId)));
    }

    private Promise<HttpResponse> handleRecordImmunization(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        return withBodyAndConsent(request, "immunizations", "WRITE", ImmunizationService.ImmunizationRecord.class,
            immunization -> immunizationService.recordImmunization(immunization)
                .then(stored -> PhrRouteSupport.jsonResponse(201, stored, correlationId)));
    }

    private Promise<HttpResponse> handleGetImmunization(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage(), correlationId);
        }
        String immunizationId = request.getPathParameter("immunizationId");
        return immunizationService.getImmunization(immunizationId)
            .then(immunization -> {
                if (immunization.isEmpty()) {
                    return PhrRouteSupport.errorResponse(404, "IMMUNIZATION_NOT_FOUND", "Immunization not found", correlationId);
                }
                return requireAccess(context, immunization.get().patientId(), "immunizations", "READ")
                    .then(decision -> decision.isAllowed()
                        ? immunizationService.getDueSchedules(immunization.get().patientId())
                            .then(schedules -> PhrRouteSupport.jsonResponse(200, immunizationDto(immunization.get(), schedules), correlationId))
                        : PhrRouteSupport.policyDenialResponse(403, context.correlationId(), decision.getReasonCode()));
            });
    }

    private Promise<HttpResponse> handleListImmunizations(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        return withPatientAccess(request, "immunizations", "READ", patientId -> immunizationService.getImmunizationHistory(patientId)
            .then(items -> immunizationService.getDueSchedules(patientId)
                .then(schedules -> {
                    List<Map<String, Object>> administered = items.stream()
                        .map(item -> immunizationDto(item, schedules))
                        .toList();
                    List<Map<String, Object>> due = schedules.stream()
                        .map(PhrClinicalRoutes::dueImmunizationDto)
                        .toList();
                    List<Map<String, Object>> dtos = java.util.stream.Stream
                        .concat(administered.stream(), due.stream())
                        .toList();
                    return PhrRouteSupport.jsonResponse(200, Map.of("patientId", patientId, "items", dtos, "count", dtos.size()), correlationId);
                })));
    }

    private <T> Promise<HttpResponse> withBodyAndConsent(
            HttpRequest request,
            String resourceType,
            String action,
            Class<T> type,
            java.util.function.Function<T, Promise<HttpResponse>> handler) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage(), correlationId);
        }
        PhrRouteSupport.PhrRequestContext finalContext = context;
        return request.loadBody()
            .then(body -> {
                T value;
                String patientId;
                try {
                    String json = body.getString(StandardCharsets.UTF_8);
                    value = PhrRouteSupport.JSON.readValue(json, type);
                    patientId = patientIdFrom(json);
                } catch (Exception ex) {
                    return PhrRouteSupport.errorResponse(400, "INVALID_" + resourceType.toUpperCase().replace('-', '_'), ex.getMessage(), correlationId);
                }
                return requireAccess(finalContext, patientId, resourceType, action)
                    .then(decision -> decision.isAllowed()
                        ? handler.apply(value)
                        : PhrRouteSupport.policyDenialResponse(403, finalContext.correlationId(), decision.getReasonCode()));
            });
    }

    private Promise<HttpResponse> withPatientAccess(
            HttpRequest request,
            String resourceType,
            String action,
            java.util.function.Function<String, Promise<HttpResponse>> handler) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        String patientId;
        try {
            context = PhrRouteSupport.requireContext(request);
            patientId = PhrRouteSupport.requiredQuery(request, "patientId");
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "INVALID_PATIENT_SCOPE", ex.getMessage(), correlationId);
        }
        return requireAccess(context, patientId, resourceType, action)
            .then(decision -> decision.isAllowed()
                ? handler.apply(patientId)
                : PhrRouteSupport.policyDenialResponse(403, context.correlationId(), "POLICY_DENIED"));
    }

    private Promise<PhrPolicyEvaluator.PolicyDecision> requireAccess(
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

    private static Map<String, Object> labObservationDto(LabResultService.LabObservation observation) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", observation.id());
        dto.put("name", firstNonBlank(observation.testName(), observation.loincDisplay(), observation.loincCode()));
        dto.put("value", observation.value() != null ? observation.value().toPlainString() : "");
        dto.put("unit", observation.unit() != null ? observation.unit() : "");
        dto.put("status", observationStatus(observation));
        dto.put("category", "laboratory");
        dto.put("observationCategory", "laboratory");
        dto.put("effectiveDate", instantString(observation.orderedAt(), observation.resultedAt()));
        dto.put("recordedAt", instantString(observation.resultedAt(), observation.orderedAt()));
        dto.put("resultedAt", instantString(observation.resultedAt(), observation.orderedAt()));
        dto.put("loincCode", observation.loincCode());
        dto.put("loincDisplay", observation.loincDisplay());
        dto.put("referenceRange", observation.referenceRange());
        dto.put("referenceRangeLow", observation.referenceRangeLow());
        dto.put("abnormal", observation.isAbnormal());
        dto.put("interpretation", observation.interpretation());
        dto.put("source", Map.of(
            "system", "lab-result-service",
            "performingLabId", observation.performingLabId() != null ? observation.performingLabId() : "unknown"
        ));
        dto.put("fhir", labObservationFhir(observation));
        return dto;
    }

    private static Map<String, Object> medicationDto(
            MedicationService.Prescription prescription,
            List<MedicationService.InteractionWarning> interactions) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", prescription.id());
        dto.put("medication", prescription.medicationName());
        dto.put("medicationName", prescription.medicationName());
        dto.put("medicationCode", prescription.medicationCode());
        dto.put("dosage", firstNonBlank(prescription.dosage(), "unspecified"));
        dto.put("route", null);
        dto.put("routeSource", "not-collected");
        dto.put("frequency", medicationFrequency(prescription.duration()));
        dto.put("schedule", medicationFrequency(prescription.duration()));
        dto.put("status", medicationStatus(prescription));
        dto.put("prescriberId", prescription.prescriberId());
        dto.put("encounterId", prescription.encounterId());
        dto.put("indication", prescription.indication());
        dto.put("prescribedAt", instantString(prescription.prescribedAt(), null));
        dto.put("startDate", instantString(prescription.prescribedAt(), null));
        dto.put("expiresAt", instantString(prescription.expiresAt(), null));
        dto.put("endDate", instantString(prescription.expiresAt(), null));
        dto.put("refillsRemaining", prescription.refillsRemaining());
        dto.put("adherenceStatus", Map.of(
            "measured", false,
            "source", "not-collected"
        ));
        dto.put("interactions", interactionDescriptions(prescription, interactions));
        dto.put("warnings", interactionRecommendations(prescription, interactions));
        dto.put("fhir", medicationFhir(prescription));
        return dto;
    }

    private static Map<String, Object> medicationFhir(MedicationService.Prescription prescription) {
        Map<String, Object> fhir = new LinkedHashMap<>();
        fhir.put("resourceType", "MedicationRequest");
        fhir.put("id", prescription.id());
        fhir.put("status", medicationFhirStatus(prescription));
        fhir.put("intent", "order");
        fhir.put("subject", Map.of("reference", "Patient/" + prescription.patientId()));
        if (prescription.encounterId() != null) {
            fhir.put("encounter", Map.of("reference", "Encounter/" + prescription.encounterId()));
        }
        fhir.put("authoredOn", instantString(prescription.prescribedAt(), null));
        if (prescription.prescriberId() != null) {
            fhir.put("requester", Map.of("reference", "Practitioner/" + prescription.prescriberId()));
        }
        fhir.put("medicationCodeableConcept", Map.of(
            "coding", List.of(Map.of(
                "code", prescription.medicationCode() != null ? prescription.medicationCode() : "",
                "display", prescription.medicationName() != null ? prescription.medicationName() : ""
            )),
            "text", prescription.medicationName() != null ? prescription.medicationName() : ""
        ));
        fhir.put("dosageInstruction", List.of(Map.of(
            "text", firstNonBlank(prescription.dosage(), "unspecified"),
            "timing", Map.of("repeat", Map.of("frequency", 1, "period", medicationPeriodDays(prescription.duration()), "periodUnit", "d"))
        )));
        return fhir;
    }

    private static List<String> interactionDescriptions(
            MedicationService.Prescription prescription,
            List<MedicationService.InteractionWarning> interactions) {
        return interactions.stream()
            .filter(warning -> warningApplies(prescription, warning))
            .map(MedicationService.InteractionWarning::description)
            .toList();
    }

    private static List<String> interactionRecommendations(
            MedicationService.Prescription prescription,
            List<MedicationService.InteractionWarning> interactions) {
        return interactions.stream()
            .filter(warning -> warningApplies(prescription, warning))
            .map(warning -> warning.severity().name() + ": " + warning.recommendation())
            .toList();
    }

    private static boolean warningApplies(MedicationService.Prescription prescription, MedicationService.InteractionWarning warning) {
        return prescription.medicationCode() != null
            && (prescription.medicationCode().equals(warning.medicationCode1())
                || prescription.medicationCode().equals(warning.medicationCode2()));
    }

    private static String medicationFrequency(java.time.Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            return "unspecified";
        }
        long days = duration.toDays();
        if (days <= 1) {
            return "daily";
        }
        return "every " + days + " days";
    }

    private static long medicationPeriodDays(java.time.Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            return 1;
        }
        return Math.max(1, duration.toDays());
    }

    private static String medicationStatus(MedicationService.Prescription prescription) {
        if (prescription.status() == MedicationService.PrescriptionStatus.DISCONTINUED) {
            return "stopped";
        }
        if (prescription.status() == MedicationService.PrescriptionStatus.COMPLETED
            || prescription.status() == MedicationService.PrescriptionStatus.EXPIRED
            || prescription.isExpired()) {
            return "history";
        }
        return "active";
    }

    private static String medicationFhirStatus(MedicationService.Prescription prescription) {
        if (prescription.status() == null) {
            return "unknown";
        }
        return switch (prescription.status()) {
            case ACTIVE -> "active";
            case COMPLETED -> "completed";
            case DISCONTINUED -> "stopped";
            case EXPIRED -> "ended";
        };
    }

    private static Map<String, Object> labObservationFhir(LabResultService.LabObservation observation) {
        Map<String, Object> fhir = new LinkedHashMap<>();
        fhir.put("resourceType", "Observation");
        fhir.put("id", observation.id());
        fhir.put("status", observation.status() != null ? observation.status().name().toLowerCase() : "unknown");
        fhir.put("category", List.of(Map.of("coding", List.of(Map.of(
            "system", "http://terminology.hl7.org/CodeSystem/observation-category",
            "code", "laboratory",
            "display", "Laboratory"
        )))));
        fhir.put("subject", Map.of("reference", "Patient/" + observation.patientId()));
        fhir.put("code", Map.of(
            "coding", List.of(Map.of(
                "system", "http://loinc.org",
                "code", observation.loincCode() != null ? observation.loincCode() : "",
                "display", observation.loincDisplay() != null ? observation.loincDisplay() : ""
            )),
            "text", firstNonBlank(observation.testName(), observation.loincDisplay(), observation.loincCode())
        ));
        fhir.put("effectiveDateTime", instantString(observation.orderedAt(), observation.resultedAt()));
        fhir.put("issued", instantString(observation.resultedAt(), observation.orderedAt()));
        if (observation.value() != null) {
            fhir.put("valueQuantity", Map.of(
                "value", observation.value(),
                "unit", observation.unit() != null ? observation.unit() : ""
            ));
        }
        if (observation.referenceRange() != null) {
            fhir.put("referenceRange", List.of(Map.of("text", observation.referenceRange())));
        }
        if (observation.interpretation() != null && !observation.interpretation().isBlank()) {
            fhir.put("interpretation", List.of(Map.of("coding", List.of(Map.of("code", observation.interpretation())))));
        }
        return fhir;
    }

    private static String observationStatus(LabResultService.LabObservation observation) {
        if (observation.status() == LabResultService.ObservationStatus.PRELIMINARY) {
            return "pending";
        }
        String interpretation = observation.interpretation();
        if (interpretation == null || interpretation.isBlank() || "N".equalsIgnoreCase(interpretation)) {
            return "normal";
        }
        if ("HH".equalsIgnoreCase(interpretation) || "LL".equalsIgnoreCase(interpretation) || "critical".equalsIgnoreCase(interpretation)) {
            return "critical";
        }
        return "attention";
    }

    private static Map<String, Object> immunizationDto(
            ImmunizationService.ImmunizationRecord immunization,
            List<ImmunizationService.VaccinationSchedule> schedules) {
        Map<String, Object> dto = new LinkedHashMap<>();
        String occurrenceDate = instantString(immunization.administeredAt(), immunization.recordedAt());
        dto.put("id", immunization.id());
        dto.put("vaccine", firstNonBlank(immunization.vaccineName(), immunization.cvxCode()));
        dto.put("vaccineName", firstNonBlank(immunization.vaccineName(), immunization.cvxCode()));
        dto.put("date", occurrenceDate);
        dto.put("occurrenceDate", occurrenceDate);
        dto.put("dose", immunization.doseNumber() > 0 ? String.valueOf(immunization.doseNumber()) : "");
        dto.put("doseNumber", immunization.doseNumber());
        dto.put("lotNumber", immunization.lotNumber());
        dto.put("cvxCode", immunization.cvxCode());
        dto.put("route", immunization.route());
        dto.put("seriesName", immunization.seriesName());
        dto.put("status", immunizationStatus(immunization.status()));
        dto.put("source", immunizationSource(immunization));
        matchingNextDue(immunization, schedules).ifPresent(schedule -> dto.put("nextDue", vaccinationScheduleDto(schedule)));
        dto.put("fhir", immunizationFhir(immunization));
        return dto;
    }

    private static Map<String, Object> dueImmunizationDto(ImmunizationService.VaccinationSchedule schedule) {
        Map<String, Object> dto = new LinkedHashMap<>();
        String dueDate = instantString(schedule.dueDate(), null);
        dto.put("id", schedule.id());
        dto.put("vaccine", firstNonBlank(schedule.vaccineName(), schedule.cvxCode()));
        dto.put("vaccineName", firstNonBlank(schedule.vaccineName(), schedule.cvxCode()));
        dto.put("date", dueDate);
        dto.put("occurrenceDate", dueDate);
        if (schedule.doseNumber() > 0) {
            dto.put("dose", String.valueOf(schedule.doseNumber()));
            dto.put("doseNumber", schedule.doseNumber());
        }
        dto.put("cvxCode", schedule.cvxCode());
        dto.put("seriesName", schedule.seriesName());
        dto.put("status", "due");
        dto.put("source", Map.of("system", "immunization-schedule-service"));
        dto.put("nextDue", vaccinationScheduleDto(schedule));
        return dto;
    }

    private static Map<String, Object> immunizationSource(ImmunizationService.ImmunizationRecord immunization) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("system", "immunization-service");
        String administeredBy = firstNonBlank(immunization.administeredBy());
        if (administeredBy != null) {
            source.put("administeredBy", administeredBy);
        }
        return source;
    }

    private static java.util.Optional<ImmunizationService.VaccinationSchedule> matchingNextDue(
            ImmunizationService.ImmunizationRecord immunization,
            List<ImmunizationService.VaccinationSchedule> schedules) {
        if (schedules == null || schedules.isEmpty()) {
            return java.util.Optional.empty();
        }
        return schedules.stream()
            .filter(schedule -> schedule.status() == ImmunizationService.ScheduleStatus.PENDING)
            .filter(schedule -> sameVaccine(immunization, schedule))
            .filter(schedule -> schedule.doseNumber() > immunization.doseNumber())
            .findFirst()
            .or(() -> schedules.stream()
                .filter(schedule -> schedule.status() == ImmunizationService.ScheduleStatus.PENDING)
                .filter(schedule -> sameVaccine(immunization, schedule))
                .findFirst());
    }

    private static boolean sameVaccine(
            ImmunizationService.ImmunizationRecord immunization,
            ImmunizationService.VaccinationSchedule schedule) {
        return nonBlankEquals(immunization.cvxCode(), schedule.cvxCode())
            || nonBlankEquals(immunization.seriesName(), schedule.seriesName())
            || nonBlankEquals(immunization.vaccineName(), schedule.vaccineName());
    }

    private static boolean nonBlankEquals(String left, String right) {
        return left != null && right != null && !left.isBlank() && left.equalsIgnoreCase(right);
    }

    private static Map<String, Object> vaccinationScheduleDto(ImmunizationService.VaccinationSchedule schedule) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", schedule.id());
        dto.put("vaccine", firstNonBlank(schedule.vaccineName(), schedule.cvxCode()));
        dto.put("vaccineName", firstNonBlank(schedule.vaccineName(), schedule.cvxCode()));
        dto.put("cvxCode", schedule.cvxCode());
        dto.put("seriesName", schedule.seriesName());
        dto.put("doseNumber", schedule.doseNumber());
        dto.put("dueDate", instantString(schedule.dueDate(), null));
        dto.put("status", schedule.status() != null ? schedule.status().name().toLowerCase() : "pending");
        return dto;
    }

    private static Map<String, Object> immunizationFhir(ImmunizationService.ImmunizationRecord immunization) {
        Map<String, Object> fhir = new LinkedHashMap<>();
        fhir.put("resourceType", "Immunization");
        fhir.put("id", immunization.id());
        fhir.put("status", immunizationFhirStatus(immunization.status()));
        fhir.put("vaccineCode", Map.of(
            "coding", List.of(Map.of(
                "system", "http://hl7.org/fhir/sid/cvx",
                "code", immunization.cvxCode() != null ? immunization.cvxCode() : "",
                "display", immunization.vaccineName() != null ? immunization.vaccineName() : ""
            )),
            "text", firstNonBlank(immunization.vaccineName(), immunization.cvxCode())
        ));
        fhir.put("patient", Map.of("reference", "Patient/" + immunization.patientId()));
        fhir.put("occurrenceDateTime", instantString(immunization.administeredAt(), immunization.recordedAt()));
        fhir.put("recorded", instantString(immunization.recordedAt(), immunization.administeredAt()));
        fhir.put("primarySource", true);
        if (immunization.encounterId() != null) {
            fhir.put("encounter", Map.of("reference", "Encounter/" + immunization.encounterId()));
        }
        if (immunization.administeredBy() != null) {
            fhir.put("performer", List.of(Map.of("actor", Map.of("reference", "Practitioner/" + immunization.administeredBy()))));
        }
        if (immunization.lotNumber() != null) {
            fhir.put("lotNumber", immunization.lotNumber());
        }
        if (immunization.expiresAt() != null) {
            fhir.put("expirationDate", immunization.expiresAt().toString());
        }
        if (immunization.route() != null) {
            fhir.put("route", Map.of("coding", List.of(Map.of(
                "system", "http://terminology.hl7.org/CodeSystem/v3-RouteOfAdministration",
                "code", immunization.route()
            ))));
        }
        Map<String, Object> protocol = new LinkedHashMap<>();
        if (immunization.seriesName() != null) {
            protocol.put("series", immunization.seriesName());
        }
        protocol.put("doseNumberPositiveInt", immunization.doseNumber());
        fhir.put("protocolApplied", List.of(protocol));
        return fhir;
    }

    private static String immunizationStatus(ImmunizationService.ImmunizationStatus status) {
        if (status == ImmunizationService.ImmunizationStatus.NOT_DONE) {
            return "not-done";
        }
        if (status == ImmunizationService.ImmunizationStatus.ENTERED_IN_ERROR) {
            return "entered-in-error";
        }
        return "completed";
    }

    private static String immunizationFhirStatus(ImmunizationService.ImmunizationStatus status) {
        if (status == ImmunizationService.ImmunizationStatus.NOT_DONE) {
            return "not-done";
        }
        if (status == ImmunizationService.ImmunizationStatus.ENTERED_IN_ERROR) {
            return "entered-in-error";
        }
        return "completed";
    }

    private static String instantString(java.time.Instant preferred, java.time.Instant fallback) {
        java.time.Instant value = preferred != null ? preferred : fallback;
        return value != null ? value.toString() : "";
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String patientIdFrom(String json) throws java.io.IOException {
        JsonNode node = PhrRouteSupport.JSON.readTree(json);
        String patientId = node.path("patientId").asText(null);
        if (patientId == null || patientId.isBlank()) {
            throw new IllegalArgumentException("patientId is required");
        }
        return patientId;
    }
}
