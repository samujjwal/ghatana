package com.ghatana.phr.api.routes;

import com.fasterxml.jackson.databind.JsonNode;
import com.ghatana.phr.kernel.service.EmergencyAccessLogService;
import com.ghatana.phr.kernel.service.TreatmentRelationshipService;
import com.ghatana.phr.security.PhrPolicyEvaluator;
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
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Emergency break-glass API routes for the PHR product.
 *
 * @doc.type class
 * @doc.purpose ActiveJ route adapter for emergency access request, review, expiry, and audit journeys
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 */
public final class PhrEmergencyRoutes {

    private final Eventloop eventloop;
    private final EmergencyAccessLogService emergencyAccessLogService;
    private final TreatmentRelationshipService treatmentRelationshipService;
    private final PhrPolicyEvaluator policyEvaluator;

    public PhrEmergencyRoutes(
            Eventloop eventloop,
            EmergencyAccessLogService emergencyAccessLogService,
            TreatmentRelationshipService treatmentRelationshipService,
            PhrPolicyEvaluator policyEvaluator) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
        this.emergencyAccessLogService = Objects.requireNonNull(
            emergencyAccessLogService,
            "emergencyAccessLogService must not be null"
        );
        this.treatmentRelationshipService = Objects.requireNonNull(
            treatmentRelationshipService,
            "treatmentRelationshipService must not be null"
        );
        this.policyEvaluator = Objects.requireNonNull(policyEvaluator, "policyEvaluator must not be null");
    }

    /**
     * Returns the routing servlet for emergency access endpoints.
     *
     * @return routing servlet; never null
     */
    public AsyncServlet getServlet() {
        return RoutingServlet.builder(eventloop)
            .with(HttpMethod.POST, "/access", this::handleLogAccess)
            .with(HttpMethod.GET, "/events/:eventId", this::handleGetEvent)
            .with(HttpMethod.GET, "/patients/:patientId", this::handlePatientLog)
            .with(HttpMethod.GET, "/reviews/pending", this::handlePendingReviews)
            .with(HttpMethod.GET, "/reviews/overdue", this::handleOverdueReviews)
            .with(HttpMethod.POST, "/reviews/:eventId", this::handleReview)
            .build();
    }

    private Promise<HttpResponse> handleLogAccess(HttpRequest request) {
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
                EmergencyAccessLogService.EmergencyAccessEvent event;
                try {
                    event = parseAccessEvent(body.getString(StandardCharsets.UTF_8), context);
                } catch (IllegalArgumentException ex) {
                    return PhrRouteSupport.errorResponse(400, "INVALID_EMERGENCY_ACCESS", ex.getMessage());
                }
                
                // Policy gate: validate justification is provided and non-empty
                if (event.justification() == null || event.justification().isBlank()) {
                    return PhrRouteSupport.errorResponse(400, "INVALID_JUSTIFICATION",
                        "Emergency access requires a documented justification");
                }
                
                // Policy gate: justification must be at least 20 characters to prevent trivial entries
                if (event.justification().length() < 20) {
                    return PhrRouteSupport.errorResponse(400, "JUSTIFICATION_TOO_SHORT",
                        "Emergency access justification must be at least 20 characters");
                }
                
                // Policy gate: validate at least one resource is being accessed
                if (event.resourcesAccessed() == null || event.resourcesAccessed().isEmpty()) {
                    return PhrRouteSupport.errorResponse(400, "INVALID_RESOURCES",
                        "Emergency access must specify at least one resource being accessed");
                }
                
                return policyEvaluator.canAccessEmergency(context, event.patientId(), event.justification())
                    .then(decision -> {
                        if (!decision.isAllowed()) {
                            return PhrRouteSupport.errorResponse(403, decision.getReasonCode(), decision.getReasonMessage());
                        }
                        return hasPatientScope(context, event.patientId())
                            .then(hasScope -> {
                                if (!hasScope) {
                                    return PhrRouteSupport.errorResponse(403, "PATIENT_SCOPE_DENIED",
                                        "Emergency access requires treatment relationship or same facility assignment");
                                }
                                
                                // Policy gate: log emergency access attempt for audit trail
                                // This is done before the actual access to ensure auditability
                                return emergencyAccessLogService.logAccess(event)
                                    .then(stored -> {
                                        // Policy gate: trigger patient notification for emergency access
                                        // Patients must be notified when their PHI is accessed via break-glass
                                        return emergencyAccessLogService.notifyPatientOfEmergencyAccess(stored)
                                            .then(__ -> PhrRouteSupport.jsonResponse(201, stored, correlationId));
                                    });
                            });
                    });
            });
    }

    private Promise<HttpResponse> handleGetEvent(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }

        return emergencyAccessLogService.getEvent(request.getPathParameter("eventId"))
            .then(event -> {
                if (event.isEmpty()) {
                    return PhrRouteSupport.errorResponse(404, "EMERGENCY_EVENT_NOT_FOUND", "Emergency access event not found");
                }
                if (!canReadEvent(context, event.get())) {
                    return PhrRouteSupport.errorResponse(403, "EMERGENCY_EVENT_DENIED", "Emergency access event is not visible to this principal");
                }
                return PhrRouteSupport.jsonResponse(200, event.get());
            });
    }

    private Promise<HttpResponse> handlePatientLog(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        String patientId;
        try {
            context = PhrRouteSupport.requireContext(request);
            patientId = request.getPathParameter("patientId");
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }
        if (!context.principalId().equals(patientId)) {
            return policyEvaluator.canAccessEmergency(context, patientId, "view emergency log")
                .then(decision -> decision.isAllowed()
                    ? emergencyAccessLogService.getPatientEmergencyLog(patientId)
                        .then(events -> PhrRouteSupport.jsonResponse(200, Map.of(
                            "patientId", patientId,
                            "items", events,
                            "count", events.size()
                        )))
                    : PhrRouteSupport.policyDenialResponse(403, context.correlationId(), decision.getReasonCode()));
        }
        return emergencyAccessLogService.getPatientEmergencyLog(patientId)
            .then(events -> PhrRouteSupport.jsonResponse(200, Map.of(
                "patientId", patientId,
                "items", events,
                "count", events.size()
            )));
    }

    private Promise<HttpResponse> handlePendingReviews(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        int limit;
        try {
            context = PhrRouteSupport.requireContext(request);
            limit = PhrRouteSupport.intQuery(request, "limit", 100, 1000);
        } catch (RuntimeException ex) {
            return PhrRouteSupport.errorResponse(400, "INVALID_PENDING_REVIEW_QUERY", ex.getMessage());
        }
        if (!"admin".equals(context.role())) {
            return PhrRouteSupport.errorResponse(403, "REVIEWER_REQUIRED", "Only administrators can list emergency reviews");
        }
        return emergencyAccessLogService.getPendingReviews(limit)
            .then(events -> PhrRouteSupport.jsonResponse(200, Map.of("items", events, "count", events.size())));
    }

    private Promise<HttpResponse> handleOverdueReviews(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        int limit;
        try {
            context = PhrRouteSupport.requireContext(request);
            limit = PhrRouteSupport.intQuery(request, "limit", 100, 1000);
        } catch (RuntimeException ex) {
            return PhrRouteSupport.errorResponse(400, "INVALID_OVERDUE_REVIEW_QUERY", ex.getMessage());
        }
        if (!"admin".equals(context.role())) {
            return PhrRouteSupport.errorResponse(403, "REVIEWER_REQUIRED", "Only administrators can list overdue emergency reviews");
        }
        return emergencyAccessLogService.getOverdueReviews(limit)
            .then(events -> PhrRouteSupport.jsonResponse(200, Map.of("items", events, "count", events.size())));
    }

    private Promise<HttpResponse> handleReview(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        String idempotencyKey;
        try {
            context = PhrRouteSupport.requireContext(request);
            idempotencyKey = PhrRouteSupport.extractIdempotencyKey(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }

        // Policy gate: only administrators can review emergency access
        if (!"admin".equals(context.role())) {
            return PhrRouteSupport.errorResponse(403, "REVIEWER_REQUIRED", "Only administrators can review emergency access");
        }

        return request.loadBody()
            .then(body -> {
                ReviewPayload payload;
                try {
                    payload = parseReview(body.getString(StandardCharsets.UTF_8));
                } catch (IllegalArgumentException ex) {
                    return PhrRouteSupport.errorResponse(400, "INVALID_EMERGENCY_REVIEW", ex.getMessage());
                }
                
                // Policy gate: require notes for escalated reviews
                if (payload.status() == EmergencyAccessLogService.ReviewStatus.ESCALATED 
                    && (payload.notes() == null || payload.notes().isBlank())) {
                    return PhrRouteSupport.errorResponse(400, "REVIEW_NOTES_REQUIRED",
                        "Escalated emergency access reviews require documented notes");
                }
                
                return emergencyAccessLogService.markReviewed(
                        request.getPathParameter("eventId"),
                        context.principalId(),
                        payload.status(),
                        payload.notes())
                    .then(reviewed -> PhrRouteSupport.jsonResponse(200, reviewed, correlationId));
            });
    }

    private boolean canReadEvent(
            PhrRouteSupport.PhrRequestContext context,
            EmergencyAccessLogService.EmergencyAccessEvent event) {
        // Use policy evaluator for audit access decision (POL-001)
        PhrPolicyEvaluator.PolicyDecision decision = policyEvaluator.canViewAuditEvent(context, event.accessorId(), event.patientId());
        return decision.isAllowed();
    }

    /**
     * Policy gate: Check if accessor has patient scope for emergency access.
     * Requires either treatment relationship or same facility assignment.
     * Uses policy evaluator for PHI access decision (POL-001).
     * 
     * @param context the request context
     * @param patientId the target patient ID
     * @return Promise containing true if accessor has patient scope
     */
    private Promise<Boolean> hasPatientScope(PhrRouteSupport.PhrRequestContext context, String patientId) {
        // Use policy evaluator for PHI access decision (POL-001)
        return policyEvaluator.canAccessEmergency(context, patientId, "emergency-access-scope")
            .map(decision -> decision.isAllowed());
    }

    private static EmergencyAccessLogService.EmergencyAccessEvent parseAccessEvent(
            String json,
            PhrRouteSupport.PhrRequestContext context) {
        try {
            JsonNode node = PhrRouteSupport.JSON.readTree(json);
            String patientId = requiredText(node, "patientId");
            String accessorId = text(node, "accessorId", context.principalId());
            if (!context.principalId().equals(accessorId)) {
                throw new IllegalArgumentException("accessorId must match X-Principal-ID");
            }
            return new EmergencyAccessLogService.EmergencyAccessEvent(
                text(node, "id", null),
                patientId,
                accessorId,
                requiredText(node, "accessorRole"),
                requiredText(node, "justification"),
                stringSet(node.path("resourcesAccessed")),
                null,
                null,
                EmergencyAccessLogService.ReviewStatus.PENDING_REVIEW,
                null,
                null,
                null,
                null,
                null
            );
        } catch (Exception ex) {
            throw new IllegalArgumentException(ex.getMessage(), ex);
        }
    }

    private static ReviewPayload parseReview(String json) {
        try {
            JsonNode node = PhrRouteSupport.JSON.readTree(json);
            EmergencyAccessLogService.ReviewStatus status =
                EmergencyAccessLogService.ReviewStatus.valueOf(requiredText(node, "status"));
            if (status == EmergencyAccessLogService.ReviewStatus.PENDING_REVIEW) {
                throw new IllegalArgumentException("status cannot be PENDING_REVIEW");
            }
            return new ReviewPayload(status, text(node, "notes", null));
        } catch (Exception ex) {
            throw new IllegalArgumentException(ex.getMessage(), ex);
        }
    }

    private static String requiredText(JsonNode node, String fieldName) {
        String value = text(node, fieldName, null);
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }

    private static String text(JsonNode node, String fieldName, String defaultValue) {
        JsonNode value = node.path(fieldName);
        return value.isMissingNode() || value.isNull() || value.asText().isBlank() ? defaultValue : value.asText();
    }

    private static Set<String> stringSet(JsonNode node) {
        if (!node.isArray()) {
            return Set.of();
        }
        return StreamSupport.stream(node.spliterator(), false)
            .map(JsonNode::asText)
            .filter(value -> !value.isBlank())
            .collect(Collectors.toUnmodifiableSet());
    }

    private record ReviewPayload(EmergencyAccessLogService.ReviewStatus status, String notes) {}
}
