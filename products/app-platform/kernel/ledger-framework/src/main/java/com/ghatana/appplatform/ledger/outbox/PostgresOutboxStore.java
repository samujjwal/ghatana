/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.ledger.outbox;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

import io.activej.promise.Promise;

/**
 * PostgreSQL-backed implementation of {@link OutboxPort} (K17-001).
 *
 * <p>Uses {@code SELECT FOR UPDATE SKIP LOCKED} to safely support concurrent
 * relay instances running on multiple application nodes.
 *
 * @doc.type class
 * @doc.purpose PostgreSQL adapter for transactional outbox (K17-001/003)
 * @doc.layer product
 * @doc.pattern Repository
 */
public final class PostgresOutboxStore implements OutboxPort {

    private static final String FETCH_UNPUBLISHED = """
            SELECT id, aggregate_id, aggregate_type, event_type, payload,
                   tenant_id, created_at, published, published_at, publish_attempts, last_error
              FROM outbox
             WHERE published = FALSE AND publish_attempts < 5
             ORDER BY created_at
             LIMIT ?
             FOR UPDATE SKIP LOCKED
            """;

    private static final String MARK_PUBLISHED = """
            UPDATE outbox SET published = TRUE, published_at = NOW() WHERE id = ANY(?)
            """;

    private static final String MARK_FAILED = """
            UPDATE outbox
               SET publish_attempts = publish_attempts + 1,
                   last_error       = ?
             WHERE id = ?
            """;

    private static final String CLEANUP = """
            DELETE FROM outbox
             WHERE published = TRUE
               AND published_at < NOW() - (? || ' days')::INTERVAL
            """;

    private final DataSource dataSource;
    private final Executor executor;

    public PostgresOutboxStore(DataSource dataSource, Executor executor) {
        this.dataSource = dataSource;
        this.executor = executor;
    }

    @Override
    public Promise<List<OutboxEntry>> fetchUnpublished(int batchSize) {
        return Promise.ofBlocking(executor, () -> {
            List<OutboxEntry> result = new ArrayList<>();
            // SELECT FOR UPDATE SKIP LOCKED must be inside explicit transaction
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                try (PreparedStatement ps = conn.prepareStatement(FETCH_UNPUBLISHED)) {
                    ps.setInt(1, batchSize);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            result.add(mapRow(rs));
                        }
                    }
                }
                conn.commit();
                conn.setAutoCommit(true);
            }
            return result;
        });
    }

    @Override
    public Promise<Void> markPublished(List<UUID> ids) {
        if (ids.isEmpty()) return Promise.of(null);
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(MARK_PUBLISHED)) {
                java.sql.Array arr = conn.createArrayOf("uuid",
                        ids.stream().map(UUID::toString).toArray(String[]::new));
                ps.setArray(1, arr);
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public Promise<Void> markFailed(UUID id, String error) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(MARK_FAILED)) {
                ps.setString(1, error != null && error.length() > 2000
                        ? error.substring(0, 2000) : error);
                ps.setObject(2, id);
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public Promise<Integer> cleanupOlderThan(int retentionDays) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(CLEANUP)) {
                ps.setInt(1, retentionDays);
                return ps.executeUpdate();
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────

    private static OutboxEntry mapRow(ResultSet rs) throws SQLException {
        Timestamp publishedAt = rs.getTimestamp("published_at");
        Timestamp createdAt = rs.getTimestamp("created_at");
        return new OutboxEntry(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("aggregate_id"),
                rs.getString("aggregate_type"),
                rs.getString("event_type"),
                rs.getString("payload"),
                (UUID) rs.getObject("tenant_id"),
                createdAt != null ? createdAt.toInstant() : Instant.now(),
                rs.getBoolean("published"),
                publishedAt != null ? publishedAt.toInstant() : null,
                rs.getInt("publish_attempts"),
                rs.getString("last_error")
        );
    }
}
