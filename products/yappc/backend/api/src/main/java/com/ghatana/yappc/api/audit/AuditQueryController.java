/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.audit;

import com.ghatana.platform.audit.AuditEvent;
import com.ghatana.platform.audit.AuditQueryService;
import io.activej.http.HttpResponse;
import io.activej.inject.annotation.Inject;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.stream.Collectors;

/**
 * REST API for querying audit events with filtering.
 *
 * <p>Exposes: {@code GET /api/v1/audit/events}
 *
 * <p><b>Query Parameters:</b>
 * <ul>
 *   <li>{@code tenantId} — tenant filter (required/automatic from auth context)
 *   <li>{@code eventType} — filter by event type (e.g., "AGENT_TURN_STARTED")
 *   <li>{@code resourceType} — filter by resource (e.g., "AGENT", "POLICY")
 *   <li>{@code resourceId} — filter by resource ID
 *   <li>{@code principal} — filter by principal (user/service account)
 *   <li>{@code from} — ISO-8601 timestamp (inclusive)
 *   <li>{@code to} — ISO-8601 timestamp (inclusive)
 *   <li>{@code limit} — max results (default 100, max 1000)
 *   <li>{@code offset} — pagination offset (default 0)
 * </ul>
 *
 * <p><b>Response:</b>
 * <pre>{@code
 * GET /api/v1/audit/events?eventType=AGENT_TURN_STARTED&limit=20
 * HTTP/1.1 200 OK
 * Content-Type: application/json
 *
 * {
 *   "events": [
 *     {
 *       "id": "uuid-1",
 *       "tenantId": "tenant-001",
 *       "eventType": "AGENT_TURN_STARTED",
 *       "principal": "agent-123",
 *       "resourceType": "AGENT",
 *       "resourceId": "agent-123",
 *       "success": true,
 *       "timestamp": "2026-03-15T14:30:00Z",
 *       "details": { "phase": "PERCEIVE", "turn": 1 }
 *     }
 *   ],
 *   "total": 150,
 *   "offset": 0,
 *   "limit": 20
 * }
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose REST controller for querying audit event history
 * @doc.layer api
 * @doc.pattern Controller
 *
 * @since 2.4.0
 */
public class AuditQueryController {

    private static final Logger logger = LoggerFactory.getLogger(AuditQueryController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AuditQueryService auditQueryService;

    @Inject
    public AuditQueryController(AuditQueryService auditQueryService) {
        this.auditQueryService = Objects.requireNonNull(auditQueryService, "auditQueryService");
    }

    /**
     * Query audit events with filtering.
     *
     * <p>Supports multiple query modes:
     * <ul>
     *   <li>No params → all events for tenant
     *   <li>eventType → filter by event type
     *   <li>principal → filter by principal/actor
     *   <li>resourceType + resourceId → filter by resource
     *   <li>from + to → filter by timestamp range
     * </ul>
     *
     * @param tenantId     owning tenant (from auth context)
     * @param eventType    optional event type filter
     * @param resourceType optional resource type filter (requires resourceId)
     * @param resourceId   optional resource ID filter (requires resourceType)
     * @param principal    optional principal filter
     * @param from         optional ISO-8601 start timestamp
     * @param to           optional ISO-8601 end timestamp
     * @param limit        max results (1–1000, default 100)
     * @param offset       pagination offset (default 0)
     * @return Promise of HTTP 200 with JSON audit event list
     */
    public Promise<HttpResponse> queryAuditEvents(
            String tenantId,
            String eventType,
            String resourceType,
            String resourceId,
            String principal,
            String from,
            String to,
            int limit,
            int offset) {

        if (tenantId == null || tenantId.isEmpty()) {
            logger.warn("[AUDIT] Query without tenantId");
            return Promise.of(
                    HttpResponse.ofCode(400)
                            .withJson("{\"error\":\"tenantId required\"}")
                            .build()
            );
        }

        // Validate limit
        int normalizedLimit = Math.min(Math.max(limit, 1), 1000);
        int normalizedOffset = Math.max(offset, 0);

        logger.info("[AUDIT] Query: tenant={} eventType={} principal={} limit={} offset={}", 
                tenantId, eventType, principal, normalizedLimit, normalizedOffset);

        // Select appropriate query based on filters
        Promise<List<AuditEvent>> queryPromise;
        
        if (eventType != null && !eventType.isEmpty()) {
            queryPromise = auditQueryService.findByEventType(tenantId, eventType);
        } else if (principal != null && !principal.isEmpty()) {
            queryPromise = auditQueryService.findByPrincipal(tenantId, principal);
        } else if (resourceType != null && !resourceType.isEmpty() && resourceId != null && !resourceId.isEmpty()) {
            queryPromise = auditQueryService.findByResource(tenantId, resourceType, resourceId);
        } else if (from != null || to != null) {
            Instant fromInstant = from != null ? Instant.parse(from) : Instant.EPOCH;
            Instant toInstant = to != null ? Instant.parse(to) : Instant.now();
            queryPromise = auditQueryService.findByTimeRange(tenantId, fromInstant, toInstant);
        } else {
            // Default: all events for tenant with pagination
            queryPromise = auditQueryService.findByTenantId(tenantId, normalizedOffset, normalizedLimit);
        }

        return queryPromise
                .map(events -> {
                    Map<String, Object> response = Map.of(
                            "events", events.stream()
                                    .map(this::eventToMap)
                                    .collect(Collectors.toList()),
                            "limit", normalizedLimit,
                            "offset", normalizedOffset,
                            "count", events.size()
                    );

                    return HttpResponse.ok200()
                            .withJson(toJson(response))
                            .build();
                })
                .mapException(ex -> {
                    logger.error("[AUDIT] Query failed: {}", ex.getMessage(), ex);
                    return new RuntimeException("Audit query failed", ex);
                });
    }

    /**
     * Get a single audit event by ID.
     *
     * @param tenantId tenant isolation
     * @param eventId  audit event ID
     * @return Promise of HTTP 200 (found) or 404 (not found)
     */
    public Promise<HttpResponse> getAuditEvent(String tenantId, String eventId) {
        if (tenantId == null || tenantId.isEmpty() || eventId == null || eventId.isEmpty()) {
            return Promise.of(
                    HttpResponse.ofCode(400)
                            .withJson("{\"error\":\"tenantId and eventId required\"}")
                            .build()
            );
        }

        logger.info("[AUDIT] Get event: tenant={} eventId={}", tenantId, eventId);

        return auditQueryService.findById(tenantId, eventId)
                .map(optEvent -> {
                    if (optEvent.isPresent()) {
                        Map<String, Object> response = eventToMap(optEvent.get());
                        return HttpResponse.ok200()
                                .withJson(toJson(response))
                                .build();
                    } else {
                        return HttpResponse.ofCode(404)
                                .withJson("{\"error\":\"Audit event not found\"}")
                                .build();
                    }
                })
                .mapException(ex -> {
                    logger.error("[AUDIT] Get event failed: {}", ex.getMessage(), ex);
                    return new RuntimeException("Audit event lookup failed", ex);
                });
    }

    /**
     * Count total audit events for a tenant.
     *
     * @param tenantId tenant isolation
     * @return Promise of HTTP 200 with total count
     */
    public Promise<HttpResponse> countTotalEvents(String tenantId) {
        if (tenantId == null || tenantId.isEmpty()) {
            return Promise.of(
                    HttpResponse.ofCode(400)
                            .withJson("{\"error\":\"tenantId required\"}")
                            .build()
            );
        }

        logger.info("[AUDIT] Count total: tenant={}", tenantId);

        return auditQueryService.countByTenantId(tenantId)
                .map(count -> {
                    Map<String, Object> response = Map.of(
                            "total", count
                    );
                    return HttpResponse.ok200()
                            .withJson(toJson(response))
                            .build();
                })
                .mapException(ex -> {
                    logger.error("[AUDIT] Count failed: {}", ex.getMessage(), ex);
                    return new RuntimeException("Audit count failed", ex);
                });
    }

    /**
     * Convert AuditEvent domain model to API response map.
     */
    private Map<String, Object> eventToMap(AuditEvent event) {
        return Map.of(
                "id", event.getId(),
                "tenantId", event.getTenantId(),
                "eventType", event.getEventType(),
                "principal", event.getPrincipal() != null ? event.getPrincipal() : "",
                "resourceType", event.getResourceType() != null ? event.getResourceType() : "",
                "resourceId", event.getResourceId() != null ? event.getResourceId() : "",
                "success", event.getSuccess(),
                "timestamp", event.getTimestamp().toString(),
                "details", event.getDetails() != null ? event.getDetails() : Map.of()
        );
    }

    private static String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON serialization failed", e);
        }
    }
}
