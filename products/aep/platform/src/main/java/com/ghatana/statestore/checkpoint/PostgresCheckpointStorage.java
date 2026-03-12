/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.statestore.checkpoint;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * PostgreSQL-backed implementation of {@link CheckpointStorage}.
 *
 * <p>Replaces {@link InMemoryCheckpointStorage} for production workloads where
 * checkpoint metadata must survive process restarts. All JDBC calls are wrapped
 * with {@code Promise.ofBlocking} to keep the ActiveJ event-loop non-blocking.
 *
 * <p>Schema managed by {@code V007__create_aep_checkpoints.sql} (Flyway).
 *
 * @doc.type class
 * @doc.purpose Durable PostgreSQL checkpoint storage for AEP fault recovery
 * @doc.layer product
 * @doc.pattern Repository
 */
public class PostgresCheckpointStorage implements CheckpointStorage {

    private static final Logger log = LoggerFactory.getLogger(PostgresCheckpointStorage.class);

    private static final String INSERT_OR_UPDATE =
            "INSERT INTO aep_checkpoints (id, type, status, start_time, complete_time, failure_reason, operator_acks) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?::jsonb) "
            + "ON CONFLICT (id) DO UPDATE SET "
            + "  status         = EXCLUDED.status, "
            + "  complete_time  = EXCLUDED.complete_time, "
            + "  failure_reason = EXCLUDED.failure_reason, "
            + "  operator_acks  = EXCLUDED.operator_acks";

    private static final String SELECT_BY_ID =
            "SELECT id, type, status, start_time, complete_time, failure_reason, operator_acks "
            + "FROM aep_checkpoints WHERE id = ?";

    private static final String DELETE_BY_ID =
            "DELETE FROM aep_checkpoints WHERE id = ? AND type = 'CHECKPOINT'";

    private final DataSource dataSource;
    private final Executor executor;
    private final ObjectMapper mapper;

    /**
     * Creates a new PostgresCheckpointStorage using a virtual-thread executor.
     *
     * @param dataSource JDBC data source for the AEP database
     */
    public PostgresCheckpointStorage(DataSource dataSource) {
        this(dataSource, Executors.newVirtualThreadPerTaskExecutor());
    }

    /**
     * Creates a new PostgresCheckpointStorage with a custom executor.
     *
     * @param dataSource JDBC data source
     * @param executor   executor used to wrap blocking JDBC calls off the event loop
     */
    public PostgresCheckpointStorage(DataSource dataSource, Executor executor) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource cannot be null");
        this.executor   = Objects.requireNonNull(executor,   "executor cannot be null");
        this.mapper     = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CheckpointStorage implementation
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public Promise<CheckpointMetadata> saveCheckpoint(CheckpointMetadata metadata) {
        Objects.requireNonNull(metadata, "metadata cannot be null");
        return Promise.ofBlocking(executor, () -> doSave(metadata));
    }

    @Override
    public Promise<CheckpointMetadata> saveSavepoint(CheckpointMetadata metadata) {
        Objects.requireNonNull(metadata, "metadata cannot be null");
        return Promise.ofBlocking(executor, () -> doSave(metadata));
    }

    @Override
    public Promise<CheckpointMetadata> loadCheckpoint(CheckpointId checkpointId) {
        Objects.requireNonNull(checkpointId, "checkpointId cannot be null");
        return Promise.ofBlocking(executor, () -> doLoad(checkpointId));
    }

    @Override
    public Promise<Void> deleteCheckpoint(CheckpointId checkpointId) {
        Objects.requireNonNull(checkpointId, "checkpointId cannot be null");
        return Promise.ofBlocking(executor, () -> {
            doDelete(checkpointId);
            return null;
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private JDBC helpers
    // ─────────────────────────────────────────────────────────────────────────

    private CheckpointMetadata doSave(CheckpointMetadata m) throws Exception {
        String acksJson = mapper.writeValueAsString(m.getOperatorAcks());
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_OR_UPDATE)) {
            ps.setString(1, m.getCheckpointId().getId());
            ps.setString(2, m.getCheckpointId().getType().name());
            ps.setString(3, m.getStatus().name());
            ps.setTimestamp(4, Timestamp.from(m.getStartTime()));
            ps.setTimestamp(5, m.getCompleteTime() != null
                    ? Timestamp.from(m.getCompleteTime()) : null);
            ps.setString(6, m.getFailureReason());
            ps.setString(7, acksJson);
            ps.executeUpdate();
        }
        log.debug("Saved checkpoint id={} type={} status={}",
                m.getCheckpointId().getId(), m.getCheckpointId().getType(), m.getStatus());
        return m;
    }

    private CheckpointMetadata doLoad(CheckpointId checkpointId) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_BY_ID)) {
            ps.setString(1, checkpointId.getId());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return rowToMetadata(rs);
            }
        }
    }

    private void doDelete(CheckpointId checkpointId) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(DELETE_BY_ID)) {
            ps.setString(1, checkpointId.getId());
            int rows = ps.executeUpdate();
            log.debug("Deleted checkpoint id={} rows={}", checkpointId.getId(), rows);
        }
    }

    private CheckpointMetadata rowToMetadata(ResultSet rs) throws Exception {
        String id         = rs.getString("id");
        CheckpointType type =
                CheckpointType.valueOf(rs.getString("type"));
        CheckpointStatus status =
                CheckpointStatus.valueOf(rs.getString("status"));
        Instant startTime    = rs.getTimestamp("start_time").toInstant();
        Timestamp completeTs = rs.getTimestamp("complete_time");
        Instant completeTime = completeTs != null ? completeTs.toInstant() : null;
        String failureReason = rs.getString("failure_reason");
        String acksJson      = rs.getString("operator_acks");

        Map<String, CheckpointMetadata.OperatorCheckpointInfo> acks =
                acksJson != null && !acksJson.equals("{}")
                        ? mapper.readValue(acksJson,
                                new TypeReference<Map<String, CheckpointMetadata.OperatorCheckpointInfo>>() {})
                        : Collections.emptyMap();

        return CheckpointMetadata.builder(CheckpointId.restore(id, type, startTime))
                .status(status)
                .startTime(startTime)
                .completeTime(completeTime)
                .failureReason(failureReason)
                .operatorAcks(acks)
                .build();
    }
}
