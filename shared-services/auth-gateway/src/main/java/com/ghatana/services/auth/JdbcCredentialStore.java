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
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * JDBC-backed {@link CredentialStore} for production deployments.
 *
 * <h2>Schema (PostgreSQL)</h2>
 * <pre>{@code
 * CREATE TABLE auth_users (
 *   username      TEXT        PRIMARY KEY,
 *   password_hash TEXT        NOT NULL,
 *   email         TEXT        NOT NULL,
 *   roles         TEXT[]      NOT NULL DEFAULT '{}',
 *   tenant_id     TEXT        NOT NULL,
 *   enabled       BOOLEAN     NOT NULL DEFAULT TRUE,
 *   created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 *   updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
 * );
 * CREATE INDEX auth_users_tenant ON auth_users(tenant_id);
 * }</pre>
 *
 * <h2>ActiveJ concurrency</h2>
 * <p>All JDBC calls are off-loaded to a dedicated {@link Executor} via
 * {@code Promise.ofBlocking} so the ActiveJ event loop thread is never blocked.</p>
 *
 * @doc.type class
 * @doc.purpose Production JDBC-backed credential store for the auth-gateway
 * @doc.layer product
 * @doc.pattern Repository
 */
public class JdbcCredentialStore implements CredentialStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcCredentialStore.class);

    /** Dedicated I/O executor — never runs on the ActiveJ event loop. */
    private static final Executor DB_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "auth-db");
        t.setDaemon(true);
        return t;
    });

    private final DataSource dataSource;

    /**
     * Creates a {@link JdbcCredentialStore} backed by the given DataSource.
     *
     * @param dataSource the JDBC DataSource (e.g. HikariCP pool)
     */
    public JdbcCredentialStore(@NotNull DataSource dataSource) {
        this.dataSource = dataSource;
        log.info("JdbcCredentialStore initialised");
    }

    @Override
    @NotNull
    public Promise<Optional<StoredUser>> findByUsername(@NotNull String username) {
        return Promise.ofBlocking(DB_EXECUTOR, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT username, password_hash, email, roles, tenant_id, enabled " +
                         "FROM auth_users WHERE username = ?")) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return Optional.<StoredUser>empty();
                    return Optional.of(mapRow(rs));
                }
            } catch (SQLException e) {
                log.error("findByUsername failed for '{}': {}", username, e.getMessage());
                throw new RuntimeException("Credential store lookup failed", e);
            }
        });
    }

    @Override
    @NotNull
    public Promise<StoredUser> createUser(
            @NotNull String username,
            @NotNull String passwordHash,
            @NotNull String email,
            @NotNull List<String> roles,
            @NotNull String tenantId) {
        return Promise.ofBlocking(DB_EXECUTOR, () -> {
            try (Connection conn = dataSource.getConnection()) {
                if (isH2(conn)) {
                    return createUserH2(conn, username, passwordHash, email, roles, tenantId);
                }
                return createUserPostgres(conn, username, passwordHash, email, roles, tenantId);
            } catch (SQLException e) {
                log.error("createUser failed for '{}': {}", username, e.getMessage());
                throw new RuntimeException("Credential store insert failed", e);
            }
        });
    }

    // ── Schema migration helper ────────────────────────────────────────────────

    /**
     * Applies the minimal DDL required by this store if the table does not yet exist.
     * Safe to call at startup; uses {@code CREATE TABLE IF NOT EXISTS}.
     */
    public void ensureSchema() {
        try (Connection conn = dataSource.getConnection()) {
            String createTableSql = isH2(conn)
                    ? "CREATE TABLE IF NOT EXISTS auth_users (" +
                      "  username      VARCHAR     PRIMARY KEY," +
                      "  password_hash VARCHAR     NOT NULL," +
                      "  email         VARCHAR     NOT NULL," +
                      "  roles         VARCHAR ARRAY NOT NULL," +
                      "  tenant_id     VARCHAR     NOT NULL," +
                      "  enabled       BOOLEAN     NOT NULL DEFAULT TRUE," +
                      "  created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP()," +
                      "  updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP()" +
                      ")"
                    : "CREATE TABLE IF NOT EXISTS auth_users (" +
                      "  username      TEXT        PRIMARY KEY," +
                      "  password_hash TEXT        NOT NULL," +
                      "  email         TEXT        NOT NULL," +
                      "  roles         TEXT[]      NOT NULL DEFAULT '{}'," +
                      "  tenant_id     TEXT        NOT NULL," +
                      "  enabled       BOOLEAN     NOT NULL DEFAULT TRUE," +
                      "  created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()," +
                      "  updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()" +
                      ")";

            try (PreparedStatement createTable = conn.prepareStatement(createTableSql)) {
                createTable.execute();
            }
            try (PreparedStatement createIndex = conn.prepareStatement(
                    "CREATE INDEX IF NOT EXISTS auth_users_tenant ON auth_users(tenant_id)")) {
                createIndex.execute();
            }
            log.info("auth_users schema verified/created");
        } catch (SQLException e) {
            log.error("ensureSchema failed: {}", e.getMessage());
            throw new RuntimeException("Schema migration failed", e);
        }
    }

    private StoredUser createUserPostgres(
            Connection conn,
            String username,
            String passwordHash,
            String email,
            List<String> roles,
            String tenantId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO auth_users (username, password_hash, email, roles, tenant_id, enabled) " +
                "VALUES (?, ?, ?, ?, ?, TRUE) " +
                "ON CONFLICT (username) DO NOTHING RETURNING *")) {
            ps.setString(1, username);
            ps.setString(2, passwordHash);
            ps.setString(3, email);
            ps.setArray(4, conn.createArrayOf("TEXT", roles.toArray()));
            ps.setString(5, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("createUser: username already exists: " + username);
                }
                return mapRow(rs);
            }
        }
    }

    private StoredUser createUserH2(
            Connection conn,
            String username,
            String passwordHash,
            String email,
            List<String> roles,
            String tenantId) throws SQLException {
        try (PreparedStatement insert = conn.prepareStatement(
                "INSERT INTO auth_users (username, password_hash, email, roles, tenant_id, enabled) VALUES (?, ?, ?, ?, ?, TRUE)")) {
            insert.setString(1, username);
            insert.setString(2, passwordHash);
            insert.setString(3, email);
            insert.setArray(4, conn.createArrayOf("VARCHAR", roles.toArray()));
            insert.setString(5, tenantId);
            insert.executeUpdate();
        } catch (SQLException e) {
            if (isDuplicateKey(e)) {
                throw new IllegalStateException("createUser: username already exists: " + username, e);
            }
            throw e;
        }

        try (PreparedStatement select = conn.prepareStatement(
                "SELECT username, password_hash, email, roles, tenant_id, enabled FROM auth_users WHERE username = ?")) {
            select.setString(1, username);
            try (ResultSet rs = select.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("createUser: inserted user could not be reloaded: " + username);
                }
                return mapRow(rs);
            }
        }
    }

    private static boolean isH2(Connection conn) throws SQLException {
        return conn.getMetaData().getDatabaseProductName().toLowerCase().contains("h2");
    }

    private static boolean isDuplicateKey(SQLException e) {
        return "23505".equals(e.getSQLState());
    }

    // ── Mapping ────────────────────────────────────────────────────────────────

    private static StoredUser mapRow(ResultSet rs) throws SQLException {
        String username     = rs.getString("username");
        String passwordHash = rs.getString("password_hash");
        String email        = rs.getString("email");
        List<String> roles  = pgArrayToList(rs.getArray("roles"));
        String tenantId     = rs.getString("tenant_id");
        boolean enabled     = rs.getBoolean("enabled");
        return new StoredUser(username, passwordHash, email, roles, tenantId, enabled);
    }

    private static List<String> pgArrayToList(Array array) throws SQLException {
        if (array == null) return List.of();
        Object[] raw = (Object[]) array.getArray();
        List<String> result = new ArrayList<>(raw.length);
        for (Object o : raw) {
            result.add(o != null ? o.toString() : "");
        }
        return List.copyOf(result);
    }
}
