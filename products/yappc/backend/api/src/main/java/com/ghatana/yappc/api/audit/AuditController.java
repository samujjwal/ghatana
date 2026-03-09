/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.audit;

import com.ghatana.platform.audit.AuditService;
import com.ghatana.platform.audit.AuditEvent;
import com.ghatana.platform.audit.AuditQueryService;
import com.ghatana.yappc.api.audit.dto.*;
import com.ghatana.yappc.api.common.ApiResponse;
import com.ghatana.yappc.api.common.JsonUtils;
import com.ghatana.yappc.api.common.TenantContextExtractor;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST API Controller for Audit Trail operations.
 *
 * <p><b>Purpose</b><br>
 * Exposes existing AuditService to YAPPC frontend for: - Recording audit events - Querying audit
 * history - Filtering by category, severity, actor
 *
 * <p><b>Usage</b><br>
 *
 * <pre>{@code
 * POST /api/audit/record - Record an audit event
 * GET  /api/audit/events?workspaceId=...&category=... - Query events
 * GET  /api/audit/events/{eventId} - Get event details
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * - API layer (HTTP → Service) - Wraps existing AuditService from libs/java/audit - Handles request
 * validation and response formatting - Enforces RBAC via AuthorizationService
 *
 * @doc.type class
 * @doc.purpose REST API for audit trail
 * @doc.layer api
 * @doc.pattern Controller
 */
public class AuditController {

  private static final Logger logger = LoggerFactory.getLogger(AuditController.class);

  private final AuditService auditService;

  /**
   * Creates AuditController.
   *
   * @param auditService the audit service
   */
  public AuditController(AuditService auditService) {
    this.auditService = Objects.requireNonNull(auditService, "auditService is required");
  }

  /**
   * Record an audit event.
   *
   * <p>POST /api/audit/record
   *
   * @param request HTTP request with AuditEvent JSON
   * @return Promise of HTTP response
   */
  public Promise<HttpResponse> recordEvent(HttpRequest request) {
    return TenantContextExtractor.requireAuthenticated(request)
        .then(
            ctx ->
                JsonUtils.parseBody(request, RecordAuditEventRequest.class)
                    .then(
                        req -> {
                          logger.info(
                              "Recording audit event: category={}, action={}, actor={}",
                              req.category(),
                              req.action(),
                              req.actorId());

                          // Build AuditEvent from DTO
                          AuditEvent event =
                              AuditEvent.builder()
                                  .tenantId(ctx.tenantId())
                                  .eventType(req.category() + "_" + req.action())
                                  .principal(req.actorId())
                                  .resourceType(req.entityType())
                                  .resourceId(req.entityId())
                                  .timestamp(Instant.now())
                                  .success(true)
                                  .detail("workspaceId", req.workspaceId())
                                  .detail("actorName", req.actorName())
                                  .detail("entityName", req.entityName())
                                  .detail("severity", req.severity())
                                  .build();

                          return auditService
                              .record(event)
                              .map(
                                  v -> {
                                    Map<String, Object> response =
                                        Map.of(
                                            "status", "recorded",
                                            "eventId", event.getId(),
                                            "timestamp", event.getTimestamp());
                                    return ApiResponse.created(response);
                                  });
                        }))
        .then(response -> Promise.of(response), e -> Promise.of(ApiResponse.fromException(e)));
  }

  /**
   * Query audit events.
   *
   * <p>GET /api/audit/events?workspaceId=...&category=...&severity=...
   *
   * <p><b>Note:</b> The base AuditService interface only supports recording events. Query
   * functionality requires an extended interface (AuditQueryService) to be implemented. This
   * endpoint returns mock data until the query interface is available.
   *
   * @param request HTTP request with query params
   * @return Promise of HTTP response with event list
   */
  public Promise<HttpResponse> queryEvents(HttpRequest request) {
    return TenantContextExtractor.requireAuthenticated(request)
        .then(
            ctx -> {
              AuditQueryService queryService = asQueryService();
              if (queryService == null) {
                int page = parsePositiveInt(request.getQueryParameter("page"), 1);
                int pageSize = parsePositiveInt(request.getQueryParameter("pageSize"), 20);
                return Promise.of(
                    ApiResponse.ok(AuditEventsPageResponse.of(List.of(), 0L, page, pageSize)));
              }

              String workspaceId = request.getQueryParameter("workspaceId");
              String category = request.getQueryParameter("category");
              String severity = request.getQueryParameter("severity");
              String actorId = request.getQueryParameter("actorId");
              int page = parsePositiveInt(request.getQueryParameter("page"), 1);
              int pageSize = parsePositiveInt(request.getQueryParameter("pageSize"), 20);

              logger.info(
                  "Querying audit events: workspaceId={}, category={}, severity={}, actorId={}",
                  workspaceId,
                  category,
                  severity,
                  actorId);

              int offset = (page - 1) * pageSize;
              return queryService
                  .findByTenantId(ctx.tenantId())
                  .map(
                      events -> {
                        List<AuditEventResponse> filtered =
                            events.stream()
                                .filter(event -> matchesWorkspace(event, workspaceId))
                                .filter(event -> matchesCategory(event, category))
                                .filter(event -> matchesSeverity(event, severity))
                                .filter(event -> matchesActor(event, actorId))
                                .sorted(
                                    Comparator.comparing(
                                            AuditEvent::getTimestamp,
                                            Comparator.nullsLast(Comparator.naturalOrder()))
                                        .reversed())
                                .map(this::toResponse)
                                .toList();

                        int fromIndex = Math.min(offset, filtered.size());
                        int toIndex = Math.min(fromIndex + pageSize, filtered.size());
                        List<AuditEventResponse> pageEvents = filtered.subList(fromIndex, toIndex);

                        AuditEventsPageResponse response =
                            AuditEventsPageResponse.of(pageEvents, filtered.size(), page, pageSize);
                        return ApiResponse.ok(response);
                      });
            })
        .then(response -> Promise.of(response), e -> Promise.of(ApiResponse.fromException(e)));
  }

  /**
   * Get audit event by ID.
   *
   * <p>GET /api/audit/events/{eventId}
   *
   * <p><b>Note:</b> The base AuditService interface only supports recording events. Fetching by ID
   * requires an extended interface to be implemented.
   *
   * @param request HTTP request
   * @param eventId event ID from path
   * @return Promise of HTTP response with event details
   */
  public Promise<HttpResponse> getEvent(HttpRequest request, String eventId) {
    return TenantContextExtractor.requireAuthenticated(request)
        .then(
            ctx -> {
              AuditQueryService queryService = asQueryService();
              if (queryService == null) {
                return Promise.of(ApiResponse.notFound("Audit event not found: " + eventId));
              }

              logger.info("Fetching audit event: {}", eventId);
              return queryService
                  .findById(ctx.tenantId(), eventId)
                  .map(
                      maybeEvent ->
                          maybeEvent
                              .map(event -> ApiResponse.ok(toResponse(event)))
                              .orElseGet(() -> ApiResponse.notFound("Audit event not found: " + eventId)));
            })
        .then(response -> Promise.of(response), e -> Promise.of(ApiResponse.fromException(e)));
  }

  private AuditQueryService asQueryService() {
    if (auditService instanceof AuditQueryService queryService) {
      return queryService;
    }
    return null;
  }

  private int parsePositiveInt(String value, int defaultValue) {
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    int parsed = Integer.parseInt(value);
    if (parsed <= 0) {
      throw new IllegalArgumentException("Query parameter must be positive: " + value);
    }
    return parsed;
  }

  private boolean matchesWorkspace(AuditEvent event, String workspaceId) {
    if (workspaceId == null || workspaceId.isBlank()) {
      return true;
    }
    Object eventWorkspaceId = event.getDetail("workspaceId");
    return workspaceId.equals(eventWorkspaceId);
  }

  private boolean matchesCategory(AuditEvent event, String category) {
    if (category == null || category.isBlank()) {
      return true;
    }
    return category.equalsIgnoreCase(extractCategory(event.getEventType()));
  }

  private boolean matchesSeverity(AuditEvent event, String severity) {
    if (severity == null || severity.isBlank()) {
      return true;
    }
    Object eventSeverity = event.getDetail("severity");
    return eventSeverity != null && severity.equalsIgnoreCase(eventSeverity.toString());
  }

  private boolean matchesActor(AuditEvent event, String actorId) {
    if (actorId == null || actorId.isBlank()) {
      return true;
    }
    return actorId.equals(event.getPrincipal());
  }

  private AuditEventResponse toResponse(AuditEvent event) {
    String category = extractCategory(event.getEventType());
    String action = extractAction(event.getEventType());

    String workspaceId = readDetail(event, "workspaceId", "");
    String actorName = readDetail(event, "actorName", "");
    String entityName = readDetail(event, "entityName", "");
    String severity = readDetail(event, "severity", "info");
    Object oldValue = event.getDetail("oldValue");
    Object newValue = event.getDetail("newValue");

    Map<String, String> metadata = new HashMap<>();
    metadata.put("tenantId", event.getTenantId());
    if (event.getSuccess() != null) {
      metadata.put("success", event.getSuccess().toString());
    }

    return new AuditEventResponse(
        event.getId(),
        workspaceId,
        category,
        action,
        event.getPrincipal(),
        actorName,
        event.getResourceType(),
        event.getResourceId(),
        entityName,
        severity,
        event.getTimestamp(),
        event.getDetails(),
        oldValue,
        newValue,
        metadata);
  }

  private String readDetail(AuditEvent event, String key, String defaultValue) {
    Object value = event.getDetail(key);
    return value != null ? value.toString() : defaultValue;
  }

  private String extractCategory(String eventType) {
    if (eventType == null || eventType.isBlank()) {
      return "UNKNOWN";
    }
    int separatorIndex = eventType.lastIndexOf('_');
    if (separatorIndex <= 0) {
      return eventType;
    }
    return eventType.substring(0, separatorIndex);
  }

  private String extractAction(String eventType) {
    if (eventType == null || eventType.isBlank()) {
      return "UNKNOWN";
    }
    int separatorIndex = eventType.lastIndexOf('_');
    if (separatorIndex < 0 || separatorIndex >= eventType.length() - 1) {
      return eventType;
    }
    return eventType.substring(separatorIndex + 1);
  }
}
