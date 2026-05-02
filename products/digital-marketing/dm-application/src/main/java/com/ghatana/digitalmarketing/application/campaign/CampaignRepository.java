package com.ghatana.digitalmarketing.application.campaign;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.campaign.Campaign;
import io.activej.promise.Promise;

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
}
