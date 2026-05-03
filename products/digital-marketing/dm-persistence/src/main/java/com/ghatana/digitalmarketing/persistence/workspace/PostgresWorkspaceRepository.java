package com.ghatana.digitalmarketing.persistence.workspace;

import com.ghatana.digitalmarketing.application.workspace.WorkspaceRepository;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.workspace.Workspace;
import com.ghatana.digitalmarketing.domain.workspace.WorkspaceStatus;
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
 * Production PostgreSQL adapter for {@link WorkspaceRepository}.
 *
 * <p>Wraps all blocking JDBC I/O in {@code Promise.ofBlocking()} to remain event-loop safe.
 * Uses upsert semantics for idempotent saves. Enforces tenant isolation at query level.</p>
 *
 * @doc.type class
 * @doc.purpose Production JDBC adapter for DMOS workspace persistence (DMOS-P0-002)
 * @doc.layer product
 * @doc.pattern Adapter, Repository
 */
public final class PostgresWorkspaceRepository implements WorkspaceRepository {

    private static final Logger LOG = LoggerFactory.getLogger(PostgresWorkspaceRepository.class);

    private static final String UPSERT_SQL =
        "INSERT INTO dmos_workspaces (id, tenant_id, name, description, status, created_at, updated_at, created_by) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
        "ON CONFLICT (id) DO UPDATE SET " +
        "  name = EXCLUDED.name, description = EXCLUDED.description, status = EXCLUDED.status, " +
        "  updated_at = EXCLUDED.updated_at";

    private static final String SELECT_BY_ID_SQL =
        "SELECT id, tenant_id, name, description, status, created_at, updated_at, created_by " +
        "FROM dmos_workspaces WHERE id = ? AND tenant_id = ?";

    private static final String SELECT_BY_TENANT_SQL =
        "SELECT id, tenant_id, name, description, status, created_at, updated_at, created_by " +
        "FROM dmos_workspaces WHERE tenant_id = ? ORDER BY created_at";

    private final DataSource dataSource;
    private final Executor executor;

    public PostgresWorkspaceRepository(DataSource dataSource, Executor executor) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    @Override
    public Promise<Workspace> save(Workspace workspace) {
        Objects.requireNonNull(workspace, "workspace must not be null");
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(UPSERT_SQL)) {
                stmt.setString(1, workspace.getId().getValue());
                stmt.setString(2, workspace.getTenantId().getValue());
                stmt.setString(3, workspace.getName());
                stmt.setString(4, workspace.getDescription());
                stmt.setString(5, workspace.getStatus().name());
                stmt.setTimestamp(6, Timestamp.from(workspace.getCreatedAt()));
                stmt.setTimestamp(7, Timestamp.from(workspace.getUpdatedAt()));
                stmt.setString(8, workspace.getCreatedBy());
                stmt.executeUpdate();
                LOG.info("[DMOS-PERSIST] workspace upserted: id={} tenant={} status={}",
                    workspace.getId().getValue(), workspace.getTenantId().getValue(), workspace.getStatus());
                return workspace;
            } catch (SQLException e) {
                LOG.error("[DMOS-PERSIST] failed to save workspace id={}: {}",
                    workspace.getId().getValue(), e.getMessage(), e);
                throw new DmPersistenceException("Failed to save workspace: " + workspace.getId().getValue(), e);
            }
        });
    }

    @Override
    public Promise<Optional<Workspace>> findById(DmTenantId tenantId, DmWorkspaceId workspaceId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(SELECT_BY_ID_SQL)) {
                stmt.setString(1, workspaceId.getValue());
                stmt.setString(2, tenantId.getValue());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRow(rs));
                    }
                    return Optional.empty();
                }
            } catch (SQLException e) {
                LOG.error("[DMOS-PERSIST] failed to find workspace id={}: {}",
                    workspaceId.getValue(), e.getMessage(), e);
                throw new DmPersistenceException("Failed to find workspace: " + workspaceId.getValue(), e);
            }
        });
    }

    @Override
    public Promise<List<Workspace>> listByTenant(DmTenantId tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(SELECT_BY_TENANT_SQL)) {
                stmt.setString(1, tenantId.getValue());
                try (ResultSet rs = stmt.executeQuery()) {
                    List<Workspace> result = new ArrayList<>();
                    while (rs.next()) {
                        result.add(mapRow(rs));
                    }
                    return result;
                }
            } catch (SQLException e) {
                LOG.error("[DMOS-PERSIST] failed to list workspaces for tenant={}: {}",
                    tenantId.getValue(), e.getMessage(), e);
                throw new DmPersistenceException("Failed to list workspaces for tenant: " + tenantId.getValue(), e);
            }
        });
    }

    private static Workspace mapRow(ResultSet rs) throws SQLException {
        Instant createdAt = rs.getTimestamp("created_at").toInstant();
        Instant updatedAt = rs.getTimestamp("updated_at").toInstant();
        return Workspace.builder()
            .id(DmWorkspaceId.of(rs.getString("id")))
            .tenantId(DmTenantId.of(rs.getString("tenant_id")))
            .name(rs.getString("name"))
            .description(rs.getString("description"))
            .status(WorkspaceStatus.valueOf(rs.getString("status")))
            .createdAt(createdAt)
            .updatedAt(updatedAt)
            .createdBy(rs.getString("created_by"))
            .build();
    }
}
