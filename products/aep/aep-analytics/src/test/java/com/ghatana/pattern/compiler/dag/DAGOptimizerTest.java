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

@DisplayName("DAGOptimizer Tests [GH-90000]")
class DAGOptimizerTest {

    @Test
    @DisplayName("optimize prunes nodes unreachable from root [GH-90000]")
    void optimizePrunesNodesUnreachableFromRoot() throws PatternValidationException { // GH-90000
        DAGOptimizer optimizer = new DAGOptimizer(null, new SimpleMeterRegistry()); // GH-90000
        OperatorDAG dag = OperatorDAG.builder() // GH-90000
                .nodes(new ArrayList<>(List.of( // GH-90000
                        node("root", "SEQ"), // GH-90000
                        node("child", "FILTER"), // GH-90000
                        node("orphan", "SELECT")))) // GH-90000
                .edges(new ArrayList<>(List.of( // GH-90000
                        edge("root", "child")))) // GH-90000
                .rootNodeId("root [GH-90000]")
                .metadata(Map.of()) // GH-90000
                .build(); // GH-90000

        optimizer.optimize(dag); // GH-90000

        assertEquals(2, dag.getNodes().size()); // GH-90000
        assertEquals(1, dag.getEdges().size()); // GH-90000
        assertTrue(dag.getNodes().stream().noneMatch(node -> "orphan".equals(node.getId()))); // GH-90000
    }

    @Test
    @DisplayName("optimize tolerates empty graph structures [GH-90000]")
    void optimizeToleratesEmptyGraphStructures() throws PatternValidationException { // GH-90000
        DAGOptimizer optimizer = new DAGOptimizer(null, new SimpleMeterRegistry()); // GH-90000
        OperatorDAG dag = OperatorDAG.builder() // GH-90000
                .nodes(new ArrayList<>()) // GH-90000
                .edges(new ArrayList<>()) // GH-90000
                .rootNodeId(null) // GH-90000
                .metadata(Map.of()) // GH-90000
                .build(); // GH-90000

        optimizer.optimize(dag); // GH-90000

        assertEquals(0, dag.getNodes().size()); // GH-90000
        assertEquals(0, dag.getEdges().size()); // GH-90000
    }

    @Test
    @DisplayName("optimize rejects null DAG [GH-90000]")
    void optimizeRejectsNullDag() { // GH-90000
        DAGOptimizer optimizer = new DAGOptimizer(null, new SimpleMeterRegistry()); // GH-90000

        PatternValidationException exception = assertThrows(PatternValidationException.class, // GH-90000
                () -> optimizer.optimize(null)); // GH-90000

        assertTrue(exception.getMessage().contains("DAG cannot be null [GH-90000]"));
    }

    private static OperatorDAG.OperatorNode node(String id, String type) { // GH-90000
        return OperatorDAG.OperatorNode.builder().id(id).type(type).metadata(Map.of()).build(); // GH-90000
    }

    private static OperatorDAG.OperatorEdge edge(String from, String to) { // GH-90000
        return OperatorDAG.OperatorEdge.builder() // GH-90000
                .fromNodeId(from) // GH-90000
                .toNodeId(to) // GH-90000
                .metadata(Map.of()) // GH-90000
                .build(); // GH-90000
    }
}
