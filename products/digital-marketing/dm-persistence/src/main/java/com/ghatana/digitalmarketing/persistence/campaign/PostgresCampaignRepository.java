package com.ghatana.digitalmarketing.persistence.campaign;

import com.ghatana.digitalmarketing.application.campaign.CampaignRepository;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
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
        "INSERT INTO dmos_campaigns (id, workspace_id, tenant_id, name, status, type, objective, budget_cents, start_date, end_date, audience, landing_page_url, created_at, updated_at, created_by) " +
        "SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ? " +
        "FROM dmos_workspaces ws WHERE ws.id = ? AND ws.tenant_id = ? " +
        "ON CONFLICT (id, workspace_id) DO UPDATE SET " +
        "  tenant_id = EXCLUDED.tenant_id, name = EXCLUDED.name, status = EXCLUDED.status, type = EXCLUDED.type, " +
        "  objective = EXCLUDED.objective, budget_cents = EXCLUDED.budget_cents, start_date = EXCLUDED.start_date, " +
        "  end_date = EXCLUDED.end_date, audience = EXCLUDED.audience, landing_page_url = EXCLUDED.landing_page_url, " +
        "  updated_at = EXCLUDED.updated_at";

    private static final String SELECT_BY_ID_SQL =
        "SELECT id, workspace_id, name, status, type, objective, budget_cents, start_date, end_date, audience, landing_page_url, created_at, updated_at, created_by " +
        "FROM dmos_campaigns WHERE id = ? AND workspace_id = ? AND tenant_id = ?";

    private static final String LIST_BY_WORKSPACE_SQL =
        "SELECT id, workspace_id, name, status, type, objective, budget_cents, start_date, end_date, audience, landing_page_url, created_at, updated_at, created_by " +
        "FROM dmos_campaigns WHERE workspace_id = ? AND tenant_id = ? " +
        "ORDER BY created_at DESC, id DESC " +
        "LIMIT ? OFFSET ?";

    private static final String COUNT_BY_WORKSPACE_SQL =
        "SELECT COUNT(*) FROM dmos_campaigns WHERE workspace_id = ? AND tenant_id = ?";

    private final DataSource dataSource;
    private final Executor executor;

    public PostgresCampaignRepository(DataSource dataSource, Executor executor) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    @Override
    public Promise<Campaign> save(DmTenantId tenantId, Campaign campaign) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(campaign, "campaign must not be null");
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(UPSERT_SQL)) {
                stmt.setString(1, campaign.getId());
                stmt.setString(2, campaign.getWorkspaceId().getValue());
                stmt.setString(3, tenantId.getValue());
                stmt.setString(4, campaign.getName());
                stmt.setString(5, campaign.getStatus().name());
                stmt.setString(6, campaign.getType().name());
                stmt.setString(7, campaign.getObjective());
                stmt.setObject(8, campaign.getBudgetCents());
                stmt.setString(9, campaign.getStartDate());
                stmt.setString(10, campaign.getEndDate());
                stmt.setString(11, campaign.getAudience());
                stmt.setString(12, campaign.getLandingPageUrl());
                stmt.setTimestamp(13, Timestamp.from(campaign.getCreatedAt()));
                stmt.setTimestamp(14, Timestamp.from(campaign.getUpdatedAt()));
                stmt.setString(15, campaign.getCreatedBy());
                stmt.setString(16, campaign.getWorkspaceId().getValue());
                stmt.setString(17, tenantId.getValue());
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
    public Promise<Optional<Campaign>> findById(DmTenantId tenantId, DmWorkspaceId workspaceId, String campaignId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        Objects.requireNonNull(campaignId, "campaignId must not be null");
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(SELECT_BY_ID_SQL)) {
                stmt.setString(1, campaignId);
                stmt.setString(2, workspaceId.getValue());
                stmt.setString(3, tenantId.getValue());
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
    public Promise<List<Campaign>> listByWorkspace(DmTenantId tenantId, DmWorkspaceId workspaceId, int limit, int offset) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        int boundedLimit = Math.min(Math.max(limit, 1), MAX_PAGE_SIZE);
        int boundedOffset = Math.max(offset, 0);

        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(LIST_BY_WORKSPACE_SQL)) {
                stmt.setString(1, workspaceId.getValue());
                stmt.setString(2, tenantId.getValue());
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
    public Promise<Long> countByWorkspace(DmTenantId tenantId, DmWorkspaceId workspaceId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");

        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(COUNT_BY_WORKSPACE_SQL)) {
                stmt.setString(1, workspaceId.getValue());
                stmt.setString(2, tenantId.getValue());
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
        long rawBudgetCents = rs.getLong("budget_cents");
        Long budgetCents = rs.wasNull() ? null : rawBudgetCents;
        return Campaign.builder()
            .id(rs.getString("id"))
            .workspaceId(DmWorkspaceId.of(rs.getString("workspace_id")))
            .name(rs.getString("name"))
            .status(CampaignStatus.valueOf(rs.getString("status")))
            .type(CampaignType.valueOf(rs.getString("type")))
            .objective(rs.getString("objective"))
            .budgetCents(budgetCents)
            .startDate(rs.getString("start_date"))
            .endDate(rs.getString("end_date"))
            .audience(rs.getString("audience"))
            .landingPageUrl(rs.getString("landing_page_url"))
            .createdAt(createdAt)
            .updatedAt(updatedAt)
            .createdBy(rs.getString("created_by"))
            .build();
    }
}
