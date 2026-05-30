package com.ghatana.phr.api.routes;

import com.ghatana.phr.kernel.service.ConsentManagementService;
import com.ghatana.phr.kernel.service.DocumentService;
import com.ghatana.phr.kernel.service.DurablePhrNotificationSender;
import com.ghatana.phr.kernel.service.PatientRecordService;
import com.ghatana.phr.security.PhrPolicyEvaluator;
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
    private final PhrPolicyEvaluator policyEvaluator;

    public PhrMobileRoutes(
            Eventloop eventloop,
            PatientRecordService patientRecordService,
            ConsentManagementService consentService,
            DocumentService documentService,
            DurablePhrNotificationSender notificationSender) {
        this(eventloop, patientRecordService, consentService, documentService, notificationSender, null);
    }

    public PhrMobileRoutes(
            Eventloop eventloop,
            PatientRecordService patientRecordService,
            ConsentManagementService consentService,
            DocumentService documentService,
            DurablePhrNotificationSender notificationSender,
            PhrPolicyEvaluator policyEvaluator) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
        this.patientRecordService = Objects.requireNonNull(patientRecordService, "patientRecordService must not be null");
        this.consentService = Objects.requireNonNull(consentService, "consentService must not be null");
        this.documentService = Objects.requireNonNull(documentService, "documentService must not be null");
        this.notificationSender = Objects.requireNonNull(notificationSender, "notificationSender must not be null");
        this.policyEvaluator = policyEvaluator;
    }

    private static String formatNotificationTitle(String notificationType) {
        if (notificationType == null || notificationType.isBlank()) {
            return "Notification";
        }
        return switch (notificationType) {
            case "CONSENT_GRANTED" -> "Consent Granted";
            case "CONSENT_REVOKED" -> "Consent Revoked";
            case "CONSENT_EXPIRING" -> "Consent Expiring Soon";
            case "APPOINTMENT_CONFIRMED" -> "Appointment Confirmed";
            case "APPOINTMENT_REMINDER" -> "Appointment Reminder";
            case "APPOINTMENT_CANCELLED" -> "Appointment Cancelled";
            case "LAB_RESULT_AVAILABLE" -> "Lab Result Available";
            case "DOCUMENT_READY" -> "Document Ready";
            case "EMERGENCY_ACCESS_REVIEW" -> "Emergency Access Review Required";
            case "PRESCRIPTION_READY" -> "Prescription Ready";
            default -> notificationType.replace('_', ' ')
                .substring(0, 1).toUpperCase()
                + notificationType.replace('_', ' ').substring(1).toLowerCase();
        };
    }

    private static String formatNotificationDetail(String referenceType, String referenceId) {
        if (referenceType == null || referenceType.isBlank()) {
            return referenceId != null ? "Reference: " + referenceId : "No additional details";
        }
        if (referenceId == null || referenceId.isBlank()) {
            return referenceType;
        }
        return referenceType + ": " + referenceId;
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
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }

        if (policyEvaluator == null) {
            if (!"patient".equals(context.role())) {
                return PhrRouteSupport.policyDenialResponse(403, context.correlationId(), "MOBILE_DASHBOARD_DENIED");
            }
        }

        Promise<PhrPolicyEvaluator.PolicyDecision> decisionPromise = policyEvaluator != null
            ? policyEvaluator.canAccessPhiResourceAsync(
                context,
                context.principalId(),
                "mobile-dashboard",
                "READ",
                context.tenantId(),
                context.facilityId())
            : Promise.of(PhrPolicyEvaluator.PolicyDecision.allowed("mobile-dashboard", "Mobile dashboard access allowed"));

        return decisionPromise
            .then(decision -> {
                if (!decision.isAllowed()) {
                    return PhrRouteSupport.policyDenialResponse(403, context.correlationId(), decision.getReasonCode());
                }

                return patientRecordService.getPatient(context.principalId())
                    .then(opt -> {
                        if (opt.isEmpty()) {
                            return PhrRouteSupport.errorResponse(404, "PATIENT_NOT_FOUND", "Patient not found: " + context.principalId(),
                                context.correlationId());
                        }

                        var patient = opt.get();
                        Map<String, Object> patientData = Map.of(
                            "id", patient.getId(),
                            "name", patient.getDemographics().getFullName(),
                            "age", patient.getDemographics().getAge(),
                            "bloodType", patient.getMedicalHistory() != null ? "O+" : "Unknown",
                            "district", patient.getDemographics().getDistrict()
                        );

                        return documentService.getPatientDocuments(context.principalId(), context.principalId())
                            .then(documents -> {
                                List<Map<String, Object>> records = documents.stream()
                                    .map(doc -> Map.<String, Object>of(
                                        "id", doc.getId(),
                                        "title", doc.getTitle(),
                                        "summary", doc.getDescription() != null ? doc.getDescription() : "",
                                        "fhirPreview", doc.getDocumentType() != null
                                            ? doc.getDocumentType() + "/" + doc.getId()
                                            : "Document/" + doc.getId()
                                    ))
                                    .limit(10)
                                    .toList();

                                return consentService.getPatientGrants(context.principalId())
                                    .then(grants -> {
                                        List<Map<String, Object>> consents = grants.stream()
                                            .map(grant -> Map.<String, Object>of(
                                                "id", grant.getGrantId(),
                                                "grantee", grant.getRecipientId(),
                                                "purpose", grant.getScope() != null ? grant.getScope().toString() : "Treatment",
                                                "active", "ACTIVE".equals(grant.getStatus()),
                                                "createdAt", grant.getCreatedAt().toString()
                                            ))
                                            .limit(10)
                                            .toList();

                                        return notificationSender.getPendingNotifications(context.principalId(), 10)
                                            .then(notifications -> {
                                                List<Map<String, Object>> notificationList = notifications.stream()
                                                    .map(entry -> {
                                                        String title = formatNotificationTitle(entry.notificationType());
                                                        String detail = formatNotificationDetail(entry.referenceType(), entry.referenceId());
                                                        return Map.<String, Object>of(
                                                            "id", entry.id(),
                                                            "title", title,
                                                            "detail", detail
                                                        );
                                                    })
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
            });
    }
}
