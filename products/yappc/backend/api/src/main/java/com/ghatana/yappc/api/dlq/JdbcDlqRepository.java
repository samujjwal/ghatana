/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.dlq;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC-backed repository for the {@code yappc_dlq} table.
 *
 * <p>Provides list, get, and status-update operations used by the DLQ management API.
 *
 * <p>Schema: see {@code V19__dlq.sql}.
 *
 * @doc.type class
 * @doc.purpose JDBC repository for DLQ entry CRUD operations
 * @doc.layer product
 * @doc.pattern Repository
 */
public class JdbcDlqRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcDlqRepository.class);

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private static final String LIST_SQL = """
            SELECT id, tenant_id, pipeline_id, node_id, event_type, event_payload,
                   failure_reason, retry_count, status, correlation_id,
                   created_at, updated_at, resolved_at
            FROM yappc_dlq
            WHERE tenant_id = ? AND status = ?
            ORDER BY created_at DESC
            LIMIT ?
            """;

    private static final String GET_SQL = """
            SELECT id, tenant_id, pipeline_id, node_id, event_type, event_payload,
                   failure_reason, retry_count, status, correlation_id,
                   created_at, updated_at, resolved_at
            FROM yappc_dlq
            WHERE id = ? AND tenant_id = ?
            """;

    private static final String UPDATE_STATUS_SQL = """
            UPDATE yappc_dlq
            SET status = ?, retry_count = retry_count + ?,
                resolved_at = ?, updated_at = now()
            WHERE id = ? AND tenant_id = ?
            """;

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    public JdbcDlqRepository(DataSource dataSource, ObjectMapper objectMapper) {
        this.dataSource   = Objects.requireNonNull(dataSource, "dataSource");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    /**
     * Lists DLQ entries for a tenant, filtered by status.
     *
     * @param tenantId the tenant to query
     * @param status   entry status ({@code PENDING}, {@code RETRYING}, etc.)
     * @param limit    maximum number of rows to return
     * @return immutable list of DLQ entries, newest first
     */
    public List<DlqEntry> list(String tenantId, String status, int limit) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(LIST_SQL)) {
            ps.setString(1, tenantId);
            ps.setString(2, status);
            ps.setInt(3, limit);

            ResultSet rs = ps.executeQuery();
            List<DlqEntry> results = new ArrayList<>();
            while (rs.next()) {
                results.add(mapRow(rs));
            }
            return Collections.unmodifiableList(results);
        } catch (Exception e) {
            log.error("Failed to list DLQ entries for tenant={} status={}", tenantId, status, e);
            return Collections.emptyList();
        }
    }

    /**
     * Returns a single DLQ entry by ID within a tenant's scope.
     *
     * @param id       the entry UUID
     * @param tenantId the owning tenant
     * @return the entry, or empty if not found
     */
    public Optional<DlqEntry> findById(UUID id, String tenantId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(GET_SQL)) {
            ps.setObject(1, id);
            ps.setString(2, tenantId);

            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                return Optional.empty();
            }
            return Optional.of(mapRow(rs));
        } catch (Exception e) {
            log.error("Failed to get DLQ entry id={} tenant={}", id, tenantId, e);
            return Optional.empty();
        }
    }

    /**
     * Updates the status and optionally increments the retry counter.
     *
     * @param id            the entry UUID
     * @param tenantId      the owning tenant
     * @param newStatus     new status value
     * @param incrementRetry {@code true} to increment retry_count by 1
     * @param resolvedAt    resolved_at timestamp (set only when status = RESOLVED; null otherwise)
     */
    public void updateStatus(UUID id, String tenantId, String newStatus,
                              boolean incrementRetry, Instant resolvedAt) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPDATE_STATUS_SQL)) {
            ps.setString(1, newStatus);
            ps.setInt(2, incrementRetry ? 1 : 0);
            ps.setTimestamp(3, resolvedAt != null ? Timestamp.from(resolvedAt) : null);
            ps.setObject(4, id);
            ps.setString(5, tenantId);
            int updated = ps.executeUpdate();
            if (updated == 0) {
                log.warn("DLQ updateStatus: no row matched id={} tenant={}", id, tenantId);
            }
        } catch (SQLException e) {
            log.error("Failed to update DLQ entry id={} status={}", id, newStatus, e);
            throw new RuntimeException("DLQ status update failed", e);
        }
    }

    // -------------------------------------------------------------------------
    // Mapping
    // -------------------------------------------------------------------------

    private DlqEntry mapRow(ResultSet rs) throws Exception {
        String payloadJson = rs.getString("event_payload");
        Map<String, Object> payload;
        try {
            payload = objectMapper.readValue(payloadJson, MAP_TYPE);
        } catch (Exception e) {
            log.warn("Failed to parse DLQ event_payload JSON — using empty map: {}", e.getMessage());
            payload = Collections.emptyMap();
        }

        Timestamp resolvedTs = rs.getTimestamp("resolved_at");

        return new DlqEntry(
                UUID.fromString(rs.getString("id")),
                rs.getString("tenant_id"),
                rs.getString("pipeline_id"),
                rs.getString("node_id"),
                rs.getString("event_type"),
                payload,
                rs.getString("failure_reason"),
                rs.getInt("retry_count"),
                rs.getString("status"),
                rs.getString("correlation_id"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(),
                resolvedTs != null ? resolvedTs.toInstant() : null
        );
    }
}
