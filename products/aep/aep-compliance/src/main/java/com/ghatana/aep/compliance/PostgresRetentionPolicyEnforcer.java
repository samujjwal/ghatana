/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.compliance;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * PostgreSQL-backed implementation of {@link RetentionPolicyEnforcer}.
 *
 * <p>Retention deadlines are persisted in the {@code retention_policies} table
 * (created by Flyway migration {@code V020__create_retention_policies.sql}).
 * All JDBC calls are dispatched off the ActiveJ event-loop via
 * {@link Promise#ofCallback} to keep the event loop non-blocking.
 *
 * @doc.type class
 * @doc.purpose Durable GDPR retention-policy enforcement backed by PostgreSQL
 * @doc.layer product
 * @doc.pattern Repository
 */
public final class PostgresRetentionPolicyEnforcer implements RetentionPolicyEnforcer {

    private static final Logger log = LoggerFactory.getLogger(PostgresRetentionPolicyEnforcer.class);

    private final DataSource dataSource;
    private final Executor executor;

    /**
     * @param dataSource pooled JDBC data source (HikariCP); never {@code null}
     * @param executor   blocking-I/O thread pool; never {@code null}
     */
    public PostgresRetentionPolicyEnforcer(DataSource dataSource, Executor executor) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.executor   = Objects.requireNonNull(executor,   "executor");
    }

    /**
     * Convenience constructor that creates a small dedicated blocking thread pool.
     *
     * @param dataSource pooled JDBC data source; never {@code null}
     */
    public PostgresRetentionPolicyEnforcer(DataSource dataSource) {
        this(dataSource, Executors.newFixedThreadPool(4,
            r -> {
                Thread t = new Thread(r, "retention-jdbc");
                t.setDaemon(true);
                return t;
            }));
    }

    // ---- RetentionPolicyEnforcer -------------------------------------------

    @Override
    public Promise<Void> registerRetention(String tenantId, String dataId, Duration retentionPeriod) {
        Objects.requireNonNull(tenantId,         "tenantId");
        Objects.requireNonNull(dataId,           "dataId");
        Objects.requireNonNull(retentionPeriod,  "retentionPeriod");

        Instant expiresAt = Instant.now().plus(retentionPeriod);
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("""
                     INSERT INTO retention_policies (tenant_id, data_id, expires_at, updated_at)
                     VALUES (?, ?, ?, NOW())
                     ON CONFLICT (tenant_id, data_id)
                     DO UPDATE SET expires_at = EXCLUDED.expires_at,
                                   updated_at = NOW()
                     """)) {
                ps.setString(1, tenantId);
                ps.setString(2, dataId);
                ps.setTimestamp(3, Timestamp.from(expiresAt));
                ps.executeUpdate();
                log.debug("[retention] Registered retention for tenant={} dataId={} expires={}",
                    tenantId, dataId, expiresAt);
            }
            return null;
        });
    }

    @Override
    public Promise<Void> checkRetention(String tenantId, String dataId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(dataId,   "dataId");

        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("""
                     SELECT expires_at FROM retention_policies
                     WHERE tenant_id = ? AND data_id = ?
                     """)) {
                ps.setString(1, tenantId);
                ps.setString(2, dataId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        Instant expiresAt = rs.getTimestamp("expires_at").toInstant();
                        if (Instant.now().isAfter(expiresAt)) {
                            log.warn("[retention] Data expired: tenant={} dataId={} expires={}",
                                tenantId, dataId, expiresAt);
                            throw new RetentionExpiredException(tenantId, dataId);
                        }
                    }
                    // No policy registered → no restriction
                }
                return null;
            }
        });
    }

    @Override
    public Promise<Void> scheduleDeletion(String tenantId, String dataId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(dataId,   "dataId");

        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("""
                     UPDATE retention_policies
                     SET scheduled_for_deletion = TRUE, updated_at = NOW()
                     WHERE tenant_id = ? AND data_id = ?
                     """)) {
                ps.setString(1, tenantId);
                ps.setString(2, dataId);
                int updated = ps.executeUpdate();
                if (updated == 0) {
                    log.warn("[retention] scheduleDeletion: no policy found for tenant={} dataId={}",
                        tenantId, dataId);
                } else {
                    log.info("[retention] Deletion scheduled: tenant={} dataId={}", tenantId, dataId);
                }
                return null;
            }
        });
    }
}
