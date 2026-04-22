package com.ghatana.aep.engine.registry;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Postgres integration tests for {@link RunLedgerBackedHistory}.
 *
 * @doc.type class
 * @doc.purpose Verify execution history survives round-trip persistence in PostgreSQL
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@Tag("integration [GH-90000]")
@Testcontainers(disabledWithoutDocker = true) // GH-90000
@DisplayName("RunLedgerBackedHistoryIntegrationTest [GH-90000]")
class RunLedgerBackedHistoryIntegrationTest extends EventloopTestBase {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:15-alpine [GH-90000]")
            .withDatabaseName("aep_orchestrator [GH-90000]")
            .withUsername("aep [GH-90000]")
            .withPassword("aep [GH-90000]");

    @Test
    @DisplayName("append persists an execution record that getHistory can read back [GH-90000]")
    void appendPersistsExecutionHistory() throws Exception { // GH-90000
        HikariConfig config = new HikariConfig(); // GH-90000
        config.setJdbcUrl(POSTGRES.getJdbcUrl()); // GH-90000
        config.setUsername(POSTGRES.getUsername()); // GH-90000
        config.setPassword(POSTGRES.getPassword()); // GH-90000

        try (HikariDataSource dataSource = new HikariDataSource(config); // GH-90000
             Connection connection = dataSource.getConnection(); // GH-90000
             Statement statement = connection.createStatement()) { // GH-90000
            statement.execute("""
                CREATE TABLE IF NOT EXISTS agent_execution_history ( // GH-90000
                    execution_id TEXT PRIMARY KEY,
                    agent_id TEXT NOT NULL,
                    status TEXT NOT NULL,
                    input_payload JSONB,
                    output_payload JSONB,
                    duration_ms BIGINT NOT NULL,
                    executed_at TIMESTAMPTZ NOT NULL DEFAULT NOW() // GH-90000
                )
                """);

            Executor executor = Runnable::run;
            RunLedgerBackedHistory history = new RunLedgerBackedHistory(dataSource, executor); // GH-90000
            AgentExecutionService.ExecutionRecord record = new AgentExecutionService.ExecutionRecord( // GH-90000
                "exec-1",
                "success",
                Map.of("message", "hello"), // GH-90000
                Map.of("result", "ok"), // GH-90000
                15L,
                Instant.parse("2026-04-15T12:00:00Z [GH-90000]").toString());

            runPromise(() -> history.append("agent-1", record)); // GH-90000
            List<AgentExecutionService.ExecutionRecord> stored = runPromise(() -> history.getHistory("agent-1", 10)); // GH-90000

            assertThat(stored).hasSize(1); // GH-90000
            assertThat(stored.get(0).executionId()).isEqualTo("exec-1 [GH-90000]");
            assertThat(stored.get(0).input()).isEqualTo(Map.of("message", "hello")); // GH-90000
            assertThat(stored.get(0).output()).isEqualTo(Map.of("result", "ok")); // GH-90000
        }
    }
}
