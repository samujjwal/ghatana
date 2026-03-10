/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.dlq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.services.lifecycle.dlq.DlqPublisher;
import io.activej.promise.Promise;
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
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * JDBC implementation of {@link DlqPublisher} that persists failed pipeline events
 * to the {@code yappc_dlq} PostgreSQL table.
 *
 * <p>Each {@link #publish} call inserts a new row with status {@code PENDING}.
 * Rows age-out or are managed via the DLQ management API.
 *
 * <p>Schema: see {@code V19__dlq.sql}.
 *
 * @doc.type class
 * @doc.purpose JDBC-backed DLQ publisher for failed AEP pipeline events
 * @doc.layer product
 * @doc.pattern Repository
 */
public class JdbcDlqPublisher implements DlqPublisher {

    private static final Logger log = LoggerFactory.getLogger(JdbcDlqPublisher.class);

    private static final String INSERT_SQL = """
            INSERT INTO yappc_dlq
              (tenant_id, pipeline_id, node_id, event_type, event_payload,
               failure_reason, correlation_id, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?)
            """;

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    private final Executor executor;

    /**
     * Creates a new {@code JdbcDlqPublisher}.
     *
     * @param dataSource   the database connection pool
     * @param objectMapper Jackson mapper for serializing event payloads
     */
    public JdbcDlqPublisher(DataSource dataSource, ObjectMapper objectMapper) {
        this.dataSource   = Objects.requireNonNull(dataSource, "dataSource");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.executor     = Executors.newVirtualThreadPerTaskExecutor();
    }

    @Override
    public Promise<Void> publish(
            String tenantId,
            String pipelineId,
            String nodeId,
            String eventType,
            Map<String, Object> eventPayload,
            String failureReason,
            String correlationId) {

        return Promise.ofBlocking(executor, () -> {
            String payloadJson;
            try {
                payloadJson = objectMapper.writeValueAsString(
                        eventPayload != null ? eventPayload : Map.of());
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize DLQ event payload — storing empty object. Error: {}", e.getMessage());
                payloadJson = "{}";
            }

            Timestamp now = Timestamp.from(Instant.now());

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
                ps.setString(1, tenantId);
                ps.setString(2, pipelineId);
                ps.setString(3, nodeId);
                ps.setString(4, eventType);
                ps.setString(5, payloadJson);
                ps.setString(6, failureReason);
                ps.setString(7, correlationId);
                ps.setTimestamp(8, now);
                ps.setTimestamp(9, now);
                ps.executeUpdate();

                log.info("[DLQ] Published failed event: pipeline={} node={} eventType={} tenant={} reason={}",
                        pipelineId, nodeId, eventType, tenantId, failureReason);
            } catch (SQLException e) {
                log.error("[DLQ] Failed to persist DLQ entry: pipeline={} node={} eventType={}",
                        pipelineId, nodeId, eventType, e);
                throw new RuntimeException("DLQ persistence failure", e);
            }

            return null;
        });
    }
}
