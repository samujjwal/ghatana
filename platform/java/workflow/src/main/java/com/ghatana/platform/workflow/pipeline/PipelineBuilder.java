package com.ghatana.platform.workflow.pipeline;

import com.ghatana.platform.workflow.operator.OperatorResult;
import com.ghatana.platform.workflow.operator.UnifiedOperator;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Predicate;

/**
 * Fluent builder for constructing {@link Pipeline} instances.
 *
 * <p>Supports sequential chains, parallel fork/join, and conditional branching.
 *
 * <p><b>Usage examples:</b>
 * <pre>{@code
 * // Sequential pipeline
 * Pipeline sequential = Pipeline.builder("fraud-detect", "1.0.0")
 *     .addNode("filter", filterOperator)
 *     .addNode("enrich", enrichOperator)
 *     .edge("filter", "enrich")
 *     .build();
 *
 * // Parallel fork/join
 * Pipeline parallel = Pipeline.builder("parallel-enrich", "1.0.0")
 *     .addNode("split", splitOperator)
 *     .addNode("geo-enrich", geoOperator)
 *     .addNode("risk-score", riskOperator)
 *     .addNode("merge", mergeOperator)
 *     .edge("split", "geo-enrich")
 *     .edge("split", "risk-score")
 *     .edge("geo-enrich", "merge")
 *     .edge("risk-score", "merge")
 *     .build();
 *
 * // Conditional branch
 * Pipeline branching = Pipeline.builder("conditional", "1.0.0")
 *     .addNode("classify", classifyOperator)
 *     .addNode("high-risk", highRiskOperator)
 *     .addNode("low-risk", lowRiskOperator)
 *     .conditionalEdge("classify", "high-risk", r -> r.isSuccess() && isHighRisk(r))
 *     .conditionalEdge("classify", "low-risk", r -> r.isSuccess() && !isHighRisk(r))
 *     .build();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Fluent DAG pipeline builder
 * @doc.layer core
 * @doc.pattern Builder
 */
public class PipelineBuilder {

    private final String pipelineId;
    private final String version;
    private final Map<String, UnifiedOperator> nodes = new LinkedHashMap<>();
    private final Map<String, List<PipelineNode.Edge>> adjacency = new LinkedHashMap<>();

    PipelineBuilder(@NotNull String pipelineId, @NotNull String version) {
        this.pipelineId = Objects.requireNonNull(pipelineId, "pipelineId");
        this.version = Objects.requireNonNull(version, "version");
    }

    /**
     * Adds an operator node to the pipeline.
     *
     * @param nodeId   unique node identifier within this pipeline
     * @param operator the operator to execute at this node
     * @return this builder
     */
    @NotNull
    public PipelineBuilder addNode(@NotNull String nodeId, @NotNull UnifiedOperator operator) {
        Objects.requireNonNull(nodeId, "nodeId");
        Objects.requireNonNull(operator, "operator");
        if (nodes.containsKey(nodeId)) {
            throw new IllegalArgumentException("Duplicate node ID: " + nodeId);
        }
        nodes.put(nodeId, operator);
        adjacency.putIfAbsent(nodeId, new ArrayList<>());
        return this;
    }

    /**
     * Adds an unconditional edge from source to target.
     *
     * @param fromNodeId source node
     * @param toNodeId   target node
     * @return this builder
     */
    @NotNull
    public PipelineBuilder edge(@NotNull String fromNodeId, @NotNull String toNodeId) {
        validateNodeExists(fromNodeId);
        validateNodeExists(toNodeId);
        adjacency.get(fromNodeId).add(PipelineNode.Edge.unconditional(toNodeId));
        return this;
    }

    /**
     * Adds a conditional edge from source to target.
     *
     * @param fromNodeId source node
     * @param toNodeId   target node
     * @param condition  predicate on the source operator's result
     * @return this builder
     */
    @NotNull
    public PipelineBuilder conditionalEdge(
            @NotNull String fromNodeId,
            @NotNull String toNodeId,
            @NotNull Predicate<OperatorResult> condition) {
        validateNodeExists(fromNodeId);
        validateNodeExists(toNodeId);
        adjacency.get(fromNodeId).add(PipelineNode.Edge.conditional(toNodeId, condition));
        return this;
    }

    /**
     * Convenience: adds nodes and sequential edges in order.
     *
     * @param nodeSpecs pairs of (nodeId, operator) — varargs must be even
     * @return this builder
     */
    @NotNull
    public PipelineBuilder sequential(@NotNull Object... nodeSpecs) {
        if (nodeSpecs.length % 2 != 0) {
            throw new IllegalArgumentException("sequential() requires (nodeId, operator) pairs");
        }
        String previousNodeId = null;
        for (int i = 0; i < nodeSpecs.length; i += 2) {
            String nodeId = (String) nodeSpecs[i];
            UnifiedOperator operator = (UnifiedOperator) nodeSpecs[i + 1];
            addNode(nodeId, operator);
            if (previousNodeId != null) {
                edge(previousNodeId, nodeId);
            }
            previousNodeId = nodeId;
        }
        return this;
    }

    /**
     * Builds the immutable {@link Pipeline}.
     *
     * @return constructed pipeline
     * @throws IllegalStateException if the graph is empty or contains cycles
     */
    @NotNull
    public Pipeline build() {
        if (nodes.isEmpty()) {
            throw new IllegalStateException("Pipeline must have at least one node");
        }
        validateNoCycles();

        // Build PipelineNode objects with their edges
        Map<String, PipelineNode> builtNodes = new LinkedHashMap<>();
        for (var entry : nodes.entrySet()) {
            String nodeId = entry.getKey();
            UnifiedOperator operator = entry.getValue();
            List<PipelineNode.Edge> edges = adjacency.getOrDefault(nodeId, List.of());
            builtNodes.put(nodeId, new PipelineNode(nodeId, operator, edges));
        }

        return new DefaultPipeline(pipelineId, version, builtNodes, adjacency);
    }

    // =========================================================================
    // Validation
    // =========================================================================

    private void validateNodeExists(@NotNull String nodeId) {
        if (!nodes.containsKey(nodeId)) {
            throw new IllegalArgumentException("Node not found: " + nodeId);
        }
    }

    private void validateNoCycles() {
        // Kahn's algorithm: topological sort to detect cycles
        Map<String, Integer> inDegree = new HashMap<>();
        nodes.keySet().forEach(id -> inDegree.put(id, 0));

        for (List<PipelineNode.Edge> edges : adjacency.values()) {
            for (PipelineNode.Edge edge : edges) {
                inDegree.merge(edge.targetNodeId(), 1, Integer::sum);
            }
        }

        Queue<String> queue = new ArrayDeque<>();
        inDegree.forEach((id, deg) -> { if (deg == 0) queue.add(id); });

        int visited = 0;
        while (!queue.isEmpty()) {
            String current = queue.poll();
            visited++;
            for (PipelineNode.Edge edge : adjacency.getOrDefault(current, List.of())) {
                int newDeg = inDegree.merge(edge.targetNodeId(), -1, Integer::sum);
                if (newDeg == 0) {
                    queue.add(edge.targetNodeId());
                }
            }
        }

        if (visited != nodes.size()) {
            throw new IllegalStateException("Pipeline contains a cycle — DAGs only");
        }
    }
}
