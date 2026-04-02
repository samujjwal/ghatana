package com.ghatana.yappc.knowledge;

import com.ghatana.datacloud.plugins.knowledgegraph.KnowledgeGraphPlugin;
import com.ghatana.datacloud.plugins.knowledgegraph.model.GraphEdge;
import com.ghatana.datacloud.plugins.knowledgegraph.model.GraphNode;
import com.ghatana.platform.testing.activej.EventloopTestBase;
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
    private YAPPCGraphService service;

    @BeforeEach
    void setUp() {
        graphPlugin = mock(KnowledgeGraphPlugin.class);
        mapper = mock(YAPPCGraphMapper.class);
        validator = mock(YAPPCGraphValidator.class);
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
    }
}
