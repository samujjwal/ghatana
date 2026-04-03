/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.engine.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.aep.engine.registry.AepCentralRegistryService;
import com.ghatana.aep.engine.registry.AgentExecutionService;
import com.ghatana.aep.engine.registry.AgentInfo;
import io.activej.http.*;
import io.activej.promise.Promise;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unified agent registry HTTP controller for AEP runtime.
 *
 * <p>Provides the single centralized API for agent discovery, execution, and lifecycle
 * management. All agent operations across all products (YAPPC, DataCloud, etc.) route
 * through this controller.
 *
 * <h2>Unified Endpoint Schema</h2>
 * <table border="1">
 *   <caption>Agent Registry HTTP API</caption>
 *   <tr><th>Method</th><th>Path</th><th>Purpose</th><th>Auth</th></tr>
 *   <tr><td>GET</td><td>/api/v1/agents</td><td>List all registered agents</td><td>user</td></tr>
 *   <tr><td>GET</td><td>/api/v1/agents/:agentId</td><td>Get agent metadata</td><td>user</td></tr>
 *   <tr><td>POST</td><td>/api/v1/agents/:agentId/execute</td><td>Execute agent</td><td>user</td></tr>
 *   <tr><td>GET</td><td>/api/v1/agents/:agentId/health</td><td>Check agent health status</td><td>user</td></tr>
 *   <tr><td>GET</td><td>/api/v1/agents/:agentId/history</td><td>Execution history (last 100)</td><td>user</td></tr>
 *   <tr><td>GET</td><td>/api/v1/agents/:agentId/memory</td><td>Agent memory (episodic/semantic)</td><td>user</td></tr>
 *   <tr><td>DELETE</td><td>/api/v1/agents/:agentId</td><td>Deregister agent</td><td>admin</td></tr>
 * </table>
 *
 * <h2>Migration Note</h2>
 * <p>As of v2.5, this controller replaces product-specific registry handlers
 * (e.g., DataCloud's AgentRegistryHandler, YAPPC's WorkflowAgentController).
 * All products now use this unified API.
 *
 * @doc.type class
 * @doc.purpose Unified HTTP controller for agent registry, discovery, and execution
 * @doc.layer engine
 * @doc.pattern Controller, Facade
 *
 * @author Ghatana AI Platform
 * @since 2.5.0
 */
public final class AepAgentRegistryController {

    private static final Logger log = LoggerFactory.getLogger(AepAgentRegistryController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AepCentralRegistryService registryService;
    private final AgentExecutionService executionService;
    private final HttpHandlerUtils httpUtils;

    /**
     * Constructs the unified agent registry controller.
     *
     * @param registryService    central registry service (materialized agents + persistence)
     * @param executionService   agent execution engine
     * @param httpUtils          HTTP response/request utilities
     */
    public AepAgentRegistryController(
            @NotNull AepCentralRegistryService registryService,
            @NotNull AgentExecutionService executionService,
            @NotNull HttpHandlerUtils httpUtils) {
        this.registryService = Objects.requireNonNull(registryService, "registryService");
        this.executionService = Objects.requireNonNull(executionService, "executionService");
        this.httpUtils = Objects.requireNonNull(httpUtils, "httpUtils");
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // LISTING & DISCOVERY
    // ═════════════════════════════════════════════════════════════════════════════

    /**
     * Lists all registered agents.
     *
     * <p><strong>Endpoint:</strong> {@code GET /api/v1/agents}
     *
     * <p><strong>Response:</strong> 200 with array of agent metadata.
     *
     * <p><strong>Query Parameters:</strong>
     * <ul>
     *   <li>{@code capability} — filter by agent capability (optional)</li>
     *   <li>{@code type} — filter by agent type (DETERMINISTIC, PROBABILISTIC, etc.)</li>
     *   <li>{@code product} — filter by owning product (yappc, data-cloud, etc.)</li>
     * </ul>
     *
     * @param request HTTP request
     * @return Promise resolving to HTTP 200 with agent list
     */
    @NotNull
    public Promise<HttpResponse> listAgents(@NotNull HttpRequest request) {
        Objects.requireNonNull(request, "request");

        return registryService
                .listAll()
                .map(agents -> {
                    // Apply optional filters
                    List<AgentInfo> filtered = agents;

                    String capabilityFilter = request.getQueryParameter("capability");
                    if (capabilityFilter != null && !capabilityFilter.isBlank()) {
                        filtered = filtered.stream()
                                .filter(a -> a.capabilities().contains(capabilityFilter))
                                .collect(Collectors.toList());
                    }

                    String typeFilter = request.getQueryParameter("type");
                    if (typeFilter != null && !typeFilter.isBlank()) {
                        filtered = filtered.stream()
                                .filter(a -> typeFilter.equals(a.type()))
                                .collect(Collectors.toList());
                    }

                    String productFilter = request.getQueryParameter("product");
                    if (productFilter != null && !productFilter.isBlank()) {
                        filtered = filtered.stream()
                                .filter(a -> productFilter.equals(a.product()))
                                .collect(Collectors.toList());
                    }

                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put(
                            "agents",
                            filtered.stream().map(this::toAgentSummary).collect(Collectors.toList()));
                    response.put("total", filtered.size());
                    response.put("timestamp", Instant.now().toString());

                    return httpUtils.jsonResponse(200, response);
                })
                .whenException(e -> log.error("Failed to list agents: {}", e.getMessage()));
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // AGENT DETAILS & METADATA
    // ═════════════════════════════════════════════════════════════════════════════

    /**
     * Gets detailed metadata for a specific agent.
     *
     * <p><strong>Endpoint:</strong> {@code GET /api/v1/agents/:agentId}
     *
     * <p><strong>Response:</strong> 200 with agent metadata, 404 if not found.
     *
     * @param request HTTP request with path parameter {@code agentId}
     * @return Promise resolving to HTTP 200/404
     */
    @NotNull
    public Promise<HttpResponse> getAgent(@NotNull HttpRequest request) {
        String agentId = request.getPathParameter("agentId");
        if (agentId == null || agentId.isBlank()) {
            return Promise.of(httpUtils.errorResponse(400, "agentId path parameter required"));
        }

        return registryService
                .resolve(agentId)
                .map(optAgent -> optAgent.map(agent -> {
                            Map<String, Object> response = new LinkedHashMap<>();
                            response.put("id", agent.id());
                            response.put("name", agent.name());
                            response.put("version", agent.version());
                            response.put("type", agent.type());
                            response.put("product", agent.product());
                            response.put("capabilities", agent.capabilities());
                            response.put("description", agent.description());
                            response.put("config", agent.config());
                            response.put("registeredAt", agent.registeredAt());
                            response.put("status", agent.status()); // live, dormant, error

                            return httpUtils.jsonResponse(200, response);
                        })
                        .orElseGet(() -> httpUtils.errorResponse(404, "Agent not found: " + agentId)))
                .whenException(e -> log.error("Failed to get agent [{}]: {}", agentId, e.getMessage()));
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // EXECUTION
    // ═════════════════════════════════════════════════════════════════════════════

    /**
     * Executes an agent with the given input.
     *
     * <p><strong>Endpoint:</strong> {@code POST /api/v1/agents/:agentId/execute}
     *
     * <p><strong>Request Body:</strong>
     * <pre>
     * {
     *   "input": { ... agent-specific input ... }
     * }
     * </pre>
     *
     * <p><strong>Response:</strong> 200 with execution result.
     *
     * @param request HTTP request with path parameter {@code agentId} and body
     * @return Promise resolving to HTTP 200/400/404/500
     */
    @NotNull
    public Promise<HttpResponse> executeAgent(@NotNull HttpRequest request) {
        String agentId = request.getPathParameter("agentId");
        if (agentId == null || agentId.isBlank()) {
            return Promise.of(httpUtils.errorResponse(400, "agentId path parameter required"));
        }

        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = MAPPER.readValue(body, Map.class);

                Object input = payload.get("input");
                if (input == null) {
                    return Promise.of(httpUtils.errorResponse(400, "input field required in request body"));
                }

                return executionService
                        .execute(agentId, input)
                        .map(result -> {
                            Map<String, Object> response = new LinkedHashMap<>();
                            response.put("agentId", agentId);
                            response.put("executionId", result.executionId());
                            response.put("status", result.status()); // success, error, timeout
                            response.put("output", result.output());
                            response.put("duration", result.durationMs());
                            response.put("executedAt", Instant.now().toString());

                            return httpUtils.jsonResponse(200, response);
                        })
                        .whenException(e -> log.warn("Agent [{}] execution failed: {}", agentId, e.getMessage()));
            } catch (Exception e) {
                log.warn("Invalid execute request for agent [{}]: {}", agentId, e.getMessage());
                return Promise.of(httpUtils.errorResponse(400, "Invalid request format: " + e.getMessage()));
            }
        });
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // HEALTH & STATUS
    // ═════════════════════════════════════════════════════════════════════════════

    /**
     * Checks the health status of an agent.
     *
     * <p><strong>Endpoint:</strong> {@code GET /api/v1/agents/:agentId/health}
     *
     * <p><strong>Response:</strong> 200 with health status (OK, DEGRADED, ERROR).
     *
     * @param request HTTP request with path parameter {@code agentId}
     * @return Promise resolving to HTTP 200/404
     */
    @NotNull
    public Promise<HttpResponse> getAgentHealth(@NotNull HttpRequest request) {
        String agentId = request.getPathParameter("agentId");
        if (agentId == null || agentId.isBlank()) {
            return Promise.of(httpUtils.errorResponse(400, "agentId path parameter required"));
        }

        return registryService
                .resolve(agentId)
                .then(optAgent -> {
                    if (optAgent.isEmpty()) {
                        return Promise.of(httpUtils.errorResponse(404, "Agent not found: " + agentId));
                    }

                    return executionService.checkHealth(agentId).map(health -> {
                        Map<String, Object> response = new LinkedHashMap<>();
                        response.put("agentId", agentId);
                        response.put("status", health.status()); // OK, DEGRADED, ERROR
                        response.put("uptime", health.uptimeMs());
                        response.put("lastExecution", health.lastExecutionTime());
                        response.put("failureRate", health.failureRate());
                        response.put("timestamp", Instant.now().toString());

                        return httpUtils.jsonResponse(200, response);
                    });
                })
                .whenException(e -> log.error("Failed to check health for agent [{}]: {}", agentId, e.getMessage()));
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // EXECUTION HISTORY
    // ═════════════════════════════════════════════════════════════════════════════

    /**
     * Gets execution history for an agent.
     *
     * <p><strong>Endpoint:</strong> {@code GET /api/v1/agents/:agentId/history}
     *
     * <p><strong>Query Parameters:</strong>
     * <ul>
     *   <li>{@code limit} — max number of entries (default 100)</li>
     *   <li>{@code status} — filter by execution status (success, error, timeout)</li>
     * </ul>
     *
     * <p><strong>Response:</strong> 200 with array of execution records.
     *
     * @param request HTTP request
     * @return Promise resolving to HTTP 200
     */
    @NotNull
    public Promise<HttpResponse> getExecutionHistory(@NotNull HttpRequest request) {
        String agentId = request.getPathParameter("agentId");
        if (agentId == null || agentId.isBlank()) {
            return Promise.of(httpUtils.errorResponse(400, "agentId path parameter required"));
        }

        String limitParam = request.getQueryParameter("limit");
        final int limit;
        if (limitParam != null) {
            int parsed = 100;
            try {
                parsed = Math.min(Integer.parseInt(limitParam), 1000); // Cap at 1000
            } catch (NumberFormatException e) {
                // Use default
            }
            limit = parsed;
        } else {
            limit = 100;
        }

        return registryService
                .resolve(agentId)
                .then(optAgent -> {
                    if (optAgent.isEmpty()) {
                        return Promise.of(httpUtils.errorResponse(404, "Agent not found: " + agentId));
                    }

                    return executionService.getHistory(agentId, limit).map(executions -> {
                        List<Map<String, Object>> historyList = executions.stream()
                                .map(exec -> Map.<String, Object>of(
                                        "executionId", exec.executionId(),
                                        "status", exec.status(),
                                        "input", exec.input(),
                                        "output", exec.output(),
                                        "duration", exec.durationMs(),
                                        "timestamp", exec.timestamp()))
                                .collect(Collectors.toList());

                        Map<String, Object> response = new LinkedHashMap<>();
                        response.put("agentId", agentId);
                        response.put("executions", historyList);
                        response.put("total", historyList.size());

                        return httpUtils.jsonResponse(200, response);
                    });
                })
                .whenException(e -> log.error("Failed to get history for agent [{}]: {}", agentId, e.getMessage()));
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // AGENT MEMORY
    // ═════════════════════════════════════════════════════════════════════════════

    /**
     * Gets agent memory state (episodic, semantic, procedural).
     *
     * <p><strong>Endpoint:</strong> {@code GET /api/v1/agents/:agentId/memory}
     *
     * <p><strong>Response:</strong> 200 with memory structure.
     *
     * @param request HTTP request with path parameter {@code agentId}
     * @return Promise resolving to HTTP 200/404
     */
    @NotNull
    public Promise<HttpResponse> getAgentMemory(@NotNull HttpRequest request) {
        String agentId = request.getPathParameter("agentId");
        if (agentId == null || agentId.isBlank()) {
            return Promise.of(httpUtils.errorResponse(400, "agentId path parameter required"));
        }

        return registryService
                .resolve(agentId)
                .then(optAgent -> {
                    if (optAgent.isEmpty()) {
                        return Promise.of(httpUtils.errorResponse(404, "Agent not found: " + agentId));
                    }

                    return executionService.getMemory(agentId).map(memory -> {
                        Map<String, Object> response = new LinkedHashMap<>();
                        response.put("agentId", agentId);
                        response.put("episodic", memory.episodic()); // Recent executions
                        response.put("semantic", memory.semantic()); // Learned facts
                        response.put("procedural", memory.procedural()); // Learned behaviors
                        response.put("lastUpdated", memory.lastUpdated());

                        return httpUtils.jsonResponse(200, response);
                    });
                })
                .whenException(e -> log.error("Failed to get memory for agent [{}]: {}", agentId, e.getMessage()));
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // DEREGISTRATION (ADMIN ONLY)
    // ═════════════════════════════════════════════════════════════════════════════

    /**
     * Deregisters (shuts down and removes) an agent.
     *
     * <p><strong>Endpoint:</strong> {@code DELETE /api/v1/agents/:agentId}
     *
     * <p><strong>Authorization:</strong> Admin role required.
     *
     * <p><strong>Response:</strong> 204 on success, 404 if not found, 403 if unauthorized.
     *
     * @param request HTTP request with path parameter {@code agentId}
     * @return Promise resolving to HTTP 204/403/404
     */
    @NotNull
    public Promise<HttpResponse> deregisterAgent(@NotNull HttpRequest request) {
        String agentId = request.getPathParameter("agentId");
        if (agentId == null || agentId.isBlank()) {
            return Promise.of(httpUtils.errorResponse(400, "agentId path parameter required"));
        }

        // TODO: Add authorization check (verify admin role)
        // if (!authService.hasRole(request, "ADMIN")) {
        //     return Promise.of(httpUtils.errorResponse(403, "Admin role required"));
        // }

        return registryService
                .resolve(agentId)
                .then(optAgent -> {
                    if (optAgent.isEmpty()) {
                        return Promise.of(httpUtils.errorResponse(404, "Agent not found: " + agentId));
                    }

                    return registryService.deregister(agentId).map(v -> {
                        log.info("Agent [{}] deregistered", agentId);
                        return HttpResponse.ofCode(204).build();
                    });
                })
                .whenException(e -> log.error("Failed to deregister agent [{}]: {}", agentId, e.getMessage()));
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ═════════════════════════════════════════════════════════════════════════════

    /**
     * Converts a full AgentInfo to a summary for list/search responses.
     */
    private Map<String, Object> toAgentSummary(@NotNull AgentInfo agent) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", agent.id());
        summary.put("name", agent.name());
        summary.put("type", agent.type());
        summary.put("product", agent.product());
        summary.put("capabilities", agent.capabilities());
        summary.put("status", agent.status());
        return summary;
    }
}
