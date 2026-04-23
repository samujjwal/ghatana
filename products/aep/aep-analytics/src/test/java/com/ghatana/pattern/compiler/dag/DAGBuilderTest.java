package com.ghatana.pattern.compiler.dag;

import com.ghatana.pattern.api.exception.PatternValidationException;
import com.ghatana.pattern.api.model.OperatorDAG;
import com.ghatana.pattern.api.model.OperatorSpec;
import com.ghatana.pattern.compiler.ast.AST;
import com.ghatana.pattern.compiler.ast.ASTBuilder;
import com.ghatana.pattern.operator.registry.OperatorRegistry;
import com.ghatana.pattern.operator.spi.OperatorMetadata;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("DAGBuilder Tests")
class DAGBuilderTest {

    @Test
    @DisplayName("build converts AST into nodes edges and metadata")
    void buildConvertsAstIntoNodesEdgesAndMetadata() throws PatternValidationException { // GH-90000
        DAGBuilder dagBuilder = new DAGBuilder(null, new SimpleMeterRegistry()); // GH-90000
        AST ast = new ASTBuilder(null, new SimpleMeterRegistry()).build( // GH-90000
                OperatorSpec.builder() // GH-90000
                        .type("SEQ")
                        .id("root")
                        .operand(OperatorSpec.builder().type("SELECT").id("select-1").build())
                        .operand(OperatorSpec.builder().type("FILTER").id("filter-1").build())
                        .build()); // GH-90000

        OperatorDAG dag = dagBuilder.build(ast); // GH-90000

        assertEquals(3, dag.getNodes().size()); // GH-90000
        assertEquals(2, dag.getEdges().size()); // GH-90000
        assertEquals("root", dag.getRootNodeId()); // GH-90000
        assertEquals(2, dag.getMetadata().get("edgeCount"));
        assertEquals(3, dag.getMetadata().get("nodeCount"));
        assertEquals(ast.getDepth(), dag.getMetadata().get("astDepth"));
        assertNotNull(dag.getMetadata().get("buildTime"));
        assertTrue(dag.getEdges().stream().allMatch(edge -> edge.getEdgeType() == OperatorDAG.EdgeType.DATA_FLOW)); // GH-90000
        assertTrue(dag.getEdges().stream().allMatch(edge -> edge.getMetadata().containsKey("astDepth")));
    }

    @Test
    @DisplayName("build rejects null AST")
    void buildRejectsNullAst() { // GH-90000
        DAGBuilder dagBuilder = new DAGBuilder(null, new SimpleMeterRegistry()); // GH-90000

        PatternValidationException exception = assertThrows(PatternValidationException.class, // GH-90000
                () -> dagBuilder.build(null)); // GH-90000

        assertTrue(exception.getMessage().contains("AST cannot be null"));
    }

    @Test
    @DisplayName("generateStateKeys only includes stateful operators")
    void generateStateKeysOnlyIncludesStatefulOperators() { // GH-90000
        OperatorRegistry registry = mock(OperatorRegistry.class); // GH-90000
        when(registry.getMetadata("SEQ")).thenReturn(OperatorMetadata.builder().type("SEQ").supportsStateful(true).build());
        when(registry.getMetadata("SELECT")).thenReturn(OperatorMetadata.builder().type("SELECT").supportsStateful(false).build());

        DAGBuilder dagBuilder = new DAGBuilder(registry, new SimpleMeterRegistry()); // GH-90000
        OperatorDAG dag = OperatorDAG.builder() // GH-90000
                .nodes(List.of( // GH-90000
                        OperatorDAG.OperatorNode.builder().id("seq-1").type("SEQ").build(),
                        OperatorDAG.OperatorNode.builder().id("select-1").type("SELECT").build(),
                        OperatorDAG.OperatorNode.builder().id("unknown-1").type("UNKNOWN").build()))
                .edges(List.of()) // GH-90000
                .rootNodeId("seq-1")
                .metadata(Map.of()) // GH-90000
                .build(); // GH-90000

        Map<String, String> stateKeys = dagBuilder.generateStateKeys(UUID.fromString("11111111-1111-1111-1111-111111111111"), dag);

        assertEquals(1, stateKeys.size()); // GH-90000
        assertEquals("pattern:11111111-1111-1111-1111-111111111111:operator:seq-1", stateKeys.get("seq-1"));
        assertFalse(stateKeys.containsKey("select-1"));
    }

    @Test
    @DisplayName("extractRequiredStreams converts event types to distinct stream names")
    void extractRequiredStreamsConvertsEventTypesToDistinctStreamNames() { // GH-90000
        DAGBuilder dagBuilder = new DAGBuilder(null, new SimpleMeterRegistry()); // GH-90000

        List<String> streams = dagBuilder.extractRequiredStreams(List.of( // GH-90000
                "com.ghatana.financial.TransactionEvent",
                "com.ghatana.financial.TransactionEvent",
                "com.ghatana.identity.UserLoginEvent",
                "raw-event"
        ));

        assertEquals(List.of("financial-transaction-event", "identity-user-login-event", "raw-event"), streams); // GH-90000
        assertEquals(List.of(), dagBuilder.extractRequiredStreams(List.of())); // GH-90000
    }
}
