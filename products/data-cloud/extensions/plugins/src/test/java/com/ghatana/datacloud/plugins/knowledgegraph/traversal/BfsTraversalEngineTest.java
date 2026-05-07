package com.ghatana.datacloud.plugins.knowledgegraph.traversal;

import com.ghatana.datacloud.plugins.knowledgegraph.model.GraphEdge;
import com.ghatana.datacloud.plugins.knowledgegraph.model.GraphNode;
import com.ghatana.datacloud.plugins.knowledgegraph.storage.GraphStorageAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link BfsTraversalEngine}.
 *
 * <p>BfsTraversalEngine is synchronous and depends only on the
 * {@link GraphStorageAdapter} interface, which is mocked. Tests verify
 * lifecycle (including the strict initialize-before-start contract), 
 * neighbor discovery, shortest-path finding, and all-paths enumeration.
 *
 * @doc.type test
 * @doc.purpose Validate BFS traversal operations: neighbors, shortest path, all paths
 * @doc.layer product
 */
@DisplayName("BfsTraversalEngine Tests")
@ExtendWith(MockitoExtension.class) 
class BfsTraversalEngineTest {

    @Mock
    private GraphStorageAdapter storageAdapter;

    private BfsTraversalEngine engine;

    private GraphNode nodeA;
    private GraphNode nodeB;
    private GraphNode nodeC;
    private GraphEdge edgeAB;
    private GraphEdge edgeBC;

    @BeforeEach
    void setUp() { 
        engine = new BfsTraversalEngine(storageAdapter); 

        nodeA = GraphNode.builder() 
                .id("node-a")
                .type("SERVICE")
                .labels(Set.of()) 
                .properties(Map.of()) 
                .tenantId("tenant-1")
                .build(); 

        nodeB = GraphNode.builder() 
                .id("node-b")
                .type("SERVICE")
                .labels(Set.of()) 
                .properties(Map.of()) 
                .tenantId("tenant-1")
                .build(); 

        nodeC = GraphNode.builder() 
                .id("node-c")
                .type("SERVICE")
                .labels(Set.of()) 
                .properties(Map.of()) 
                .tenantId("tenant-1")
                .build(); 

        edgeAB = GraphEdge.builder() 
                .id("edge-ab")
                .sourceNodeId("node-a")
                .targetNodeId("node-b")
                .tenantId("tenant-1")
                .relationshipType("CALLS")
                .build(); 

        edgeBC = GraphEdge.builder() 
                .id("edge-bc")
                .sourceNodeId("node-b")
                .targetNodeId("node-c")
                .tenantId("tenant-1")
                .relationshipType("CALLS")
                .build(); 
    }

    // =========================================================================
    // LIFECYCLE
    // =========================================================================

    @Nested
    @DisplayName("Lifecycle")
    class Lifecycle {

        @Test
        @DisplayName("should be healthy after initialize and start")
        void shouldBeHealthyAfterInitializeAndStart() { 
            engine.initialize(Map.of()); 
            engine.start(); 

            assertThat(engine.isHealthy()).isTrue(); 
        }

        @Test
        @DisplayName("start should throw IllegalStateException when not initialized")
        void startShouldThrowWhenNotInitialized() { 
            assertThatThrownBy(() -> engine.start()) 
                    .isInstanceOf(IllegalStateException.class); 
        }

        @Test
        @DisplayName("should not be healthy after stop")
        void shouldNotBeHealthyAfterStop() { 
            engine.initialize(Map.of()); 
            engine.start(); 
            engine.stop(); 

            assertThat(engine.isHealthy()).isFalse(); 
        }

        @Test
        @DisplayName("should not be healthy after shutdown")
        void shouldNotBeHealthyAfterShutdown() { 
            engine.initialize(Map.of()); 
            engine.start(); 
            engine.shutdown(); 

            assertThat(engine.isHealthy()).isFalse(); 
        }
    }

    // =========================================================================
    // GET NEIGHBORS
    // =========================================================================

    @Nested
    @DisplayName("getNeighbors")
    class GetNeighbors {

        @BeforeEach
        void init() { 
            engine.initialize(Map.of()); 
            engine.start(); 
        }

        @Test
        @DisplayName("should return direct neighbor at depth 1")
        void shouldReturnDirectNeighbor() { 
            when(storageAdapter.getNode("node-a", "tenant-1")).thenReturn(nodeA); 
            when(storageAdapter.getOutgoingEdges("node-a", "tenant-1")) 
                    .thenReturn(List.of(edgeAB)); 
            when(storageAdapter.getNode("node-b", "tenant-1")).thenReturn(nodeB); 

            List<GraphNode> neighbors = engine.getNeighbors("node-a", 1, "tenant-1"); 

            assertThat(neighbors).hasSize(1); 
            assertThat(neighbors.get(0).getId()).isEqualTo("node-b");
        }

        @Test
        @DisplayName("should return transitive neighbors at depth 2")
        void shouldReturnTransitiveNeighborsAtDepth2() { 
            when(storageAdapter.getNode("node-a", "tenant-1")).thenReturn(nodeA); 
            when(storageAdapter.getOutgoingEdges("node-a", "tenant-1")) 
                    .thenReturn(List.of(edgeAB)); 
            when(storageAdapter.getNode("node-b", "tenant-1")).thenReturn(nodeB); 
            when(storageAdapter.getOutgoingEdges("node-b", "tenant-1")) 
                    .thenReturn(List.of(edgeBC)); 
            when(storageAdapter.getNode("node-c", "tenant-1")).thenReturn(nodeC); 

            List<GraphNode> neighbors = engine.getNeighbors("node-a", 2, "tenant-1"); 

            assertThat(neighbors).extracting(GraphNode::getId) 
                    .containsExactlyInAnyOrder("node-b", "node-c"); 
        }

        @Test
        @DisplayName("should return empty list when start node does not exist")
        void shouldReturnEmptyWhenStartNodeMissing() { 
            when(storageAdapter.getNode("missing", "tenant-1")).thenReturn(null); 

            List<GraphNode> neighbors = engine.getNeighbors("missing", 1, "tenant-1"); 
            assertThat(neighbors).isEmpty(); 
        }

        @Test
        @DisplayName("should return empty list when node has no outgoing edges")
        void shouldReturnEmptyWhenNoOutgoingEdges() { 
            when(storageAdapter.getNode("node-a", "tenant-1")).thenReturn(nodeA); 
            when(storageAdapter.getOutgoingEdges("node-a", "tenant-1")).thenReturn(List.of()); 

            List<GraphNode> neighbors = engine.getNeighbors("node-a", 1, "tenant-1"); 
            assertThat(neighbors).isEmpty(); 
        }
    }

    // =========================================================================
    // SHORTEST PATH
    // =========================================================================

    @Nested
    @DisplayName("findShortestPath")
    class FindShortestPath {

        @BeforeEach
        void init() { 
            engine.initialize(Map.of()); 
            engine.start(); 
        }

        @Test
        @DisplayName("should find direct shortest path between two connected nodes")
        void shouldFindDirectShortestPath() { 
            // findShortestPath uses getOutgoingEdges for BFS, getNode for path reconstruction
            when(storageAdapter.getOutgoingEdges("node-a", "tenant-1")) 
                    .thenReturn(List.of(edgeAB)); 
            when(storageAdapter.getNode("node-a", "tenant-1")).thenReturn(nodeA); 
            when(storageAdapter.getNode("node-b", "tenant-1")).thenReturn(nodeB); 

            List<GraphNode> path = engine.findShortestPath("node-a", "node-b", "tenant-1"); 

            assertThat(path).isNotEmpty(); 
            assertThat(path.get(0).getId()).isEqualTo("node-a");
            assertThat(path.get(path.size() - 1).getId()).isEqualTo("node-b");
        }

        @Test
        @DisplayName("should return empty list when source node has no outgoing edges and target unreachable")
        void shouldReturnEmptyWhenSourceMissing() { 
            // findShortestPath performs BFS using getOutgoingEdges; it doesn't getNode the source
            when(storageAdapter.getOutgoingEdges("no-src", "tenant-1")).thenReturn(List.of()); 

            List<GraphNode> path = engine.findShortestPath("no-src", "node-b", "tenant-1"); 
            assertThat(path).isEmpty(); 
        }

        @Test
        @DisplayName("should return empty list when no path exists")
        void shouldReturnEmptyWhenNoPath() { 
            when(storageAdapter.getOutgoingEdges("node-a", "tenant-1")).thenReturn(List.of()); 

            List<GraphNode> path = engine.findShortestPath("node-a", "node-c", "tenant-1"); 
            assertThat(path).isEmpty(); 
        }
    }

    // =========================================================================
    // ALL PATHS
    // =========================================================================

    @Nested
    @DisplayName("findAllPaths")
    class FindAllPaths {

        @BeforeEach
        void init() { 
            engine.initialize(Map.of()); 
            engine.start(); 
        }

        @Test
        @DisplayName("should find all paths up to maxLength")
        void shouldFindAllPaths() { 
            when(storageAdapter.getNode("node-a", "tenant-1")).thenReturn(nodeA); 
            when(storageAdapter.getOutgoingEdges("node-a", "tenant-1")) 
                    .thenReturn(List.of(edgeAB)); 
            when(storageAdapter.getNode("node-b", "tenant-1")).thenReturn(nodeB); 
            lenient().when(storageAdapter.getOutgoingEdges("node-b", "tenant-1")) 
                    .thenReturn(List.of()); 

            List<List<GraphNode>> paths = engine.findAllPaths("node-a", "node-b", 5, "tenant-1"); 

            assertThat(paths).isNotEmpty(); 
        }

        @Test
        @DisplayName("should return empty list when source node does not exist")
        void shouldReturnEmptyWhenSourceMissing() { 
            when(storageAdapter.getNode("no-src", "tenant-1")).thenReturn(null); 

            List<List<GraphNode>> paths = engine.findAllPaths("no-src", "node-b", 5, "tenant-1"); 
            assertThat(paths).isEmpty(); 
        }
    }
}
