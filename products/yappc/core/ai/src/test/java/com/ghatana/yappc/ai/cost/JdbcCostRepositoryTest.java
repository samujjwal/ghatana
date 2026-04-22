package com.ghatana.yappc.ai.cost;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.ai.cost.CostRepository.CostEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link JdbcCostRepository}.
 */
@DisplayName("JdbcCostRepository Tests [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
class JdbcCostRepositoryTest extends EventloopTestBase {

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement preparedStatement;

    private JdbcCostRepository repository;

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        repository = new JdbcCostRepository(dataSource, eventloop()); // GH-90000
        lenient().when(dataSource.getConnection()).thenReturn(connection); // GH-90000
        lenient().when(connection.prepareStatement(anyString())).thenReturn(preparedStatement); // GH-90000
        lenient().when(preparedStatement.executeUpdate()).thenReturn(1); // GH-90000
    }

    @Test
    @DisplayName("save persists event with all fields to the database [GH-90000]")
    void save_persistsEvent_withAllFields() { // GH-90000
        CostEvent event = buildEvent("tenant-1", "user-A", "gpt-4", "openai", 100, 50, 0.0045); // GH-90000

        assertThatCode(() -> runPromise(() -> repository.save(event))) // GH-90000
            .doesNotThrowAnyException(); // GH-90000

        try {
            verify(preparedStatement).setString(1, event.id()); // GH-90000
            verify(preparedStatement).setString(2, event.callId()); // GH-90000
            verify(preparedStatement).setString(3, event.tenantId()); // GH-90000
            verify(preparedStatement).setString(4, event.userId()); // GH-90000
            verify(preparedStatement).setString(5, event.model()); // GH-90000
            verify(preparedStatement).setString(6, event.provider()); // GH-90000
            verify(preparedStatement).setString(7, event.featureId()); // GH-90000
            verify(preparedStatement).setInt(8, event.tokensInput()); // GH-90000
            verify(preparedStatement).setInt(9, event.tokensOutput()); // GH-90000
            verify(preparedStatement).setBigDecimal( // GH-90000
                eq(10), eq(java.math.BigDecimal.valueOf(event.costUsd()))); // GH-90000
            verify(preparedStatement).setTimestamp( // GH-90000
                eq(11), eq(java.sql.Timestamp.from(event.occurredAt()))); // GH-90000
            verify(preparedStatement).executeUpdate(); // GH-90000
        } catch (Exception ex) { // GH-90000
            throw new AssertionError("Unexpected exception during verification", ex); // GH-90000
        }
    }

    @Test
    @DisplayName("save returns completed promise on success [GH-90000]")
    void save_returnsCompletedPromise_onSuccess() { // GH-90000
        CostEvent event = buildEvent("t1", "u1", "claude-3-sonnet", "anthropic", 200, 80, 0.002); // GH-90000

        assertThatCode(() -> runPromise(() -> repository.save(event))) // GH-90000
            .doesNotThrowAnyException(); // GH-90000
    }

    @Test
    @DisplayName("save propagates database exception as Promise failure [GH-90000]")
    void save_propagatesException_onDatabaseError() throws Exception { // GH-90000
        when(preparedStatement.executeUpdate()).thenThrow(new java.sql.SQLException("DB unavailable [GH-90000]"));

        CostEvent event = buildEvent("t1", "u1", "gpt-4", "openai", 10, 10, 0.001); // GH-90000

        try {
            runPromise(() -> repository.save(event)); // GH-90000
            throw new AssertionError("Expected exception was not thrown [GH-90000]");
        } catch (Exception ex) { // GH-90000
            // Expected: Promise failure propagated
        }
    }

    @Test
    @DisplayName("save handles null userId and null featureId [GH-90000]")
    void save_handlesNullOptionalFields() { // GH-90000
        CostEvent event = new CostEvent( // GH-90000
            UUID.randomUUID().toString(), // GH-90000
            UUID.randomUUID().toString(), // GH-90000
            "tenant-2",
            null,          // userId
            "gpt-3.5-turbo",
            "openai",
            null,          // featureId
            50, 30, 0.0002,
            Instant.now() // GH-90000
        );

        assertThatCode(() -> runPromise(() -> repository.save(event))) // GH-90000
            .doesNotThrowAnyException(); // GH-90000
    }

    @Test
    @DisplayName("save opens fresh connection per call [GH-90000]")
    void save_opensFreshConnection_perCall() throws Exception { // GH-90000
        CostEvent event1 = buildEvent("t1", "u1", "gpt-4", "openai", 100, 50, 0.005); // GH-90000
        CostEvent event2 = buildEvent("t2", "u2", "gpt-4", "openai", 200, 100, 0.01); // GH-90000

        runPromise(() -> repository.save(event1)); // GH-90000
        runPromise(() -> repository.save(event2)); // GH-90000

        verify(dataSource, times(2)).getConnection(); // GH-90000
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static CostEvent buildEvent(String tenantId, String userId, String model, // GH-90000
                                         String provider, int tokensIn, int tokensOut,
                                         double cost) {
        return new CostEvent( // GH-90000
            UUID.randomUUID().toString(), // GH-90000
            UUID.randomUUID().toString(), // GH-90000
            tenantId,
            userId,
            model,
            provider,
            "code-generation",
            tokensIn,
            tokensOut,
            cost,
            Instant.now() // GH-90000
        );
    }
}
