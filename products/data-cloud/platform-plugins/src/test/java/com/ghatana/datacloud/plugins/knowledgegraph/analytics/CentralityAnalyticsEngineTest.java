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
@DisplayName("CentralityAnalyticsEngine Tests")
@ExtendWith(MockitoExtension.class) 
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
    void setUp() { 
        engine = new CentralityAnalyticsEngine(storageAdapter); 
        engine.initialize(Map.of()); 
        engine.start(); 

        nodeA = GraphNode.builder() 
                .id("node-a")
                .type("SERVICE")
                .labels(Set.of("java"))
                .properties(Map.of("name", "ServiceA")) 
                .tenantId("tenant-1")
                .build(); 

        nodeB = GraphNode.builder() 
                .id("node-b")
                .type("SERVICE")
                .labels(Set.of("java"))
                .properties(Map.of("name", "ServiceB")) 
                .tenantId("tenant-1")
                .build(); 

        nodeC = GraphNode.builder() 
                .id("node-c")
                .type("SERVICE")
                .labels(Set.of("java"))
                .properties(Map.of("name", "ServiceC")) 
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
        @DisplayName("should be healthy after start")
        void shouldBeHealthyAfterStart() { 
            assertThat(engine.isHealthy()).isTrue(); 
        }

        @Test
        @DisplayName("should not be healthy after stop")
        void shouldNotBeHealthyAfterStop() { 
            engine.stop(); 
            assertThat(engine.isHealthy()).isFalse(); 
        }

        @Test
        @DisplayName("should not be healthy after shutdown")
        void shouldNotBeHealthyAfterShutdown() { 
            engine.shutdown(); 
            assertThat(engine.isHealthy()).isFalse(); 
        }
    }

    // =========================================================================
    // BETWEENNESS CENTRALITY
    // =========================================================================

    @Nested
    @DisplayName("calculateBetweennessCentrality")
    class BetweennessCentrality {

        @Test
        @DisplayName("should return centrality map for all nodes")
        void shouldReturnCentralityMap() { 
            when(storageAdapter.queryNodes(any(GraphQuery.class))) 
                    .thenReturn(List.of(nodeA, nodeB, nodeC)); 
            lenient().when(storageAdapter.getOutgoingEdges("node-a", "tenant-1")) 
                    .thenReturn(List.of(edgeAB)); 
            lenient().when(storageAdapter.getOutgoingEdges("node-b", "tenant-1")) 
                    .thenReturn(List.of(edgeBC)); 
            lenient().when(storageAdapter.getOutgoingEdges("node-c", "tenant-1")) 
                    .thenReturn(List.of()); 

            Map<String, Double> centrality = engine.calculateBetweennessCentrality("tenant-1");

            assertThat(centrality).containsKeys("node-a", "node-b", "node-c"); 
            assertThat(centrality.get("node-b")).isGreaterThanOrEqualTo(0.0);
        }

        @Test
        @DisplayName("should return empty map when no nodes exist")
        void shouldReturnEmptyMapWhenNoNodes() { 
            when(storageAdapter.queryNodes(any(GraphQuery.class))).thenReturn(List.of()); 

            Map<String, Double> centrality = engine.calculateBetweennessCentrality("tenant-empty");
            assertThat(centrality).isEmpty(); 
        }
    }

    // =========================================================================
    // CLOSENESS CENTRALITY
    // =========================================================================

    @Nested
    @DisplayName("calculateClosenessCentrality")
    class ClosenessCentrality {

        @Test
        @DisplayName("should return closeness map for all nodes")
        void shouldReturnClosenessMap() { 
            when(storageAdapter.queryNodes(any(GraphQuery.class))) 
                    .thenReturn(List.of(nodeA, nodeB, nodeC)); 
            lenient().when(storageAdapter.getOutgoingEdges(anyString(), eq("tenant-1")))
                    .thenReturn(List.of()); 

            Map<String, Double> closeness = engine.calculateClosenessCentrality("tenant-1");

            assertThat(closeness).containsKeys("node-a", "node-b", "node-c"); 
        }

        @Test
        @DisplayName("should return empty map for tenant with no nodes")
        void shouldReturnEmptyMapForEmptyTenant() { 
            when(storageAdapter.queryNodes(any(GraphQuery.class))).thenReturn(List.of()); 

            Map<String, Double> closeness = engine.calculateClosenessCentrality("empty-tenant");
            assertThat(closeness).isEmpty(); 
        }
    }

    // =========================================================================
    // COMMUNITY DETECTION
    // =========================================================================

    @Nested
    @DisplayName("detectCommunities")
    class CommunityDetection {

        @Test
        @DisplayName("should detect communities and return community assignments")
        void shouldDetectCommunities() { 
            when(storageAdapter.queryNodes(any(GraphQuery.class))) 
                    .thenReturn(List.of(nodeA, nodeB, nodeC)); 
            lenient().when(storageAdapter.getNodeEdges(anyString(), eq("tenant-1")))
                    .thenReturn(List.of()); 

            Map<String, Integer> communities = engine.detectCommunities("tenant-1");

            assertThat(communities).containsKeys("node-a", "node-b", "node-c"); 
        }

        @Test
        @DisplayName("should return empty map when no nodes exist")
        void shouldReturnEmptyWhenNoNodes() { 
            when(storageAdapter.queryNodes(any(GraphQuery.class))).thenReturn(List.of()); 

            Map<String, Integer> communities = engine.detectCommunities("no-tenant");
            assertThat(communities).isEmpty(); 
        }
    }

    // =========================================================================
    // IMPACT ANALYSIS
    // =========================================================================

    @Nested
    @DisplayName("analyzeImpact")
    class ImpactAnalysis {

        @Test
        @DisplayName("should analyze impact starting from a given node")
        void shouldAnalyzeImpact() { 
            // analyzeImpact uses getIncomingEdges (nodes that depend on the source) 
            GraphEdge edgeBdependsOnA = GraphEdge.builder() 
                    .id("edge-ba")
                    .sourceNodeId("node-b")
                    .targetNodeId("node-a")
                    .tenantId("tenant-1")
                    .relationshipType("DEPENDS_ON")
                    .build(); 
            when(storageAdapter.getIncomingEdges("node-a", "tenant-1")) 
                    .thenReturn(List.of(edgeBdependsOnA)); 
            when(storageAdapter.getNode("node-b", "tenant-1")).thenReturn(nodeB); 
            lenient().when(storageAdapter.getIncomingEdges("node-b", "tenant-1")) 
                    .thenReturn(List.of()); 

            List<GraphNode> impacted = engine.analyzeImpact("node-a", 1, "tenant-1"); 

            assertThat(impacted).isNotNull(); 
        }

        @Test
        @DisplayName("should return empty list when node has no dependents")
        void shouldReturnEmptyForNodeWithNoDependents() { 
            when(storageAdapter.getIncomingEdges("node-leaf", "tenant-1")) 
                    .thenReturn(List.of()); 

            List<GraphNode> impacted = engine.analyzeImpact("node-leaf", 2, "tenant-1"); 
            assertThat(impacted).isEmpty(); 
        }
    }

    // =========================================================================
    // GRAPH STATISTICS
    // =========================================================================

    @Nested
    @DisplayName("calculateStatistics")
    class GraphStatisticsTest {

        @Test
        @DisplayName("should return statistics with node and edge counts")
        void shouldReturnStatistics() { 
            when(storageAdapter.queryNodes(any(GraphQuery.class))) 
                    .thenReturn(List.of(nodeA, nodeB, nodeC)); 
            lenient().when(storageAdapter.queryEdges(any(GraphQuery.class))) 
                    .thenReturn(List.of(edgeAB, edgeBC)); 

            GraphAnalyticsEngine.GraphStatistics stats =
                    engine.calculateStatistics("tenant-1");

            assertThat(stats).isNotNull(); 
            assertThat(stats.nodeCount()).isEqualTo(3); 
        }
    }
}
