/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.http.controllers;

import com.ghatana.aep.AepEngine;
import com.ghatana.aep.eventcloud.store.EventCloudAgentStore;
import com.ghatana.aep.server.http.HttpHelper;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.spi.EntityStore;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Controller for agent management endpoints.
 * Handles agent lifecycle, execution, and memory queries.
 *
 * <p>Agent registry operations (list, get, deregister) are backed by
 * {@link EventCloudAgentStore} — the canonical Data-Cloud-backed store for AEP agents.
 * Memory operations (episodes, facts, policies) delegate to {@link DataCloudClient}
 * for the {@code dc_memory} collection.
 *
 * @doc.type class
 * @doc.purpose Agent registry and memory management via EventCloudAgentStore
 * @doc.layer product
 * @doc.pattern Service
 * @doc.gaa.lifecycle perceive
 */
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    private final AepEngine engine;
    /** Agent registry store backed by Data-Cloud EntityStore. Null when Data-Cloud is absent. */
    @Nullable
    private final EventCloudAgentStore agentStore;
    /** Data-Cloud client retained for memory operations (episodes, facts, policies). */
    @Nullable
    private final DataCloudClient agentDataCloud;

    /**
     * Creates an agent controller backed by the canonical {@link EventCloudAgentStore}.
     *
     * @param engine      AEP engine for event processing
     * @param agentDataCloud optional Data-Cloud client; when non-null a store is created
     *                       via {@code agentDataCloud.entityStore()} and memory endpoints
     *                       are also enabled
     */
    public AgentController(AepEngine engine, @Nullable DataCloudClient agentDataCloud) {
        this.engine = engine;
        this.agentDataCloud = agentDataCloud;
        EntityStore entityStore = agentDataCloud != null ? agentDataCloud.entityStore() : null;
        this.agentStore = entityStore != null ? new EventCloudAgentStore(entityStore) : null;
    }

    public Promise<HttpResponse> handleListAgents(HttpRequest request) {
        String tenantId = HttpHelper.resolveTenantId(request);
        if (agentStore == null) {
            return Promise.of(HttpHelper.errorResponse(
                503,
                "Agent registry not available — DataCloudClient not configured",
                Map.of("tenantId", tenantId)
            ));
        }
        String limitParam = request.getQueryParameter("limit");
        int limit = limitParam != null ? Math.min(Integer.parseInt(limitParam), 1000) : 1000;
        return agentStore.listAgents(tenantId, limit)
            .map(entities -> {
                List<Map<String, Object>> summaries = entities.stream()
                    .map(e -> Map.<String, Object>of(
                        "id", e.data().getOrDefault("id", e.id().value()),
                        "name", e.data().getOrDefault("name", e.id().value()),
                        "type", e.data().getOrDefault("type", "unknown"),
                        "status", e.data().getOrDefault("status", "ACTIVE"),
                        "tenantId", tenantId
                    ))
                    .toList();
                return HttpHelper.jsonResponse(Map.of(
                    "tenantId", tenantId,
                    "agents", summaries,
                    "count", summaries.size(),
                    "timestamp", Instant.now().toString()
                ));
            })
            .then(Promise::of, e -> {
                log.error("[agents] list failed for tenant={}: {}", tenantId, e.getMessage(), e);
                return Promise.of(HttpHelper.errorResponse(500,
                    "Failed to list agents: " + e.getMessage()));
            });
    }

    public Promise<HttpResponse> handleGetAgent(HttpRequest request) {
        String agentId = request.getPathParameter("agentId");
        if (agentId == null || agentId.isBlank()) {
            return Promise.of(HttpHelper.errorResponse(400,
                "agentId path parameter is required"));
        }
        if (agentStore == null) {
            return Promise.of(HttpHelper.errorResponse(503,
                "Agent registry not available — DataCloudClient not configured"));
        }
        String tenantId = HttpHelper.resolveTenantId(request);
        return agentStore.findById(tenantId, agentId)
            .map(opt -> opt
                .map(e -> HttpHelper.jsonResponse(Map.of(
                    "id", e.data().getOrDefault("id", e.id().value()),
                    "tenantId", tenantId,
                    "data", e.data(),
                    "timestamp", Instant.now().toString()
                )))
                .orElse(HttpHelper.errorResponse(404, "Agent not found: " + agentId)))
            .then(Promise::of, e -> {
                log.error("[agents] get failed for agentId={}: {}", agentId, e.getMessage(), e);
                return Promise.of(HttpHelper.errorResponse(500,
                    "Failed to get agent: " + e.getMessage()));
            });
    }

    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleExecuteAgent(HttpRequest request) {
        String agentId = request.getPathParameter("agentId");
        if (agentId == null || agentId.isBlank()) {
            return Promise.of(HttpHelper.errorResponse(400,
                "agentId path parameter is required"));
        }
        return request.loadBody().then(buf -> {
            try {
                String bodyStr = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> reqBody = bodyStr.isBlank()
                    ? Map.of()
                    : HttpHelper.mapper().readValue(bodyStr, Map.class);
                String tenantId = reqBody.containsKey("tenantId")
                    ? (String) reqBody.get("tenantId")
                    : HttpHelper.resolveTenantId(request);
                Map<String, Object> input = reqBody.containsKey("input")
                    ? (Map<String, Object>) reqBody.get("input")
                    : Map.of();

                Map<String, Object> payload = new java.util.HashMap<>(input);
                payload.put("agentId", agentId);
                payload.put("tenantId", tenantId);

                AepEngine.Event event = new AepEngine.Event(
                    "agent.invocation",
                    Map.copyOf(payload),
                    Map.of("agentId", agentId),
                    Instant.now()
                );
                return engine.process(tenantId, event)
                    .map(result -> HttpHelper.jsonResponse(Map.of(
                        "agentId", agentId,
                        "tenantId", tenantId,
                        "eventId", result.eventId(),
                        "success", result.success(),
                        "detections", result.detections().size(),
                        "timestamp", Instant.now().toString()
                    )))
                    .then(Promise::of, e -> {
                        log.error("[agents] execute failed for agentId={}: {}",
                            agentId, e.getMessage(), e);
                        return Promise.of(HttpHelper.errorResponse(500,
                            "Agent execution failed: " + e.getMessage()));
                    });
            } catch (Exception e) {
                return Promise.of(HttpHelper.errorResponse(400,
                    "Invalid request: " + e.getMessage()));
            }
        }, e -> Promise.of(HttpHelper.errorResponse(400, "Failed to read request body")));
    }

    public Promise<HttpResponse> handleGetAgentMemory(HttpRequest request) {
        String agentId = request.getPathParameter("agentId");
        if (agentId == null || agentId.isBlank()) {
            return Promise.of(HttpHelper.errorResponse(400,
                "agentId path parameter is required"));
        }
        if (agentDataCloud == null) {
            return Promise.of(HttpHelper.errorResponse(503,
                "Agent memory not available — DataCloudClient not configured"));
        }
        String tenantId = HttpHelper.resolveTenantId(request);
        DataCloudClient.Query query = DataCloudClient.Query.builder()
            .filter(DataCloudClient.Filter.eq("agentId", agentId))
            .limit(10_000)
            .build();
        return agentDataCloud.query(tenantId, "dc_memory", query)
            .map(items -> {
                long episodic = items.stream()
                    .filter(e -> "EPISODIC".equals(e.data().get("type"))).count();
                long semantic = items.stream()
                    .filter(e -> "SEMANTIC".equals(e.data().get("type"))).count();
                long procedural = items.stream()
                    .filter(e -> "PROCEDURAL".equals(e.data().get("type"))).count();
                long preference = items.stream()
                    .filter(e -> "PREFERENCE".equals(e.data().get("type"))).count();
                long other = items.size() - episodic - semantic - procedural - preference;
                return HttpHelper.jsonResponse(Map.of(
                    "agentId", agentId,
                    "tenantId", tenantId,
                    "total", items.size(),
                    "byType", Map.of(
                        "episodic", episodic,
                        "semantic", semantic,
                        "procedural", procedural,
                        "preference", preference,
                        "other", other
                    ),
                    "timestamp", Instant.now().toString()
                ));
            })
            .then(Promise::of, e -> {
                log.error("[agents] memory query failed for agentId={}: {}",
                    agentId, e.getMessage(), e);
                return Promise.of(HttpHelper.errorResponse(500,
                    "Failed to query agent memory: " + e.getMessage()));
            });
    }

    public Promise<HttpResponse> handleGetAgentEpisodes(HttpRequest request) {
        String agentId = request.getPathParameter("agentId");
        if (agentId == null || agentId.isBlank()) {
            return Promise.of(HttpHelper.errorResponse(400,
                "agentId path parameter is required"));
        }
        if (agentDataCloud == null) {
            return Promise.of(HttpHelper.errorResponse(503,
                "Episode store not available — DataCloudClient not configured"));
        }
        String tenantId = HttpHelper.resolveTenantId(request);
        String limitParam = request.getQueryParameter("limit");
        int limit = limitParam != null ? Math.min(Integer.parseInt(limitParam), 500) : 50;
        DataCloudClient.Query query = DataCloudClient.Query.builder()
            .filters(List.of(
                DataCloudClient.Filter.eq("agentId", agentId),
                DataCloudClient.Filter.eq("type", "EPISODIC")
            ))
            .limit(limit)
            .build();
        return agentDataCloud.query(tenantId, "dc_memory", query)
            .map(items -> {
                var episodes = items.stream()
                    .map(e -> {
                        Map<String, Object> ep = new java.util.HashMap<>(e.data());
                        ep.put("id", e.id());
                        return ep;
                    })
                    .toList();
                return HttpHelper.jsonResponse(Map.of(
                    "agentId", agentId,
                    "tenantId", tenantId,
                    "episodes", episodes,
                    "count", episodes.size(),
                    "timestamp", Instant.now().toString()
                ));
            })
            .then(Promise::of, e -> {
                log.error("[agents] episodes query failed for agentId={}: {}",
                    agentId, e.getMessage(), e);
                return Promise.of(HttpHelper.errorResponse(500,
                    "Failed to query agent episodes: " + e.getMessage()));
            });
    }

    public Promise<HttpResponse> handleGetAgentFacts(HttpRequest request) {
        String agentId = request.getPathParameter("agentId");
        if (agentId == null || agentId.isBlank()) {
            return Promise.of(HttpHelper.errorResponse(400,
                "agentId path parameter is required"));
        }
        if (agentDataCloud == null) {
            return Promise.of(HttpHelper.errorResponse(503,
                "Fact store not available — DataCloudClient not configured"));
        }
        String tenantId = HttpHelper.resolveTenantId(request);
        String limitParam = request.getQueryParameter("limit");
        int limit = limitParam != null ? Math.min(Integer.parseInt(limitParam), 500) : 100;
        DataCloudClient.Query query = DataCloudClient.Query.builder()
            .filters(List.of(
                DataCloudClient.Filter.eq("agentId", agentId),
                DataCloudClient.Filter.eq("type", "SEMANTIC")
            ))
            .limit(limit)
            .build();
        return agentDataCloud.query(tenantId, "dc_memory", query)
            .map(items -> {
                var facts = items.stream()
                    .map(e -> {
                        Map<String, Object> fact = new java.util.HashMap<>(e.data());
                        fact.put("id", e.id());
                        return fact;
                    })
                    .toList();
                return HttpHelper.jsonResponse(Map.of(
                    "agentId", agentId,
                    "tenantId", tenantId,
                    "facts", facts,
                    "count", facts.size(),
                    "timestamp", Instant.now().toString()
                ));
            })
            .then(Promise::of, e -> {
                log.error("[agents] facts query failed for agentId={}: {}",
                    agentId, e.getMessage(), e);
                return Promise.of(HttpHelper.errorResponse(500,
                    "Failed to query agent facts: " + e.getMessage()));
            });
    }

    public Promise<HttpResponse> handleGetAgentPolicies(HttpRequest request) {
        String agentId = request.getPathParameter("agentId");
        if (agentId == null || agentId.isBlank()) {
            return Promise.of(HttpHelper.errorResponse(400,
                "agentId path parameter is required"));
        }
        if (agentDataCloud == null) {
            return Promise.of(HttpHelper.errorResponse(503,
                "Policy store not available — DataCloudClient not configured"));
        }
        String tenantId = HttpHelper.resolveTenantId(request);
        String limitParam = request.getQueryParameter("limit");
        int limit = limitParam != null ? Math.min(Integer.parseInt(limitParam), 200) : 50;
        DataCloudClient.Query query = DataCloudClient.Query.builder()
            .filters(List.of(
                DataCloudClient.Filter.eq("agentId", agentId),
                DataCloudClient.Filter.eq("type", "PROCEDURAL")
            ))
            .limit(limit)
            .build();
        return agentDataCloud.query(tenantId, "dc_memory", query)
            .map(items -> {
                var policies = items.stream()
                    .map(e -> {
                        Map<String, Object> policy = new java.util.HashMap<>(e.data());
                        policy.put("id", e.id());
                        return policy;
                    })
                    .toList();
                return HttpHelper.jsonResponse(Map.of(
                    "agentId", agentId,
                    "tenantId", tenantId,
                    "policies", policies,
                    "count", policies.size(),
                    "timestamp", Instant.now().toString()
                ));
            })
            .then(Promise::of, e -> {
                log.error("[agents] policies query failed for agentId={}: {}",
                    agentId, e.getMessage(), e);
                return Promise.of(HttpHelper.errorResponse(500,
                    "Failed to query agent policies: " + e.getMessage()));
            });
    }

    public Promise<HttpResponse> handleDeregisterAgent(HttpRequest request) {
        String agentId = request.getPathParameter("agentId");
        if (agentId == null || agentId.isBlank()) {
            return Promise.of(HttpHelper.errorResponse(400,
                "agentId path parameter is required"));
        }
        if (agentStore == null) {
            return Promise.of(HttpHelper.errorResponse(503,
                "Agent registry not available — DataCloudClient not configured"));
        }
        String tenantId = HttpHelper.resolveTenantId(request);
        return agentStore.findById(tenantId, agentId)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.of(HttpHelper.errorResponse(404,
                        "Agent not found: " + agentId));
                }
                return agentStore.delete(tenantId, agentId)
                    .map(ignored -> HttpHelper.jsonResponse(Map.of(
                        "deleted", true,
                        "agentId", agentId,
                        "tenantId", tenantId,
                        "timestamp", Instant.now().toString()
                    )));
            })
            .then(Promise::of, e -> {
                log.error("[agents] deregister failed for agentId={}: {}",
                    agentId, e.getMessage(), e);
                return Promise.of(HttpHelper.errorResponse(500,
                    "Failed to deregister agent: " + e.getMessage()));
            });
    }
}
