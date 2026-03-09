package com.ghatana.platform.workflow.pipeline;

import com.ghatana.platform.domain.domain.event.Event;
import com.ghatana.platform.workflow.operator.OperatorConfig;
import com.ghatana.platform.workflow.operator.OperatorResult;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Immutable default implementation of {@link Pipeline}.
 *
 * @doc.type class
 * @doc.purpose Default immutable DAG pipeline implementation
 * @doc.layer core
 * @doc.pattern Composite
 */
final class DefaultPipeline implements Pipeline {

    private final String id;
    private final String version;
    private final Map<String, PipelineNode> nodesById;
    private final Map<String, List<PipelineNode.Edge>> adjacency;
    private final List<PipelineNode> topologicalOrder;
    private final List<PipelineNode> rootNodes;

    DefaultPipeline(
            @NotNull String id,
            @NotNull String version,
            @NotNull Map<String, PipelineNode> nodesById,
            @NotNull Map<String, List<PipelineNode.Edge>> adjacency) {
        this.id = id;
        this.version = version;
        this.nodesById = Map.copyOf(nodesById);
        this.adjacency = adjacency.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> List.copyOf(e.getValue())));
        this.topologicalOrder = computeTopologicalOrder();
        this.rootNodes = computeRootNodes();
    }

    @Override
    @NotNull
    public String getId() {
        return id;
    }

    @Override
    @NotNull
    public String getVersion() {
        return version;
    }

    @Override
    @NotNull
    public List<PipelineNode> getRootNodes() {
        return rootNodes;
    }

    @Override
    @NotNull
    public List<PipelineNode> getNodesTopological() {
        return topologicalOrder;
    }

    @Override
    @NotNull
    public Optional<PipelineNode> getNode(@NotNull String nodeId) {
        return Optional.ofNullable(nodesById.get(nodeId));
    }

    @Override
    @NotNull
    public Map<String, List<PipelineNode.Edge>> getAdjacency() {
        return adjacency;
    }

    @Override
    @NotNull
    public Promise<Void> initialize(@NotNull OperatorConfig config) {
        List<Promise<Void>> inits = topologicalOrder.stream()
                .map(node -> node.operator().initialize(config))
                .toList();
        return Promises.toList(inits).map(ignored -> null);
    }

    @Override
    @NotNull
    public Promise<OperatorResult> execute(@NotNull Event event) {
        return DAGPipelineExecutor.execute(this, event);
    }

    @Override
    @NotNull
    public Promise<Void> shutdown() {
        // Stop in reverse topological order
        List<PipelineNode> reversed = new ArrayList<>(topologicalOrder);
        Collections.reverse(reversed);
        List<Promise<Void>> stops = reversed.stream()
                .map(node -> node.operator().stop())
                .toList();
        return Promises.toList(stops).map(ignored -> null);
    }

    // =========================================================================
    // Internal
    // =========================================================================

    private List<PipelineNode> computeTopologicalOrder() {
        Map<String, Integer> inDegree = new HashMap<>();
        nodesById.keySet().forEach(id -> inDegree.put(id, 0));
        for (List<PipelineNode.Edge> edges : adjacency.values()) {
            for (PipelineNode.Edge edge : edges) {
                inDegree.merge(edge.targetNodeId(), 1, Integer::sum);
            }
        }

        Queue<String> queue = new ArrayDeque<>();
        inDegree.forEach((nodeId, deg) -> { if (deg == 0) queue.add(nodeId); });

        List<PipelineNode> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            sorted.add(nodesById.get(current));
            for (PipelineNode.Edge edge : adjacency.getOrDefault(current, List.of())) {
                int newDeg = inDegree.merge(edge.targetNodeId(), -1, Integer::sum);
                if (newDeg == 0) {
                    queue.add(edge.targetNodeId());
                }
            }
        }
        return List.copyOf(sorted);
    }

    private List<PipelineNode> computeRootNodes() {
        Set<String> hasIncoming = new HashSet<>();
        for (List<PipelineNode.Edge> edges : adjacency.values()) {
            for (PipelineNode.Edge edge : edges) {
                hasIncoming.add(edge.targetNodeId());
            }
        }
        return topologicalOrder.stream()
                .filter(node -> !hasIncoming.contains(node.nodeId()))
                .toList();
    }
}
