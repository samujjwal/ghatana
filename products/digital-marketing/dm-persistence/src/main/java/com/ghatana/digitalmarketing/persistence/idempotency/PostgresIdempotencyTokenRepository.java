package com.ghatana.digitalmarketing.persistence.idempotency;

import com.ghatana.digitalmarketing.application.idempotency.IdempotencyService.IdempotentResponse;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.persistence.DmPersistenceException;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

/**
 * PostgreSQL implementation of IdempotencyTokenRepository.
 *
 * @doc.type class
 * @doc.purpose PostgreSQL idempotency token repository
 * @doc.layer product
 * @doc.pattern Repository
 */
public class PostgresIdempotencyTokenRepository implements IdempotencyTokenRepository {

    private static final Logger LOG = LoggerFactory.getLogger(PostgresIdempotencyTokenRepository.class);
    private static final int DEFAULT_EXPIRATION_HOURS = 24;

    private final DataSource dataSource;

    public PostgresIdempotencyTokenRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Promise<IdempotentResponse> findByKey(DmWorkspaceId workspaceId, String idempotencyKey) {
        return Promise.ofBlocking(() -> {
            String sql = """
                SELECT response_payload, response_status, response_headers
                FROM idempotency_tokens
                WHERE workspace_id = ? AND operation_key = ? AND expires_at > NOW()
                """;

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, workspaceId.getValue());
                stmt.setString(2, idempotencyKey);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(new IdempotentResponse(
                            rs.getString("response_payload"),
                            rs.getInt("response_status"),
                            rs.getString("response_headers")
                        ));
                    }
                    return Optional.empty();
                }
            } catch (SQLException e) {
                LOG.error("Failed to find idempotency token: {}", idempotencyKey, e);
                throw new DmPersistenceException("Failed to find idempotency token", e);
            }
        }).then(opt -> opt.isPresent() ? Promise.of(opt.get()) : Promise.of(null));
    }

    @Override
    public Promise<Void> store(DmWorkspaceId workspaceId, String idempotencyKey, IdempotentResponse response, Instant expiresAt) {
        return Promise.ofBlocking(() -> {
            String sql = """
                INSERT INTO idempotency_tokens (id, tenant_id, workspace_id, operation_key,
                                                   response_payload, response_status, response_headers,
                                                   created_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), ?)
                ON CONFLICT (tenant_id, workspace_id, operation_key) DO NOTHING
                """;

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                String tokenId = java.util.UUID.randomUUID().toString();
                stmt.setString(1, tokenId);
                stmt.setString(2, workspaceId.getValue().split("-")[0]); // Extract tenant from workspace
                stmt.setString(3, workspaceId.getValue());
                stmt.setString(4, idempotencyKey);
                stmt.setString(5, response.body());
                stmt.setInt(6, response.statusCode());
                stmt.setString(7, response.headers());
                stmt.setObject(8, expiresAt);

                int rowsAffected = stmt.executeUpdate();
                LOG.debug("[DMOS] Idempotency token stored: key={} workspace={} rows={}",
                    idempotencyKey, workspaceId.getValue(), rowsAffected);
                return null;
            } catch (SQLException e) {
                LOG.error("Failed to store idempotency token: {}", idempotencyKey, e);
                throw new DmPersistenceException("Failed to store idempotency token", e);
            }
        });
    }

    @Override
    public Promise<Integer> deleteExpired() {
        return Promise.ofBlocking(() -> {
            String sql = "DELETE FROM idempotency_tokens WHERE expires_at < NOW()";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                int rowsDeleted = stmt.executeUpdate();
                if (rowsDeleted > 0) {
                    LOG.info("[DMOS] Deleted {} expired idempotency tokens", rowsDeleted);
                }
                return rowsDeleted;
            } catch (SQLException e) {
                LOG.error("Failed to delete expired idempotency tokens", e);
                throw new DmPersistenceException("Failed to delete expired idempotency tokens", e);
            }
        });
    }
}
