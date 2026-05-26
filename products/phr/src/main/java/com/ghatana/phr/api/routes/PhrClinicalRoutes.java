package com.ghatana.phr.api.routes;

import com.fasterxml.jackson.databind.JsonNode;
import com.ghatana.phr.kernel.service.ConsentManagementService;
import com.ghatana.phr.kernel.service.ImmunizationService;
import com.ghatana.phr.kernel.service.LabResultService;
import com.ghatana.phr.kernel.service.MedicationService;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.RoutingServlet;
import io.activej.promise.Promise;

import java.nio.charset.StandardCharsets;
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
    private final ConsentManagementService consentService;

    public PhrClinicalRoutes(
            Eventloop eventloop,
            LabResultService labResultService,
            MedicationService medicationService,
            ImmunizationService immunizationService,
            ConsentManagementService consentService) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
        this.labResultService = Objects.requireNonNull(labResultService, "labResultService must not be null");
        this.medicationService = Objects.requireNonNull(medicationService, "medicationService must not be null");
        this.immunizationService = Objects.requireNonNull(immunizationService, "immunizationService must not be null");
        this.consentService = Objects.requireNonNull(consentService, "consentService must not be null");
    }

    /**
     * Returns the routing servlet for clinical endpoints.
     *
     * @return routing servlet; never null
     */
    public AsyncServlet getServlet() {
        return RoutingServlet.builder(eventloop)
            .with("/labs/*", labServlet())
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

    private AsyncServlet medicationServlet() {
        return RoutingServlet.builder(eventloop)
            .with(HttpMethod.POST, "/prescriptions", this::handlePrescribeMedication)
            .with(HttpMethod.GET, "/prescriptions/:prescriptionId", this::handleGetPrescription)
            .with(HttpMethod.POST, "/prescriptions/:prescriptionId/discontinue", this::handleDiscontinuePrescription)
            .with(HttpMethod.POST, "/prescriptions/:prescriptionId/refill", this::handleRefillPrescription)
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
        return withBodyAndConsent(request, "lab-results", LabResultService.LabObservation.class,
            observation -> labResultService.recordObservation(observation)
                .then(stored -> PhrRouteSupport.jsonResponse(201, stored)));
    }

    private Promise<HttpResponse> handleGetLabObservation(HttpRequest request) {
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }
        String observationId = request.getPathParameter("observationId");
        return labResultService.getObservation(observationId)
            .then(observation -> {
                if (observation.isEmpty()) {
                    return PhrRouteSupport.errorResponse(404, "LAB_OBSERVATION_NOT_FOUND", "Lab observation not found");
                }
                return requireAccess(context, observation.get().patientId(), "lab-results")
                    .then(allowed -> allowed
                        ? PhrRouteSupport.jsonResponse(200, observation.get())
                        : PhrRouteSupport.errorResponse(403, "CONSENT_REQUIRED", "Consent is required to read lab results"));
            });
    }

    private Promise<HttpResponse> handleListLabObservations(HttpRequest request) {
        return withPatientAccess(request, "lab-results", patientId -> labResultService.getPatientObservations(patientId)
            .then(items -> PhrRouteSupport.jsonResponse(200, Map.of("patientId", patientId, "items", items, "count", items.size()))));
    }

    private Promise<HttpResponse> handleGetLabTrend(HttpRequest request) {
        String loincCode;
        try {
            loincCode = PhrRouteSupport.requiredQuery(request, "loincCode");
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "INVALID_TREND", ex.getMessage());
        }
        return withPatientAccess(request, "lab-results", patientId -> labResultService.getTrend(patientId, loincCode)
            .then(items -> PhrRouteSupport.jsonResponse(200, Map.of("patientId", patientId, "loincCode", loincCode, "items", items, "count", items.size()))));
    }

    private Promise<HttpResponse> handlePrescribeMedication(HttpRequest request) {
        return withBodyAndConsent(request, "medications", MedicationService.Prescription.class,
            prescription -> medicationService.prescribe(prescription)
                .then(stored -> PhrRouteSupport.jsonResponse(201, stored)));
    }

    private Promise<HttpResponse> handleGetPrescription(HttpRequest request) {
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }
        String prescriptionId = request.getPathParameter("prescriptionId");
        return medicationService.getPrescription(prescriptionId)
            .then(prescription -> {
                if (prescription.isEmpty()) {
                    return PhrRouteSupport.errorResponse(404, "PRESCRIPTION_NOT_FOUND", "Prescription not found");
                }
                return requireAccess(context, prescription.get().patientId(), "medications")
                    .then(allowed -> allowed
                        ? PhrRouteSupport.jsonResponse(200, prescription.get())
                        : PhrRouteSupport.errorResponse(403, "CONSENT_REQUIRED", "Consent is required to read medications"));
            });
    }

    private Promise<HttpResponse> handleListActivePrescriptions(HttpRequest request) {
        return withPatientAccess(request, "medications", patientId -> medicationService.getActivePrescriptions(patientId)
            .then(items -> PhrRouteSupport.jsonResponse(200, Map.of("patientId", patientId, "items", items, "count", items.size()))));
    }

    private Promise<HttpResponse> handleDiscontinuePrescription(HttpRequest request) {
        String reason = request.getQueryParameter("reason");
        String patientId = request.getQueryParameter("patientId");
        if (patientId == null || patientId.isBlank()) {
            return PhrRouteSupport.errorResponse(400, "INVALID_DISCONTINUE", "patientId query parameter is required");
        }
        return withPatientAccess(request, "medications", ignored -> medicationService.discontinue(
                request.getPathParameter("prescriptionId"),
                reason == null || reason.isBlank() ? "not specified" : reason)
            .then(updated -> PhrRouteSupport.jsonResponse(200, updated)));
    }

    private Promise<HttpResponse> handleRefillPrescription(HttpRequest request) {
        String patientId = request.getQueryParameter("patientId");
        if (patientId == null || patientId.isBlank()) {
            return PhrRouteSupport.errorResponse(400, "INVALID_REFILL", "patientId query parameter is required");
        }
        return withPatientAccess(request, "medications", ignored -> medicationService.refill(request.getPathParameter("prescriptionId"))
            .then(updated -> PhrRouteSupport.jsonResponse(200, updated)));
    }

    private Promise<HttpResponse> handleRecordImmunization(HttpRequest request) {
        return withBodyAndConsent(request, "immunizations", ImmunizationService.ImmunizationRecord.class,
            immunization -> immunizationService.recordImmunization(immunization)
                .then(stored -> PhrRouteSupport.jsonResponse(201, stored)));
    }

    private Promise<HttpResponse> handleGetImmunization(HttpRequest request) {
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }
        String immunizationId = request.getPathParameter("immunizationId");
        return immunizationService.getImmunization(immunizationId)
            .then(immunization -> {
                if (immunization.isEmpty()) {
                    return PhrRouteSupport.errorResponse(404, "IMMUNIZATION_NOT_FOUND", "Immunization not found");
                }
                return requireAccess(context, immunization.get().patientId(), "immunizations")
                    .then(allowed -> allowed
                        ? PhrRouteSupport.jsonResponse(200, immunization.get())
                        : PhrRouteSupport.errorResponse(403, "CONSENT_REQUIRED", "Consent is required to read immunizations"));
            });
    }

    private Promise<HttpResponse> handleListImmunizations(HttpRequest request) {
        return withPatientAccess(request, "immunizations", patientId -> immunizationService.getImmunizationHistory(patientId)
            .then(items -> PhrRouteSupport.jsonResponse(200, Map.of("patientId", patientId, "items", items, "count", items.size()))));
    }

    private <T> Promise<HttpResponse> withBodyAndConsent(
            HttpRequest request,
            String resourceType,
            Class<T> type,
            java.util.function.Function<T, Promise<HttpResponse>> handler) {
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
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
                    return PhrRouteSupport.errorResponse(400, "INVALID_" + resourceType.toUpperCase().replace('-', '_'), ex.getMessage());
                }
                return requireAccess(finalContext, patientId, resourceType)
                    .then(allowed -> allowed
                        ? handler.apply(value)
                        : PhrRouteSupport.errorResponse(403, "CONSENT_REQUIRED", "Consent is required for " + resourceType));
            });
    }

    private Promise<HttpResponse> withPatientAccess(
            HttpRequest request,
            String resourceType,
            java.util.function.Function<String, Promise<HttpResponse>> handler) {
        PhrRouteSupport.PhrRequestContext context;
        String patientId;
        try {
            context = PhrRouteSupport.requireContext(request);
            patientId = PhrRouteSupport.requiredQuery(request, "patientId");
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "INVALID_PATIENT_SCOPE", ex.getMessage());
        }
        return requireAccess(context, patientId, resourceType)
            .then(allowed -> allowed
                ? handler.apply(patientId)
                : PhrRouteSupport.errorResponse(403, "CONSENT_REQUIRED", "Consent is required for " + resourceType));
    }

    private Promise<Boolean> requireAccess(PhrRouteSupport.PhrRequestContext context, String patientId, String resourceType) {
        if (context.principalId().equals(patientId) || PhrRouteSupport.isPrivileged(context)) {
            return Promise.of(true);
        }
        return consentService.validateAccess(patientId, context.principalId(), resourceType)
            .map(ConsentManagementService.ConsentValidationResult::isAllowed);
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
