/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.workflow.jdbc;

import com.ghatana.platform.workflow.WorkflowWaitCoordinator;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * PostgreSQL-backed implementation of {@link WorkflowWaitCoordinator}.
 *
 * <p>Persists wait conditions to the {@code workflow_wait_conditions} table.
 * Timer-based waits are resolved by polling {@link #findFirableWaits(Instant)};
 * event-based waits are resolved by {@link #signal(String, String, Map)}.
 *
 * @doc.type class
 * @doc.purpose PostgreSQL persistence for workflow wait conditions
 * @doc.layer platform
 * @doc.pattern Repository
 */
public final class JdbcWorkflowWaitCoordinator implements WorkflowWaitCoordinator {
    private static final Logger log = LoggerFactory.getLogger(JdbcWorkflowWaitCoordinator.class);

    private static final String INSERT_WAIT = """
        INSERT INTO workflow_wait_conditions
            (run_id, wait_kind, event_type, correlation_key, fire_at, fired)
        VALUES (?, ?, ?, ?, ?, FALSE)
        """;

    private static final String SIGNAL_BY_EVENT = """
        UPDATE workflow_wait_conditions
        SET fired = TRUE
        WHERE run_id = ? AND event_type = ? AND fired = FALSE
        RETURNING run_id
        """;

    private static final String FIND_FIRABLE = """
        SELECT run_id
        FROM workflow_wait_conditions
        WHERE fired = FALSE AND fire_at <= ?
        """;

    private static final String CANCEL_WAITS = """
        DELETE FROM workflow_wait_conditions
        WHERE run_id = ? AND fired = FALSE
        """;

    private final DataSource dataSource;
    private final ExecutorService executor;

    public JdbcWorkflowWaitCoordinator(@NotNull DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @Override
    public Promise<Void> registerWait(@NotNull String runId, @NotNull WaitCondition condition) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(INSERT_WAIT)) {
                ps.setString(1, runId);
                ps.setString(2, condition.kind().name());
                ps.setString(3, condition.eventType());
                ps.setString(4, condition.correlationKey());
                ps.setTimestamp(5, condition.fireAt() != null ? Timestamp.from(condition.fireAt()) : null);
                ps.executeUpdate();
            }
        });
    }

    @Override
    public Promise<Boolean> signal(
            @NotNull String runId,
            @NotNull String signalName,
            @Nullable Map<String, Object> payload) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SIGNAL_BY_EVENT)) {
                ps.setString(1, runId);
                ps.setString(2, signalName);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next(); // true if a row was updated
                }
            }
        });
    }

    @Override
    public Promise<List<String>> findFirableWaits(@NotNull Instant now) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(FIND_FIRABLE)) {
                ps.setTimestamp(1, Timestamp.from(now));
                try (ResultSet rs = ps.executeQuery()) {
                    List<String> runIds = new ArrayList<>();
                    while (rs.next()) {
                        runIds.add(rs.getString("run_id"));
                    }
                    return runIds;
                }
            }
        });
    }

    @Override
    public void cancel(@NotNull String runId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(CANCEL_WAITS)) {
            ps.setString(1, runId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to cancel waits for run {}: {}", runId, e.getMessage());
        }
    }
}
