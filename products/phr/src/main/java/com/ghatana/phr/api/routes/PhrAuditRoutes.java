package com.ghatana.phr.api.routes;

import com.ghatana.kernel.observability.AuditTrailService;
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
 * Clinicians and admins may query across the full tenant.
 *
 * @doc.type class
 * @doc.purpose ActiveJ route adapter for querying the PHR audit event trail
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 */
public final class PhrAuditRoutes {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 200;
    private static final Set<String> ALLOWED_FILTERS = Set.of("all", "access", "consent", "emergency");

    private final Eventloop eventloop;
    private final AuditTrailService auditTrailService;

    /**
     * Creates audit routes.
     *
     * @param eventloop         the ActiveJ event loop; must not be null
     * @param auditTrailService the audit trail service; must not be null
     */
    public PhrAuditRoutes(Eventloop eventloop, AuditTrailService auditTrailService) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
        this.auditTrailService = Objects.requireNonNull(auditTrailService, "auditTrailService must not be null");
    }

    /**
     * Returns the routing servlet for audit endpoints.
     *
     * @return routing servlet; never null
     */
    public AsyncServlet getServlet() {
        return RoutingServlet.builder(eventloop)
            .with(HttpMethod.GET, "/events", this::handleQueryEvents)
            .build();
    }

    private Promise<HttpResponse> handleQueryEvents(HttpRequest request) {
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }

        String patientIdParam = request.getQueryParameter("patientId");
        String filterParam = request.getQueryParameter("filter");
        String pageParam = request.getQueryParameter("page");
        String pageSizeParam = request.getQueryParameter("pageSize");

        // Patient-scoped enforcement: non-privileged principals may only query their own data.
        String effectiveEntityId;
        if (PhrRouteSupport.hasClinicalRole(context)) {
            effectiveEntityId = patientIdParam; // may be null → full-tenant query
        } else {
            effectiveEntityId = context.principalId(); // always scoped to self
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
            return PhrRouteSupport.jsonResponse(200, responseBody);
        } catch (Exception ex) {
            return PhrRouteSupport.errorResponse(500, "AUDIT_QUERY_FAILED", "Failed to query audit events");
        }
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
            // Collect remaining data fields as details strings
            Map<String, String> details = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                if (!Set.of("resourceType", "resourceId", "success").contains(entry.getKey()) && entry.getValue() != null) {
                    details.put(entry.getKey(), String.valueOf(entry.getValue()));
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
            return null; // no event type filter → all types
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
