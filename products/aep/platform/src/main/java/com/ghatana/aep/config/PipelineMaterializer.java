/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.aep.config;

import com.ghatana.aep.domain.pipeline.AgentSpec;
import com.ghatana.aep.domain.pipeline.PipelineSpec;
import com.ghatana.aep.domain.pipeline.PipelineStageSpec;
import com.ghatana.core.operator.OperatorId;
import com.ghatana.core.pipeline.Pipeline;
import com.ghatana.core.pipeline.PipelineBuilder;
import com.ghatana.core.pipeline.PipelineEdge;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Materializes a declarative {@link PipelineSpec} (YAML layer) into a
 * runtime-executable {@link Pipeline} (DAG layer) via the {@link PipelineBuilder}.
 *
 * <h2>Mapping Rules</h2>
 * <table>
 *   <tr><th>PipelineSpec</th><th>Pipeline</th></tr>
 *   <tr><td>{@code PipelineStageSpec.name}</td><td>{@code PipelineStage.stageId}</td></tr>
 *   <tr><td>{@code AgentSpec.agent}</td><td>{@code PipelineStage.operatorId} (via {@code OperatorId.parse})</td></tr>
 *   <tr><td>{@code AgentSpec.dependencies}</td><td>{@code PipelineEdge} (primary edges)</td></tr>
 *   <tr><td>{@code AgentSpec.failureEscalation}</td><td>{@code PipelineEdge.error} edges</td></tr>
 *   <tr><td>{@code PipelineStageSpec.connectorIds}</td><td>metadata on the pipeline</td></tr>
 *   <tr><td>Sequential stages (no dependencies)</td><td>Linear primary edge chain</td></tr>
 * </table>
 *
 * <h2>Agent → OperatorId Resolution</h2>
 * Agent names are resolved to {@link OperatorId} via:
 * <ol>
 *   <li>If the agent string matches {@code namespace:type:name:version} format → parsed directly</li>
 *   <li>If simple name (e.g., "fraud-detector") → resolved as {@code aep:agent:<name>:latest}</li>
 *   <li>Custom {@link OperatorIdResolver} if provided</li>
 * </ol>
 *
 * @doc.type class
 * @doc.purpose PipelineSpec → Pipeline materializer
 * @doc.layer product
 * @doc.pattern Materializer, Builder
 *
 * @author Ghatana AI Platform
 * @since 3.0.0
 */
public class PipelineMaterializer {

    private static final Logger log = LoggerFactory.getLogger(PipelineMaterializer.class);

    private static final String DEFAULT_NAMESPACE = "aep";
    private static final String DEFAULT_TYPE = "agent";
    private static final String DEFAULT_VERSION = "latest";

    private final OperatorIdResolver operatorIdResolver;

    /**
     * Creates a materializer with default operator ID resolution.
     */
    public PipelineMaterializer() {
        this(null);
    }

    /**
     * Creates a materializer with a custom operator ID resolver.
     *
     * @param resolver custom resolver, or null for default behavior
     */
    public PipelineMaterializer(@Nullable OperatorIdResolver resolver) {
        this.operatorIdResolver = resolver;
    }

    /**
     * Materializes a {@link PipelineSpec} into a runtime {@link Pipeline}.
     *
     * @param spec       the declarative pipeline specification
     * @param pipelineId unique pipeline identifier (e.g., "fraud-detection")
     * @param version    pipeline version (e.g., "1.0.0")
     * @return an executable Pipeline
     * @throws PipelineMaterializationException if the spec is invalid
     */
    @NotNull
    public Pipeline materialize(
            @NotNull PipelineSpec spec,
            @NotNull String pipelineId,
            @NotNull String version) {

        Objects.requireNonNull(spec, "PipelineSpec must not be null");
        Objects.requireNonNull(pipelineId, "pipelineId must not be null");
        Objects.requireNonNull(version, "version must not be null");

        List<PipelineStageSpec> stages = spec.getStages();
        if (stages == null || stages.isEmpty()) {
            throw new PipelineMaterializationException(
                    "PipelineSpec has no stages for pipeline '" + pipelineId + "'");
        }

        PipelineBuilder builder = Pipeline.builder(pipelineId, version)
                .name(pipelineId)
                .description("Materialized from PipelineSpec");

        // Track all node IDs at stage and agent level for edge wiring
        List<String> stageIds = new ArrayList<>();
        Map<String, String> agentIdToStageId = new LinkedHashMap<>();

        // ─── Phase 1: Register stages (one per agent in workflow) ─────────
        for (PipelineStageSpec stageSpec : stages) {
            String stageBaseName = stageSpec.getName();
            if (stageBaseName == null || stageBaseName.isBlank()) {
                throw new PipelineMaterializationException(
                        "PipelineStageSpec has no name in pipeline '" + pipelineId + "'");
            }

            List<AgentSpec> workflow = stageSpec.getWorkflow();
            if (workflow == null || workflow.isEmpty()) {
                // Stage with no agents — register as a pass-through node
                OperatorId opId = resolveOperatorId(stageBaseName);
                Map<String, Object> config = buildStageConfig(stageSpec, null);
                builder.stage(stageBaseName, opId, config);
                stageIds.add(stageBaseName);
                continue;
            }

            for (AgentSpec agentSpec : workflow) {
                String agentName = agentSpec.getAgent();
                if (agentName == null || agentName.isBlank()) {
                    agentName = agentSpec.getId();
                }
                if (agentName == null || agentName.isBlank()) {
                    throw new PipelineMaterializationException(
                            "AgentSpec has no agent name or id in stage '" +
                                    stageBaseName + "' of pipeline '" + pipelineId + "'");
                }

                // Unique stage ID: stageName.agentId or stageName.agentName
                String nodeId = agentSpec.getId() != null
                        ? stageBaseName + "." + agentSpec.getId()
                        : stageBaseName + "." + sanitizeId(agentName);

                OperatorId opId = resolveOperatorId(agentName);
                Map<String, Object> config = buildStageConfig(stageSpec, agentSpec);
                builder.stage(nodeId, opId, config);

                stageIds.add(nodeId);
                if (agentSpec.getId() != null) {
                    agentIdToStageId.put(agentSpec.getId(), nodeId);
                }
                agentIdToStageId.put(agentName, nodeId);
            }
        }

        // ─── Phase 2: Wire edges ──────────────────────────────────────────
        Set<String> wiredNodes = new HashSet<>();

        for (PipelineStageSpec stageSpec : stages) {
            List<AgentSpec> workflow = stageSpec.getWorkflow();
            if (workflow == null) continue;

            for (AgentSpec agentSpec : workflow) {
                String agentName = agentSpec.getAgent() != null
                        ? agentSpec.getAgent() : agentSpec.getId();
                if (agentName == null) continue;

                String nodeId = findNodeId(agentIdToStageId, stageSpec.getName(), agentSpec);

                // Dependency edges (explicit)
                if (agentSpec.getDependencies() != null) {
                    for (String dep : agentSpec.getDependencies()) {
                        String depNodeId = resolveNodeId(agentIdToStageId, dep);
                        if (depNodeId != null) {
                            builder.edge(depNodeId, nodeId, PipelineEdge.LABEL_PRIMARY);
                            wiredNodes.add(nodeId);
                        } else {
                            log.warn("Unresolved dependency '{}' for agent '{}' in pipeline '{}'",
                                    dep, agentName, pipelineId);
                        }
                    }
                }

                // Error/escalation edges
                if (agentSpec.getFailureEscalation() != null) {
                    for (String escalation : agentSpec.getFailureEscalation()) {
                        String escNodeId = resolveNodeId(agentIdToStageId, escalation);
                        if (escNodeId != null) {
                            builder.onError(nodeId, escNodeId);
                        }
                    }
                }
            }
        }

        // ─── Phase 3: Chain unwired sequential stages ──────────────────────
        for (int i = 0; i < stageIds.size() - 1; i++) {
            String current = stageIds.get(i);
            String next = stageIds.get(i + 1);
            // Only auto-chain if 'next' has no explicit dependencies
            if (!wiredNodes.contains(next)) {
                builder.edge(current, next, PipelineEdge.LABEL_PRIMARY);
            }
        }

        // ─── Phase 4: Metadata ────────────────────────────────────────────
        builder.metadata("materializedFrom", "PipelineSpec");
        builder.metadata("stageCount", stageIds.size());

        // Add connector metadata
        for (PipelineStageSpec stageSpec : stages) {
            if (stageSpec.getConnectorIds() != null && !stageSpec.getConnectorIds().isEmpty()) {
                builder.metadata("connectors." + stageSpec.getName(),
                        stageSpec.getConnectorIds());
            }
        }

        Pipeline pipeline = builder.build();
        log.info("Materialized pipeline '{}' v{}: {} stages, {} edges",
                pipelineId, version, pipeline.getStages().size(), pipeline.getEdges().size());

        return pipeline;
    }

    /**
     * Materializes a PipelineSpec with default version "1.0.0".
     *
     * @param spec       the pipeline specification
     * @param pipelineId pipeline identifier
     * @return an executable Pipeline
     */
    @NotNull
    public Pipeline materialize(@NotNull PipelineSpec spec, @NotNull String pipelineId) {
        return materialize(spec, pipelineId, "1.0.0");
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Operator ID Resolution
    // ═════════════════════════════════════════════════════════════════════════

    @NotNull
    private OperatorId resolveOperatorId(@NotNull String agentName) {
        if (operatorIdResolver != null) {
            OperatorId resolved = operatorIdResolver.resolve(agentName);
            if (resolved != null) return resolved;
        }

        // Try parsing as fully-qualified OperatorId
        if (agentName.chars().filter(c -> c == ':').count() == 3) {
            try {
                return OperatorId.parse(agentName);
            } catch (Exception ignored) {
                // Fall through to default resolution
            }
        }

        // Default: aep:agent:<name>:latest
        return OperatorId.of(DEFAULT_NAMESPACE, DEFAULT_TYPE,
                sanitizeId(agentName), DEFAULT_VERSION);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Edge Resolution
    // ═════════════════════════════════════════════════════════════════════════

    @Nullable
    private String resolveNodeId(Map<String, String> agentIdToStage, String ref) {
        // Direct match by agent ID
        if (agentIdToStage.containsKey(ref)) {
            return agentIdToStage.get(ref);
        }
        // Check if ref IS a stage ID directly
        if (agentIdToStage.containsValue(ref)) {
            return ref;
        }
        return null;
    }

    @NotNull
    private String findNodeId(Map<String, String> agentIdToStage,
                              String stageBaseName, AgentSpec agentSpec) {
        String agentName = agentSpec.getAgent() != null
                ? agentSpec.getAgent() : agentSpec.getId();
        String nodeId = agentSpec.getId() != null
                ? stageBaseName + "." + agentSpec.getId()
                : stageBaseName + "." + sanitizeId(agentName);
        return nodeId;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Config Building
    // ═════════════════════════════════════════════════════════════════════════

    @NotNull
    private Map<String, Object> buildStageConfig(
            @NotNull PipelineStageSpec stageSpec,
            @Nullable AgentSpec agentSpec) {

        Map<String, Object> config = new LinkedHashMap<>();

        // Stage-level config
        if (stageSpec.getStageType() != null) {
            config.put("stageType", stageSpec.getStageType());
        }
        if (stageSpec.getConnectorIds() != null) {
            config.put("connectorIds", stageSpec.getConnectorIds());
        }

        // Agent-level config
        if (agentSpec != null) {
            if (agentSpec.getRole() != null) {
                config.put("role", agentSpec.getRole());
            }
            if (agentSpec.getAgentTasks() != null) {
                config.put("agentTasks", agentSpec.getAgentTasks());
            }
            if (agentSpec.getAcceptanceCriteria() != null) {
                config.put("acceptanceCriteria", agentSpec.getAcceptanceCriteria());
            }
            if (agentSpec.getInputsSpec() != null) {
                config.put("inputsSpec", agentSpec.getInputsSpec().stream()
                        .map(io -> Map.of(
                                "name", io.getName(),
                                "description", Objects.toString(io.getDescription(), ""),
                                "format", Objects.toString(io.getFormat(), "")))
                        .collect(Collectors.toList()));
            }
            if (agentSpec.getOutputsSpec() != null) {
                config.put("outputsSpec", agentSpec.getOutputsSpec().stream()
                        .map(io -> Map.of(
                                "name", io.getName(),
                                "description", Objects.toString(io.getDescription(), ""),
                                "format", Objects.toString(io.getFormat(), "")))
                        .collect(Collectors.toList()));
            }
            if (agentSpec.getHitl() != null && agentSpec.getHitl()) {
                config.put("hitl", true);
                if (agentSpec.getHitlReason() != null) {
                    config.put("hitlReason", agentSpec.getHitlReason());
                }
            }
            if (agentSpec.getChildren() != null && !agentSpec.getChildren().isEmpty()) {
                config.put("childAgentCount", agentSpec.getChildren().size());
            }
        }

        return config;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Utilities
    // ═════════════════════════════════════════════════════════════════════════

    private static String sanitizeId(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "-").toLowerCase();
    }

    /**
     * Functional interface for custom operator ID resolution.
     */
    @FunctionalInterface
    public interface OperatorIdResolver {
        /**
         * Resolves an agent name to an OperatorId.
         *
         * @param agentName agent name from the spec
         * @return resolved OperatorId, or null to fall back to default resolution
         */
        @Nullable
        OperatorId resolve(@NotNull String agentName);
    }
}
