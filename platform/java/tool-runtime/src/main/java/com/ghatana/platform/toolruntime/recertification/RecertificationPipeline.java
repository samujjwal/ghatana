/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.toolruntime.recertification;

import io.activej.promise.Promise;

import java.util.List;

/**
 * Orchestrates periodic recertification campaigns for tenant governance objects.
 *
 * <p>A recertification campaign is a structured review cycle where certifiers
 * confirm (or revoke) that existing permissions, tools, policies, and data-access grants
 * should remain active. Campaigns pass through the states defined in {@link CampaignStatus}.
 *
 * <p>Typical workflow:
 * <pre>{@code
 * // 1. Compliance officer creates a campaign
 * RecertificationCampaign campaign = pipeline.createCampaign(
 *     tenantId, "Q1-2026 Agent Permissions", RecertificationScope.AGENT_PERMISSIONS);
 *
 * // 2. Query items and distribute to reviewers
 * List<RecertificationItem> items = pipeline.getItems(campaign.campaignId());
 *
 * // 3. Certifiers render decisions
 * pipeline.certify(campaign.campaignId(), items.get(0).itemId(), "reviewer@example.com");
 * pipeline.revoke(campaign.campaignId(), items.get(1).itemId(), "reviewer@example.com", "Unused for 6 months");
 *
 * // 4. Generate audit report
 * RecertificationReport report = pipeline.generateReport(campaign.campaignId());
 * }</pre>
 *
 * @doc.type interface
 * @doc.purpose Orchestrate periodic recertification campaigns for tenant governance
 * @doc.layer platform
 * @doc.pattern Service
 */
public interface RecertificationPipeline {

    /**
     * Create a new recertification campaign.
     *
     * <p>The pipeline populates the campaign with items appropriate to the given
     * {@link RecertificationScope}. The campaign starts in {@link CampaignStatus#IN_PROGRESS}.
     *
     * @param tenantId     owning tenant
     * @param campaignName human-readable name
     * @param scope        what categories of objects to recertify
     * @return promise resolving to the new {@link RecertificationCampaign}
     */
    Promise<RecertificationCampaign> createCampaign(String tenantId, String campaignName,
            RecertificationScope scope);

    /**
     * Retrieve a campaign by ID.
     *
     * @param campaignId campaign identifier
     * @return promise resolving to the campaign, or failing with {@link IllegalArgumentException}
     */
    Promise<RecertificationCampaign> getCampaign(String campaignId);

    /**
     * List all campaigns for a tenant, ordered by creation date descending.
     *
     * @param tenantId the tenant to query
     * @return promise resolving to the list of campaigns
     */
    Promise<List<RecertificationCampaign>> listCampaigns(String tenantId);

    /**
     * Retrieve all items enrolled in a campaign.
     *
     * @param campaignId campaign identifier
     * @return promise resolving to all items, or failing with {@link IllegalArgumentException}
     */
    Promise<List<RecertificationItem>> getItems(String campaignId);

    /**
     * Record a {@link ItemDecision#CERTIFIED} decision for an item.
     *
     * @param campaignId  owning campaign
     * @param itemId      item to certify
     * @param certifierId reviewer identifier
     * @return promise resolving to the updated item
     * @throws IllegalArgumentException if campaign or item not found
     * @throws IllegalStateException    if the item has already been reviewed
     */
    Promise<RecertificationItem> certify(String campaignId, String itemId, String certifierId);

    /**
     * Record a {@link ItemDecision#REVOKED} decision for an item.
     *
     * @param campaignId  owning campaign
     * @param itemId      item to revoke
     * @param certifierId reviewer identifier
     * @param reason      required revocation reason
     * @return promise resolving to the updated item
     * @throws IllegalArgumentException if campaign or item not found
     * @throws IllegalStateException    if the item has already been reviewed
     */
    Promise<RecertificationItem> revoke(String campaignId, String itemId,
            String certifierId, String reason);

    /**
     * Generate a summary audit report for a campaign.
     *
     * <p>The campaign is marked {@link CampaignStatus#COMPLETED} when this method is
     * called (if all items are reviewed); otherwise the report reflects current progress.
     *
     * @param campaignId campaign identifier
     * @return promise resolving to the {@link RecertificationReport}
     * @throws IllegalArgumentException if the campaign is not found
     */
    Promise<RecertificationReport> generateReport(String campaignId);
}
