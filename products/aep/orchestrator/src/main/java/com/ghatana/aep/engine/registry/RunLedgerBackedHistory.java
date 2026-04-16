package com.ghatana.aep.engine.registry;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * JDBC-backed execution history store for agent runs.
 *
 * <p>The name reflects the production-readiness plan terminology. In the
 * current codebase this implementation persists execution records in a durable
 * relational ledger table so {@code getHistory()} survives process restarts.
 *
 * @doc.type class
 * @doc.purpose Durable JDBC-backed execution history store for agent runs
 * @doc.layer product
 * @doc.pattern Repository
 */
public final class RunLedgerBackedHistory implements AgentExecutionHistoryStore {

    private static final ObjectMapper MAPPER = JsonUtils.getDefaultMapper();
    private static final TypeReference<Object> OBJECT_TYPE = new TypeReference<>() {};

    private final DataSource dataSource;
    private final Executor executor;

    public RunLedgerBackedHistory(@NotNull DataSource dataSource, @NotNull Executor executor) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    @Override
    public Promise<Void> append(String agentId, AgentExecutionService.ExecutionRecord record) {
        Objects.requireNonNull(agentId, "agentId");
        Objects.requireNonNull(record, "record");

        return Promise.ofBlocking(executor, () -> {
            String sql = """
                INSERT INTO agent_execution_history (
                    execution_id, agent_id, status, input_payload, output_payload, duration_ms, executed_at
                ) VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?, ?)
                """;

            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, record.executionId());
                statement.setString(2, agentId);
                statement.setString(3, record.status());
                statement.setString(4, MAPPER.writeValueAsString(record.input()));
                statement.setString(5, MAPPER.writeValueAsString(record.output()));
                statement.setLong(6, record.durationMs());
                statement.setTimestamp(7, Timestamp.from(Instant.parse(record.timestamp())));
                statement.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public Promise<List<AgentExecutionService.ExecutionRecord>> getHistory(String agentId, int limit) {
        Objects.requireNonNull(agentId, "agentId");
        int sanitizedLimit = Math.max(1, Math.min(limit, 1000));

        return Promise.ofBlocking(executor, () -> {
            String sql = """
                SELECT execution_id, status, input_payload, output_payload, duration_ms, executed_at
                  FROM agent_execution_history
                 WHERE agent_id = ?
                 ORDER BY executed_at DESC
                 LIMIT ?
                """;

            List<AgentExecutionService.ExecutionRecord> records = new ArrayList<>();
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, agentId);
                statement.setInt(2, sanitizedLimit);

                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        records.add(new AgentExecutionService.ExecutionRecord(
                            resultSet.getString("execution_id"),
                            resultSet.getString("status"),
                            parseJson(resultSet.getString("input_payload")),
                            parseJson(resultSet.getString("output_payload")),
                            resultSet.getLong("duration_ms"),
                            resultSet.getTimestamp("executed_at").toInstant().toString()));
                    }
                }
            }
            return records;
        });
    }

    private Object parseJson(String json) throws Exception {
        if (json == null || json.isBlank()) {
            return null;
        }
        return MAPPER.readValue(json, OBJECT_TYPE);
    }
}
