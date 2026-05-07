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
@Tag("integration")
@Testcontainers(disabledWithoutDocker = true) 
@DisplayName("RunLedgerBackedHistoryIntegrationTest")
class RunLedgerBackedHistoryIntegrationTest extends EventloopTestBase {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("aep_orchestrator")
            .withUsername("aep")
            .withPassword("aep");

    @Test
    @DisplayName("append persists an execution record that getHistory can read back")
    void appendPersistsExecutionHistory() throws Exception { 
        HikariConfig config = new HikariConfig(); 
        config.setJdbcUrl(POSTGRES.getJdbcUrl()); 
        config.setUsername(POSTGRES.getUsername()); 
        config.setPassword(POSTGRES.getPassword()); 

        try (HikariDataSource dataSource = new HikariDataSource(config); 
             Connection connection = dataSource.getConnection(); 
             Statement statement = connection.createStatement()) { 
            statement.execute("""
                CREATE TABLE IF NOT EXISTS agent_execution_history (
                    execution_id TEXT PRIMARY KEY,
                    agent_id TEXT NOT NULL,
                    status TEXT NOT NULL,
                    input_payload JSONB,
                    output_payload JSONB,
                    duration_ms BIGINT NOT NULL,
                    executed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                )
                """);

            Executor executor = Runnable::run;
            RunLedgerBackedHistory history = new RunLedgerBackedHistory(dataSource, executor); 
            AgentExecutionService.ExecutionRecord record = new AgentExecutionService.ExecutionRecord( 
                "exec-1",
                "success",
                Map.of("message", "hello"), 
                Map.of("result", "ok"), 
                15L,
                Instant.parse("2026-04-15T12:00:00Z").toString());

            runPromise(() -> history.append("agent-1", record)); 
            List<AgentExecutionService.ExecutionRecord> stored = runPromise(() -> history.getHistory("agent-1", 10)); 

            assertThat(stored).hasSize(1); 
            assertThat(stored.get(0).executionId()).isEqualTo("exec-1");
            assertThat(stored.get(0).input()).isEqualTo(Map.of("message", "hello")); 
            assertThat(stored.get(0).output()).isEqualTo(Map.of("result", "ok")); 
        }
    }
}
