package com.ghatana.digitalmarketing.application.optimization;

import com.ghatana.digitalmarketing.domain.optimization.NextBestActionRecommendation;
import com.ghatana.digitalmarketing.domain.optimization.NextBestActionStatus;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for next-best-action recommendation persistence.
 *
 * @doc.type interface
 * @doc.purpose Persistence operations for next-best-action recommendations (P3-004)
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface NextBestActionRecommendationRepository {

    /**
     * Save a recommendation.
     *
     * @param recommendation the recommendation to save
     * @return Promise containing the saved recommendation
     */
    Promise<NextBestActionRecommendation> save(NextBestActionRecommendation recommendation);

    /**
     * Update a recommendation.
     *
     * @param recommendation the recommendation to update
     * @return Promise containing the updated recommendation
     */
    Promise<NextBestActionRecommendation> update(NextBestActionRecommendation recommendation);

    /**
     * Find a recommendation by ID.
     *
     * @param id the recommendation ID
     * @return Promise containing optional recommendation
     */
    Promise<Optional<NextBestActionRecommendation>> findById(String id);

    /**
     * List recommendations by tenant.
     *
     * @param tenantId the tenant ID
     * @return Promise containing list of recommendations
     */
    Promise<List<NextBestActionRecommendation>> listByTenant(String tenantId);

    /**
     * List recommendations by workspace.
     *
     * @param tenantId the tenant ID
     * @param workspaceId the workspace ID
     * @return Promise containing list of recommendations
     */
    Promise<List<NextBestActionRecommendation>> listByWorkspace(String tenantId, String workspaceId);

    /**
     * List recommendations by campaign.
     *
     * @param tenantId the tenant ID
     * @param campaignId the campaign ID
     * @return Promise containing list of recommendations
     */
    Promise<List<NextBestActionRecommendation>> listByCampaign(String tenantId, String campaignId);

    /**
     * List recommendations by status.
     *
     * @param tenantId the tenant ID
     * @param status the status to filter by
     * @return Promise containing list of recommendations
     */
    Promise<List<NextBestActionRecommendation>> listByStatus(String tenantId, NextBestActionStatus status);
}
