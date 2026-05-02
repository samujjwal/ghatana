package com.ghatana.digitalmarketing.application.content;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.content.ContentAsset;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for DMOS content asset persistence.
 *
 * @doc.type interface
 * @doc.purpose Content asset persistence contract for DMOS application services
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface ContentAssetRepository {

    /**
     * Saves a content asset (insert or update by ID within the workspace).
     *
     * @param asset the asset to save; must not be null
     * @return promise resolving to the saved asset
     */
    Promise<ContentAsset> save(ContentAsset asset);

    /**
     * Finds a content asset by ID within the given workspace.
     *
     * @param workspaceId the owning workspace; must not be null
     * @param assetId     the asset ID; must not be null
     * @return promise resolving to an optional content asset
     */
    Promise<Optional<ContentAsset>> findById(DmWorkspaceId workspaceId, String assetId);

    /**
     * Finds all content assets associated with a given campaign.
     *
     * @param workspaceId the owning workspace; must not be null
     * @param campaignId  the campaign ID; must not be null
     * @return promise resolving to a list of content assets; never null
     */
    Promise<List<ContentAsset>> findByCampaign(DmWorkspaceId workspaceId, String campaignId);

    /**
     * Counts approved content assets for the given campaign.
     *
     * @param workspaceId the owning workspace; must not be null
     * @param campaignId  the campaign ID; must not be null
     * @return promise resolving to the count of approved assets
     */
    Promise<Integer> countApprovedByCampaign(DmWorkspaceId workspaceId, String campaignId);
}
