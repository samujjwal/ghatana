/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.audit.AuditEvent;
import com.ghatana.platform.audit.AuditQueryService;
import com.ghatana.platform.audit.AuditService;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * JDBC-backed implementation of {@link AuditService} and {@link AuditQueryService}.
 *
 * <p>All SQL operations are executed on a dedicated blocking executor and wrapped
 * with {@code Promise.ofBlocking} so the ActiveJ event-loop thread is never
 * blocked.
 *
 * <p>Schema: see {@code V12__audit_events.sql}.
 *
 * @doc.type class
 * @doc.purpose Durable JDBC-backed audit event persistence
 * @doc.layer product
 * @doc.pattern Repository, Service
 */
public class JdbcAuditService implements AuditService, AuditQueryService {

    private static final Logger log = LoggerFactory.getLogger(JdbcAuditService.class);

    private static final Executor JDBC_EXECUTOR =
            Executors.newFixedThreadPool(4, r -> {
                Thread t = new Thread(r, "audit-jdbc");
                t.setDaemon(true);
                return t;
            });

    private static final TypeReference<Map<String, Object>> MAP_TYPE =
            new TypeReference<>() {};

    private static final String INSERT_SQL = """
            INSERT INTO audit_events
              (id, tenant_id, event_type, principal, resource_type, resource_id,
               success, occurred_at, details)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
            ON CONFLICT (id) DO NOTHING
            """;

    private static final String SELECT_BY_TENANT = """
            SELECT * FROM audit_events
            WHERE tenant_id = ?
            ORDER BY occurred_at DESC
            """;

    private static final String SELECT_BY_TENANT_PAGED = """
            SELECT * FROM audit_events
            WHERE tenant_id = ?
            ORDER BY occurred_at DESC
            LIMIT ? OFFSET ?
            """;

    private static final String SELECT_BY_RESOURCE = """
            SELECT * FROM audit_events
            WHERE tenant_id = ? AND resource_type = ? AND resource_id = ?
            ORDER BY occurred_at DESC
            """;

    private static final String SELECT_BY_PRINCIPAL = """
            SELECT * FROM audit_events
            WHERE tenant_id = ? AND principal = ?
            ORDER BY occurred_at DESC
            """;

    private static final String SELECT_BY_EVENT_TYPE = """
            SELECT * FROM audit_events
            WHERE tenant_id = ? AND event_type = ?
            ORDER BY occurred_at DESC
            """;

    private static final String SELECT_BY_TIME_RANGE = """
            SELECT * FROM audit_events
            WHERE tenant_id = ? AND occurred_at >= ? AND occurred_at <= ?
            ORDER BY occurred_at DESC
            """;

    private static final String SELECT_BY_ID = """
            SELECT * FROM audit_events
            WHERE tenant_id = ? AND id = ?
            """;

    private static final String COUNT_BY_TENANT = """
            SELECT COUNT(*) FROM audit_events WHERE tenant_id = ?
            """;

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    public JdbcAuditService(@NotNull DataSource dataSource,
                            @NotNull ObjectMapper objectMapper) {
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
    }

    // =========================================================================
    // AuditService
    // =========================================================================

    @Override
    public Promise<Void> record(@NotNull AuditEvent event) {
        return Promise.ofBlocking(JDBC_EXECUTOR, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {

                ps.setString(1, event.getId());
                ps.setString(2, event.getTenantId());
                ps.setString(3, event.getEventType());
                ps.setString(4, event.getPrincipal());
                ps.setString(5, event.getResourceType());
                ps.setString(6, event.getResourceId());
                ps.setObject(7, event.getSuccess());
                ps.setTimestamp(8, event.getTimestamp() != null
                        ? Timestamp.from(event.getTimestamp())
                        : Timestamp.from(Instant.now()));
                ps.setString(9, serializeDetails(event.getDetails()));

                ps.executeUpdate();
                log.debug("Recorded audit event id={} tenant={} type={}",
                        event.getId(), event.getTenantId(), event.getEventType());
            }
            return null;
        });
    }

    // =========================================================================
    // AuditQueryService
    // =========================================================================

    @Override
    public Promise<List<AuditEvent>> findByTenantId(@NotNull String tenantId) {
        return Promise.ofBlocking(JDBC_EXECUTOR, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SELECT_BY_TENANT)) {
                ps.setString(1, tenantId);
                return mapResultSet(ps.executeQuery());
            }
        });
    }

    @Override
    public Promise<List<AuditEvent>> findByTenantId(@NotNull String tenantId,
                                                    int offset, int limit) {
        return Promise.ofBlocking(JDBC_EXECUTOR, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SELECT_BY_TENANT_PAGED)) {
                ps.setString(1, tenantId);
                ps.setInt(2, limit);
                ps.setInt(3, offset);
                return mapResultSet(ps.executeQuery());
            }
        });
    }

    @Override
    public Promise<List<AuditEvent>> findByResource(@NotNull String tenantId,
                                                    @NotNull String resourceType,
                                                    @NotNull String resourceId) {
        return Promise.ofBlocking(JDBC_EXECUTOR, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SELECT_BY_RESOURCE)) {
                ps.setString(1, tenantId);
                ps.setString(2, resourceType);
                ps.setString(3, resourceId);
                return mapResultSet(ps.executeQuery());
            }
        });
    }

    @Override
    public Promise<List<AuditEvent>> findByPrincipal(@NotNull String tenantId,
                                                     @NotNull String principal) {
        return Promise.ofBlocking(JDBC_EXECUTOR, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SELECT_BY_PRINCIPAL)) {
                ps.setString(1, tenantId);
                ps.setString(2, principal);
                return mapResultSet(ps.executeQuery());
            }
        });
    }

    @Override
    public Promise<List<AuditEvent>> findByEventType(@NotNull String tenantId,
                                                     @NotNull String eventType) {
        return Promise.ofBlocking(JDBC_EXECUTOR, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SELECT_BY_EVENT_TYPE)) {
                ps.setString(1, tenantId);
                ps.setString(2, eventType);
                return mapResultSet(ps.executeQuery());
            }
        });
    }

    @Override
    public Promise<List<AuditEvent>> findByTimeRange(@NotNull String tenantId,
                                                     @NotNull Instant from,
                                                     @NotNull Instant to) {
        return Promise.ofBlocking(JDBC_EXECUTOR, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SELECT_BY_TIME_RANGE)) {
                ps.setString(1, tenantId);
                ps.setTimestamp(2, Timestamp.from(from));
                ps.setTimestamp(3, Timestamp.from(to));
                return mapResultSet(ps.executeQuery());
            }
        });
    }

    @Override
    public Promise<Optional<AuditEvent>> findById(@NotNull String tenantId,
                                                  @NotNull String eventId) {
        return Promise.ofBlocking(JDBC_EXECUTOR, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SELECT_BY_ID)) {
                ps.setString(1, tenantId);
                ps.setString(2, eventId);
                List<AuditEvent> results = mapResultSet(ps.executeQuery());
                return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
            }
        });
    }

    @Override
    public Promise<Long> countByTenantId(@NotNull String tenantId) {
        return Promise.ofBlocking(JDBC_EXECUTOR, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(COUNT_BY_TENANT)) {
                ps.setString(1, tenantId);
                ResultSet rs = ps.executeQuery();
                return rs.next() ? rs.getLong(1) : 0L;
            }
        });
    }

    @Override
    public Promise<List<AuditEvent>> search(@NotNull String tenantId, AuditQueryService.AuditSearchCriteria criteria) {
        // Delegate to findByTenantId for now — full criteria filtering can be added later
        return findByTenantId(tenantId);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private List<AuditEvent> mapResultSet(@NotNull ResultSet rs) throws SQLException {
        List<AuditEvent> events = new ArrayList<>();
        while (rs.next()) {
            events.add(rowToEvent(rs));
        }
        return events;
    }

    private AuditEvent rowToEvent(@NotNull ResultSet rs) throws SQLException {
        String detailsJson = rs.getString("details");
        Map<String, Object> details = new HashMap<>();
        if (detailsJson != null) {
            try {
                details = objectMapper.readValue(detailsJson, MAP_TYPE);
            } catch (JsonProcessingException e) {
                log.warn("Failed to deserialize audit details for id={}", rs.getString("id"), e);
            }
        }

        Timestamp ts = rs.getTimestamp("occurred_at");
        return AuditEvent.builder()
                .id(rs.getString("id"))
                .tenantId(rs.getString("tenant_id"))
                .eventType(rs.getString("event_type"))
                .principal(rs.getString("principal"))
                .resourceType(rs.getString("resource_type"))
                .resourceId(rs.getString("resource_id"))
                .success(rs.getObject("success", Boolean.class))
                .timestamp(ts != null ? ts.toInstant() : null)
                .details(details)
                .build();
    }

    private String serializeDetails(@NotNull Map<String, Object> details) {
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize audit details, storing empty object", e);
            return "{}";
        }
    }
}
