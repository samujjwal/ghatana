package com.ghatana.datacloud.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.spi.WriteIdempotencyStore;
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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * H2-backed durable implementation of {@link WriteIdempotencyStore}.
 *
 * <p>Stores idempotency entries in the {@code dc_write_idempotency} table so that cached
 * responses survive process restarts. Uses the same H2 data source as the entity store.
 * Schema is initialised on first use via {@code initializeSchema()}.
 *
 * <p>This generic store replaces the entity-specific {@code H2EntityWriteIdempotencyStore}
 * to support idempotency across all mutating routes (entities, pipelines, events, governance, analytics).
 *
 * @doc.type class
 * @doc.purpose Persists write idempotency state in H2 so entries survive restarts
 * @doc.layer product
 * @doc.pattern Repository
 */
public final class H2WriteIdempotencyStore implements WriteIdempotencyStore {

    private static final Logger LOG = LoggerFactory.getLogger(H2WriteIdempotencyStore.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private static final String CREATE_TABLE_SQL = """
        CREATE TABLE IF NOT EXISTS dc_write_idempotency (
            scoped_key   VARCHAR(2048)  NOT NULL PRIMARY KEY,
            response_json TEXT          NOT NULL,
            stored_at    TIMESTAMP      NOT NULL,
            expires_at   TIMESTAMP      NOT NULL
        )
        """;

    private static final String CREATE_INDEX_SQL =
        "CREATE INDEX IF NOT EXISTS dc_write_idempotency_expires_idx ON dc_write_idempotency (expires_at)";

    private static final String SELECT_SQL = """
        SELECT response_json, expires_at
          FROM dc_write_idempotency
         WHERE scoped_key = ?
        """;

    private static final String UPSERT_SQL = """
        MERGE INTO dc_write_idempotency (scoped_key, response_json, stored_at, expires_at)
        KEY (scoped_key)
        VALUES (?, ?, ?, ?)
        """;

    private static final String EVICT_EXPIRED_SQL =
        "DELETE FROM dc_write_idempotency WHERE expires_at < ?";

    private final DataSource dataSource;
    private final Duration ttl;

    /**
     * Creates a durable idempotency store backed by the provided data source.
     *
     * @param dataSource H2 data source (shared with entity store)
     * @param ttl        time-to-live for stored entries; entries older than this are treated as absent
     */
    public H2WriteIdempotencyStore(DataSource dataSource, Duration ttl) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.ttl = Objects.requireNonNull(ttl, "ttl");
        initializeSchema();
    }

    @Override
    public Optional<Map<String, Object>> get(String tenantId, String operationScope, String idempotencyKey) {
        String scopedKey = scopedKey(tenantId, operationScope, idempotencyKey);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL)) {
            ps.setString(1, scopedKey);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                Instant expiresAt = rs.getTimestamp(2).toInstant();
                if (Instant.now().isAfter(expiresAt)) {
                    evictExpired(conn);
                    return Optional.empty();
                }
                String json = rs.getString(1);
                return Optional.of(OBJECT_MAPPER.readValue(json, MAP_TYPE));
            }
        } catch (SQLException | JsonProcessingException ex) {
            LOG.warn("[DC-BE-002] Failed to read idempotency entry key={}: {}", scopedKey, ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void put(String tenantId, String operationScope, String idempotencyKey, Map<String, Object> responseBody) {
        String scopedKey = scopedKey(tenantId, operationScope, idempotencyKey);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPSERT_SQL)) {
            Instant now = Instant.now();
            ps.setString(1, scopedKey);
            ps.setString(2, OBJECT_MAPPER.writeValueAsString(responseBody));
            ps.setTimestamp(3, Timestamp.from(now));
            ps.setTimestamp(4, Timestamp.from(now.plus(ttl)));
            ps.executeUpdate();
            if (!conn.getAutoCommit()) conn.commit();
        } catch (SQLException | JsonProcessingException ex) {
            LOG.warn("[DC-BE-002] Failed to store idempotency entry key={}: {}", scopedKey, ex.getMessage());
        }
    }

    // ==================== internals ====================

    private static String scopedKey(String tenantId, String operationScope, String idempotencyKey) {
        return tenantId + "\u0000" + operationScope + "\u0000" + idempotencyKey;
    }

    private void initializeSchema() {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement tbl = conn.prepareStatement(CREATE_TABLE_SQL);
             PreparedStatement idx = conn.prepareStatement(CREATE_INDEX_SQL)) {
            tbl.execute();
            idx.execute();
            if (!conn.getAutoCommit()) conn.commit();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to initialise dc_write_idempotency schema", ex);
        }
    }

    private void evictExpired(Connection conn) {
        try (PreparedStatement ps = conn.prepareStatement(EVICT_EXPIRED_SQL)) {
            ps.setTimestamp(1, Timestamp.from(Instant.now()));
            ps.executeUpdate();
            if (!conn.getAutoCommit()) conn.commit();
        } catch (SQLException ex) {
            LOG.debug("[DC-BE-002] Eviction of expired idempotency entries failed: {}", ex.getMessage());
        }
    }
}
