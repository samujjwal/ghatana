package com.ghatana.yappc.knowledge;

import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.knowledge.model.YAPPCGraphEdge;
import com.ghatana.yappc.knowledge.model.YAPPCGraphMetadata;
import com.ghatana.yappc.knowledge.model.YAPPCGraphNode;
import com.ghatana.yappc.knowledge.spi.DataStorePort;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Verify YAPPCGraphService behavior against DataStorePort seam
 * @doc.layer service
 * @doc.pattern Test
 */
@DisplayName("YAPPCGraphService")
class YAPPCGraphServiceTest extends EventloopTestBase {

    private DataStorePort dataStorePort;
    private YAPPCGraphMapper mapper;
    private YAPPCGraphValidator validator;
    private YAPPCGraphService service;

    @BeforeEach
    void setUp() {
        dataStorePort = mock(DataStorePort.class);
        mapper = mock(YAPPCGraphMapper.class);
        validator = mock(YAPPCGraphValidator.class);
        service = new YAPPCGraphService(dataStorePort, mapper, validator);
    }

    @Test
    @DisplayName("createYAPPCNode validates, writes via port, and maps response")
    void shouldCreateNodeThroughPort() {
        YAPPCGraphNode input = yappcNode("node-1", YAPPCGraphNode.YAPPCNodeType.SERVICE);
        DataStorePort.GraphNode portNode = portNode("node-1");

        when(mapper.toPortNode(input)).thenReturn(portNode);
        when(dataStorePort.createNode(eq(TenantId.of("tenant-1")), eq(portNode))).thenReturn(Promise.of(portNode));
        when(mapper.fromPortNode(portNode)).thenReturn(input);

        YAPPCGraphNode result = runPromise(() -> service.createYAPPCNode(input));

        assertThat(result).isEqualTo(input);
        verify(validator).validateNode(input);
        verify(mapper).toPortNode(input);
        verify(dataStorePort).createNode(eq(TenantId.of("tenant-1")), eq(portNode));
        verify(mapper).fromPortNode(portNode);
    }

    @Test
    @DisplayName("findCodeDependencies queries port and maps edges")
    void shouldFindCodeDependenciesFromPort() {
        DataStorePort.GraphEdge portEdge = portEdge("comp-A", "comp-B", "DEPENDS_ON");
        YAPPCGraphEdge mappedEdge = yappcEdge("comp-A", "comp-B");

        when(dataStorePort.queryEdges(eq(TenantId.of("tenant-1")), any(DataStorePort.GraphQuery.class)))
                .thenReturn(Promise.of(List.of(portEdge)));
        when(mapper.fromPortEdge(portEdge)).thenReturn(mappedEdge);

        List<YAPPCGraphEdge> result = runPromise(() -> service.findCodeDependencies("comp-A", "tenant-1"));

        assertThat(result).containsExactly(mappedEdge);
        verify(dataStorePort).queryEdges(eq(TenantId.of("tenant-1")), any(DataStorePort.GraphQuery.class));
        verify(mapper).fromPortEdge(portEdge);
    }

    @Test
    @DisplayName("findComponentsByType queries port and maps nodes")
    void shouldFindComponentsByTypeFromPort() {
        DataStorePort.GraphNode portNode = portNode("svc-1");
        YAPPCGraphNode mappedNode = yappcNode("svc-1", YAPPCGraphNode.YAPPCNodeType.SERVICE);

        when(dataStorePort.queryNodes(eq(TenantId.of("tenant-1")), any(DataStorePort.GraphQuery.class)))
                .thenReturn(Promise.of(List.of(portNode)));
        when(mapper.fromPortNode(portNode)).thenReturn(mappedNode);

        List<YAPPCGraphNode> result = runPromise(() -> service.findComponentsByType("SERVICE", "tenant-1"));

        assertThat(result).containsExactly(mappedNode);
        verify(dataStorePort).queryNodes(eq(TenantId.of("tenant-1")), any(DataStorePort.GraphQuery.class));
        verify(mapper).fromPortNode(portNode);
    }

    private YAPPCGraphNode yappcNode(String id, YAPPCGraphNode.YAPPCNodeType type) {
        Instant now = Instant.now();
        return YAPPCGraphNode.builder()
                .id(id)
                .type(type)
                .name(id)
                .description("test")
                .properties(Map.of())
                .tags(Set.of())
                .metadata(new YAPPCGraphMetadata("tenant-1", "proj-1", "ws-1", "tester", now, now, "1", Map.of()))
                .build();
    }

    private YAPPCGraphEdge yappcEdge(String sourceId, String targetId) {
        Instant now = Instant.now();
        return YAPPCGraphEdge.builder()
                .id(sourceId + "_" + targetId + "_DEPENDS_ON")
                .sourceNodeId(sourceId)
                .targetNodeId(targetId)
                .relationshipType(YAPPCGraphEdge.YAPPCRelationshipType.DEPENDS_ON)
                .properties(Map.of())
                .metadata(new YAPPCGraphMetadata("tenant-1", "proj-1", "ws-1", "tester", now, now, "1", Map.of()))
                .build();
    }

    private DataStorePort.GraphNode portNode(String id) {
        Instant now = Instant.now();
        return new DataStorePort.GraphNode(
                id,
                "SERVICE",
                Map.of(),
                Set.of(),
                TenantId.of("tenant-1"),
                now,
                now,
                1L
        );
    }

    private DataStorePort.GraphEdge portEdge(String sourceId, String targetId, String relationshipType) {
        Instant now = Instant.now();
        return new DataStorePort.GraphEdge(
                sourceId + "_" + targetId + "_" + relationshipType,
                sourceId,
                targetId,
                relationshipType,
                Map.of(),
                TenantId.of("tenant-1"),
                now,
                now,
                1L
        );
    }
}
