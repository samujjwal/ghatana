package com.ghatana.digitalmarketing.persistence.connector;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.digitalmarketing.application.connector.DmConnectorRepository;
import com.ghatana.digitalmarketing.domain.connector.DmConnectorConfig;
import com.ghatana.digitalmarketing.domain.connector.DmConnectorStatus;
import com.ghatana.digitalmarketing.domain.connector.DmConnectorType;
import com.ghatana.digitalmarketing.persistence.campaign.DmPersistenceException;
import io.activej.promise.Promise;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * PostgreSQL adapter for {@link DmConnectorRepository}.
 *
 * @doc.type class
 * @doc.purpose Durable repository for connector configurations in production
 * @doc.layer product
 * @doc.pattern Repository
 */
public final class PostgresDmConnectorRepository implements DmConnectorRepository {

    private static final TypeReference<Map<String, String>> SETTINGS_TYPE = new TypeReference<>() {};

    private final DataSource dataSource;
    private final Executor executor;
    private final ObjectMapper objectMapper;

    public PostgresDmConnectorRepository(DataSource dataSource, Executor executor) {
        this.dataSource = dataSource;
        this.executor = executor;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public Promise<DmConnectorConfig> save(DmConnectorConfig connector) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                INSERT INTO dmos_connector_configs (
                    id, tenant_id, workspace_id, name, connector_type, status, settings_json,
                    external_account_id, failure_reason, created_at, updated_at, last_health_check_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    tenant_id = EXCLUDED.tenant_id,
                    workspace_id = EXCLUDED.workspace_id,
                    name = EXCLUDED.name,
                    connector_type = EXCLUDED.connector_type,
                    status = EXCLUDED.status,
                    settings_json = EXCLUDED.settings_json,
                    external_account_id = EXCLUDED.external_account_id,
                    failure_reason = EXCLUDED.failure_reason,
                    updated_at = EXCLUDED.updated_at,
                    last_health_check_at = EXCLUDED.last_health_check_at
                """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, connector.getId());
                stmt.setString(2, connector.getTenantId());
                stmt.setString(3, connector.getWorkspaceId());
                stmt.setString(4, connector.getName());
                stmt.setString(5, connector.getConnectorType().name());
                stmt.setString(6, connector.getStatus().name());
                stmt.setString(7, objectMapper.writeValueAsString(connector.getSettings()));
                stmt.setString(8, connector.getExternalAccountId());
                stmt.setString(9, connector.getFailureReason());
                stmt.setTimestamp(10, Timestamp.from(connector.getCreatedAt()));
                stmt.setTimestamp(11, Timestamp.from(connector.getUpdatedAt()));
                stmt.setTimestamp(12, connector.getLastHealthCheckAt() != null
                    ? Timestamp.from(connector.getLastHealthCheckAt())
                    : null);
                stmt.executeUpdate();
                return connector;
            } catch (Exception e) {
                throw new DmPersistenceException("Failed to save connector config " + connector.getId(), e);
            }
        });
    }

    @Override
    public Promise<Optional<DmConnectorConfig>> findById(String id) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                SELECT id, tenant_id, workspace_id, name, connector_type, status, settings_json,
                       external_account_id, failure_reason, created_at, updated_at, last_health_check_at
                FROM dmos_connector_configs
                WHERE id = ?
                """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRow(rs));
                    }
                    return Optional.empty();
                }
            } catch (Exception e) {
                throw new DmPersistenceException("Failed to find connector config " + id, e);
            }
        });
    }

    @Override
    public Promise<List<DmConnectorConfig>> findByType(String tenantId, DmConnectorType type, int limit) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                SELECT id, tenant_id, workspace_id, name, connector_type, status, settings_json,
                       external_account_id, failure_reason, created_at, updated_at, last_health_check_at
                FROM dmos_connector_configs
                WHERE tenant_id = ? AND connector_type = ?
                ORDER BY updated_at DESC
                LIMIT ?
                """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, tenantId);
                stmt.setString(2, type.name());
                stmt.setInt(3, Math.max(1, limit));
                return readList(stmt);
            } catch (Exception e) {
                throw new DmPersistenceException("Failed to list connector configs by type", e);
            }
        });
    }

    @Override
    public Promise<List<DmConnectorConfig>> findByStatus(String tenantId, DmConnectorStatus status, int limit) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                SELECT id, tenant_id, workspace_id, name, connector_type, status, settings_json,
                       external_account_id, failure_reason, created_at, updated_at, last_health_check_at
                FROM dmos_connector_configs
                WHERE tenant_id = ? AND status = ?
                ORDER BY updated_at DESC
                LIMIT ?
                """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, tenantId);
                stmt.setString(2, status.name());
                stmt.setInt(3, Math.max(1, limit));
                return readList(stmt);
            } catch (Exception e) {
                throw new DmPersistenceException("Failed to list connector configs by status", e);
            }
        });
    }

    @Override
    public Promise<DmConnectorConfig> update(DmConnectorConfig connector) {
        return save(connector);
    }

    @Override
    public Promise<Long> countByStatus(String tenantId, DmConnectorStatus status) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT COUNT(*) FROM dmos_connector_configs WHERE tenant_id = ? AND status = ?";
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, tenantId);
                stmt.setString(2, status.name());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getLong(1);
                    }
                    return 0L;
                }
            } catch (SQLException e) {
                throw new DmPersistenceException("Failed to count connector configs by status", e);
            }
        });
    }

    private List<DmConnectorConfig> readList(PreparedStatement stmt) throws Exception {
        try (ResultSet rs = stmt.executeQuery()) {
            List<DmConnectorConfig> result = new ArrayList<>();
            while (rs.next()) {
                result.add(mapRow(rs));
            }
            return result;
        }
    }

    private DmConnectorConfig mapRow(ResultSet rs) throws Exception {
        String settingsJson = rs.getString("settings_json");
        Map<String, String> settings = settingsJson == null || settingsJson.isBlank()
            ? Map.of()
            : objectMapper.readValue(settingsJson, SETTINGS_TYPE);

        Timestamp createdAt = rs.getTimestamp("created_at");
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        Timestamp lastHealthCheckAt = rs.getTimestamp("last_health_check_at");

        return DmConnectorConfig.builder()
            .id(rs.getString("id"))
            .tenantId(rs.getString("tenant_id"))
            .workspaceId(rs.getString("workspace_id"))
            .name(rs.getString("name"))
            .connectorType(DmConnectorType.valueOf(rs.getString("connector_type")))
            .status(DmConnectorStatus.valueOf(rs.getString("status")))
            .settings(settings)
            .externalAccountId(rs.getString("external_account_id"))
            .failureReason(rs.getString("failure_reason"))
            .createdAt(createdAt.toInstant())
            .updatedAt(updatedAt.toInstant())
            .lastHealthCheckAt(lastHealthCheckAt != null ? lastHealthCheckAt.toInstant() : null)
            .build();
    }
}
