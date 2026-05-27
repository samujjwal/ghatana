package com.ghatana.phr.api.routes;

import com.ghatana.phr.application.patient.PatientOperationContext;
import com.ghatana.phr.application.record.RecordService;
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
 * Clinical timeline API for the PHR product.
 *
 * <p>Returns a chronological list of health events for a patient.
 * Patients may access their own timeline. Clinical roles may access any patient's timeline.
 *
 * @doc.type class
 * @doc.purpose ActiveJ route adapter serving the patient health timeline
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 */
public final class PhrTimelineRoutes {

    private final Eventloop eventloop;
    private final RecordService recordService;

    public PhrTimelineRoutes(Eventloop eventloop, RecordService recordService) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
        this.recordService = Objects.requireNonNull(recordService, "recordService must not be null");
    }

    /**
     * Returns the routing servlet for timeline endpoints.
     *
     * @return routing servlet; never null
     */
    public AsyncServlet getServlet() {
        return RoutingServlet.builder(eventloop)
            .with(HttpMethod.GET, "/:patientId", this::handleGetTimeline)
            .with(HttpMethod.GET, "/:patientId/category/:category", this::handleGetTimelineByCategory)
            .build();
    }

    private Promise<HttpResponse> handleGetTimeline(HttpRequest request) {
        PhrRouteSupport.PhrRequestContext context;
        String patientId;
        try {
            context = PhrRouteSupport.requireContext(request);
            patientId = request.getPathParameter("patientId");
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }

        if (!PhrRouteSupport.canAccessPatientRecordForRole(context, patientId)) {
            return PhrRouteSupport.errorResponse(403, "TIMELINE_ACCESS_DENIED",
                "Access denied to timeline for patient " + patientId,
                context.correlationId());
        }

        PatientOperationContext opCtx = new PatientOperationContext(
            context.tenantId(),
            "default",
            context.principalId(),
            patientId,
            context.correlationId()
        );

        return recordService.getRecordTimeline(opCtx, patientId)
            .then(timeline -> {
                List<Map<String, Object>> items = timeline.entries().stream()
                    .map(entry -> Map.of(
                        "id", entry.entryId(),
                        "occurredAt", entry.timestamp(),
                        "eventType", entry.category(),
                        "title", entry.type(),
                        "description", entry.description(),
                        "details", entry.details()
                    ))
                    .toList();
                
                Map<String, Object> response = new java.util.LinkedHashMap<>();
                response.put("patientId", patientId);
                response.put("items", items);
                response.put("count", items.size());
                response.put("generatedAt", timeline.generatedAt());
                
                return PhrRouteSupport.jsonResponse(200, response, context.correlationId());
            });
    }

    private Promise<HttpResponse> handleGetTimelineByCategory(HttpRequest request) {
        PhrRouteSupport.PhrRequestContext context;
        String patientId;
        String category;
        try {
            context = PhrRouteSupport.requireContext(request);
            patientId = request.getPathParameter("patientId");
            category = request.getPathParameter("category");
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }

        if (!PhrRouteSupport.canAccessPatientRecordForRole(context, patientId)) {
            return PhrRouteSupport.errorResponse(403, "TIMELINE_ACCESS_DENIED",
                "Access denied to timeline for patient " + patientId,
                context.correlationId());
        }

        PatientOperationContext opCtx = new PatientOperationContext(
            context.tenantId(),
            "default",
            context.principalId(),
            patientId,
            context.correlationId()
        );

        return recordService.getTimelineByCategory(opCtx, patientId, category)
            .then(entries -> {
                List<Map<String, Object>> items = entries.stream()
                    .map(entry -> Map.of(
                        "id", entry.entryId(),
                        "occurredAt", entry.timestamp(),
                        "eventType", entry.category(),
                        "title", entry.type(),
                        "description", entry.description(),
                        "details", entry.details()
                    ))
                    .toList();
                
                Map<String, Object> response = new java.util.LinkedHashMap<>();
                response.put("patientId", patientId);
                response.put("category", category);
                response.put("items", items);
                response.put("count", items.size());
                
                return PhrRouteSupport.jsonResponse(200, response, context.correlationId());
            });
    }
}
