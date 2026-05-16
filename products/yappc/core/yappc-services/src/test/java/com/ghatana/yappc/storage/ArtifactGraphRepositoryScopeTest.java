package com.ghatana.yappc.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.domain.artifact.ArtifactNodeDto;
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
    @DisplayName("saveNodes binds workspaceId when saving each node")
    void saveNodes_bindsWorkspaceId() throws Exception {
        ArtifactNodeDto node = new ArtifactNodeDto(
                "node-1", "component", "MyComponent", "src/MyComponent.tsx",
                "", Map.of(), List.of(), TENANT_ID, PRODUCT_ID,
                Map.of(), "ts-extractor", "1.0.0", 0.9, "exact",
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
}
