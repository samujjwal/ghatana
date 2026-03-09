/*
 * Copyright (c) 2025 Ghatana Platform Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.products.yappc.domain.agent.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.http.server.response.ResponseBuilder;
import com.ghatana.products.yappc.domain.agent.*;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * HTTP controller for AI agent operations.
 * <p>
 * Exposes REST endpoints for:
 * <ul>
 *   <li>Agent discovery and listing</li>
 *   <li>Agent execution</li>
 *   <li>Agent health checks</li>
 *   <li>Agent metrics</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose HTTP controller for AI agent REST API
 * @doc.layer product
 * @doc.pattern Controller
 */
public final class AgentController {

    private static final Logger LOG = LoggerFactory.getLogger(AgentController.class);

    private static final Pattern AGENT_NAME_PATTERN =
            Pattern.compile("/api/v1/agents/([^/]+)(?:/.*)?");

    private final AgentRegistry registry;
    private final ObjectMapper objectMapper;

    /**
     * Creates a new AgentController.
     *
     * @param registry The agent registry
     * @param objectMapper JSON object mapper
     */
    public AgentController(
            @NotNull AgentRegistry registry,
            @NotNull ObjectMapper objectMapper
    ) {
        this.registry = Objects.requireNonNull(registry, "registry is required");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper is required");
    }

    // -------------------------------------------------------------------------
    // Agent Discovery Endpoints
    // -------------------------------------------------------------------------

    /**
     * GET /api/v1/agents
     * <p>
     * Lists all registered agents with their metadata.
     */
    public Promise<HttpResponse> listAgents(HttpRequest request) {
        LOG.debug("Listing all registered agents");

        List<AgentInfo> agentInfos = registry.getAllMetadata().stream()
                .map(this::toAgentInfo)
                .collect(Collectors.toList());

        return Promise.of(
                ResponseBuilder.ok()
                        .json(Map.of(
                                "agents", agentInfos,
                                "total", agentInfos.size()
                        ))
                        .build()
        );
    }

    /**
     * GET /api/v1/agents/:name
     * <p>
     * Gets detailed information about a specific agent.
     */
    public Promise<HttpResponse> getAgent(HttpRequest request) {
        Optional<String> agentNameOpt = extractAgentName(request.getPath());

        if (agentNameOpt.isEmpty()) {
            return Promise.of(badRequest("Agent name is required"));
        }

        String agentName = agentNameOpt.get();
        LOG.debug("Getting agent: {}", agentName);

        AgentName name = parseAgentName(agentName);
        if (name == null) {
            return Promise.of(notFound("Agent not found: " + agentName));
        }

        AIAgent<?, ?> agent = registry.get(name);
        if (agent == null) {
            return Promise.of(notFound("Agent not found: " + agentName));
        }

        return Promise.of(
            ResponseBuilder.ok()
                .json(toAgentDetail(agent))
                .build()
        );
    }

    /**
     * GET /api/v1/agents/:name/health
     * <p>
     * Gets health status of a specific agent.
     */
    public Promise<HttpResponse> getAgentHealth(HttpRequest request) {
        Optional<String> agentNameOpt = extractAgentName(request.getPath());

        if (agentNameOpt.isEmpty()) {
            return Promise.of(badRequest("Agent name is required"));
        }

        String agentName = agentNameOpt.get();
        LOG.debug("Getting health for agent: {}", agentName);

        AgentName name = parseAgentName(agentName);
        if (name == null) {
            return Promise.of(notFound("Agent not found: " + agentName));
        }

        AIAgent<?, ?> agent = registry.get(name);
        if (agent == null) {
            return Promise.of(notFound("Agent not found: " + agentName));
        }

        return agent.healthCheck()
            .map(health -> ResponseBuilder.ok()
                .json(toHealthResponse(name, health))
                .build());
    }

    /**
     * GET /api/v1/agents/health
     * <p>
     * Gets health status of all agents.
     */
    public Promise<HttpResponse> getAllAgentsHealth(HttpRequest request) {
        LOG.debug("Getting health for all agents");

        return registry.healthCheckAll()
            .map(healthMap -> {
                List<Map<String, Object>> healthList = healthMap.entrySet().stream()
                    .map(e -> toHealthResponse(e.getKey(), e.getValue()))
                    .collect(Collectors.toList());

                long healthy = healthList.stream()
                    .filter(h -> Boolean.TRUE.equals(h.get("healthy")))
                    .count();

                return ResponseBuilder.ok()
                    .json(Map.of(
                        "agents", healthList,
                        "total", healthList.size(),
                        "healthy", healthy,
                        "unhealthy", healthList.size() - healthy
                    ))
                    .build();
            });
    }

    // -------------------------------------------------------------------------
    // Agent Execution Endpoints
    // -------------------------------------------------------------------------

    /**
     * POST /api/v1/agents/:name/execute
     * <p>
     * Executes an agent with the provided input.
     */
    public Promise<HttpResponse> executeAgent(HttpRequest request) {
        Optional<String> agentNameOpt = extractAgentName(request.getPath());

        if (agentNameOpt.isEmpty()) {
            return Promise.of(badRequest("Agent name is required"));
        }

        String agentName = agentNameOpt.get();
        LOG.debug("Executing agent: {}", agentName);

        AgentName name = parseAgentName(agentName);
        if (name == null) {
            return Promise.of(notFound("Agent not found: " + agentName));
        }

        // Parse request body
        return request.loadBody().then(loadedBody -> {
            try {
                byte[] body = loadedBody.asArray();
                JsonNode inputNode = objectMapper.readTree(body);

                // Extract context from request
                AIAgentContext context = extractContext(request, inputNode);

                // Route to appropriate agent
                return executeByAgentName(name, inputNode, context);

            } catch (Exception e) {
                LOG.error("Failed to parse request body", e);
                return Promise.of(badRequest("Invalid request body: " + e.getMessage()));
            }
        });
    }

    /**
     * POST /api/v1/agents/copilot/chat
     * <p>
     * Specialized endpoint for copilot chat interactions.
     */
    public Promise<HttpResponse> copilotChat(HttpRequest request) {
        LOG.debug("Copilot chat request");

        return request.loadBody().then(loadedBody -> {
            try {
                byte[] body = loadedBody.asArray();
                JsonNode inputNode = objectMapper.readTree(body);

                AIAgentContext context = extractContext(request, inputNode);

                return executeCopilot(inputNode, context);

            } catch (Exception e) {
                LOG.error("Failed to process copilot chat", e);
                return Promise.of(badRequest("Invalid request: " + e.getMessage()));
            }
        });
    }

    /**
     * POST /api/v1/agents/search
     * <p>
     * Specialized endpoint for semantic search.
     */
    public Promise<HttpResponse> search(HttpRequest request) {
        LOG.debug("Search request");

        return request.loadBody().then(loadedBody -> {
            try {
                byte[] body = loadedBody.asArray();
                JsonNode inputNode = objectMapper.readTree(body);

                AIAgentContext context = extractContext(request, inputNode);

                return executeSearch(inputNode, context);

            } catch (Exception e) {
                LOG.error("Failed to process search", e);
                return Promise.of(badRequest("Invalid request: " + e.getMessage()));
            }
        });
    }

    /**
     * POST /api/v1/agents/predict
     * <p>
     * Specialized endpoint for predictions.
     */
    public Promise<HttpResponse> predict(HttpRequest request) {
        LOG.debug("Prediction request");

        return request.loadBody().then(loadedBody -> {
            try {
                byte[] body = loadedBody.asArray();
                JsonNode inputNode = objectMapper.readTree(body);

                AIAgentContext context = extractContext(request, inputNode);

                return executePrediction(inputNode, context);

            } catch (Exception e) {
                LOG.error("Failed to process prediction", e);
                return Promise.of(badRequest("Invalid request: " + e.getMessage()));
            }
        });
    }

    // -------------------------------------------------------------------------
    // Capability-Based Routing
    // -------------------------------------------------------------------------

    /**
     * GET /api/v1/agents/capabilities
     * <p>
     * Lists all available capabilities across agents.
     */
    public Promise<HttpResponse> listCapabilities(HttpRequest request) {
        LOG.debug("Listing all capabilities");

        Map<String, List<String>> capabilityToAgents = new HashMap<>();

        for (AgentMetadata metadata : registry.getAllMetadata()) {
            for (String capability : metadata.capabilities()) {
                capabilityToAgents
                        .computeIfAbsent(capability, k -> new ArrayList<>())
                        .add(metadata.name().getDisplayName());
            }
        }

        return Promise.of(
                ResponseBuilder.ok()
                        .json(Map.of(
                                "capabilities", capabilityToAgents,
                                "totalCapabilities", capabilityToAgents.size()
                        ))
                        .build()
        );
    }

    /**
     * GET /api/v1/agents/by-capability/:capability
     * <p>
     * Finds agents with a specific capability.
     */
    public Promise<HttpResponse> findByCapability(HttpRequest request) {
        String path = request.getPath();
        String capability = path.substring(path.lastIndexOf('/') + 1);

        LOG.debug("Finding agents with capability: {}", capability);

        List<AgentInfo> agentInfos = registry.findByCapability(capability).stream()
            .map(info -> {
                Optional<AgentMetadata> metadataOpt = registry.getMetadata(info.name());
                return metadataOpt.map(this::toAgentInfo)
                    .orElseGet(() -> new AgentInfo(
                        info.name().name(),
                        info.name().getDisplayName(),
                        info.version(),
                        info.description(),
                        info.capabilities(),
                        List.of()
                    ));
            })
            .collect(Collectors.toList());

        return Promise.of(
            ResponseBuilder.ok()
                .json(Map.of(
                    "capability", capability,
                    "agents", agentInfos,
                    "count", agentInfos.size()
                ))
                .build()
        );
    }

    // -------------------------------------------------------------------------
    // Private Helpers - Execution Routing
    // -------------------------------------------------------------------------

    private Promise<HttpResponse> executeByAgentName(
            AgentName name,
            JsonNode inputNode,
            AIAgentContext context
    ) {
        return switch (name) {
            case COPILOT_AGENT -> executeCopilot(inputNode, context);
            case QUERY_PARSER_AGENT -> executeQueryParser(inputNode, context);
            case PREDICTION_AGENT -> executePrediction(inputNode, context);
            case ANOMALY_DETECTOR_AGENT -> executeAnomalyDetector(inputNode, context);
            case CODE_GENERATOR_AGENT -> executeCodeGenerator(inputNode, context);
            case RECOMMENDATION_AGENT -> executeRecommendation(inputNode, context);
            case SEARCH_AGENT -> executeSearch(inputNode, context);
            case WORKFLOW_ROUTER_AGENT -> executeWorkflowRouter(inputNode, context);
            case SENTIMENT_AGENT -> executeSentiment(inputNode, context);
            case DOC_GENERATOR_AGENT -> executeDocGenerator(inputNode, context);
        };
    }

    private Promise<HttpResponse> executeCopilot(JsonNode inputNode, AIAgentContext context) {
        String query = Optional.ofNullable(getStringField(inputNode, "query"))
                .orElse(getStringField(inputNode, "message"));

        Map<String, Object> additionalContext = new HashMap<>(parseObjectMap(inputNode.get("additionalContext")));
        String workspaceId = getStringField(inputNode, "workspaceId");
        if (workspaceId != null) {
            additionalContext.put("workspaceId", workspaceId);
        }
        String itemId = getStringField(inputNode, "itemId");
        if (itemId != null) {
            additionalContext.put("itemId", itemId);
        }
        List<Map<String, Object>> conversationHistory = parseConversationHistoryRaw(inputNode);
        if (!conversationHistory.isEmpty()) {
            additionalContext.put("conversationHistory", conversationHistory);
        }

        CopilotInput input = new CopilotInput(
                Objects.requireNonNullElse(query, ""),
                getStringField(inputNode, "sessionId"),
                parseCopilotViewContext(inputNode),
                emptyToNull(parseStringList(inputNode, "selectedItems")),
                emptyToNull(parseCopilotRecentActions(inputNode)),
                additionalContext.isEmpty() ? null : additionalContext
        );

        return executeTypedAgent(AgentName.COPILOT_AGENT, input, context);
    }

    private Promise<HttpResponse> executeQueryParser(JsonNode inputNode, AIAgentContext context) {
        QueryParserInput input = QueryParserInput.builder()
                .query(getStringField(inputNode, "query"))
                .currentRoute(getStringField(inputNode, "currentRoute"))
                .persona(getStringField(inputNode, "persona"))
                .visibleItems(parseStringList(inputNode, "visibleItems"))
                .recentQueries(parseStringList(inputNode, "recentQueries"))
                .build();

        return executeTypedAgent(AgentName.QUERY_PARSER_AGENT, input, context);
    }

    private Promise<HttpResponse> executePrediction(JsonNode inputNode, AIAgentContext context) {
        PredictionInput input = PredictionInput.builder()
            .itemId(getStringField(inputNode, "itemId"))
            .currentPhase(getStringField(inputNode, "currentPhase"))
            .historicalData(parseHistoricalData(inputNode))
            .teamMetrics(parseTeamMetrics(inputNode))
            .similarItems(parseSimilarItems(inputNode))
            .horizonDays(getIntField(inputNode, "horizonDays", 30))
            .build();

        return executeTypedAgent(AgentName.PREDICTION_AGENT, input, context);
    }

    private Promise<HttpResponse> executeAnomalyDetector(JsonNode inputNode, AIAgentContext context) {
        AnomalyInput input = AnomalyInput.builder()
            .metricType(parseAnomalyMetricType(inputNode))
            .currentMetrics(parseCurrentMetrics(inputNode))
            .timeSeriesData(parseTimeSeriesMetrics(inputNode))
            .sensitivity(getDoubleFieldWithDefault(inputNode, "sensitivity", 0.8))
            .context(parseObjectMap(inputNode.get("context")))
            .build();

        return executeTypedAgent(AgentName.ANOMALY_DETECTOR_AGENT, input, context);
    }

    private Promise<HttpResponse> executeCodeGenerator(JsonNode inputNode, AIAgentContext context) {
        CodeGeneratorInput input = CodeGeneratorInput.builder()
                .itemId(getStringField(inputNode, "itemId"))
                .generationType(parseGenerationType(inputNode))
                .description(getStringField(inputNode, "description"))
                .language(getStringField(inputNode, "language"))
                .framework(getStringField(inputNode, "framework"))
                .requirements(emptyToNull(parseStringList(inputNode, "requirements")))
                .context(parseObjectMap(inputNode.get("context")))
                .build();

        return executeTypedAgent(AgentName.CODE_GENERATOR_AGENT, input, context);
    }

    private Promise<HttpResponse> executeRecommendation(JsonNode inputNode, AIAgentContext context) {
        RecommendationInput input = RecommendationInput.builder()
                .type(parseRecommendationType(inputNode))
                .userId(getStringField(inputNode, "userId"))
                .workspaceId(getStringField(inputNode, "workspaceId"))
                .itemId(getStringField(inputNode, "itemId"))
                .currentContext(getStringField(inputNode, "currentContext"))
                .recentActions(emptyToNull(parseStringList(inputNode, "recentActions")))
                .filters(parseObjectMap(inputNode.get("filters")))
                .maxResults(getIntField(inputNode, "maxResults", 5))
                .build();

        return executeTypedAgent(AgentName.RECOMMENDATION_AGENT, input, context);
    }

    private Promise<HttpResponse> executeSearch(JsonNode inputNode, AIAgentContext context) {
        SearchAgent.SearchInput input = SearchAgent.SearchInput.builder()
            .query(getStringField(inputNode, "query"))
            .workspaceId(getStringField(inputNode, "workspaceId"))
            .collections(emptyToNull(parseStringList(inputNode, "collections")))
            .filters(parseObjectMap(inputNode.get("filters")))
            .searchMode(parseSearchMode(inputNode))
            .limit(getIntField(inputNode, "limit", 20))
            .offset(getIntField(inputNode, "offset", 0))
            .build();

        return executeTypedAgent(AgentName.SEARCH_AGENT, input, context);
    }

    private Promise<HttpResponse> executeWorkflowRouter(JsonNode inputNode, AIAgentContext context) {
        WorkflowRouterAgent.RouterInput input = WorkflowRouterAgent.RouterInput.builder()
            .routingType(parseRoutingType(inputNode))
            .workflowId(getStringField(inputNode, "workflowId"))
            .currentStep(getStringField(inputNode, "currentStep"))
            .targetStep(getStringField(inputNode, "targetStep"))
            .intent(getStringField(inputNode, "intent"))
            .context(parseObjectMap(inputNode.get("context")))
            .constraints(parseObjectMap(inputNode.get("constraints")))
            .build();

        return executeTypedAgent(AgentName.WORKFLOW_ROUTER_AGENT, input, context);
    }

    private Promise<HttpResponse> executeSentiment(JsonNode inputNode, AIAgentContext context) {
        List<String> texts = parseStringList(inputNode, "texts");
        SentimentAgent.SentimentInput input = SentimentAgent.SentimentInput.builder()
            .text(getStringField(inputNode, "text"))
            .texts(emptyToNull(texts))
            .context(getStringField(inputNode, "context"))
            .includeEmotions(getBooleanField(inputNode, "includeEmotions", true))
            .includeKeywords(getBooleanField(inputNode, "includeKeywords", true))
            .build();

        return executeTypedAgent(AgentName.SENTIMENT_AGENT, input, context);
    }

    private Promise<HttpResponse> executeDocGenerator(JsonNode inputNode, AIAgentContext context) {
        DocGeneratorAgent.DocInput input = DocGeneratorAgent.DocInput.builder()
            .docType(parseDocType(inputNode))
            .title(getStringField(inputNode, "title"))
            .description(getStringField(inputNode, "description"))
            .context(parseObjectMap(inputNode.get("context")))
            .sourceCode(parseStringMap(inputNode.get("sourceCode")))
            .existingDocs(parseStringMap(inputNode.get("existingDocs")))
            .templateId(getStringField(inputNode, "templateId"))
            .language(getStringField(inputNode, "language"))
            .audience(getStringField(inputNode, "audience"))
            .build();

        return executeTypedAgent(AgentName.DOC_GENERATOR_AGENT, input, context);
    }

    @SuppressWarnings("unchecked")
    private <TIn, TOut> Promise<HttpResponse> executeTypedAgent(
            AgentName name,
            TIn input,
            AIAgentContext context
    ) {
        AIAgent<?, ?> rawAgent = registry.get(name);
        if (rawAgent == null) {
            return Promise.of(notFound("Agent not registered: " + name.getDisplayName()));
        }

        AIAgent<TIn, TOut> agent = (AIAgent<TIn, TOut>) rawAgent;
        return agent.execute(input, context)
                .map(result -> toExecuteResponse(name, result));
    }

    // -------------------------------------------------------------------------
    // Private Helpers - Response Building
    // -------------------------------------------------------------------------

    private AgentInfo toAgentInfo(AgentMetadata metadata) {
        return new AgentInfo(
                metadata.name().name(),
                metadata.name().getDisplayName(),
                metadata.version(),
                metadata.description(),
                metadata.capabilities(),
                metadata.supportedModels()
        );
    }

    private Map<String, Object> toAgentDetail(AIAgent<?, ?> agent) {
        AgentMetadata metadata = agent.getMetadata();
        return Map.of(
                "name", metadata.name().name(),
                "displayName", metadata.name().getDisplayName(),
                "version", metadata.version(),
                "description", metadata.description(),
                "capabilities", metadata.capabilities(),
                "supportedModels", metadata.supportedModels(),
                "latencySlaMs", metadata.latencySLA(),
                "isHealthy", true // NOTE: Assumes healthy — full health check via /agents/{name}/health endpoint
        );
    }

    private Map<String, Object> toHealthResponse(AgentName name, AgentHealth health) {
        return Map.of(
                "agent", name.getDisplayName(),
                "healthy", health.healthy(),
                "latencyMs", health.latencyMs(),
                "lastCheck", health.lastCheck().toString(),
                "dependencies", health.dependencies(),
                "errorMessage", health.errorMessage() != null ? health.errorMessage() : ""
        );
    }

    private <T> HttpResponse toExecuteResponse(AgentName name, AgentResult<T> result) {
        if (result.success()) {
            var metricsMap = new java.util.HashMap<String, Object>();
            metricsMap.put("latencyMs", result.metrics().latencyMs());
            metricsMap.put("tokensUsed", result.metrics().tokensUsed() != null ? result.metrics().tokensUsed() : 0);
            metricsMap.put("model", result.metrics().modelVersion());
            metricsMap.put("confidence", result.metrics().confidence() != null ? result.metrics().confidence() : 0.0);
            metricsMap.put("costUSD", result.metrics().costUSD() != null ? result.metrics().costUSD() : 0.0);

            return ResponseBuilder.ok()
                    .json(Map.of(
                            "agent", name.getDisplayName(),
                            "success", true,
                            "data", result.data(),
                            "metrics", metricsMap,
                            "trace", Map.of(
                        "requestId", result.trace().requestId(),
                        "timestamp", result.trace().timestamp().toString(),
                        "agentName", result.trace().agentName(),
                        "metadata", result.trace().metadata()
                            )
                    ))
                    .build();
        } else {
            return ResponseBuilder.internalServerError()
                    .json(Map.of(
                            "agent", name.getDisplayName(),
                            "success", false,
                            "error", Map.of(
                                    "code", result.error().code(),
                                    "message", result.error().message(),
                                    "retryable", result.error().retryable()
                            ),
                            "trace", Map.of(
                        "requestId", result.trace().requestId(),
                        "timestamp", result.trace().timestamp().toString(),
                        "agentName", result.trace().agentName(),
                        "metadata", result.trace().metadata()
                            )
                    ))
                    .build();
        }
    }

    // -------------------------------------------------------------------------
    // Private Helpers - Parsing
    // -------------------------------------------------------------------------

    private Optional<String> extractAgentName(String path) {
        Matcher matcher = AGENT_NAME_PATTERN.matcher(path);
        return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private AgentName parseAgentName(String name) {
        try {
            // Try exact enum match first
            return AgentName.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            // Try display name match
            for (AgentName agentName : AgentName.values()) {
                if (agentName.getDisplayName().equalsIgnoreCase(name) ||
                        agentName.name().equalsIgnoreCase(name.replace("-", "_"))) {
                    return agentName;
                }
            }
            return null;
        }
    }

    private AIAgentContext extractContext(HttpRequest request, JsonNode inputNode) {
        String userId = Optional.ofNullable(request.getHeader(HttpHeaders.of("X-User-ID"))).orElse("anonymous");
        String workspaceId = Optional.ofNullable(getStringField(inputNode, "workspaceId"))
            .orElse(Optional.ofNullable(request.getHeader(HttpHeaders.of("X-Workspace-ID"))).orElse("default"));
        String requestId = Optional.ofNullable(request.getHeader(HttpHeaders.of("X-Request-ID")))
            .orElse(UUID.randomUUID().toString());
        String tenantId = Optional.ofNullable(request.getHeader(HttpHeaders.of("X-Tenant-ID"))).orElse("default");
        String organizationId = Optional.ofNullable(request.getHeader(HttpHeaders.of("X-Organization-ID"))).orElse("default");

        Set<String> permissions = Optional.ofNullable(request.getHeader(HttpHeaders.of("X-Permissions")))
            .map(v -> Arrays.stream(v.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toUnmodifiableSet()))
            .orElse(Set.of());

        long timeoutMs = Optional.ofNullable(request.getHeader(HttpHeaders.of("X-Timeout-MS")))
            .map(v -> {
                try {
                return Long.parseLong(v);
                } catch (NumberFormatException e) {
                return AIAgentContext.DEFAULT_TIMEOUT;
                }
            })
            .orElse(AIAgentContext.DEFAULT_TIMEOUT);

        Map<String, Object> metadata = new HashMap<>();
        Optional.ofNullable(request.getHeader(HttpHeaders.of("X-Trace-ID"))).ifPresent(v -> metadata.put("traceId", v));
        Optional.ofNullable(request.getHeader(HttpHeaders.of("X-Span-ID"))).ifPresent(v -> metadata.put("spanId", v));

        return AIAgentContext.builder()
            .userId(userId)
            .workspaceId(workspaceId)
            .requestId(requestId)
            .tenantId(tenantId)
            .organizationId(organizationId)
            .permissions(permissions)
            .preferences(UserAIPreferences.defaults())
            .timeout(timeoutMs)
            .metadata(metadata)
            .build();
    }

    private String getStringField(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && !value.isNull() ? value.asText() : null;
    }

    private int getIntField(JsonNode node, String field, int defaultValue) {
        JsonNode value = node.get(field);
        return value != null && !value.isNull() ? value.asInt(defaultValue) : defaultValue;
    }

    private float getFloatField(JsonNode node, String field, float defaultValue) {
        JsonNode value = node.get(field);
        return value != null && !value.isNull() ? (float) value.asDouble(defaultValue) : defaultValue;
    }

    private double getDoubleField(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && !value.isNull() ? value.asDouble() : 0.0;
    }

    private boolean getBooleanField(JsonNode node, String field, boolean defaultValue) {
        JsonNode value = node.get(field);
        return value != null && !value.isNull() ? value.asBoolean(defaultValue) : defaultValue;
    }

    private List<String> parseStringList(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        value.forEach(item -> result.add(item.asText()));
        return result;
    }

    private List<Double> parseDoubleList(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isArray()) {
            return List.of();
        }
        List<Double> result = new ArrayList<>();
        value.forEach(item -> result.add(item.asDouble()));
        return result;
    }

    private List<Map<String, Object>> parseConversationHistoryRaw(JsonNode node) {
        JsonNode history = node.get("conversationHistory");
        if (history == null || !history.isArray()) {
            return List.of();
        }
        List<Map<String, Object>> messages = new ArrayList<>();
        history.forEach(msg -> messages.add(Map.of(
                "role", msg.hasNonNull("role") ? msg.get("role").asText() : "user",
                "content", msg.hasNonNull("content") ? msg.get("content").asText() : ""
        )));
        return messages;
    }

    private Map<String, Object> parseFilters(JsonNode node) {
        JsonNode filters = node.get("filters");
        if (filters == null || !filters.isObject()) {
            return Map.of();
        }
        Map<String, Object> result = new HashMap<>();
        filters.fields().forEachRemaining(entry ->
                result.put(entry.getKey(), entry.getValue().asText()));
        return result;
    }

    private Map<String, Object> parseFeatures(JsonNode node) {
        JsonNode features = node.get("features");
        if (features == null || !features.isObject()) {
            return Map.of();
        }
        Map<String, Object> result = new HashMap<>();
        features.fields().forEachRemaining(entry -> {
            JsonNode value = entry.getValue();
            if (value.isNumber()) {
                result.put(entry.getKey(), value.doubleValue());
            } else if (value.isBoolean()) {
                result.put(entry.getKey(), value.booleanValue());
            } else {
                result.put(entry.getKey(), value.asText());
            }
        });
        return result;
    }

    private Map<String, Object> parseContext(JsonNode node) {
        JsonNode context = node.get("context");
        if (context == null || !context.isObject()) {
            return Map.of();
        }
        Map<String, Object> result = new HashMap<>(parseObjectMap(context));
        result.put("_raw", context.toString());
        return result;
    }

    private Map<String, Object> parseWorkflowContext(JsonNode node) {
        JsonNode context = node.get("workflowContext");
        if (context == null || !context.isObject()) {
            return Map.of();
        }
        Map<String, Object> result = new HashMap<>();
        context.fields().forEachRemaining(entry ->
                result.put(entry.getKey(), entry.getValue().asText()));
        return result;
    }

    private SearchAgent.SearchMode parseSearchMode(JsonNode node) {
        String mode = getStringField(node, "searchMode");
        if (mode == null) {
            return SearchAgent.SearchMode.HYBRID;
        }
        try {
            return SearchAgent.SearchMode.valueOf(mode.toUpperCase());
        } catch (IllegalArgumentException e) {
            return SearchAgent.SearchMode.HYBRID;
        }
    }

    private CopilotInput.CopilotViewContext parseCopilotViewContext(JsonNode node) {
        JsonNode currentView = node.get("currentView");
        if (currentView != null && currentView.isObject()) {
            String route = currentView.hasNonNull("route") ? currentView.get("route").asText() : "/";
            String phaseId = currentView.hasNonNull("phaseId") ? currentView.get("phaseId").asText() : null;
            String viewMode = currentView.hasNonNull("viewMode") ? currentView.get("viewMode").asText() : null;
            Map<String, Object> filters = parseObjectMap(currentView.get("filters"));
            return new CopilotInput.CopilotViewContext(route, phaseId, viewMode, filters.isEmpty() ? null : filters);
        }

        String route = Optional.ofNullable(getStringField(node, "route"))
                .orElse(Optional.ofNullable(getStringField(node, "currentRoute")).orElse("/"));
        return CopilotInput.CopilotViewContext.of(route);
    }

    private List<CopilotInput.CopilotRecentAction> parseCopilotRecentActions(JsonNode node) {
        JsonNode actions = node.get("recentActions");
        if (actions == null || !actions.isArray()) {
            return List.of();
        }

        List<CopilotInput.CopilotRecentAction> result = new ArrayList<>();
        actions.forEach(action -> {
            if (!action.isObject()) {
                return;
            }
            String type = action.hasNonNull("type") ? action.get("type").asText() : "unknown";
            String description = action.hasNonNull("description") ? action.get("description").asText() : "";
            String timestamp = action.hasNonNull("timestamp") ? action.get("timestamp").asText() : String.valueOf(System.currentTimeMillis());
            Map<String, Object> details = parseObjectMap(action.get("details"));
            result.add(new CopilotInput.CopilotRecentAction(type, description, timestamp, details.isEmpty() ? null : details));
        });
        return result;
    }

    private List<PredictionInput.HistoricalDataPoint> parseHistoricalData(JsonNode node) {
        JsonNode data = node.get("historicalData");
        if (data == null || !data.isArray()) {
            return List.of();
        }
        List<PredictionInput.HistoricalDataPoint> result = new ArrayList<>();
        data.forEach(item -> {
            if (!item.isObject()) {
                return;
            }
            String timestamp = item.hasNonNull("timestamp") ? item.get("timestamp").asText() : String.valueOf(System.currentTimeMillis());
            String metric = item.hasNonNull("metric") ? item.get("metric").asText() : "metric";
            double value = item.hasNonNull("value") ? item.get("value").asDouble() : 0.0;
            Map<String, Object> attributes = parseObjectMap(item.get("attributes"));
            result.add(new PredictionInput.HistoricalDataPoint(timestamp, metric, value, attributes.isEmpty() ? null : attributes));
        });
        return result;
    }

    private PredictionInput.TeamMetrics parseTeamMetrics(JsonNode node) {
        JsonNode tm = node.get("teamMetrics");
        if (tm == null || !tm.isObject()) {
            return null;
        }
        return new PredictionInput.TeamMetrics(
                getIntField(tm, "teamSize", 0),
                tm.hasNonNull("averageVelocity") ? tm.get("averageVelocity").asDouble() : 0.0,
                tm.hasNonNull("capacityUtilization") ? tm.get("capacityUtilization").asDouble() : 0.0,
                getIntField(tm, "activeItems", 0),
                getIntField(tm, "blockedItems", 0)
        );
    }

    private List<PredictionInput.SimilarItem> parseSimilarItems(JsonNode node) {
        JsonNode items = node.get("similarItems");
        if (items == null || !items.isArray()) {
            return null;
        }
        List<PredictionInput.SimilarItem> result = new ArrayList<>();
        items.forEach(item -> {
            if (!item.isObject()) {
                return;
            }
            String itemId = item.hasNonNull("itemId") ? item.get("itemId").asText() : "";
            double similarity = item.hasNonNull("similarity") ? item.get("similarity").asDouble() : 0.0;
            String outcome = item.hasNonNull("outcome") ? item.get("outcome").asText() : "";
            int actualDuration = item.hasNonNull("actualDuration") ? item.get("actualDuration").asInt() : 0;
            List<String> phases = item.has("phases") && item.get("phases").isArray()
                    ? parseStringList(item, "phases")
                    : null;
            result.add(new PredictionInput.SimilarItem(itemId, similarity, outcome, actualDuration, phases));
        });
        return result;
    }

    private AnomalyInput.AnomalyMetricType parseAnomalyMetricType(JsonNode node) {
        String type = getStringField(node, "metricType");
        if (type == null) {
            return AnomalyInput.AnomalyMetricType.CUSTOM;
        }
        try {
            return AnomalyInput.AnomalyMetricType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return AnomalyInput.AnomalyMetricType.CUSTOM;
        }
    }

    private List<AnomalyInput.MetricDataPoint> parseCurrentMetrics(JsonNode node) {
        JsonNode metrics = node.get("currentMetrics");
        if (metrics != null && metrics.isArray()) {
            List<AnomalyInput.MetricDataPoint> result = new ArrayList<>();
            metrics.forEach(item -> {
                if (!item.isObject()) {
                    return;
                }
                String timestamp = item.hasNonNull("timestamp") ? item.get("timestamp").asText() : String.valueOf(System.currentTimeMillis());
                String metricName = item.hasNonNull("metricName") ? item.get("metricName").asText() : "metric";
                double value = item.hasNonNull("value") ? item.get("value").asDouble() : 0.0;
                result.add(new AnomalyInput.MetricDataPoint(timestamp, metricName, value, null));
            });
            return result;
        }

        String metricName = getStringField(node, "metricName");
        if (metricName == null) {
            return List.of();
        }
        return List.of(new AnomalyInput.MetricDataPoint(
                String.valueOf(System.currentTimeMillis()),
                metricName,
                getDoubleField(node, "currentValue"),
                null
        ));
    }

    private List<AnomalyInput.MetricDataPoint> parseTimeSeriesMetrics(JsonNode node) {
        JsonNode metrics = node.get("timeSeriesData");
        if (metrics != null && metrics.isArray()) {
            List<AnomalyInput.MetricDataPoint> result = new ArrayList<>();
            metrics.forEach(item -> {
                if (!item.isObject()) {
                    return;
                }
                String timestamp = item.hasNonNull("timestamp") ? item.get("timestamp").asText() : String.valueOf(System.currentTimeMillis());
                String metricName = item.hasNonNull("metricName") ? item.get("metricName").asText() : "metric";
                double value = item.hasNonNull("value") ? item.get("value").asDouble() : 0.0;
                result.add(new AnomalyInput.MetricDataPoint(timestamp, metricName, value, null));
            });
            return result;
        }

        JsonNode values = node.get("historicalValues");
        if (values == null || !values.isArray()) {
            return null;
        }
        String metricName = Optional.ofNullable(getStringField(node, "metricName")).orElse("metric");
        List<AnomalyInput.MetricDataPoint> result = new ArrayList<>();
        values.forEach(v -> result.add(new AnomalyInput.MetricDataPoint(
                String.valueOf(System.currentTimeMillis()),
                metricName,
                v.asDouble(),
                null
        )));
        return result;
    }

    private double getDoubleFieldWithDefault(JsonNode node, String field, double defaultValue) {
        JsonNode value = node.get(field);
        return value != null && !value.isNull() ? value.asDouble(defaultValue) : defaultValue;
    }

    private Map<String, Object> parseObjectMap(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        Map<String, Object> result = new HashMap<>();
        node.fields().forEachRemaining(entry -> {
            JsonNode value = entry.getValue();
            if (value == null || value.isNull()) {
                return;
            }
            if (value.isNumber()) {
                result.put(entry.getKey(), value.numberValue());
            } else if (value.isBoolean()) {
                result.put(entry.getKey(), value.booleanValue());
            } else if (value.isTextual()) {
                result.put(entry.getKey(), value.asText());
            } else {
                result.put(entry.getKey(), value.toString());
            }
        });
        return result;
    }

    private Map<String, String> parseStringMap(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        Map<String, String> result = new HashMap<>();
        node.fields().forEachRemaining(entry -> {
            JsonNode value = entry.getValue();
            if (value != null && !value.isNull()) {
                result.put(entry.getKey(), value.asText());
            }
        });
        return result;
    }

    private static <T> List<T> emptyToNull(List<T> list) {
        return list == null || list.isEmpty() ? null : list;
    }

    private CodeGeneratorInput.GenerationType parseGenerationType(JsonNode node) {
        String type = getStringField(node, "generationType");
        if (type == null) {
            return CodeGeneratorInput.GenerationType.IMPLEMENTATION;
        }
        try {
            return CodeGeneratorInput.GenerationType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return CodeGeneratorInput.GenerationType.IMPLEMENTATION;
        }
    }

    private RecommendationInput.RecommendationType parseRecommendationType(JsonNode node) {
        String type = getStringField(node, "recommendationType");
        if (type == null) {
            return RecommendationInput.RecommendationType.SIMILAR_ITEMS;
        }
        try {
            return RecommendationInput.RecommendationType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return RecommendationInput.RecommendationType.SIMILAR_ITEMS;
        }
    }

    private WorkflowRouterAgent.RoutingType parseRoutingType(JsonNode node) {
        String type = getStringField(node, "routingType");
        if (type == null) {
            return WorkflowRouterAgent.RoutingType.NEXT_STEP;
        }
        try {
            return WorkflowRouterAgent.RoutingType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return WorkflowRouterAgent.RoutingType.NEXT_STEP;
        }
    }

    private DocGeneratorAgent.DocType parseDocType(JsonNode node) {
        String type = getStringField(node, "docType");
        if (type == null) {
            return DocGeneratorAgent.DocType.README;
        }
        try {
            return DocGeneratorAgent.DocType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return DocGeneratorAgent.DocType.README;
        }
    }

    private Map<String, Object> parseDocOptions(JsonNode node) {
        JsonNode options = node.get("options");
        if (options == null || !options.isObject()) {
            return Map.of();
        }
        Map<String, Object> result = new HashMap<>();
        options.fields().forEachRemaining(entry -> {
            JsonNode value = entry.getValue();
            if (value.isBoolean()) {
                result.put(entry.getKey(), value.booleanValue());
            } else if (value.isNumber()) {
                result.put(entry.getKey(), value.numberValue());
            } else {
                result.put(entry.getKey(), value.asText());
            }
        });
        return result;
    }

    // -------------------------------------------------------------------------
    // Private Helpers - Error Responses
    // -------------------------------------------------------------------------

    private HttpResponse badRequest(String message) {
        return ResponseBuilder.badRequest()
                .json(Map.of(
                        "error", "BAD_REQUEST",
                        "message", message
                ))
                .build();
    }

    private HttpResponse notFound(String message) {
        return ResponseBuilder.notFound()
                .json(Map.of(
                        "error", "NOT_FOUND",
                        "message", message
                ))
                .build();
    }

    // -------------------------------------------------------------------------
    // Inner Classes
    // -------------------------------------------------------------------------

    /**
     * Agent info for listing.
     */
    public record AgentInfo(
            String name,
            String displayName,
            String version,
            String description,
            List<String> capabilities,
            List<String> supportedModels
    ) {}
}
