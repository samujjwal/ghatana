package com.ghatana.services.userprofile;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * PostgreSQL-backed implementation of {@link UserProfileStore}.
 *
 * <p>All JDBC calls are executed off the ActiveJ event loop using
 * {@code Promise.ofBlocking} with the shared {@link ForkJoinPool}.</p>
 *
 * <h3>DDL</h3>
 * <pre>{@code
 * CREATE TABLE IF NOT EXISTS user_profiles (
 *     user_id               TEXT        NOT NULL,
 *     tenant_id             TEXT        NOT NULL,
 *     email                 TEXT        NOT NULL,
 *     display_name          TEXT        NOT NULL,
 *     avatar_url            TEXT,
 *     preferred_language    TEXT        NOT NULL DEFAULT 'en-US',
 *     timezone              TEXT        NOT NULL DEFAULT 'UTC',
 *     theme                 TEXT        NOT NULL DEFAULT 'system',
 *     notifications_enabled BOOLEAN     NOT NULL DEFAULT TRUE,
 *     created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
 *     updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
 *     PRIMARY KEY (tenant_id, user_id)
 * );
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose PostgreSQL implementation of UserProfileStore
 * @doc.layer platform
 * @doc.pattern Repository
 */
public class PostgresUserProfileStore implements UserProfileStore {

    private static final Logger log = LoggerFactory.getLogger(PostgresUserProfileStore.class);

    private static final String SQL_FIND =
            "SELECT user_id, tenant_id, email, display_name, avatar_url, " +
            "preferred_language, timezone, theme, notifications_enabled, " +
            "created_at, updated_at " +
            "FROM user_profiles " +
            "WHERE tenant_id = ? AND user_id = ?";

    private static final String SQL_UPSERT =
            "INSERT INTO user_profiles " +
            "(user_id, tenant_id, email, display_name, avatar_url, " +
            " preferred_language, timezone, theme, notifications_enabled, " +
            " created_at, updated_at) " +
            "VALUES (?,?,?,?,?,?,?,?,?,?,?) " +
            "ON CONFLICT (tenant_id, user_id) DO UPDATE SET " +
            "  email                 = EXCLUDED.email, " +
            "  display_name          = EXCLUDED.display_name, " +
            "  avatar_url            = EXCLUDED.avatar_url, " +
            "  preferred_language    = EXCLUDED.preferred_language, " +
            "  timezone              = EXCLUDED.timezone, " +
            "  theme                 = EXCLUDED.theme, " +
            "  notifications_enabled = EXCLUDED.notifications_enabled, " +
            "  updated_at            = EXCLUDED.updated_at " +
            "RETURNING user_id, tenant_id, email, display_name, avatar_url, " +
            "  preferred_language, timezone, theme, notifications_enabled, " +
            "  created_at, updated_at";

    private static final String SQL_DELETE =
            "DELETE FROM user_profiles WHERE tenant_id = ? AND user_id = ?";

    private static final String DDL =
            "CREATE TABLE IF NOT EXISTS user_profiles (" +
            "  user_id               TEXT        NOT NULL," +
            "  tenant_id             TEXT        NOT NULL," +
            "  email                 TEXT        NOT NULL," +
            "  display_name          TEXT        NOT NULL," +
            "  avatar_url            TEXT," +
            "  preferred_language    TEXT        NOT NULL DEFAULT 'en-US'," +
            "  timezone              TEXT        NOT NULL DEFAULT 'UTC'," +
            "  theme                 TEXT        NOT NULL DEFAULT 'system'," +
            "  notifications_enabled BOOLEAN     NOT NULL DEFAULT TRUE," +
            "  created_at            TIMESTAMPTZ NOT NULL DEFAULT now()," +
            "  updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()," +
            "  PRIMARY KEY (tenant_id, user_id)" +
            ")";

    private final DataSource dataSource;
    private final Executor executor;

    public PostgresUserProfileStore(DataSource dataSource) {
        this(dataSource, ForkJoinPool.commonPool());
    }

    public PostgresUserProfileStore(DataSource dataSource, Executor executor) {
        this.dataSource = dataSource;
        this.executor   = executor;
        initSchema();
    }

    /** Creates the table on startup if it does not already exist. */
    private void initSchema() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(DDL);
            log.info("user_profiles table ready");
        } catch (SQLException e) {
            log.error("Failed to initialise user_profiles schema", e);
            throw new RuntimeException("Schema init failed", e);
        }
    }

    // ─── UserProfileStore ────────────────────────────────────────────────────

    @Override
    public Promise<Optional<UserProfile>> findByTenantAndUser(String tenantId, String userId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SQL_FIND)) {
                ps.setString(1, tenantId);
                ps.setString(2, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRow(rs));
                    }
                    return Optional.empty();
                }
            }
        });
    }

    @Override
    public Promise<UserProfile> upsert(UserProfile profile) {
        Instant now = Instant.now();
        UserProfile toSave = profile.withUpdatedAt(now);
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SQL_UPSERT)) {
                ps.setString(1,  toSave.userId());
                ps.setString(2,  toSave.tenantId());
                ps.setString(3,  toSave.email());
                ps.setString(4,  toSave.displayName());
                ps.setString(5,  toSave.avatarUrl());
                ps.setString(6,  toSave.preferredLanguage());
                ps.setString(7,  toSave.timezone());
                ps.setString(8,  toSave.theme());
                ps.setBoolean(9, toSave.notificationsEnabled());
                ps.setTimestamp(10, Timestamp.from(toSave.createdAt()));
                ps.setTimestamp(11, Timestamp.from(toSave.updatedAt()));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapRow(rs);
                    }
                    // Fallback: return what we tried to save (RETURNING clause should always fire)
                    return toSave;
                }
            }
        });
    }

    @Override
    public Promise<Void> delete(String tenantId, String userId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SQL_DELETE)) {
                ps.setString(1, tenantId);
                ps.setString(2, userId);
                ps.executeUpdate();
                return null;
            }
        });
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private static UserProfile mapRow(ResultSet rs) throws SQLException {
        Timestamp created = rs.getTimestamp("created_at");
        Timestamp updated = rs.getTimestamp("updated_at");
        return UserProfile.builder()
                .userId(rs.getString("user_id"))
                .tenantId(rs.getString("tenant_id"))
                .email(rs.getString("email"))
                .displayName(rs.getString("display_name"))
                .avatarUrl(rs.getString("avatar_url"))
                .preferredLanguage(rs.getString("preferred_language"))
                .timezone(rs.getString("timezone"))
                .theme(rs.getString("theme"))
                .notificationsEnabled(rs.getBoolean("notifications_enabled"))
                .createdAt(created != null ? created.toInstant() : Instant.now())
                .updatedAt(updated != null ? updated.toInstant() : Instant.now())
                .build();
    }
}
