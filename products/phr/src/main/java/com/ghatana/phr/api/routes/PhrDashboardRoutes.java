package com.ghatana.phr.api.routes;

import com.ghatana.phr.kernel.service.AppointmentService;
import com.ghatana.phr.kernel.service.MedicationServiceExtensions;
import com.ghatana.phr.kernel.service.PatientRecordServiceExtensions;
import com.ghatana.phr.kernel.service.DocumentServiceExtensions;
import com.ghatana.phr.kernel.service.ConsentManagementServiceExtensions;
import com.ghatana.phr.kernel.service.EmergencyAccessLogServiceExtensions;
import com.ghatana.phr.repository.UserRepository;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.RoutingServlet;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Dashboard summary API for the PHR web application.
 *
 * <p>Provides a backend-owned dashboard endpoint that aggregates patient profile,
 * next appointment, medications, recent observations, active conditions, documents,
 * and access alerts into a single payload. This replaces frontend-composed dashboard
 * data with a server-side source of truth.
 *
 * @doc.type class
 * @doc.purpose ActiveJ route adapter serving the patient dashboard summary for the web application
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 */
public final class PhrDashboardRoutes {

    private final Eventloop eventloop;
    private final UserRepository userRepository;
    private final AppointmentService appointmentService;
    private final MedicationServiceExtensions medicationServiceExtensions;
    private final PatientRecordServiceExtensions patientRecordServiceExtensions;
    private final DocumentServiceExtensions documentServiceExtensions;
    private final ConsentManagementServiceExtensions consentServiceExtensions;
    private final EmergencyAccessLogServiceExtensions emergencyAccessLogServiceExtensions;

    public PhrDashboardRoutes(
            Eventloop eventloop,
            UserRepository userRepository,
            AppointmentService appointmentService,
            MedicationServiceExtensions medicationServiceExtensions,
            PatientRecordServiceExtensions patientRecordServiceExtensions,
            DocumentServiceExtensions documentServiceExtensions,
            ConsentManagementServiceExtensions consentServiceExtensions,
            EmergencyAccessLogServiceExtensions emergencyAccessLogServiceExtensions) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
        this.appointmentService = Objects.requireNonNull(appointmentService, "appointmentService must not be null");
        this.medicationServiceExtensions = Objects.requireNonNull(medicationServiceExtensions, "medicationServiceExtensions must not be null");
        this.patientRecordServiceExtensions = Objects.requireNonNull(patientRecordServiceExtensions, "patientRecordServiceExtensions must not be null");
        this.documentServiceExtensions = Objects.requireNonNull(documentServiceExtensions, "documentServiceExtensions must not be null");
        this.consentServiceExtensions = Objects.requireNonNull(consentServiceExtensions, "consentServiceExtensions must not be null");
        this.emergencyAccessLogServiceExtensions = Objects.requireNonNull(emergencyAccessLogServiceExtensions, "emergencyAccessLogServiceExtensions must not be null");
    }

    /**
     * Returns the routing servlet for dashboard endpoints.
     *
     * @return routing servlet; never null
     */
    public AsyncServlet getServlet() {
        return RoutingServlet.builder(eventloop)
            .with(HttpMethod.GET, "/", this::handleGetDashboard)
            .build();
    }

    private Promise<HttpResponse> handleGetDashboard(HttpRequest request) {
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }

        // Fetch patient profile
        Optional<com.ghatana.phr.model.PHRUser> userOpt = userRepository.findByUserId(context.principalId());
        if (userOpt.isEmpty()) {
            return PhrRouteSupport.errorResponse(404, "PATIENT_NOT_FOUND", "Patient not found");
        }

        com.ghatana.phr.model.PHRUser user = userOpt.get();

        // Build dashboard payload with IA-required widgets
        Map<String, Object> dashboard = new LinkedHashMap<>();
        dashboard.put("tenantId", context.tenantId());
        dashboard.put("principalId", context.principalId());
        dashboard.put("role", context.role());
        dashboard.put("correlationId", context.correlationId());

        // Profile summary widget
        Map<String, Object> profileSummary = new LinkedHashMap<>();
        profileSummary.put("name", user.getUsername() != null ? user.getUsername() : context.principalId());
        profileSummary.put("email", user.getEmail());
        profileSummary.put("providerId", user.getProviderId());
        profileSummary.put("active", user.isActive());
        dashboard.put("profileSummary", profileSummary);

        // Fetch next appointment
        return appointmentService.getNextAppointment(context.principalId())
            .map(nextAppointment -> {
                dashboard.put("nextAppointment", nextAppointment != null 
                    ? Map.of(
                        "appointmentId", nextAppointment.getAppointmentId(),
                        "scheduledTime", nextAppointment.getScheduledTime().toString(),
                        "provider", nextAppointment.getProvider(),
                        "type", nextAppointment.getType()
                    )
                    : null);

                // Fetch active medications count
                return medicationServiceExtensions.getActiveMedicationCount(context.principalId())
                    .map(medicationCount -> {
                        dashboard.put("medications", Map.of(
                            "activeCount", medicationCount,
                            "adherenceAlert", medicationCount > 5 // Alert if >5 active meds
                        ));

                        // Fetch recent observations
                        return patientRecordServiceExtensions.getRecentObservations(context.principalId(), 5)
                            .map(observations -> {
                                boolean hasCritical = observations.stream()
                                    .anyMatch(obs -> "critical".equalsIgnoreCase(obs.getSeverity()));
                                dashboard.put("recentObservations", Map.of(
                                    "count", observations.size(),
                                    "hasCritical", hasCritical
                                ));

                                // Fetch active conditions
                                return patientRecordServiceExtensions.getActiveConditions(context.principalId())
                                    .map(conditions -> {
                                        boolean hasChronic = conditions.stream()
                                            .anyMatch(cond -> "chronic".equalsIgnoreCase(cond.getChronicity()));
                                        dashboard.put("activeConditions", Map.of(
                                            "count", conditions.size(),
                                            "hasChronic", hasChronic
                                        ));

                                        // Fetch documents
                                        return documentServiceExtensions.getDocumentCount(context.principalId())
                                            .map(docCount -> {
                                                dashboard.put("documents", Map.of(
                                                    "totalCount", docCount,
                                                    "pendingOcr", 0 // TODO: Implement OCR pending count
                                                ));

                                                // Fetch access alerts
                                                return consentServiceExtensions.getExpiringConsents(context.principalId())
                                                    .map(expiringCount -> {
                                                        return emergencyAccessLogServiceExtensions.hasPendingEmergencyAccess(context.principalId())
                                                            .map(hasPending -> {
                                                                Map<String, Object> accessAlerts = new LinkedHashMap<>();
                                                                accessAlerts.put("expiringConsents", expiringCount);
                                                                accessAlerts.put("emergencyAccessPending", hasPending);
                                                                dashboard.put("accessAlerts", accessAlerts);

                                                                // Freshness timestamp
                                                                dashboard.put("generatedAt", Instant.now().toString());

                                                                return PhrRouteSupport.jsonResponseWithCorrelation(200, dashboard, context.correlationId());
                                                            });
                                                    });
                                            });
                                    });
                            });
                    });
            })
            .recoverWithException(ex -> PhrRouteSupport.errorResponse(500, "DASHBOARD_ERROR", 
                "Failed to build dashboard: " + ex.getMessage()));
    }
}
