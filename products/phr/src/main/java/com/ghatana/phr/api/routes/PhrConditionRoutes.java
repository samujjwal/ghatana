package com.ghatana.phr.api.routes;

import com.ghatana.phr.application.clinical.ClinicalService;
import com.ghatana.phr.application.patient.PatientOperationContext;
import com.ghatana.phr.kernel.service.PatientRecordServiceExtensions;
import com.ghatana.phr.security.PhrPolicyEvaluator;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.RoutingServlet;
import io.activej.promise.Promise;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Clinical condition API for the PHR product.
 *
 * <p>Returns active and resolved diagnoses for a given patient.
 * Access follows the standard patient-record access policy.
 *
 * @doc.type class
 * @doc.purpose ActiveJ route adapter for FHIR Condition resources
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 */
public final class PhrConditionRoutes {

    private static final String RESOURCE_TYPE = "conditions";

    private final Eventloop eventloop;
    private final PhrPolicyEvaluator policyEvaluator;
    private final ClinicalService clinicalService;

    public PhrConditionRoutes(
            Eventloop eventloop,
            PhrPolicyEvaluator policyEvaluator,
            ClinicalService clinicalService) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
        this.policyEvaluator = Objects.requireNonNull(policyEvaluator, "policyEvaluator must not be null");
        this.clinicalService = Objects.requireNonNull(clinicalService, "clinicalService must not be null");
    }

    public PhrConditionRoutes(
            Eventloop eventloop,
            PhrPolicyEvaluator policyEvaluator,
            PatientRecordServiceExtensions patientRecordServiceExtensions) {
        this(eventloop, policyEvaluator, new PatientMedicalHistoryConditionService(patientRecordServiceExtensions));
    }

    /**
     * Returns the routing servlet for condition endpoints.
     *
     * @return routing servlet; never null
     */
    public AsyncServlet getServlet() {
        return RoutingServlet.builder(eventloop)
            .with(HttpMethod.GET, "/", this::handleListConditions)
            .with(HttpMethod.GET, "/:conditionId", this::handleGetCondition)
            .with(HttpMethod.GET, "/patient/:patientId", this::handleListConditionsByPath)
            .build();
    }

    private Promise<HttpResponse> handleListConditions(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        String patientId;
        try {
            context = PhrRouteSupport.requireContext(request);
            patientId = PhrRouteSupport.requiredQuery(request, "patientId");
        } catch (RuntimeException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage(), correlationId);
        }

        return authorize(context, patientId)
            .then(decision -> {
                if (!decision.isAllowed()) {
                    return PhrRouteSupport.policyDenialResponse(403, context.correlationId(), decision.getReasonCode());
                }

                PatientOperationContext opCtx = operationContext(context, patientId);
                return clinicalService.listConditions(opCtx, patientId)
                    .then(conditions -> {
                        List<Map<String, Object>> conditionMaps = conditions.stream()
                            .map(PhrConditionRoutes::conditionDto)
                            .toList();

                        Map<String, Object> response = new LinkedHashMap<>();
                        response.put("patientId", patientId);
                        response.put("items", conditionMaps);
                        response.put("count", conditionMaps.size());
                        return PhrRouteSupport.jsonResponseWithCorrelation(200, response, context.correlationId());
                    });
            });
    }

    private Promise<HttpResponse> handleListConditionsByPath(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        String patientId = request.getPathParameter("patientId");
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage(), correlationId);
        }
        return authorize(context, patientId).then(decision -> {
            if (!decision.isAllowed()) {
                return PhrRouteSupport.policyDenialResponse(403, context.correlationId(), decision.getReasonCode());
            }
            PatientOperationContext opCtx = operationContext(context, patientId);
            return clinicalService.listConditions(opCtx, patientId)
                .then(conditions -> {
                    List<Map<String, Object>> conditionMaps = conditions.stream()
                        .map(PhrConditionRoutes::conditionDto)
                        .toList();

                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("patientId", patientId);
                    response.put("items", conditionMaps);
                    response.put("count", conditionMaps.size());
                    return PhrRouteSupport.jsonResponseWithCorrelation(200, response, context.correlationId());
                });
        });
    }

    private Promise<HttpResponse> handleGetCondition(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        String patientId;
        try {
            context = PhrRouteSupport.requireContext(request);
            patientId = PhrRouteSupport.requiredQuery(request, "patientId");
        } catch (RuntimeException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage(), correlationId);
        }

        return authorize(context, patientId)
            .then(decision -> {
                if (!decision.isAllowed()) {
                    return PhrRouteSupport.policyDenialResponse(403, context.correlationId(), decision.getReasonCode());
                }
                PatientOperationContext opCtx = operationContext(context, patientId);
                return clinicalService.getCondition(opCtx, request.getPathParameter("conditionId"))
                    .then(condition -> {
                        if (condition.isEmpty() || !patientId.equals(condition.get().patientId())) {
                            return PhrRouteSupport.errorResponse(404, "CONDITION_NOT_FOUND", "Condition not found", context.correlationId());
                        }
                        return PhrRouteSupport.jsonResponseWithCorrelation(200, conditionDto(condition.get()), context.correlationId());
                    });
            });
    }

    private Promise<PhrPolicyEvaluator.PolicyDecision> authorize(PhrRouteSupport.PhrRequestContext context, String patientId) {
        return policyEvaluator.canAccessPhiResourceAsync(
            context,
            patientId,
            RESOURCE_TYPE,
            "READ",
            context.tenantId(),
            context.facilityId()
        );
    }

    private static PatientOperationContext operationContext(PhrRouteSupport.PhrRequestContext context, String patientId) {
        return new PatientOperationContext(
            context.tenantId(),
            "default",
            context.principalId(),
            patientId,
            context.correlationId()
        );
    }

    private static Map<String, Object> conditionDto(ClinicalService.Condition condition) {
        String status = normalizedStatus(condition);
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", condition.conditionId());
        dto.put("name", condition.conditionName());
        dto.put("display", condition.conditionName());
        dto.put("code", condition.code());
        dto.put("icdCode", condition.code());
        dto.put("status", status);
        dto.put("clinicalStatus", status);
        dto.put("verificationStatus", "confirmed");
        dto.put("severity", condition.severity());
        dto.put("onsetDate", condition.recordedAt());
        if (condition.resolvedAt() != null) {
            dto.put("resolvedDate", condition.resolvedAt());
        }
        dto.put("fhir", fhirCondition(condition, status));
        return dto;
    }

    private static Map<String, Object> fhirCondition(ClinicalService.Condition condition, String status) {
        Map<String, Object> fhir = new LinkedHashMap<>();
        fhir.put("resourceType", "Condition");
        fhir.put("id", condition.conditionId());
        fhir.put("subject", Map.of("reference", "Patient/" + condition.patientId()));
        fhir.put("clinicalStatus", Map.of("coding", List.of(Map.of(
            "system", "http://terminology.hl7.org/CodeSystem/condition-clinical",
            "code", "resolved".equals(status) ? "resolved" : "active"
        ))));
        fhir.put("verificationStatus", Map.of("coding", List.of(Map.of(
            "system", "http://terminology.hl7.org/CodeSystem/condition-ver-status",
            "code", "confirmed"
        ))));
        fhir.put("code", Map.of(
            "coding", List.of(Map.of(
                "system", "http://snomed.info/sct",
                "code", condition.code() != null ? condition.code() : "",
                "display", condition.conditionName()
            )),
            "text", condition.conditionName()
        ));
        fhir.put("onsetDateTime", condition.recordedAt());
        if (condition.resolvedAt() != null) {
            fhir.put("abatementDateTime", condition.resolvedAt());
        }
        return fhir;
    }

    private static String normalizedStatus(ClinicalService.Condition condition) {
        if ("RESOLVED".equalsIgnoreCase(condition.status()) || condition.resolvedAt() != null) {
            return "resolved";
        }
        if ("chronic".equalsIgnoreCase(condition.severity())) {
            return "chronic";
        }
        return "active";
    }

    private static final class PatientMedicalHistoryConditionService implements ClinicalService {
        private final PatientRecordServiceExtensions patientRecordServiceExtensions;

        private PatientMedicalHistoryConditionService(PatientRecordServiceExtensions patientRecordServiceExtensions) {
            this.patientRecordServiceExtensions = Objects.requireNonNull(patientRecordServiceExtensions, "patientRecordServiceExtensions must not be null");
        }

        @Override
        public Promise<List<Condition>> listConditions(PatientOperationContext ctx, String patientId) {
            return patientRecordServiceExtensions.getActiveConditions(patientId)
                .map(items -> items.stream()
                    .map(item -> new Condition(
                        item.getId(),
                        patientId,
                        item.getDisplay(),
                        item.getCode(),
                        item.getChronicity(),
                        item.getStatus(),
                        item.getOnsetDate(),
                        null
                    ))
                    .toList());
        }

        @Override
        public Promise<java.util.Optional<Condition>> getCondition(PatientOperationContext ctx, String conditionId) {
            return listConditions(ctx, ctx.patientId())
                .map(items -> items.stream()
                    .filter(item -> conditionId.equals(item.conditionId()))
                    .findFirst());
        }

        @Override public Promise<Encounter> createEncounter(PatientOperationContext ctx, CreateEncounterRequest request) { return unsupported(); }
        @Override public Promise<java.util.Optional<Encounter>> getEncounter(PatientOperationContext ctx, String encounterId) { return unsupported(); }
        @Override public Promise<Encounter> updateEncounter(PatientOperationContext ctx, String encounterId, UpdateEncounterRequest request) { return unsupported(); }
        @Override public Promise<Encounter> completeEncounter(PatientOperationContext ctx, String encounterId) { return unsupported(); }
        @Override public Promise<List<Encounter>> listEncounters(PatientOperationContext ctx, String patientId) { return unsupported(); }
        @Override public Promise<Medication> prescribeMedication(PatientOperationContext ctx, PrescribeMedicationRequest request) { return unsupported(); }
        @Override public Promise<java.util.Optional<Medication>> getMedication(PatientOperationContext ctx, String medicationId) { return unsupported(); }
        @Override public Promise<Medication> recordAdministration(PatientOperationContext ctx, String medicationId, AdministrationRecord record) { return unsupported(); }
        @Override public Promise<List<Medication>> listMedications(PatientOperationContext ctx, String patientId) { return unsupported(); }
        @Override public Promise<Medication> requestRefill(PatientOperationContext ctx, String medicationId) { return unsupported(); }
        @Override public Promise<Allergy> recordAllergy(PatientOperationContext ctx, RecordAllergyRequest request) { return unsupported(); }
        @Override public Promise<java.util.Optional<Allergy>> getAllergy(PatientOperationContext ctx, String allergyId) { return unsupported(); }
        @Override public Promise<List<Allergy>> listAllergies(PatientOperationContext ctx, String patientId) { return unsupported(); }
        @Override public Promise<AllergyCrossCheck> getMedicationCrossCheck(PatientOperationContext ctx, String patientId) { return unsupported(); }
        @Override public Promise<Condition> recordCondition(PatientOperationContext ctx, RecordConditionRequest request) { return unsupported(); }
        @Override public Promise<Condition> resolveCondition(PatientOperationContext ctx, String conditionId) { return unsupported(); }
        @Override public Promise<LabOrder> orderLab(PatientOperationContext ctx, OrderLabRequest request) { return unsupported(); }
        @Override public Promise<java.util.Optional<LabOrder>> getLabOrder(PatientOperationContext ctx, String labId) { return unsupported(); }
        @Override public Promise<LabResult> recordLabResult(PatientOperationContext ctx, String labId, LabResult result) { return unsupported(); }
        @Override public Promise<java.util.Optional<LabResult>> getLabResult(PatientOperationContext ctx, String labId) { return unsupported(); }
        @Override public Promise<List<LabOrder>> listLabOrders(PatientOperationContext ctx, String patientId) { return unsupported(); }
        @Override public Promise<Immunization> recordImmunization(PatientOperationContext ctx, RecordImmunizationRequest request) { return unsupported(); }
        @Override public Promise<java.util.Optional<Immunization>> getImmunization(PatientOperationContext ctx, String immunizationId) { return unsupported(); }
        @Override public Promise<List<Immunization>> listImmunizations(PatientOperationContext ctx, String patientId) { return unsupported(); }
        @Override public Promise<ImmunizationSchedule> getImmunizationSchedule(PatientOperationContext ctx, String patientId) { return unsupported(); }
        @Override public Promise<String> generateCertificate(PatientOperationContext ctx, String patientId) { return unsupported(); }
        @Override public Promise<Document> uploadDocument(PatientOperationContext ctx, UploadDocumentRequest request) { return unsupported(); }
        @Override public Promise<java.util.Optional<Document>> getDocument(PatientOperationContext ctx, String documentId) { return unsupported(); }
        @Override public Promise<Document> updateDocumentMetadata(PatientOperationContext ctx, String documentId, DocumentMetadata metadata) { return unsupported(); }
        @Override public Promise<List<Document>> listDocuments(PatientOperationContext ctx, String patientId) { return unsupported(); }

        private static <T> Promise<T> unsupported() {
            return Promise.ofException(new UnsupportedOperationException("Only condition reads are supported by this adapter"));
        }
    }
}
