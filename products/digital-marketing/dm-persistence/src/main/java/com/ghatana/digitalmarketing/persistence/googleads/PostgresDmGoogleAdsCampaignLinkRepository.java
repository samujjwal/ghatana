package com.ghatana.digitalmarketing.persistence.googleads;

import com.ghatana.digitalmarketing.application.googleads.DmGoogleAdsCampaignLinkRepository;
import com.ghatana.digitalmarketing.domain.googleads.DmGoogleAdsCampaignLink;
import com.ghatana.digitalmarketing.persistence.campaign.DmPersistenceException;
import io.activej.promise.Promise;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * PostgreSQL adapter for {@link DmGoogleAdsCampaignLinkRepository}.
 *
 * @doc.type class
 * @doc.purpose Durable repository for DMOS to Google Ads campaign links
 * @doc.layer product
 * @doc.pattern Repository
 */
public final class PostgresDmGoogleAdsCampaignLinkRepository implements DmGoogleAdsCampaignLinkRepository {

    private final DataSource dataSource;
    private final Executor executor;

    public PostgresDmGoogleAdsCampaignLinkRepository(DataSource dataSource, Executor executor) {
        this.dataSource = dataSource;
        this.executor = executor;
    }

    @Override
    public Promise<DmGoogleAdsCampaignLink> save(DmGoogleAdsCampaignLink link) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                INSERT INTO dmos_google_ads_campaign_links (
                    id, tenant_id, connector_id, internal_campaign_id, external_campaign_id, created_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (internal_campaign_id) DO UPDATE SET
                    tenant_id = EXCLUDED.tenant_id,
                    connector_id = EXCLUDED.connector_id,
                    external_campaign_id = EXCLUDED.external_campaign_id
                """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, link.getId());
                stmt.setString(2, link.getTenantId());
                stmt.setString(3, link.getConnectorId());
                stmt.setString(4, link.getInternalCampaignId());
                stmt.setString(5, link.getExternalCampaignId());
                stmt.setTimestamp(6, Timestamp.from(link.getCreatedAt()));
                stmt.executeUpdate();
                return link;
            } catch (SQLException e) {
                throw new DmPersistenceException("Failed to save Google Ads campaign link " + link.getId(), e);
            }
        });
    }

    @Override
    public Promise<Optional<DmGoogleAdsCampaignLink>> findByInternalCampaignId(String internalCampaignId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                SELECT id, tenant_id, connector_id, internal_campaign_id, external_campaign_id, created_at
                FROM dmos_google_ads_campaign_links
                WHERE internal_campaign_id = ?
                """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, internalCampaignId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    DmGoogleAdsCampaignLink link = DmGoogleAdsCampaignLink.builder()
                        .id(rs.getString("id"))
                        .tenantId(rs.getString("tenant_id"))
                        .connectorId(rs.getString("connector_id"))
                        .internalCampaignId(rs.getString("internal_campaign_id"))
                        .externalCampaignId(rs.getString("external_campaign_id"))
                        .createdAt(rs.getTimestamp("created_at").toInstant())
                        .build();
                    return Optional.of(link);
                }
            } catch (SQLException e) {
                throw new DmPersistenceException("Failed to find Google Ads campaign link " + internalCampaignId, e);
            }
        });
    }
}
