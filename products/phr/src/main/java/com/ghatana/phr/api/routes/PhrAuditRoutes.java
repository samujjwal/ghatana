package com.ghatana.phr.api.routes;

import com.ghatana.kernel.observability.AuditTrailService;
import com.ghatana.phr.kernel.policy.PhrLogRedactor;
import com.ghatana.phr.security.PhrPolicyEvaluator;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.RoutingServlet;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Audit trail API routes for the PHR product.
 *
 * <p>Exposes the immutable audit trail to authorised principals. Patients can
 * only query events whose {@code entityId} matches their own principal ID.
 * Clinicians may query explicitly patient-scoped events, and admins may query
 * across the full tenant.
 *
 * @doc.type class
 * @doc.purpose ActiveJ route adapter for querying the PHR audit event trail
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 */
public final class PhrAuditRoutes {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 200;
    private static final int DETAIL_LOOKUP_LIMIT = 1000;
    private static final int EXPORT_LIMIT = 1000;
    private static final Set<String> ALLOWED_FILTERS = Set.of("all", "access", "consent", "emergency");

    private final Eventloop eventloop;
    private final AuditTrailService auditTrailService;
    private final PhrPolicyEvaluator policyEvaluator;

    /**
     * Creates audit routes.
     *
     * @param eventloop         the ActiveJ event loop; must not be null
     * @param auditTrailService the audit trail service; must not be null
     */
    public PhrAuditRoutes(Eventloop eventloop, AuditTrailService auditTrailService, PhrPolicyEvaluator policyEvaluator) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
        this.auditTrailService = Objects.requireNonNull(auditTrailService, "auditTrailService must not be null");
        this.policyEvaluator = Objects.requireNonNull(policyEvaluator, "policyEvaluator must not be null");
    }

    /**
     * Returns the routing servlet for audit endpoints.
     *
     * @return routing servlet; never null
     */
    public AsyncServlet getServlet() {
        return RoutingServlet.builder(eventloop)
            .with(HttpMethod.GET, "/events", this::handleQueryEvents)
            .with(HttpMethod.GET, "/events/export", this::handleExportEvents)
            .with(HttpMethod.GET, "/events/:eventId", this::handleGetEventDetail)
            .build();
    }

    private Promise<HttpResponse> handleQueryEvents(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage(), correlationId);
        }

        String patientIdParam = request.getQueryParameter("patientId");
        String filterParam = request.getQueryParameter("filter");
        String pageParam = request.getQueryParameter("page");
        String pageSizeParam = request.getQueryParameter("pageSize");

        // For admin PHI access, require purpose and justification
        if ("admin".equals(context.role()) && patientIdParam != null && !patientIdParam.isBlank()) {
            String purpose = request.getQueryParameter("purpose");
            String justification = request.getQueryParameter("justification");
            if (purpose == null || purpose.isBlank() || justification == null || justification.isBlank()) {
                return PhrRouteSupport.errorResponse(400, "MISSING_ADMIN_JUSTIFICATION",
                    "Admin PHI access requires purpose and justification parameters", correlationId);
            }
            return policyEvaluator.canAccessPhiWithAdminJustification(
                    context,
                    patientIdParam,
                    "audit-trail",
                    "READ",
                    purpose,
                    justification)
                .then(adminDecision -> {
                    if (!adminDecision.isAllowed()) {
                        return PhrRouteSupport.policyDenialResponse(403, context.correlationId(), adminDecision.getReasonCode());
                    }
                    return continueQueryEvents(context, patientIdParam, filterParam, pageParam, pageSizeParam, correlationId);
                });
        }

        return policyEvaluator.canQueryAuditEventsAsync(context, patientIdParam)
            .then(entityScopeDecision -> {
                if (!entityScopeDecision.isAllowed()) {
                    return PhrRouteSupport.policyDenialResponse(403, context.correlationId(), entityScopeDecision.getReasonCode());
                }
                return continueQueryEvents(context, patientIdParam, filterParam, pageParam, pageSizeParam, correlationId);
            });
    }

    private Promise<HttpResponse> continueQueryEvents(
            PhrRouteSupport.PhrRequestContext context,
            String patientIdParam,
            String filterParam,
            String pageParam,
            String pageSizeParam,
            String correlationId) {
        String effectiveEntityId;
        if ("admin".equals(context.role()) || "clinician".equals(context.role())) {
            effectiveEntityId = patientIdParam;
        } else {
            effectiveEntityId = context.principalId();
        }
        String eventTypeFilter = resolveEventTypeFilter(filterParam);
        int page = parsePage(pageParam);
        int pageSize = parsePageSize(pageSizeParam);

        AuditTrailService.AuditQuery.Builder queryBuilder = AuditTrailService.AuditQuery.builder()
            .tenantId(context.tenantId())
            .limit(pageSize * page); // over-fetch to support offset-based paging

        if (effectiveEntityId != null && !effectiveEntityId.isBlank()) {
            queryBuilder.entityId(effectiveEntityId);
        }
        if (eventTypeFilter != null) {
            queryBuilder.eventType(eventTypeFilter);
        }

        AuditTrailService.AuditQuery query = queryBuilder.build();

        try {
            List<AuditTrailService.AuditTrailEvent> allMatching = auditTrailService.queryAuditEvents(query);
            int total = allMatching.size();
            int fromIndex = Math.min((page - 1) * pageSize, total);
            int toIndex = Math.min(fromIndex + pageSize, total);
            List<AuditTrailService.AuditTrailEvent> pageEvents = allMatching.subList(fromIndex, toIndex);

            List<Map<String, Object>> eventDtos = pageEvents.stream()
                .map(this::toEventDto)
                .toList();

            Map<String, Object> responseBody = new LinkedHashMap<>();
            responseBody.put("events", eventDtos);
            responseBody.put("total", total);
            responseBody.put("page", page);
            responseBody.put("pageSize", pageSize);
            return PhrRouteSupport.jsonResponse(200, responseBody, correlationId);
        } catch (Exception ex) {
            return PhrRouteSupport.errorResponse(500, "AUDIT_QUERY_FAILED", "Failed to query audit events", correlationId);
        }
    }

    private Promise<HttpResponse> handleGetEventDetail(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage(), correlationId);
        }

        String eventId = request.getPathParameter("eventId");
        if (eventId == null || eventId.isBlank()) {
            return PhrRouteSupport.errorResponse(400, "INVALID_EVENT_ID", "Event ID is required", correlationId);
        }

        try {
            AuditTrailService.AuditQuery query = AuditTrailService.AuditQuery.builder()
                .tenantId(context.tenantId())
                .limit(DETAIL_LOOKUP_LIMIT)
                .build();

            List<AuditTrailService.AuditTrailEvent> events = auditTrailService.queryAuditEvents(query);
            AuditTrailService.AuditTrailEvent event = events.stream()
                .filter(e -> eventId.equals(e.getEventId()))
                .findFirst()
                .orElse(null);

            if (event == null) {
                return PhrRouteSupport.errorResponse(404, "EVENT_NOT_FOUND", "Audit event not found", correlationId);
            }

            return policyEvaluator.canViewAuditEventAsync(context, event.getUserId(), event.getEntityId())
                .then(detailDecision -> {
                    if (!detailDecision.isAllowed()) {
                        return PhrRouteSupport.policyDenialResponse(403, context.correlationId(), detailDecision.getReasonCode());
                    }
                    return PhrRouteSupport.jsonResponse(200, toEventDto(event), correlationId);
                });
        } catch (Exception ex) {
            return PhrRouteSupport.errorResponse(500, "AUDIT_DETAIL_FAILED", "Failed to fetch event detail", correlationId);
        }
    }

    private Promise<HttpResponse> handleExportEvents(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage(), correlationId);
        }

        PhrPolicyEvaluator.PolicyDecision exportDecision = policyEvaluator.canViewAuditTrail(context);
        if (!exportDecision.isAllowed()) {
            return PhrRouteSupport.policyDenialResponse(403, context.correlationId());
        }

        String patientIdParam = request.getQueryParameter("patientId");
        String filterParam = request.getQueryParameter("filter");
        String formatParam = request.getQueryParameter("format");
        String format = (formatParam != null && !formatParam.isBlank()) ? formatParam : "json";

        if (!"json".equalsIgnoreCase(format) && !"csv".equalsIgnoreCase(format)) {
            return PhrRouteSupport.errorResponse(400, "INVALID_FORMAT", "Format must be 'json' or 'csv'", correlationId);
        }

        String effectiveEntityId = patientIdParam;
        String eventTypeFilter = resolveEventTypeFilter(filterParam);

        AuditTrailService.AuditQuery.Builder queryBuilder = AuditTrailService.AuditQuery.builder()
            .tenantId(context.tenantId())
            .limit(EXPORT_LIMIT);

        if (effectiveEntityId != null && !effectiveEntityId.isBlank()) {
            queryBuilder.entityId(effectiveEntityId);
        }
        if (eventTypeFilter != null) {
            queryBuilder.eventType(eventTypeFilter);
        }

        try {
            List<AuditTrailService.AuditTrailEvent> events = auditTrailService.queryAuditEvents(queryBuilder.build());

            if ("csv".equalsIgnoreCase(format)) {
                String csv = convertToCsv(events);
                return PhrRouteSupport.textResponse(200, csv, "text/csv", correlationId);
            } else {
                List<Map<String, Object>> eventDtos = events.stream()
                    .map(this::toEventDto)
                    .toList();
                return PhrRouteSupport.jsonResponse(200, Map.of(
                    "events", eventDtos,
                    "total", eventDtos.size(),
                    "exportedAt", Instant.now().toString()
                ), correlationId);
            }
        } catch (Exception ex) {
            return PhrRouteSupport.errorResponse(500, "AUDIT_EXPORT_FAILED", "Failed to export audit events", correlationId);
        }
    }

    private String convertToCsv(List<AuditTrailService.AuditTrailEvent> events) {
        StringBuilder csv = new StringBuilder();
        csv.append("eventId,tenantId,eventType,principal,timestamp,success,resourceType,resourceId\n");

        for (AuditTrailService.AuditTrailEvent event : events) {
            csv.append(event.getEventId()).append(",");
            csv.append(event.getTenantId()).append(",");
            csv.append(csvField(event.getEventType() != null ? event.getEventType() : event.getAction())).append(",");
            csv.append(csvField(event.getUserId())).append(",");
            csv.append(Instant.ofEpochMilli(event.getTimestamp()).toString()).append(",");
            csv.append(isSuccessEvent(event)).append(",");

            Map<String, Object> data = event.getData();
            csv.append(csvField(data != null && data.containsKey("resourceType") ? data.get("resourceType") : "")).append(",");
            csv.append(csvField(data != null && data.containsKey("resourceId") ? data.get("resourceId") :
                  (event.getEntityId() != null ? event.getEntityId() : ""))).append("\n");
        }

        return csv.toString();
    }

    private String csvField(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        if (!text.contains(",") && !text.contains("\"") && !text.contains("\n") && !text.contains("\r")) {
            return text;
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private Map<String, Object> toEventDto(AuditTrailService.AuditTrailEvent event) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", event.getEventId());
        dto.put("tenantId", event.getTenantId());
        dto.put("eventType", event.getEventType() != null ? event.getEventType() : event.getAction());
        dto.put("principal", event.getUserId());
        dto.put("timestamp", Instant.ofEpochMilli(event.getTimestamp()).toString());
        dto.put("success", isSuccessEvent(event));

        Map<String, Object> data = event.getData();
        if (data != null) {
            if (data.containsKey("resourceType")) {
                dto.put("resourceType", data.get("resourceType"));
            }
            if (data.containsKey("resourceId")) {
                dto.put("resourceId", data.get("resourceId"));
            } else if (event.getEntityId() != null) {
                dto.put("resourceId", event.getEntityId());
            }
            // Collect remaining data fields as details strings with PHI redaction
            Map<String, String> details = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                if (!Set.of("resourceType", "resourceId", "success").contains(entry.getKey()) && entry.getValue() != null) {
                    String value = String.valueOf(entry.getValue());
                    // Redact sensitive fields based on canonical classification
                    String redactedValue = PhrLogRedactor.redactForAuditExport(value);
                    details.put(entry.getKey(), redactedValue);
                }
            }
            if (!details.isEmpty()) {
                dto.put("details", details);
            }
        } else if (event.getEntityId() != null) {
            dto.put("resourceId", event.getEntityId());
        }

        return dto;
    }

    private boolean isSuccessEvent(AuditTrailService.AuditTrailEvent event) {
        Map<String, Object> data = event.getData();
        if (data != null && data.containsKey("success")) {
            Object value = data.get("success");
            if (value instanceof Boolean boolValue) {
                return boolValue;
            }
            if (value instanceof String strValue) {
                return "true".equalsIgnoreCase(strValue);
            }
        }
        // Default: treat recorded events as successful unless explicitly marked failed
        return true;
    }

    private String resolveEventTypeFilter(String filter) {
        if (filter == null || filter.isBlank() || "all".equalsIgnoreCase(filter)) {
            return null;
        }
        String normalised = filter.strip().toLowerCase();
        if (!ALLOWED_FILTERS.contains(normalised)) {
            return null;
        }
        return normalised; // e.g. "access", "consent", "emergency"
    }

    private int parsePage(String pageParam) {
        if (pageParam == null) {
            return 1;
        }
        try {
            int page = Integer.parseInt(pageParam.strip());
            return Math.max(1, page);
        } catch (NumberFormatException ex) {
            return 1;
        }
    }

    private int parsePageSize(String pageSizeParam) {
        if (pageSizeParam == null) {
            return DEFAULT_PAGE_SIZE;
        }
        try {
            int size = Integer.parseInt(pageSizeParam.strip());
            return Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        } catch (NumberFormatException ex) {
            return DEFAULT_PAGE_SIZE;
        }
    }
}
