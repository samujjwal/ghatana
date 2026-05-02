package com.ghatana.pattern.api.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("OperatorDAG Tests")
class OperatorDAGTest {

    @Test
    @DisplayName("builder preserves nodes edges metadata and root id")
    void builderPreservesNodesEdgesMetadataAndRootId() { 
        OperatorDAG.OperatorNode node = OperatorDAG.OperatorNode.builder() 
                .id("root")
                .type("SEQ")
                .metadata(Map.of("depth", 0)) 
                .build(); 
        OperatorDAG.OperatorEdge edge = OperatorDAG.OperatorEdge.builder() 
                .fromNodeId("root")
                .toNodeId("child")
                .edgeType(OperatorDAG.EdgeType.CONTROL_FLOW) 
                .metadata(Map.of("weight", 1)) 
                .build(); 

        OperatorDAG dag = OperatorDAG.builder() 
                .nodes(List.of(node)) 
                .edges(List.of(edge)) 
                .rootNodeId("root")
                .metadata(Map.of("nodeCount", 1)) 
                .build(); 

        assertEquals("root", dag.getRootNodeId()); 
        assertEquals(1, dag.getNodes().size()); 
        assertEquals(1, dag.getEdges().size()); 
        assertEquals(1, dag.getMetadata().get("nodeCount"));
        assertEquals("controlFlow", dag.getEdges().get(0).getEdgeType().getValue()); 
        assertEquals(OperatorDAG.EdgeType.CONTROL_FLOW, OperatorDAG.EdgeType.fromValue("controlFlow"));
        assertNull(OperatorDAG.EdgeType.fromValue("missing"));
        assertEquals("OperatorDAG{nodeCount=1, edgeCount=1, rootNodeId='root'}", dag.toString()); 
    }
}
