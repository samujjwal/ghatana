package com.ghatana.phr.api.routes;

import com.fasterxml.jackson.databind.JsonNode;
import com.ghatana.phr.kernel.service.EmergencyAccessLogService;
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

    public PhrEmergencyRoutes(Eventloop eventloop, EmergencyAccessLogService emergencyAccessLogService) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
        this.emergencyAccessLogService = Objects.requireNonNull(
            emergencyAccessLogService,
            "emergencyAccessLogService must not be null"
        );
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
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
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
                return emergencyAccessLogService.logAccess(event)
                    .then(stored -> PhrRouteSupport.jsonResponse(201, stored));
            });
    }

    private Promise<HttpResponse> handleGetEvent(HttpRequest request) {
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
        PhrRouteSupport.PhrRequestContext context;
        String patientId;
        try {
            context = PhrRouteSupport.requireContext(request);
            patientId = request.getPathParameter("patientId");
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }
        if (!context.principalId().equals(patientId) && !PhrRouteSupport.isPrivileged(context)) {
            return PhrRouteSupport.errorResponse(403, "EMERGENCY_LOG_DENIED", "Patient emergency log is not visible to this principal");
        }
        return emergencyAccessLogService.getPatientEmergencyLog(patientId)
            .then(events -> PhrRouteSupport.jsonResponse(200, Map.of(
                "patientId", patientId,
                "items", events,
                "count", events.size()
            )));
    }

    private Promise<HttpResponse> handlePendingReviews(HttpRequest request) {
        PhrRouteSupport.PhrRequestContext context;
        int limit;
        try {
            context = PhrRouteSupport.requireContext(request);
            limit = PhrRouteSupport.intQuery(request, "limit", 100, 1000);
        } catch (RuntimeException ex) {
            return PhrRouteSupport.errorResponse(400, "INVALID_PENDING_REVIEW_QUERY", ex.getMessage());
        }
        if (!PhrRouteSupport.isPrivileged(context)) {
            return PhrRouteSupport.errorResponse(403, "REVIEWER_REQUIRED", "Only administrators can list emergency reviews");
        }
        return emergencyAccessLogService.getPendingReviews(limit)
            .then(events -> PhrRouteSupport.jsonResponse(200, Map.of("items", events, "count", events.size())));
    }

    private Promise<HttpResponse> handleOverdueReviews(HttpRequest request) {
        PhrRouteSupport.PhrRequestContext context;
        int limit;
        try {
            context = PhrRouteSupport.requireContext(request);
            limit = PhrRouteSupport.intQuery(request, "limit", 100, 1000);
        } catch (RuntimeException ex) {
            return PhrRouteSupport.errorResponse(400, "INVALID_OVERDUE_REVIEW_QUERY", ex.getMessage());
        }
        if (!PhrRouteSupport.isPrivileged(context)) {
            return PhrRouteSupport.errorResponse(403, "REVIEWER_REQUIRED", "Only administrators can list overdue emergency reviews");
        }
        return emergencyAccessLogService.getOverdueReviews(limit)
            .then(events -> PhrRouteSupport.jsonResponse(200, Map.of("items", events, "count", events.size())));
    }

    private Promise<HttpResponse> handleReview(HttpRequest request) {
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }
        if (!PhrRouteSupport.isPrivileged(context)) {
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
                return emergencyAccessLogService.markReviewed(
                        request.getPathParameter("eventId"),
                        context.principalId(),
                        payload.status(),
                        payload.notes())
                    .then(reviewed -> PhrRouteSupport.jsonResponse(200, reviewed));
            });
    }

    private static boolean canReadEvent(
            PhrRouteSupport.PhrRequestContext context,
            EmergencyAccessLogService.EmergencyAccessEvent event) {
        return PhrRouteSupport.isPrivileged(context)
            || context.principalId().equals(event.patientId())
            || context.principalId().equals(event.accessorId());
    }

    private static EmergencyAccessLogService.EmergencyAccessEvent parseAccessEvent(
            String json,
            PhrRouteSupport.PhrRequestContext context) {
        try {
            JsonNode node = PhrRouteSupport.JSON.readTree(json);
            String patientId = requiredText(node, "patientId");
            String accessorId = text(node, "accessorId", context.principalId());
            if (!context.principalId().equals(accessorId) && !PhrRouteSupport.isPrivileged(context)) {
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
