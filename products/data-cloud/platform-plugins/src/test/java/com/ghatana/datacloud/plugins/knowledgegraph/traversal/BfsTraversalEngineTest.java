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
 * lifecycle (including the strict initialize-before-start contract), // GH-90000
 * neighbor discovery, shortest-path finding, and all-paths enumeration.
 *
 * @doc.type test
 * @doc.purpose Validate BFS traversal operations: neighbors, shortest path, all paths
 * @doc.layer product
 */
@DisplayName("BfsTraversalEngine Tests")
@ExtendWith(MockitoExtension.class) // GH-90000
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
    void setUp() { // GH-90000
        engine = new BfsTraversalEngine(storageAdapter); // GH-90000

        nodeA = GraphNode.builder() // GH-90000
                .id("node-a")
                .type("SERVICE")
                .labels(Set.of()) // GH-90000
                .properties(Map.of()) // GH-90000
                .tenantId("tenant-1")
                .build(); // GH-90000

        nodeB = GraphNode.builder() // GH-90000
                .id("node-b")
                .type("SERVICE")
                .labels(Set.of()) // GH-90000
                .properties(Map.of()) // GH-90000
                .tenantId("tenant-1")
                .build(); // GH-90000

        nodeC = GraphNode.builder() // GH-90000
                .id("node-c")
                .type("SERVICE")
                .labels(Set.of()) // GH-90000
                .properties(Map.of()) // GH-90000
                .tenantId("tenant-1")
                .build(); // GH-90000

        edgeAB = GraphEdge.builder() // GH-90000
                .id("edge-ab")
                .sourceNodeId("node-a")
                .targetNodeId("node-b")
                .tenantId("tenant-1")
                .relationshipType("CALLS")
                .build(); // GH-90000

        edgeBC = GraphEdge.builder() // GH-90000
                .id("edge-bc")
                .sourceNodeId("node-b")
                .targetNodeId("node-c")
                .tenantId("tenant-1")
                .relationshipType("CALLS")
                .build(); // GH-90000
    }

    // =========================================================================
    // LIFECYCLE
    // =========================================================================

    @Nested
    @DisplayName("Lifecycle")
    class Lifecycle {

        @Test
        @DisplayName("should be healthy after initialize and start")
        void shouldBeHealthyAfterInitializeAndStart() { // GH-90000
            engine.initialize(Map.of()); // GH-90000
            engine.start(); // GH-90000

            assertThat(engine.isHealthy()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("start should throw IllegalStateException when not initialized")
        void startShouldThrowWhenNotInitialized() { // GH-90000
            assertThatThrownBy(() -> engine.start()) // GH-90000
                    .isInstanceOf(IllegalStateException.class); // GH-90000
        }

        @Test
        @DisplayName("should not be healthy after stop")
        void shouldNotBeHealthyAfterStop() { // GH-90000
            engine.initialize(Map.of()); // GH-90000
            engine.start(); // GH-90000
            engine.stop(); // GH-90000

            assertThat(engine.isHealthy()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("should not be healthy after shutdown")
        void shouldNotBeHealthyAfterShutdown() { // GH-90000
            engine.initialize(Map.of()); // GH-90000
            engine.start(); // GH-90000
            engine.shutdown(); // GH-90000

            assertThat(engine.isHealthy()).isFalse(); // GH-90000
        }
    }

    // =========================================================================
    // GET NEIGHBORS
    // =========================================================================

    @Nested
    @DisplayName("getNeighbors")
    class GetNeighbors {

        @BeforeEach
        void init() { // GH-90000
            engine.initialize(Map.of()); // GH-90000
            engine.start(); // GH-90000
        }

        @Test
        @DisplayName("should return direct neighbor at depth 1")
        void shouldReturnDirectNeighbor() { // GH-90000
            when(storageAdapter.getNode("node-a", "tenant-1")).thenReturn(nodeA); // GH-90000
            when(storageAdapter.getOutgoingEdges("node-a", "tenant-1")) // GH-90000
                    .thenReturn(List.of(edgeAB)); // GH-90000
            when(storageAdapter.getNode("node-b", "tenant-1")).thenReturn(nodeB); // GH-90000

            List<GraphNode> neighbors = engine.getNeighbors("node-a", 1, "tenant-1"); // GH-90000

            assertThat(neighbors).hasSize(1); // GH-90000
            assertThat(neighbors.get(0).getId()).isEqualTo("node-b");
        }

        @Test
        @DisplayName("should return transitive neighbors at depth 2")
        void shouldReturnTransitiveNeighborsAtDepth2() { // GH-90000
            when(storageAdapter.getNode("node-a", "tenant-1")).thenReturn(nodeA); // GH-90000
            when(storageAdapter.getOutgoingEdges("node-a", "tenant-1")) // GH-90000
                    .thenReturn(List.of(edgeAB)); // GH-90000
            when(storageAdapter.getNode("node-b", "tenant-1")).thenReturn(nodeB); // GH-90000
            when(storageAdapter.getOutgoingEdges("node-b", "tenant-1")) // GH-90000
                    .thenReturn(List.of(edgeBC)); // GH-90000
            when(storageAdapter.getNode("node-c", "tenant-1")).thenReturn(nodeC); // GH-90000

            List<GraphNode> neighbors = engine.getNeighbors("node-a", 2, "tenant-1"); // GH-90000

            assertThat(neighbors).extracting(GraphNode::getId) // GH-90000
                    .containsExactlyInAnyOrder("node-b", "node-c"); // GH-90000
        }

        @Test
        @DisplayName("should return empty list when start node does not exist")
        void shouldReturnEmptyWhenStartNodeMissing() { // GH-90000
            when(storageAdapter.getNode("missing", "tenant-1")).thenReturn(null); // GH-90000

            List<GraphNode> neighbors = engine.getNeighbors("missing", 1, "tenant-1"); // GH-90000
            assertThat(neighbors).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should return empty list when node has no outgoing edges")
        void shouldReturnEmptyWhenNoOutgoingEdges() { // GH-90000
            when(storageAdapter.getNode("node-a", "tenant-1")).thenReturn(nodeA); // GH-90000
            when(storageAdapter.getOutgoingEdges("node-a", "tenant-1")).thenReturn(List.of()); // GH-90000

            List<GraphNode> neighbors = engine.getNeighbors("node-a", 1, "tenant-1"); // GH-90000
            assertThat(neighbors).isEmpty(); // GH-90000
        }
    }

    // =========================================================================
    // SHORTEST PATH
    // =========================================================================

    @Nested
    @DisplayName("findShortestPath")
    class FindShortestPath {

        @BeforeEach
        void init() { // GH-90000
            engine.initialize(Map.of()); // GH-90000
            engine.start(); // GH-90000
        }

        @Test
        @DisplayName("should find direct shortest path between two connected nodes")
        void shouldFindDirectShortestPath() { // GH-90000
            // findShortestPath uses getOutgoingEdges for BFS, getNode for path reconstruction
            when(storageAdapter.getOutgoingEdges("node-a", "tenant-1")) // GH-90000
                    .thenReturn(List.of(edgeAB)); // GH-90000
            when(storageAdapter.getNode("node-a", "tenant-1")).thenReturn(nodeA); // GH-90000
            when(storageAdapter.getNode("node-b", "tenant-1")).thenReturn(nodeB); // GH-90000

            List<GraphNode> path = engine.findShortestPath("node-a", "node-b", "tenant-1"); // GH-90000

            assertThat(path).isNotEmpty(); // GH-90000
            assertThat(path.get(0).getId()).isEqualTo("node-a");
            assertThat(path.get(path.size() - 1).getId()).isEqualTo("node-b");
        }

        @Test
        @DisplayName("should return empty list when source node has no outgoing edges and target unreachable")
        void shouldReturnEmptyWhenSourceMissing() { // GH-90000
            // findShortestPath performs BFS using getOutgoingEdges; it doesn't getNode the source
            when(storageAdapter.getOutgoingEdges("no-src", "tenant-1")).thenReturn(List.of()); // GH-90000

            List<GraphNode> path = engine.findShortestPath("no-src", "node-b", "tenant-1"); // GH-90000
            assertThat(path).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should return empty list when no path exists")
        void shouldReturnEmptyWhenNoPath() { // GH-90000
            when(storageAdapter.getOutgoingEdges("node-a", "tenant-1")).thenReturn(List.of()); // GH-90000

            List<GraphNode> path = engine.findShortestPath("node-a", "node-c", "tenant-1"); // GH-90000
            assertThat(path).isEmpty(); // GH-90000
        }
    }

    // =========================================================================
    // ALL PATHS
    // =========================================================================

    @Nested
    @DisplayName("findAllPaths")
    class FindAllPaths {

        @BeforeEach
        void init() { // GH-90000
            engine.initialize(Map.of()); // GH-90000
            engine.start(); // GH-90000
        }

        @Test
        @DisplayName("should find all paths up to maxLength")
        void shouldFindAllPaths() { // GH-90000
            when(storageAdapter.getNode("node-a", "tenant-1")).thenReturn(nodeA); // GH-90000
            when(storageAdapter.getOutgoingEdges("node-a", "tenant-1")) // GH-90000
                    .thenReturn(List.of(edgeAB)); // GH-90000
            when(storageAdapter.getNode("node-b", "tenant-1")).thenReturn(nodeB); // GH-90000
            lenient().when(storageAdapter.getOutgoingEdges("node-b", "tenant-1")) // GH-90000
                    .thenReturn(List.of()); // GH-90000

            List<List<GraphNode>> paths = engine.findAllPaths("node-a", "node-b", 5, "tenant-1"); // GH-90000

            assertThat(paths).isNotEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should return empty list when source node does not exist")
        void shouldReturnEmptyWhenSourceMissing() { // GH-90000
            when(storageAdapter.getNode("no-src", "tenant-1")).thenReturn(null); // GH-90000

            List<List<GraphNode>> paths = engine.findAllPaths("no-src", "node-b", 5, "tenant-1"); // GH-90000
            assertThat(paths).isEmpty(); // GH-90000
        }
    }
}
