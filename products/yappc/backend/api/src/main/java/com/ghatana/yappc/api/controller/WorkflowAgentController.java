/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module - Workflow Agent Controller
 */
package com.ghatana.yappc.api.controller;

import com.ghatana.agent.workflow.*;
import com.ghatana.core.activej.promise.PromiseUtils;
import com.ghatana.yappc.api.common.ApiResponse;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST controller for workflow agent execution endpoints.
 *
 * <p>Provides API access to the workflow agent system for:
 * <ul>
 *   <li>Executing agent tasks on work items</li>
 *   <li>Batch execution of multiple agent requests</li>
 *   <li>Cancelling pending executions</li>
 *   <li>Querying execution status and agent health</li>
 * </ul>
 *
 * <p><b>API Endpoints:</b>
 * <pre>
 * POST   /api/agents/execute       - Execute a single agent task
 * POST   /api/agents/execute/batch - Execute multiple agent tasks
 * DELETE /api/agents/execute/:id   - Cancel an execution
 * GET    /api/agents/execute/:id   - Get execution status
 * GET    /api/agents              - List all registered agents
 * GET    /api/agents/role/:role   - Get agents by role
 * GET    /api/agents/:id/health   - Get agent health info
 * </pre>
 *
 * @doc.type class
 * @doc.purpose HTTP endpoints for workflow agent execution
 * @doc.layer api
 * @doc.pattern Controller
 */
public class WorkflowAgentController {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowAgentController.class);

    private final WorkflowAgentService agentService;
    private final WorkflowAgentRegistry agentRegistry;

    /**
     * Constructs controller with required dependencies.
     *
     * @param agentService the workflow agent service for execution
     * @param agentRegistry the agent registry for lookup
     */
    public WorkflowAgentController(
            WorkflowAgentService agentService,
            WorkflowAgentRegistry agentRegistry) {
        this.agentService = agentService;
        this.agentRegistry = agentRegistry;
    }

    /**
     * POST /api/agents/execute - Execute a single agent task.
     *
     * <p>Request body:
     * <pre>
     * {
     *   "agentId": "agent-123",
     *   "role": "code-reviewer",
     *   "itemId": "item-456",
     *   "input": { "prompt": "Review this code", ... },
     *   "priority": "normal"
     * }
     * </pre>
     *
     * @param request the HTTP request containing execution parameters
     * @return Promise of execution result
     */
    public Promise<HttpResponse> executeAgent(HttpRequest request) {
        logger.debug("POST /api/agents/execute");

        return request.loadBody()
            .then(body -> {
                try {
                    // Parse request body
                    Map<String, Object> requestBody = parseJsonBody(body.getString(StandardCharsets.UTF_8));
                    
                    String agentId = (String) requestBody.get("agentId");
                    String roleCode = (String) requestBody.get("role");
                    String itemId = (String) requestBody.get("itemId");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> input = (Map<String, Object>) requestBody.getOrDefault("input", Map.of());
                    String priorityStr = (String) requestBody.getOrDefault("priority", "medium");
                    
                    if (agentId == null || roleCode == null || itemId == null) {
                        return Promise.of(ApiResponse.badRequest("Missing required fields: agentId, role, itemId"));
                    }
                    
                    WorkflowAgentRole role;
                    try {
                        role = WorkflowAgentRole.fromCode(roleCode);
                    } catch (IllegalArgumentException e) {
                        return Promise.of(ApiResponse.badRequest("Invalid role: " + roleCode));
                    }
                    
                    WorkflowAgentRequest.Priority priority;
                    try {
                        priority = WorkflowAgentRequest.Priority.valueOf(priorityStr.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        priority = WorkflowAgentRequest.Priority.MEDIUM;
                    }
                    
                    // Build execution request
                    WorkflowAgentRequest agentRequest = WorkflowAgentRequest.builder(agentId, role)
                        .itemId(itemId)
                        .input(input)
                        .priority(priority)
                        .context(WorkflowAgentRequest.ExecutionContext.system("default"))
                        .build();
                    
                    // Execute and return result
                    return agentService.execute(agentRequest)
                        .map(result -> ApiResponse.ok(toResponseMap(result)));
                        
                } catch (Exception e) {
                    logger.error("Error executing agent", e);
                    return Promise.of(ApiResponse.badRequest("Invalid request: " + e.getMessage()));
                }
            });
    }

    /**
     * POST /api/agents/execute/batch - Execute multiple agent tasks.
     *
     * @param request the HTTP request containing batch execution parameters
     * @return Promise of batch execution results
     */
    public Promise<HttpResponse> executeBatch(HttpRequest request) {
        logger.debug("POST /api/agents/execute/batch");

        return request.loadBody()
            .then(body -> {
                try {
                    Map<String, Object> requestBody = parseJsonBody(body.getString(StandardCharsets.UTF_8));
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> requests = (List<Map<String, Object>>) requestBody.get("requests");
                    
                    if (requests == null || requests.isEmpty()) {
                        return Promise.of(ApiResponse.badRequest("Missing or empty requests array"));
                    }
                    
                    List<WorkflowAgentRequest> agentRequests = new ArrayList<>();
                    for (Map<String, Object> req : requests) {
                        String agentId = (String) req.get("agentId");
                        String roleCode = (String) req.get("role");
                        String itemId = (String) req.get("itemId");
                        @SuppressWarnings("unchecked")
                        Map<String, Object> input = (Map<String, Object>) req.getOrDefault("input", Map.of());
                        
                        if (agentId == null || roleCode == null || itemId == null) {
                            continue; // Skip invalid entries
                        }
                        
                        try {
                            WorkflowAgentRole role = WorkflowAgentRole.fromCode(roleCode);
                            agentRequests.add(WorkflowAgentRequest.builder(agentId, role)
                                .itemId(itemId)
                                .input(input)
                                .context(WorkflowAgentRequest.ExecutionContext.system("default"))
                                .build());
                        } catch (IllegalArgumentException e) {
                            logger.warn("Skipping invalid role in batch: {}", roleCode);
                        }
                    }
                    
                    if (agentRequests.isEmpty()) {
                        return Promise.of(ApiResponse.badRequest("No valid requests in batch"));
                    }
                    
                    return agentService.executeBatch(agentRequests)
                        .map(results -> {
                            List<Map<String, Object>> resultMaps = results.stream()
                                .map(this::toResponseMap)
                                .toList();
                            return ApiResponse.ok(Map.of("results", resultMaps));
                        });
                        
                } catch (Exception e) {
                    logger.error("Error executing batch", e);
                    return Promise.of(ApiResponse.badRequest("Invalid request: " + e.getMessage()));
                }
            });
    }

    /**
     * DELETE /api/agents/execute/:id - Cancel an execution.
     *
     * @param request the HTTP request with execution ID path parameter
     * @return Promise of cancellation result
     */
    public Promise<HttpResponse> cancelExecution(HttpRequest request) {
        String requestId = request.getPathParameter("id");
        logger.debug("DELETE /api/agents/execute/{}", requestId);

        return agentService.cancel(requestId)
            .map(cancelled -> {
                if (cancelled) {
                    return ApiResponse.ok(Map.of(
                        "cancelled", true,
                        "requestId", requestId
                    ));
                } else {
                    return ApiResponse.notFound("Execution not found or already completed: " + requestId);
                }
            });
    }

    /**
     * GET /api/agents/execute/:id - Get execution status.
     *
     * @param request the HTTP request with execution ID path parameter
     * @return Promise of execution status
     */
    public Promise<HttpResponse> getExecutionStatus(HttpRequest request) {
        String requestId = request.getPathParameter("id");
        logger.debug("GET /api/agents/execute/{}", requestId);

        return agentService.getStatus(requestId)
            .map(status -> ApiResponse.ok(Map.of(
                "requestId", requestId,
                "status", status.name()
            )));
    }

    /**
     * GET /api/agents - List all registered agents.
     *
     * @param request the HTTP request
     * @return Promise of agent list
     */
    public Promise<HttpResponse> listAgents(HttpRequest request) {
        logger.debug("GET /api/agents");

        List<Promise<List<Map<String, Object>>>> rolePromises = new ArrayList<>();
        
        // Get agents for each role
        for (WorkflowAgentRole role : WorkflowAgentRole.values()) {
            Promise<List<Map<String, Object>>> roleAgents = agentRegistry.getAgentsByRole(role)
                .then(agentIds -> {
                    List<Promise<Map<String, Object>>> agentPromises = new ArrayList<>();
                    for (String agentId : agentIds) {
                        Promise<Map<String, Object>> agentPromise = agentRegistry.getAgentMetadata(agentId)
                            .map(optMetadata -> optMetadata
                                .map(metadata -> toAgentMap(agentId, metadata))
                                .orElse(Collections.emptyMap()));
                        agentPromises.add(agentPromise);
                    }
                    return PromiseUtils.all(agentPromises);
                });
            rolePromises.add(roleAgents);
        }

        return PromiseUtils.all(rolePromises)
            .map(listsOfLists -> {
                List<Map<String, Object>> agentList = new ArrayList<>();
                for (List<Map<String, Object>> list : listsOfLists) {
                    for (Map<String, Object> agent : list) {
                        if (!agent.isEmpty()) {
                            agentList.add(agent);
                        }
                    }
                }
                return ApiResponse.ok(Map.of("agents", agentList));
            });
    }

    /**
     * GET /api/agents/role/:role - Get agents by role.
     *
     * @param request the HTTP request with role path parameter
     * @return Promise of agents for the specified role
     */
    public Promise<HttpResponse> getAgentsByRole(HttpRequest request) {
        String roleCode = request.getPathParameter("role");
        logger.debug("GET /api/agents/role/{}", roleCode);

        WorkflowAgentRole role;
        try {
            role = WorkflowAgentRole.fromCode(roleCode);
        } catch (IllegalArgumentException e) {
            return Promise.of(ApiResponse.badRequest("Invalid role: " + roleCode));
        }

        return agentService.getAgentsForRole(role)
            .then(agentIds -> {
                List<Promise<Map<String, Object>>> agentPromises = new ArrayList<>();
                for (String agentId : agentIds) {
                    Promise<Map<String, Object>> agentPromise = agentRegistry.getAgentMetadata(agentId)
                        .map(optMetadata -> optMetadata
                            .map(metadata -> toAgentMap(agentId, metadata))
                            .orElse(Collections.emptyMap()));
                    agentPromises.add(agentPromise);
                }
                return PromiseUtils.all(agentPromises)
                    .map(agentMaps -> {
                        List<Map<String, Object>> agents = agentMaps.stream()
                            .filter(map -> !map.isEmpty())
                            .toList();
                        return ApiResponse.ok(Map.of(
                            "role", roleCode,
                            "agents", agents
                        ));
                    });
            });
    }

    /**
     * GET /api/agents/:id/health - Get agent health info.
     *
     * @param request the HTTP request with agent ID path parameter
     * @return Promise of agent health information
     */
    public Promise<HttpResponse> getAgentHealth(HttpRequest request) {
        String agentId = request.getPathParameter("id");
        logger.debug("GET /api/agents/{}/health", agentId);

        return agentService.getAgentHealth(agentId)
            .map(healthInfo -> ApiResponse.ok(Map.of(
                "agentId", agentId,
                "healthy", healthInfo.healthy(),
                "lastExecutionTime", healthInfo.lastExecutionTime(),
                "successRate", healthInfo.successRate(),
                "avgResponseTimeMs", healthInfo.avgResponseTimeMs()
            )));
    }

    // ========== Helper Methods ==========

    /**
     * Parses JSON string to map.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonBody(String json) {
        // Use existing JsonUtils or Jackson for parsing
        // For now, using simple approach with available utilities
        try {
            return com.ghatana.yappc.api.common.JsonUtils.fromJson(json, Map.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Converts WorkflowAgentResult to response map.
     */
    private Map<String, Object> toResponseMap(WorkflowAgentResult result) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", result.id());
        map.put("requestId", result.requestId());
        map.put("agentId", result.agentId());
        map.put("status", result.status().name().toLowerCase());
        map.put("output", result.output());
        map.put("confidence", result.confidence());
        
        if (result.error() != null) {
            map.put("error", result.error());
        }
        
        if (result.metrics() != null) {
            map.put("metrics", Map.of(
                "durationMs", result.metrics().durationMs(),
                "tokensUsed", result.metrics().tokensUsed(),
                "cost", result.metrics().cost()
            ));
        }
        
        map.put("startedAt", result.startedAt().toString());
        if (result.completedAt() != null) {
            map.put("completedAt", result.completedAt().toString());
        }
        
        return map;
    }

    /**
     * Converts agent metadata to response map.
     */
    private Map<String, Object> toAgentMap(String agentId, WorkflowAgentRegistry.AgentMetadata metadata) {
        return Map.of(
            "id", agentId,
            "name", metadata.name(),
            "role", metadata.role().getCode(),
            "enabled", metadata.enabled(),
            "registeredAt", metadata.registeredAt()
        );
    }
}
