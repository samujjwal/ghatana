package com.ghatana.yappc.services.lifecycle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * JDBC-backed durable idempotency store for lifecycle approval endpoints.
 *
 * <p>Replaces {@link InMemoryIdempotencyStore} so approval replay windows
 * survive service restarts.  Uses PostgreSQL UPSERT and TTL eviction.
 *
 * @doc.type class
 * @doc.purpose Durable idempotency key storage for approval HTTP handlers.
 * @doc.layer product
 * @doc.pattern Repository
 */
final class JdbcIdempotencyStore implements ApprovalHttpHandlers.IdempotencyStore {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcIdempotencyStore.class);

    private static final String TABLE_NAME = "approval_idempotency_store";

    private static final String CREATE_TABLE_SQL =
        "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
        "  idempotency_key VARCHAR(255) PRIMARY KEY," +
        "  response_body   TEXT NOT NULL," +
        "  expires_at      TIMESTAMP WITH TIME ZONE NOT NULL" +
        ")";

    private static final String GET_SQL =
        "SELECT response_body FROM " + TABLE_NAME +
        " WHERE idempotency_key = ? AND expires_at > ?";

    private static final String PUT_SQL =
        "INSERT INTO " + TABLE_NAME + " (idempotency_key, response_body, expires_at)" +
        " VALUES (?, ?, ?)" +
        " ON CONFLICT (idempotency_key)" +
        " DO UPDATE SET response_body = EXCLUDED.response_body," +
        "               expires_at    = EXCLUDED.expires_at";

    private static final String CLEANUP_SQL =
        "DELETE FROM " + TABLE_NAME + " WHERE expires_at <= ?";

    private final DataSource dataSource;
    private final Duration replayWindow;

    JdbcIdempotencyStore(DataSource dataSource, Duration replayWindow) {
        this.dataSource = java.util.Objects.requireNonNull(dataSource, "dataSource");
        this.replayWindow = java.util.Objects.requireNonNull(replayWindow, "replayWindow");
        ensureTable();
    }

    @Override
    public Optional<String> get(String key) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(GET_SQL)) {
            ps.setString(1, key);
            ps.setTimestamp(2, Timestamp.from(Instant.now()));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getString(1));
                }
            }
        } catch (SQLException e) {
            LOG.error("Idempotency get failed for key={}", key, e);
        }
        return Optional.empty();
    }

    @Override
    public void put(String key, String responseBody) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(PUT_SQL)) {
            ps.setString(1, key);
            ps.setString(2, responseBody);
            ps.setTimestamp(3, Timestamp.from(Instant.now().plus(replayWindow)));
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.error("Idempotency put failed for key={}", key, e);
        }
    }

    /** Best-effort cleanup of expired rows.  Called periodically by the service. */
    int cleanupExpired() {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(CLEANUP_SQL)) {
            ps.setTimestamp(1, Timestamp.from(Instant.now()));
            return ps.executeUpdate();
        } catch (SQLException e) {
            LOG.error("Idempotency cleanup failed", e);
            return 0;
        }
    }

    private void ensureTable() {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(CREATE_TABLE_SQL)) {
            ps.executeUpdate();
            LOG.info("Ensured idempotency table {}", TABLE_NAME);
        } catch (SQLException e) {
            LOG.error("Failed to create idempotency table {}", TABLE_NAME, e);
        }
    }
}
