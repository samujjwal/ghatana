package com.ghatana.phr.api.routes;

import com.ghatana.phr.application.clinical.ClinicalService;
import com.ghatana.phr.application.clinical.ClinicalService.CreateEncounterRequest;
import com.ghatana.phr.application.clinical.ClinicalService.UpdateEncounterRequest;
import com.ghatana.phr.application.clinical.ClinicalService.PrescribeMedicationRequest;
import com.ghatana.phr.application.patient.PatientOperationContext;
import com.ghatana.phr.kernel.service.ConsentManagementService;
import com.ghatana.phr.kernel.service.PatientRecordService;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.RoutingServlet;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Provider (clinician) API for the PHR product.
 *
 * <p>Provides clinician-scoped endpoints for listing patients and accessing
 * clinical summaries. All endpoints require clinical role and enforce
 * consent/treatment relationship policy.
 *
 * @doc.type class
 * @doc.purpose ActiveJ route adapter for provider patient roster and clinical summary access
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 */
public final class PhrProviderRoutes {

    private final Eventloop eventloop;
    private final PatientRecordService patientRecordService;
    private final ConsentManagementService consentService;
    private final ClinicalService clinicalService;

    public PhrProviderRoutes(
            Eventloop eventloop,
            PatientRecordService patientRecordService,
            ConsentManagementService consentService,
            ClinicalService clinicalService) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
        this.patientRecordService = Objects.requireNonNull(patientRecordService, "patientRecordService must not be null");
        this.consentService = Objects.requireNonNull(consentService, "consentService must not be null");
        this.clinicalService = Objects.requireNonNull(clinicalService, "clinicalService must not be null");
    }

    /**
     * Returns the routing servlet for provider endpoints.
     *
     * @return routing servlet; never null
     */
    public AsyncServlet getServlet() {
        return RoutingServlet.builder(eventloop)
            .with(HttpMethod.GET, "/patients", this::handleGetPatients)
            .with(HttpMethod.GET, "/patient/:patientId/summary", this::handleGetPatientSummary)
            .with(HttpMethod.GET, "/patient/:patientId/detail", this::handleGetPatientDetail)
            .with(HttpMethod.POST, "/patient/:patientId/encounters", this::handleCreateEncounter)
            .with(HttpMethod.GET, "/patient/:patientId/encounters/:encounterId", this::handleGetEncounter)
            .with(HttpMethod.PUT, "/patient/:patientId/encounters/:encounterId", this::handleUpdateEncounter)
            .with(HttpMethod.POST, "/patient/:patientId/encounters/:encounterId/complete", this::handleCompleteEncounter)
            .with(HttpMethod.POST, "/patient/:patientId/medications", this::handlePrescribeMedication)
            .with(HttpMethod.GET, "/calendar", this::handleGetProviderCalendar)
            .build();
    }

    private Promise<HttpResponse> handleGetPatients(HttpRequest request) {
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }

        if (!("clinician".equals(context.role()) || "admin".equals(context.role()))) {
            return PhrRouteSupport.errorResponse(403, "CLINICAL_ROLE_REQUIRED",
                "Only clinician or admin principals may access the patient roster",
                context.correlationId());
        }

        String searchQuery = request.getQueryParameter("q");
        String limitParam = request.getQueryParameter("limit");
        int limit = limitParam != null ? Integer.parseInt(limitParam) : 50;

        return patientRecordService.searchPatients(
            "deleted = false",
            Map.of(),
            limit,
            0
        ).then(patients -> {
            List<Map<String, Object>> patientSummaries = patients.stream()
                .map(patient -> Map.<String, Object>of(
                    "id", patient.getId(),
                    "name", patient.getDemographics().getFullName(),
                    "age", patient.getDemographics().getAge(),
                    "status", "active",
                    "hasConsent", true,
                    "lastVisit", patient.getMedicalHistory() != null ? "Recent" : "Unknown"
                ))
                .toList();
            return PhrRouteSupport.jsonResponse(200, Map.of(
                "patients", patientSummaries,
                "total", patientSummaries.size()
            ), context.correlationId());
        });
    }

    private Promise<HttpResponse> handleGetPatientSummary(HttpRequest request) {
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }

        if (!("clinician".equals(context.role()) || "admin".equals(context.role()))) {
            return PhrRouteSupport.errorResponse(403, "CLINICAL_ROLE_REQUIRED",
                "Only clinician or admin principals may view patient clinical summaries",
                context.correlationId());
        }

        String patientId = request.getPathParameter("patientId");
        
        // Check consent/treatment relationship before accessing patient data
        return consentService.checkAccess(
            new com.ghatana.phr.kernel.consent.ConsentService.ConsentCheckRequest(
                UUID.randomUUID().toString(),
                context.tenantId(),
                new com.ghatana.phr.kernel.consent.ConsentService.ActorContext(
                    context.principalId(),
                    com.ghatana.phr.kernel.consent.ConsentService.ActorType.PROVIDER,
                    null,
                    context.principalId(),
                    null,
                    Set.of()
                ),
                new com.ghatana.phr.kernel.consent.ConsentService.TargetResource(
                    patientId,
                    "Patient",
                    null,
                    com.ghatana.phr.kernel.policy.PhrDataClassification.C3
                ),
                com.ghatana.phr.kernel.consent.ConsentService.ConsentAction.PATIENT_READ,
                com.ghatana.phr.kernel.consent.ConsentService.PurposeOfUse.CARE_DELIVERY,
                null
            )
        ).then(decision -> {
            if (!decision.allowed()) {
                return PhrRouteSupport.errorResponse(403, "CONSENT_REQUIRED",
                    "Patient has not granted consent for this clinician to access their data",
                    context.correlationId());
            }
            
            return patientRecordService.getPatient(patientId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return PhrRouteSupport.errorResponse(404, "PATIENT_NOT_FOUND",
                            "Patient not found: " + patientId,
                            context.correlationId());
                    }
                    var patient = opt.get();
                    return PhrRouteSupport.jsonResponse(200, Map.of(
                        "patientId", patientId,
                        "tenantId", context.tenantId(),
                        "clinicianId", context.principalId(),
                        "name", patient.getDemographics().getFullName(),
                        "age", patient.getDemographics().getAge(),
                        "summary", "Clinical summary for patient",
                        "consentStatus", decision.reasonCode().name()
                    ), context.correlationId());
                });
        });
    }

    private Promise<HttpResponse> handleGetPatientDetail(HttpRequest request) {
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }

        if (!("clinician".equals(context.role()) || "admin".equals(context.role()))) {
            return PhrRouteSupport.errorResponse(403, "CLINICAL_ROLE_REQUIRED",
                "Only clinician or admin principals may view patient details",
                context.correlationId());
        }

        String patientId = request.getPathParameter("patientId");
        
        // Check consent/treatment relationship before accessing patient data
        return consentService.checkAccess(
            new com.ghatana.phr.kernel.consent.ConsentService.ConsentCheckRequest(
                UUID.randomUUID().toString(),
                context.tenantId(),
                new com.ghatana.phr.kernel.consent.ConsentService.ActorContext(
                    context.principalId(),
                    com.ghatana.phr.kernel.consent.ConsentService.ActorType.PROVIDER,
                    null,
                    context.principalId(),
                    null,
                    Set.of()
                ),
                new com.ghatana.phr.kernel.consent.ConsentService.TargetResource(
                    patientId,
                    "Patient",
                    null,
                    com.ghatana.phr.kernel.policy.PhrDataClassification.C3
                ),
                com.ghatana.phr.kernel.consent.ConsentService.ConsentAction.PATIENT_READ,
                com.ghatana.phr.kernel.consent.ConsentService.PurposeOfUse.CARE_DELIVERY,
                null
            )
        ).then(decision -> {
            if (!decision.allowed()) {
                return PhrRouteSupport.errorResponse(403, "CONSENT_REQUIRED",
                    "Patient has not granted consent for this clinician to access their detailed data",
                    context.correlationId());
            }
            
            return patientRecordService.getPatient(patientId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return PhrRouteSupport.errorResponse(404, "PATIENT_NOT_FOUND",
                            "Patient not found: " + patientId,
                            context.correlationId());
                    }
                    var patient = opt.get();
                    var demographics = patient.getDemographics();
                    var medicalHistory = patient.getMedicalHistory();
                    
                    return PhrRouteSupport.jsonResponse(200, Map.of(
                        "patientId", patientId,
                        "tenantId", context.tenantId(),
                        "clinicianId", context.principalId(),
                        "demographics", Map.of(
                            "fullName", demographics.getFullName(),
                            "age", demographics.getAge(),
                            "gender", demographics.getGender(),
                            "bloodType", medicalHistory != null ? medicalHistory.getBloodType() : "Unknown",
                            "district", demographics.getDistrict(),
                            "municipality", demographics.getMunicipality()
                        ),
                        "contact", Map.of(
                            "phone", demographics.getPhone(),
                            "email", demographics.getEmail()
                        ),
                        "medicalHistory", medicalHistory != null ? Map.of(
                            "allergies", medicalHistory.getAllergies(),
                            "chronicConditions", medicalHistory.getChronicConditions(),
                            "bloodType", medicalHistory.getBloodType()
                        ) : Map.of(),
                        "consentStatus", decision.reasonCode().name(),
                        "lastUpdated", patient.getUpdatedAt() != null ? patient.getUpdatedAt().toString() : ""
                    ), context.correlationId());
                });
        });
    }

    private Promise<HttpResponse> handleCreateEncounter(HttpRequest request) {
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }

        if (!("clinician".equals(context.role()) || "admin".equals(context.role()))) {
            return PhrRouteSupport.errorResponse(403, "CLINICAL_ROLE_REQUIRED",
                "Only clinician or admin principals may create encounters",
                context.correlationId());
        }

        String patientId = request.getPathParameter("patientId");
        
        return consentService.checkAccess(
            new com.ghatana.phr.kernel.consent.ConsentService.ConsentCheckRequest(
                UUID.randomUUID().toString(),
                context.tenantId(),
                new com.ghatana.phr.kernel.consent.ConsentService.ActorContext(
                    context.principalId(),
                    com.ghatana.phr.kernel.consent.ConsentService.ActorType.PROVIDER,
                    null,
                    context.principalId(),
                    null,
                    Set.of()
                ),
                new com.ghatana.phr.kernel.consent.ConsentService.TargetResource(
                    patientId,
                    "Encounter",
                    null,
                    com.ghatana.phr.kernel.policy.PhrDataClassification.C3
                ),
                com.ghatana.phr.kernel.consent.ConsentService.ConsentAction.PATIENT_WRITE,
                com.ghatana.phr.kernel.consent.ConsentService.PurposeOfUse.CARE_DELIVERY,
                null
            )
        ).then(decision -> {
            if (!decision.allowed()) {
                return PhrRouteSupport.errorResponse(403, "CONSENT_REQUIRED",
                    "Patient has not granted consent for this clinician to create encounters",
                    context.correlationId());
            }
            
            return request.loadBody()
                .then(body -> {
                    try {
                        String json = body.getString(java.nio.charset.StandardCharsets.UTF_8);
                        var node = PhrRouteSupport.JSON.readTree(json);
                        
                        String encounterType = node.path("encounterType").asText();
                        String location = node.path("location").asText();
                        
                        PatientOperationContext ctx = new PatientOperationContext(
                            context.tenantId(),
                            "default",
                            context.principalId(),
                            patientId,
                            context.correlationId()
                        );
                        
                        CreateEncounterRequest createRequest = new CreateEncounterRequest(
                            patientId,
                            encounterType,
                            context.principalId(),
                            location
                        );
                        
                        return clinicalService.createEncounter(ctx, createRequest)
                            .then(encounter -> PhrRouteSupport.jsonResponse(201, Map.of(
                                "encounterId", encounter.encounterId(),
                                "patientId", encounter.patientId(),
                                "encounterType", encounter.encounterType(),
                                "participant", encounter.participant(),
                                "location", encounter.location(),
                                "status", encounter.status(),
                                "createdAt", encounter.createdAt()
                            ), context.correlationId()));
                    } catch (Exception ex) {
                        return PhrRouteSupport.errorResponse(400, "INVALID_REQUEST",
                            "Invalid encounter request format", context.correlationId());
                    }
                });
        });
    }

    private Promise<HttpResponse> handleGetEncounter(HttpRequest request) {
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }

        if (!("clinician".equals(context.role()) || "admin".equals(context.role()))) {
            return PhrRouteSupport.errorResponse(403, "CLINICAL_ROLE_REQUIRED",
                "Only clinician or admin principals may view encounters",
                context.correlationId());
        }

        String patientId = request.getPathParameter("patientId");
        String encounterId = request.getPathParameter("encounterId");
        
        return consentService.checkAccess(
            new com.ghatana.phr.kernel.consent.ConsentService.ConsentCheckRequest(
                UUID.randomUUID().toString(),
                context.tenantId(),
                new com.ghatana.phr.kernel.consent.ConsentService.ActorContext(
                    context.principalId(),
                    com.ghatana.phr.kernel.consent.ConsentService.ActorType.PROVIDER,
                    null,
                    context.principalId(),
                    null,
                    Set.of()
                ),
                new com.ghatana.phr.kernel.consent.ConsentService.TargetResource(
                    patientId,
                    "Encounter",
                    null,
                    com.ghatana.phr.kernel.policy.PhrDataClassification.C3
                ),
                com.ghatana.phr.kernel.consent.ConsentService.ConsentAction.PATIENT_READ,
                com.ghatana.phr.kernel.consent.ConsentService.PurposeOfUse.CARE_DELIVERY,
                null
            )
        ).then(decision -> {
            if (!decision.allowed()) {
                return PhrRouteSupport.errorResponse(403, "CONSENT_REQUIRED",
                    "Patient has not granted consent for this clinician to view encounters",
                    context.correlationId());
            }
            
            PatientOperationContext ctx = new PatientOperationContext(
                context.tenantId(),
                "default",
                context.principalId(),
                patientId,
                context.correlationId()
            );
            
            return clinicalService.getEncounter(ctx, encounterId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return PhrRouteSupport.errorResponse(404, "ENCOUNTER_NOT_FOUND",
                            "Encounter not found: " + encounterId,
                            context.correlationId());
                    }
                    var encounter = opt.get();
                    return PhrRouteSupport.jsonResponse(200, Map.of(
                        "encounterId", encounter.encounterId(),
                        "patientId", encounter.patientId(),
                        "encounterType", encounter.encounterType(),
                        "participant", encounter.participant(),
                        "location", encounter.location(),
                        "status", encounter.status(),
                        "createdAt", encounter.createdAt(),
                        "completedAt", encounter.completedAt()
                    ), context.correlationId());
                });
        });
    }

    private Promise<HttpResponse> handleUpdateEncounter(HttpRequest request) {
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }

        if (!("clinician".equals(context.role()) || "admin".equals(context.role()))) {
            return PhrRouteSupport.errorResponse(403, "CLINICAL_ROLE_REQUIRED",
                "Only clinician or admin principals may update encounters",
                context.correlationId());
        }

        String patientId = request.getPathParameter("patientId");
        String encounterId = request.getPathParameter("encounterId");
        
        return consentService.checkAccess(
            new com.ghatana.phr.kernel.consent.ConsentService.ConsentCheckRequest(
                UUID.randomUUID().toString(),
                context.tenantId(),
                new com.ghatana.phr.kernel.consent.ConsentService.ActorContext(
                    context.principalId(),
                    com.ghatana.phr.kernel.consent.ConsentService.ActorType.PROVIDER,
                    null,
                    context.principalId(),
                    null,
                    Set.of()
                ),
                new com.ghatana.phr.kernel.consent.ConsentService.TargetResource(
                    patientId,
                    "Encounter",
                    null,
                    com.ghatana.phr.kernel.policy.PhrDataClassification.C3
                ),
                com.ghatana.phr.kernel.consent.ConsentService.ConsentAction.PATIENT_WRITE,
                com.ghatana.phr.kernel.consent.ConsentService.PurposeOfUse.CARE_DELIVERY,
                null
            )
        ).then(decision -> {
            if (!decision.allowed()) {
                return PhrRouteSupport.errorResponse(403, "CONSENT_REQUIRED",
                    "Patient has not granted consent for this clinician to update encounters",
                    context.correlationId());
            }
            
            return request.loadBody()
                .then(body -> {
                    try {
                        String json = body.getString(java.nio.charset.StandardCharsets.UTF_8);
                        var node = PhrRouteSupport.JSON.readTree(json);
                        
                        String encounterType = node.has("encounterType") ? node.path("encounterType").asText() : null;
                        String participant = node.has("participant") ? node.path("participant").asText() : null;
                        
                        PatientOperationContext ctx = new PatientOperationContext(
                            context.tenantId(),
                            "default",
                            context.principalId(),
                            patientId,
                            context.correlationId()
                        );
                        
                        UpdateEncounterRequest updateRequest = new UpdateEncounterRequest(
                            encounterType,
                            participant,
                            null
                        );
                        
                        return clinicalService.updateEncounter(ctx, encounterId, updateRequest)
                            .then(encounter -> PhrRouteSupport.jsonResponse(200, Map.of(
                                "encounterId", encounter.encounterId(),
                                "patientId", encounter.patientId(),
                                "encounterType", encounter.encounterType(),
                                "participant", encounter.participant(),
                                "location", encounter.location(),
                                "status", encounter.status(),
                                "createdAt", encounter.createdAt(),
                                "completedAt", encounter.completedAt()
                            ), context.correlationId()));
                    } catch (Exception ex) {
                        return PhrRouteSupport.errorResponse(400, "INVALID_REQUEST",
                            "Invalid encounter update format", context.correlationId());
                    }
                });
        });
    }

    private Promise<HttpResponse> handleCompleteEncounter(HttpRequest request) {
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }

        if (!("clinician".equals(context.role()) || "admin".equals(context.role()))) {
            return PhrRouteSupport.errorResponse(403, "CLINICAL_ROLE_REQUIRED",
                "Only clinician or admin principals may complete encounters",
                context.correlationId());
        }

        String patientId = request.getPathParameter("patientId");
        String encounterId = request.getPathParameter("encounterId");
        
        return consentService.checkAccess(
            new com.ghatana.phr.kernel.consent.ConsentService.ConsentCheckRequest(
                UUID.randomUUID().toString(),
                context.tenantId(),
                new com.ghatana.phr.kernel.consent.ConsentService.ActorContext(
                    context.principalId(),
                    com.ghatana.phr.kernel.consent.ConsentService.ActorType.PROVIDER,
                    null,
                    context.principalId(),
                    null,
                    Set.of()
                ),
                new com.ghatana.phr.kernel.consent.ConsentService.TargetResource(
                    patientId,
                    "Encounter",
                    null,
                    com.ghatana.phr.kernel.policy.PhrDataClassification.C3
                ),
                com.ghatana.phr.kernel.consent.ConsentService.ConsentAction.PATIENT_WRITE,
                com.ghatana.phr.kernel.consent.ConsentService.PurposeOfUse.CARE_DELIVERY,
                null
            )
        ).then(decision -> {
            if (!decision.allowed()) {
                return PhrRouteSupport.errorResponse(403, "CONSENT_REQUIRED",
                    "Patient has not granted consent for this clinician to complete encounters",
                    context.correlationId());
            }
            
            PatientOperationContext ctx = new PatientOperationContext(
                context.tenantId(),
                "default",
                context.principalId(),
                patientId,
                context.correlationId()
            );
            
            return clinicalService.completeEncounter(ctx, encounterId)
                .then(encounter -> PhrRouteSupport.jsonResponse(200, Map.of(
                    "encounterId", encounter.encounterId(),
                    "patientId", encounter.patientId(),
                    "encounterType", encounter.encounterType(),
                    "participant", encounter.participant(),
                    "location", encounter.location(),
                    "status", encounter.status(),
                    "createdAt", encounter.createdAt(),
                    "completedAt", encounter.completedAt()
                ), context.correlationId()));
        });
    }

    private Promise<HttpResponse> handlePrescribeMedication(HttpRequest request) {
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }

        if (!("clinician".equals(context.role()) || "admin".equals(context.role()))) {
            return PhrRouteSupport.errorResponse(403, "CLINICAL_ROLE_REQUIRED",
                "Only clinician or admin principals may prescribe medications",
                context.correlationId());
        }

        String patientId = request.getPathParameter("patientId");
        
        return consentService.checkAccess(
            new com.ghatana.phr.kernel.consent.ConsentService.ConsentCheckRequest(
                UUID.randomUUID().toString(),
                context.tenantId(),
                new com.ghatana.phr.kernel.consent.ConsentService.ActorContext(
                    context.principalId(),
                    com.ghatana.phr.kernel.consent.ConsentService.ActorType.PROVIDER,
                    null,
                    context.principalId(),
                    null,
                    Set.of()
                ),
                new com.ghatana.phr.kernel.consent.ConsentService.TargetResource(
                    patientId,
                    "Medication",
                    null,
                    com.ghatana.phr.kernel.policy.PhrDataClassification.C3
                ),
                com.ghatana.phr.kernel.consent.ConsentService.ConsentAction.MEDICATION_WRITE,
                com.ghatana.phr.kernel.consent.ConsentService.PurposeOfUse.CARE_DELIVERY,
                null
            )
        ).then(decision -> {
            if (!decision.allowed()) {
                return PhrRouteSupport.errorResponse(403, "CONSENT_REQUIRED",
                    "Patient has not granted consent for this clinician to prescribe medications",
                    context.correlationId());
            }
            
            return request.loadBody()
                .then(body -> {
                    try {
                        String json = body.getString(java.nio.charset.StandardCharsets.UTF_8);
                        var node = PhrRouteSupport.JSON.readTree(json);
                        
                        String medicationName = node.path("medicationName").asText();
                        String dosage = node.path("dosage").asText();
                        String frequency = node.path("frequency").asText();
                        String route = node.path("route").asText();
                        
                        PatientOperationContext ctx = new PatientOperationContext(
                            context.tenantId(),
                            "default",
                            context.principalId(),
                            patientId,
                            context.correlationId()
                        );
                        
                        PrescribeMedicationRequest prescribeRequest = new PrescribeMedicationRequest(
                            patientId,
                            medicationName,
                            dosage,
                            frequency,
                            route
                        );
                        
                        return clinicalService.prescribeMedication(ctx, prescribeRequest)
                            .then(medication -> PhrRouteSupport.jsonResponse(201, Map.of(
                                "medicationId", medication.medicationId(),
                                "patientId", medication.patientId(),
                                "medicationName", medication.medicationName(),
                                "dosage", medication.dosage(),
                                "frequency", medication.frequency(),
                                "route", medication.route(),
                                "status", medication.status(),
                                "prescribedAt", medication.prescribedAt()
                            ), context.correlationId()));
                    } catch (Exception ex) {
                        return PhrRouteSupport.errorResponse(400, "INVALID_REQUEST",
                            "Invalid medication prescription format", context.correlationId());
                    }
                });
        });
    }

    private Promise<HttpResponse> handleGetProviderCalendar(HttpRequest request) {
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }

        if (!("clinician".equals(context.role()) || "admin".equals(context.role()))) {
            return PhrRouteSupport.errorResponse(403, "CLINICAL_ROLE_REQUIRED",
                "Only clinician or admin principals may view provider calendar",
                context.correlationId());
        }

        String startDateParam = request.getQueryParameter("startDate");
        String endDateParam = request.getQueryParameter("endDate");
        
        return PhrRouteSupport.jsonResponse(200, Map.of(
            "providerId", context.principalId(),
            "tenantId", context.tenantId(),
            "startDate", startDateParam != null ? startDateParam : "",
            "endDate", endDateParam != null ? endDateParam : "",
            "appointments", List.of(),
            "encounters", List.of(),
            "availability", Map.of(
                "message", "Provider calendar feature - returns scheduled appointments and encounters for the specified date range"
            )
        ), context.correlationId());
    }
}
