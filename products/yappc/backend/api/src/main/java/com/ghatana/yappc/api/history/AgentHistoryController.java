/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.history;

import com.ghatana.agent.memory.model.MemoryItemType;
import com.ghatana.agent.memory.model.taskstate.TaskState;
import com.ghatana.agent.memory.persistence.PersistentMemoryPlane;
import com.ghatana.agent.memory.store.MemoryQuery;
import com.ghatana.agent.memory.store.taskstate.TaskStateStore;
import com.ghatana.yappc.api.common.ApiResponse;
import com.ghatana.yappc.api.common.TenantContextExtractor;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * REST controller providing access to agent execution history and turn rationale.
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>{@code GET /api/v1/agents/{id}/history?limit=20&amp;offset=0}
 *       — paginated list of task-state records for the given agent</li>
 *   <li>{@code GET /api/v1/agents/{id}/rationale/{turnId}}
 *       — episodic memory entries captured during a specific agent turn</li>
 * </ul>
 *
 * <h2>History Response (200 OK)</h2>
 * <pre>
 * {
 *   "agentId": "requirements-analyst-v2",
 *   "tenantId": "tenant-alpha",
 *   "total": 3,
 *   "items": [
 *     {
 *       "taskId": "task-uuid",
 *       "name": "Implement login feature",
 *       "status": "COMPLETED",
 *       "currentPhase": "implementation",
 *       "createdAt": "2025-01-01T10:00:00Z",
 *       "lastActiveAt": "2025-01-01T11:30:00Z",
 *       "phaseCount": 5,
 *       "checkpointCount": 3
 *     }
 *   ]
 * }
 * </pre>
 *
 * <h2>Rationale Response (200 OK)</h2>
 * <pre>
 * {
 *   "agentId": "requirements-analyst-v2",
 *   "turnId": "turn-uuid",
 *   "episodes": [
 *     {
 *       "id": "episode-uuid",
 *       "turnId": "turn-uuid",
 *       "summary": "Identified 3 gaps in requirements",
 *       "confidence": 0.92,
 *       "createdAt": "2025-01-01T10:01:00Z"
 *     }
 *   ]
 * }
 * </pre>
 *
 * @doc.type class
 * @doc.purpose REST API for agent execution history and turn-level rationale
 * @doc.layer api
 * @doc.pattern Controller
 * @doc.gaa.lifecycle capture
 */
public class AgentHistoryController {

    private static final Logger log = LoggerFactory.getLogger(AgentHistoryController.class);

    private static final int DEFAULT_LIMIT  = 20;
    private static final int MAX_LIMIT      = 100;

    private final TaskStateStore taskStateStore;
    private final PersistentMemoryPlane memoryPlane;

    /**
     * Creates a new {@code AgentHistoryController}.
     *
     * @param memoryPlane the persistent memory plane providing both task state
     *                    and episodic memory access (never {@code null})
     */
    public AgentHistoryController(PersistentMemoryPlane memoryPlane) {
        this.memoryPlane    = Objects.requireNonNull(memoryPlane, "memoryPlane");
        this.taskStateStore = Objects.requireNonNull(memoryPlane.getTaskStateStore(), "taskStateStore");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/agents/{id}/history
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns a paginated list of active task-state records for the given agent,
     * scoped to the authenticated tenant.
     *
     * <p>Query parameters:
     * <ul>
     *   <li>{@code limit}  — number of records to return (1–{@value #MAX_LIMIT}, default {#DEFAULT_LIMIT})</li>
     *   <li>{@code offset} — zero-based offset for pagination (default 0)</li>
     * </ul>
     *
     * @param request HTTP request (must be authenticated)
     * @param agentId the agent identifier from the URL path
     * @return 200 OK with paginated history, or 400/401 on validation failure
     */
    public Promise<HttpResponse> getHistory(HttpRequest request, String agentId) {
        TenantContextExtractor.RequestContext ctx = TenantContextExtractor.extract(request);
        if (!ctx.authenticated()) {
            return Promise.of(ApiResponse.unauthorized("Authentication required"));
        }

        int limit  = parseQueryInt(request, "limit",  DEFAULT_LIMIT, 1, MAX_LIMIT);
        int offset = parseQueryInt(request, "offset", 0, 0, Integer.MAX_VALUE);

        log.debug("Agent history: agentId={} tenant={} limit={} offset={}", agentId, ctx.tenantId(), limit, offset);

        return taskStateStore.listActiveTasks(agentId)
                .map(tasks -> {
                    // Apply tenant scoping and client-side pagination
                    List<TaskState> filtered = tasks.stream()
                            .filter(t -> ctx.tenantId().equals(t.getTenantId()))
                            .sorted((a, b) -> b.getUpdatedAt().compareTo(a.getUpdatedAt()))
                            .skip(offset)
                            .limit(limit)
                            .toList();

                    List<Map<String, Object>> items = filtered.stream()
                            .map(this::serializeTaskState)
                            .toList();

                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("agentId",  agentId);
                    response.put("tenantId", ctx.tenantId());
                    response.put("total",    items.size());
                    response.put("offset",   offset);
                    response.put("limit",    limit);
                    response.put("items",    items);

                    return ApiResponse.ok(response);
                })
                .then(Promise::of, e -> {
                    log.error("Agent history query failed: agentId={} error={}", agentId, e.getMessage(), e);
                    return Promise.of(ApiResponse.fromException(e));
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/agents/{id}/rationale/{turnId}
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the episodic memory entries captured during a specific agent turn,
     * providing reasoning transparency (rationale) for agent decisions.
     *
     * @param request HTTP request (must be authenticated)
     * @param agentId the agent identifier from the URL path
     * @param turnId  the turn identifier (UUID) from the URL path
     * @return 200 OK with turn episodes or 404 if no episodes found for that turn
     */
    public Promise<HttpResponse> getRationale(HttpRequest request, String agentId, String turnId) {
        TenantContextExtractor.RequestContext ctx = TenantContextExtractor.extract(request);
        if (!ctx.authenticated()) {
            return Promise.of(ApiResponse.unauthorized("Authentication required"));
        }

        log.debug("Agent rationale: agentId={} turnId={} tenant={}", agentId, turnId, ctx.tenantId());

        MemoryQuery query = MemoryQuery.builder()
                .agentId(agentId)
                .tenantId(ctx.tenantId())
                .itemTypes(List.of(MemoryItemType.EPISODE))
                .limit(MAX_LIMIT)
                .build();

        return memoryPlane.queryEpisodes(query)
                .map(episodes -> {
                    // Filter to the specific turnId
                    var turnEpisodes = episodes.stream()
                            .filter(ep -> turnId.equals(ep.getTurnId()))
                            .toList();

                    if (turnEpisodes.isEmpty()) {
                        return ApiResponse.notFound(
                                "No rationale found for agent '" + agentId + "' turn '" + turnId + "'");
                    }

                    List<Map<String, Object>> items = turnEpisodes.stream()
                            .map(ep -> {
                                Map<String, Object> m = new LinkedHashMap<>();
                                m.put("id",         ep.getId());
                                m.put("turnId",     ep.getTurnId());
                                m.put("input",      ep.getInput());
                                m.put("output",     ep.getOutput());
                                m.put("action",     ep.getAction());
                                m.put("confidence", ep.getValidity().getConfidence());
                                m.put("cost",       ep.getCost());
                                m.put("latencyMs",  ep.getLatencyMs());
                                m.put("createdAt",  ep.getCreatedAt());
                                if (ep.getProvenance() != null) {
                                    m.put("source", ep.getProvenance().getSource());
                                }
                                return m;
                            })
                            .toList();

                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("agentId",  agentId);
                    response.put("turnId",   turnId);
                    response.put("tenantId", ctx.tenantId());
                    response.put("episodes", items);
                    return ApiResponse.ok(response);
                })
                .then(Promise::of, e -> {
                    log.error("Agent rationale query failed: agentId={} turnId={} error={}",
                            agentId, turnId, e.getMessage(), e);
                    return Promise.of(ApiResponse.fromException(e));
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private Map<String, Object> serializeTaskState(TaskState t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("taskId",          t.getTaskId());
        m.put("name",            t.getName());
        m.put("status",          t.getStatus().name());
        m.put("currentPhase",    t.getCurrentPhase());
        m.put("createdAt",       t.getCreatedAt());
        m.put("updatedAt",       t.getUpdatedAt());
        m.put("lastActiveAt",    t.getLastActiveAt());
        m.put("phaseCount",      t.getPhases().size());
        m.put("checkpointCount", t.getCheckpoints().size());
        m.put("blockerCount",    t.getBlockers().size());
        return m;
    }

    private int parseQueryInt(HttpRequest request, String param, int defaultValue,
                               int minValue, int maxValue) {
        String raw = request.getQueryParameter(param);
        if (raw == null || raw.isBlank()) return defaultValue;
        try {
            return Math.max(minValue, Math.min(maxValue, Integer.parseInt(raw.trim())));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
