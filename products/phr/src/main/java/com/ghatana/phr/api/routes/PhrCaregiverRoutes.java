package com.ghatana.phr.api.routes;

import com.ghatana.phr.kernel.service.CaregiverService;
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

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Caregiver delegated-access API for the PHR product.
 *
 * <p>Provides caregiver-scoped endpoints for listing dependents and accessing
 * patient records under an active consent grant.
 *
 * @doc.type class
 * @doc.purpose ActiveJ route adapter for caregiver dependent listing and delegated patient access
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 */
public final class PhrCaregiverRoutes {

    private final Eventloop eventloop;
    private final CaregiverService caregiverService;
    private final PatientRecordService patientRecordService;
    private final PhrPolicyEvaluator policyEvaluator;

    public PhrCaregiverRoutes(
            Eventloop eventloop,
            CaregiverService caregiverService,
            PatientRecordService patientRecordService,
            PhrPolicyEvaluator policyEvaluator) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
        this.caregiverService = Objects.requireNonNull(caregiverService, "caregiverService must not be null");
        this.patientRecordService = Objects.requireNonNull(patientRecordService, "patientRecordService must not be null");
        this.policyEvaluator = Objects.requireNonNull(policyEvaluator, "policyEvaluator must not be null");
    }

    /**
     * Returns the routing servlet for caregiver endpoints.
     *
     * @return routing servlet; never null
     */
    public AsyncServlet getServlet() {
        return RoutingServlet.builder(eventloop)
            .with(HttpMethod.GET, "/dashboard", this::handleGetCaregiverDashboard)
            .with(HttpMethod.GET, "/dependents", this::handleGetDependents)
            .with(HttpMethod.GET, "/patient/:patientId", this::handleGetPatientSummary)
            .with(HttpMethod.GET, "/patient/:patientId/detail", this::handleGetPatientDetail)
            .build();
    }

    private Promise<HttpResponse> handleGetCaregiverDashboard(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage(, correlationId));
        }

        // Use policy evaluator for PHI access decision (POL-001)
        return policyEvaluator.canAccessPhiResourceAsync(
                context,
                "caregiver-dashboard-scope",
                "caregiver-dashboard",
                "READ",
                context.tenantId(),
                context.facilityId())
            .then(decision -> {
                if (!decision.isAllowed()) {
                    return PhrRouteSupport.policyDenialResponse(403, context.correlationId(), decision.getReasonCode());
                }

                // Caregiver dashboard with dependent list, consent status, and alerts
                return caregiverService.getPatientsForCaregiver(context.principalId())
            .then(relationships -> {
                List<Promise<Map<String, Object>>> dependentPromises = relationships.stream()
                    .map(rel -> patientRecordService.getPatient(rel.patientId())
                        .then(opt -> {
                            if (opt.isEmpty()) {
                                return Promise.of(Map.<String, Object>of(
                                    "id", rel.patientId(),
                                    "name", "[REDACTED]",
                                    "relationship", rel.relationshipType(),
                                    "consentStatus", rel.status(),
                                    "expiresAt", rel.expiresAt() != null ? rel.expiresAt().toString() : ""
                                ));
                            }
                            var patient = opt.get();
                            return Promise.of(Map.<String, Object>of(
                                "id", patient.getId(),
                                "name", patient.getDemographics().getFullName(),
                                "relationship", rel.relationshipType(),
                                "consentStatus", rel.status(),
                                "expiresAt", rel.expiresAt() != null ? rel.expiresAt().toString() : "",
                                "age", patient.getDemographics().getAge(),
                                "alertCount", 0
                            ));
                        }))
                    .toList();

                return Promises.all(dependentPromises)
                    .then(dependents -> PhrRouteSupport.jsonResponse(200, Map.of(
                        "caregiverId", context.principalId(, correlationId),
                        "tenantId", context.tenantId(),
                        "dependents", dependents,
                        "alerts", Map.of(
                            "expiringConsents", 0,
                            "revokedConsents", 0,
                            "emergencyAccessRequests", 0
                        ),
                        "generatedAt", java.time.Instant.now().toString()
                    ), context.correlationId()));
            });
            });
    }

    private Promise<HttpResponse> handleGetDependents(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage(, correlationId));
        }

        // Use policy evaluator for PHI access decision (POL-001)
        return policyEvaluator.canAccessPhiResourceAsync(
                context,
                "caregiver-dependents-scope",
                "caregiver-dependents",
                "READ",
                context.tenantId(),
                context.facilityId())
            .then(decision -> {
                if (!decision.isAllowed()) {
                    return PhrRouteSupport.policyDenialResponse(403, context.correlationId(), decision.getReasonCode());
                }

                return caregiverService.getPatientsForCaregiver(context.principalId())
                    .then(relationships -> {
                        List<Promise<Map<String, Object>>> dependentPromises = relationships.stream()
                            .map(rel -> patientRecordService.getPatient(rel.patientId())
                                .then(opt -> {
                                    if (opt.isPresent()) {
                                        var patient = opt.get();
                                        Map<String, Object> dependent = new LinkedHashMap<>();
                                        dependent.put("id", patient.getId());
                                        dependent.put("name", patient.getDemographics().getFullName());
                                        dependent.put("relationship", rel.relationshipType().name());
                                        dependent.put("age", patient.getDemographics().getAge());
                                        dependent.put("consentScope", rel.consentScope());
                                        dependent.put("relationshipId", rel.id());
                                        dependent.put("status", rel.status().name());
                                        if (rel.expiresAt() != null) {
                                            dependent.put("expiresAt", rel.expiresAt().toString());
                                        }
                                        return Promise.of(dependent);
                                    }
                                    return Promise.of(Map.<String, Object>of(
                                        "id", rel.patientId(),
                                        "name", "Unknown",
                                        "relationship", rel.relationshipType().name(),
                                        "status", rel.status().name()
                                    ));
                                }))
                            .toList();

                        return Promises.toList(dependentPromises)
                            .then(dependents -> PhrRouteSupport.jsonResponse(200, Map.of(
                                "dependents", dependents,
                                "total", dependents.size(, correlationId)
                            ), context.correlationId()));
                    });
        });
    }

    private Promise<HttpResponse> handleGetPatientSummary(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage(, correlationId));
        }

        String patientId = request.getPathParameter("patientId");

        return policyEvaluator.canAccessPhiResourceAsync(
            context,
            patientId,
            "caregiver-patient-summary",
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
                        "tenantId", context.tenantId(, correlationId),
                        "caregiverId", context.principalId(),
                        "name", patient.getDemographics().getFullName(),
                        "age", patient.getDemographics().getAge(),
                        "summary", "Caregiver-scoped patient summary",
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
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage(, correlationId));
        }

        String patientId = request.getPathParameter("patientId");

        return policyEvaluator.canAccessPhiResourceAsync(
            context,
            patientId,
            "caregiver-patient-detail",
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
                        "tenantId", context.tenantId(, correlationId),
                        "caregiverId", context.principalId(),
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
}
