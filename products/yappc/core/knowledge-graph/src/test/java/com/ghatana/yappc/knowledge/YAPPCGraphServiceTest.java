package com.ghatana.yappc.knowledge;

import com.ghatana.datacloud.plugins.knowledgegraph.KnowledgeGraphPlugin;
import com.ghatana.datacloud.plugins.knowledgegraph.model.GraphEdge;
import com.ghatana.datacloud.plugins.knowledgegraph.model.GraphNode;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.knowledge.embedding.KGEmbeddingService;
import com.ghatana.yappc.knowledge.persistence.KGEdgeRepository;
import com.ghatana.yappc.knowledge.persistence.KGNodeRepository;
import com.ghatana.yappc.knowledge.query.KGQueryService;
import com.ghatana.yappc.knowledge.query.KGSemanticSearchService;
import com.ghatana.yappc.knowledge.model.YAPPCGraphEdge;
import com.ghatana.yappc.knowledge.model.YAPPCGraphMetadata;
import com.ghatana.yappc.knowledge.model.YAPPCGraphNode;
import com.ghatana.yappc.knowledge.model.YAPPCImpactAnalysis;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * @doc.type class
 * @doc.purpose Verify YAPPCGraphService routing, mapping, and validation
 * @doc.layer service
 * @doc.pattern Test
 */
@DisplayName("YAPPCGraphService")
class YAPPCGraphServiceTest extends EventloopTestBase {

    private KnowledgeGraphPlugin graphPlugin;
    private YAPPCGraphMapper mapper;
    private YAPPCGraphValidator validator;
    private KGNodeRepository nodeRepository;
    private KGEdgeRepository edgeRepository;
    private KGEmbeddingService embeddingService;
    private KGSemanticSearchService semanticSearchService;
    private KGQueryService queryService;
    private YAPPCGraphService service;

    @BeforeEach
    void setUp() { // GH-90000
        graphPlugin = mock(KnowledgeGraphPlugin.class); // GH-90000
        mapper = mock(YAPPCGraphMapper.class); // GH-90000
        validator = mock(YAPPCGraphValidator.class); // GH-90000
        nodeRepository = mock(KGNodeRepository.class); // GH-90000
        edgeRepository = mock(KGEdgeRepository.class); // GH-90000
        embeddingService = mock(KGEmbeddingService.class); // GH-90000
        semanticSearchService = mock(KGSemanticSearchService.class); // GH-90000
        queryService = mock(KGQueryService.class); // GH-90000
        service = new YAPPCGraphService(graphPlugin, mapper, validator); // GH-90000
    }

    private YAPPCGraphNode yappcNode(String id, YAPPCGraphNode.YAPPCNodeType type) { // GH-90000
        return YAPPCGraphNode.builder() // GH-90000
                .id(id) // GH-90000
                .type(type) // GH-90000
                .name("MyComponent")
                .description("A test component")
                .properties(Map.of()) // GH-90000
                .tags(Set.of("java"))
                .metadata(new YAPPCGraphMetadata( // GH-90000
                        "tenant-1", "proj-1", "ws-1",
                        "tester", Instant.now(), Instant.now(), // GH-90000
                        "1.0", Map.of())) // GH-90000
                .build(); // GH-90000
    }

    private GraphNode dcNode(String id) { // GH-90000
        return GraphNode.builder() // GH-90000
                .id(id).type("CLASS")
                .properties(Map.of()) // GH-90000
                .labels(Set.of()) // GH-90000
                .tenantId("tenant-1")
                .build(); // GH-90000
    }

    private GraphEdge dcEdge(String src, String tgt, String rel) { // GH-90000
        return GraphEdge.builder() // GH-90000
                .id(src + "_" + tgt + "_" + rel) // GH-90000
                .sourceNodeId(src).targetNodeId(tgt) // GH-90000
                .relationshipType(rel) // GH-90000
                .properties(Map.of()) // GH-90000
                .tenantId("tenant-1")
                .build(); // GH-90000
    }

    private YAPPCGraphEdge yappcEdge(String src, String tgt) { // GH-90000
        return YAPPCGraphEdge.builder() // GH-90000
                .id(src + "_" + tgt + "_DEPENDS_ON") // GH-90000
                .sourceNodeId(src).targetNodeId(tgt) // GH-90000
                .relationshipType(YAPPCGraphEdge.YAPPCRelationshipType.DEPENDS_ON) // GH-90000
                .properties(Map.of()) // GH-90000
                .metadata(new YAPPCGraphMetadata( // GH-90000
                        "tenant-1", "proj-1", "ws-1",
                        "tester", Instant.now(), Instant.now(), // GH-90000
                        "1.0", Map.of())) // GH-90000
                .build(); // GH-90000
    }

    @Nested
    @DisplayName("createYAPPCNode()")
    class CreateYAPPCNodeTests {

        @Test
        @DisplayName("validates, maps, persists and returns YAPPC node")
        void shouldCreateNode() { // GH-90000
            YAPPCGraphNode input = yappcNode("node-1", YAPPCGraphNode.YAPPCNodeType.SERVICE); // GH-90000
            GraphNode dc = dcNode("node-1");
            YAPPCGraphNode expected = yappcNode("node-1", YAPPCGraphNode.YAPPCNodeType.SERVICE); // GH-90000

            when(mapper.toDataCloudNode(input)).thenReturn(dc); // GH-90000
            when(graphPlugin.createNode(dc)).thenReturn(Promise.of(dc)); // GH-90000
            when(mapper.fromDataCloudNode(dc)).thenReturn(expected); // GH-90000

            YAPPCGraphNode result = runPromise(() -> service.createYAPPCNode(input)); // GH-90000

            assertThat(result).isEqualTo(expected); // GH-90000
            verify(validator).validateNode(input); // GH-90000
            verify(mapper).toDataCloudNode(input); // GH-90000
            verify(graphPlugin).createNode(dc); // GH-90000
            verify(mapper).fromDataCloudNode(dc); // GH-90000
        }

        @Test
        @DisplayName("validation failure propagates as IllegalArgumentException")
        void shouldPropagateValidationFailure() { // GH-90000
            YAPPCGraphNode bad = yappcNode("", YAPPCGraphNode.YAPPCNodeType.CLASS); // GH-90000
            doThrow(new IllegalArgumentException("Node id cannot be null or blank"))
                    .when(validator).validateNode(bad); // GH-90000

            assertThatThrownBy(() -> runPromise(() -> service.createYAPPCNode(bad))) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("Node id");

            verify(graphPlugin, never()).createNode(any()); // GH-90000
        }

        @Test
        @DisplayName("persists to JDBC repository before publishing to plugin when repository is configured")
        void shouldPersistToRepositoryWhenConfigured() { // GH-90000
            YAPPCGraphNode input = yappcNode("node-2", YAPPCGraphNode.YAPPCNodeType.SERVICE); // GH-90000
            GraphNode dc = dcNode("node-2");

            service = new YAPPCGraphService( // GH-90000
                    graphPlugin,
                    mapper,
                    validator,
                    nodeRepository,
                    edgeRepository,
                    embeddingService,
                    semanticSearchService);
            when(nodeRepository.saveNode(input)).thenReturn(Promise.of(input)); // GH-90000
            when(mapper.toDataCloudNode(input)).thenReturn(dc); // GH-90000
            when(graphPlugin.createNode(dc)).thenReturn(Promise.of(dc)); // GH-90000
            when(mapper.fromDataCloudNode(dc)).thenReturn(input); // GH-90000
            when(embeddingService.indexNode(input)).thenReturn(Promise.of((Void) null)); // GH-90000

            YAPPCGraphNode result = runPromise(() -> service.createYAPPCNode(input)); // GH-90000

            assertThat(result).isEqualTo(input); // GH-90000
            verify(nodeRepository).saveNode(input); // GH-90000
            verify(graphPlugin).createNode(dc); // GH-90000
            verify(embeddingService).indexNode(input); // GH-90000
        }

        @Test
        @DisplayName("returns persisted node without publishing when plugin is absent")
        void shouldReturnPersistedNodeWhenPluginIsAbsent() { // GH-90000
            YAPPCGraphNode input = yappcNode("node-3", YAPPCGraphNode.YAPPCNodeType.SERVICE); // GH-90000

            service = new YAPPCGraphService(null, mapper, validator, nodeRepository, edgeRepository); // GH-90000
            when(nodeRepository.saveNode(input)).thenReturn(Promise.of(input)); // GH-90000

            YAPPCGraphNode result = runPromise(() -> service.createYAPPCNode(input)); // GH-90000

            assertThat(result).isEqualTo(input); // GH-90000
            verify(mapper, never()).toDataCloudNode(any()); // GH-90000
        }
    }

    @Nested
    @DisplayName("findCodeDependencies()")
    class FindCodeDependenciesTests {

        @Test
        @DisplayName("queries plugin for DEPENDS_ON, IMPORTS, EXTENDS, IMPLEMENTS edges and maps result")
        void shouldFindDependencies() { // GH-90000
            GraphEdge dcDep1 = dcEdge("comp-A", "comp-B", "DEPENDS_ON"); // GH-90000
            GraphEdge dcDep2 = dcEdge("comp-A", "comp-C", "IMPORTS"); // GH-90000
            YAPPCGraphEdge e1 = yappcEdge("comp-A", "comp-B"); // GH-90000
            YAPPCGraphEdge e2 = yappcEdge("comp-A", "comp-C"); // GH-90000

            when(graphPlugin.queryEdges(any())).thenReturn(Promise.of(List.of(dcDep1, dcDep2))); // GH-90000
            when(mapper.fromDataCloudEdge(dcDep1)).thenReturn(e1); // GH-90000
            when(mapper.fromDataCloudEdge(dcDep2)).thenReturn(e2); // GH-90000

            List<YAPPCGraphEdge> result = runPromise( // GH-90000
                    () -> service.findCodeDependencies("comp-A", "tenant-1")); // GH-90000

            assertThat(result).hasSize(2).containsExactlyInAnyOrder(e1, e2); // GH-90000
        }

        @Test
        @DisplayName("returns empty list when plugin finds no edges")
        void shouldReturnEmptyWhenNoDependencies() { // GH-90000
            when(graphPlugin.queryEdges(any())).thenReturn(Promise.of(List.of())); // GH-90000

            List<YAPPCGraphEdge> result = runPromise( // GH-90000
                    () -> service.findCodeDependencies("comp-X", "tenant-1")); // GH-90000

            assertThat(result).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("uses JDBC edge repository when configured")
        void shouldUseEdgeRepositoryWhenConfigured() { // GH-90000
            YAPPCGraphEdge e1 = yappcEdge("comp-A", "comp-B"); // GH-90000
            service = new YAPPCGraphService(graphPlugin, mapper, validator, nodeRepository, edgeRepository); // GH-90000
            when(edgeRepository.findEdgesFromSource( // GH-90000
                    eq("comp-A"),
                    eq("tenant-1"),
                    eq(Set.of("DEPENDS_ON", "IMPORTS", "EXTENDS", "IMPLEMENTS")))) // GH-90000
                    .thenReturn(Promise.of(List.of(e1))); // GH-90000

            List<YAPPCGraphEdge> result = runPromise(() -> service.findCodeDependencies("comp-A", "tenant-1")); // GH-90000

            assertThat(result).containsExactly(e1); // GH-90000
            verify(graphPlugin, never()).queryEdges(any()); // GH-90000
        }
    }

    @Nested
    @DisplayName("analyzeChangeImpact()")
    class AnalyzeChangeImpactTests {

        @Test
        @DisplayName("returns impact analysis with affected node count and score")
        void shouldBuildImpactAnalysis() { // GH-90000
            GraphNode n1 = dcNode("dep-1");
            GraphNode n2 = dcNode("dep-2");
            YAPPCGraphNode yn1 = yappcNode("dep-1", YAPPCGraphNode.YAPPCNodeType.SERVICE); // GH-90000
            YAPPCGraphNode yn2 = yappcNode("dep-2", YAPPCGraphNode.YAPPCNodeType.CLASS); // GH-90000

            when(graphPlugin.getNeighbors(eq("comp-A"), eq(5), eq("tenant-1")))
                    .thenReturn(Promise.of(List.of(n1, n2))); // GH-90000
            when(mapper.fromDataCloudNode(n1)).thenReturn(yn1); // GH-90000
            when(mapper.fromDataCloudNode(n2)).thenReturn(yn2); // GH-90000

            YAPPCImpactAnalysis analysis = runPromise( // GH-90000
                    () -> service.analyzeChangeImpact("comp-A", "tenant-1")); // GH-90000

            assertThat(analysis.componentId()).isEqualTo("comp-A");
            assertThat(analysis.affectedNodes()).hasSize(2); // GH-90000
            assertThat(analysis.impactScore()).isGreaterThan(0.0); // GH-90000
        }

        @Test
        @DisplayName("returns zero impact score when no neighbors found")
        void shouldReturnZeroImpactWhenNoNeighbors() { // GH-90000
            when(graphPlugin.getNeighbors(any(), anyInt(), any())) // GH-90000
                    .thenReturn(Promise.of(List.of())); // GH-90000

            YAPPCImpactAnalysis analysis = runPromise( // GH-90000
                    () -> service.analyzeChangeImpact("isolated", "tenant-1")); // GH-90000

            assertThat(analysis.affectedNodes()).isEmpty(); // GH-90000
            assertThat(analysis.impactScore()).isEqualTo(0.0); // GH-90000
        }

            @Test
            @DisplayName("uses KGQueryService traversal when configured")
            void shouldUseQueryServiceWhenConfigured() { // GH-90000
                YAPPCGraphNode serviceNode = yappcNode("dep-svc", YAPPCGraphNode.YAPPCNodeType.SERVICE); // GH-90000
                YAPPCGraphNode apiNode = yappcNode("dep-api", YAPPCGraphNode.YAPPCNodeType.API); // GH-90000
                YAPPCGraphNode testNode = yappcNode("dep-test", YAPPCGraphNode.YAPPCNodeType.TEST); // GH-90000

                service = new YAPPCGraphService( // GH-90000
                    graphPlugin,
                    mapper,
                    validator,
                    nodeRepository,
                    edgeRepository,
                    embeddingService,
                    semanticSearchService,
                    queryService);
                when(queryService.traverse("comp-A", 3, "tenant-1")) // GH-90000
                    .thenReturn(Promise.of(List.of(serviceNode, apiNode, testNode))); // GH-90000

                YAPPCImpactAnalysis analysis = runPromise(() -> service.analyzeChangeImpact("comp-A", "tenant-1")); // GH-90000

                assertThat(analysis.affectedNodes()).containsExactly(serviceNode, apiNode, testNode); // GH-90000
                    assertThat(analysis.impactScore()).isCloseTo(0.7, org.assertj.core.data.Offset.offset(0.0000001)); // GH-90000
                assertThat(analysis.recommendations()) // GH-90000
                    .contains("Update service contracts and API documentation")
                    .contains("Review and update affected tests");
                verify(graphPlugin, never()).getNeighbors(any(), anyInt(), any()); // GH-90000
            }
    }

    @Nested
    @DisplayName("findComponentsByType()")
    class FindComponentsByTypeTests {

        @Test
        @DisplayName("queries plugin for nodes matching type and maps results")
        void shouldFindComponentsByType() { // GH-90000
            GraphNode n1 = dcNode("svc-1");
            YAPPCGraphNode yn1 = yappcNode("svc-1", YAPPCGraphNode.YAPPCNodeType.SERVICE); // GH-90000

            when(graphPlugin.queryNodes(any())).thenReturn(Promise.of(List.of(n1))); // GH-90000
            when(mapper.fromDataCloudNode(n1)).thenReturn(yn1); // GH-90000

            List<YAPPCGraphNode> result = runPromise( // GH-90000
                    () -> service.findComponentsByType("SERVICE", "tenant-1")); // GH-90000

            assertThat(result).hasSize(1); // GH-90000
            assertThat(result.get(0).type()).isEqualTo(YAPPCGraphNode.YAPPCNodeType.SERVICE); // GH-90000
        }

        @Test
        @DisplayName("uses JDBC node repository when configured")
        void shouldUseNodeRepositoryWhenConfigured() { // GH-90000
            YAPPCGraphNode yn1 = yappcNode("svc-2", YAPPCGraphNode.YAPPCNodeType.SERVICE); // GH-90000
            service = new YAPPCGraphService(graphPlugin, mapper, validator, nodeRepository, edgeRepository); // GH-90000
            when(nodeRepository.findNodesByType("SERVICE", "tenant-1", 1000)) // GH-90000
                    .thenReturn(Promise.of(List.of(yn1))); // GH-90000

            List<YAPPCGraphNode> result = runPromise(() -> service.findComponentsByType("SERVICE", "tenant-1")); // GH-90000

            assertThat(result).containsExactly(yn1); // GH-90000
            verify(graphPlugin, never()).queryNodes(any()); // GH-90000
        }
    }

        @Nested
        @DisplayName("semanticSearch")
        class SemanticSearchTests {

        @Test
        @DisplayName("returns semantic node matches when semantic search service is configured")
        void shouldReturnSemanticMatches() { // GH-90000
            YAPPCGraphNode node = yappcNode("svc-3", YAPPCGraphNode.YAPPCNodeType.SERVICE); // GH-90000
            KGSemanticSearchService.SemanticNodeMatch match =
                new KGSemanticSearchService.SemanticNodeMatch(node, 0.91, Map.of("tenantId", "tenant-1")); // GH-90000

            service = new YAPPCGraphService( // GH-90000
                graphPlugin,
                mapper,
                validator,
                nodeRepository,
                edgeRepository,
                embeddingService,
                semanticSearchService);
            when(semanticSearchService.findSimilarNodes("billing service", "tenant-1", 5, 0.75)) // GH-90000
                .thenReturn(Promise.of(List.of(match))); // GH-90000

            List<KGSemanticSearchService.SemanticNodeMatch> result = runPromise( // GH-90000
                () -> service.semanticSearch("billing service", "tenant-1", 5, 0.75)); // GH-90000

            assertThat(result).containsExactly(match); // GH-90000
        }

        @Test
        @DisplayName("returns empty list when semantic search service is absent")
        void shouldReturnEmptyWhenSemanticSearchServiceIsAbsent() { // GH-90000
            assertThat(runPromise(() -> service.semanticSearch("billing service", "tenant-1", 5, 0.75))).isEmpty(); // GH-90000
        }
        }

    @Nested
    @DisplayName("createCodeRelationship()")
    class CreateCodeRelationshipTests {

        @Test
        @DisplayName("creates edge in plugin and returns mapped YAPPC edge")
        void shouldCreateCodeRelationship() { // GH-90000
            GraphEdge createdEdge = dcEdge("comp-A", "comp-B", "DEPENDS_ON"); // GH-90000
            YAPPCGraphEdge expected = yappcEdge("comp-A", "comp-B"); // GH-90000

            when(graphPlugin.createEdge(any(GraphEdge.class))).thenReturn(Promise.of(createdEdge)); // GH-90000
            when(mapper.fromDataCloudEdge(createdEdge)).thenReturn(expected); // GH-90000

            YAPPCGraphEdge result = runPromise( // GH-90000
                    () -> service.createCodeRelationship("comp-A", "comp-B", "DEPENDS_ON", "tenant-1")); // GH-90000

            assertThat(result).isEqualTo(expected); // GH-90000
            verify(graphPlugin).createEdge(argThat(e -> // GH-90000
                    "comp-A".equals(e.getSourceNodeId()) && // GH-90000
                    "comp-B".equals(e.getTargetNodeId()) && // GH-90000
                    "DEPENDS_ON".equals(e.getRelationshipType()))); // GH-90000
        }

        @Test
        @DisplayName("persists relationship to JDBC repository when configured")
        void shouldPersistRelationshipWhenConfigured() { // GH-90000
            GraphEdge createdEdge = dcEdge("comp-A", "comp-B", "DEPENDS_ON"); // GH-90000
            YAPPCGraphEdge expected = yappcEdge("comp-A", "comp-B"); // GH-90000

            service = new YAPPCGraphService(graphPlugin, mapper, validator, nodeRepository, edgeRepository); // GH-90000
            when(edgeRepository.saveEdge(any(YAPPCGraphEdge.class))).thenReturn(Promise.of(expected)); // GH-90000
            when(graphPlugin.createEdge(any(GraphEdge.class))).thenReturn(Promise.of(createdEdge)); // GH-90000
            when(mapper.fromDataCloudEdge(createdEdge)).thenReturn(expected); // GH-90000

            YAPPCGraphEdge result = runPromise( // GH-90000
                    () -> service.createCodeRelationship("comp-A", "comp-B", "DEPENDS_ON", "tenant-1")); // GH-90000

            assertThat(result).isEqualTo(expected); // GH-90000
            verify(edgeRepository).saveEdge(any(YAPPCGraphEdge.class)); // GH-90000
        }

        @Test
        @DisplayName("returns persisted relationship when plugin is absent")
        void shouldReturnPersistedRelationshipWhenPluginIsAbsent() { // GH-90000
            YAPPCGraphEdge expected = yappcEdge("comp-A", "comp-B"); // GH-90000

            service = new YAPPCGraphService(null, mapper, validator, nodeRepository, edgeRepository); // GH-90000
            when(edgeRepository.saveEdge(any(YAPPCGraphEdge.class))).thenReturn(Promise.of(expected)); // GH-90000

            YAPPCGraphEdge result = runPromise( // GH-90000
                    () -> service.createCodeRelationship("comp-A", "comp-B", "DEPENDS_ON", "tenant-1")); // GH-90000

            assertThat(result).isEqualTo(expected); // GH-90000
        }
    }

    @Nested
    @DisplayName("findDependencyPath()")
    class FindDependencyPathTests {

        @Test
        @DisplayName("delegates to plugin findShortestPath and maps result nodes")
        void shouldFindDependencyPath() { // GH-90000
            GraphNode mid = dcNode("mid-1");
            YAPPCGraphNode yMid = yappcNode("mid-1", YAPPCGraphNode.YAPPCNodeType.CLASS); // GH-90000

            when(graphPlugin.findShortestPath("A", "B", "tenant-1")) // GH-90000
                    .thenReturn(Promise.of(List.of(mid))); // GH-90000
            when(mapper.fromDataCloudNode(mid)).thenReturn(yMid); // GH-90000

            List<YAPPCGraphNode> path = runPromise( // GH-90000
                    () -> service.findDependencyPath("A", "B", "tenant-1")); // GH-90000

            assertThat(path).containsExactly(yMid); // GH-90000
        }

        @Test
        @DisplayName("returns empty list when no path exists")
        void shouldReturnEmptyPathWhenNotConnected() { // GH-90000
            when(graphPlugin.findShortestPath(any(), any(), any())) // GH-90000
                    .thenReturn(Promise.of(List.of())); // GH-90000

            List<YAPPCGraphNode> path = runPromise( // GH-90000
                    () -> service.findDependencyPath("X", "Y", "tenant-1")); // GH-90000

            assertThat(path).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("uses KGQueryService path search when configured")
        void shouldUseQueryServiceForDependencyPath() { // GH-90000
            YAPPCGraphNode source = yappcNode("A", YAPPCGraphNode.YAPPCNodeType.CLASS); // GH-90000
            YAPPCGraphNode target = yappcNode("B", YAPPCGraphNode.YAPPCNodeType.SERVICE); // GH-90000

            service = new YAPPCGraphService( // GH-90000
                    graphPlugin,
                    mapper,
                    validator,
                    nodeRepository,
                    edgeRepository,
                    embeddingService,
                    semanticSearchService,
                    queryService);
            when(queryService.findPaths("A", "B", "tenant-1")) // GH-90000
                    .thenReturn(Promise.of(List.of(List.of(source, target)))); // GH-90000

            List<YAPPCGraphNode> path = runPromise(() -> service.findDependencyPath("A", "B", "tenant-1")); // GH-90000

            assertThat(path).containsExactly(source, target); // GH-90000
            verify(graphPlugin, never()).findShortestPath(any(), any(), any()); // GH-90000
        }

        @Test
        @DisplayName("returns empty path when query service has no paths")
        void shouldReturnEmptyPathWhenQueryServiceHasNoPaths() { // GH-90000
            service = new YAPPCGraphService( // GH-90000
                    graphPlugin,
                    mapper,
                    validator,
                    nodeRepository,
                    edgeRepository,
                    embeddingService,
                    semanticSearchService,
                    queryService);
            when(queryService.findPaths("A", "B", "tenant-1")).thenReturn(Promise.of(List.of())); // GH-90000

            List<YAPPCGraphNode> path = runPromise(() -> service.findDependencyPath("A", "B", "tenant-1")); // GH-90000

            assertThat(path).isEmpty(); // GH-90000
        }
    }

    @Nested
    @DisplayName("getWorkspaceDependencies()")
    class GetWorkspaceDependenciesTests {

        @Test
        @DisplayName("uses JDBC repository when configured")
        void shouldUseRepositoryWhenConfigured() { // GH-90000
            YAPPCGraphEdge calls = YAPPCGraphEdge.builder() // GH-90000
                    .id("edge-calls")
                    .sourceNodeId("comp-A")
                    .targetNodeId("comp-B")
                    .relationshipType(YAPPCGraphEdge.YAPPCRelationshipType.CALLS) // GH-90000
                    .properties(Map.of()) // GH-90000
                    .metadata(new YAPPCGraphMetadata("tenant-1", "proj-1", "ws-1", "tester", Instant.now(), Instant.now(), "1.0", Map.of())) // GH-90000
                    .build(); // GH-90000

            service = new YAPPCGraphService(graphPlugin, mapper, validator, nodeRepository, edgeRepository); // GH-90000
            when(edgeRepository.findEdgesForWorkspace("ws-1", "tenant-1", Set.of("DEPENDS_ON", "USES", "CALLS"))) // GH-90000
                    .thenReturn(Promise.of(List.of(calls))); // GH-90000

            Map<String, List<YAPPCGraphEdge>> grouped = runPromise(() -> service.getWorkspaceDependencies("ws-1", "tenant-1")); // GH-90000

            assertThat(grouped).containsKey("CALLS");
            assertThat(grouped.get("CALLS")).containsExactly(calls);
            verify(graphPlugin, never()).queryEdges(any()); // GH-90000
        }

        @Test
        @DisplayName("uses plugin query when repository is absent")
        void shouldUsePluginWhenRepositoryIsAbsent() { // GH-90000
            GraphEdge dcCall = dcEdge("comp-A", "comp-B", "CALLS"); // GH-90000
            YAPPCGraphEdge mapped = YAPPCGraphEdge.builder() // GH-90000
                    .id("edge-calls")
                    .sourceNodeId("comp-A")
                    .targetNodeId("comp-B")
                    .relationshipType(YAPPCGraphEdge.YAPPCRelationshipType.CALLS) // GH-90000
                    .properties(Map.of()) // GH-90000
                    .metadata(new YAPPCGraphMetadata("tenant-1", "proj-1", "ws-1", "tester", Instant.now(), Instant.now(), "1.0", Map.of())) // GH-90000
                    .build(); // GH-90000

            when(graphPlugin.queryEdges(any())).thenReturn(Promise.of(List.of(dcCall))); // GH-90000
            when(mapper.fromDataCloudEdge(dcCall)).thenReturn(mapped); // GH-90000

            Map<String, List<YAPPCGraphEdge>> grouped = runPromise(() -> service.getWorkspaceDependencies("ws-1", "tenant-1")); // GH-90000

            assertThat(grouped).containsKey("CALLS");
            assertThat(grouped.get("CALLS")).containsExactly(mapped);
        }
    }

    @Nested
    @DisplayName("impact recommendations")
    class ImpactRecommendationTests {

        @Test
        @DisplayName("adds high impact recommendation when more than ten nodes are affected")
        void shouldAddHighImpactRecommendationForLargeImpactSets() { // GH-90000
            List<YAPPCGraphNode> affectedNodes = java.util.stream.IntStream.range(0, 11) // GH-90000
                    .mapToObj(index -> yappcNode("node-" + index, YAPPCGraphNode.YAPPCNodeType.CLASS)) // GH-90000
                    .toList(); // GH-90000

            service = new YAPPCGraphService( // GH-90000
                    graphPlugin,
                    mapper,
                    validator,
                    nodeRepository,
                    edgeRepository,
                    embeddingService,
                    semanticSearchService,
                    queryService);
            when(queryService.traverse("large-impact", 3, "tenant-1")).thenReturn(Promise.of(affectedNodes)); // GH-90000

            YAPPCImpactAnalysis analysis = runPromise(() -> service.analyzeChangeImpact("large-impact", "tenant-1")); // GH-90000

            assertThat(analysis.recommendations()).contains("High impact change - consider breaking into smaller changes");
        }
    }
}
