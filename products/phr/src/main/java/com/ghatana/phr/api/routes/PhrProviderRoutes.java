package com.ghatana.phr.api.routes;

import com.ghatana.phr.application.clinical.ClinicalService;
import com.ghatana.phr.application.clinical.ClinicalService.CreateEncounterRequest;
import com.ghatana.phr.application.clinical.ClinicalService.UpdateEncounterRequest;
import com.ghatana.phr.application.clinical.ClinicalService.PrescribeMedicationRequest;
import com.ghatana.phr.application.patient.PatientOperationContext;
import com.ghatana.phr.kernel.service.ConsentManagementService;
import com.ghatana.phr.kernel.service.PatientRecordService;
import com.ghatana.phr.security.PhrPolicyEvaluator;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.RoutingServlet;
import io.activej.promise.Promise;
import io.activej.promise.Promises;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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
    private final PhrPolicyEvaluator policyEvaluator;

    public PhrProviderRoutes(
            Eventloop eventloop,
            PatientRecordService patientRecordService,
            ConsentManagementService consentService,
            ClinicalService clinicalService,
            PhrPolicyEvaluator policyEvaluator) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
        this.patientRecordService = Objects.requireNonNull(patientRecordService, "patientRecordService must not be null");
        this.consentService = Objects.requireNonNull(consentService, "consentService must not be null");
        this.clinicalService = Objects.requireNonNull(clinicalService, "clinicalService must not be null");
        this.policyEvaluator = Objects.requireNonNull(policyEvaluator, "policyEvaluator must not be null");
    }

    /**
     * Returns the routing servlet for provider endpoints.
     *
     * @return routing servlet; never null
     */
    public AsyncServlet getServlet() {
        return RoutingServlet.builder(eventloop)
            .with(HttpMethod.GET, "/dashboard", this::handleGetProviderDashboard)
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

    private Promise<HttpResponse> handleGetProviderDashboard(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage(), correlationId);
        }

        if (!"clinician".equals(context.role()) && !"admin".equals(context.role())) {
            return PhrRouteSupport.errorResponse(403, "PROVIDER_ROLE_REQUIRED",
                "Provider dashboard access requires clinician or admin role", context.correlationId());
        }

        return scopedRosterCandidates(context, 10)
            .then(patients -> authorizedPatientSummaries(context, patients, "provider-dashboard", 5)
            .then(recentPatients -> {

                Map<String, Object> appointments = new java.util.LinkedHashMap<>();
                appointments.put("todayCount", 0);
                appointments.put("upcomingCount", 0);
                appointments.put("nextAppointment", null);

                return PhrRouteSupport.jsonResponse(200, Map.of(
                    "providerId", context.principalId(),
                    "tenantId", context.tenantId(),
                    "workQueue", Map.of(
                        "pendingReviews", 0,
                        "pendingEncounters", 0,
                        "urgentPatients", 0
                    ),
                    "appointments", appointments,
                    "recentPatients", recentPatients,
                    "alerts", Map.of(
                        "expiringConsents", 0,
                        "emergencyAccessRequests", 0,
                        "criticalLabs", 0
                    ),
                    "generatedAt", java.time.Instant.now().toString()
                ), context.correlationId());
            }));
    }

    private Promise<HttpResponse> handleGetPatients(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage(), correlationId);
        }

        if (!"clinician".equals(context.role()) && !"admin".equals(context.role())) {
            return PhrRouteSupport.errorResponse(403, "PROVIDER_ROLE_REQUIRED",
                "Provider roster access requires clinician or admin role", context.correlationId());
        }

        String limitParam = request.getQueryParameter("limit");
        int limit;
        try {
            limit = parseLimit(limitParam, 50, 200);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "INVALID_LIMIT", ex.getMessage(), context.correlationId());
        }

        return scopedRosterCandidates(context, limit)
            .then(patients -> authorizedPatientSummaries(context, patients, "patient-roster", limit)
            .then(patientSummaries -> {
                return PhrRouteSupport.jsonResponse(200, Map.of(
                    "items", patientSummaries,
                    "count", patientSummaries.size()
                ), context.correlationId());
            }));
    }

    private Promise<List<PatientRecordService.Patient>> scopedRosterCandidates(
            PhrRouteSupport.PhrRequestContext context,
            int limit) {
        if ("admin".equals(context.role())) {
            return Promise.of(List.of());
        }

        List<Promise<List<PatientRecordService.Patient>>> candidateSources = new ArrayList<>();
        candidateSources.add(consentService.getActiveGrantsForRecipient(context.principalId())
            .then(grants -> {
                List<Promise<Optional<PatientRecordService.Patient>>> patientReads = grants.stream()
                    .map(ConsentManagementService.ConsentGrant::getPatientId)
                    .distinct()
                    .limit(limit)
                    .map(patientRecordService::getPatient)
                    .toList();
                return Promises.toList(patientReads)
                    .map(patients -> patients.stream()
                        .flatMap(Optional::stream)
                        .toList());
            }));

        if (context.facilityId() != null && !context.facilityId().isBlank()) {
            candidateSources.add(patientRecordService.searchPatients(
                "facilityId = :facilityId AND deleted = false",
                Map.of("facilityId", context.facilityId()),
                limit,
                0
            ));
        }

        return Promises.toList(candidateSources)
            .map(candidateLists -> {
                Map<String, PatientRecordService.Patient> uniquePatients = new LinkedHashMap<>();
                candidateLists.stream()
                    .flatMap(List::stream)
                    .forEach(patient -> uniquePatients.putIfAbsent(patient.getId(), patient));
                return uniquePatients.values().stream()
                    .limit(limit)
                    .toList();
            });
    }

    private Promise<List<Map<String, Object>>> authorizedPatientSummaries(
            PhrRouteSupport.PhrRequestContext context,
            List<PatientRecordService.Patient> patients,
            String resourceType,
            int limit) {
        List<Promise<AuthorizedRosterPatient>> authorizationPromises = patients.stream()
            .limit(limit)
            .map(patient -> policyEvaluator.canAccessPhiResourceAsync(
                    context,
                    patient.getId(),
                    resourceType,
                    "READ",
                    context.tenantId(),
                    context.facilityId())
                .map(decision -> new AuthorizedRosterPatient(patient, decision)))
            .toList();

        return Promises.toList(authorizationPromises)
            .map(authorizedPatients -> authorizedPatients.stream()
                .filter(authorizedPatient -> authorizedPatient.decision().isAllowed())
                .map(this::patientSummary)
                .toList());
    }

    private Map<String, Object> patientSummary(AuthorizedRosterPatient authorizedPatient) {
        PatientRecordService.Patient patient = authorizedPatient.patient();
        return Map.of(
            "id", patient.getId(),
            "name", patient.getDemographics().getFullName(),
            "age", patient.getDemographics().getAge(),
            "status", "active",
            "policyStatus", authorizedPatient.decision().getReasonCode(),
            "lastVisit", patient.getMedicalHistory() != null ? "Recent" : "Unknown"
        );
    }

    private static int parseLimit(String limitParam, int defaultLimit, int maxLimit) {
        if (limitParam == null || limitParam.isBlank()) {
            return defaultLimit;
        }
        try {
            int parsed = Integer.parseInt(limitParam);
            if (parsed < 1 || parsed > maxLimit) {
                throw new IllegalArgumentException("limit must be between 1 and " + maxLimit);
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("limit must be a positive integer", ex);
        }
    }

    private record AuthorizedRosterPatient(
            PatientRecordService.Patient patient,
            PhrPolicyEvaluator.PolicyDecision decision) {}

    private Promise<HttpResponse> handleGetPatientSummary(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage(), correlationId);
        }

        String patientId = request.getPathParameter("patientId");
        return policyEvaluator.canAccessPhiResourceAsync(
            context,
            patientId,
            "provider-patient-summary",
            "READ",
            context.tenantId(),
            context.facilityId()
        ).then(decision -> {
            if (!decision.isAllowed()) {
                return PhrRouteSupport.policyDenialResponse(403, context.correlationId(), decision.getReasonCode());
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
                        "policyStatus", decision.getReasonCode()
                    ), context.correlationId());
                });
        });
    }

    private Promise<HttpResponse> handleGetPatientDetail(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage(), correlationId);
        }

        String patientId = request.getPathParameter("patientId");
        return policyEvaluator.canAccessPhiResourceAsync(
            context,
            patientId,
            "provider-patient-detail",
            "READ",
            context.tenantId(),
            context.facilityId()
        ).then(decision -> {
            if (!decision.isAllowed()) {
                return PhrRouteSupport.policyDenialResponse(403, context.correlationId(), decision.getReasonCode());
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
                        "policyStatus", decision.getReasonCode(),
                        "lastUpdated", patient.getUpdatedAt() != null ? patient.getUpdatedAt().toString() : ""
                    ), context.correlationId());
                });
        });
    }

    private Promise<HttpResponse> handleCreateEncounter(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage(), correlationId);
        }

        String patientId = request.getPathParameter("patientId");
        return policyEvaluator.canAccessPhiResourceAsync(
            context,
            patientId,
            "provider-encounter",
            "WRITE",
            context.tenantId(),
            context.facilityId()
        ).then(decision -> {
            if (!decision.isAllowed()) {
                return PhrRouteSupport.policyDenialResponse(403, context.correlationId(), decision.getReasonCode());
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
                            context.facilityId() != null && !context.facilityId().isBlank() ? context.facilityId() : "default",
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
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage(), correlationId);
        }

        String patientId = request.getPathParameter("patientId");
        String encounterId = request.getPathParameter("encounterId");
        return policyEvaluator.canAccessPhiResourceAsync(
            context,
            patientId,
            "provider-encounter",
            "READ",
            context.tenantId(),
            context.facilityId()
        ).then(decision -> {
            if (!decision.isAllowed()) {
                return PhrRouteSupport.policyDenialResponse(403, context.correlationId(), decision.getReasonCode());
            }

            PatientOperationContext ctx = new PatientOperationContext(
                context.tenantId(),
                context.facilityId() != null && !context.facilityId().isBlank() ? context.facilityId() : "default",
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
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage(), correlationId);
        }

        String patientId = request.getPathParameter("patientId");
        String encounterId = request.getPathParameter("encounterId");
        return policyEvaluator.canAccessPhiResourceAsync(
            context,
            patientId,
            "provider-encounter",
            "WRITE",
            context.tenantId(),
            context.facilityId()
        ).then(decision -> {
            if (!decision.isAllowed()) {
                return PhrRouteSupport.policyDenialResponse(403, context.correlationId(), decision.getReasonCode());
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
                            context.facilityId() != null && !context.facilityId().isBlank() ? context.facilityId() : "default",
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
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage(), correlationId);
        }

        String patientId = request.getPathParameter("patientId");
        String encounterId = request.getPathParameter("encounterId");
        return policyEvaluator.canAccessPhiResourceAsync(
            context,
            patientId,
            "provider-encounter",
            "WRITE",
            context.tenantId(),
            context.facilityId()
        ).then(decision -> {
            if (!decision.isAllowed()) {
                return PhrRouteSupport.policyDenialResponse(403, context.correlationId(), decision.getReasonCode());
            }

            PatientOperationContext ctx = new PatientOperationContext(
                context.tenantId(),
                context.facilityId() != null && !context.facilityId().isBlank() ? context.facilityId() : "default",
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
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage(), correlationId);
        }

        String patientId = request.getPathParameter("patientId");
        return policyEvaluator.canAccessPhiResourceAsync(
            context,
            patientId,
            "provider-medication",
            "WRITE",
            context.tenantId(),
            context.facilityId()
        ).then(decision -> {
            if (!decision.isAllowed()) {
                return PhrRouteSupport.policyDenialResponse(403, context.correlationId(), decision.getReasonCode());
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
                            context.facilityId() != null && !context.facilityId().isBlank() ? context.facilityId() : "default",
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
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage(), correlationId);
        }

        return policyEvaluator.canAccessPhiResourceAsync(
                context,
                "calendar-scope",
                "provider-calendar",
                "READ",
                context.tenantId(),
                context.facilityId())
            .then(decision -> {
                if (!decision.isAllowed()) {
                    return PhrRouteSupport.policyDenialResponse(403, context.correlationId(), decision.getReasonCode());
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
            });
    }
}
