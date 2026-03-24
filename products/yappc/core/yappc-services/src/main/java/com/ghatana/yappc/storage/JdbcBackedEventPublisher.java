/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Lifecycle — JDBC-backed Durable Event Publisher
 */
package com.ghatana.yappc.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.agent.AepEventPublisher;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * JDBC-backed durable implementation of {@link AepEventPublisher}.
 *
 * <p>Writes each event to {@code yappc.event_outbox} with status {@code PENDING},
 * enabling an outbox-pattern relay to downstream AEP consumers. Events survive
 * service restarts unlike the in-memory predecessor.
 *
 * <p>Schema (expected):
 * <pre>
 * CREATE TABLE IF NOT EXISTS yappc.event_outbox (
 *   id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 *   event_type  TEXT        NOT NULL,
 *   tenant_id   TEXT        NOT NULL,
 *   payload     JSONB       NOT NULL,
 *   status      TEXT        NOT NULL DEFAULT 'PENDING',
 *   occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 *   dispatched_at TIMESTAMPTZ
 * );
 * </pre>
 *
 * <p><b>Thread-safety</b>: Uses a dedicated virtual-thread executor for all JDBC
 * operations to avoid blocking the ActiveJ event loop.
 *
 * @doc.type class
 * @doc.purpose Durable JDBC-backed event publisher replacing InMemoryEventPublisher
 * @doc.layer infrastructure
 * @doc.pattern Outbox / Repository
 */
public class JdbcBackedEventPublisher implements AepEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(JdbcBackedEventPublisher.class);

    private static final String INSERT_OUTBOX =
            "INSERT INTO yappc.event_outbox " +
            "(id, event_type, tenant_id, payload, status, occurred_at) " +
            "VALUES (?, ?, ?, ?::jsonb, 'PENDING', ?)";

    private final DataSource dataSource;
    private final ObjectMapper mapper;
    private final Executor executor;

    /**
     * Constructs a {@code JdbcBackedEventPublisher}.
     *
     * @param dataSource the JDBC DataSource pointing to the YAPPC database
     * @param mapper     Jackson ObjectMapper used to serialize event payloads
     */
    public JdbcBackedEventPublisher(DataSource dataSource, ObjectMapper mapper) {
        this.dataSource = dataSource;
        this.mapper     = mapper;
        this.executor   = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Serialises {@code payload} to JSON and inserts a {@code PENDING} row into
     * {@code yappc.event_outbox}. The call is dispatched to a virtual thread so the
     * ActiveJ event loop is never blocked.
     */
    @Override
    public Promise<Void> publish(String eventType, String tenantId, Map<String, Object> payload) {
        return Promise.ofBlocking(executor, () -> {
            String payloadJson = serializePayload(payload);
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(INSERT_OUTBOX)) {

                ps.setString(1, UUID.randomUUID().toString());
                ps.setString(2, eventType);
                ps.setString(3, tenantId);
                ps.setString(4, payloadJson);
                ps.setTimestamp(5, Timestamp.from(Instant.now()));
                ps.executeUpdate();

                log.info("Published event: type={} tenant={}", eventType, tenantId);
            } catch (Exception e) {
                log.error("Failed to persist event type={} tenant={}", eventType, tenantId, e);
                throw new RuntimeException("Event persistence failed: " + eventType, e);
            }
            return null;
        });
    }

    private String serializePayload(Map<String, Object> payload) {
        try {
            return mapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.warn("Could not serialize event payload; using empty object", e);
            return "{}";
        }
    }
}
