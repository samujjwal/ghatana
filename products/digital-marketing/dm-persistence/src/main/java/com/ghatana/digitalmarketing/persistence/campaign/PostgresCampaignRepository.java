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

    private static final String UPSERT_SQL =
        "INSERT INTO dmos_campaigns (id, workspace_id, name, status, type, created_at, updated_at, created_by) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
        "ON CONFLICT (id, workspace_id) DO UPDATE SET " +
        "  name = EXCLUDED.name, status = EXCLUDED.status, type = EXCLUDED.type, " +
        "  updated_at = EXCLUDED.updated_at, created_by = EXCLUDED.created_by";

    private static final String SELECT_BY_ID_SQL =
        "SELECT id, workspace_id, name, status, type, created_at, updated_at, created_by " +
        "FROM dmos_campaigns WHERE id = ? AND workspace_id = ?";

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
                stmt.executeUpdate();
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
