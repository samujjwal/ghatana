package com.ghatana.platform.workflow.pipeline;

import com.ghatana.platform.workflow.operator.UnifiedOperator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * A node in a {@link Pipeline} DAG, wrapping a {@link UnifiedOperator}.
 *
 * <p>Each node has a unique ID, an operator to execute, and edges to
 * downstream nodes. Edges may be conditional (branch) or unconditional.
 *
 * @doc.type record
 * @doc.purpose DAG node wrapping an operator
 * @doc.layer core
 * @doc.pattern Value Object
 */
public record PipelineNode(
        @NotNull String nodeId,
        @NotNull UnifiedOperator operator,
        @NotNull List<Edge> downstreamEdges
) {

    public PipelineNode {
        Objects.requireNonNull(nodeId, "nodeId");
        Objects.requireNonNull(operator, "operator");
        downstreamEdges = List.copyOf(downstreamEdges);
    }

    /**
     * An edge from this node to a downstream node, optionally guarded by a condition.
     *
     * @param targetNodeId  downstream node ID
     * @param condition     if null, edge is unconditional; otherwise result must pass predicate
     */
    public record Edge(
            @NotNull String targetNodeId,
            @Nullable Predicate<com.ghatana.platform.workflow.operator.OperatorResult> condition
    ) {
        public Edge {
            Objects.requireNonNull(targetNodeId, "targetNodeId");
        }

        /** Creates an unconditional edge. */
        public static Edge unconditional(@NotNull String targetNodeId) {
            return new Edge(targetNodeId, null);
        }

        /** Creates a conditional edge. */
        public static Edge conditional(
                @NotNull String targetNodeId,
                @NotNull Predicate<com.ghatana.platform.workflow.operator.OperatorResult> condition) {
            return new Edge(targetNodeId, condition);
        }

        /** Returns true if this edge has no condition. */
        public boolean isUnconditional() {
            return condition == null;
        }
    }
}
