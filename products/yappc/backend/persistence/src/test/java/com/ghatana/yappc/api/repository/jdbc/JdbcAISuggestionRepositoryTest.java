package com.ghatana.yappc.api.repository.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.api.domain.AISuggestion;
import com.ghatana.yappc.api.domain.AISuggestion.Priority;
import com.ghatana.yappc.api.domain.AISuggestion.SuggestionStatus;
import com.ghatana.yappc.api.domain.AISuggestion.SuggestionType;

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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

@DisplayName("JDBC AI Suggestion Repository Tests")
/**
 * @doc.type class
 * @doc.purpose Handles jdbc ai suggestion repository test operations
 * @doc.layer product
 * @doc.pattern Test
 */
class JdbcAISuggestionRepositoryTest extends EventloopTestBase {

    @Mock
    private DataSource dataSource;
    @Mock
    private Connection connection;
    @Mock
    private PreparedStatement preparedStatement;
    @Mock
    private ResultSet resultSet;

    private JdbcAISuggestionRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Executor executor = Runnable::run; // Sync executor for tests

    @BeforeEach
    void setUp() throws SQLException {
        MockitoAnnotations.openMocks(this);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        
        repository = new JdbcAISuggestionRepository(dataSource, executor, objectMapper);
    }

    @Test
    void shouldFindById() throws SQLException {
        // GIVEN
        UUID id = UUID.randomUUID();
        setupMockResultSet(id);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);

        // WHEN
        Optional<AISuggestion> result = runPromise(() -> repository.findById("test-tenant", id));

        // THEN
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(id);
        assertThat(result.get().getTitle()).isEqualTo("Optimize Query");
        verify(preparedStatement).setString(1, "test-tenant");
        verify(preparedStatement).setObject(2, id);
    }

    @Test
    void shouldFindPending() throws SQLException {
        // GIVEN
        setupMockResultSet(UUID.randomUUID());
        when(preparedStatement.executeQuery()).thenReturn(resultSet);

        // WHEN
        List<AISuggestion> result = runPromise(() -> repository.findPending("test-tenant"));

        // THEN
        assertThat(result).hasSize(1);
        verify(preparedStatement).setObject(1, "test-tenant");
    }

    @Test
    void shouldCountByStatus() throws SQLException {
        // GIVEN
        when(resultSet.next()).thenReturn(true).thenReturn(true).thenReturn(false);
        when(resultSet.getString("status")).thenReturn("ACCEPTED").thenReturn("PENDING");
        when(resultSet.getLong("cnt")).thenReturn(5L).thenReturn(3L);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);

        // WHEN
        Map<SuggestionStatus, Long> counts = runPromise(() -> repository.countByStatus("test-tenant"));

        // THEN
        assertThat(counts.get(SuggestionStatus.ACCEPTED)).isEqualTo(5L);
        assertThat(counts.get(SuggestionStatus.PENDING)).isEqualTo(3L);
        assertThat(counts.get(SuggestionStatus.REJECTED)).isEqualTo(0L); // Default
    }

    @Test
    void shouldGetAcceptanceRate() throws SQLException {
        // GIVEN
        when(resultSet.next()).thenReturn(true).thenReturn(false);
        when(resultSet.getLong("accepted")).thenReturn(80L);
        when(resultSet.getLong("rejected")).thenReturn(20L);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);

        // WHEN
        Double rate = runPromise(() -> repository.getAcceptanceRate("test-tenant"));

        // THEN
        assertThat(rate).isEqualTo(0.8);
    }

    @Test
    void shouldSaveNewSuggestion() throws SQLException {
        // GIVEN
        AISuggestion suggestion = new AISuggestion();
        suggestion.setTenantId("test-tenant");
        suggestion.setProjectId("proj-1");
        suggestion.setType(SuggestionType.PERFORMANCE);
        suggestion.setStatus(SuggestionStatus.PENDING);
        suggestion.setTitle("New Suggestion");
        suggestion.setContent("Content");
        suggestion.setRationale("Why");
        suggestion.setPriority(Priority.MEDIUM);
        
        // Mock exists check -> false
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);
        
        // Mock insert -> success
        when(preparedStatement.executeUpdate()).thenReturn(1);

        // WHEN
        AISuggestion saved = runPromise(() -> repository.save(suggestion));

        // THEN
        assertThat(saved.getId()).isNotNull();
        verify(preparedStatement, times(1)).executeQuery(); // exists check
        verify(preparedStatement, times(1)).executeUpdate(); // insert
    }

    private void setupMockResultSet(UUID id) throws SQLException {
        when(resultSet.next()).thenReturn(true).thenReturn(false);
        when(resultSet.getString("id")).thenReturn(id.toString());
        when(resultSet.getString("tenant_id")).thenReturn("test-tenant");
        when(resultSet.getString("project_id")).thenReturn("proj-1");
        when(resultSet.getString("type")).thenReturn("PERFORMANCE");
        when(resultSet.getString("status")).thenReturn("PENDING");
        when(resultSet.getString("title")).thenReturn("Optimize Query");
        when(resultSet.getString("content")).thenReturn("Use indexing");
        when(resultSet.getString("rationale")).thenReturn("Performance");
        when(resultSet.getString("source_model")).thenReturn("gpt-4");
        when(resultSet.getString("target_resource_id")).thenReturn("res-1");
        when(resultSet.getString("target_resource_type")).thenReturn("code");
        when(resultSet.getDouble("confidence")).thenReturn(0.95);
        when(resultSet.getString("priority")).thenReturn("HIGH");
        when(resultSet.getString("created_by")).thenReturn("system");
        when(resultSet.getTimestamp("created_at")).thenReturn(new Timestamp(System.currentTimeMillis()));
        when(resultSet.getString("tags")).thenReturn("[]");
        when(resultSet.getString("metadata")).thenReturn("{}");
    }
}
