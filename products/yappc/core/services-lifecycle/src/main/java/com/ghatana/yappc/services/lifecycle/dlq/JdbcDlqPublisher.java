/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Lifecycle Services
 */
package com.ghatana.yappc.services.lifecycle.dlq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * JDBC-backed production implementation of {@link DlqPublisher}.
 *
 * <p>Inserts failed pipeline events into the {@code yappc_dlq} PostgreSQL table so they
 * can be inspected, retried, or alarmed on by operations teams.
 *
 * <p>All JDBC calls run on the supplied {@link Executor} (never the ActiveJ event loop)
 * via {@link Promise#ofBlocking(Executor, java.util.concurrent.Callable)}.
 *
 * <h2>Schema (see {@code V14__yappc_dlq.sql})</h2>
 * <pre>{@code
 * CREATE TABLE IF NOT EXISTS yappc_dlq (
 *   id              UUID         DEFAULT gen_random_uuid() PRIMARY KEY,
 *   tenant_id       TEXT         NOT NULL,
 *   pipeline_id     TEXT         NOT NULL,
 *   node_id         TEXT         NOT NULL,
 *   event_type      TEXT         NOT NULL,
 *   payload         JSONB        NOT NULL,
 *   failure_reason  TEXT         NOT NULL,
 *   correlation_id  TEXT,
 *   created_at      TIMESTAMPTZ  DEFAULT now()
 * );
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose JDBC-backed DLQ publisher for failed YAPPC pipeline events
 * @doc.layer product
 * @doc.pattern Repository
 */
public class JdbcDlqPublisher implements DlqPublisher {

    private static final Logger log = LoggerFactory.getLogger(JdbcDlqPublisher.class);

    private static final String INSERT_SQL = """
            INSERT INTO yappc_dlq
              (id, tenant_id, pipeline_id, node_id, event_type, event_payload, failure_reason, correlation_id, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?)
            """;

    private final DataSource dataSource;
    private final Executor   executor;
    private final ObjectMapper objectMapper;

    /**
     * @param dataSource   YAPPC PostgreSQL data source
     * @param executor     off-event-loop executor for blocking JDBC calls
     * @param objectMapper JSON serialiser for the event payload
     */
    public JdbcDlqPublisher(
            @NotNull DataSource   dataSource,
            @NotNull Executor     executor,
            @NotNull ObjectMapper objectMapper) {
        this.dataSource   = Objects.requireNonNull(dataSource,   "dataSource");
        this.executor     = Objects.requireNonNull(executor,     "executor");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public Promise<Void> publish(
            @NotNull String              tenantId,
            @NotNull String              pipelineId,
            @NotNull String              nodeId,
            @NotNull String              eventType,
            @NotNull Map<String, Object> eventPayload,
            @NotNull String              failureReason,
            @Nullable String             correlationId) {

        return Promise.ofBlocking(executor, () -> {
            String payloadJson = toJson(eventPayload);
            insertDlqRow(tenantId, pipelineId, nodeId, eventType, payloadJson, failureReason, correlationId);
            return null;
        });
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void insertDlqRow(
            String tenantId,
            String pipelineId,
            String nodeId,
            String eventType,
            String payloadJson,
            String failureReason,
            @Nullable String correlationId) throws SQLException {

        Timestamp now = Timestamp.from(Instant.now());
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {

            ps.setObject(1, UUID.randomUUID());
            ps.setString(2, tenantId);
            ps.setString(3, pipelineId);
            ps.setString(4, nodeId);
            ps.setString(5, eventType);
            ps.setString(6, payloadJson);
            ps.setString(7, failureReason);
            ps.setString(8, correlationId);
            ps.setTimestamp(9, now);
            ps.setTimestamp(10, now);

            int rows = ps.executeUpdate();
            log.debug("DLQ row inserted: tenant={} pipeline={} node={} eventType={} rows={}",
                    tenantId, pipelineId, nodeId, eventType, rows);
        } catch (SQLException e) {
            log.error("Failed to insert DLQ row: tenant={} pipeline={} node={} eventType={}: {}",
                    tenantId, pipelineId, nodeId, eventType, e.getMessage(), e);
            throw e;
        }
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.warn("Could not serialise DLQ payload to JSON, using empty object: {}", e.getMessage());
            return "{}";
        }
    }
}
