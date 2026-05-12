package com.ghatana.kernel.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.kernel.observability.AuditTrailPersistence;
import com.ghatana.kernel.observability.AuditTrailService;
import com.ghatana.kernel.observability.DefaultAuditTrailService;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * PostgreSQL-backed {@link AuditTrailPersistence} adapter.
 *
 * <p>Persists canonical kernel audit events in a durable relational table so
 * restart and node replacement do not lose the audit hash-chain history.
 *
 * @doc.type class
 * @doc.purpose Durable PostgreSQL persistence for kernel audit trail events
 * @doc.layer platform
 * @doc.pattern Adapter
 */
public class PostgresAuditTrailPersistence implements AuditTrailPersistence {

    private static final String TABLE = "kernel_audit_events";

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    public PostgresAuditTrailPersistence(DataSource dataSource, ObjectMapper objectMapper) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource cannot be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
    }

    /** Creates the backing table if it does not yet exist. */
    public void ensureSchema() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                 CREATE TABLE IF NOT EXISTS kernel_audit_events (
                   event_id VARCHAR(128) PRIMARY KEY,
                   event_type VARCHAR(128) NOT NULL,
                   entity_id VARCHAR(256) NOT NULL,
                   user_id VARCHAR(256),
                   tenant_id VARCHAR(128),
                   action VARCHAR(256),
                   event_data TEXT,
                   event_timestamp BIGINT NOT NULL,
                   previous_hash VARCHAR(256),
                   event_hash VARCHAR(256) NOT NULL
                 )
                 """)) {
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to initialize kernel audit schema", exception);
        }
    }

    @Override
    public void persist(DefaultAuditTrailService.StoredAuditEvent event) {
        Objects.requireNonNull(event, "event cannot be null");

        String sql = """
            INSERT INTO kernel_audit_events (
              event_id, event_type, entity_id, user_id, tenant_id,
              action, event_data, event_timestamp, previous_hash, event_hash
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        AuditTrailService.AuditTrailEvent auditEvent = event.event();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, auditEvent.getEventId());
            statement.setString(2, auditEvent.getEventType());
            statement.setString(3, auditEvent.getEntityId());
            statement.setString(4, auditEvent.getUserId());
            statement.setString(5, auditEvent.getTenantId());
            statement.setString(6, auditEvent.getAction());
            statement.setString(7, writeJson(auditEvent.getData()));
            statement.setLong(8, auditEvent.getTimestamp());
            statement.setString(9, auditEvent.getPreviousHash());
            statement.setString(10, event.hash());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to persist kernel audit event", exception);
        }
    }

    @Override
    public List<DefaultAuditTrailService.StoredAuditEvent> loadAll() {
        String sql = """
            SELECT event_id, event_type, entity_id, user_id, tenant_id,
                   action, event_data, event_timestamp, previous_hash, event_hash
            FROM kernel_audit_events
            ORDER BY event_timestamp ASC
            """;

        List<DefaultAuditTrailService.StoredAuditEvent> events = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                AuditTrailService.AuditTrailEvent event = AuditTrailService.AuditTrailEvent.builder()
                    .eventId(resultSet.getString("event_id"))
                    .eventType(resultSet.getString("event_type"))
                    .entityId(resultSet.getString("entity_id"))
                    .userId(resultSet.getString("user_id"))
                    .tenantId(resultSet.getString("tenant_id"))
                    .action(resultSet.getString("action"))
                    .data(readJsonMap(resultSet.getString("event_data")))
                    .timestamp(resultSet.getLong("event_timestamp"))
                    .previousHash(resultSet.getString("previous_hash"))
                    .build();
                events.add(new DefaultAuditTrailService.StoredAuditEvent(event,
                    resultSet.getString("event_hash")));
            }
            return events;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load kernel audit events", exception);
        }
    }

    private String writeJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize audit event payload", exception);
        }
    }

    private Map<String, Object> readJsonMap(String json) {
        try {
            if (json == null || json.isBlank()) {
                return Map.of();
            }
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to deserialize audit event payload", exception);
        }
    }
}
