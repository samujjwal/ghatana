package com.ghatana.datacloud.plugins.knowledgegraph.analytics;

import com.ghatana.datacloud.plugins.knowledgegraph.model.GraphEdge;
import com.ghatana.datacloud.plugins.knowledgegraph.model.GraphNode;
import com.ghatana.datacloud.plugins.knowledgegraph.model.GraphQuery;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link CentralityAnalyticsEngine}.
 *
 * <p>CentralityAnalyticsEngine is synchronous and depends only on the
 * {@link GraphStorageAdapter} interface, which is mocked via Mockito.
 * Tests verify lifecycle, centrality calculations, community detection,
 * impact analysis, and graph statistics with stubbed storage data.
 *
 * @doc.type test
 * @doc.purpose Validate graph analytics operations: centrality, communities, impact, statistics
 * @doc.layer product
 */
@DisplayName("CentralityAnalyticsEngine Tests [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
class CentralityAnalyticsEngineTest {

    @Mock
    private GraphStorageAdapter storageAdapter;

    private CentralityAnalyticsEngine engine;

    private GraphNode nodeA;
    private GraphNode nodeB;
    private GraphNode nodeC;
    private GraphEdge edgeAB;
    private GraphEdge edgeBC;

    @BeforeEach
    void setUp() { // GH-90000
        engine = new CentralityAnalyticsEngine(storageAdapter); // GH-90000
        engine.initialize(Map.of()); // GH-90000
        engine.start(); // GH-90000

        nodeA = GraphNode.builder() // GH-90000
                .id("node-a [GH-90000]")
                .type("SERVICE [GH-90000]")
                .labels(Set.of("java [GH-90000]"))
                .properties(Map.of("name", "ServiceA")) // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .build(); // GH-90000

        nodeB = GraphNode.builder() // GH-90000
                .id("node-b [GH-90000]")
                .type("SERVICE [GH-90000]")
                .labels(Set.of("java [GH-90000]"))
                .properties(Map.of("name", "ServiceB")) // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .build(); // GH-90000

        nodeC = GraphNode.builder() // GH-90000
                .id("node-c [GH-90000]")
                .type("SERVICE [GH-90000]")
                .labels(Set.of("java [GH-90000]"))
                .properties(Map.of("name", "ServiceC")) // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .build(); // GH-90000

        edgeAB = GraphEdge.builder() // GH-90000
                .id("edge-ab [GH-90000]")
                .sourceNodeId("node-a [GH-90000]")
                .targetNodeId("node-b [GH-90000]")
                .tenantId("tenant-1 [GH-90000]")
                .relationshipType("CALLS [GH-90000]")
                .build(); // GH-90000

        edgeBC = GraphEdge.builder() // GH-90000
                .id("edge-bc [GH-90000]")
                .sourceNodeId("node-b [GH-90000]")
                .targetNodeId("node-c [GH-90000]")
                .tenantId("tenant-1 [GH-90000]")
                .relationshipType("CALLS [GH-90000]")
                .build(); // GH-90000
    }

    // =========================================================================
    // LIFECYCLE
    // =========================================================================

    @Nested
    @DisplayName("Lifecycle [GH-90000]")
    class Lifecycle {

        @Test
        @DisplayName("should be healthy after start [GH-90000]")
        void shouldBeHealthyAfterStart() { // GH-90000
            assertThat(engine.isHealthy()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should not be healthy after stop [GH-90000]")
        void shouldNotBeHealthyAfterStop() { // GH-90000
            engine.stop(); // GH-90000
            assertThat(engine.isHealthy()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("should not be healthy after shutdown [GH-90000]")
        void shouldNotBeHealthyAfterShutdown() { // GH-90000
            engine.shutdown(); // GH-90000
            assertThat(engine.isHealthy()).isFalse(); // GH-90000
        }
    }

    // =========================================================================
    // BETWEENNESS CENTRALITY
    // =========================================================================

    @Nested
    @DisplayName("calculateBetweennessCentrality [GH-90000]")
    class BetweennessCentrality {

        @Test
        @DisplayName("should return centrality map for all nodes [GH-90000]")
        void shouldReturnCentralityMap() { // GH-90000
            when(storageAdapter.queryNodes(any(GraphQuery.class))) // GH-90000
                    .thenReturn(List.of(nodeA, nodeB, nodeC)); // GH-90000
            lenient().when(storageAdapter.getOutgoingEdges("node-a", "tenant-1")) // GH-90000
                    .thenReturn(List.of(edgeAB)); // GH-90000
            lenient().when(storageAdapter.getOutgoingEdges("node-b", "tenant-1")) // GH-90000
                    .thenReturn(List.of(edgeBC)); // GH-90000
            lenient().when(storageAdapter.getOutgoingEdges("node-c", "tenant-1")) // GH-90000
                    .thenReturn(List.of()); // GH-90000

            Map<String, Double> centrality = engine.calculateBetweennessCentrality("tenant-1 [GH-90000]");

            assertThat(centrality).containsKeys("node-a", "node-b", "node-c"); // GH-90000
            assertThat(centrality.get("node-b [GH-90000]")).isGreaterThanOrEqualTo(0.0);
        }

        @Test
        @DisplayName("should return empty map when no nodes exist [GH-90000]")
        void shouldReturnEmptyMapWhenNoNodes() { // GH-90000
            when(storageAdapter.queryNodes(any(GraphQuery.class))).thenReturn(List.of()); // GH-90000

            Map<String, Double> centrality = engine.calculateBetweennessCentrality("tenant-empty [GH-90000]");
            assertThat(centrality).isEmpty(); // GH-90000
        }
    }

    // =========================================================================
    // CLOSENESS CENTRALITY
    // =========================================================================

    @Nested
    @DisplayName("calculateClosenessCentrality [GH-90000]")
    class ClosenessCentrality {

        @Test
        @DisplayName("should return closeness map for all nodes [GH-90000]")
        void shouldReturnClosenessMap() { // GH-90000
            when(storageAdapter.queryNodes(any(GraphQuery.class))) // GH-90000
                    .thenReturn(List.of(nodeA, nodeB, nodeC)); // GH-90000
            lenient().when(storageAdapter.getOutgoingEdges(anyString(), eq("tenant-1 [GH-90000]")))
                    .thenReturn(List.of()); // GH-90000

            Map<String, Double> closeness = engine.calculateClosenessCentrality("tenant-1 [GH-90000]");

            assertThat(closeness).containsKeys("node-a", "node-b", "node-c"); // GH-90000
        }

        @Test
        @DisplayName("should return empty map for tenant with no nodes [GH-90000]")
        void shouldReturnEmptyMapForEmptyTenant() { // GH-90000
            when(storageAdapter.queryNodes(any(GraphQuery.class))).thenReturn(List.of()); // GH-90000

            Map<String, Double> closeness = engine.calculateClosenessCentrality("empty-tenant [GH-90000]");
            assertThat(closeness).isEmpty(); // GH-90000
        }
    }

    // =========================================================================
    // COMMUNITY DETECTION
    // =========================================================================

    @Nested
    @DisplayName("detectCommunities [GH-90000]")
    class CommunityDetection {

        @Test
        @DisplayName("should detect communities and return community assignments [GH-90000]")
        void shouldDetectCommunities() { // GH-90000
            when(storageAdapter.queryNodes(any(GraphQuery.class))) // GH-90000
                    .thenReturn(List.of(nodeA, nodeB, nodeC)); // GH-90000
            lenient().when(storageAdapter.getNodeEdges(anyString(), eq("tenant-1 [GH-90000]")))
                    .thenReturn(List.of()); // GH-90000

            Map<String, Integer> communities = engine.detectCommunities("tenant-1 [GH-90000]");

            assertThat(communities).containsKeys("node-a", "node-b", "node-c"); // GH-90000
        }

        @Test
        @DisplayName("should return empty map when no nodes exist [GH-90000]")
        void shouldReturnEmptyWhenNoNodes() { // GH-90000
            when(storageAdapter.queryNodes(any(GraphQuery.class))).thenReturn(List.of()); // GH-90000

            Map<String, Integer> communities = engine.detectCommunities("no-tenant [GH-90000]");
            assertThat(communities).isEmpty(); // GH-90000
        }
    }

    // =========================================================================
    // IMPACT ANALYSIS
    // =========================================================================

    @Nested
    @DisplayName("analyzeImpact [GH-90000]")
    class ImpactAnalysis {

        @Test
        @DisplayName("should analyze impact starting from a given node [GH-90000]")
        void shouldAnalyzeImpact() { // GH-90000
            // analyzeImpact uses getIncomingEdges (nodes that depend on the source) // GH-90000
            GraphEdge edgeBdependsOnA = GraphEdge.builder() // GH-90000
                    .id("edge-ba [GH-90000]")
                    .sourceNodeId("node-b [GH-90000]")
                    .targetNodeId("node-a [GH-90000]")
                    .tenantId("tenant-1 [GH-90000]")
                    .relationshipType("DEPENDS_ON [GH-90000]")
                    .build(); // GH-90000
            when(storageAdapter.getIncomingEdges("node-a", "tenant-1")) // GH-90000
                    .thenReturn(List.of(edgeBdependsOnA)); // GH-90000
            when(storageAdapter.getNode("node-b", "tenant-1")).thenReturn(nodeB); // GH-90000
            lenient().when(storageAdapter.getIncomingEdges("node-b", "tenant-1")) // GH-90000
                    .thenReturn(List.of()); // GH-90000

            List<GraphNode> impacted = engine.analyzeImpact("node-a", 1, "tenant-1"); // GH-90000

            assertThat(impacted).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("should return empty list when node has no dependents [GH-90000]")
        void shouldReturnEmptyForNodeWithNoDependents() { // GH-90000
            when(storageAdapter.getIncomingEdges("node-leaf", "tenant-1")) // GH-90000
                    .thenReturn(List.of()); // GH-90000

            List<GraphNode> impacted = engine.analyzeImpact("node-leaf", 2, "tenant-1"); // GH-90000
            assertThat(impacted).isEmpty(); // GH-90000
        }
    }

    // =========================================================================
    // GRAPH STATISTICS
    // =========================================================================

    @Nested
    @DisplayName("calculateStatistics [GH-90000]")
    class GraphStatisticsTest {

        @Test
        @DisplayName("should return statistics with node and edge counts [GH-90000]")
        void shouldReturnStatistics() { // GH-90000
            when(storageAdapter.queryNodes(any(GraphQuery.class))) // GH-90000
                    .thenReturn(List.of(nodeA, nodeB, nodeC)); // GH-90000
            lenient().when(storageAdapter.queryEdges(any(GraphQuery.class))) // GH-90000
                    .thenReturn(List.of(edgeAB, edgeBC)); // GH-90000

            GraphAnalyticsEngine.GraphStatistics stats =
                    engine.calculateStatistics("tenant-1 [GH-90000]");

            assertThat(stats).isNotNull(); // GH-90000
            assertThat(stats.nodeCount()).isEqualTo(3); // GH-90000
        }
    }
}
