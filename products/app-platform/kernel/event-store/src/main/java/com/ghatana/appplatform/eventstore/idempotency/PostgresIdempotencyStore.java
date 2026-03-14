package com.ghatana.appplatform.eventstore.idempotency;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * PostgreSQL fallback idempotency store for when Redis is unavailable.
 * Uses the {@code idempotency_keys} table (V006 migration).
 *
 * @doc.type class
 * @doc.purpose PostgreSQL fallback idempotency store adapter (STORY-K05-015)
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class PostgresIdempotencyStore implements IdempotencyStore {

    private final DataSource dataSource;

    public PostgresIdempotencyStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public boolean claim(String tenantId, String idempotencyKey, String responseHash, int ttlSeconds) {
        String sql = """
            INSERT INTO idempotency_keys (idempotency_key, tenant_id, response_hash, expires_at)
            VALUES (?, ?, ?, NOW() + (? || ' seconds')::INTERVAL)
            ON CONFLICT (idempotency_key, tenant_id) DO NOTHING
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, idempotencyKey);
            ps.setString(2, tenantId);
            ps.setString(3, responseHash);
            ps.setInt(4, ttlSeconds);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to claim idempotency key: " + idempotencyKey, e);
        }
    }

    @Override
    public Optional<String> getResponseHash(String tenantId, String idempotencyKey) {
        String sql = """
            SELECT response_hash FROM idempotency_keys
             WHERE idempotency_key = ? AND tenant_id = ? AND expires_at > NOW()
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, idempotencyKey);
            ps.setString(2, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getString("response_hash")) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get idempotency response hash: " + idempotencyKey, e);
        }
    }
}
