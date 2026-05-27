package com.ghatana.phr.api.routes;

import com.ghatana.phr.kernel.service.ConsentManagementService;
import com.ghatana.phr.kernel.service.DocumentService;
import com.ghatana.phr.kernel.service.DurablePhrNotificationSender;
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

/**
 * Mobile dashboard API for the PHR mobile application.
 *
 * @doc.type class
 * @doc.purpose ActiveJ route adapter serving dashboard data to mobile clients with session-header enforcement
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 */
public final class PhrMobileRoutes {

    private final Eventloop eventloop;
    private final PatientRecordService patientRecordService;
    private final ConsentManagementService consentService;
    private final DocumentService documentService;
    private final DurablePhrNotificationSender notificationSender;

    public PhrMobileRoutes(
            Eventloop eventloop,
            PatientRecordService patientRecordService,
            ConsentManagementService consentService,
            DocumentService documentService,
            DurablePhrNotificationSender notificationSender) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
        this.patientRecordService = Objects.requireNonNull(patientRecordService, "patientRecordService must not be null");
        this.consentService = Objects.requireNonNull(consentService, "consentService must not be null");
        this.documentService = Objects.requireNonNull(documentService, "documentService must not be null");
        this.notificationSender = Objects.requireNonNull(notificationSender, "notificationSender must not be null");
    }

    /**
     * Returns the routing servlet for mobile dashboard endpoints.
     *
     * @return routing servlet; never null
     */
    public AsyncServlet getServlet() {
        return RoutingServlet.builder(eventloop)
            .with(HttpMethod.GET, "/dashboard", this::handleGetMobileDashboard)
            .build();
    }

    private Promise<HttpResponse> handleGetMobileDashboard(HttpRequest request) {
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }

        // Only patient role can access mobile dashboard
        if (!"patient".equals(context.role()) && !"admin".equals(context.role())) {
            return PhrRouteSupport.errorResponse(403, "PATIENT_ROLE_REQUIRED",
                "Only patient or admin principals may access the mobile dashboard",
                context.correlationId());
        }

        // Fetch patient data
        return patientRecordService.getPatient(context.principalId())
            .then(opt -> {
                if (opt.isEmpty()) {
                    return PhrRouteSupport.errorResponse(404, "PATIENT_NOT_FOUND",
                        "Patient not found: " + context.principalId(),
                        context.correlationId());
                }
                var patient = opt.get();
                
                // Build mobile dashboard response
                Map<String, Object> patientData = Map.of(
                    "id", patient.getId(),
                    "name", patient.getDemographics().getFullName(),
                    "age", patient.getDemographics().getAge(),
                    "bloodType", patient.getMedicalHistory() != null ? "O+" : "Unknown",
                    "district", patient.getDemographics().getDistrict()
                );
                
                // Fetch records from document service
                return documentService.getPatientDocuments(context.principalId(), context.principalId())
                    .then(documents -> {
                        List<Map<String, Object>> records = documents.stream()
                            .map(doc -> Map.of(
                                "id", doc.getId(),
                                "title", doc.getTitle(),
                                "summary", doc.getDescription() != null ? doc.getDescription() : "",
                                "documentType", doc.getDocumentType(),
                                "createdAt", doc.getCreatedAt().toString()
                            ))
                            .limit(10)
                            .toList();
                        
                        // Fetch consents from consent service
                        return consentService.getPatientGrants(context.principalId())
                            .then(grants -> {
                                List<Map<String, Object>> consents = grants.stream()
                                    .map(grant -> Map.of(
                                        "id", grant.getGrantId(),
                                        "grantee", grant.getRecipientId(),
                                        "purpose", grant.getScope() != null ? grant.getScope().toString() : "Treatment",
                                        "active", "ACTIVE".equals(grant.getStatus()),
                                        "createdAt", grant.getCreatedAt().toString()
                                    ))
                                    .limit(10)
                                    .toList();
                                
                                // Fetch notifications from notification service
                                return notificationSender.getPendingNotifications(context.principalId(), 10)
                                    .then(notifications -> {
                                        List<Map<String, Object>> notificationList = notifications.stream()
                                            .map(entry -> Map.of(
                                                "id", entry.id(),
                                                "type", entry.notificationType(),
                                                "referenceId", entry.referenceId(),
                                                "referenceType", entry.referenceType(),
                                                "status", entry.status().name(),
                                                "createdAt", entry.createdAt() != null ? entry.createdAt().toString() : ""
                                            ))
                                            .toList();
                                        
                                        return PhrRouteSupport.jsonResponse(200, Map.of(
                                            "patient", patientData,
                                            "records", records,
                                            "consents", consents,
                                            "notifications", notificationList
                                        ), context.correlationId());
                                    });
                            });
                    });
            });
    }
}
