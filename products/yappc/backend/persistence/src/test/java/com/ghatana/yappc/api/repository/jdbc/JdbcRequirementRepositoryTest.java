package com.ghatana.yappc.api.repository.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ghatana.yappc.api.domain.Requirement;
import com.ghatana.yappc.api.domain.Requirement.Priority;
import com.ghatana.yappc.api.domain.Requirement.RequirementStatus;
import com.ghatana.yappc.api.domain.Requirement.RequirementType;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.eventloop.Eventloop;
import com.ghatana.platform.testing.activej.EventloopTestBase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

import javax.sql.DataSource;

@DisplayName("JDBC Requirement Repository Tests")
/**
 * @doc.type class
 * @doc.purpose Handles jdbc requirement repository test operations
 * @doc.layer product
 * @doc.pattern Test
 */
class JdbcRequirementRepositoryTest extends EventloopTestBase {

    @Mock
    private DataSource dataSource;
    @Mock
    private Connection connection;
    @Mock
    private PreparedStatement preparedStatement;
    @Mock
    private ResultSet resultSet;

    private JdbcRequirementRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Executor executor = Runnable::run; // Sync executor for tests

    @BeforeEach
    void setUp() throws SQLException {
        MockitoAnnotations.openMocks(this);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(connection.prepareStatement(anyString(), any(int[].class))).thenReturn(preparedStatement); // For returning generated keys if used, or just overloading
        
        repository = new JdbcRequirementRepository(dataSource, objectMapper);
    }

    @Test
    void shouldFindById() throws SQLException {
        // GIVEN
        UUID id = UUID.randomUUID();
        setupMockResultSet(id);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);

        // WHEN
        Optional<Requirement> result = runPromise(() -> repository.findById("test-tenant", id));

        // THEN
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(id);
        assertThat(result.get().getTitle()).isEqualTo("Test Use Case");
        // Verify params. Note: implementation might set different indices based on query.
        verify(preparedStatement).setString(1, "test-tenant");
        verify(preparedStatement).setObject(2, id);
    }

    @Test
    void shouldSaveNewRequirement() throws SQLException {
        // GIVEN
        Requirement req = Requirement.builder()
                .tenantId("test-tenant")
                .title("New Requirement")
                .description("Description")
                .type(RequirementType.FUNCTIONAL)
                .status(RequirementStatus.DRAFT)
                .priority(Priority.HIGH)
                .createdBy(UUID.randomUUID().toString())
                .build();

        // Mock existsSync -> finding nothing
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);
        
        // Mock insertSync -> success
        when(preparedStatement.executeUpdate()).thenReturn(1);

        // WHEN
        Requirement saved = runPromise(() -> repository.save(req));

        // THEN
        assertThat(saved).isNotNull();
        // Just verify expected calls happened
        verify(preparedStatement, times(1)).executeQuery(); // exists check
        verify(preparedStatement, times(1)).executeUpdate(); // insert
    }
    
    @Test
    void shouldUpdateExistingRequirement() throws SQLException {
        // GIVEN
        UUID id = UUID.randomUUID();
        Requirement req = Requirement.builder()
                .id(id)
                .tenantId("test-tenant")
                .title("Updated Title")
                .description("Description")
                .type(RequirementType.FUNCTIONAL)
                .status(RequirementStatus.APPROVED)
                .priority(Priority.MEDIUM)
                .createdBy(UUID.randomUUID().toString())
                .build();

        // Mock existsSync -> found
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        
        // Mock updateSync -> success
        when(preparedStatement.executeUpdate()).thenReturn(1);

        // WHEN
        Requirement saved = runPromise(() -> repository.save(req));

        // THEN
        assertThat(saved.getTitle()).isEqualTo("Updated Title");
        verify(preparedStatement, times(1)).executeQuery(); // exists check
        verify(preparedStatement, times(1)).executeUpdate(); // update
    }

    private void setupMockResultSet(UUID id) throws SQLException {
        setupMockResultSet(this.resultSet, id);
    }

    private void setupMockResultSet(ResultSet rs, UUID id) throws SQLException {
        when(rs.next()).thenReturn(true).thenReturn(false);
        when(rs.getString("id")).thenReturn(id.toString()); // Fix for mapRow requiring String
        when(rs.getObject("id", UUID.class)).thenReturn(id);
        when(rs.getString("tenant_id")).thenReturn("test-tenant");
        when(rs.getString("title")).thenReturn("Test Use Case");
        when(rs.getString("description")).thenReturn("A test requirement");
        when(rs.getString("type")).thenReturn("FUNCTIONAL");
        when(rs.getString("status")).thenReturn("DRAFT");
        when(rs.getString("priority")).thenReturn("HIGH");
        when(rs.getTimestamp("created_at")).thenReturn(new Timestamp(System.currentTimeMillis()));
        when(rs.getString("created_by")).thenReturn(UUID.randomUUID().toString()); // Fix type
        // JSONB fields need to respond w/ string representation of JSON
        when(rs.getString("tags")).thenReturn("[]");
        when(rs.getString("dependencies")).thenReturn("[]");
        when(rs.getString("quality_metrics")).thenReturn("{}");
        when(rs.getString("metadata")).thenReturn("{}"); // Added metadata
    }
}
