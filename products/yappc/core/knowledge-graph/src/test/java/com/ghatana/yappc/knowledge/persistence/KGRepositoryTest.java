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
    void setUp() throws Exception { // GH-90000
        dataSource = mock(DataSource.class); // GH-90000
        connection = mock(Connection.class); // GH-90000
        preparedStatement = mock(PreparedStatement.class); // GH-90000
        resultSet = mock(ResultSet.class); // GH-90000

        when(dataSource.getConnection()).thenReturn(connection); // GH-90000
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement); // GH-90000
        when(preparedStatement.executeUpdate()).thenReturn(1); // GH-90000

        nodeRepository = new KGNodeRepository(dataSource, new ObjectMapper(), Runnable::run); // GH-90000
        edgeRepository = new KGEdgeRepository(dataSource, new ObjectMapper(), Runnable::run); // GH-90000
    }

    @Nested
    @DisplayName("KGNodeRepository")
    class NodeRepositoryTests {

        @Test
        @DisplayName("default constructor uses shared defaults")
        void defaultConstructorUsesSharedDefaults() { // GH-90000
            assertThat(new KGNodeRepository(dataSource)).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("saveNode persists all node fields")
        void saveNodePersistsAllFields() { // GH-90000
            YAPPCGraphNode node = node(); // GH-90000

            YAPPCGraphNode persisted = runPromise(() -> nodeRepository.saveNode(node)); // GH-90000
            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class); // GH-90000

            assertThat(persisted).isEqualTo(node); // GH-90000
            try {
                verify(connection).prepareStatement(sqlCaptor.capture()); // GH-90000
                verify(preparedStatement).setString(1, node.id()); // GH-90000
                verify(preparedStatement).setString(2, node.type().name()); // GH-90000
                verify(preparedStatement).setString(3, node.name()); // GH-90000
                verify(preparedStatement).setString(4, node.description()); // GH-90000
                verify(preparedStatement).setString(8, node.metadata().tenantId()); // GH-90000
                verify(preparedStatement).executeUpdate(); // GH-90000
            } catch (Exception exception) { // GH-90000
                throw new AssertionError(exception); // GH-90000
            }
            assertThat(sqlCaptor.getValue()).contains("ON CONFLICT (tenant_id, node_id) DO UPDATE SET");
            assertThat(sqlCaptor.getValue()).doesNotContain("tenant_id = EXCLUDED.tenant_id");
        }

        @Test
        @DisplayName("findNodesByType maps rows back to YAPPC nodes")
        void findNodesByTypeMapsRows() throws Exception { // GH-90000
            when(preparedStatement.executeQuery()).thenReturn(resultSet); // GH-90000
            when(resultSet.next()).thenReturn(true, false); // GH-90000
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

            List<YAPPCGraphNode> nodes = runPromise(() -> nodeRepository.findNodesByType("SERVICE", "tenant-1", 100)); // GH-90000

            assertThat(nodes).hasSize(1); // GH-90000
            assertThat(nodes.get(0).id()).isEqualTo("node-1");
            assertThat(nodes.get(0).properties()).containsEntry("language", "java"); // GH-90000
            assertThat(nodes.get(0).tags()).containsExactlyInAnyOrder("backend", "critical"); // GH-90000
        }

        @Test
        @DisplayName("findNodesByIds returns empty immediately when no ids supplied")
        void findNodesByIdsReturnsEmptyImmediatelyWhenNoIdsSupplied() { // GH-90000
            assertThat(runPromise(() -> nodeRepository.findNodesByIds(List.of(), "tenant-1"))).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("findNodesByIds maps rows for all requested ids")
        void findNodesByIdsMapsRowsForAllRequestedIds() throws Exception { // GH-90000
            when(preparedStatement.executeQuery()).thenReturn(resultSet); // GH-90000
            when(resultSet.next()).thenReturn(true, false); // GH-90000
            stubNodeRow("node-2", "CLASS", "{}", "[]", "{}"); // GH-90000

            List<YAPPCGraphNode> nodes = runPromise(() -> nodeRepository.findNodesByIds(List.of("node-2", "node-3"), "tenant-1")); // GH-90000

            assertThat(nodes).singleElement().satisfies(node -> assertThat(node.id()).isEqualTo("node-2"));
            verify(preparedStatement).setString(2, "node-2"); // GH-90000
            verify(preparedStatement).setString(3, "node-3"); // GH-90000
        }

        @Test
        @DisplayName("findNodeById returns mapped optional node")
        void findNodeByIdReturnsMappedOptionalNode() throws Exception { // GH-90000
            when(preparedStatement.executeQuery()).thenReturn(resultSet); // GH-90000
            when(resultSet.next()).thenReturn(true); // GH-90000
            stubNodeRow("node-7", "SERVICE", "{\"language\":\"java\"}", "[\"backend\"]", "{\"domain\":\"payments\"}"); // GH-90000

            Optional<YAPPCGraphNode> node = runPromise(() -> nodeRepository.findNodeById("node-7", "tenant-1")); // GH-90000

            assertThat(node).isPresent(); // GH-90000
            assertThat(node.orElseThrow().id()).isEqualTo("node-7");
            verify(preparedStatement).setString(1, "tenant-1"); // GH-90000
            verify(preparedStatement).setString(2, "node-7"); // GH-90000
        }

        @Test
        @DisplayName("findNodeById returns empty when row is missing")
        void findNodeByIdReturnsEmptyWhenRowIsMissing() throws Exception { // GH-90000
            when(preparedStatement.executeQuery()).thenReturn(resultSet); // GH-90000
            when(resultSet.next()).thenReturn(false); // GH-90000

            Optional<YAPPCGraphNode> node = runPromise(() -> nodeRepository.findNodeById("missing", "tenant-1")); // GH-90000

            assertThat(node).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("findNodesByProject maps rows and falls back empty json payloads")
        void findNodesByProjectMapsRowsAndFallsBackEmptyJsonPayloads() throws Exception { // GH-90000
            when(preparedStatement.executeQuery()).thenReturn(resultSet); // GH-90000
            when(resultSet.next()).thenReturn(true, false); // GH-90000
            stubNodeRow("node-project", "DOCUMENT", null, "", null); // GH-90000

            List<YAPPCGraphNode> nodes = runPromise(() -> nodeRepository.findNodesByProject("proj-1", "tenant-1")); // GH-90000

            assertThat(nodes).singleElement().satisfies(node -> { // GH-90000
                assertThat(node.properties()).isEmpty(); // GH-90000
                assertThat(node.tags()).isEmpty(); // GH-90000
                assertThat(node.metadata().labels()).isEmpty(); // GH-90000
            });
            verify(preparedStatement).setString(1, "tenant-1"); // GH-90000
            verify(preparedStatement).setString(2, "proj-1"); // GH-90000
        }

        @Test
        @DisplayName("findNodesByProject returns empty when no rows match")
        void findNodesByProjectReturnsEmptyWhenNoRowsMatch() throws Exception { // GH-90000
            when(preparedStatement.executeQuery()).thenReturn(resultSet); // GH-90000
            when(resultSet.next()).thenReturn(false); // GH-90000

            assertThat(runPromise(() -> nodeRepository.findNodesByProject("proj-missing", "tenant-1"))).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("findNodesByProject maps multiple rows")
        void findNodesByProjectMapsMultipleRows() throws Exception { // GH-90000
            ResultSet secondRow = mock(ResultSet.class); // GH-90000
            when(preparedStatement.executeQuery()).thenReturn(resultSet); // GH-90000
            when(resultSet.next()).thenReturn(true, true, false); // GH-90000
            stubNodeRow("node-10", "SERVICE", "{}", "[]", "{}"); // GH-90000
            when(resultSet.getString("node_id")).thenReturn("node-10", "node-11");
            when(resultSet.getString("node_type")).thenReturn("SERVICE", "API");

            List<YAPPCGraphNode> nodes = runPromise(() -> nodeRepository.findNodesByProject("proj-1", "tenant-1")); // GH-90000

            assertThat(nodes).extracting(YAPPCGraphNode::id).containsExactly("node-10", "node-11"); // GH-90000
        }

        @Test
        @DisplayName("countNodesByTenant returns count when a row is present")
        void countNodesByTenantReturnsCountWhenRowIsPresent() throws Exception { // GH-90000
            when(preparedStatement.executeQuery()).thenReturn(resultSet); // GH-90000
            when(resultSet.next()).thenReturn(true); // GH-90000
            when(resultSet.getInt("node_count")).thenReturn(9);

            assertThat(runPromise(() -> nodeRepository.countNodesByTenant("tenant-1"))).isEqualTo(9);
        }

        @Test
        @DisplayName("countNodesByTenant returns zero when query yields no rows")
        void countNodesByTenantReturnsZeroWhenQueryYieldsNoRows() throws Exception { // GH-90000
            when(preparedStatement.executeQuery()).thenReturn(resultSet); // GH-90000
            when(resultSet.next()).thenReturn(false); // GH-90000

            assertThat(runPromise(() -> nodeRepository.countNodesByTenant("tenant-1"))).isZero();
        }

        @Test
        @DisplayName("deleteNode is tenant scoped and reports whether a row was deleted")
        void deleteNodeIsTenantScopedAndReportsWhetherARowWasDeleted() throws Exception { // GH-90000
            when(preparedStatement.executeUpdate()).thenReturn(1, 0); // GH-90000

            assertThat(runPromise(() -> nodeRepository.deleteNode("node-1", "tenant-1"))).isTrue(); // GH-90000
            assertThat(runPromise(() -> nodeRepository.deleteNode("node-1", "tenant-1"))).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("saveNode wraps serialization errors")
        void saveNodeWrapsSerializationErrors() throws Exception { // GH-90000
            ObjectMapper brokenMapper = mock(ObjectMapper.class); // GH-90000
            when(brokenMapper.writeValueAsString(org.mockito.ArgumentMatchers.any())) // GH-90000
                    .thenThrow(mock(JsonProcessingException.class)); // GH-90000
            KGNodeRepository brokenRepository = new KGNodeRepository(dataSource, brokenMapper, Runnable::run); // GH-90000

            assertThatThrownBy(() -> runPromise(() -> brokenRepository.saveNode(node()))) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("serialize knowledge graph node data");
        }

        @Test
        @DisplayName("findNodesByType wraps invalid json payloads")
        void findNodesByTypeWrapsInvalidJsonPayloads() throws Exception { // GH-90000
            when(preparedStatement.executeQuery()).thenReturn(resultSet); // GH-90000
            when(resultSet.next()).thenReturn(true); // GH-90000
            stubNodeRow("node-bad", "SERVICE", "{bad-json", "[]", "{}"); // GH-90000

            assertThatThrownBy(() -> runPromise(() -> nodeRepository.findNodesByType("SERVICE", "tenant-1", 10))) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("deserialize knowledge graph node data");
        }
    }

    @Nested
    @DisplayName("KGEdgeRepository")
    class EdgeRepositoryTests {

        @Test
        @DisplayName("default constructor uses shared defaults")
        void defaultConstructorUsesSharedDefaults() { // GH-90000
            assertThat(new KGEdgeRepository(dataSource)).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("saveEdge persists all edge fields")
        void saveEdgePersistsAllFields() { // GH-90000
            YAPPCGraphEdge edge = edge(); // GH-90000

            YAPPCGraphEdge persisted = runPromise(() -> edgeRepository.saveEdge(edge)); // GH-90000
            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class); // GH-90000

            assertThat(persisted).isEqualTo(edge); // GH-90000
            try {
                verify(connection).prepareStatement(sqlCaptor.capture()); // GH-90000
                verify(preparedStatement).setString(1, edge.id()); // GH-90000
                verify(preparedStatement).setString(2, edge.sourceNodeId()); // GH-90000
                verify(preparedStatement).setString(3, edge.targetNodeId()); // GH-90000
                verify(preparedStatement).setString(4, edge.relationshipType().name()); // GH-90000
                verify(preparedStatement).executeUpdate(); // GH-90000
            } catch (Exception exception) { // GH-90000
                throw new AssertionError(exception); // GH-90000
            }
            assertThat(sqlCaptor.getValue()).contains("ON CONFLICT (tenant_id, edge_id) DO UPDATE SET");
            assertThat(sqlCaptor.getValue()).doesNotContain("tenant_id = EXCLUDED.tenant_id");
        }

        @Test
        @DisplayName("findEdgesFromSource maps rows back to YAPPC edges")
        void findEdgesFromSourceMapsRows() throws Exception { // GH-90000
            when(preparedStatement.executeQuery()).thenReturn(resultSet); // GH-90000
            when(resultSet.next()).thenReturn(true, false); // GH-90000
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

            List<YAPPCGraphEdge> edges = runPromise(() -> edgeRepository.findEdgesFromSource( // GH-90000
                    "node-a",
                    "tenant-1",
                    Set.of("DEPENDS_ON")));

            assertThat(edges).hasSize(1); // GH-90000
            assertThat(edges.get(0).id()).isEqualTo("edge-1");
            assertThat(edges.get(0).sourceNodeId()).isEqualTo("node-a");
            assertThat(edges.get(0).targetNodeId()).isEqualTo("node-b");
        }

        @Test
        @DisplayName("findEdgesForWorkspace returns all relationships when filter is empty")
        void findEdgesForWorkspaceReturnsAllRelationshipsWhenFilterIsEmpty() throws Exception { // GH-90000
            when(preparedStatement.executeQuery()).thenReturn(resultSet); // GH-90000
            when(resultSet.next()).thenReturn(true, false); // GH-90000
            stubEdgeRow("edge-workspace", "node-a", "node-b", "CALLS", "{\"weight\":1}", "{\"source\":\"analysis\"}"); // GH-90000

            List<YAPPCGraphEdge> edges = runPromise(() -> edgeRepository.findEdgesForWorkspace("ws-1", "tenant-1", Set.of())); // GH-90000

            assertThat(edges).singleElement().satisfies(edge -> assertThat(edge.relationshipType()).isEqualTo(YAPPCGraphEdge.YAPPCRelationshipType.CALLS)); // GH-90000
        }

        @Test
        @DisplayName("findEdgesFromSource returns all rows when filter is empty")
        void findEdgesFromSourceReturnsAllRowsWhenFilterIsEmpty() throws Exception { // GH-90000
            when(preparedStatement.executeQuery()).thenReturn(resultSet); // GH-90000
            when(resultSet.next()).thenReturn(true, false); // GH-90000
            stubEdgeRow("edge-all", "node-a", "node-b", "IMPLEMENTS", "{\"weight\":1}", "{\"source\":\"analysis\"}"); // GH-90000

            List<YAPPCGraphEdge> edges = runPromise(() -> edgeRepository.findEdgesFromSource("node-a", "tenant-1", Set.of())); // GH-90000

            assertThat(edges).singleElement().satisfies(edge -> assertThat(edge.relationshipType()).isEqualTo(YAPPCGraphEdge.YAPPCRelationshipType.IMPLEMENTS)); // GH-90000
        }

        @Test
        @DisplayName("findEdgesFromSource filters out relationships not requested")
        void findEdgesFromSourceFiltersOutRelationshipsNotRequested() throws Exception { // GH-90000
            when(preparedStatement.executeQuery()).thenReturn(resultSet); // GH-90000
            when(resultSet.next()).thenReturn(true, false); // GH-90000
            stubEdgeRow("edge-all", "node-a", "node-b", "IMPLEMENTS", "{\"weight\":1}", "{\"source\":\"analysis\"}"); // GH-90000

            assertThat(runPromise(() -> edgeRepository.findEdgesFromSource("node-a", "tenant-1", Set.of("DEPENDS_ON")))).isEmpty();
        }

        @Test
        @DisplayName("findEdgesForWorkspace filters out relationships not requested")
        void findEdgesForWorkspaceFiltersOutRelationshipsNotRequested() throws Exception { // GH-90000
            when(preparedStatement.executeQuery()).thenReturn(resultSet); // GH-90000
            when(resultSet.next()).thenReturn(true, false); // GH-90000
            stubEdgeRow("edge-filtered", "node-a", "node-b", "CALLS", "{\"weight\":1}", "{\"source\":\"analysis\"}"); // GH-90000

            assertThat(runPromise(() -> edgeRepository.findEdgesForWorkspace("ws-1", "tenant-1", Set.of("DEPENDS_ON")))).isEmpty();
        }

        @Test
        @DisplayName("findEdgesForWorkspace keeps requested relationships when filter matches")
        void findEdgesForWorkspaceKeepsRequestedRelationshipsWhenFilterMatches() throws Exception { // GH-90000
            when(preparedStatement.executeQuery()).thenReturn(resultSet); // GH-90000
            when(resultSet.next()).thenReturn(true, false); // GH-90000
            stubEdgeRow("edge-match", "node-a", "node-b", "CALLS", "{\"weight\":1}", "{\"source\":\"analysis\"}"); // GH-90000

            assertThat(runPromise(() -> edgeRepository.findEdgesForWorkspace("ws-1", "tenant-1", Set.of("CALLS")))).hasSize(1);
        }

        @Test
        @DisplayName("findEdgesToTarget maps rows back to tenant scoped edges")
        void findEdgesToTargetMapsRowsBackToTenantScopedEdges() throws Exception { // GH-90000
            when(preparedStatement.executeQuery()).thenReturn(resultSet); // GH-90000
            when(resultSet.next()).thenReturn(true, false); // GH-90000
            stubEdgeRow("edge-target", "node-x", "node-target", "USES", "{\"weight\":2}", "{\"source\":\"analysis\"}"); // GH-90000

            List<YAPPCGraphEdge> edges = runPromise(() -> edgeRepository.findEdgesToTarget("node-target", "tenant-1", Set.of("USES")));

            assertThat(edges).singleElement().satisfies(edge -> { // GH-90000
                assertThat(edge.sourceNodeId()).isEqualTo("node-x");
                assertThat(edge.targetNodeId()).isEqualTo("node-target");
            });
        }

        @Test
        @DisplayName("findEdgesToTarget returns all rows when filter is empty")
        void findEdgesToTargetReturnsAllRowsWhenFilterIsEmpty() throws Exception { // GH-90000
            when(preparedStatement.executeQuery()).thenReturn(resultSet); // GH-90000
            when(resultSet.next()).thenReturn(true, false); // GH-90000
            stubEdgeRow("edge-target-all", "node-x", "node-target", "USES", "{}", "{}"); // GH-90000

            assertThat(runPromise(() -> edgeRepository.findEdgesToTarget("node-target", "tenant-1", Set.of()))).hasSize(1); // GH-90000
        }

        @Test
        @DisplayName("findEdgesToTarget filters out relationships not requested")
        void findEdgesToTargetFiltersOutRelationshipsNotRequested() throws Exception { // GH-90000
            when(preparedStatement.executeQuery()).thenReturn(resultSet); // GH-90000
            when(resultSet.next()).thenReturn(true, false); // GH-90000
            stubEdgeRow("edge-target-all", "node-x", "node-target", "USES", "{}", "{}"); // GH-90000

            assertThat(runPromise(() -> edgeRepository.findEdgesToTarget("node-target", "tenant-1", Set.of("DEPENDS_ON")))).isEmpty();
        }

        @Test
        @DisplayName("findEdgesByProject filters out relationship types not requested")
        void findEdgesByProjectFiltersOutRelationshipTypesNotRequested() throws Exception { // GH-90000
            when(preparedStatement.executeQuery()).thenReturn(resultSet); // GH-90000
            when(resultSet.next()).thenReturn(true, false); // GH-90000
            stubEdgeRow("edge-project", "node-a", "node-b", "CALLS", "{\"weight\":1}", "{\"source\":\"analysis\"}"); // GH-90000

            List<YAPPCGraphEdge> edges = runPromise(() -> edgeRepository.findEdgesByProject("proj-1", "tenant-1", Set.of("DEPENDS_ON")));

            assertThat(edges).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("findEdgesByProject maps rows and falls back empty json payloads")
        void findEdgesByProjectMapsRowsAndFallsBackEmptyJsonPayloads() throws Exception { // GH-90000
            when(preparedStatement.executeQuery()).thenReturn(resultSet); // GH-90000
            when(resultSet.next()).thenReturn(true, false); // GH-90000
            stubEdgeRow("edge-project-ok", "node-a", "node-b", "DEPENDS_ON", null, ""); // GH-90000

            List<YAPPCGraphEdge> edges = runPromise(() -> edgeRepository.findEdgesByProject("proj-1", "tenant-1", Set.of("DEPENDS_ON")));

            assertThat(edges).singleElement().satisfies(edge -> { // GH-90000
                assertThat(edge.properties()).isEmpty(); // GH-90000
                assertThat(edge.metadata().labels()).isEmpty(); // GH-90000
            });
        }

        @Test
        @DisplayName("findEdgesByProject returns all rows when filter is empty")
        void findEdgesByProjectReturnsAllRowsWhenFilterIsEmpty() throws Exception { // GH-90000
            when(preparedStatement.executeQuery()).thenReturn(resultSet); // GH-90000
            when(resultSet.next()).thenReturn(true, false); // GH-90000
            stubEdgeRow("edge-project-all", "node-a", "node-b", "USES", "{}", "{}"); // GH-90000

            assertThat(runPromise(() -> edgeRepository.findEdgesByProject("proj-1", "tenant-1", Set.of()))).hasSize(1); // GH-90000
        }

        @Test
        @DisplayName("countEdgesByTenant returns count when a row is present")
        void countEdgesByTenantReturnsCountWhenRowIsPresent() throws Exception { // GH-90000
            when(preparedStatement.executeQuery()).thenReturn(resultSet); // GH-90000
            when(resultSet.next()).thenReturn(true); // GH-90000
            when(resultSet.getInt("edge_count")).thenReturn(4);

            assertThat(runPromise(() -> edgeRepository.countEdgesByTenant("tenant-1"))).isEqualTo(4);
        }

        @Test
        @DisplayName("countEdgesByTenant returns zero when query yields no rows")
        void countEdgesByTenantReturnsZeroWhenQueryYieldsNoRows() throws Exception { // GH-90000
            when(preparedStatement.executeQuery()).thenReturn(resultSet); // GH-90000
            when(resultSet.next()).thenReturn(false); // GH-90000

            assertThat(runPromise(() -> edgeRepository.countEdgesByTenant("tenant-1"))).isZero();
        }

        @Test
        @DisplayName("deleteEdge is tenant scoped and reports whether a row was deleted")
        void deleteEdgeIsTenantScopedAndReportsWhetherARowWasDeleted() throws Exception { // GH-90000
            when(preparedStatement.executeUpdate()).thenReturn(1, 0); // GH-90000

            assertThat(runPromise(() -> edgeRepository.deleteEdge("edge-1", "tenant-1"))).isTrue(); // GH-90000
            assertThat(runPromise(() -> edgeRepository.deleteEdge("edge-1", "tenant-1"))).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("saveEdge wraps serialization errors")
        void saveEdgeWrapsSerializationErrors() throws Exception { // GH-90000
            ObjectMapper brokenMapper = mock(ObjectMapper.class); // GH-90000
            when(brokenMapper.writeValueAsString(org.mockito.ArgumentMatchers.any())) // GH-90000
                    .thenThrow(mock(JsonProcessingException.class)); // GH-90000
            KGEdgeRepository brokenRepository = new KGEdgeRepository(dataSource, brokenMapper, Runnable::run); // GH-90000

            assertThatThrownBy(() -> runPromise(() -> brokenRepository.saveEdge(edge()))) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("serialize knowledge graph edge data");
        }

        @Test
        @DisplayName("findEdgesFromSource wraps invalid json payloads")
        void findEdgesFromSourceWrapsInvalidJsonPayloads() throws Exception { // GH-90000
            when(preparedStatement.executeQuery()).thenReturn(resultSet); // GH-90000
            when(resultSet.next()).thenReturn(true); // GH-90000
            stubEdgeRow("edge-bad", "node-a", "node-b", "DEPENDS_ON", "{bad-json", "{}"); // GH-90000

            assertThatThrownBy(() -> runPromise(() -> edgeRepository.findEdgesFromSource("node-a", "tenant-1", Set.of("DEPENDS_ON"))))
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("deserialize knowledge graph edge data");
        }
    }

    private void stubNodeRow(String nodeId, String nodeType, String propertiesJson, String tagsJson, String labelsJson) throws Exception { // GH-90000
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

    private void stubEdgeRow(String edgeId, String sourceId, String targetId, String relationshipType, String propertiesJson, String labelsJson) throws Exception { // GH-90000
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

    private static YAPPCGraphNode node() { // GH-90000
        return YAPPCGraphNode.builder() // GH-90000
                .id("node-1")
                .type(YAPPCGraphNode.YAPPCNodeType.SERVICE) // GH-90000
                .name("BillingService")
                .description("Handles billing")
                .properties(Map.of("language", "java")) // GH-90000
                .tags(Set.of("backend", "critical")) // GH-90000
                .metadata(metadata()) // GH-90000
                .build(); // GH-90000
    }

    private static YAPPCGraphEdge edge() { // GH-90000
        return YAPPCGraphEdge.builder() // GH-90000
                .id("edge-1")
                .sourceNodeId("node-a")
                .targetNodeId("node-b")
                .relationshipType(YAPPCGraphEdge.YAPPCRelationshipType.DEPENDS_ON) // GH-90000
                .properties(Map.of("weight", 1)) // GH-90000
                .metadata(metadata()) // GH-90000
                .build(); // GH-90000
    }

    private static YAPPCGraphMetadata metadata() { // GH-90000
        return new YAPPCGraphMetadata( // GH-90000
                "tenant-1",
                "proj-1",
                "ws-1",
                "tester",
                Instant.parse("2026-04-06T00:00:00Z"),
                Instant.parse("2026-04-06T01:00:00Z"),
                "1.0",
                Map.of("domain", "payments")); // GH-90000
    }
}
