package com.ghatana.yappc.knowledge.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.knowledge.model.YAPPCGraphEdge;
import com.ghatana.yappc.knowledge.model.YAPPCGraphMetadata;
import com.ghatana.yappc.knowledge.model.YAPPCGraphNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Verifies JDBC CRUD behavior for knowledge graph repositories
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("KGRepository Tests")
class KGRepositoryTest extends EventloopTestBase {

    private DataSource dataSource;
    private Connection connection;
    private PreparedStatement preparedStatement;
    private ResultSet resultSet;
    private KGNodeRepository nodeRepository;
    private KGEdgeRepository edgeRepository;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = mock(DataSource.class);
        connection = mock(Connection.class);
        preparedStatement = mock(PreparedStatement.class);
        resultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeUpdate()).thenReturn(1);

        nodeRepository = new KGNodeRepository(dataSource, new ObjectMapper(), Runnable::run);
        edgeRepository = new KGEdgeRepository(dataSource, new ObjectMapper(), Runnable::run);
    }

    @Nested
    @DisplayName("KGNodeRepository")
    class NodeRepositoryTests {

        @Test
        @DisplayName("saveNode persists all node fields")
        void saveNodePersistsAllFields() {
            YAPPCGraphNode node = node();

            YAPPCGraphNode persisted = runPromise(() -> nodeRepository.saveNode(node));

            assertThat(persisted).isEqualTo(node);
            try {
                verify(preparedStatement).setString(1, node.id());
                verify(preparedStatement).setString(2, node.type().name());
                verify(preparedStatement).setString(3, node.name());
                verify(preparedStatement).setString(4, node.description());
                verify(preparedStatement).setString(8, node.metadata().tenantId());
                verify(preparedStatement).executeUpdate();
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        }

        @Test
        @DisplayName("findNodesByType maps rows back to YAPPC nodes")
        void findNodesByTypeMapsRows() throws Exception {
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true, false);
            when(resultSet.getString("node_id")).thenReturn("node-1");
            when(resultSet.getString("node_type")).thenReturn("SERVICE");
            when(resultSet.getString("label")).thenReturn("BillingService");
            when(resultSet.getString("description")).thenReturn("Handles billing");
            when(resultSet.getString("properties_json")).thenReturn("{\"language\":\"java\"}");
            when(resultSet.getString("tags_json")).thenReturn("[\"backend\",\"critical\"]");
            when(resultSet.getString("tenant_id")).thenReturn("tenant-1");
            when(resultSet.getString("project_id")).thenReturn("proj-1");
            when(resultSet.getString("workspace_id")).thenReturn("ws-1");
            when(resultSet.getString("created_by")).thenReturn("tester");
            when(resultSet.getTimestamp("created_at")).thenReturn(Timestamp.from(Instant.parse("2026-04-06T00:00:00Z")));
            when(resultSet.getTimestamp("updated_at")).thenReturn(Timestamp.from(Instant.parse("2026-04-06T01:00:00Z")));
            when(resultSet.getString("version")).thenReturn("1.0");
            when(resultSet.getString("labels_json")).thenReturn("{\"domain\":\"payments\"}");

            List<YAPPCGraphNode> nodes = runPromise(() -> nodeRepository.findNodesByType("SERVICE", "tenant-1", 100));

            assertThat(nodes).hasSize(1);
            assertThat(nodes.get(0).id()).isEqualTo("node-1");
            assertThat(nodes.get(0).properties()).containsEntry("language", "java");
            assertThat(nodes.get(0).tags()).containsExactlyInAnyOrder("backend", "critical");
        }
    }

    @Nested
    @DisplayName("KGEdgeRepository")
    class EdgeRepositoryTests {

        @Test
        @DisplayName("saveEdge persists all edge fields")
        void saveEdgePersistsAllFields() {
            YAPPCGraphEdge edge = edge();

            YAPPCGraphEdge persisted = runPromise(() -> edgeRepository.saveEdge(edge));

            assertThat(persisted).isEqualTo(edge);
            try {
                verify(preparedStatement).setString(1, edge.id());
                verify(preparedStatement).setString(2, edge.sourceNodeId());
                verify(preparedStatement).setString(3, edge.targetNodeId());
                verify(preparedStatement).setString(4, edge.relationshipType().name());
                verify(preparedStatement).executeUpdate();
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        }

        @Test
        @DisplayName("findEdgesFromSource maps rows back to YAPPC edges")
        void findEdgesFromSourceMapsRows() throws Exception {
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true, false);
            when(resultSet.getString("edge_id")).thenReturn("edge-1");
            when(resultSet.getString("from_node_id")).thenReturn("node-a");
            when(resultSet.getString("to_node_id")).thenReturn("node-b");
            when(resultSet.getString("relationship_type")).thenReturn("DEPENDS_ON");
            when(resultSet.getString("properties_json")).thenReturn("{\"weight\":1}");
            when(resultSet.getString("tenant_id")).thenReturn("tenant-1");
            when(resultSet.getString("project_id")).thenReturn("proj-1");
            when(resultSet.getString("workspace_id")).thenReturn("ws-1");
            when(resultSet.getString("created_by")).thenReturn("tester");
            when(resultSet.getTimestamp("created_at")).thenReturn(Timestamp.from(Instant.parse("2026-04-06T00:00:00Z")));
            when(resultSet.getTimestamp("updated_at")).thenReturn(Timestamp.from(Instant.parse("2026-04-06T01:00:00Z")));
            when(resultSet.getString("version")).thenReturn("1.0");
            when(resultSet.getString("labels_json")).thenReturn("{\"source\":\"analysis\"}");

            List<YAPPCGraphEdge> edges = runPromise(() -> edgeRepository.findEdgesFromSource(
                    "node-a",
                    "tenant-1",
                    Set.of("DEPENDS_ON")));

            assertThat(edges).hasSize(1);
            assertThat(edges.get(0).id()).isEqualTo("edge-1");
            assertThat(edges.get(0).sourceNodeId()).isEqualTo("node-a");
            assertThat(edges.get(0).targetNodeId()).isEqualTo("node-b");
        }
    }

    private static YAPPCGraphNode node() {
        return YAPPCGraphNode.builder()
                .id("node-1")
                .type(YAPPCGraphNode.YAPPCNodeType.SERVICE)
                .name("BillingService")
                .description("Handles billing")
                .properties(Map.of("language", "java"))
                .tags(Set.of("backend", "critical"))
                .metadata(metadata())
                .build();
    }

    private static YAPPCGraphEdge edge() {
        return YAPPCGraphEdge.builder()
                .id("edge-1")
                .sourceNodeId("node-a")
                .targetNodeId("node-b")
                .relationshipType(YAPPCGraphEdge.YAPPCRelationshipType.DEPENDS_ON)
                .properties(Map.of("weight", 1))
                .metadata(metadata())
                .build();
    }

    private static YAPPCGraphMetadata metadata() {
        return new YAPPCGraphMetadata(
                "tenant-1",
                "proj-1",
                "ws-1",
                "tester",
                Instant.parse("2026-04-06T00:00:00Z"),
                Instant.parse("2026-04-06T01:00:00Z"),
                "1.0",
                Map.of("domain", "payments"));
    }
}