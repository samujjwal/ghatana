/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */
package com.ghatana.audit.persistent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.platform.audit.AuditEvent;
import com.ghatana.platform.audit.AuditQueryService;
import com.ghatana.platform.audit.AuditService;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/**
 * JDBC-based persistent implementation of {@link AuditService} and {@link AuditQueryService}.
 *
 * <p>Replaces the {@code InMemoryAuditQueryService} (ConcurrentHashMap-backed) with
 * a durable PostgreSQL-backed implementation using the {@code audit_trail} table
 * created by migration {@code V006__create_audit_trail.sql}.
 *
 * <p><b>Design Decisions:</b>
 * <ul>
 *   <li>Uses raw JDBC (no JPA) for maximum control and minimal overhead</li>
 *   <li>Append-only writes — no UPDATE/DELETE operations</li>
 *   <li>JSONB for flexible details/state columns</li>
 *   <li>All queries are tenant-isolated</li>
 *   <li>Thread-safe — DataSource handles connection pooling</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose JDBC-backed persistent audit trail service with tenant-isolated append-only writes
 * @doc.layer product
 * @doc.pattern Service
 * @since 2.0.0
 */
public class JdbcPersistentAuditService implements AuditService, AuditQueryService {

    private static final Logger log = LoggerFactory.getLogger(JdbcPersistentAuditService.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private static final String INSERT_SQL = """
            INSERT INTO audit_trail (tenant_id, event_type, entity_type, entity_id, action, actor,
                actor_type, "timestamp", details, previous_state, new_state, correlation_id,
                source, ip_address, success, error_message)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SELECT_BY_TENANT = """
            SELECT * FROM audit_trail WHERE tenant_id = ? ORDER BY "timestamp" DESC
            """;

    private static final String SELECT_BY_TENANT_PAGED = """
            SELECT * FROM audit_trail WHERE tenant_id = ? ORDER BY "timestamp" DESC LIMIT ? OFFSET ?
            """;

    private static final String SELECT_BY_RESOURCE = """
            SELECT * FROM audit_trail WHERE tenant_id = ? AND entity_type = ? AND entity_id = ?
            ORDER BY "timestamp" DESC
            """;

    private static final String SELECT_BY_PRINCIPAL = """
            SELECT * FROM audit_trail WHERE tenant_id = ? AND actor = ?
            ORDER BY "timestamp" DESC
            """;

    private static final String SELECT_BY_EVENT_TYPE = """
            SELECT * FROM audit_trail WHERE tenant_id = ? AND event_type = ?
            ORDER BY "timestamp" DESC
            """;

    private static final String SELECT_BY_TIME_RANGE = """
            SELECT * FROM audit_trail WHERE tenant_id = ? AND "timestamp" >= ? AND "timestamp" <= ?
            ORDER BY "timestamp" DESC
            """;

    private static final String SELECT_BY_ID = """
            SELECT * FROM audit_trail WHERE tenant_id = ? AND id = ?
            """;

    private static final String COUNT_BY_TENANT = """
            SELECT COUNT(*) FROM audit_trail WHERE tenant_id = ?
            """;

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    public JdbcPersistentAuditService(DataSource dataSource, ObjectMapper objectMapper) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public JdbcPersistentAuditService(DataSource dataSource) {
        this(dataSource, JsonUtils.getDefaultMapper());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AuditService (Write)
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public Promise<Void> record(AuditEvent event) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {

            String tenantId = extractTenantId(event);
            ps.setString(1, tenantId);
            ps.setString(2, event.getEventType());
            ps.setString(3, event.getResourceType());
            ps.setString(4, event.getResourceId());
            ps.setString(5, extractAction(event));
            ps.setString(6, event.getPrincipal());
            ps.setString(7, extractDetail(event, "actorType", "USER"));
            ps.setTimestamp(8, Timestamp.from(
                    event.getTimestamp() != null ? event.getTimestamp() : Instant.now()));
            ps.setString(9, toJson(event.getDetails()));
            ps.setString(10, toJson(extractMapDetail(event, "previousState")));
            ps.setString(11, toJson(extractMapDetail(event, "newState")));
            ps.setString(12, extractDetail(event, "correlationId", null));
            ps.setString(13, extractDetail(event, "source", null));
            ps.setString(14, extractDetail(event, "ipAddress", null));
            ps.setBoolean(15, event.getSuccess() != null ? event.getSuccess() : true);
            ps.setString(16, extractDetail(event, "errorMessage", null));

            ps.executeUpdate();

            if (log.isDebugEnabled()) {
                log.debug("Recorded audit event: tenant={}, type={}, resource={}:{}",
                        tenantId, event.getEventType(), event.getResourceType(), event.getResourceId());
            }

            return Promise.complete();
        } catch (SQLException e) {
            log.error("Failed to record audit event: {}", e.getMessage(), e);
            return Promise.ofException(e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AuditQueryService (Read)
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public Promise<List<AuditEvent>> findByTenantId(String tenantId) {
        return executeQuery(SELECT_BY_TENANT, tenantId);
    }

    @Override
    public Promise<List<AuditEvent>> findByTenantId(String tenantId, int offset, int limit) {
        return executeQueryWithPagination(SELECT_BY_TENANT_PAGED, tenantId, offset, limit);
    }

    @Override
    public Promise<List<AuditEvent>> findByResource(String tenantId, String resourceType, String resourceId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_BY_RESOURCE)) {
            ps.setString(1, tenantId);
            ps.setString(2, resourceType);
            ps.setString(3, resourceId);
            return Promise.of(mapResults(ps.executeQuery()));
        } catch (SQLException e) {
            log.error("findByResource failed: {}", e.getMessage(), e);
            return Promise.ofException(e);
        }
    }

    @Override
    public Promise<List<AuditEvent>> findByPrincipal(String tenantId, String principal) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_BY_PRINCIPAL)) {
            ps.setString(1, tenantId);
            ps.setString(2, principal);
            return Promise.of(mapResults(ps.executeQuery()));
        } catch (SQLException e) {
            log.error("findByPrincipal failed: {}", e.getMessage(), e);
            return Promise.ofException(e);
        }
    }

    @Override
    public Promise<List<AuditEvent>> findByEventType(String tenantId, String eventType) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_BY_EVENT_TYPE)) {
            ps.setString(1, tenantId);
            ps.setString(2, eventType);
            return Promise.of(mapResults(ps.executeQuery()));
        } catch (SQLException e) {
            log.error("findByEventType failed: {}", e.getMessage(), e);
            return Promise.ofException(e);
        }
    }

    @Override
    public Promise<List<AuditEvent>> findByTimeRange(String tenantId, Instant from, Instant to) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_BY_TIME_RANGE)) {
            ps.setString(1, tenantId);
            ps.setTimestamp(2, Timestamp.from(from));
            ps.setTimestamp(3, Timestamp.from(to));
            return Promise.of(mapResults(ps.executeQuery()));
        } catch (SQLException e) {
            log.error("findByTimeRange failed: {}", e.getMessage(), e);
            return Promise.ofException(e);
        }
    }

    @Override
    public Promise<Optional<AuditEvent>> findById(String tenantId, String eventId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_BY_ID)) {
            ps.setString(1, tenantId);
            ps.setLong(2, Long.parseLong(eventId));
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return Promise.of(Optional.of(mapRow(rs)));
            }
            return Promise.of(Optional.empty());
        } catch (SQLException | NumberFormatException e) {
            log.error("findById failed: {}", e.getMessage(), e);
            return Promise.ofException(new SQLException("findById failed", e));
        }
    }

    @Override
    public Promise<Long> countByTenantId(String tenantId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(COUNT_BY_TENANT)) {
            ps.setString(1, tenantId);
            ResultSet rs = ps.executeQuery();
            rs.next();
            return Promise.of(rs.getLong(1));
        } catch (SQLException e) {
            log.error("countByTenantId failed: {}", e.getMessage(), e);
            return Promise.ofException(e);
        }
    }

    @Override
    public Promise<List<AuditEvent>> search(String tenantId, AuditSearchCriteria criteria) {
        StringBuilder sql = new StringBuilder("SELECT * FROM audit_trail WHERE tenant_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(tenantId);

        if (criteria.resourceType() != null) {
            sql.append(" AND entity_type = ?");
            params.add(criteria.resourceType());
        }
        if (criteria.resourceId() != null) {
            sql.append(" AND entity_id = ?");
            params.add(criteria.resourceId());
        }
        if (criteria.principal() != null) {
            sql.append(" AND actor = ?");
            params.add(criteria.principal());
        }
        if (criteria.eventType() != null) {
            sql.append(" AND event_type = ?");
            params.add(criteria.eventType());
        }
        if (criteria.fromDate() != null) {
            sql.append(" AND \"timestamp\" >= ?");
            params.add(Timestamp.from(criteria.fromDate()));
        }
        if (criteria.toDate() != null) {
            sql.append(" AND \"timestamp\" <= ?");
            params.add(Timestamp.from(criteria.toDate()));
        }
        if (criteria.success() != null) {
            sql.append(" AND success = ?");
            params.add(criteria.success());
        }

        sql.append(" ORDER BY \"timestamp\" DESC LIMIT ? OFFSET ?");
        params.add(criteria.limit());
        params.add(criteria.offset());

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                Object param = params.get(i);
                if (param instanceof String s) ps.setString(i + 1, s);
                else if (param instanceof Timestamp ts) ps.setTimestamp(i + 1, ts);
                else if (param instanceof Boolean b) ps.setBoolean(i + 1, b);
                else if (param instanceof Integer n) ps.setInt(i + 1, n);
                else ps.setObject(i + 1, param);
            }
            return Promise.of(mapResults(ps.executeQuery()));
        } catch (SQLException e) {
            log.error("search failed: {}", e.getMessage(), e);
            return Promise.ofException(e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Internal Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private Promise<List<AuditEvent>> executeQuery(String sql, String tenantId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tenantId);
            return Promise.of(mapResults(ps.executeQuery()));
        } catch (SQLException e) {
            log.error("Query failed: {}", e.getMessage(), e);
            return Promise.ofException(e);
        }
    }

    private Promise<List<AuditEvent>> executeQueryWithPagination(String sql, String tenantId, int offset, int limit) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tenantId);
            ps.setInt(2, limit);
            ps.setInt(3, offset);
            return Promise.of(mapResults(ps.executeQuery()));
        } catch (SQLException e) {
            log.error("Paged query failed: {}", e.getMessage(), e);
            return Promise.ofException(e);
        }
    }

    private List<AuditEvent> mapResults(ResultSet rs) throws SQLException {
        List<AuditEvent> results = new ArrayList<>();
        while (rs.next()) {
            results.add(mapRow(rs));
        }
        return results;
    }

    private AuditEvent mapRow(ResultSet rs) throws SQLException {
        Map<String, Object> details = parseJson(rs.getString("details"));

        // Enrich details with columns not in AuditEvent's core fields
        String entityType = rs.getString("entity_type");
        if (entityType != null) details.putIfAbsent("entityType", entityType);
        String action = rs.getString("action");
        if (action != null) details.putIfAbsent("action", action);
        String actorType = rs.getString("actor_type");
        if (actorType != null) details.putIfAbsent("actorType", actorType);
        String correlationId = rs.getString("correlation_id");
        if (correlationId != null) details.putIfAbsent("correlationId", correlationId);
        String source = rs.getString("source");
        if (source != null) details.putIfAbsent("source", source);
        String ipAddress = rs.getString("ip_address");
        if (ipAddress != null) details.putIfAbsent("ipAddress", ipAddress);
        String errorMessage = rs.getString("error_message");
        if (errorMessage != null) details.putIfAbsent("errorMessage", errorMessage);

        // Map previous_state and new_state into details
        String prevState = rs.getString("previous_state");
        if (prevState != null) details.putIfAbsent("previousState", parseJson(prevState));
        String newState = rs.getString("new_state");
        if (newState != null) details.putIfAbsent("newState", parseJson(newState));

        Timestamp ts = rs.getTimestamp("timestamp");

        return AuditEvent.builder()
                .id(String.valueOf(rs.getLong("id")))
                .tenantId(rs.getString("tenant_id"))
                .eventType(rs.getString("event_type"))
                .timestamp(ts != null ? ts.toInstant() : Instant.now())
                .principal(rs.getString("actor"))
                .resourceType(rs.getString("entity_type"))
                .resourceId(rs.getString("entity_id"))
                .success(rs.getBoolean("success"))
                .details(details)
                .build();
    }

    private String extractTenantId(AuditEvent event) {
        if (event.getTenantId() != null) return event.getTenantId();
        Map<String, Object> details = event.getDetails();
        if (details != null && details.containsKey("tenantId")) {
            return String.valueOf(details.get("tenantId"));
        }
        return "default";
    }

    private String extractAction(AuditEvent event) {
        Map<String, Object> details = event.getDetails();
        if (details != null && details.containsKey("action")) {
            return String.valueOf(details.get("action"));
        }
        // Derive from eventType: e.g., "AGENT_CREATED" -> "CREATE"
        String type = event.getEventType();
        if (type != null) {
            if (type.contains("CREATE") || type.contains("REGISTER")) return "CREATE";
            if (type.contains("UPDATE") || type.contains("MODIFY")) return "UPDATE";
            if (type.contains("DELETE") || type.contains("REMOVE")) return "DELETE";
            if (type.contains("EXECUTE") || type.contains("RUN")) return "EXECUTE";
            if (type.contains("LOGIN")) return "LOGIN";
            if (type.contains("LOGOUT")) return "LOGOUT";
            if (type.contains("DEPLOY")) return "DEPLOY";
            if (type.contains("ACTIVATE")) return "ACTIVATE";
            if (type.contains("DEACTIVATE")) return "DEACTIVATE";
            if (type.contains("CONFIGURE")) return "CONFIGURE";
            if (type.contains("READ") || type.contains("ACCESS")) return "READ";
            if (type.contains("GRANT")) return "GRANT";
            if (type.contains("REVOKE")) return "REVOKE";
        }
        return "CREATE"; // safe default
    }

    private String extractDetail(AuditEvent event, String key, String defaultValue) {
        Map<String, Object> details = event.getDetails();
        if (details != null && details.containsKey(key)) {
            return String.valueOf(details.get(key));
        }
        return defaultValue;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMapDetail(AuditEvent event, String key) {
        Map<String, Object> details = event.getDetails();
        if (details != null && details.get(key) instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("JSON serialization failed: {}", e.getMessage());
            return "{}";
        }
    }

    private Map<String, Object> parseJson(String json) {
        if (json == null || json.isBlank()) return new HashMap<>();
        try {
            return new HashMap<>(objectMapper.readValue(json, MAP_TYPE));
        } catch (JsonProcessingException e) {
            log.warn("JSON parse failed: {}", e.getMessage());
            return new HashMap<>();
        }
    }
}
