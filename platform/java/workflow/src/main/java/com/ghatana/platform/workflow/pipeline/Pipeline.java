package com.ghatana.platform.workflow.pipeline;

import com.ghatana.platform.domain.domain.event.Event;
import com.ghatana.platform.workflow.operator.OperatorConfig;
import com.ghatana.platform.workflow.operator.OperatorResult;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A directed acyclic graph (DAG) of {@link PipelineNode}s representing
 * a composable operator pipeline.
 *
 * <p>Pipelines are immutable once built via {@link PipelineBuilder}. Execution
 * is handled by {@link DAGPipelineExecutor}.
 *
 * <p><b>Lifecycle:</b>
 * <ol>
 *   <li>Build via {@link Pipeline#builder(String, String)}</li>
 *   <li>Initialize via {@link #initialize(OperatorConfig)}</li>
 *   <li>Execute via {@link #execute(Event)} or through {@link DAGPipelineExecutor}</li>
 *   <li>Shutdown via {@link #shutdown()}</li>
 * </ol>
 *
 * @doc.type interface
 * @doc.purpose DAG pipeline of composed operators
 * @doc.layer core
 * @doc.pattern Composite / Builder
 */
public interface Pipeline {

    /** Pipeline identifier. */
    @NotNull String getId();

    /** Pipeline version. */
    @NotNull String getVersion();

    /** Returns the entry-point (root) nodes — nodes with no incoming edges. */
    @NotNull List<PipelineNode> getRootNodes();

    /** Returns all nodes in topological order. */
    @NotNull List<PipelineNode> getNodesTopological();

    /** Looks up a node by ID. */
    @NotNull Optional<PipelineNode> getNode(@NotNull String nodeId);

    /** Returns a read-only view of the adjacency map (nodeId → downstream edges). */
    @NotNull Map<String, List<PipelineNode.Edge>> getAdjacency();

    /**
     * Initializes all operators in topological order.
     *
     * @param config shared pipeline-level configuration
     * @return Promise completing when all operators are initialized
     */
    @NotNull Promise<Void> initialize(@NotNull OperatorConfig config);

    /**
     * Executes the pipeline on a single event, propagating through the DAG.
     *
     * @param event input event
     * @return Promise of the merged result from all terminal nodes
     */
    @NotNull Promise<OperatorResult> execute(@NotNull Event event);

    /**
     * Shuts down all operators gracefully.
     *
     * @return Promise completing when all operators are stopped
     */
    @NotNull Promise<Void> shutdown();

    /**
     * Creates a new pipeline builder.
     *
     * @param id      pipeline identifier
     * @param version pipeline version
     * @return new builder
     */
    static PipelineBuilder builder(@NotNull String id, @NotNull String version) {
        return new PipelineBuilder(id, version);
    }
}
