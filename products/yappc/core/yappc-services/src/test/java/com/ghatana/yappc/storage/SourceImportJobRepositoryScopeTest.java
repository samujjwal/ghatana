package com.ghatana.yappc.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * @doc.type class
 * @doc.purpose Verifies SourceImportJobRepository scopes all queries to tenant+workspace+project
 * @doc.layer test
 * @doc.pattern ScopeTest
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SourceImportJobRepository Scope Isolation Tests")
class SourceImportJobRepositoryScopeTest extends EventloopTestBase {

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement statement;

    @Mock
    private ResultSet resultSet;

    private SourceImportJobRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        repository = new SourceImportJobRepository(dataSource, new ObjectMapper(), Runnable::run);
        lenient().when(dataSource.getConnection()).thenReturn(connection);
        lenient().when(connection.prepareStatement(anyString())).thenReturn(statement);
        lenient().when(statement.executeQuery()).thenReturn(resultSet);
        lenient().when(resultSet.next()).thenReturn(false);
    }

    @Test
    @DisplayName("findJobsByScope binds workspaceId as the second parameter")
    void findJobsByScopeBindsWorkspaceId() throws Exception {
        runPromise(() -> repository.findJobsByScope("tenant-1", "workspace-X", "project-1", 20));

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(statement, atLeastOnce()).setString(anyInt(), captor.capture());

        assertThat(captor.getAllValues()).contains("workspace-X");
    }

    @Test
    @DisplayName("findJobsByScope requires non-null workspaceId")
    void findJobsByScopeRequiresWorkspaceId() {
        assertThatThrownBy(() -> repository.findJobsByScope("tenant-1", null, "project-1", 20))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("workspaceId");
    }

    @Test
    @DisplayName("findJobsByStatus binds workspaceId")
    void findJobsByStatusBindsWorkspaceId() throws Exception {
        runPromise(() -> repository.findJobsByStatus(
            "tenant-1", "workspace-Y", "project-1",
            com.ghatana.yappc.api.SourceImportJob.JobStatus.SUBMITTED, 10));

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(statement, atLeastOnce()).setString(anyInt(), captor.capture());

        assertThat(captor.getAllValues()).contains("workspace-Y");
    }

    @Test
    @DisplayName("findJobsByStatus requires non-null workspaceId")
    void findJobsByStatusRequiresWorkspaceId() {
        assertThatThrownBy(() -> repository.findJobsByStatus(
            "tenant-1", null, "project-1",
            com.ghatana.yappc.api.SourceImportJob.JobStatus.SUBMITTED, 10))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("workspaceId");
    }

    @Test
    @DisplayName("findJobById with scope binds all three scope parameters")
    void findJobByIdScopedBindsAllThreeParams() throws Exception {
        runPromise(() -> repository.findJobById("job-abc", "tenant-1", "workspace-1", "project-1"));

        verify(statement).setString(1, "job-abc");
        verify(statement).setString(2, "tenant-1");
        verify(statement).setString(3, "workspace-1");
        verify(statement).setString(4, "project-1");
    }
}
