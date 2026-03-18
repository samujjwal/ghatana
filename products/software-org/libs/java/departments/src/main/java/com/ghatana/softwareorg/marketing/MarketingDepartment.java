package com.ghatana.softwareorg.marketing;

import com.ghatana.virtualorg.framework.AbstractOrganization;
import com.ghatana.virtualorg.framework.event.EventPublisher;
import com.ghatana.softwareorg.domain.common.BaseSoftwareOrgDepartment;
import com.ghatana.platform.types.identity.Identifier;

/**
 * Marketing Department for software-org.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides extension hooks for marketing-specific operations. Core behavior
 * (event types, workflows, agents) is defined in YAML configuration.
 *
 * <p>
 * <b>Extension Points</b><br>
 * - Campaign management
 * - Lead generation
 * - Content publishing
 * - Brand positioning
 *
 * @doc.type class
 * @doc.purpose Marketing department extension hooks
 * @doc.layer product
 * @doc.pattern Extension Point
 */
public class MarketingDepartment extends BaseSoftwareOrgDepartment {

    public static final String DEPARTMENT_TYPE = "MARKETING";
    public static final String DEPARTMENT_NAME = "Marketing";

    public MarketingDepartment(AbstractOrganization org, EventPublisher publisher) {
        super(org, publisher, DEPARTMENT_NAME, DEPARTMENT_TYPE);
    }

    /**
     * Hook: Launch a marketing campaign.
     *
     * @param campaignName   campaign name
     * @param channel        marketing channel
     * @param budgetAllocated budget allocated
     * @return campaign ID
     */
    public String launchCampaign(String campaignName, String channel, float budgetAllocated) {
        String campaignId = Identifier.random().raw();

        publishEvent("MarketingCampaignLaunched", newPayload()
                .withField("campaign_id", campaignId)
                .withField("campaign_name", campaignName)
                .withField("channel", channel)
                .withField("budget_allocated", budgetAllocated)
                .withTimestamp()
                .build());

        return campaignId;
    }

    /**
     * Hook: Update campaign performance metrics.
     *
     * @param campaignId  campaign identifier
     * @param impressions total impressions
     * @param conversions total conversions
     */
    public void updateCampaignPerformance(String campaignId, int impressions, int conversions) {
        float conversionRate = impressions > 0 ? (float) conversions / impressions : 0.0f;

        publishEvent("CampaignPerformanceUpdated", newPayload()
                .withField("campaign_id", campaignId)
                .withField("impressions", impressions)
                .withField("conversions", conversions)
                .withField("conversion_rate", conversionRate)
                .withTimestamp()
                .build());
    }

    /**
     * Hook: Record a lead generation event.
     *
     * @param campaignId  campaign identifier
     * @param leadQuality lead quality score
     * @return lead ID
     */
    public String recordLeadGeneration(String campaignId, String leadQuality) {
        String leadId = Identifier.random().raw();

        publishEvent("LeadGenerationEventRecorded", newPayload()
                .withField("lead_id", leadId)
                .withField("campaign_id", campaignId)
                .withField("lead_quality", leadQuality)
                .withTimestamp()
                .build());

        return leadId;
    }

    /**
     * Hook: Publish content.
     *
     * @param contentType content type
     * @param title       content title
     * @return content ID
     */
    public String publishContent(String contentType, String title) {
        String contentId = Identifier.random().raw();

        publishEvent("ContentPublished", newPayload()
                .withField("content_id", contentId)
                .withField("content_type", contentType)
                .withField("title", title)
                .withTimestamp()
                .build());

        return contentId;
    }

    /**
     * Hook: Update brand positioning.
     *
     * @param marketSegment          market segment
     * @param competitivePositioning competitive positioning
     */
    public void updateBrandPositioning(String marketSegment, String competitivePositioning) {
        publishEvent("BrandPositioningUpdated", newPayload()
                .withField("market_segment", marketSegment)
                .withField("competitive_positioning", competitivePositioning)
                .withTimestamp()
                .build());
    }
}
