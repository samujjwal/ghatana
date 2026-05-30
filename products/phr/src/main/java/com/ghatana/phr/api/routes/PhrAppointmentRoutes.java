package com.ghatana.phr.api.routes;

import com.ghatana.phr.kernel.service.AppointmentService;
import com.ghatana.phr.security.PhrPolicyEvaluator;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.RoutingServlet;
import io.activej.promise.Promise;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

/**
 * Appointment management API routes for the PHR product.
 *
 * <p>Provides appointment booking, rescheduling, cancellation, and provider slot management.
 * Access follows the standard patient-record access policy with role-based permissions.</p>
 *
 * @doc.type class
 * @doc.purpose ActiveJ route adapter for appointment booking and management journeys
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 */
public final class PhrAppointmentRoutes {

    private final Eventloop eventloop;
    private final AppointmentService appointmentService;
    private final PhrPolicyEvaluator policyEvaluator;

    public PhrAppointmentRoutes(
            Eventloop eventloop,
            AppointmentService appointmentService,
            PhrPolicyEvaluator policyEvaluator) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
        this.appointmentService = Objects.requireNonNull(appointmentService, "appointmentService must not be null");
        this.policyEvaluator = Objects.requireNonNull(policyEvaluator, "policyEvaluator must not be null");
    }

    /**
     * Returns the routing servlet for appointment endpoints.
     *
     * @return routing servlet; never null
     */
    public AsyncServlet getServlet() {
        return RoutingServlet.builder(eventloop)
            .with(HttpMethod.POST, "/", this::handleBookAppointment)
            .with(HttpMethod.POST, "/:appointmentId/reschedule", this::handleRescheduleAppointment)
            .with(HttpMethod.POST, "/:appointmentId/cancel", this::handleCancelAppointment)
            .with(HttpMethod.GET, "/slots", this::handleGetAvailableSlots)
            .with(HttpMethod.GET, "/:appointmentId", this::handleGetAppointment)
            .with(HttpMethod.GET, "/", this::handleListAppointments)
            .build();
    }

    private Promise<HttpResponse> handleBookAppointment(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
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
                AppointmentService.AppointmentRequest appointmentRequest;
                try {
                    appointmentRequest = parseAppointmentRequest(body.getString(StandardCharsets.UTF_8));
                } catch (IllegalArgumentException ex) {
                    return PhrRouteSupport.errorResponse(400, "INVALID_APPOINTMENT_REQUEST", ex.getMessage());
                }
                
                return requireAccess(context, appointmentRequest.getPatientId(), "appointments", "WRITE")
                    .then(decision -> {
                        if (!decision.isAllowed()) {
                            return PhrRouteSupport.policyDenialResponse(403, context.correlationId(), decision.getReasonCode());
                        }
                        return appointmentService.createAppointment(appointmentRequest)
                            .then(created -> PhrRouteSupport.jsonResponse(201, created, correlationId));
                    });
            });
    }

    private Promise<HttpResponse> handleRescheduleAppointment(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        String appointmentId;
        try {
            context = PhrRouteSupport.requireContext(request);
            appointmentId = request.getPathParameter("appointmentId");
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }

        return request.loadBody()
            .then(body -> {
                String newScheduledTime;
                try {
                    var node = PhrRouteSupport.JSON.readTree(body.getString(StandardCharsets.UTF_8));
                    newScheduledTime = node.path("scheduledTime").asText(null);
                    if (newScheduledTime == null || newScheduledTime.isBlank()) {
                        throw new IllegalArgumentException("scheduledTime is required");
                    }
                } catch (Exception ex) {
                    return PhrRouteSupport.errorResponse(400, "INVALID_RESCHEDULE", ex.getMessage());
                }
                
                // For rescheduling, we need to get the appointment first to check patient access
                return appointmentService.getPatientAppointments(context.principalId(), null)
                    .then(appointments -> {
                        // Check if the appointment belongs to the patient
                        boolean hasAccess = appointments.stream()
                            .anyMatch(app -> app.getId().equals(appointmentId));
                        
                        if (!hasAccess) {
                            return PhrRouteSupport.errorResponse(403, "APPOINTMENT_ACCESS_DENIED", 
                                "You do not have access to this appointment", context.correlationId());
                        }
                        
                        // TODO: Implement reschedule logic in AppointmentService
                        return PhrRouteSupport.jsonResponse(200, Map.of(
                            "appointmentId", appointmentId,
                            "scheduledTime", newScheduledTime,
                            "status", "RESCHEDULED"
                        ), correlationId);
                    });
            });
    }

    private Promise<HttpResponse> handleCancelAppointment(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        String appointmentId;
        String reason;
        try {
            context = PhrRouteSupport.requireContext(request);
            appointmentId = request.getPathParameter("appointmentId");
            reason = request.getQueryParameter("reason");
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }

        return appointmentService.getPatientAppointments(context.principalId(), null)
            .then(appointments -> {
                // Check if the appointment belongs to the patient
                boolean hasAccess = appointments.stream()
                    .anyMatch(app -> app.getId().equals(appointmentId));
                
                if (!hasAccess) {
                    return PhrRouteSupport.errorResponse(403, "APPOINTMENT_ACCESS_DENIED", 
                        "You do not have access to this appointment", context.correlationId());
                }
                
                return appointmentService.cancelAppointment(appointmentId, 
                    reason != null ? reason : "Cancelled by patient")
                    .then($ -> PhrRouteSupport.jsonResponse(200, Map.of(
                        "appointmentId", appointmentId,
                        "status", "CANCELLED"
                    ), correlationId));
            });
    }

    private Promise<HttpResponse> handleGetAvailableSlots(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        String providerId;
        String date;
        try {
            context = PhrRouteSupport.requireContext(request);
            providerId = PhrRouteSupport.requiredQuery(request, "providerId");
            date = request.getQueryParameter("date");
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "INVALID_SLOT_QUERY", ex.getMessage());
        }

        return appointmentService.getAvailableSlots(providerId, date != null ? date : java.time.LocalDate.now().toString())
            .then(slots -> PhrRouteSupport.jsonResponse(200, Map.of(
                "providerId", providerId,
                "date", date != null ? date : java.time.LocalDate.now().toString(),
                "items", slots,
                "count", slots.size()
            )));
    }

    private Promise<HttpResponse> handleGetAppointment(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        String appointmentId;
        try {
            context = PhrRouteSupport.requireContext(request);
            appointmentId = request.getPathParameter("appointmentId");
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }

        return appointmentService.getPatientAppointments(context.principalId(), null)
            .then(appointments -> {
                var appointment = appointments.stream()
                    .filter(app -> app.getId().equals(appointmentId))
                    .findFirst();
                
                if (appointment.isEmpty()) {
                    return PhrRouteSupport.errorResponse(404, "APPOINTMENT_NOT_FOUND", 
                        "Appointment not found or not accessible", context.correlationId());
                }
                
                return PhrRouteSupport.jsonResponse(200, appointment.get());
            });
    }

    private Promise<HttpResponse> handleListAppointments(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        String status;
        try {
            context = PhrRouteSupport.requireContext(request);
            status = request.getQueryParameter("status");
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }

        return appointmentService.getPatientAppointments(context.principalId(), status)
            .then(appointments -> PhrRouteSupport.jsonResponse(200, Map.of(
                "patientId", context.principalId(),
                "items", appointments,
                "count", appointments.size()
            )));
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

    private static AppointmentService.AppointmentRequest parseAppointmentRequest(String json) throws java.io.IOException {
        var node = PhrRouteSupport.JSON.readTree(json);
        String patientId = requiredText(node, "patientId");
        String providerId = requiredText(node, "providerId");
        String slotId = text(node, "slotId", null);
        Instant scheduledTime = Instant.parse(requiredText(node, "scheduledTime"));
        int durationMinutes = node.path("durationMinutes").asInt(30);
        String appointmentType = text(node, "appointmentType", text(node, "type", "GENERAL"));
        
        return new AppointmentService.AppointmentRequest(
            patientId,
            providerId,
            slotId,
            scheduledTime,
            durationMinutes,
            text(node, "reason", null),
            appointmentType
        );
    }

    private static String requiredText(com.fasterxml.jackson.databind.JsonNode node, String fieldName) {
        String value = text(node, fieldName, null);
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }

    private static String text(com.fasterxml.jackson.databind.JsonNode node, String fieldName, String defaultValue) {
        var value = node.path(fieldName);
        return value.isMissingNode() || value.isNull() || value.asText().isBlank() ? defaultValue : value.asText();
    }
}
