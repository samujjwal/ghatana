package com.ghatana.digitalmarketing.persistence.campaign;

import com.ghatana.digitalmarketing.application.campaign.CampaignRepository;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.campaign.Campaign;
import com.ghatana.digitalmarketing.domain.campaign.CampaignStatus;
import com.ghatana.digitalmarketing.domain.campaign.CampaignType;
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
 * Production PostgreSQL adapter for {@link CampaignRepository}.
 *
 * <p>Wraps all blocking JDBC I/O in {@code Promise.ofBlocking()} to remain event-loop safe.
 * Uses upsert semantics (INSERT … ON CONFLICT DO UPDATE) for idempotent saves.</p>
 *
 * @doc.type class
 * @doc.purpose Production JDBC adapter for DMOS campaign persistence (DMOS-P0-001)
 * @doc.layer product
 * @doc.pattern Adapter, Repository
 */
public final class PostgresCampaignRepository implements CampaignRepository {

    private static final Logger LOG = LoggerFactory.getLogger(PostgresCampaignRepository.class);

    private static final int MAX_PAGE_SIZE = 100;

    private static final String UPSERT_SQL =
        "INSERT INTO dmos_campaigns (id, workspace_id, tenant_id, name, status, type, created_at, updated_at, created_by) " +
        "SELECT ?, ?, ws.tenant_id, ?, ?, ?, ?, ?, ? " +
        "FROM dmos_workspaces ws WHERE ws.id = ? " +
        "ON CONFLICT (id, workspace_id) DO UPDATE SET " +
        "  tenant_id = EXCLUDED.tenant_id, name = EXCLUDED.name, status = EXCLUDED.status, type = EXCLUDED.type, " +
        "  updated_at = EXCLUDED.updated_at";

    private static final String SELECT_BY_ID_SQL =
        "SELECT id, workspace_id, name, status, type, created_at, updated_at, created_by " +
        "FROM dmos_campaigns WHERE id = ? AND workspace_id = ? " +
        "AND tenant_id = (SELECT tenant_id FROM dmos_workspaces WHERE id = ?)";

    private static final String LIST_BY_WORKSPACE_SQL =
        "SELECT id, workspace_id, name, status, type, created_at, updated_at, created_by " +
        "FROM dmos_campaigns WHERE workspace_id = ? " +
        "AND tenant_id = (SELECT tenant_id FROM dmos_workspaces WHERE id = ?) " +
        "ORDER BY created_at DESC, id DESC " +
        "LIMIT ? OFFSET ?";

    private static final String COUNT_BY_WORKSPACE_SQL =
        "SELECT COUNT(*) FROM dmos_campaigns WHERE workspace_id = ? " +
        "AND tenant_id = (SELECT tenant_id FROM dmos_workspaces WHERE id = ?)";

    private final DataSource dataSource;
    private final Executor executor;

    public PostgresCampaignRepository(DataSource dataSource, Executor executor) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    @Override
    public Promise<Campaign> save(Campaign campaign) {
        Objects.requireNonNull(campaign, "campaign must not be null");
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(UPSERT_SQL)) {
                stmt.setString(1, campaign.getId());
                stmt.setString(2, campaign.getWorkspaceId().getValue());
                stmt.setString(3, campaign.getName());
                stmt.setString(4, campaign.getStatus().name());
                stmt.setString(5, campaign.getType().name());
                stmt.setTimestamp(6, Timestamp.from(campaign.getCreatedAt()));
                stmt.setTimestamp(7, Timestamp.from(campaign.getUpdatedAt()));
                stmt.setString(8, campaign.getCreatedBy());
                stmt.setString(9, campaign.getWorkspaceId().getValue());
                int rowsUpdated = stmt.executeUpdate();
                if (rowsUpdated == 0) {
                    throw new DmPersistenceException(
                        "Workspace not found for campaign save: " + campaign.getWorkspaceId().getValue(),
                        new IllegalStateException("Workspace missing during campaign upsert"));
                }
                LOG.info("[DMOS-PERSIST] campaign upserted: id={} workspace={} status={}",
                    campaign.getId(), campaign.getWorkspaceId().getValue(), campaign.getStatus());
                return campaign;
            } catch (SQLException e) {
                LOG.error("[DMOS-PERSIST] failed to save campaign id={}: {}", campaign.getId(), e.getMessage(), e);
                throw new DmPersistenceException("Failed to save campaign: " + campaign.getId(), e);
            }
        });
    }

    @Override
    public Promise<Optional<Campaign>> findById(DmWorkspaceId workspaceId, String campaignId) {
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        Objects.requireNonNull(campaignId, "campaignId must not be null");
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(SELECT_BY_ID_SQL)) {
                stmt.setString(1, campaignId);
                stmt.setString(2, workspaceId.getValue());
                stmt.setString(3, workspaceId.getValue());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRow(rs));
                    }
                    return Optional.empty();
                }
            } catch (SQLException e) {
                LOG.error("[DMOS-PERSIST] failed to find campaign id={}: {}", campaignId, e.getMessage(), e);
                throw new DmPersistenceException("Failed to find campaign: " + campaignId, e);
            }
        });
    }

    @Override
    public Promise<List<Campaign>> listByWorkspace(DmWorkspaceId workspaceId, int limit, int offset) {
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        int boundedLimit = Math.min(Math.max(limit, 1), MAX_PAGE_SIZE);
        int boundedOffset = Math.max(offset, 0);

        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(LIST_BY_WORKSPACE_SQL)) {
                stmt.setString(1, workspaceId.getValue());
                stmt.setString(2, workspaceId.getValue());
                stmt.setInt(3, boundedLimit);
                stmt.setInt(4, boundedOffset);
                try (ResultSet rs = stmt.executeQuery()) {
                    List<Campaign> results = new ArrayList<>();
                    while (rs.next()) {
                        results.add(mapRow(rs));
                    }
                    LOG.info("[DMOS-PERSIST] Listed {} campaigns for workspace {}",
                        results.size(), workspaceId.getValue());
                    return results;
                }
            } catch (SQLException e) {
                LOG.error("[DMOS-PERSIST] Failed to list campaigns for workspace {}: {}",
                    workspaceId.getValue(), e.getMessage(), e);
                throw new DmPersistenceException("Failed to list campaigns for workspace: " + workspaceId.getValue(), e);
            }
        });
    }

    @Override
    public Promise<Long> countByWorkspace(DmWorkspaceId workspaceId) {
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");

        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(COUNT_BY_WORKSPACE_SQL)) {
                stmt.setString(1, workspaceId.getValue());
                stmt.setString(2, workspaceId.getValue());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getLong(1);
                    }
                    return 0L;
                }
            } catch (SQLException e) {
                LOG.error("[DMOS-PERSIST] Failed to count campaigns for workspace {}: {}",
                    workspaceId.getValue(), e.getMessage(), e);
                throw new DmPersistenceException("Failed to count campaigns for workspace: " + workspaceId.getValue(), e);
            }
        });
    }

    private static Campaign mapRow(ResultSet rs) throws SQLException {
        Instant createdAt = rs.getTimestamp("created_at").toInstant();
        Instant updatedAt = rs.getTimestamp("updated_at").toInstant();
        return Campaign.builder()
            .id(rs.getString("id"))
            .workspaceId(DmWorkspaceId.of(rs.getString("workspace_id")))
            .name(rs.getString("name"))
            .status(CampaignStatus.valueOf(rs.getString("status")))
            .type(CampaignType.valueOf(rs.getString("type")))
            .createdAt(createdAt)
            .updatedAt(updatedAt)
            .createdBy(rs.getString("created_by"))
            .build();
    }
}
