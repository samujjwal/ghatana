package com.ghatana.digitalmarketing.application.campaign;

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
    Promise<Campaign> save(Campaign campaign);

    /**
     * Finds a campaign by ID within a workspace.
     *
     * @param workspaceId the workspace scope
     * @param campaignId  the campaign ID
     * @return promise resolving to an optional campaign
     */
    Promise<Optional<Campaign>> findById(DmWorkspaceId workspaceId, String campaignId);

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
    Promise<List<Campaign>> listByWorkspace(DmWorkspaceId workspaceId, int limit, int offset);

    /**
     * Counts total campaigns in a workspace for pagination metadata.
     *
     * @param workspaceId the workspace scope
     * @return promise resolving to the total count
     */
    Promise<Long> countByWorkspace(DmWorkspaceId workspaceId);
}
