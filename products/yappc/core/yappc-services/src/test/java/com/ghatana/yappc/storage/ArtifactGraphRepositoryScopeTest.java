package com.ghatana.yappc.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.domain.artifact.*;
import com.ghatana.yappc.domain.artifact.SourceLocationDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Verifies that workspaceId is correctly bound in all SQL queries — no more hardcoded 'default'
 * @doc.layer test
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ArtifactGraphRepository Workspace Scope Tests")
class ArtifactGraphRepositoryScopeTest extends EventloopTestBase {

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement statement;

    @Mock
    private ResultSet resultSet;

    private ArtifactGraphRepository repository;

    private static final Executor DIRECT_EXECUTOR = Runnable::run;
    private static final String TENANT_ID = "tenant-acme";
    private static final String WORKSPACE_ID = "ws-engineering";
    private static final String PRODUCT_ID = "proj-alpha";

    @BeforeEach
    void setUp() throws Exception {
        repository = new ArtifactGraphRepository(dataSource, new ObjectMapper(), DIRECT_EXECUTOR);
        lenient().when(dataSource.getConnection()).thenReturn(connection);
        lenient().when(connection.prepareStatement(anyString())).thenReturn(statement);
        lenient().when(statement.executeQuery()).thenReturn(resultSet);
        lenient().when(resultSet.next()).thenReturn(false);
    }

    @Test
    @DisplayName("findNodesByProduct binds workspaceId at position 2 — not 'default'")
    void findNodesByProduct_bindsWorkspaceId() throws Exception {
        runPromise(() -> repository.findNodesByProduct(PRODUCT_ID, TENANT_ID, WORKSPACE_ID, 100));

        verify(statement).setString(1, TENANT_ID);
        verify(statement).setString(2, WORKSPACE_ID);
        verify(statement).setString(3, PRODUCT_ID);
    }

    @Test
    @DisplayName("findNodesByProduct with includeTombstones=true binds workspaceId")
    void findNodesByProductIncludeTombstones_bindsWorkspaceId() throws Exception {
        runPromise(() -> repository.findNodesByProduct(PRODUCT_ID, TENANT_ID, WORKSPACE_ID, 50, true));

        verify(statement).setString(1, TENANT_ID);
        verify(statement).setString(2, WORKSPACE_ID);
        verify(statement).setString(3, PRODUCT_ID);
    }

    @Test
    @DisplayName("findEdgesByProduct binds workspaceId at position 2")
    void findEdgesByProduct_bindsWorkspaceId() throws Exception {
        runPromise(() -> repository.findEdgesByProduct(PRODUCT_ID, TENANT_ID, WORKSPACE_ID));

        verify(statement).setString(1, TENANT_ID);
        verify(statement).setString(2, WORKSPACE_ID);
        verify(statement).setString(3, PRODUCT_ID);
    }

    @Test
    @DisplayName("findNodesByIds binds workspaceId at position 2")
    void findNodesByIds_bindsWorkspaceId() throws Exception {
        runPromise(() -> repository.findNodesByIds(List.of("node-1", "node-2"), TENANT_ID, WORKSPACE_ID));

        verify(statement).setString(1, TENANT_ID);
        verify(statement).setString(2, WORKSPACE_ID);
    }

    @Test
    @DisplayName("tombstoneGraphForProduct binds workspaceId in both edge and node UPDATE statements")
    void tombstoneGraphForProduct_bindsWorkspaceIdInBothStatements() throws Exception {
        runPromise(() -> repository.tombstoneGraphForProduct(PRODUCT_ID, TENANT_ID, WORKSPACE_ID));

        verify(statement, atLeast(2)).setString(eq(3), eq(WORKSPACE_ID));
    }

    @Test
    @DisplayName("tombstoneGraphForSnapshot binds workspaceId and snapshotId in both edge and node UPDATE statements")
    void tombstoneGraphForSnapshot_bindsWorkspaceAndSnapshotInBothStatements() throws Exception {
        String snapshotId = "snap-rollback-1";

        runPromise(() -> repository.tombstoneGraphForSnapshot(PRODUCT_ID, TENANT_ID, WORKSPACE_ID, snapshotId));

        verify(statement, atLeast(2)).setString(eq(3), eq(WORKSPACE_ID));
        verify(statement, atLeast(2)).setString(eq(5), eq(snapshotId));
    }

    @Test
    @DisplayName("tombstoneGraphForSnapshot returns true when either node or edge tombstone updates rows")
    void tombstoneGraphForSnapshot_returnsTrueWhenAnyRowTombstoned() throws Exception {
        when(statement.executeUpdate()).thenReturn(1, 0);

        Boolean tombstoned = runPromise(() -> repository.tombstoneGraphForSnapshot(
            PRODUCT_ID,
            TENANT_ID,
            WORKSPACE_ID,
            "snap-rollback-2"
        ));

        assertThat(tombstoned).isTrue();
    }

    @Test
    @DisplayName("saveNodes binds workspaceId when saving each node")
    void saveNodes_bindsWorkspaceId() throws Exception {
        ArtifactNodeDto node = new ArtifactNodeDto(
                "node-1", "component", "MyComponent", "src/MyComponent.tsx",
                "", Map.of(), List.of(), TENANT_ID, PRODUCT_ID,
                new SourceLocationDto("src/MyComponent.tsx", 0, 0, 0, 0), "ts-extractor", "1.0.0", 0.9, "exact",
                List.of(), List.of(), null, null
        );

        runPromise(() -> repository.saveNodes(PRODUCT_ID, TENANT_ID, WORKSPACE_ID, List.of(node)));

        verify(statement).setString(9, WORKSPACE_ID);
    }

    @Test
    @DisplayName("tombstoneNodes binds workspaceId at position 4")
    void tombstoneNodes_bindsWorkspaceId() throws Exception {
        runPromise(() -> repository.tombstoneNodes(List.of("node-1"), TENANT_ID, WORKSPACE_ID, "snap-1"));

        verify(statement).setString(4, WORKSPACE_ID);
    }

    @Test
    @DisplayName("computeSnapshotDiff handles null node content without throwing")
    void computeSnapshotDiff_handlesNullNodeContent() throws Exception {
        lenient().when(resultSet.next()).thenReturn(true, true, false);
        lenient().when(resultSet.getString("snapshot_id")).thenReturn("snap-from", "snap-to");
        lenient().when(resultSet.getString("node_id")).thenReturn("node-1", "node-1");
        lenient().when(resultSet.getString("node_type")).thenReturn("component", "component");
        lenient().when(resultSet.getString("node_name")).thenReturn("Widget", "Widget");
        lenient().when(resultSet.getString("file_path")).thenReturn("src/Widget.tsx", "src/Widget.tsx");
        lenient().doReturn(null).when(resultSet).getString("content_snippet");
        lenient().when(resultSet.getString("properties_json")).thenReturn("{}", "{}");
        lenient().when(resultSet.getString("tags_json")).thenReturn("[]", "[]");
        lenient().when(resultSet.getString("tenant_id")).thenReturn(TENANT_ID, TENANT_ID);
        lenient().when(resultSet.getString("workspace_id")).thenReturn(WORKSPACE_ID, WORKSPACE_ID);
        lenient().when(resultSet.getString("project_id")).thenReturn(PRODUCT_ID, PRODUCT_ID);
        lenient().doReturn(null).when(resultSet).getString("source_location_json");
        lenient().when(resultSet.getString("extractor_id")).thenReturn("ts-extractor", "ts-extractor");
        lenient().when(resultSet.getString("extractor_version")).thenReturn("1.0.0", "1.0.0");
        lenient().when(resultSet.getObject("confidence")).thenReturn(0.9d, 0.9d);
        lenient().when(resultSet.getDouble("confidence")).thenReturn(0.9d, 0.9d);
        lenient().when(resultSet.getString("provenance")).thenReturn("exact", "exact");
        lenient().when(resultSet.getString("privacy_security_flags_json")).thenReturn("[]", "[]");
        lenient().when(resultSet.getString("residual_fragment_ids_json")).thenReturn("[]", "[]");
        lenient().when(resultSet.getString("source_ref")).thenReturn("source-ref", "source-ref");
        lenient().when(resultSet.getString("symbol_ref")).thenReturn("symbol-ref", "symbol-ref");

        ArtifactGraphRepository.SnapshotDiffResult diff = runPromise(() -> repository.computeSnapshotDiff(
            PRODUCT_ID,
            TENANT_ID,
            WORKSPACE_ID,
            "snap-from",
            "snap-to"
        ));

        assertThat(diff.modifiedNodes()).isEmpty();
        assertThat(diff.addedNodes()).isEmpty();
        assertThat(diff.removedNodes()).isEmpty();
    }
}
