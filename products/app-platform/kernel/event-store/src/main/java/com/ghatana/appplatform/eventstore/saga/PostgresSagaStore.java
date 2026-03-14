package com.ghatana.appplatform.eventstore.saga;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC-backed saga store using the {@code saga_definitions} and {@code saga_instances}
 * tables created by V007__saga_tables.sql.
 *
 * <p>Definition serialization is minimal (sagaType + version + JSON steps).
 * Full definition management should go through a richer registry if needed.
 *
 * @doc.type class
 * @doc.purpose PostgreSQL saga store adapter (STORY-K05-020)
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class PostgresSagaStore implements SagaStore {

    private final DataSource dataSource;

    public PostgresSagaStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // ── Definitions ──────────────────────────────────────────────────────────

    @Override
    public void saveDefinition(SagaDefinition definition) {
        String sql = """
            INSERT INTO saga_definitions (saga_type, version, description, steps_json)
            VALUES (?, ?, ?, ?::jsonb)
            ON CONFLICT (saga_type, version) DO NOTHING
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, definition.sagaType());
            ps.setInt(2, definition.version());
            ps.setString(3, definition.description());
            ps.setString(4, stepsToJson(definition));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save saga definition: " + definition.sagaType(), e);
        }
    }

    @Override
    public Optional<SagaDefinition> getDefinition(String sagaType, int version) {
        String sql = """
            SELECT saga_type, version, description, steps_json
              FROM saga_definitions WHERE saga_type = ? AND version = ?
            """;
        return querySingleDefinition(sql, sagaType, version);
    }

    @Override
    public Optional<SagaDefinition> getLatestDefinition(String sagaType) {
        String sql = """
            SELECT saga_type, version, description, steps_json
              FROM saga_definitions WHERE saga_type = ?
              ORDER BY version DESC LIMIT 1
            """;
        return querySingleDefinition(sql, sagaType, null);
    }

    // ── Instances ─────────────────────────────────────────────────────────────

    @Override
    public void saveInstance(SagaInstance instance) {
        String sql = """
            INSERT INTO saga_instances
              (saga_id, saga_type, saga_version, tenant_id, correlation_id,
               saga_state, current_step, retry_count, last_error, started_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?::saga_state, ?, ?, ?, ?, ?)
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindInstance(ps, instance);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save saga instance: " + instance.sagaId(), e);
        }
    }

    @Override
    public void updateInstance(SagaInstance instance) {
        String sql = """
            UPDATE saga_instances
               SET saga_state = ?::saga_state,
                   current_step = ?,
                   retry_count = ?,
                   last_error = ?,
                   updated_at = ?
             WHERE saga_id = ?
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, instance.state().name());
            ps.setInt(2, instance.currentStepOrder());
            ps.setInt(3, instance.retryCount());
            ps.setString(4, instance.lastError());
            ps.setTimestamp(5, Timestamp.from(instance.updatedAt()));
            ps.setString(6, instance.sagaId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update saga instance: " + instance.sagaId(), e);
        }
    }

    @Override
    public Optional<SagaInstance> findInstance(String sagaId) {
        String sql = """
            SELECT saga_id, saga_type, saga_version, tenant_id, correlation_id,
                   saga_state, current_step, retry_count, last_error, started_at, updated_at
              FROM saga_instances WHERE saga_id = ?
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sagaId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapInstance(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find saga instance: " + sagaId, e);
        }
    }

    @Override
    public List<SagaInstance> findActiveByCorrelation(String tenantId, String correlationId) {
        String sql = """
            SELECT saga_id, saga_type, saga_version, tenant_id, correlation_id,
                   saga_state, current_step, retry_count, last_error, started_at, updated_at
              FROM saga_instances
             WHERE tenant_id = ? AND correlation_id = ?
               AND saga_state NOT IN ('COMPLETED', 'COMPENSATED', 'FAILED')
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tenantId);
            ps.setString(2, correlationId);
            List<SagaInstance> results = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(mapInstance(rs));
            }
            return results;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find active sagas for correlationId=" + correlationId, e);
        }
    }

    // ── Private Helpers ───────────────────────────────────────────────────────

    private Optional<SagaDefinition> querySingleDefinition(String sql, String sagaType, Integer version) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sagaType);
            if (version != null) ps.setInt(2, version);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                // Definitions stored with minimal steps metadata; full steps require caller injection
                return Optional.of(new SagaDefinition(
                    rs.getString("saga_type"),
                    rs.getInt("version"),
                    rs.getString("description"),
                    List.of() // steps rehydration is handled by the definition registry layer
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query saga definition: " + sagaType, e);
        }
    }

    private void bindInstance(PreparedStatement ps, SagaInstance i) throws SQLException {
        ps.setString(1, i.sagaId());
        ps.setString(2, i.sagaType());
        ps.setInt(3, i.sagaVersion());
        ps.setString(4, i.tenantId());
        ps.setString(5, i.correlationId());
        ps.setString(6, i.state().name());
        ps.setInt(7, i.currentStepOrder());
        ps.setInt(8, i.retryCount());
        ps.setString(9, i.lastError());
        ps.setTimestamp(10, Timestamp.from(i.startedAt()));
        ps.setTimestamp(11, Timestamp.from(i.updatedAt()));
    }

    private SagaInstance mapInstance(ResultSet rs) throws SQLException {
        return new SagaInstance(
            rs.getString("saga_id"),
            rs.getString("saga_type"),
            rs.getInt("saga_version"),
            rs.getString("tenant_id"),
            rs.getString("correlation_id"),
            SagaState.valueOf(rs.getString("saga_state")),
            rs.getInt("current_step"),
            rs.getInt("retry_count"),
            rs.getString("last_error"),
            rs.getTimestamp("started_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
        );
    }

    private String stepsToJson(SagaDefinition definition) {
        // Minimal JSON conversion without pulling in a JSON library — steps persisted as ordered array
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < definition.steps().size(); i++) {
            SagaStep s = definition.steps().get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"stepName\":\"").append(s.stepName())
              .append("\",\"stepOrder\":").append(s.stepOrder())
              .append(",\"actionEventType\":\"").append(s.actionEventType())
              .append("\",\"completionEventType\":\"").append(s.completionEventType())
              .append("\",\"maxRetries\":").append(s.maxRetries())
              .append(",\"mandatory\":").append(s.mandatory()).append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    // ── STORY-K05-019: Timeout finder ─────────────────────────────────────────

    @Override
    public List<SagaInstance> findTimedOutInstances(Instant cutoff) {
        String sql = """
            SELECT saga_id, saga_type, saga_version, tenant_id, correlation_id,
                   saga_state, current_step, retry_count, last_error, started_at, updated_at
              FROM saga_instances
             WHERE saga_state = 'STEP_PENDING'
               AND updated_at < ?
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.from(cutoff));
            List<SagaInstance> results = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(mapInstance(rs));
            }
            return results;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query timed-out saga instances", e);
        }
    }
}
