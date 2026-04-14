/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.incident;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * PostgreSQL-backed {@link KillSwitchService}.
 *
 * <p>Kill-switch state is persisted in the {@code kill_switch_state} table
 * (created by Flyway migration {@code V020__create_retention_policies.sql}).
 * The special scope {@code "global"} represents the system-wide kill-switch;
 * per-tenant rows use the tenant ID as the scope.
 *
 * @doc.type class
 * @doc.purpose Durable kill-switch service backed by PostgreSQL
 * @doc.layer shared-service
 * @doc.pattern Repository
 */
public final class PostgresKillSwitchService implements KillSwitchService {

    private static final Logger log = LoggerFactory.getLogger(PostgresKillSwitchService.class);
    private static final String GLOBAL_SCOPE = "global";

    private final DataSource dataSource;
    private final Executor executor;

    /**
     * @param dataSource pooled JDBC data source (HikariCP); never {@code null}
     * @param executor   blocking-I/O thread pool; never {@code null}
     */
    public PostgresKillSwitchService(DataSource dataSource, Executor executor) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.executor   = Objects.requireNonNull(executor,   "executor");
    }

    /**
     * Convenience constructor that creates a small dedicated blocking thread pool.
     */
    public PostgresKillSwitchService(DataSource dataSource) {
        this(dataSource, Executors.newFixedThreadPool(4,
            r -> { Thread t = new Thread(r, "killswitch-jdbc"); t.setDaemon(true); return t; }));
    }

    // ---- KillSwitchService -------------------------------------------------

    @Override
    public Promise<Void> activate(String tenantId, String reason, String incidentId) {
        Objects.requireNonNull(tenantId,    "tenantId");
        Objects.requireNonNull(reason,      "reason");
        Objects.requireNonNull(incidentId,  "incidentId");

        return setActive(tenantId, true, reason, incidentId);
    }

    @Override
    public Promise<Void> deactivate(String tenantId, String reason) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(reason,   "reason");

        return setActive(tenantId, false, reason, null);
    }

    @Override
    public Promise<Boolean> isActive(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId");
        return queryActive(tenantId);
    }

    @Override
    public Promise<Void> activateGlobal(String reason, String incidentId) {
        Objects.requireNonNull(reason,     "reason");
        Objects.requireNonNull(incidentId, "incidentId");

        return setActive(GLOBAL_SCOPE, true, reason, incidentId);
    }

    @Override
    public Promise<Boolean> isGlobalActive() {
        return queryActive(GLOBAL_SCOPE);
    }

    // ---- Internal ----------------------------------------------------------

    private Promise<Void> setActive(String scope, boolean active, String reason, String incidentId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("""
                     INSERT INTO kill_switch_state
                         (scope, active, reason, incident_id, activated_at, deactivated_at, updated_at)
                     VALUES (?, ?, ?, ?, ?, ?, NOW())
                     ON CONFLICT (scope) DO UPDATE SET
                         active          = EXCLUDED.active,
                         reason          = EXCLUDED.reason,
                         incident_id     = EXCLUDED.incident_id,
                         activated_at    = EXCLUDED.activated_at,
                         deactivated_at  = EXCLUDED.deactivated_at,
                         updated_at      = NOW()
                     """)) {
                Timestamp now = Timestamp.from(Instant.now());
                ps.setString(1, scope);
                ps.setBoolean(2, active);
                ps.setString(3, reason);
                ps.setString(4, incidentId);
                ps.setTimestamp(5, active ? now : null);
                ps.setTimestamp(6, active ? null : now);
                ps.executeUpdate();
                log.info("[kill-switch] scope={} active={} reason={}", scope, active, reason);
            }
            return null;
        });
    }

    private Promise<Boolean> queryActive(String scope) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("""
                     SELECT active FROM kill_switch_state WHERE scope = ?
                     """)) {
                ps.setString(1, scope);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() && rs.getBoolean("active");
                }
            }
        });
    }
}
