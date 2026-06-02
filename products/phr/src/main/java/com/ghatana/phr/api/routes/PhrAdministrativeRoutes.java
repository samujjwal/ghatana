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
import java.util.LinkedHashMap;
import java.util.List;
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
    private final ConsentManagementService consentService;
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
        this.consentService = Objects.requireNonNull(consentService, "consentService must not be null");
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
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage(), correlationId);
        }

        if (!"admin".equals(context.role())) {
            return PhrRouteSupport.errorResponse(403, "ADMIN_ROLE_REQUIRED",
                "Only admin principals may access the admin dashboard",
                context.correlationId());
        }

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
            .with(HttpMethod.POST, "/", this::handlePatientFacingAppointmentPost)
            .with(HttpMethod.GET, "/slots", this::handleAvailableSlots)
            .with(HttpMethod.POST, "/:appointmentId/reschedule", this::handlePatientFacingRescheduleAppointment)
            .with(HttpMethod.POST, "/:appointmentId/cancel", this::handlePatientFacingCancelAppointment)
            .with(HttpMethod.GET, "/", this::handleListAppointments)
            .build();
    }

    private AsyncServlet appointmentServlet() {
        return RoutingServlet.builder(eventloop)
            .with(HttpMethod.POST, "/", this::handleBookAppointment)
            .with(HttpMethod.POST, "/:appointmentId/reschedule", this::handleRescheduleAppointment)
            .with(HttpMethod.GET, "/", this::handleListAppointments)
            .with(HttpMethod.GET, "/slots", this::handleAvailableSlots)
            .with(HttpMethod.POST, "/:appointmentId/cancel", this::handleCancelAppointment)
            .build();
    }

    private Promise<HttpResponse> handlePatientFacingAppointmentPost(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        String idempotencyKey;
        try {
            context = PhrRouteSupport.requireContext(request);
            idempotencyKey = PhrRouteSupport.extractIdempotencyKey(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage(), correlationId);
        }

        return request.loadBody()
            .then(body -> {
                JsonNode node;
                try {
                    node = PhrRouteSupport.JSON.readTree(body.getString(StandardCharsets.UTF_8));
                } catch (Exception ex) {
                    return PhrRouteSupport.errorResponse(400, "INVALID_APPOINTMENT_REQUEST", "Request body must be valid JSON", correlationId);
                }

                if (node.hasNonNull("specialty") && node.hasNonNull("preferredDate") && !node.hasNonNull("providerId")) {
                    return createSchedulingRequest(context, idempotencyKey, node, correlationId);
                }

                AppointmentService.AppointmentRequest appointmentRequest;
                try {
                    appointmentRequest = parseAppointmentRequest(node);
                } catch (IllegalArgumentException ex) {
                    return PhrRouteSupport.errorResponse(400, "INVALID_APPOINTMENT_REQUEST", ex.getMessage(), correlationId);
                }

                return requireAccess(context, appointmentRequest.getPatientId(), "appointments", "WRITE")
                    .then(decision -> decision.isAllowed()
                        ? appointmentService.createAppointment(appointmentRequest)
                            .then(stored -> PhrRouteSupport.jsonResponse(201, appointmentDto(stored), correlationId))
                        : PhrRouteSupport.policyDenialResponse(403, context.correlationId(), decision.getReasonCode()));
            });
    }

    private Promise<HttpResponse> createSchedulingRequest(
            PhrRouteSupport.PhrRequestContext context,
            String idempotencyKey,
            JsonNode node,
            String correlationId) {
        AppointmentService.SchedulingRequest schedulingRequest;
        try {
            schedulingRequest = new AppointmentService.SchedulingRequest(
                null,
                context.principalId(),
                requireTextField(node, "specialty"),
                requireTextField(node, "preferredDate"),
                textField(node, "notes"),
                null,
                null,
                idempotencyKey
            );
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "INVALID_APPOINTMENT_REQUEST", ex.getMessage(), correlationId);
        }
        return requireAccess(context, context.principalId(), "appointments", "WRITE")
            .then(decision -> decision.isAllowed()
                ? appointmentService.createSchedulingRequest(schedulingRequest, idempotencyKey, context.principalId())
                    .then(stored -> PhrRouteSupport.jsonResponse(201, schedulingRequestDto(stored), correlationId))
                : PhrRouteSupport.policyDenialResponse(403, context.correlationId(), decision.getReasonCode()));
    }

    private static String requireTextField(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull() || field.asText("").isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return field.asText().strip();
    }

    private static String textField(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull() || field.asText("").isBlank()) {
            return null;
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
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        String status = request.getQueryParameter("status");
        return withPatientAccess(request, "appointments", "READ", patientId -> appointmentService.getPatientAppointments(patientId, status)
            .then(items -> {
                List<Map<String, Object>> dtos = items.stream()
                    .map(PhrAdministrativeRoutes::appointmentDto)
                    .toList();
                return PhrRouteSupport.jsonResponse(200, Map.of("patientId", patientId, "items", dtos, "count", dtos.size()), correlationId);
            }));
    }

    private Promise<HttpResponse> handleBookAppointment(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        return withBodyAndConsent(request, "appointments", "WRITE", AppointmentService.AppointmentRequest.class,
            appointmentRequest -> appointmentService.createAppointment(appointmentRequest)
                .then(stored -> PhrRouteSupport.jsonResponse(201, appointmentDto(stored), correlationId)));
    }

    private Promise<HttpResponse> handleRescheduleAppointment(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        return withPatientAccess(request, "appointments", "WRITE", ignored -> request.loadBody()
            .then(body -> {
                String newDateTime;
                try {
                    JsonNode node = PhrRouteSupport.JSON.readTree(body.getString(StandardCharsets.UTF_8));
                    newDateTime = requireTextField(node, "newDateTime");
                } catch (Exception ex) {
                    return PhrRouteSupport.errorResponse(400, "INVALID_RESCHEDULE", ex.getMessage(), correlationId);
                }
                return appointmentService.rescheduleAppointment(request.getPathParameter("appointmentId"), newDateTime)
                    .then(updated -> PhrRouteSupport.jsonResponse(200, appointmentDto(updated), correlationId));
            }));
    }

    private Promise<HttpResponse> handlePatientFacingRescheduleAppointment(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage(), correlationId);
        }
        PhrRouteSupport.PhrRequestContext finalContext = context;
        return request.loadBody()
            .then(body -> {
                String patientId;
                String newDateTime;
                try {
                    JsonNode node = PhrRouteSupport.JSON.readTree(body.getString(StandardCharsets.UTF_8));
                    patientId = firstNonBlank(textField(node, "patientId"), finalContext.principalId());
                    newDateTime = firstNonBlank(textField(node, "newDateTime"), textField(node, "scheduledTime"), textField(node, "newSlot"));
                    if (newDateTime == null || newDateTime.isBlank()) {
                        throw new IllegalArgumentException("newDateTime is required");
                    }
                } catch (Exception ex) {
                    return PhrRouteSupport.errorResponse(400, "INVALID_RESCHEDULE", ex.getMessage(), correlationId);
                }
                return requireAccess(finalContext, patientId, "appointments", "WRITE")
                    .then(decision -> decision.isAllowed()
                        ? appointmentService.rescheduleAppointment(request.getPathParameter("appointmentId"), newDateTime)
                            .then(updated -> PhrRouteSupport.jsonResponse(200, appointmentDto(updated), correlationId))
                        : PhrRouteSupport.policyDenialResponse(403, finalContext.correlationId(), decision.getReasonCode()));
            });
    }

    private Promise<HttpResponse> handleAvailableSlots(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        try {
            PhrRouteSupport.requireContext(request);
            String providerId = PhrRouteSupport.requiredQuery(request, "providerId");
            String date = PhrRouteSupport.requiredQuery(request, "date");
            return appointmentService.getAvailableSlots(providerId, date)
                .then(items -> {
                    List<Map<String, Object>> dtos = items.stream()
                        .map(PhrAdministrativeRoutes::timeSlotDto)
                        .toList();
                    return PhrRouteSupport.jsonResponse(200, Map.of("providerId", providerId, "items", dtos, "count", dtos.size()), correlationId);
                });
        } catch (RuntimeException ex) {
            return PhrRouteSupport.errorResponse(400, "INVALID_SLOT_QUERY", ex.getMessage(), correlationId);
        }
    }

    private Promise<HttpResponse> handleCancelAppointment(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        String reason = request.getQueryParameter("reason");
        return withPatientAccess(request, "appointments", "WRITE", ignored -> appointmentService.cancelAppointment(
                request.getPathParameter("appointmentId"),
                reason == null || reason.isBlank() ? "cancelled by API request" : reason)
            .then($ -> PhrRouteSupport.jsonResponse(200, Map.of(
                "appointmentId", request.getPathParameter("appointmentId"),
                "status", "cancelled",
                "success", true
            ), correlationId)));
    }

    private Promise<HttpResponse> handlePatientFacingCancelAppointment(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage(), correlationId);
        }
        String queryReason = request.getQueryParameter("reason");
        PhrRouteSupport.PhrRequestContext finalContext = context;
        return request.loadBody()
            .then(body -> {
                String patientId = finalContext.principalId();
                String reason = queryReason;
                try {
                    if (body.getArray().length > 0) {
                        JsonNode node = PhrRouteSupport.JSON.readTree(body.getString(StandardCharsets.UTF_8));
                        patientId = firstNonBlank(textField(node, "patientId"), patientId);
                        reason = firstNonBlank(textField(node, "reason"), reason);
                    }
                } catch (Exception ex) {
                    return PhrRouteSupport.errorResponse(400, "INVALID_CANCEL", ex.getMessage(), correlationId);
                }
                String finalReason = reason == null || reason.isBlank() ? "cancelled by API request" : reason;
                String finalPatientId = patientId;
                return requireAccess(finalContext, finalPatientId, "appointments", "WRITE")
                    .then(decision -> decision.isAllowed()
                        ? appointmentService.cancelAppointment(request.getPathParameter("appointmentId"), finalReason)
                            .then($ -> PhrRouteSupport.jsonResponse(200, Map.of(
                                "appointmentId", request.getPathParameter("appointmentId"),
                                "status", "cancelled",
                                "success", true
                            ), correlationId))
                        : PhrRouteSupport.policyDenialResponse(403, finalContext.correlationId(), decision.getReasonCode()));
            });
    }

    private Promise<HttpResponse> handleScheduleTelemedicine(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        return withBodyAndConsent(request, "telemedicine", "WRITE", TelemedicineService.TeleSession.class,
            session -> telemedicineService.scheduleSession(session)
                .then(stored -> PhrRouteSupport.jsonResponse(201, stored, correlationId)));
    }

    private Promise<HttpResponse> handleGetTelemedicineSession(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage(), correlationId);
        }
        return telemedicineService.getSession(request.getPathParameter("sessionId"))
            .then(session -> {
                if (session.isEmpty()) {
                    return PhrRouteSupport.errorResponse(404, "TELEMEDICINE_SESSION_NOT_FOUND", "Telemedicine session not found", correlationId);
                }
                return requireAccess(context, session.get().patientId(), "telemedicine", "READ")
                    .then(decision -> decision.isAllowed()
                        ? PhrRouteSupport.jsonResponse(200, session.get(), correlationId)
                        : PhrRouteSupport.policyDenialResponse(403, context.correlationId(), decision.getReasonCode()));
            });
    }

    private Promise<HttpResponse> handleStartTelemedicine(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        return withPatientAccess(request, "telemedicine", "WRITE", ignored -> telemedicineService.startSession(request.getPathParameter("sessionId"))
            .then(updated -> PhrRouteSupport.jsonResponse(200, updated, correlationId)));
    }

    private Promise<HttpResponse> handleCompleteTelemedicine(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        String notes = request.getQueryParameter("notes");
        return withPatientAccess(request, "telemedicine", "WRITE", ignored -> telemedicineService.completeSession(
                request.getPathParameter("sessionId"),
                notes)
            .then(updated -> PhrRouteSupport.jsonResponse(200, updated, correlationId)));
    }

    private Promise<HttpResponse> handleCancelTelemedicine(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        String reason = request.getQueryParameter("reason");
        return withPatientAccess(request, "telemedicine", "WRITE", ignored -> telemedicineService.cancelSession(
                request.getPathParameter("sessionId"),
                reason == null || reason.isBlank() ? "cancelled by API request" : reason)
            .then(updated -> PhrRouteSupport.jsonResponse(200, updated, correlationId)));
    }

    private Promise<HttpResponse> handleListTelemedicine(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        return withPatientAccess(request, "telemedicine", "READ", patientId -> telemedicineService.getPatientSessions(patientId)
            .then(items -> PhrRouteSupport.jsonResponse(200, Map.of("patientId", patientId, "items", items, "count", items.size()), correlationId)));
    }

    private Promise<HttpResponse> handleCreateReferral(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        return withBodyAndConsent(request, "referrals", "WRITE", ReferralService.Referral.class,
            referral -> referralService.createReferral(referral)
                .then(stored -> PhrRouteSupport.jsonResponse(201, stored, correlationId)));
    }

    private Promise<HttpResponse> handleGetReferral(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage(), correlationId);
        }
        return referralService.getReferral(request.getPathParameter("referralId"))
            .then(referral -> {
                if (referral.isEmpty()) {
                    return PhrRouteSupport.errorResponse(404, "REFERRAL_NOT_FOUND", "Referral not found", correlationId);
                }
                return requireAccess(context, referral.get().patientId(), "referrals", "READ")
                    .then(decision -> decision.isAllowed()
                        ? PhrRouteSupport.jsonResponse(200, referral.get(), correlationId)
                        : PhrRouteSupport.policyDenialResponse(403, context.correlationId(), decision.getReasonCode()));
            });
    }

    private Promise<HttpResponse> handleAcceptReferral(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        return withPatientAccess(request, "referrals", "WRITE", ignored -> referralService.acceptReferral(
                request.getPathParameter("referralId"),
                principalFrom(request))
            .then(updated -> PhrRouteSupport.jsonResponse(200, updated, correlationId)));
    }

    private Promise<HttpResponse> handleCloseReferral(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        String notes = request.getQueryParameter("notes");
        return withPatientAccess(request, "referrals", "WRITE", ignored -> referralService.closeReferral(
                request.getPathParameter("referralId"),
                notes)
            .then(updated -> PhrRouteSupport.jsonResponse(200, updated, correlationId)));
    }

    private Promise<HttpResponse> handleListReferrals(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        return withPatientAccess(request, "referrals", "READ", patientId -> referralService.getPatientReferrals(patientId)
            .then(items -> PhrRouteSupport.jsonResponse(200, Map.of("patientId", patientId, "items", items, "count", items.size()), correlationId)));
    }

    private Promise<HttpResponse> handleCreateEncounter(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        return withBodyAndConsent(request, "billing", "WRITE", BillingService.BillingEncounter.class,
            encounter -> billingService.createEncounter(encounter)
                .then(stored -> PhrRouteSupport.jsonResponse(201, stored, correlationId)));
    }

    private Promise<HttpResponse> handleGetEncounter(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage(), correlationId);
        }
        return billingService.getEncounter(request.getPathParameter("encounterId"))
            .then(encounter -> {
                if (encounter.isEmpty()) {
                    return PhrRouteSupport.errorResponse(404, "BILLING_ENCOUNTER_NOT_FOUND", "Billing encounter not found", correlationId);
                }
                return requireAccess(context, encounter.get().patientId(), "billing", "READ")
                    .then(decision -> decision.isAllowed()
                        ? PhrRouteSupport.jsonResponse(200, encounter.get(), correlationId)
                        : PhrRouteSupport.policyDenialResponse(403, context.correlationId(), decision.getReasonCode()));
            });
    }

    private Promise<HttpResponse> handleCloseEncounter(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        return withPatientAccess(request, "billing", "WRITE", ignored -> billingService.closeEncounter(request.getPathParameter("encounterId"))
            .then(updated -> PhrRouteSupport.jsonResponse(200, updated, correlationId)));
    }

    private Promise<HttpResponse> handleSubmitClaim(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        return withBodyAndConsent(request, "billing", "WRITE", BillingService.InsuranceClaim.class,
            claim -> billingService.submitClaim(claim)
                .then(stored -> PhrRouteSupport.jsonResponse(201, stored, correlationId)));
    }

    private Promise<HttpResponse> handleGetClaim(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage(), correlationId);
        }
        return billingService.getClaim(request.getPathParameter("claimId"))
            .then(claim -> {
                if (claim.isEmpty()) {
                    return PhrRouteSupport.errorResponse(404, "CLAIM_NOT_FOUND", "Claim not found", correlationId);
                }
                return requireAccess(context, claim.get().patientId(), "billing", "READ")
                    .then(decision -> decision.isAllowed()
                        ? PhrRouteSupport.jsonResponse(200, claim.get(), correlationId)
                        : PhrRouteSupport.policyDenialResponse(403, context.correlationId(), decision.getReasonCode()));
            });
    }

    private Promise<HttpResponse> handleUpdateClaimStatus(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        return withPatientAccess(request, "billing", "WRITE", ignored -> request.loadBody()
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
                    return PhrRouteSupport.errorResponse(400, "INVALID_CLAIM_STATUS", ex.getMessage(), correlationId);
                }
                return billingService.updateClaimStatus(request.getPathParameter("claimId"), status, note)
                    .then(updated -> PhrRouteSupport.jsonResponse(200, updated, correlationId));
            }));
    }

    private Promise<HttpResponse> handleBillingHistory(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        return withPatientAccess(request, "billing", "READ", patientId -> billingService.getPatientBillingHistory(patientId)
            .then(items -> PhrRouteSupport.jsonResponse(200, Map.of("patientId", patientId, "items", items, "count", items.size()), correlationId)));
    }

    private <T> Promise<HttpResponse> withBodyAndConsent(
            HttpRequest request,
            String resourceType,
            String action,
            Class<T> type,
            java.util.function.Function<T, Promise<HttpResponse>> handler) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage(), correlationId);
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
                    return PhrRouteSupport.errorResponse(400, "INVALID_" + resourceType.toUpperCase().replace('-', '_'), ex.getMessage(), correlationId);
                }
                return requireAccess(finalContext, patientId, resourceType, action)
                    .then(decision -> {
                        if (decision.isAllowed()) {
                            return handler.apply(value);
                        }
                        return PhrRouteSupport.policyDenialResponse(403, finalContext.correlationId(), decision.getReasonCode());
                    });
            });
    }

    private Promise<HttpResponse> withPatientAccess(
            HttpRequest request,
            String resourceType,
            String action,
            java.util.function.Function<String, Promise<HttpResponse>> handler) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        String patientId;
        try {
            context = PhrRouteSupport.requireContext(request);
            patientId = PhrRouteSupport.requiredQuery(request, "patientId");
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "INVALID_PATIENT_SCOPE", ex.getMessage(), correlationId);
        }
        return requireAccess(context, patientId, resourceType, action)
            .then(decision -> {
                if (decision.isAllowed()) {
                    return handler.apply(patientId);
                }
                return PhrRouteSupport.policyDenialResponse(403, context.correlationId(), "POLICY_DENIED");
            });
    }

    private Promise<PhrPolicyEvaluator.PolicyDecision> requireAccess(
            PhrRouteSupport.PhrRequestContext context,
            String patientId,
            String resourceType,
            String action) {
        return policyEvaluator.canAccessPhiResourceAsync(
                context,
                patientId,
                resourceType,
                action,
                context.tenantId(),
                context.facilityId());
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

    private static AppointmentService.AppointmentRequest parseAppointmentRequest(JsonNode node) {
        String patientId = requireTextField(node, "patientId");
        String providerId = requireTextField(node, "providerId");
        String slotId = firstNonBlank(textField(node, "slotId"), textField(node, "slot"));
        if (slotId == null) {
            throw new IllegalArgumentException("slotId is required");
        }
        Instant scheduledTime;
        try {
            scheduledTime = Instant.parse(requireTextField(node, "scheduledTime"));
        } catch (Exception ex) {
            throw new IllegalArgumentException("scheduledTime must be a valid ISO-8601 instant");
        }
        int durationMinutes = node.path("durationMinutes").asInt(30);
        String appointmentType = firstNonBlank(textField(node, "appointmentType"), textField(node, "type"), "IN_PERSON");
        String reason = firstNonBlank(textField(node, "reason"), textField(node, "notes"), appointmentType);
        return new AppointmentService.AppointmentRequest(
            patientId,
            providerId,
            slotId,
            scheduledTime,
            durationMinutes,
            reason,
            appointmentType
        );
    }

    private static Map<String, Object> appointmentDto(AppointmentService.Appointment appointment) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", appointment.getId());
        dto.put("appointmentId", appointment.getId());
        dto.put("patientId", appointment.getPatientId());
        dto.put("provider", appointment.getProviderId());
        dto.put("providerId", appointment.getProviderId());
        dto.put("specialty", firstNonBlank(appointment.getAppointmentType(), appointment.getReason()));
        dto.put("appointmentType", appointment.getAppointmentType());
        dto.put("startsAt", appointment.getScheduledTime() != null ? appointment.getScheduledTime().toString() : "");
        dto.put("scheduledTime", appointment.getScheduledTime() != null ? appointment.getScheduledTime().toString() : "");
        dto.put("durationMinutes", appointment.getDurationMinutes());
        dto.put("location", "provider-calendar");
        dto.put("status", appointmentStatus(appointment.getStatus()));
        dto.put("slotId", appointment.getSlotId());
        dto.put("reason", appointment.getReason());
        dto.put("createdAt", appointment.getCreatedAt() != null ? appointment.getCreatedAt().toString() : "");
        dto.put("updatedAt", appointment.getUpdatedAt() != null ? appointment.getUpdatedAt().toString() : "");
        dto.put("reminderSent", "SCHEDULED".equals(appointment.getStatus()));
        dto.put("version", appointment.getVersion());
        return dto;
    }

    private static Map<String, Object> schedulingRequestDto(AppointmentService.SchedulingRequest request) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", request.id());
        dto.put("patientId", request.patientId());
        dto.put("status", "requested");
        dto.put("specialty", request.specialty());
        dto.put("preferredDate", request.preferredDate());
        dto.put("createdAt", request.createdAt() != null ? request.createdAt().toString() : "");
        if (request.notes() != null) {
            dto.put("notes", request.notes());
        }
        if (request.idempotencyKey() != null) {
            dto.put("idempotencyKey", request.idempotencyKey());
        }
        return dto;
    }

    private static Map<String, Object> timeSlotDto(AppointmentService.TimeSlot slot) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", slot.getId());
        dto.put("slotId", slot.getId());
        dto.put("providerId", slot.getProviderId());
        dto.put("date", slot.getDate());
        dto.put("startTime", slot.getStartTime() != null ? slot.getStartTime().toString() : "");
        dto.put("endTime", slot.getEndTime() != null ? slot.getEndTime().toString() : "");
        dto.put("status", slot.getStatus() != null ? slot.getStatus().toLowerCase() : "unknown");
        return dto;
    }

    private static String appointmentStatus(String status) {
        if ("SCHEDULED".equals(status)) {
            return "confirmed";
        }
        if ("CANCELLED".equals(status)) {
            return "cancelled";
        }
        if ("COMPLETED".equals(status)) {
            return "completed";
        }
        if (status == null || status.isBlank()) {
            return "requested";
        }
        return status.toLowerCase();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
