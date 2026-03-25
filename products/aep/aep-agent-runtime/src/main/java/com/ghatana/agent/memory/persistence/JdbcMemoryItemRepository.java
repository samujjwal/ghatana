package com.ghatana.agent.memory.persistence;

import com.ghatana.agent.memory.model.MemoryItem;
import com.ghatana.agent.memory.model.MemoryItemType;
import com.ghatana.agent.memory.store.MemoryQuery;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * PostgreSQL implementation of MemoryItemRepository.
 *
 * <p>Uses JSONB for flexible content storage and standard SQL for querying.
 * All blocking JDBC calls are wrapped in {@code Promise.ofBlocking} to
 * keep the ActiveJ eventloop unblocked.</p>
 *
 * <p>Expected table DDL:
 * <pre>{@code
 * CREATE TABLE IF NOT EXISTS memory_items (
 *     id            TEXT PRIMARY KEY,
 *     type          TEXT NOT NULL,
 *     tenant_id     TEXT NOT NULL,
 *     sphere_id     TEXT,
 *     content       JSONB NOT NULL,
 *     classification TEXT NOT NULL DEFAULT 'UNCLASSIFIED',
 *     created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 *     updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 *     expires_at    TIMESTAMPTZ,
 *     deleted_at    TIMESTAMPTZ
 * );
 * CREATE INDEX idx_memory_items_tenant ON memory_items(tenant_id);
 * CREATE INDEX idx_memory_items_type   ON memory_items(type);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose PostgreSQL memory persistence with JDBC
 * @doc.layer agent-memory
 * @doc.pattern Repository
 * @doc.gaa.memory episodic
 */
public class JdbcMemoryItemRepository implements MemoryItemRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcMemoryItemRepository.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final DataSource dataSource;
    private final ExecutorService executor;

    /**
     * Creates a new repository backed by the given DataSource.
     *
     * @param dataSource JDBC DataSource (e.g., HikariCP)
     */
    public JdbcMemoryItemRepository(@NotNull DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @Override
    @NotNull
    public Promise<MemoryItem> save(@NotNull MemoryItem item) {
        return Promise.ofBlocking(executor, () -> {
            log.debug("Saving memory item: {} (type={})", item.getId(), item.getType());
            String sql = """
                INSERT INTO memory_items (id, type, tenant_id, sphere_id, content, classification,
                                          created_at, updated_at, expires_at)
                VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    content = EXCLUDED.content,
                    classification = EXCLUDED.classification,
                    updated_at = EXCLUDED.updated_at,
                    expires_at = EXCLUDED.expires_at
                """;
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, item.getId());
                ps.setString(2, item.getType().name());
                ps.setString(3, item.getTenantId());
                ps.setString(4, item.getSphereId());
                ps.setString(5, MAPPER.writeValueAsString(item));
                ps.setString(6, item.getClassification());
                ps.setTimestamp(7, Timestamp.from(item.getCreatedAt()));
                ps.setTimestamp(8, Timestamp.from(item.getUpdatedAt()));
                ps.setTimestamp(9, item.getExpiresAt() != null ? Timestamp.from(item.getExpiresAt()) : null);
                ps.executeUpdate();
            }
            return item;
        });
    }

    @Override
    @NotNull
    public Promise<@Nullable MemoryItem> findById(@NotNull String id) {
        return Promise.ofBlocking(executor, () -> {
            log.debug("Finding memory item by id: {}", id);
            String sql = "SELECT content FROM memory_items WHERE id = ? AND deleted_at IS NULL";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return MAPPER.readValue(rs.getString("content"), MemoryItem.class);
                    }
                    return null;
                }
            }
        });
    }

    @Override
    @NotNull
    public Promise<List<MemoryItem>> findByQuery(@NotNull MemoryQuery query) {
        return Promise.ofBlocking(executor, () -> {
            log.debug("Querying memory items: limit={}", query.getLimit());
            StringBuilder sql = new StringBuilder(
                "SELECT content FROM memory_items WHERE deleted_at IS NULL");
            List<Object> params = new ArrayList<>();

            if (query.getTenantId() != null) {
                sql.append(" AND tenant_id = ?");
                params.add(query.getTenantId());
            }
            if (query.getSphereId() != null) {
                sql.append(" AND sphere_id = ?");
                params.add(query.getSphereId());
            }
            if (query.getItemTypes() != null && !query.getItemTypes().isEmpty()) {
                sql.append(" AND type = ANY(?)");
                params.add(query.getItemTypes().stream().map(MemoryItemType::name).toArray(String[]::new));
            }
            if (query.getStartTime() != null) {
                sql.append(" AND created_at >= ?");
                params.add(Timestamp.from(query.getStartTime()));
            }
            if (query.getEndTime() != null) {
                sql.append(" AND created_at <= ?");
                params.add(Timestamp.from(query.getEndTime()));
            }
            sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
            params.add(query.getLimit());
            params.add(query.getOffset());

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    Object param = params.get(i);
                    if (param instanceof String[]) {
                        ps.setArray(i + 1, conn.createArrayOf("text", (String[]) param));
                    } else if (param instanceof Timestamp ts) {
                        ps.setTimestamp(i + 1, ts);
                    } else if (param instanceof Integer v) {
                        ps.setInt(i + 1, v);
                    } else {
                        ps.setString(i + 1, param.toString());
                    }
                }
                List<MemoryItem> results = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        results.add(MAPPER.readValue(rs.getString("content"), MemoryItem.class));
                    }
                }
                return results;
            }
        });
    }

    @Override
    @NotNull
    public Promise<Void> delete(@NotNull String id) {
        return Promise.ofBlocking(executor, () -> {
            log.debug("Deleting memory item: {}", id);
            String sql = "DELETE FROM memory_items WHERE id = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, id);
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    @NotNull
    public Promise<Void> softDelete(@NotNull String id) {
        return Promise.ofBlocking(executor, () -> {
            log.debug("Soft-deleting memory item: {}", id);
            String sql = "UPDATE memory_items SET deleted_at = NOW() WHERE id = ? AND deleted_at IS NULL";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, id);
                ps.executeUpdate();
            }
            return null;
        });
    }
}
