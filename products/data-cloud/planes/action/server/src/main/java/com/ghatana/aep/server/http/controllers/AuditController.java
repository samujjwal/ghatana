/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.http.controllers;

import com.ghatana.aep.server.http.HttpHelper;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.spi.EntityStore;
import com.ghatana.platform.domain.eventstore.TenantContext;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * HTTP controller for audit log ingestion and query.
 *
 * <p>Provides append-only audit trail endpoints used by the frontend
 * {@code auditLogService} and by server-side mutation handlers (cancel, publish,
 * marketplace install, policy approval).
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST  /api/v1/audit/log}   — append an audit event</li>
 *   <li>{@code GET   /api/v1/audit/query} — tenant-scoped, paginated query</li>
 * </ul>
 *
 * <p>When a {@link DataCloudClient} is available, events are persisted via the
 * entity store. When unavailable (dev/embedded mode) events are held in a
 * bounded in-memory list and a {@code "source": "ephemeral"} warning is returned.
 *
 * @doc.type class
 * @doc.purpose Append-only audit log API for compliance and privacy-sensitive operations
 * @doc.layer product
 * @doc.pattern Controller
 */
public final class AuditController implements AepController {

    private static final Logger log = LoggerFactory.getLogger(AuditController.class);

    /** Bounded in-memory fallback — max 2 000 entries; rotated FIFO when full. */
    private static final int MAX_MEMORY_ENTRIES = 2_000;

    @Nullable
    private final DataCloudClient dataCloudClient;

    /**
     * In-memory fallback store — used when DataCloud is not configured.
     * Thread-safe; written and read on the event-loop thread only.
     */
    private final List<Map<String, Object>> inMemoryLog = new CopyOnWriteArrayList<>();

    /**
     * @param dataCloudClient optional Data Cloud client; when {@code null} falls back to in-memory
     */
    public AuditController(@Nullable DataCloudClient dataCloudClient) {
        this.dataCloudClient = dataCloudClient;
    }

    @Override
    public String getBasePath() {
        return "/api/v1/audit";
    }

    @Override
    public Promise<HttpResponse> handle(HttpRequest request, String path) {
        if (HttpMethod.POST == request.getMethod() && "log".equals(path)) {
            return handleLog(request);
        }
        if (HttpMethod.GET == request.getMethod() && "query".equals(path)) {
            return handleQuery(request);
        }
        return Promise.of(HttpHelper.errorResponse(404, "Audit endpoint not found: " + path));
    }

    /**
     * Records an audit event sent by the frontend or by server-side mutation handlers.
     *
     * <p>Expected request body (validated fields):
     * <pre>{@code
     * {
     *   "id":           "uuid",
     *   "timestamp":    "ISO-8601",
     *   "userId":       "string",
     *   "tenantId":     "string",
     *   "action":       "one of the allowed action enum values",
     *   "resource":     "string",
     *   "status":       "success | failure | denied",
     *   "metadata":     { ... }  // optional
     * }
     * }</pre>
     */
    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleLog(HttpRequest request) {
        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> event = HttpHelper.mapper().readValue(body, Map.class);

                String tenantId = asString(event.get("tenantId"));
                if (tenantId == null || tenantId.isBlank()) {
                    return Promise.of(HttpHelper.errorResponse(400, "tenantId is required"));
                }
                String action = asString(event.get("action"));
                if (action == null || action.isBlank()) {
                    return Promise.of(HttpHelper.errorResponse(400, "action is required"));
                }
                String status = asString(event.get("status"));
                if (status == null || status.isBlank()) {
                    return Promise.of(HttpHelper.errorResponse(400, "status is required"));
                }

                // Enrich with server-side receipt timestamp
                Map<String, Object> enriched = new LinkedHashMap<>(event);
                enriched.putIfAbsent("id", java.util.UUID.randomUUID().toString());
                enriched.putIfAbsent("timestamp", Instant.now().toString());
                enriched.put("receivedAt", Instant.now().toString());

                return persistEvent(tenantId, enriched)
                    .map(source -> HttpHelper.jsonResponse(Map.of(
                        "logged", true,
                        "id", enriched.get("id"),
                        "source", source,
                        "timestamp", Instant.now().toString()
                    )));

            } catch (Exception e) {
                log.error("[audit] failed to parse log request", e);
                return Promise.of(HttpHelper.errorResponse(400, "Invalid audit event: " + e.getMessage()));
            }
        }, e -> {
            log.error("[audit] failed to read log request body", e);
            return Promise.of(HttpHelper.errorResponse(400, "Failed to read request body"));
        });
    }

    /**
     * Queries audit events for the requesting tenant.
     *
     * <p>Query parameters:
     * <ul>
     *   <li>{@code tenantId} — required (also accepted from {@code X-Tenant-Id} header)</li>
     *   <li>{@code action}   — optional action filter</li>
     *   <li>{@code status}   — optional status filter ({@code success|failure|denied})</li>
     *   <li>{@code from}     — optional ISO-8601 start datetime</li>
     *   <li>{@code to}       — optional ISO-8601 end datetime</li>
     *   <li>{@code limit}    — max results (default 50, max 500)</li>
     *   <li>{@code offset}   — pagination offset (default 0)</li>
     * </ul>
     */
    public Promise<HttpResponse> handleQuery(HttpRequest request) {
        String tenantId = HttpHelper.resolveTenantId(request);
        String actionFilter = request.getQueryParameter("action");
        String statusFilter = request.getQueryParameter("status");
        String fromParam   = request.getQueryParameter("from");
        String toParam     = request.getQueryParameter("to");
        int limit  = parseIntParam(request.getQueryParameter("limit"),  50,  500);
        int offset = parseIntParam(request.getQueryParameter("offset"),  0, Integer.MAX_VALUE);

        return queryEvents(tenantId, actionFilter, statusFilter, fromParam, toParam, limit, offset)
            .map(result -> HttpHelper.jsonResponse(Map.of(
                "events",  result.events(),
                "total",   result.total(),
                "hasMore", result.hasMore(),
                "source",  result.source(),
                "timestamp", Instant.now().toString()
            )))
            .then(Promise::of, e -> Promise.of(
                HttpHelper.errorResponse(500, "Failed to query audit log: " + e.getMessage())));
    }

    // ─── Persistence helpers ──────────────────────────────────────────────────

    /**
     * Persists one audit event. Returns the storage source label ({@code "data-cloud"} or
     * {@code "ephemeral"}).
     */
    private Promise<String> persistEvent(String tenantId, Map<String, Object> enriched) {
        if (dataCloudClient != null && dataCloudClient.entityStore() != null) {
            String id = asString(enriched.get("id"));
            TenantContext tenantContext = TenantContext.of(tenantId);
            EntityStore.Entity entity = EntityStore.Entity.builder()
                    .id(id)
                    .collection("audit-events")
                    .data(enriched)
                    .build();
            return dataCloudClient.entityStore()
                .save(tenantContext, entity)
                .map(ignored -> "data-cloud")
                .then(Promise::of, err -> {
                    log.warn("[audit] DataCloud write failed, falling back to memory: {}", err.getMessage());
                    appendToMemory(enriched);
                    return Promise.of("ephemeral-fallback");
                });
        }
        appendToMemory(enriched);
        return Promise.of("ephemeral");
    }

    private void appendToMemory(Map<String, Object> event) {
        if (inMemoryLog.size() >= MAX_MEMORY_ENTRIES) {
            inMemoryLog.remove(0);
        }
        inMemoryLog.add(event);
    }

    /** Result container for query responses. */
    private record QueryResult(List<Map<String, Object>> events, int total, boolean hasMore, String source) {}

    private Promise<QueryResult> queryEvents(
            String tenantId,
            @Nullable String actionFilter,
            @Nullable String statusFilter,
            @Nullable String fromParam,
            @Nullable String toParam,
            int limit,
            int offset) {

        // For now, read from in-memory log regardless of DataCloud availability.
        // When DataCloud entityStore grows a query API, switch here first.
        List<Map<String, Object>> filtered = inMemoryLog.stream()
            .filter(e -> tenantId.equals(e.get("tenantId")))
            .filter(e -> actionFilter == null || actionFilter.equals(e.get("action")))
            .filter(e -> statusFilter == null || statusFilter.equals(e.get("status")))
            .filter(e -> fromParam == null || timestampAfter(e, fromParam))
            .filter(e -> toParam   == null || timestampBefore(e, toParam))
            .toList();

        int total = filtered.size();
        List<Map<String, Object>> page = filtered.stream()
            .skip(offset)
            .limit(limit)
            .toList();
        boolean hasMore = (offset + page.size()) < total;

        String source = dataCloudClient != null ? "data-cloud" : "ephemeral";
        return Promise.of(new QueryResult(new ArrayList<>(page), total, hasMore, source));
    }

    // ─── Utility helpers ──────────────────────────────────────────────────────

    private static boolean timestampAfter(Map<String, Object> event, String from) {
        try {
            String ts = asString(event.get("timestamp"));
            return ts != null && !Instant.parse(ts).isBefore(Instant.parse(from));
        } catch (Exception ignored) {
            return true;
        }
    }

    private static boolean timestampBefore(Map<String, Object> event, String to) {
        try {
            String ts = asString(event.get("timestamp"));
            return ts != null && !Instant.parse(ts).isAfter(Instant.parse(to));
        } catch (Exception ignored) {
            return true;
        }
    }

    @Nullable
    private static String asString(@Nullable Object value) {
        return value != null ? value.toString() : null;
    }

    private static int parseIntParam(@Nullable String raw, int defaultValue, int maxValue) {
        if (raw == null || raw.isBlank()) return defaultValue;
        try {
            int parsed = Integer.parseInt(raw.trim());
            return Math.min(Math.max(parsed, 0), maxValue);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }
}
