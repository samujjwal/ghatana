package com.ghatana.phr.api.routes;

import com.fasterxml.jackson.databind.JsonNode;
import com.ghatana.phr.kernel.service.AppointmentService;
import com.ghatana.phr.kernel.service.BillingService;
import com.ghatana.phr.kernel.service.ConsentManagementService;
import com.ghatana.phr.kernel.service.ReferralService;
import com.ghatana.phr.kernel.service.TelemedicineService;
import com.ghatana.phr.security.PhrPolicyEvaluator;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.RoutingServlet;
import io.activej.promise.Promise;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Administrative journey API routes for PHR appointments, telemedicine, referrals, and billing.
 *
 * @doc.type class
 * @doc.purpose ActiveJ route adapter for administrative PHR backend journeys
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 */
public final class PhrAdministrativeRoutes {

    private final Eventloop eventloop;
    private final AppointmentService appointmentService;
    private final TelemedicineService telemedicineService;
    private final ReferralService referralService;
    private final BillingService billingService;
    private final PhrPolicyEvaluator policyEvaluator;

    public PhrAdministrativeRoutes(
            Eventloop eventloop,
            AppointmentService appointmentService,
            TelemedicineService telemedicineService,
            ReferralService referralService,
            BillingService billingService,
            ConsentManagementService consentService,
            PhrPolicyEvaluator policyEvaluator) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
        this.appointmentService = Objects.requireNonNull(appointmentService, "appointmentService must not be null");
        this.telemedicineService = Objects.requireNonNull(telemedicineService, "telemedicineService must not be null");
        this.referralService = Objects.requireNonNull(referralService, "referralService must not be null");
        this.billingService = Objects.requireNonNull(billingService, "billingService must not be null");
        Objects.requireNonNull(consentService, "consentService must not be null");
        this.policyEvaluator = Objects.requireNonNull(policyEvaluator, "policyEvaluator must not be null");
    }

    /**
     * Returns the routing servlet for administrative endpoints.
     *
     * @return routing servlet; never null
     */
    public AsyncServlet getServlet() {
        return RoutingServlet.builder(eventloop)
            .with(HttpMethod.GET, "/dashboard", this::handleGetAdminDashboard)
            .with("/appointments/*", appointmentServlet())
            .with("/telemedicine/*", telemedicineServlet())
            .with("/referrals/*", referralServlet())
            .with("/billing/*", getBillingServlet())
            .build();
    }

    private Promise<HttpResponse> handleGetAdminDashboard(HttpRequest request) {
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }

        if (!"admin".equals(context.role())) {
            return PhrRouteSupport.errorResponse(403, "ADMIN_ROLE_REQUIRED",
                "Only admin principals may access the admin dashboard",
                context.correlationId());
        }

        // Return admin dashboard with system-wide metrics
        return PhrRouteSupport.jsonResponse(200, Map.of(
            "adminId", context.principalId(),
            "tenantId", context.tenantId(),
            "metrics", Map.of(
                "totalPatients", 0,
                "activeAppointments", 0,
                "pendingReferrals", 0,
                "openClaims", 0
            ),
            "summary", "Admin dashboard for PHR system management"
        ), context.correlationId());
    }

    /**
     * Returns a patient-facing servlet for the top-level {@code /appointments} path.
     *
     * <p>Handles patient self-scheduling requests. This is separate from the admin
     * appointment management at {@code /admin/appointments/*}.
     *
     * @return routing servlet; never null
     */
    public AsyncServlet getPatientFacingServlet() {
        return RoutingServlet.builder(eventloop)
            .with(HttpMethod.POST, "/", this::handleCreateSchedulingRequest)
            .build();
    }

    private AsyncServlet appointmentServlet() {
        return RoutingServlet.builder(eventloop)
            .with(HttpMethod.GET, "/", this::handleListAppointments)
            .with(HttpMethod.GET, "/slots", this::handleAvailableSlots)
            .with(HttpMethod.POST, "/:appointmentId/cancel", this::handleCancelAppointment)
            .build();
    }

    private Promise<HttpResponse> handleCreateSchedulingRequest(HttpRequest request) {
        PhrRouteSupport.PhrRequestContext context;
        String idempotencyKey;
        try {
            context = PhrRouteSupport.requireContext(request);
            idempotencyKey = PhrRouteSupport.extractIdempotencyKey(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }

        return request.loadBody()
            .then(body -> {
                String specialty;
                String preferredDate;
                String notes;
                try {
                    JsonNode node = PhrRouteSupport.JSON.readTree(body.getString(StandardCharsets.UTF_8));
                    specialty = requireTextField(node, "specialty");
                    preferredDate = requireTextField(node, "preferredDate");
                    notes = node.path("notes").isMissingNode() ? null : node.path("notes").asText(null);
                } catch (IllegalArgumentException ex) {
                    return PhrRouteSupport.errorResponse(400, "INVALID_APPOINTMENT_REQUEST", ex.getMessage());
                } catch (Exception ex) {
                    return PhrRouteSupport.errorResponse(400, "INVALID_APPOINTMENT_REQUEST", "Request body must be valid JSON");
                }

                String requestId = "req-" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12);
                String createdAt = Instant.now().toString();
                String patientId = context.principalId();

                Map<String, Object> result = new java.util.LinkedHashMap<>();
                result.put("id", requestId);
                result.put("status", "requested");
                result.put("specialty", specialty);
                result.put("preferredDate", preferredDate);
                result.put("createdAt", createdAt);
                if (notes != null && !notes.isBlank()) {
                    result.put("notes", notes);
                }
                result.put("patientId", patientId);
                if (idempotencyKey != null) {
                    result.put("idempotencyKey", idempotencyKey);
                }

                return PhrRouteSupport.jsonResponse(201, result);
            });
    }

    private static String requireTextField(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull() || field.asText("").isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return field.asText().strip();
    }

    private AsyncServlet telemedicineServlet() {
        return RoutingServlet.builder(eventloop)
            .with(HttpMethod.POST, "/sessions", this::handleScheduleTelemedicine)
            .with(HttpMethod.GET, "/sessions/:sessionId", this::handleGetTelemedicineSession)
            .with(HttpMethod.POST, "/sessions/:sessionId/start", this::handleStartTelemedicine)
            .with(HttpMethod.POST, "/sessions/:sessionId/complete", this::handleCompleteTelemedicine)
            .with(HttpMethod.POST, "/sessions/:sessionId/cancel", this::handleCancelTelemedicine)
            .with(HttpMethod.GET, "/", this::handleListTelemedicine)
            .build();
    }

    private AsyncServlet referralServlet() {
        return RoutingServlet.builder(eventloop)
            .with(HttpMethod.POST, "/", this::handleCreateReferral)
            .with(HttpMethod.GET, "/:referralId", this::handleGetReferral)
            .with(HttpMethod.POST, "/:referralId/accept", this::handleAcceptReferral)
            .with(HttpMethod.POST, "/:referralId/close", this::handleCloseReferral)
            .with(HttpMethod.GET, "/", this::handleListReferrals)
            .build();
    }

    public AsyncServlet getBillingServlet() {
        return RoutingServlet.builder(eventloop)
            .with(HttpMethod.POST, "/encounters", this::handleCreateEncounter)
            .with(HttpMethod.GET, "/encounters/:encounterId", this::handleGetEncounter)
            .with(HttpMethod.POST, "/encounters/:encounterId/close", this::handleCloseEncounter)
            .with(HttpMethod.POST, "/claims", this::handleSubmitClaim)
            .with(HttpMethod.GET, "/claims/:claimId", this::handleGetClaim)
            .with(HttpMethod.POST, "/claims/:claimId/status", this::handleUpdateClaimStatus)
            .with(HttpMethod.GET, "/", this::handleBillingHistory)
            .build();
    }

    private Promise<HttpResponse> handleListAppointments(HttpRequest request) {
        String status = request.getQueryParameter("status");
        return withPatientAccess(request, "appointments", patientId -> appointmentService.getPatientAppointments(patientId, status)
            .then(items -> PhrRouteSupport.jsonResponse(200, Map.of("patientId", patientId, "items", items, "count", items.size()))));
    }

    private Promise<HttpResponse> handleAvailableSlots(HttpRequest request) {
        try {
            PhrRouteSupport.requireContext(request);
            String providerId = PhrRouteSupport.requiredQuery(request, "providerId");
            String date = PhrRouteSupport.requiredQuery(request, "date");
            return appointmentService.getAvailableSlots(providerId, date)
                .then(items -> PhrRouteSupport.jsonResponse(200, Map.of("providerId", providerId, "items", items, "count", items.size())));
        } catch (RuntimeException ex) {
            return PhrRouteSupport.errorResponse(400, "INVALID_SLOT_QUERY", ex.getMessage());
        }
    }

    private Promise<HttpResponse> handleCancelAppointment(HttpRequest request) {
        String reason = request.getQueryParameter("reason");
        return withPatientAccess(request, "appointments", ignored -> appointmentService.cancelAppointment(
                request.getPathParameter("appointmentId"),
                reason == null || reason.isBlank() ? "cancelled by API request" : reason)
            .then($ -> PhrRouteSupport.jsonResponse(200, Map.of(
                "appointmentId", request.getPathParameter("appointmentId"),
                "status", "CANCELLED"
            ))));
    }

    private Promise<HttpResponse> handleScheduleTelemedicine(HttpRequest request) {
        return withBodyAndConsent(request, "telemedicine", TelemedicineService.TeleSession.class,
            session -> telemedicineService.scheduleSession(session)
                .then(stored -> PhrRouteSupport.jsonResponse(201, stored)));
    }

    private Promise<HttpResponse> handleGetTelemedicineSession(HttpRequest request) {
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }
        return telemedicineService.getSession(request.getPathParameter("sessionId"))
            .then(session -> {
                if (session.isEmpty()) {
                    return PhrRouteSupport.errorResponse(404, "TELEMEDICINE_SESSION_NOT_FOUND", "Telemedicine session not found");
                }
                return requireAccess(context, session.get().patientId(), "telemedicine")
                    .then(allowed -> allowed
                        ? PhrRouteSupport.jsonResponse(200, session.get())
                        : PhrRouteSupport.errorResponse(403, "CONSENT_REQUIRED", "Consent is required for telemedicine"));
            });
    }

    private Promise<HttpResponse> handleStartTelemedicine(HttpRequest request) {
        return withPatientAccess(request, "telemedicine", ignored -> telemedicineService.startSession(request.getPathParameter("sessionId"))
            .then(updated -> PhrRouteSupport.jsonResponse(200, updated)));
    }

    private Promise<HttpResponse> handleCompleteTelemedicine(HttpRequest request) {
        String notes = request.getQueryParameter("notes");
        return withPatientAccess(request, "telemedicine", ignored -> telemedicineService.completeSession(
                request.getPathParameter("sessionId"),
                notes)
            .then(updated -> PhrRouteSupport.jsonResponse(200, updated)));
    }

    private Promise<HttpResponse> handleCancelTelemedicine(HttpRequest request) {
        String reason = request.getQueryParameter("reason");
        return withPatientAccess(request, "telemedicine", ignored -> telemedicineService.cancelSession(
                request.getPathParameter("sessionId"),
                reason == null || reason.isBlank() ? "cancelled by API request" : reason)
            .then(updated -> PhrRouteSupport.jsonResponse(200, updated)));
    }

    private Promise<HttpResponse> handleListTelemedicine(HttpRequest request) {
        return withPatientAccess(request, "telemedicine", patientId -> telemedicineService.getPatientSessions(patientId)
            .then(items -> PhrRouteSupport.jsonResponse(200, Map.of("patientId", patientId, "items", items, "count", items.size()))));
    }

    private Promise<HttpResponse> handleCreateReferral(HttpRequest request) {
        return withBodyAndConsent(request, "referrals", ReferralService.Referral.class,
            referral -> referralService.createReferral(referral)
                .then(stored -> PhrRouteSupport.jsonResponse(201, stored)));
    }

    private Promise<HttpResponse> handleGetReferral(HttpRequest request) {
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }
        return referralService.getReferral(request.getPathParameter("referralId"))
            .then(referral -> {
                if (referral.isEmpty()) {
                    return PhrRouteSupport.errorResponse(404, "REFERRAL_NOT_FOUND", "Referral not found");
                }
                return requireAccess(context, referral.get().patientId(), "referrals")
                    .then(allowed -> allowed
                        ? PhrRouteSupport.jsonResponse(200, referral.get())
                        : PhrRouteSupport.errorResponse(403, "CONSENT_REQUIRED", "Consent is required for referrals"));
            });
    }

    private Promise<HttpResponse> handleAcceptReferral(HttpRequest request) {
        return withPatientAccess(request, "referrals", ignored -> referralService.acceptReferral(
                request.getPathParameter("referralId"),
                principalFrom(request))
            .then(updated -> PhrRouteSupport.jsonResponse(200, updated)));
    }

    private Promise<HttpResponse> handleCloseReferral(HttpRequest request) {
        String notes = request.getQueryParameter("notes");
        return withPatientAccess(request, "referrals", ignored -> referralService.closeReferral(
                request.getPathParameter("referralId"),
                notes)
            .then(updated -> PhrRouteSupport.jsonResponse(200, updated)));
    }

    private Promise<HttpResponse> handleListReferrals(HttpRequest request) {
        return withPatientAccess(request, "referrals", patientId -> referralService.getPatientReferrals(patientId)
            .then(items -> PhrRouteSupport.jsonResponse(200, Map.of("patientId", patientId, "items", items, "count", items.size()))));
    }

    private Promise<HttpResponse> handleCreateEncounter(HttpRequest request) {
        return withBodyAndConsent(request, "billing", BillingService.BillingEncounter.class,
            encounter -> billingService.createEncounter(encounter)
                .then(stored -> PhrRouteSupport.jsonResponse(201, stored)));
    }

    private Promise<HttpResponse> handleGetEncounter(HttpRequest request) {
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }
        return billingService.getEncounter(request.getPathParameter("encounterId"))
            .then(encounter -> {
                if (encounter.isEmpty()) {
                    return PhrRouteSupport.errorResponse(404, "BILLING_ENCOUNTER_NOT_FOUND", "Billing encounter not found");
                }
                return requireAccess(context, encounter.get().patientId(), "billing")
                    .then(allowed -> allowed
                        ? PhrRouteSupport.jsonResponse(200, encounter.get())
                        : PhrRouteSupport.errorResponse(403, "CONSENT_REQUIRED", "Consent is required for billing"));
            });
    }

    private Promise<HttpResponse> handleCloseEncounter(HttpRequest request) {
        return withPatientAccess(request, "billing", ignored -> billingService.closeEncounter(request.getPathParameter("encounterId"))
            .then(updated -> PhrRouteSupport.jsonResponse(200, updated)));
    }

    private Promise<HttpResponse> handleSubmitClaim(HttpRequest request) {
        return withBodyAndConsent(request, "billing", BillingService.InsuranceClaim.class,
            claim -> billingService.submitClaim(claim)
                .then(stored -> PhrRouteSupport.jsonResponse(201, stored)));
    }

    private Promise<HttpResponse> handleGetClaim(HttpRequest request) {
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }
        return billingService.getClaim(request.getPathParameter("claimId"))
            .then(claim -> {
                if (claim.isEmpty()) {
                    return PhrRouteSupport.errorResponse(404, "CLAIM_NOT_FOUND", "Claim not found");
                }
                return requireAccess(context, claim.get().patientId(), "billing")
                    .then(allowed -> allowed
                        ? PhrRouteSupport.jsonResponse(200, claim.get())
                        : PhrRouteSupport.errorResponse(403, "CONSENT_REQUIRED", "Consent is required for billing"));
            });
    }

    private Promise<HttpResponse> handleUpdateClaimStatus(HttpRequest request) {
        return withPatientAccess(request, "billing", ignored -> request.loadBody()
            .then(body -> {
                BillingService.ClaimStatus status;
                String note;
                try {
                    JsonNode node = PhrRouteSupport.JSON.readTree(body.getString(StandardCharsets.UTF_8));
                    String rawStatus = node.path("status").asText(null);
                    if (rawStatus == null || rawStatus.isBlank()) {
                        throw new IllegalArgumentException("status is required");
                    }
                    status = BillingService.ClaimStatus.valueOf(rawStatus);
                    note = node.path("note").isMissingNode() ? null : node.path("note").asText();
                } catch (Exception ex) {
                    return PhrRouteSupport.errorResponse(400, "INVALID_CLAIM_STATUS", ex.getMessage());
                }
                return billingService.updateClaimStatus(request.getPathParameter("claimId"), status, note)
                    .then(updated -> PhrRouteSupport.jsonResponse(200, updated));
            }));
    }

    private Promise<HttpResponse> handleBillingHistory(HttpRequest request) {
        return withPatientAccess(request, "billing", patientId -> billingService.getPatientBillingHistory(patientId)
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
        if ("admin".equals(context.role())) {
            return Promise.of(true);
        }
        return policyEvaluator.canAccessPhiResourceAsync(
                context,
                patientId,
                resourceType,
                "READ",
                context.tenantId(),
                context.facilityId())
            .map(PhrPolicyEvaluator.PolicyDecision::isAllowed);
    }

    private static String principalFrom(HttpRequest request) {
        return PhrRouteSupport.requireContext(request).principalId();
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
