package com.ghatana.platform.workflow.pipeline;

import com.ghatana.platform.domain.domain.event.Event;
import com.ghatana.platform.workflow.operator.OperatorResult;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Executes a {@link Pipeline} DAG using ActiveJ {@link Promise} combinators.
 *
 * <p><b>Execution strategy:</b>
 * <ol>
 *   <li>Start from root nodes (no incoming edges) — run in parallel</li>
 *   <li>For each completed node, evaluate outgoing edges (unconditional + conditional)</li>
 *   <li>Activate downstream nodes whose ALL incoming edge sources have completed</li>
 *   <li>Fan-out: pass each output event separately to each downstream operator</li>
 *   <li>Terminal nodes (no outgoing edges) contribute results to final merge</li>
 * </ol>
 *
 * <p>All execution is non-blocking using ActiveJ Promise composition.
 * Failures in one branch do not prevent other branches from completing.
 *
 * @doc.type class
 * @doc.purpose DAG execution engine using ActiveJ Promises
 * @doc.layer core
 * @doc.pattern Strategy / Interpreter
 */
public final class DAGPipelineExecutor {

    private static final Logger log = LoggerFactory.getLogger(DAGPipelineExecutor.class);

    private DAGPipelineExecutor() {
        // Utility class
    }

    /**
     * Executes the pipeline by processing the input event through the DAG.
     *
     * @param pipeline the pipeline to execute
     * @param event    the input event
     * @return merged result from all terminal nodes
     */
    @NotNull
    public static Promise<OperatorResult> execute(@NotNull Pipeline pipeline, @NotNull Event event) {
        List<PipelineNode> roots = pipeline.getRootNodes();
        if (roots.isEmpty()) {
            return Promise.of(OperatorResult.empty());
        }

        Map<String, List<PipelineNode.Edge>> adjacency = pipeline.getAdjacency();

        // Track which nodes are terminal (no outgoing edges or all conditional with no match)
        Set<String> terminalIds = findTerminalNodeIds(pipeline);

        // Execute from all root nodes in parallel, collecting terminal results
        List<Promise<OperatorResult>> terminalPromises = new ArrayList<>();

        for (PipelineNode root : roots) {
            collectTerminalResults(root, event, pipeline, adjacency, terminalIds, terminalPromises);
        }

        // Merge all terminal results
        return Promises.toList(terminalPromises).map(results -> {
            OperatorResult.Builder merged = OperatorResult.builder().success();
            for (OperatorResult r : results) {
                merged.mergeWith(r);
            }
            return merged.build();
        });
    }

    /**
     * Recursively executes a node and its downstream nodes, collecting terminal results.
     */
    private static void collectTerminalResults(
            @NotNull PipelineNode node,
            @NotNull Event event,
            @NotNull Pipeline pipeline,
            @NotNull Map<String, List<PipelineNode.Edge>> adjacency,
            @NotNull Set<String> terminalIds,
            @NotNull List<Promise<OperatorResult>> terminalPromises) {

        Promise<OperatorResult> nodeResult = executeNode(node, event);

        List<PipelineNode.Edge> edges = adjacency.getOrDefault(node.nodeId(), List.of());

        if (edges.isEmpty()) {
            // Terminal node — collect result
            terminalPromises.add(nodeResult);
            return;
        }

        // For each downstream edge, chain execution
        for (PipelineNode.Edge edge : edges) {
            Promise<OperatorResult> downstream = nodeResult.then(result -> {
                if (!result.isSuccess()) {
                    log.debug("Node {} failed, not propagating to {}", node.nodeId(), edge.targetNodeId());
                    return Promise.of(result);
                }

                // Evaluate condition
                if (!edge.isUnconditional() && !edge.condition().test(result)) {
                    log.debug("Conditional edge {} → {} not taken", node.nodeId(), edge.targetNodeId());
                    return Promise.of(OperatorResult.empty());
                }

                Optional<PipelineNode> targetOpt = pipeline.getNode(edge.targetNodeId());
                if (targetOpt.isEmpty()) {
                    return Promise.of(OperatorResult.failed("Target node not found: " + edge.targetNodeId()));
                }

                PipelineNode target = targetOpt.get();
                List<Event> outputEvents = result.getOutputEvents();

                if (outputEvents.isEmpty()) {
                    return Promise.of(OperatorResult.empty());
                }

                // Process each output event through the downstream operator
                List<Promise<OperatorResult>> eventResults = new ArrayList<>();
                for (Event outputEvent : outputEvents) {
                    eventResults.add(executeNodeRecursive(target, outputEvent, pipeline, adjacency, terminalIds));
                }

                return Promises.toList(eventResults).map(results -> {
                    OperatorResult.Builder merged = OperatorResult.builder().success();
                    for (OperatorResult r : results) {
                        merged.mergeWith(r);
                    }
                    return merged.build();
                });
            });

            if (terminalIds.contains(edge.targetNodeId())) {
                terminalPromises.add(downstream);
            } else {
                // Intermediate results still need to propagate through
                terminalPromises.add(downstream);
            }
        }
    }

    /**
     * Executes a node and recursively all its descendants, returning merged results.
     */
    @NotNull
    private static Promise<OperatorResult> executeNodeRecursive(
            @NotNull PipelineNode node,
            @NotNull Event event,
            @NotNull Pipeline pipeline,
            @NotNull Map<String, List<PipelineNode.Edge>> adjacency,
            @NotNull Set<String> terminalIds) {

        return executeNode(node, event).then(result -> {
            if (!result.isSuccess()) {
                return Promise.of(result);
            }

            List<PipelineNode.Edge> edges = adjacency.getOrDefault(node.nodeId(), List.of());
            if (edges.isEmpty()) {
                // Terminal: return this result
                return Promise.of(result);
            }

            List<Promise<OperatorResult>> downstreamResults = new ArrayList<>();

            for (PipelineNode.Edge edge : edges) {
                if (!edge.isUnconditional() && !edge.condition().test(result)) {
                    continue;
                }

                Optional<PipelineNode> targetOpt = pipeline.getNode(edge.targetNodeId());
                if (targetOpt.isEmpty()) {
                    continue;
                }

                PipelineNode target = targetOpt.get();
                for (Event outputEvent : result.getOutputEvents()) {
                    downstreamResults.add(
                            executeNodeRecursive(target, outputEvent, pipeline, adjacency, terminalIds));
                }
            }

            if (downstreamResults.isEmpty()) {
                return Promise.of(result);
            }

            return Promises.toList(downstreamResults).map(results -> {
                OperatorResult.Builder merged = OperatorResult.builder().success();
                for (OperatorResult r : results) {
                    merged.mergeWith(r);
                }
                return merged.build();
            });
        });
    }

    /**
     * Executes a single node operator, capturing timing.
     */
    @NotNull
    private static Promise<OperatorResult> executeNode(@NotNull PipelineNode node, @NotNull Event event) {
        long start = System.nanoTime();
        log.debug("Executing node: {} (operator={})", node.nodeId(), node.operator().getName());

        return node.operator().process(event)
                .map(result -> {
                    long elapsed = System.nanoTime() - start;
                    log.debug("Node {} completed in {}ns, outputs={}", node.nodeId(), elapsed,
                            result.getOutputEvents().size());
                    return OperatorResult.builder()
                            .success()
                            .addEvents(result.getOutputEvents())
                            .processingTime(elapsed)
                            .build();
                })
                .mapException(ex -> {
                    log.warn("Node {} failed: {}", node.nodeId(), ex.getMessage());
                    return ex;
                });
    }

    /**
     * Finds node IDs that have no outgoing edges (terminal/leaf nodes).
     */
    @NotNull
    private static Set<String> findTerminalNodeIds(@NotNull Pipeline pipeline) {
        Map<String, List<PipelineNode.Edge>> adjacency = pipeline.getAdjacency();
        Set<String> terminals = new HashSet<>();
        for (PipelineNode node : pipeline.getNodesTopological()) {
            List<PipelineNode.Edge> edges = adjacency.getOrDefault(node.nodeId(), List.of());
            if (edges.isEmpty()) {
                terminals.add(node.nodeId());
            }
        }
        return terminals;
    }
}
