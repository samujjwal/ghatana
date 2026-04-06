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
    void setUp() {
        graphPlugin = mock(KnowledgeGraphPlugin.class);
        mapper = mock(YAPPCGraphMapper.class);
        validator = mock(YAPPCGraphValidator.class);
        nodeRepository = mock(KGNodeRepository.class);
        edgeRepository = mock(KGEdgeRepository.class);
        embeddingService = mock(KGEmbeddingService.class);
        semanticSearchService = mock(KGSemanticSearchService.class);
        queryService = mock(KGQueryService.class);
        service = new YAPPCGraphService(graphPlugin, mapper, validator);
    }

    private YAPPCGraphNode yappcNode(String id, YAPPCGraphNode.YAPPCNodeType type) {
        return YAPPCGraphNode.builder()
                .id(id)
                .type(type)
                .name("MyComponent")
                .description("A test component")
                .properties(Map.of())
                .tags(Set.of("java"))
                .metadata(new YAPPCGraphMetadata(
                        "tenant-1", "proj-1", "ws-1",
                        "tester", Instant.now(), Instant.now(),
                        "1.0", Map.of()))
                .build();
    }

    private GraphNode dcNode(String id) {
        return GraphNode.builder()
                .id(id).type("CLASS")
                .properties(Map.of())
                .labels(Set.of())
                .tenantId("tenant-1")
                .build();
    }

    private GraphEdge dcEdge(String src, String tgt, String rel) {
        return GraphEdge.builder()
                .id(src + "_" + tgt + "_" + rel)
                .sourceNodeId(src).targetNodeId(tgt)
                .relationshipType(rel)
                .properties(Map.of())
                .tenantId("tenant-1")
                .build();
    }

    private YAPPCGraphEdge yappcEdge(String src, String tgt) {
        return YAPPCGraphEdge.builder()
                .id(src + "_" + tgt + "_DEPENDS_ON")
                .sourceNodeId(src).targetNodeId(tgt)
                .relationshipType(YAPPCGraphEdge.YAPPCRelationshipType.DEPENDS_ON)
                .properties(Map.of())
                .metadata(new YAPPCGraphMetadata(
                        "tenant-1", "proj-1", "ws-1",
                        "tester", Instant.now(), Instant.now(),
                        "1.0", Map.of()))
                .build();
    }

    @Nested
    @DisplayName("createYAPPCNode()")
    class CreateYAPPCNodeTests {

        @Test
        @DisplayName("validates, maps, persists and returns YAPPC node")
        void shouldCreateNode() {
            YAPPCGraphNode input = yappcNode("node-1", YAPPCGraphNode.YAPPCNodeType.SERVICE);
            GraphNode dc = dcNode("node-1");
            YAPPCGraphNode expected = yappcNode("node-1", YAPPCGraphNode.YAPPCNodeType.SERVICE);

            when(mapper.toDataCloudNode(input)).thenReturn(dc);
            when(graphPlugin.createNode(dc)).thenReturn(Promise.of(dc));
            when(mapper.fromDataCloudNode(dc)).thenReturn(expected);

            YAPPCGraphNode result = runPromise(() -> service.createYAPPCNode(input));

            assertThat(result).isEqualTo(expected);
            verify(validator).validateNode(input);
            verify(mapper).toDataCloudNode(input);
            verify(graphPlugin).createNode(dc);
            verify(mapper).fromDataCloudNode(dc);
        }

        @Test
        @DisplayName("validation failure propagates as IllegalArgumentException")
        void shouldPropagateValidationFailure() {
            YAPPCGraphNode bad = yappcNode("", YAPPCGraphNode.YAPPCNodeType.CLASS);
            doThrow(new IllegalArgumentException("Node id cannot be null or blank"))
                    .when(validator).validateNode(bad);

            assertThatThrownBy(() -> runPromise(() -> service.createYAPPCNode(bad)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Node id");

            verify(graphPlugin, never()).createNode(any());
        }

        @Test
        @DisplayName("persists to JDBC repository before publishing to plugin when repository is configured")
        void shouldPersistToRepositoryWhenConfigured() {
            YAPPCGraphNode input = yappcNode("node-2", YAPPCGraphNode.YAPPCNodeType.SERVICE);
            GraphNode dc = dcNode("node-2");

            service = new YAPPCGraphService(
                    graphPlugin,
                    mapper,
                    validator,
                    nodeRepository,
                    edgeRepository,
                    embeddingService,
                    semanticSearchService);
            when(nodeRepository.saveNode(input)).thenReturn(Promise.of(input));
            when(mapper.toDataCloudNode(input)).thenReturn(dc);
            when(graphPlugin.createNode(dc)).thenReturn(Promise.of(dc));
            when(mapper.fromDataCloudNode(dc)).thenReturn(input);
            when(embeddingService.indexNode(input)).thenReturn(Promise.of((Void) null));

            YAPPCGraphNode result = runPromise(() -> service.createYAPPCNode(input));

            assertThat(result).isEqualTo(input);
            verify(nodeRepository).saveNode(input);
            verify(graphPlugin).createNode(dc);
            verify(embeddingService).indexNode(input);
        }

        @Test
        @DisplayName("returns persisted node without publishing when plugin is absent")
        void shouldReturnPersistedNodeWhenPluginIsAbsent() {
            YAPPCGraphNode input = yappcNode("node-3", YAPPCGraphNode.YAPPCNodeType.SERVICE);

            service = new YAPPCGraphService(null, mapper, validator, nodeRepository, edgeRepository);
            when(nodeRepository.saveNode(input)).thenReturn(Promise.of(input));

            YAPPCGraphNode result = runPromise(() -> service.createYAPPCNode(input));

            assertThat(result).isEqualTo(input);
            verify(mapper, never()).toDataCloudNode(any());
        }
    }

    @Nested
    @DisplayName("findCodeDependencies()")
    class FindCodeDependenciesTests {

        @Test
        @DisplayName("queries plugin for DEPENDS_ON, IMPORTS, EXTENDS, IMPLEMENTS edges and maps result")
        void shouldFindDependencies() {
            GraphEdge dcDep1 = dcEdge("comp-A", "comp-B", "DEPENDS_ON");
            GraphEdge dcDep2 = dcEdge("comp-A", "comp-C", "IMPORTS");
            YAPPCGraphEdge e1 = yappcEdge("comp-A", "comp-B");
            YAPPCGraphEdge e2 = yappcEdge("comp-A", "comp-C");

            when(graphPlugin.queryEdges(any())).thenReturn(Promise.of(List.of(dcDep1, dcDep2)));
            when(mapper.fromDataCloudEdge(dcDep1)).thenReturn(e1);
            when(mapper.fromDataCloudEdge(dcDep2)).thenReturn(e2);

            List<YAPPCGraphEdge> result = runPromise(
                    () -> service.findCodeDependencies("comp-A", "tenant-1"));

            assertThat(result).hasSize(2).containsExactlyInAnyOrder(e1, e2);
        }

        @Test
        @DisplayName("returns empty list when plugin finds no edges")
        void shouldReturnEmptyWhenNoDependencies() {
            when(graphPlugin.queryEdges(any())).thenReturn(Promise.of(List.of()));

            List<YAPPCGraphEdge> result = runPromise(
                    () -> service.findCodeDependencies("comp-X", "tenant-1"));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("uses JDBC edge repository when configured")
        void shouldUseEdgeRepositoryWhenConfigured() {
            YAPPCGraphEdge e1 = yappcEdge("comp-A", "comp-B");
            service = new YAPPCGraphService(graphPlugin, mapper, validator, nodeRepository, edgeRepository);
            when(edgeRepository.findEdgesFromSource(
                    eq("comp-A"),
                    eq("tenant-1"),
                    eq(Set.of("DEPENDS_ON", "IMPORTS", "EXTENDS", "IMPLEMENTS"))))
                    .thenReturn(Promise.of(List.of(e1)));

            List<YAPPCGraphEdge> result = runPromise(() -> service.findCodeDependencies("comp-A", "tenant-1"));

            assertThat(result).containsExactly(e1);
            verify(graphPlugin, never()).queryEdges(any());
        }
    }

    @Nested
    @DisplayName("analyzeChangeImpact()")
    class AnalyzeChangeImpactTests {

        @Test
        @DisplayName("returns impact analysis with affected node count and score")
        void shouldBuildImpactAnalysis() {
            GraphNode n1 = dcNode("dep-1");
            GraphNode n2 = dcNode("dep-2");
            YAPPCGraphNode yn1 = yappcNode("dep-1", YAPPCGraphNode.YAPPCNodeType.SERVICE);
            YAPPCGraphNode yn2 = yappcNode("dep-2", YAPPCGraphNode.YAPPCNodeType.CLASS);

            when(graphPlugin.getNeighbors(eq("comp-A"), eq(5), eq("tenant-1")))
                    .thenReturn(Promise.of(List.of(n1, n2)));
            when(mapper.fromDataCloudNode(n1)).thenReturn(yn1);
            when(mapper.fromDataCloudNode(n2)).thenReturn(yn2);

            YAPPCImpactAnalysis analysis = runPromise(
                    () -> service.analyzeChangeImpact("comp-A", "tenant-1"));

            assertThat(analysis.componentId()).isEqualTo("comp-A");
            assertThat(analysis.affectedNodes()).hasSize(2);
            assertThat(analysis.impactScore()).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("returns zero impact score when no neighbors found")
        void shouldReturnZeroImpactWhenNoNeighbors() {
            when(graphPlugin.getNeighbors(any(), anyInt(), any()))
                    .thenReturn(Promise.of(List.of()));

            YAPPCImpactAnalysis analysis = runPromise(
                    () -> service.analyzeChangeImpact("isolated", "tenant-1"));

            assertThat(analysis.affectedNodes()).isEmpty();
            assertThat(analysis.impactScore()).isEqualTo(0.0);
        }

            @Test
            @DisplayName("uses KGQueryService traversal when configured")
            void shouldUseQueryServiceWhenConfigured() {
                YAPPCGraphNode serviceNode = yappcNode("dep-svc", YAPPCGraphNode.YAPPCNodeType.SERVICE);
                YAPPCGraphNode apiNode = yappcNode("dep-api", YAPPCGraphNode.YAPPCNodeType.API);
                YAPPCGraphNode testNode = yappcNode("dep-test", YAPPCGraphNode.YAPPCNodeType.TEST);

                service = new YAPPCGraphService(
                    graphPlugin,
                    mapper,
                    validator,
                    nodeRepository,
                    edgeRepository,
                    embeddingService,
                    semanticSearchService,
                    queryService);
                when(queryService.traverse("comp-A", 3, "tenant-1"))
                    .thenReturn(Promise.of(List.of(serviceNode, apiNode, testNode)));

                YAPPCImpactAnalysis analysis = runPromise(() -> service.analyzeChangeImpact("comp-A", "tenant-1"));

                assertThat(analysis.affectedNodes()).containsExactly(serviceNode, apiNode, testNode);
                    assertThat(analysis.impactScore()).isCloseTo(0.7, org.assertj.core.data.Offset.offset(0.0000001));
                assertThat(analysis.recommendations())
                    .contains("Update service contracts and API documentation")
                    .contains("Review and update affected tests");
                verify(graphPlugin, never()).getNeighbors(any(), anyInt(), any());
            }
    }

    @Nested
    @DisplayName("findComponentsByType()")
    class FindComponentsByTypeTests {

        @Test
        @DisplayName("queries plugin for nodes matching type and maps results")
        void shouldFindComponentsByType() {
            GraphNode n1 = dcNode("svc-1");
            YAPPCGraphNode yn1 = yappcNode("svc-1", YAPPCGraphNode.YAPPCNodeType.SERVICE);

            when(graphPlugin.queryNodes(any())).thenReturn(Promise.of(List.of(n1)));
            when(mapper.fromDataCloudNode(n1)).thenReturn(yn1);

            List<YAPPCGraphNode> result = runPromise(
                    () -> service.findComponentsByType("SERVICE", "tenant-1"));

            assertThat(result).hasSize(1);
            assertThat(result.get(0).type()).isEqualTo(YAPPCGraphNode.YAPPCNodeType.SERVICE);
        }

        @Test
        @DisplayName("uses JDBC node repository when configured")
        void shouldUseNodeRepositoryWhenConfigured() {
            YAPPCGraphNode yn1 = yappcNode("svc-2", YAPPCGraphNode.YAPPCNodeType.SERVICE);
            service = new YAPPCGraphService(graphPlugin, mapper, validator, nodeRepository, edgeRepository);
            when(nodeRepository.findNodesByType("SERVICE", "tenant-1", 1000))
                    .thenReturn(Promise.of(List.of(yn1)));

            List<YAPPCGraphNode> result = runPromise(() -> service.findComponentsByType("SERVICE", "tenant-1"));

            assertThat(result).containsExactly(yn1);
            verify(graphPlugin, never()).queryNodes(any());
        }
    }

        @Nested
        @DisplayName("semanticSearch")
        class SemanticSearchTests {

        @Test
        @DisplayName("returns semantic node matches when semantic search service is configured")
        void shouldReturnSemanticMatches() {
            YAPPCGraphNode node = yappcNode("svc-3", YAPPCGraphNode.YAPPCNodeType.SERVICE);
            KGSemanticSearchService.SemanticNodeMatch match =
                new KGSemanticSearchService.SemanticNodeMatch(node, 0.91, Map.of("tenantId", "tenant-1"));

            service = new YAPPCGraphService(
                graphPlugin,
                mapper,
                validator,
                nodeRepository,
                edgeRepository,
                embeddingService,
                semanticSearchService);
            when(semanticSearchService.findSimilarNodes("billing service", "tenant-1", 5, 0.75))
                .thenReturn(Promise.of(List.of(match)));

            List<KGSemanticSearchService.SemanticNodeMatch> result = runPromise(
                () -> service.semanticSearch("billing service", "tenant-1", 5, 0.75));

            assertThat(result).containsExactly(match);
        }

        @Test
        @DisplayName("returns empty list when semantic search service is absent")
        void shouldReturnEmptyWhenSemanticSearchServiceIsAbsent() {
            assertThat(runPromise(() -> service.semanticSearch("billing service", "tenant-1", 5, 0.75))).isEmpty();
        }
        }

    @Nested
    @DisplayName("createCodeRelationship()")
    class CreateCodeRelationshipTests {

        @Test
        @DisplayName("creates edge in plugin and returns mapped YAPPC edge")
        void shouldCreateCodeRelationship() {
            GraphEdge createdEdge = dcEdge("comp-A", "comp-B", "DEPENDS_ON");
            YAPPCGraphEdge expected = yappcEdge("comp-A", "comp-B");

            when(graphPlugin.createEdge(any(GraphEdge.class))).thenReturn(Promise.of(createdEdge));
            when(mapper.fromDataCloudEdge(createdEdge)).thenReturn(expected);

            YAPPCGraphEdge result = runPromise(
                    () -> service.createCodeRelationship("comp-A", "comp-B", "DEPENDS_ON", "tenant-1"));

            assertThat(result).isEqualTo(expected);
            verify(graphPlugin).createEdge(argThat(e ->
                    "comp-A".equals(e.getSourceNodeId()) &&
                    "comp-B".equals(e.getTargetNodeId()) &&
                    "DEPENDS_ON".equals(e.getRelationshipType())));
        }

        @Test
        @DisplayName("persists relationship to JDBC repository when configured")
        void shouldPersistRelationshipWhenConfigured() {
            GraphEdge createdEdge = dcEdge("comp-A", "comp-B", "DEPENDS_ON");
            YAPPCGraphEdge expected = yappcEdge("comp-A", "comp-B");

            service = new YAPPCGraphService(graphPlugin, mapper, validator, nodeRepository, edgeRepository);
            when(edgeRepository.saveEdge(any(YAPPCGraphEdge.class))).thenReturn(Promise.of(expected));
            when(graphPlugin.createEdge(any(GraphEdge.class))).thenReturn(Promise.of(createdEdge));
            when(mapper.fromDataCloudEdge(createdEdge)).thenReturn(expected);

            YAPPCGraphEdge result = runPromise(
                    () -> service.createCodeRelationship("comp-A", "comp-B", "DEPENDS_ON", "tenant-1"));

            assertThat(result).isEqualTo(expected);
            verify(edgeRepository).saveEdge(any(YAPPCGraphEdge.class));
        }

        @Test
        @DisplayName("returns persisted relationship when plugin is absent")
        void shouldReturnPersistedRelationshipWhenPluginIsAbsent() {
            YAPPCGraphEdge expected = yappcEdge("comp-A", "comp-B");

            service = new YAPPCGraphService(null, mapper, validator, nodeRepository, edgeRepository);
            when(edgeRepository.saveEdge(any(YAPPCGraphEdge.class))).thenReturn(Promise.of(expected));

            YAPPCGraphEdge result = runPromise(
                    () -> service.createCodeRelationship("comp-A", "comp-B", "DEPENDS_ON", "tenant-1"));

            assertThat(result).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("findDependencyPath()")
    class FindDependencyPathTests {

        @Test
        @DisplayName("delegates to plugin findShortestPath and maps result nodes")
        void shouldFindDependencyPath() {
            GraphNode mid = dcNode("mid-1");
            YAPPCGraphNode yMid = yappcNode("mid-1", YAPPCGraphNode.YAPPCNodeType.CLASS);

            when(graphPlugin.findShortestPath("A", "B", "tenant-1"))
                    .thenReturn(Promise.of(List.of(mid)));
            when(mapper.fromDataCloudNode(mid)).thenReturn(yMid);

            List<YAPPCGraphNode> path = runPromise(
                    () -> service.findDependencyPath("A", "B", "tenant-1"));

            assertThat(path).containsExactly(yMid);
        }

        @Test
        @DisplayName("returns empty list when no path exists")
        void shouldReturnEmptyPathWhenNotConnected() {
            when(graphPlugin.findShortestPath(any(), any(), any()))
                    .thenReturn(Promise.of(List.of()));

            List<YAPPCGraphNode> path = runPromise(
                    () -> service.findDependencyPath("X", "Y", "tenant-1"));

            assertThat(path).isEmpty();
        }

        @Test
        @DisplayName("uses KGQueryService path search when configured")
        void shouldUseQueryServiceForDependencyPath() {
            YAPPCGraphNode source = yappcNode("A", YAPPCGraphNode.YAPPCNodeType.CLASS);
            YAPPCGraphNode target = yappcNode("B", YAPPCGraphNode.YAPPCNodeType.SERVICE);

            service = new YAPPCGraphService(
                    graphPlugin,
                    mapper,
                    validator,
                    nodeRepository,
                    edgeRepository,
                    embeddingService,
                    semanticSearchService,
                    queryService);
            when(queryService.findPaths("A", "B", "tenant-1"))
                    .thenReturn(Promise.of(List.of(List.of(source, target))));

            List<YAPPCGraphNode> path = runPromise(() -> service.findDependencyPath("A", "B", "tenant-1"));

            assertThat(path).containsExactly(source, target);
            verify(graphPlugin, never()).findShortestPath(any(), any(), any());
        }

        @Test
        @DisplayName("returns empty path when query service has no paths")
        void shouldReturnEmptyPathWhenQueryServiceHasNoPaths() {
            service = new YAPPCGraphService(
                    graphPlugin,
                    mapper,
                    validator,
                    nodeRepository,
                    edgeRepository,
                    embeddingService,
                    semanticSearchService,
                    queryService);
            when(queryService.findPaths("A", "B", "tenant-1")).thenReturn(Promise.of(List.of()));

            List<YAPPCGraphNode> path = runPromise(() -> service.findDependencyPath("A", "B", "tenant-1"));

            assertThat(path).isEmpty();
        }
    }

    @Nested
    @DisplayName("getWorkspaceDependencies()")
    class GetWorkspaceDependenciesTests {

        @Test
        @DisplayName("uses JDBC repository when configured")
        void shouldUseRepositoryWhenConfigured() {
            YAPPCGraphEdge calls = YAPPCGraphEdge.builder()
                    .id("edge-calls")
                    .sourceNodeId("comp-A")
                    .targetNodeId("comp-B")
                    .relationshipType(YAPPCGraphEdge.YAPPCRelationshipType.CALLS)
                    .properties(Map.of())
                    .metadata(new YAPPCGraphMetadata("tenant-1", "proj-1", "ws-1", "tester", Instant.now(), Instant.now(), "1.0", Map.of()))
                    .build();

            service = new YAPPCGraphService(graphPlugin, mapper, validator, nodeRepository, edgeRepository);
            when(edgeRepository.findEdgesForWorkspace("ws-1", "tenant-1", Set.of("DEPENDS_ON", "USES", "CALLS")))
                    .thenReturn(Promise.of(List.of(calls)));

            Map<String, List<YAPPCGraphEdge>> grouped = runPromise(() -> service.getWorkspaceDependencies("ws-1", "tenant-1"));

            assertThat(grouped).containsKey("CALLS");
            assertThat(grouped.get("CALLS")).containsExactly(calls);
            verify(graphPlugin, never()).queryEdges(any());
        }

        @Test
        @DisplayName("uses plugin query when repository is absent")
        void shouldUsePluginWhenRepositoryIsAbsent() {
            GraphEdge dcCall = dcEdge("comp-A", "comp-B", "CALLS");
            YAPPCGraphEdge mapped = YAPPCGraphEdge.builder()
                    .id("edge-calls")
                    .sourceNodeId("comp-A")
                    .targetNodeId("comp-B")
                    .relationshipType(YAPPCGraphEdge.YAPPCRelationshipType.CALLS)
                    .properties(Map.of())
                    .metadata(new YAPPCGraphMetadata("tenant-1", "proj-1", "ws-1", "tester", Instant.now(), Instant.now(), "1.0", Map.of()))
                    .build();

            when(graphPlugin.queryEdges(any())).thenReturn(Promise.of(List.of(dcCall)));
            when(mapper.fromDataCloudEdge(dcCall)).thenReturn(mapped);

            Map<String, List<YAPPCGraphEdge>> grouped = runPromise(() -> service.getWorkspaceDependencies("ws-1", "tenant-1"));

            assertThat(grouped).containsKey("CALLS");
            assertThat(grouped.get("CALLS")).containsExactly(mapped);
        }
    }

    @Nested
    @DisplayName("impact recommendations")
    class ImpactRecommendationTests {

        @Test
        @DisplayName("adds high impact recommendation when more than ten nodes are affected")
        void shouldAddHighImpactRecommendationForLargeImpactSets() {
            List<YAPPCGraphNode> affectedNodes = java.util.stream.IntStream.range(0, 11)
                    .mapToObj(index -> yappcNode("node-" + index, YAPPCGraphNode.YAPPCNodeType.CLASS))
                    .toList();

            service = new YAPPCGraphService(
                    graphPlugin,
                    mapper,
                    validator,
                    nodeRepository,
                    edgeRepository,
                    embeddingService,
                    semanticSearchService,
                    queryService);
            when(queryService.traverse("large-impact", 3, "tenant-1")).thenReturn(Promise.of(affectedNodes));

            YAPPCImpactAnalysis analysis = runPromise(() -> service.analyzeChangeImpact("large-impact", "tenant-1"));

            assertThat(analysis.recommendations()).contains("High impact change - consider breaking into smaller changes");
        }
    }
}
