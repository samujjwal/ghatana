package com.ghatana.phr.api.routes;

import com.ghatana.phr.kernel.service.CaregiverService;
import com.ghatana.phr.kernel.service.PatientRecordService;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.RoutingServlet;
import io.activej.promise.Promise;
import io.activej.promise.Promises;

import java.util.List;
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

    public PhrCaregiverRoutes(
            Eventloop eventloop,
            CaregiverService caregiverService,
            PatientRecordService patientRecordService) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
        this.caregiverService = Objects.requireNonNull(caregiverService, "caregiverService must not be null");
        this.patientRecordService = Objects.requireNonNull(patientRecordService, "patientRecordService must not be null");
    }

    /**
     * Returns the routing servlet for caregiver endpoints.
     *
     * @return routing servlet; never null
     */
    public AsyncServlet getServlet() {
        return RoutingServlet.builder(eventloop)
            .with(HttpMethod.GET, "/dependents", this::handleGetDependents)
            .with(HttpMethod.GET, "/patient/:patientId", this::handleGetPatientSummary)
            .with(HttpMethod.GET, "/patient/:patientId/detail", this::handleGetPatientDetail)
            .build();
    }

    private Promise<HttpResponse> handleGetDependents(HttpRequest request) {
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }

        if (!"caregiver".equals(context.role()) && !"admin".equals(context.role())) {
            return PhrRouteSupport.errorResponse(403, "CAREGIVER_ROLE_REQUIRED",
                "Only caregiver or admin principals may access dependent lists",
                context.correlationId());
        }

        // Fetch real caregiver relationships and patient data
        return caregiverService.getPatientsForCaregiver(context.principalId())
            .then(relationships -> {
                // Fetch patient details for each relationship
                List<Promise<Map<String, Object>>> dependentPromises = relationships.stream()
                    .map(rel -> patientRecordService.getPatient(rel.patientId())
                        .then(opt -> {
                            if (opt.isPresent()) {
                                var patient = opt.get();
                                return Promise.of(Map.<String, Object>of(
                                    "id", patient.getId(),
                                    "name", patient.getDemographics().getFullName(),
                                    "relationship", rel.relationshipType().name(),
                                    "age", patient.getDemographics().getAge(),
                                    "consentScope", rel.consentScope(),
                                    "relationshipId", rel.id(),
                                    "status", rel.status().name(),
                                    "expiresAt", rel.expiresAt() != null ? rel.expiresAt().toString() : null
                                ));
                            } else {
                                return Promise.of(Map.<String, Object>of(
                                    "id", rel.patientId(),
                                    "name", "Unknown",
                                    "relationship", rel.relationshipType().name(),
                                    "status", rel.status().name()
                                ));
                            }
                        })
                    )
                    .toList();

                return Promises.toList(dependentPromises)
                    .then(dependents -> PhrRouteSupport.jsonResponse(200, Map.of(
                        "dependents", dependents,
                        "total", dependents.size()
                    ), context.correlationId()));
            });
    }

    private Promise<HttpResponse> handleGetPatientSummary(HttpRequest request) {
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }

        String patientId = request.getPathParameter("patientId");
        
        // Verify caregiver has relationship with patient
        return caregiverService.getPatientsForCaregiver(context.principalId())
            .then(relationships -> {
                boolean hasRelationship = relationships.stream()
                    .anyMatch(rel -> rel.patientId().equals(patientId) && rel.status() == com.ghatana.phr.kernel.service.CaregiverService.RelationshipStatus.ACTIVE);
                
                if (!hasRelationship) {
                    return PhrRouteSupport.errorResponse(403, "CAREGIVER_PATIENT_ACCESS_DENIED",
                        "Caregiver does not have an active relationship with this patient",
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
                            "caregiverId", context.principalId(),
                            "name", patient.getDemographics().getFullName(),
                            "age", patient.getDemographics().getAge(),
                            "summary", "Caregiver-scoped patient summary"
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

        String patientId = request.getPathParameter("patientId");
        
        // Verify caregiver has relationship with patient
        return caregiverService.getPatientsForCaregiver(context.principalId())
            .then(relationships -> {
                boolean hasRelationship = relationships.stream()
                    .anyMatch(rel -> rel.patientId().equals(patientId) && rel.status() == com.ghatana.phr.kernel.service.CaregiverService.RelationshipStatus.ACTIVE);
                
                if (!hasRelationship) {
                    return PhrRouteSupport.errorResponse(403, "CAREGIVER_PATIENT_ACCESS_DENIED",
                        "Caregiver does not have an active relationship with this patient",
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
                            "lastUpdated", patient.getUpdatedAt() != null ? patient.getUpdatedAt().toString() : ""
                        ), context.correlationId());
                    });
            });
    }
}
