package com.ghatana.yappc.knowledge.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.knowledge.model.YAPPCGraphEdge;
import com.ghatana.yappc.knowledge.model.YAPPCGraphMetadata;
import com.ghatana.yappc.knowledge.model.YAPPCGraphNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        @DisplayName("default constructor uses shared defaults")
        void defaultConstructorUsesSharedDefaults() {
            assertThat(new KGNodeRepository(dataSource)).isNotNull();
        }

        @Test
        @DisplayName("saveNode persists all node fields")
        void saveNodePersistsAllFields() {
            YAPPCGraphNode node = node();

            YAPPCGraphNode persisted = runPromise(() -> nodeRepository.saveNode(node));
            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);

            assertThat(persisted).isEqualTo(node);
            try {
                verify(connection).prepareStatement(sqlCaptor.capture());
                verify(preparedStatement).setString(1, node.id());
                verify(preparedStatement).setString(2, node.type().name());
                verify(preparedStatement).setString(3, node.name());
                verify(preparedStatement).setString(4, node.description());
                verify(preparedStatement).setString(8, node.metadata().tenantId());
                verify(preparedStatement).executeUpdate();
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
            assertThat(sqlCaptor.getValue()).contains("ON CONFLICT (tenant_id, node_id) DO UPDATE SET");
            assertThat(sqlCaptor.getValue()).doesNotContain("tenant_id = EXCLUDED.tenant_id");
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

        @Test
        @DisplayName("findNodesByIds returns empty immediately when no ids supplied")
        void findNodesByIdsReturnsEmptyImmediatelyWhenNoIdsSupplied() {
            assertThat(runPromise(() -> nodeRepository.findNodesByIds(List.of(), "tenant-1"))).isEmpty();
        }

        @Test
        @DisplayName("findNodesByIds maps rows for all requested ids")
        void findNodesByIdsMapsRowsForAllRequestedIds() throws Exception {
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true, false);
            stubNodeRow("node-2", "CLASS", "{}", "[]", "{}");

            List<YAPPCGraphNode> nodes = runPromise(() -> nodeRepository.findNodesByIds(List.of("node-2", "node-3"), "tenant-1"));

            assertThat(nodes).singleElement().satisfies(node -> assertThat(node.id()).isEqualTo("node-2"));
            verify(preparedStatement).setString(2, "node-2");
            verify(preparedStatement).setString(3, "node-3");
        }

        @Test
        @DisplayName("findNodeById returns mapped optional node")
        void findNodeByIdReturnsMappedOptionalNode() throws Exception {
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true);
            stubNodeRow("node-7", "SERVICE", "{\"language\":\"java\"}", "[\"backend\"]", "{\"domain\":\"payments\"}");

            Optional<YAPPCGraphNode> node = runPromise(() -> nodeRepository.findNodeById("node-7", "tenant-1"));

            assertThat(node).isPresent();
            assertThat(node.orElseThrow().id()).isEqualTo("node-7");
            verify(preparedStatement).setString(1, "tenant-1");
            verify(preparedStatement).setString(2, "node-7");
        }

        @Test
        @DisplayName("findNodeById returns empty when row is missing")
        void findNodeByIdReturnsEmptyWhenRowIsMissing() throws Exception {
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(false);

            Optional<YAPPCGraphNode> node = runPromise(() -> nodeRepository.findNodeById("missing", "tenant-1"));

            assertThat(node).isEmpty();
        }

        @Test
        @DisplayName("findNodesByProject maps rows and falls back empty json payloads")
        void findNodesByProjectMapsRowsAndFallsBackEmptyJsonPayloads() throws Exception {
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true, false);
            stubNodeRow("node-project", "DOCUMENT", null, "", null);

            List<YAPPCGraphNode> nodes = runPromise(() -> nodeRepository.findNodesByProject("proj-1", "tenant-1"));

            assertThat(nodes).singleElement().satisfies(node -> {
                assertThat(node.properties()).isEmpty();
                assertThat(node.tags()).isEmpty();
                assertThat(node.metadata().labels()).isEmpty();
            });
            verify(preparedStatement).setString(1, "tenant-1");
            verify(preparedStatement).setString(2, "proj-1");
        }

        @Test
        @DisplayName("findNodesByProject returns empty when no rows match")
        void findNodesByProjectReturnsEmptyWhenNoRowsMatch() throws Exception {
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(false);

            assertThat(runPromise(() -> nodeRepository.findNodesByProject("proj-missing", "tenant-1"))).isEmpty();
        }

        @Test
        @DisplayName("findNodesByProject maps multiple rows")
        void findNodesByProjectMapsMultipleRows() throws Exception {
            ResultSet secondRow = mock(ResultSet.class);
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true, true, false);
            stubNodeRow("node-10", "SERVICE", "{}", "[]", "{}");
            when(resultSet.getString("node_id")).thenReturn("node-10", "node-11");
            when(resultSet.getString("node_type")).thenReturn("SERVICE", "API");

            List<YAPPCGraphNode> nodes = runPromise(() -> nodeRepository.findNodesByProject("proj-1", "tenant-1"));

            assertThat(nodes).extracting(YAPPCGraphNode::id).containsExactly("node-10", "node-11");
        }

        @Test
        @DisplayName("countNodesByTenant returns count when a row is present")
        void countNodesByTenantReturnsCountWhenRowIsPresent() throws Exception {
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true);
            when(resultSet.getInt("node_count")).thenReturn(9);

            assertThat(runPromise(() -> nodeRepository.countNodesByTenant("tenant-1"))).isEqualTo(9);
        }

        @Test
        @DisplayName("countNodesByTenant returns zero when query yields no rows")
        void countNodesByTenantReturnsZeroWhenQueryYieldsNoRows() throws Exception {
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(false);

            assertThat(runPromise(() -> nodeRepository.countNodesByTenant("tenant-1"))).isZero();
        }

        @Test
        @DisplayName("deleteNode is tenant scoped and reports whether a row was deleted")
        void deleteNodeIsTenantScopedAndReportsWhetherARowWasDeleted() throws Exception {
            when(preparedStatement.executeUpdate()).thenReturn(1, 0);

            assertThat(runPromise(() -> nodeRepository.deleteNode("node-1", "tenant-1"))).isTrue();
            assertThat(runPromise(() -> nodeRepository.deleteNode("node-1", "tenant-1"))).isFalse();
        }

        @Test
        @DisplayName("saveNode wraps serialization errors")
        void saveNodeWrapsSerializationErrors() throws Exception {
            ObjectMapper brokenMapper = mock(ObjectMapper.class);
            when(brokenMapper.writeValueAsString(org.mockito.ArgumentMatchers.any()))
                    .thenThrow(mock(JsonProcessingException.class));
            KGNodeRepository brokenRepository = new KGNodeRepository(dataSource, brokenMapper, Runnable::run);

            assertThatThrownBy(() -> runPromise(() -> brokenRepository.saveNode(node())))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("serialize knowledge graph node data");
        }

        @Test
        @DisplayName("findNodesByType wraps invalid json payloads")
        void findNodesByTypeWrapsInvalidJsonPayloads() throws Exception {
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true);
            stubNodeRow("node-bad", "SERVICE", "{bad-json", "[]", "{}");

            assertThatThrownBy(() -> runPromise(() -> nodeRepository.findNodesByType("SERVICE", "tenant-1", 10)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("deserialize knowledge graph node data");
        }
    }

    @Nested
    @DisplayName("KGEdgeRepository")
    class EdgeRepositoryTests {

        @Test
        @DisplayName("default constructor uses shared defaults")
        void defaultConstructorUsesSharedDefaults() {
            assertThat(new KGEdgeRepository(dataSource)).isNotNull();
        }

        @Test
        @DisplayName("saveEdge persists all edge fields")
        void saveEdgePersistsAllFields() {
            YAPPCGraphEdge edge = edge();

            YAPPCGraphEdge persisted = runPromise(() -> edgeRepository.saveEdge(edge));
            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);

            assertThat(persisted).isEqualTo(edge);
            try {
                verify(connection).prepareStatement(sqlCaptor.capture());
                verify(preparedStatement).setString(1, edge.id());
                verify(preparedStatement).setString(2, edge.sourceNodeId());
                verify(preparedStatement).setString(3, edge.targetNodeId());
                verify(preparedStatement).setString(4, edge.relationshipType().name());
                verify(preparedStatement).executeUpdate();
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
            assertThat(sqlCaptor.getValue()).contains("ON CONFLICT (tenant_id, edge_id) DO UPDATE SET");
            assertThat(sqlCaptor.getValue()).doesNotContain("tenant_id = EXCLUDED.tenant_id");
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

        @Test
        @DisplayName("findEdgesForWorkspace returns all relationships when filter is empty")
        void findEdgesForWorkspaceReturnsAllRelationshipsWhenFilterIsEmpty() throws Exception {
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true, false);
            stubEdgeRow("edge-workspace", "node-a", "node-b", "CALLS", "{\"weight\":1}", "{\"source\":\"analysis\"}");

            List<YAPPCGraphEdge> edges = runPromise(() -> edgeRepository.findEdgesForWorkspace("ws-1", "tenant-1", Set.of()));

            assertThat(edges).singleElement().satisfies(edge -> assertThat(edge.relationshipType()).isEqualTo(YAPPCGraphEdge.YAPPCRelationshipType.CALLS));
        }

        @Test
        @DisplayName("findEdgesFromSource returns all rows when filter is empty")
        void findEdgesFromSourceReturnsAllRowsWhenFilterIsEmpty() throws Exception {
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true, false);
            stubEdgeRow("edge-all", "node-a", "node-b", "IMPLEMENTS", "{\"weight\":1}", "{\"source\":\"analysis\"}");

            List<YAPPCGraphEdge> edges = runPromise(() -> edgeRepository.findEdgesFromSource("node-a", "tenant-1", Set.of()));

            assertThat(edges).singleElement().satisfies(edge -> assertThat(edge.relationshipType()).isEqualTo(YAPPCGraphEdge.YAPPCRelationshipType.IMPLEMENTS));
        }

        @Test
        @DisplayName("findEdgesFromSource filters out relationships not requested")
        void findEdgesFromSourceFiltersOutRelationshipsNotRequested() throws Exception {
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true, false);
            stubEdgeRow("edge-all", "node-a", "node-b", "IMPLEMENTS", "{\"weight\":1}", "{\"source\":\"analysis\"}");

            assertThat(runPromise(() -> edgeRepository.findEdgesFromSource("node-a", "tenant-1", Set.of("DEPENDS_ON")))).isEmpty();
        }

        @Test
        @DisplayName("findEdgesForWorkspace filters out relationships not requested")
        void findEdgesForWorkspaceFiltersOutRelationshipsNotRequested() throws Exception {
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true, false);
            stubEdgeRow("edge-filtered", "node-a", "node-b", "CALLS", "{\"weight\":1}", "{\"source\":\"analysis\"}");

            assertThat(runPromise(() -> edgeRepository.findEdgesForWorkspace("ws-1", "tenant-1", Set.of("DEPENDS_ON")))).isEmpty();
        }

        @Test
        @DisplayName("findEdgesForWorkspace keeps requested relationships when filter matches")
        void findEdgesForWorkspaceKeepsRequestedRelationshipsWhenFilterMatches() throws Exception {
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true, false);
            stubEdgeRow("edge-match", "node-a", "node-b", "CALLS", "{\"weight\":1}", "{\"source\":\"analysis\"}");

            assertThat(runPromise(() -> edgeRepository.findEdgesForWorkspace("ws-1", "tenant-1", Set.of("CALLS")))).hasSize(1);
        }

        @Test
        @DisplayName("findEdgesToTarget maps rows back to tenant scoped edges")
        void findEdgesToTargetMapsRowsBackToTenantScopedEdges() throws Exception {
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true, false);
            stubEdgeRow("edge-target", "node-x", "node-target", "USES", "{\"weight\":2}", "{\"source\":\"analysis\"}");

            List<YAPPCGraphEdge> edges = runPromise(() -> edgeRepository.findEdgesToTarget("node-target", "tenant-1", Set.of("USES")));

            assertThat(edges).singleElement().satisfies(edge -> {
                assertThat(edge.sourceNodeId()).isEqualTo("node-x");
                assertThat(edge.targetNodeId()).isEqualTo("node-target");
            });
        }

        @Test
        @DisplayName("findEdgesToTarget returns all rows when filter is empty")
        void findEdgesToTargetReturnsAllRowsWhenFilterIsEmpty() throws Exception {
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true, false);
            stubEdgeRow("edge-target-all", "node-x", "node-target", "USES", "{}", "{}");

            assertThat(runPromise(() -> edgeRepository.findEdgesToTarget("node-target", "tenant-1", Set.of()))).hasSize(1);
        }

        @Test
        @DisplayName("findEdgesToTarget filters out relationships not requested")
        void findEdgesToTargetFiltersOutRelationshipsNotRequested() throws Exception {
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true, false);
            stubEdgeRow("edge-target-all", "node-x", "node-target", "USES", "{}", "{}");

            assertThat(runPromise(() -> edgeRepository.findEdgesToTarget("node-target", "tenant-1", Set.of("DEPENDS_ON")))).isEmpty();
        }

        @Test
        @DisplayName("findEdgesByProject filters out relationship types not requested")
        void findEdgesByProjectFiltersOutRelationshipTypesNotRequested() throws Exception {
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true, false);
            stubEdgeRow("edge-project", "node-a", "node-b", "CALLS", "{\"weight\":1}", "{\"source\":\"analysis\"}");

            List<YAPPCGraphEdge> edges = runPromise(() -> edgeRepository.findEdgesByProject("proj-1", "tenant-1", Set.of("DEPENDS_ON")));

            assertThat(edges).isEmpty();
        }

        @Test
        @DisplayName("findEdgesByProject maps rows and falls back empty json payloads")
        void findEdgesByProjectMapsRowsAndFallsBackEmptyJsonPayloads() throws Exception {
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true, false);
            stubEdgeRow("edge-project-ok", "node-a", "node-b", "DEPENDS_ON", null, "");

            List<YAPPCGraphEdge> edges = runPromise(() -> edgeRepository.findEdgesByProject("proj-1", "tenant-1", Set.of("DEPENDS_ON")));

            assertThat(edges).singleElement().satisfies(edge -> {
                assertThat(edge.properties()).isEmpty();
                assertThat(edge.metadata().labels()).isEmpty();
            });
        }

        @Test
        @DisplayName("findEdgesByProject returns all rows when filter is empty")
        void findEdgesByProjectReturnsAllRowsWhenFilterIsEmpty() throws Exception {
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true, false);
            stubEdgeRow("edge-project-all", "node-a", "node-b", "USES", "{}", "{}");

            assertThat(runPromise(() -> edgeRepository.findEdgesByProject("proj-1", "tenant-1", Set.of()))).hasSize(1);
        }

        @Test
        @DisplayName("countEdgesByTenant returns count when a row is present")
        void countEdgesByTenantReturnsCountWhenRowIsPresent() throws Exception {
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true);
            when(resultSet.getInt("edge_count")).thenReturn(4);

            assertThat(runPromise(() -> edgeRepository.countEdgesByTenant("tenant-1"))).isEqualTo(4);
        }

        @Test
        @DisplayName("countEdgesByTenant returns zero when query yields no rows")
        void countEdgesByTenantReturnsZeroWhenQueryYieldsNoRows() throws Exception {
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(false);

            assertThat(runPromise(() -> edgeRepository.countEdgesByTenant("tenant-1"))).isZero();
        }

        @Test
        @DisplayName("deleteEdge is tenant scoped and reports whether a row was deleted")
        void deleteEdgeIsTenantScopedAndReportsWhetherARowWasDeleted() throws Exception {
            when(preparedStatement.executeUpdate()).thenReturn(1, 0);

            assertThat(runPromise(() -> edgeRepository.deleteEdge("edge-1", "tenant-1"))).isTrue();
            assertThat(runPromise(() -> edgeRepository.deleteEdge("edge-1", "tenant-1"))).isFalse();
        }

        @Test
        @DisplayName("saveEdge wraps serialization errors")
        void saveEdgeWrapsSerializationErrors() throws Exception {
            ObjectMapper brokenMapper = mock(ObjectMapper.class);
            when(brokenMapper.writeValueAsString(org.mockito.ArgumentMatchers.any()))
                    .thenThrow(mock(JsonProcessingException.class));
            KGEdgeRepository brokenRepository = new KGEdgeRepository(dataSource, brokenMapper, Runnable::run);

            assertThatThrownBy(() -> runPromise(() -> brokenRepository.saveEdge(edge())))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("serialize knowledge graph edge data");
        }

        @Test
        @DisplayName("findEdgesFromSource wraps invalid json payloads")
        void findEdgesFromSourceWrapsInvalidJsonPayloads() throws Exception {
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true);
            stubEdgeRow("edge-bad", "node-a", "node-b", "DEPENDS_ON", "{bad-json", "{}");

            assertThatThrownBy(() -> runPromise(() -> edgeRepository.findEdgesFromSource("node-a", "tenant-1", Set.of("DEPENDS_ON"))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("deserialize knowledge graph edge data");
        }
    }

    private void stubNodeRow(String nodeId, String nodeType, String propertiesJson, String tagsJson, String labelsJson) throws Exception {
        when(resultSet.getString("node_id")).thenReturn(nodeId);
        when(resultSet.getString("node_type")).thenReturn(nodeType);
        when(resultSet.getString("label")).thenReturn("BillingService");
        when(resultSet.getString("description")).thenReturn("Handles billing");
        when(resultSet.getString("properties_json")).thenReturn(propertiesJson);
        when(resultSet.getString("tags_json")).thenReturn(tagsJson);
        when(resultSet.getString("tenant_id")).thenReturn("tenant-1");
        when(resultSet.getString("project_id")).thenReturn("proj-1");
        when(resultSet.getString("workspace_id")).thenReturn("ws-1");
        when(resultSet.getString("created_by")).thenReturn("tester");
        when(resultSet.getTimestamp("created_at")).thenReturn(Timestamp.from(Instant.parse("2026-04-06T00:00:00Z")));
        when(resultSet.getTimestamp("updated_at")).thenReturn(Timestamp.from(Instant.parse("2026-04-06T01:00:00Z")));
        when(resultSet.getString("version")).thenReturn("1.0");
        when(resultSet.getString("labels_json")).thenReturn(labelsJson);
    }

    private void stubEdgeRow(String edgeId, String sourceId, String targetId, String relationshipType, String propertiesJson, String labelsJson) throws Exception {
        when(resultSet.getString("edge_id")).thenReturn(edgeId);
        when(resultSet.getString("from_node_id")).thenReturn(sourceId);
        when(resultSet.getString("to_node_id")).thenReturn(targetId);
        when(resultSet.getString("relationship_type")).thenReturn(relationshipType);
        when(resultSet.getString("properties_json")).thenReturn(propertiesJson);
        when(resultSet.getString("tenant_id")).thenReturn("tenant-1");
        when(resultSet.getString("project_id")).thenReturn("proj-1");
        when(resultSet.getString("workspace_id")).thenReturn("ws-1");
        when(resultSet.getString("created_by")).thenReturn("tester");
        when(resultSet.getTimestamp("created_at")).thenReturn(Timestamp.from(Instant.parse("2026-04-06T00:00:00Z")));
        when(resultSet.getTimestamp("updated_at")).thenReturn(Timestamp.from(Instant.parse("2026-04-06T01:00:00Z")));
        when(resultSet.getString("version")).thenReturn("1.0");
        when(resultSet.getString("labels_json")).thenReturn(labelsJson);
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