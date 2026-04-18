/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.services.auth;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * JDBC-backed {@link TokenBlocklist} for production deployments.
 *
 * <h2>Schema (PostgreSQL)</h2>
 * <pre>{@code
 * CREATE TABLE auth_token_blocklist (
 *   jti          TEXT        PRIMARY KEY,
 *   expires_at   BIGINT      NOT NULL,
 *   blocked_at   BIGINT      NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)
 * );
 * CREATE INDEX auth_token_blocklist_expires ON auth_token_blocklist(expires_at);
 * }</pre>
 *
 * <h2>ActiveJ concurrency</h2>
 * <p>All JDBC calls are off-loaded to a dedicated {@link Executor} via
 * {@code Promise.ofBlocking} so the ActiveJ event loop thread is never blocked.</p>
 *
 * @doc.type class
 * @doc.purpose Production JDBC-backed token blocklist for the auth-gateway
 * @doc.layer product
 * @doc.pattern Repository
 */
public class JdbcTokenBlocklist implements TokenBlocklist {

    private static final Logger log = LoggerFactory.getLogger(JdbcTokenBlocklist.class);

    /** Dedicated I/O executor — never runs on the ActiveJ event loop. */
    private static final Executor DB_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "auth-blocklist");
        t.setDaemon(true);
        return t;
    });

    private final DataSource dataSource;

    /**
     * Creates a {@link JdbcTokenBlocklist} backed by the given DataSource.
     *
     * @param dataSource the JDBC DataSource (e.g. HikariCP pool)
     */
    public JdbcTokenBlocklist(@NotNull DataSource dataSource) {
        this.dataSource = dataSource;
        log.info("JdbcTokenBlocklist initialised");
    }

    @Override
    @NotNull
    public Promise<Void> block(@NotNull String jti, long expiresAt) {
        return Promise.ofBlocking(DB_EXECUTOR, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO auth_token_blocklist (jti, expires_at, blocked_at) " +
                         "VALUES (?, ?, ?) " +
                         "ON CONFLICT (jti) DO UPDATE SET blocked_at = excluded.blocked_at")) {
                ps.setString(1, jti);
                ps.setLong(2, expiresAt);
                ps.setLong(3, System.currentTimeMillis());
                ps.executeUpdate();
                log.debug("Blocked token jti={} (expiresAt={})", jti, expiresAt);
                return null;
            } catch (SQLException e) {
                log.error("block failed for jti='{}': {}", jti, e.getMessage());
                throw new RuntimeException("Token blocklist insert failed", e);
            }
        });
    }

    @Override
    @NotNull
    public Promise<Boolean> isBlocked(@NotNull String jti) {
        return Promise.ofBlocking(DB_EXECUTOR, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT 1 FROM auth_token_blocklist WHERE jti = ? AND expires_at > ?")) {
                ps.setString(1, jti);
                ps.setLong(2, System.currentTimeMillis());
                try (ResultSet rs = ps.executeQuery()) {
                    boolean blocked = rs.next();
                    if (blocked) {
                        log.debug("Token jti={} is blocked", jti);
                    }
                    return blocked;
                }
            } catch (SQLException e) {
                log.error("isBlocked failed for jti='{}': {}", jti, e.getMessage());
                throw new RuntimeException("Token blocklist lookup failed", e);
            }
        });
    }

    @Override
    @NotNull
    public Promise<Integer> cleanupExpired() {
        return Promise.ofBlocking(DB_EXECUTOR, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "DELETE FROM auth_token_blocklist WHERE expires_at < ?")) {
                long now = System.currentTimeMillis();
                ps.setLong(1, now);
                int deleted = ps.executeUpdate();
                if (deleted > 0) {
                    log.info("Cleaned up {} expired blocklist entries", deleted);
                }
                return deleted;
            } catch (SQLException e) {
                log.error("cleanupExpired failed: {}", e.getMessage());
                throw new RuntimeException("Token blocklist cleanup failed", e);
            }
        });
    }

    // ── Schema migration helper ────────────────────────────────────────────────

    /**
     * Applies the minimal DDL required by this store if the table does not yet exist.
     * Safe to call at startup; uses {@code CREATE TABLE IF NOT EXISTS}.
     */
    public void ensureSchema() {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS auth_token_blocklist (" +
                     "  jti          TEXT        PRIMARY KEY," +
                     "  expires_at   BIGINT      NOT NULL," +
                     "  blocked_at   BIGINT      NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)" +
                     "); " +
                     "CREATE INDEX IF NOT EXISTS auth_token_blocklist_expires ON auth_token_blocklist(expires_at);")) {
            ps.execute();
            log.info("auth_token_blocklist schema verified/created");
        } catch (SQLException e) {
            log.error("ensureSchema failed: {}", e.getMessage());
            throw new RuntimeException("Schema migration failed", e);
        }
    }
}
