package com.ghatana.orchestrator.core;

import com.ghatana.agent.planning.PlanGraph;
import com.ghatana.orchestrator.cache.PipelineCache;
import com.ghatana.orchestrator.client.AgentRegistryClient;
import com.ghatana.orchestrator.client.PipelineRegistryClient;
import com.ghatana.orchestrator.config.OrchestratorConfig;
import com.ghatana.orchestrator.loader.SpecFormatLoader;
import com.ghatana.orchestrator.models.OrchestratorPipelineEntity;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.workflow.planning.PlanCompiler;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main orchestrator component responsible for pipeline lifecycle management.
 *
 * <p>Manages the full pipeline lifecycle: loading, caching, deployment, and health
 * monitoring. Delegates to {@code PipelineRegistryClient} for pipeline retrieval and
 * {@code AgentRegistryClient} for agent availability checks.
 *
 * <p>When a {@link PlanCompiler} is provided, planning agents route through
 * {@link #compilePlan(String, String, String)} before pipeline execution, ensuring
 * all planned actions are validated and governance-checked before they run.
 *
 * @doc.type class
 * @doc.purpose Core pipeline lifecycle manager — load, cache, deploy, and monitor pipelines
 * @doc.layer product
 * @doc.pattern Service
 */
public class Orchestrator {

    private static final Logger log = LoggerFactory.getLogger(Orchestrator.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final PipelineCache pipelineCache;
    private final AgentRegistryClient agentRegistryClient;
    private final PipelineRegistryClient pipelineRegistryClient;
    private final OrchestratorConfig config;
    private final MetricsCollector metrics;
    private final SpecFormatLoader specFormatLoader;

    @Nullable
    private final PlanCompiler planCompiler;

    private final AtomicInteger currentRefreshes = new AtomicInteger(0);
    private volatile boolean isStarted = false;

    /**
     * Creates an Orchestrator without planning support (backward-compatible constructor).
     */
    public Orchestrator(
            PipelineCache pipelineCache,
            AgentRegistryClient agentRegistryClient,
            PipelineRegistryClient pipelineRegistryClient,
            OrchestratorConfig config,
            MetricsCollector metrics,
            SpecFormatLoader specFormatLoader) {
        this(pipelineCache, agentRegistryClient, pipelineRegistryClient, config, metrics, specFormatLoader, null);
    }

    /**
     * Creates an Orchestrator with planning support via a {@link PlanCompiler}.
     *
     * @param planCompiler optional plan compiler for planning-agent pipelines; may be null
     */
    public Orchestrator(
            @NotNull PipelineCache pipelineCache,
            @NotNull AgentRegistryClient agentRegistryClient,
            @NotNull PipelineRegistryClient pipelineRegistryClient,
            @NotNull OrchestratorConfig config,
            @NotNull MetricsCollector metrics,
            @NotNull SpecFormatLoader specFormatLoader,
            @Nullable PlanCompiler planCompiler) {
        this.pipelineCache = Objects.requireNonNull(pipelineCache, "pipelineCache");
        this.agentRegistryClient = Objects.requireNonNull(agentRegistryClient, "agentRegistryClient");
        this.pipelineRegistryClient = Objects.requireNonNull(pipelineRegistryClient, "pipelineRegistryClient");
        this.config = Objects.requireNonNull(config, "config");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.specFormatLoader = Objects.requireNonNull(specFormatLoader, "specFormatLoader");
        this.planCompiler = planCompiler;
    }

    /**
     * Start the orchestrator.
     */
    public Promise<Void> start() {
        if (isStarted) {
            return Promise.of(null);
        }

        return loadInitialPipelines().whenResult(() -> {
            isStarted = true;
            metrics.recordTimer("orch.startup", 0L);
        });
    }

    /**
     * Stop the orchestrator.
     */
    public Promise<Void> stop() {
        if (!isStarted) {
            return Promise.of(null);
        }

        return pipelineCache.clear().whenResult(() -> {
            isStarted = false;
        });
    }

    /**
     * Check if healthy.
     */
    public Promise<Boolean> isHealthy() {
        if (!isStarted) {
            return Promise.of(false);
        }

        return agentRegistryClient.isHealthy().then(agentHealthy -> pipelineRegistryClient
                .isHealthy()
                .map(pipelineHealthy -> agentHealthy && pipelineHealthy));
    }

    /**
     * Get current status.
     */
    public OrchestratorStatus getStatus() {
        return new OrchestratorStatus(
                isStarted,
                currentRefreshes.get() > 0,
                Instant.now(),
                pipelineCache.size(),
                currentRefreshes.get(),
                config.getRefreshInterval());
    }

    /**
     * Refresh pipelines.
     */
    public Promise<Void> refreshPipelines() {
        return loadPipelinesFromRegistry().then(pipelines -> pipelineCache.putAll(pipelines));
    }

    /**
     * Compiles a planning objective into a {@link PlanGraph} using the configured
     * {@link PlanCompiler}.
     *
     * <p>Planning agents route their objectives through this method before pipeline
     * execution, ensuring that all planned actions are validated and governance-checked.
     *
     * @param agentId   the planning agent ID; must not be blank
     * @param tenantId  the owning tenant; must not be blank
     * @param objective the objective or prompt to compile into a plan
     * @return a promise of the compiled {@link PlanGraph}
     * @throws IllegalStateException if no {@link PlanCompiler} has been configured
     */
    public Promise<PlanGraph> compilePlan(
            @NotNull String agentId, @NotNull String tenantId, @NotNull String objective) {
        if (planCompiler == null) {
            return Promise.ofException(
                    new IllegalStateException("PlanCompiler is not configured for this Orchestrator. "
                            + "Inject one via the 7-arg constructor."));
        }
        log.debug("Compiling plan for agent {} (tenant {}) — objective: {}", agentId, tenantId, objective);
        return planCompiler.compile(agentId, tenantId, objective, Map.of());
    }

    /**
     * Returns {@code true} if this Orchestrator has a {@link PlanCompiler} configured.
     */
    public boolean hasPlanCompiler() {
        return planCompiler != null;
    }

    /**
     * Get pipeline by ID.
     */
    public Promise<OrchestratorPipelineEntity> getPipeline(String pipelineId) {
        return pipelineCache.get(pipelineId).map(pipeline -> pipeline.orElse(null));
    }

    /**
     * List all pipelines.
     */
    public Promise<List<OrchestratorPipelineEntity>> listPipelines() {
        return pipelineCache.getAllPipelines();
    }

    /**
     * Deploy a pipeline by ID.
     * Fetches the pipeline from the registry, validates agents, and adds to cache.
     *
     * @param pipelineId The pipeline ID to deploy
     * @return The deployed pipeline entity, or null if deployment failed
     *
     * @doc.type method
     * @doc.purpose Deploy a pipeline to the orchestrator
     * @doc.layer core
     */
    public Promise<OrchestratorPipelineEntity> deployPipeline(String pipelineId) {
        long startTime = System.currentTimeMillis();

        return pipelineRegistryClient.getPipeline(pipelineId).then(optionalPipeline -> {
            if (optionalPipeline.isEmpty()) {
                metrics.incrementCounter("orch.pipeline.deploy.failed");
                return Promise.of(null);
            }

            OrchestratorPipelineEntity pipeline = optionalPipeline.get();

            // Validate agents if the pipeline has agent references
            return validatePipelineAgents(pipeline).then(isValid -> {
                if (!isValid) {
                    metrics.incrementCounter("orch.pipeline.validation.failed");
                    return Promise.of(null);
                }

                return pipelineCache.put(pipelineId, pipeline).map(ignored -> {
                    long duration = System.currentTimeMillis() - startTime;
                    metrics.recordTimer("orch.pipeline.deploy.time", duration);
                    metrics.incrementCounter("orch.pipeline.deployed");
                    return pipeline;
                });
            });
        });
    }

    /**
     * Undeploy a pipeline by ID.
     * Removes the pipeline from the cache and cleans up resources.
     *
     * @param pipelineId The pipeline ID to undeploy
     * @return true if the pipeline was undeployed, false if it wasn't deployed
     *
     * @doc.type method
     * @doc.purpose Undeploy a pipeline from the orchestrator
     * @doc.layer core
     */
    public Promise<Boolean> undeployPipeline(String pipelineId) {
        long startTime = System.currentTimeMillis();

        return pipelineCache.remove(pipelineId).map(removed -> {
            long duration = System.currentTimeMillis() - startTime;
            metrics.recordTimer("orch.pipeline.undeploy.time", duration);

            if (removed) {
                metrics.incrementCounter("orch.pipeline.undeployed");
            }
            return removed;
        });
    }

    // Private methods

    /**
     * Validate that all agents referenced by the pipeline are available.
     *
     * @param pipeline the pipeline to validate
     * @return Promise<Boolean> true if all agents are valid and available
     */
    private Promise<Boolean> validatePipelineAgents(OrchestratorPipelineEntity pipeline) {
        // Extract agent IDs from pipeline
        List<String> agentIds = extractAgentIds(pipeline);

        if (agentIds == null || agentIds.isEmpty()) {
            log.debug("Pipeline {} has no agent references, skipping agent validation", pipeline.id);
            return Promise.of(Boolean.TRUE);
        }

        log.debug("Validating {} agents for pipeline {}", agentIds.size(), pipeline.id);

        // Validate each agent exists and is active
        List<Promise<Boolean>> validationPromises = agentIds.stream()
                .map(agentId -> agentRegistryClient.getAgent(agentId).map(optionalAgent -> {
                    if (optionalAgent.isEmpty()) {
                        log.error("Agent not found in registry: {}", agentId);
                        metrics.incrementCounter(
                                "orch.agent.validation.failed", "agent_id", agentId, "reason", "not_found");
                        return false;
                    }

                    // Additional validation: check if agent is active
                    var agent = optionalAgent.get();
                    if (!"active".equalsIgnoreCase(agent.getStatus())) {
                        log.error("Agent is not active: {} (status: {})", agentId, agent.getStatus());
                        metrics.incrementCounter(
                                "orch.agent.validation.failed", "agent_id", agentId, "reason", "not_active");
                        return false;
                    }

                    log.debug("Agent validated: {}", agentId);
                    return true;
                }))
                .toList();

        // Wait for all validations to complete and check if all succeeded
        return Promises.toList(validationPromises)
                .map(results -> results.stream().allMatch(Boolean::booleanValue));
    }

    /**
     * Extract agent IDs from pipeline configuration.
     * Parses the JSON config to find agent references in steps/nodes.
     */
    private List<String> extractAgentIds(OrchestratorPipelineEntity pipeline) {
        List<String> agentIds = new ArrayList<>();

        String spec = pipeline.config;
        if (spec == null || spec.isBlank()) {
            return agentIds;
        }

        try {
            // Parse JSON config - common formats: {"steps": [{"agentId": "..."}]} or {"nodes": [...]}
            com.fasterxml.jackson.databind.JsonNode root = OBJECT_MAPPER.readTree(spec);

            // Extract from "steps" array
            if (root.has("steps")) {
                com.fasterxml.jackson.databind.JsonNode steps = root.get("steps");
                if (steps.isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode step : steps) {
                        extractAgentIdFromNode(step, agentIds);
                    }
                }
            }

            // Extract from "nodes" array (alternative format)
            if (root.has("nodes")) {
                com.fasterxml.jackson.databind.JsonNode nodes = root.get("nodes");
                if (nodes.isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode node : nodes) {
                        extractAgentIdFromNode(node, agentIds);
                    }
                }
            }

            // Extract from "agents" array (direct format)
            if (root.has("agents")) {
                com.fasterxml.jackson.databind.JsonNode agents = root.get("agents");
                if (agents.isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode agent : agents) {
                        if (agent.isTextual()) {
                            agentIds.add(agent.asText());
                        } else if (agent.has("id")) {
                            agentIds.add(agent.get("id").asText());
                        }
                    }
                }
            }

            log.debug("Extracted {} agent IDs from pipeline {}", agentIds.size(), pipeline.id);

        } catch (Exception e) {
            log.error("Failed to parse pipeline config for pipeline {}: {}", pipeline.id, e.getMessage());
            metrics.incrementCounter("orch.pipeline.parsing.failed", "pipeline_id", pipeline.id);
        }

        return agentIds;
    }

    /**
     * Helper to extract agent ID from a JSON node (step/node).
     */
    private void extractAgentIdFromNode(com.fasterxml.jackson.databind.JsonNode node, List<String> agentIds) {
        // Try "agentId" field
        if (node.has("agentId") && node.get("agentId").isTextual()) {
            agentIds.add(node.get("agentId").asText());
        }
        // Try "agent_id" field (snake_case)
        else if (node.has("agent_id") && node.get("agent_id").isTextual()) {
            agentIds.add(node.get("agent_id").asText());
        }
        // Try nested "agent" object with "id"
        else if (node.has("agent") && node.get("agent").has("id")) {
            agentIds.add(node.get("agent").get("id").asText());
        }
    }

    private Promise<Void> loadInitialPipelines() {
        return refreshPipelines();
    }

    private Promise<List<OrchestratorPipelineEntity>> loadPipelinesFromRegistry() {
        currentRefreshes.incrementAndGet();

        return pipelineRegistryClient.listAllPipelines().whenComplete(() -> currentRefreshes.decrementAndGet());
    }

    /**
     * Status information.
     */
    public static class OrchestratorStatus {
        private final boolean isRunning;
        private final boolean isStopping;
        private final Instant lastRefresh;
        private final int cachedPipelines;
        private final int currentRefreshes;
        private final Duration refreshInterval;

        public OrchestratorStatus(
                boolean isRunning,
                boolean isStopping,
                Instant lastRefresh,
                int cachedPipelines,
                int currentRefreshes,
                Duration refreshInterval) {
            this.isRunning = isRunning;
            this.isStopping = isStopping;
            this.lastRefresh = lastRefresh;
            this.cachedPipelines = cachedPipelines;
            this.currentRefreshes = currentRefreshes;
            this.refreshInterval = refreshInterval;
        }

        public boolean isRunning() {
            return isRunning;
        }

        public boolean isStopping() {
            return isStopping;
        }

        public Instant getLastRefresh() {
            return lastRefresh;
        }

        public int getCachedPipelines() {
            return cachedPipelines;
        }

        public int getCurrentRefreshes() {
            return currentRefreshes;
        }

        public Duration getRefreshInterval() {
            return refreshInterval;
        }
    }
}
