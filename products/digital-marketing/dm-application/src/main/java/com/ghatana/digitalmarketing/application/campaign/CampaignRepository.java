package com.ghatana.digitalmarketing.application.campaign;

import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.campaign.Campaign;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for DMOS campaign persistence.
 *
 * @doc.type interface
 * @doc.purpose Campaign persistence contract for DMOS application services
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface CampaignRepository {

    /**
     * Saves a campaign (insert or update).
     *
     * @param campaign the campaign to save
     * @return promise resolving to the saved campaign
     */
    default Promise<Campaign> save(DmTenantId tenantId, Campaign campaign) {
        return save(campaign);
    }

    /**
     * Saves a campaign when tenant scope is unavailable in legacy local/test callers.
     *
     * @param campaign the campaign to save
     * @return promise resolving to the saved campaign
     */
    default Promise<Campaign> save(Campaign campaign) {
        return save(DmTenantId.of("__legacy_unspecified_tenant__"), campaign);
    }

    /**
     * Finds a campaign by ID within a workspace.
     *
     * @param workspaceId the workspace scope
     * @param campaignId  the campaign ID
     * @return promise resolving to an optional campaign
     */
    default Promise<Optional<Campaign>> findById(DmTenantId tenantId, DmWorkspaceId workspaceId, String campaignId) {
        return findById(workspaceId, campaignId);
    }

    /**
     * Legacy workspace-only lookup retained for local/test callers.
     *
     * @param workspaceId the workspace scope
     * @param campaignId  the campaign ID
     * @return promise resolving to an optional campaign
     */
    default Promise<Optional<Campaign>> findById(DmWorkspaceId workspaceId, String campaignId) {
        return findById(DmTenantId.of("__legacy_unspecified_tenant__"), workspaceId, campaignId);
    }

    /**
     * Lists campaigns for a workspace with pagination and deterministic ordering.
     *
     * <p>Results are ordered by {@code createdAt} descending (newest first) for
     * deterministic, stable pagination.</p>
     *
     * @param workspaceId the workspace scope
     * @param limit       maximum number of results to return (max 100)
     * @param offset      number of results to skip for pagination
     * @return promise resolving to a list of campaigns in the workspace
     */
    default Promise<List<Campaign>> listByWorkspace(DmTenantId tenantId, DmWorkspaceId workspaceId, int limit, int offset) {
        return listByWorkspace(workspaceId, limit, offset);
    }

    /**
     * Legacy workspace-only listing retained for local/test callers.
     *
     * @param workspaceId the workspace scope
     * @param limit       maximum number of results to return (max 100)
     * @param offset      number of results to skip for pagination
     * @return promise resolving to a list of campaigns in the workspace
     */
    default Promise<List<Campaign>> listByWorkspace(DmWorkspaceId workspaceId, int limit, int offset) {
        return listByWorkspace(DmTenantId.of("__legacy_unspecified_tenant__"), workspaceId, limit, offset);
    }

    /**
     * Counts total campaigns in a workspace for pagination metadata.
     *
     * @param workspaceId the workspace scope
     * @return promise resolving to the total count
     */
    default Promise<Long> countByWorkspace(DmTenantId tenantId, DmWorkspaceId workspaceId) {
        return countByWorkspace(workspaceId);
    }

    /**
     * Legacy workspace-only count retained for local/test callers.
     *
     * @param workspaceId the workspace scope
     * @return promise resolving to the total count
     */
    default Promise<Long> countByWorkspace(DmWorkspaceId workspaceId) {
        return countByWorkspace(DmTenantId.of("__legacy_unspecified_tenant__"), workspaceId);
    }
}
