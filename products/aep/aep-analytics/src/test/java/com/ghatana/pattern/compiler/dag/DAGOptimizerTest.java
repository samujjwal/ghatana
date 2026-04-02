package com.ghatana.pattern.compiler.dag;

import com.ghatana.pattern.api.exception.PatternValidationException;
import com.ghatana.pattern.api.model.OperatorDAG;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("DAGOptimizer Tests")
class DAGOptimizerTest {

    @Test
    @DisplayName("optimize prunes nodes unreachable from root")
    void optimizePrunesNodesUnreachableFromRoot() throws PatternValidationException {
        DAGOptimizer optimizer = new DAGOptimizer(null, new SimpleMeterRegistry());
        OperatorDAG dag = OperatorDAG.builder()
                .nodes(new ArrayList<>(List.of(
                        node("root", "SEQ"),
                        node("child", "FILTER"),
                        node("orphan", "SELECT"))))
                .edges(new ArrayList<>(List.of(
                        edge("root", "child"))))
                .rootNodeId("root")
                .metadata(Map.of())
                .build();

        optimizer.optimize(dag);

        assertEquals(2, dag.getNodes().size());
        assertEquals(1, dag.getEdges().size());
        assertTrue(dag.getNodes().stream().noneMatch(node -> "orphan".equals(node.getId())));
    }

    @Test
    @DisplayName("optimize tolerates empty graph structures")
    void optimizeToleratesEmptyGraphStructures() throws PatternValidationException {
        DAGOptimizer optimizer = new DAGOptimizer(null, new SimpleMeterRegistry());
        OperatorDAG dag = OperatorDAG.builder()
                .nodes(new ArrayList<>())
                .edges(new ArrayList<>())
                .rootNodeId(null)
                .metadata(Map.of())
                .build();

        optimizer.optimize(dag);

        assertEquals(0, dag.getNodes().size());
        assertEquals(0, dag.getEdges().size());
    }

    @Test
    @DisplayName("optimize rejects null DAG")
    void optimizeRejectsNullDag() {
        DAGOptimizer optimizer = new DAGOptimizer(null, new SimpleMeterRegistry());

        PatternValidationException exception = assertThrows(PatternValidationException.class,
                () -> optimizer.optimize(null));

        assertTrue(exception.getMessage().contains("DAG cannot be null"));
    }

    private static OperatorDAG.OperatorNode node(String id, String type) {
        return OperatorDAG.OperatorNode.builder().id(id).type(type).metadata(Map.of()).build();
    }

    private static OperatorDAG.OperatorEdge edge(String from, String to) {
        return OperatorDAG.OperatorEdge.builder()
                .fromNodeId(from)
                .toNodeId(to)
                .metadata(Map.of())
                .build();
    }
}