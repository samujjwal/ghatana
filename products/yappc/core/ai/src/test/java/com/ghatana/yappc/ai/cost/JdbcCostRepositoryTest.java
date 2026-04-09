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
@DisplayName("JdbcCostRepository Tests")
@ExtendWith(MockitoExtension.class)
class JdbcCostRepositoryTest extends EventloopTestBase {

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement preparedStatement;

    private JdbcCostRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        repository = new JdbcCostRepository(dataSource, eventloop());
        lenient().when(dataSource.getConnection()).thenReturn(connection);
        lenient().when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        lenient().when(preparedStatement.executeUpdate()).thenReturn(1);
    }

    @Test
    @DisplayName("save persists event with all fields to the database")
    void save_persistsEvent_withAllFields() {
        CostEvent event = buildEvent("tenant-1", "user-A", "gpt-4", "openai", 100, 50, 0.0045);

        assertThatCode(() -> runPromise(() -> repository.save(event)))
            .doesNotThrowAnyException();

        try {
            verify(preparedStatement).setString(1, event.id());
            verify(preparedStatement).setString(2, event.callId());
            verify(preparedStatement).setString(3, event.tenantId());
            verify(preparedStatement).setString(4, event.userId());
            verify(preparedStatement).setString(5, event.model());
            verify(preparedStatement).setString(6, event.provider());
            verify(preparedStatement).setString(7, event.featureId());
            verify(preparedStatement).setInt(8, event.tokensInput());
            verify(preparedStatement).setInt(9, event.tokensOutput());
            verify(preparedStatement).setBigDecimal(
                eq(10), eq(java.math.BigDecimal.valueOf(event.costUsd())));
            verify(preparedStatement).setTimestamp(
                eq(11), eq(java.sql.Timestamp.from(event.occurredAt())));
            verify(preparedStatement).executeUpdate();
        } catch (Exception ex) {
            throw new AssertionError("Unexpected exception during verification", ex);
        }
    }

    @Test
    @DisplayName("save returns completed promise on success")
    void save_returnsCompletedPromise_onSuccess() {
        CostEvent event = buildEvent("t1", "u1", "claude-3-sonnet", "anthropic", 200, 80, 0.002);

        assertThatCode(() -> runPromise(() -> repository.save(event)))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("save propagates database exception as Promise failure")
    void save_propagatesException_onDatabaseError() throws Exception {
        when(preparedStatement.executeUpdate()).thenThrow(new java.sql.SQLException("DB unavailable"));

        CostEvent event = buildEvent("t1", "u1", "gpt-4", "openai", 10, 10, 0.001);

        try {
            runPromise(() -> repository.save(event));
            throw new AssertionError("Expected exception was not thrown");
        } catch (Exception ex) {
            // Expected: Promise failure propagated
        }
    }

    @Test
    @DisplayName("save handles null userId and null featureId")
    void save_handlesNullOptionalFields() {
        CostEvent event = new CostEvent(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            "tenant-2",
            null,          // userId
            "gpt-3.5-turbo",
            "openai",
            null,          // featureId
            50, 30, 0.0002,
            Instant.now()
        );

        assertThatCode(() -> runPromise(() -> repository.save(event)))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("save opens fresh connection per call")
    void save_opensFreshConnection_perCall() throws Exception {
        CostEvent event1 = buildEvent("t1", "u1", "gpt-4", "openai", 100, 50, 0.005);
        CostEvent event2 = buildEvent("t2", "u2", "gpt-4", "openai", 200, 100, 0.01);

        runPromise(() -> repository.save(event1));
        runPromise(() -> repository.save(event2));

        verify(dataSource, times(2)).getConnection();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static CostEvent buildEvent(String tenantId, String userId, String model,
                                         String provider, int tokensIn, int tokensOut,
                                         double cost) {
        return new CostEvent(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            tenantId,
            userId,
            model,
            provider,
            "code-generation",
            tokensIn,
            tokensOut,
            cost,
            Instant.now()
        );
    }
}
