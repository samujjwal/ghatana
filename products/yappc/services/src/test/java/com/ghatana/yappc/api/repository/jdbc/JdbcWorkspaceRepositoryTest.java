package com.ghatana.yappc.api.repository.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.api.domain.Workspace;
import com.ghatana.yappc.api.domain.Workspace.WorkspaceStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

@DisplayName("JDBC Workspace Repository Tests")
/**
 * @doc.type class
 * @doc.purpose Handles jdbc workspace repository test operations
 * @doc.layer product
 * @doc.pattern Test
 */
class JdbcWorkspaceRepositoryTest extends EventloopTestBase {

    @Mock
    private DataSource dataSource;
    @Mock
    private Connection connection;
    @Mock
    private PreparedStatement preparedStatement;
    @Mock
    private ResultSet resultSet;

    private JdbcWorkspaceRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final Executor executor = Runnable::run;

    @BeforeEach
    void setUp() throws SQLException {
        MockitoAnnotations.openMocks(this);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        
        repository = new JdbcWorkspaceRepository(dataSource, executor, objectMapper);
    }

    @Test
    void shouldFindById() throws SQLException {
        // GIVEN
        UUID id = UUID.randomUUID();
        setupMockResultSet(id);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);

        // WHEN
        Optional<Workspace> result = runPromise(() -> repository.findById("test-tenant", id));

        // THEN
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(id);
        assertThat(result.get().getName()).isEqualTo("Test Workspace");
        verify(preparedStatement).setString(1, "test-tenant");
        verify(preparedStatement).setObject(2, id);
    }

    @Test
    void shouldFindByMemberUserId() throws SQLException {
        // GIVEN
        setupMockResultSet(UUID.randomUUID());
        when(preparedStatement.executeQuery()).thenReturn(resultSet);

        // WHEN
        List<Workspace> result = runPromise(() -> repository.findByMemberUserId("test-tenant", "user-123"));

        // THEN
        assertThat(result).hasSize(1);
        // Uses queryList, so ensures setObject is called
        verify(preparedStatement).setObject(1, "test-tenant");
        // Param 2 is the JSON filter string
        verify(preparedStatement).setObject(2, "[{\"userId\": \"user-123\"}]");
    }

    @Test
    void shouldSaveNewWorkspace() throws SQLException {
        // GIVEN
        Workspace workspace = new Workspace();
        workspace.setTenantId("test-tenant");
        workspace.setName("New Workspace");
        workspace.setDescription("Desc");
        workspace.setOwnerId("user-1");
        
        // Mock exists check -> false
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);
        
        // Mock insert -> success
        when(preparedStatement.executeUpdate()).thenReturn(1);

        // WHEN
        Workspace saved = runPromise(() -> repository.save(workspace));

        // THEN
        assertThat(saved.getId()).isNotNull();
        verify(preparedStatement, times(1)).executeQuery(); // exists check
        verify(preparedStatement, times(1)).executeUpdate(); // insert
    }

    private void setupMockResultSet(UUID id) throws SQLException {
        when(resultSet.next()).thenReturn(true).thenReturn(false);
        when(resultSet.getString("id")).thenReturn(id.toString());
        when(resultSet.getString("tenant_id")).thenReturn("test-tenant");
        when(resultSet.getString("name")).thenReturn("Test Workspace");
        when(resultSet.getString("description")).thenReturn("A test workspace");
        when(resultSet.getString("owner_id")).thenReturn("user-1");
        when(resultSet.getString("status")).thenReturn("ACTIVE");
        when(resultSet.getTimestamp("created_at")).thenReturn(new Timestamp(System.currentTimeMillis()));
        when(resultSet.getTimestamp("updated_at")).thenReturn(new Timestamp(System.currentTimeMillis()));
        // JSONB fields
        when(resultSet.getString("settings")).thenReturn("{}");
        when(resultSet.getString("members")).thenReturn("[]");
        when(resultSet.getString("teams")).thenReturn("[]");
        when(resultSet.getString("metadata")).thenReturn("{}");
    }
}
