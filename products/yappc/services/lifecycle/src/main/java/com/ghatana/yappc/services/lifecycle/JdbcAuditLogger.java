/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Lifecycle Service — Durable JDBC Audit Logger
 */
package com.ghatana.yappc.services.lifecycle;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.audit.AuditLogger;
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
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * JDBC-backed {@link AuditLogger} for YAPPC Lifecycle Service phase audit events.
 *
 * <p>All writes are dispatched on a dedicated virtual-thread executor via
 * {@code Promise.ofBlocking} so the ActiveJ event-loop thread is never blocked.
 *
 * <p>Schema: {@code yappc.lifecycle_audit_events} — created by Flyway migration
 * {@code V18__lifecycle_audit_events.sql}.
 *
 * <p>Each audit event is persisted with:
 * <ul>
 *   <li>{@code id} — random UUID (idempotency key via ON CONFLICT DO NOTHING)</li>
 *   <li>{@code tenant_id} — from event map or "default"</li>
 *   <li>{@code event_type} — from event map key "type"</li>
 *   <li>{@code occurred_at} — current UTC timestamp</li>
 *   <li>{@code payload} — full event map as JSONB</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Durable JDBC-backed audit logger for lifecycle phase events
 * @doc.layer product
 * @doc.pattern Repository, Adapter
 * @doc.gaa.lifecycle capture
 */
public class JdbcAuditLogger implements AuditLogger {

    private static final Logger log = LoggerFactory.getLogger(JdbcAuditLogger.class);

    private static final Executor EXECUTOR =
            Executors.newVirtualThreadPerTaskExecutor();

    private static final String INSERT_SQL = """
            INSERT INTO lifecycle_audit_events
              (id, tenant_id, event_type, occurred_at, payload)
            VALUES (?, ?, ?, ?, ?::jsonb)
            ON CONFLICT (id) DO NOTHING
            """;

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    /**
     * Constructs a {@code JdbcAuditLogger}.
     *
     * @param dataSource   YAPPC PostgreSQL data source
     * @param objectMapper Jackson mapper for JSONB serialization
     */
    public JdbcAuditLogger(DataSource dataSource, ObjectMapper objectMapper) {
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
    }

    /**
     * Persists the audit event asynchronously.
     *
     * @param event audit event fields; must contain at least {@code "type"}
     * @return {@link Promise} that completes when the event is persisted,
     *         or resolved normally even on {@link SQLException} (error is logged)
     */
    @Override
    public Promise<Void> log(Map<String, Object> event) {
        return Promise.ofBlocking(EXECUTOR, () -> {
            String id       = UUID.randomUUID().toString();
            String tenantId = extractString(event, "tenant_id", "default");
            String type     = extractString(event, "type", "UNKNOWN");
            String payload  = toJson(event);

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
                ps.setString(1, id);
                ps.setString(2, tenantId);
                ps.setString(3, type);
                ps.setTimestamp(4, Timestamp.from(Instant.now()));
                ps.setString(5, payload);
                ps.executeUpdate();
                log.debug("Audit event persisted: id={} type={} tenant={}", id, type, tenantId);
            } catch (SQLException e) {
                // Log and continue — audit failures must never block lifecycle operations
                log.error("Failed to persist audit event (type={} tenant={}): {}",
                        type, tenantId, e.getMessage());
            }
        });
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private String extractString(Map<String, Object> map, String key, String fallback) {
        Object value = map.get(key);
        return (value instanceof String s && !s.isBlank()) ? s : fallback;
    }

    private String toJson(Map<String, Object> event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize audit event to JSON; storing empty object: {}", e.getMessage());
            return "{}";
        }
    }
}
