package com.ghatana.digitalmarketing.persistence.funnel;

import com.ghatana.digitalmarketing.application.funnel.DemoWorkspaceRepository;
import com.ghatana.digitalmarketing.domain.funnel.DemoWorkspace;
import com.ghatana.digitalmarketing.domain.funnel.DemoWorkspaceStatus;
import com.ghatana.digitalmarketing.persistence.campaign.DmPersistenceException;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * Production PostgreSQL adapter for {@link DemoWorkspaceRepository}.
 *
 * <p>Wraps all blocking JDBC I/O in {@code Promise.ofBlocking()} to remain event-loop safe.
 * Uses upsert semantics for idempotent saves. Enforces tenant isolation at query level.</p>
 *
 * @doc.type class
 * @doc.purpose Production JDBC adapter for demo workspace persistence (P3-001)
 * @doc.layer product
 * @doc.pattern Adapter, Repository
 */
public final class PostgresDemoWorkspaceRepository implements DemoWorkspaceRepository {

    private static final Logger LOG = LoggerFactory.getLogger(PostgresDemoWorkspaceRepository.class);

    private static final String UPSERT_SQL =
        "INSERT INTO dmos_demo_workspaces (id, tenant_id, workspace_id, lead_id, template_id, status, template_config, created_at, activated_at, expires_at, deactivation_reason) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?) " +
        "ON CONFLICT (id) DO UPDATE SET " +
        "  status = EXCLUDED.status, activated_at = EXCLUDED.activated_at, expires_at = EXCLUDED.expires_at, " +
        "  deactivation_reason = EXCLUDED.deactivation_reason";

    private static final String SELECT_BY_ID_SQL =
        "SELECT id, tenant_id, workspace_id, lead_id, template_id, status, template_config, created_at, activated_at, expires_at, deactivation_reason " +
        "FROM dmos_demo_workspaces WHERE id = ? AND tenant_id = ?";

    private static final String SELECT_BY_LEAD_ID_SQL =
        "SELECT id, tenant_id, workspace_id, lead_id, template_id, status, template_config, created_at, activated_at, expires_at, deactivation_reason " +
        "FROM dmos_demo_workspaces WHERE lead_id = ? AND tenant_id = ?";

    private static final String SELECT_BY_TENANT_SQL =
        "SELECT id, tenant_id, workspace_id, lead_id, template_id, status, template_config, created_at, activated_at, expires_at, deactivation_reason " +
        "FROM dmos_demo_workspaces WHERE tenant_id = ? ORDER BY created_at";

    private static final String DELETE_SQL =
        "DELETE FROM dmos_demo_workspaces WHERE id = ? AND tenant_id = ?";

    private final DataSource dataSource;
    private final Executor executor;

    public PostgresDemoWorkspaceRepository(DataSource dataSource, Executor executor) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    @Override
    public Promise<DemoWorkspace> save(DemoWorkspace workspace) {
        Objects.requireNonNull(workspace, "workspace must not be null");
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(UPSERT_SQL)) {
                stmt.setString(1, workspace.getId());
                stmt.setString(2, workspace.getTenantId());
                stmt.setString(3, workspace.getWorkspaceId());
                stmt.setString(4, workspace.getLeadId());
                stmt.setString(5, workspace.getTemplateId());
                stmt.setString(6, workspace.getStatus().name());
                stmt.setString(7, workspace.getTemplateConfig() != null ? workspace.getTemplateConfig().toString() : null);
                stmt.setTimestamp(8, Timestamp.from(workspace.getCreatedAt()));
                stmt.setTimestamp(9, workspace.getActivatedAt() != null ? Timestamp.from(workspace.getActivatedAt()) : null);
                stmt.setTimestamp(10, workspace.getExpiresAt() != null ? Timestamp.from(workspace.getExpiresAt()) : null);
                stmt.setString(11, workspace.getDeactivationReason());
                stmt.executeUpdate();
                LOG.info("[DMOS-PERSIST] demo workspace upserted: id={} tenant={} status={}",
                    workspace.getId(), workspace.getTenantId(), workspace.getStatus());
                return workspace;
            } catch (SQLException e) {
                LOG.error("[DMOS-PERSIST] failed to save demo workspace id={}: {}",
                    workspace.getId(), e.getMessage(), e);
                throw new DmPersistenceException("Failed to save demo workspace: " + workspace.getId(), e);
            }
        });
    }

    @Override
    public Promise<Optional<DemoWorkspace>> findById(String id) {
        Objects.requireNonNull(id, "id must not be null");
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(SELECT_BY_ID_SQL)) {
                stmt.setString(1, id);
                stmt.setString(2, extractTenantId(id));
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRow(rs));
                    }
                    return Optional.empty();
                }
            } catch (SQLException e) {
                LOG.error("[DMOS-PERSIST] failed to find demo workspace id={}: {}", id, e.getMessage(), e);
                throw new DmPersistenceException("Failed to find demo workspace: " + id, e);
            }
        });
    }

    @Override
    public Promise<List<DemoWorkspace>> findByLeadId(String leadId) {
        Objects.requireNonNull(leadId, "leadId must not be null");
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(SELECT_BY_LEAD_ID_SQL)) {
                stmt.setString(1, leadId);
                stmt.setString(2, extractTenantId(leadId));
                try (ResultSet rs = stmt.executeQuery()) {
                    List<DemoWorkspace> result = new ArrayList<>();
                    while (rs.next()) {
                        result.add(mapRow(rs));
                    }
                    return result;
                }
            } catch (SQLException e) {
                LOG.error("[DMOS-PERSIST] failed to find demo workspace by lead_id={}: {}", leadId, e.getMessage(), e);
                throw new DmPersistenceException("Failed to find demo workspace by lead_id: " + leadId, e);
            }
        });
    }

    @Override
    public Promise<List<DemoWorkspace>> findByTenantId(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(SELECT_BY_TENANT_SQL)) {
                stmt.setString(1, tenantId);
                try (ResultSet rs = stmt.executeQuery()) {
                    List<DemoWorkspace> result = new ArrayList<>();
                    while (rs.next()) {
                        result.add(mapRow(rs));
                    }
                    return result;
                }
            } catch (SQLException e) {
                LOG.error("[DMOS-PERSIST] failed to list demo workspaces for tenant={}: {}", tenantId, e.getMessage(), e);
                throw new DmPersistenceException("Failed to list demo workspaces for tenant: " + tenantId, e);
            }
        });
    }

    @Override
    public Promise<List<DemoWorkspace>> listByTenant(String tenantId) {
        return findByTenantId(tenantId);
    }

    @Override
    public Promise<Void> delete(String id) {
        Objects.requireNonNull(id, "id must not be null");
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(DELETE_SQL)) {
                stmt.setString(1, id);
                stmt.setString(2, extractTenantId(id));
                int rows = stmt.executeUpdate();
                LOG.info("[DMOS-PERSIST] demo workspace deleted: id={} rows={}", id, rows);
                return null;
            } catch (SQLException e) {
                LOG.error("[DMOS-PERSIST] failed to delete demo workspace id={}: {}", id, e.getMessage(), e);
                throw new DmPersistenceException("Failed to delete demo workspace: " + id, e);
            }
        });
    }

    private static String extractTenantId(String id) {
        return id.substring(0, id.indexOf('-'));
    }

    private static DemoWorkspace mapRow(ResultSet rs) throws SQLException {
        Instant createdAt = rs.getTimestamp("created_at").toInstant();
        Timestamp activatedAtTs = rs.getTimestamp("activated_at");
        Instant activatedAt = activatedAtTs != null ? activatedAtTs.toInstant() : null;
        Timestamp expiresAtTs = rs.getTimestamp("expires_at");
        Instant expiresAt = expiresAtTs != null ? expiresAtTs.toInstant() : null;
        String templateConfigJson = rs.getString("template_config");
        
        return DemoWorkspace.builder()
            .id(rs.getString("id"))
            .tenantId(rs.getString("tenant_id"))
            .workspaceId(rs.getString("workspace_id"))
            .leadId(rs.getString("lead_id"))
            .templateId(rs.getString("template_id"))
            .status(DemoWorkspaceStatus.valueOf(rs.getString("status")))
            .templateConfig(templateConfigJson != null ? parseJsonMap(templateConfigJson) : null)
            .createdAt(createdAt)
            .activatedAt(activatedAt)
            .expiresAt(expiresAt)
            .deactivationReason(rs.getString("deactivation_reason"))
            .build();
    }

    private static java.util.Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {});
        } catch (Exception e) {
            LOG.warn("[DMOS-PERSIST] failed to parse template_config json: {}", e.getMessage());
            return null;
        }
    }
}
