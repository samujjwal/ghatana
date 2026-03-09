package com.ghatana.softwareorg.marketing.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.virtualorg.framework.event.EventPublisher;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * REST API controller for Marketing department events.
 *
 * <p>
 * <b>Purpose</b><br>
 * Exposes HTTP endpoints for marketing campaign operations (campaign
 * management, lead generation, engagement tracking, content metrics).
 *
 * @doc.type class
 * @doc.purpose Marketing domain REST API
 * @doc.layer product
 * @doc.pattern Controller
 */
public class MarketingEventController {

    private final EventPublisher eventPublisher;
    private final MetricsCollector metrics;
    private final ObjectMapper objectMapper = JsonUtils.getDefaultMapper();

    public MarketingEventController(EventPublisher eventPublisher, MetricsCollector metrics) {
        this.eventPublisher = eventPublisher;
        this.metrics = metrics;
    }

    /**
     * Launches marketing campaign.
     *
     * @param tenantId tenant context
     * @param campaignId campaign identifier
     * @param campaignName campaign name
     * @param channel marketing channel (EMAIL, SOCIAL, CONTENT, PAID, EVENT)
     * @param targetAudience target audience description
     * @return campaign ID
     */
    public String launchCampaign(
            String tenantId, String campaignId, String campaignName, String channel,
            String targetAudience) {
        if (campaignId == null) {
            campaignId = UUID.randomUUID().toString();
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("campaignId", campaignId);
        payload.put("campaignName", campaignName);
        payload.put("channel", channel);
        payload.put("targetAudience", targetAudience);
        payload.put("status", "LAUNCHED");

        payload.put("tenantId", tenantId);
        try {
            eventPublisher.publish("marketing.campaign.launched", objectMapper.writeValueAsBytes(payload));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize event payload", e);
        }
        metrics.incrementCounter("marketing.campaigns.launched", "channel", channel);
        return campaignId;
    }

    /**
     * Records lead generation event.
     *
     * @param tenantId tenant context
     * @param leadId lead identifier
     * @param campaignId source campaign
     * @param leadEmail lead email address
     * @param source lead source
     */
    public void recordLead(
            String tenantId, String leadId, String campaignId, String leadEmail, String source) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("leadId", leadId);
        payload.put("campaignId", campaignId);
        payload.put("leadEmail", leadEmail);
        payload.put("source", source);

        payload.put("tenantId", tenantId);
        try {
            eventPublisher.publish("marketing.lead.generated", objectMapper.writeValueAsBytes(payload));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize event payload", e);
        }
        metrics.incrementCounter("marketing.leads.generated", "source", source, "campaign", campaignId);
    }

    /**
     * Records customer engagement event.
     *
     * @param tenantId tenant context
     * @param engagementId engagement identifier
     * @param leadId lead being engaged
     * @param engagementType type of engagement (VIEW, CLICK, DOWNLOAD, SIGNUP)
     */
    public void recordEngagement(
            String tenantId, String engagementId, String leadId, String engagementType) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("engagementId", engagementId);
        payload.put("leadId", leadId);
        payload.put("engagementType", engagementType);

        payload.put("tenantId", tenantId);
        try {
            eventPublisher.publish("marketing.engagement.recorded", objectMapper.writeValueAsBytes(payload));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize event payload", e);
        }
        metrics.incrementCounter("marketing.engagements", "type", engagementType);
    }

    /**
     * Records campaign performance metrics.
     *
     * @param tenantId tenant context
     * @param campaignId campaign identifier
     * @param impressions number of impressions
     * @param clicks number of clicks
     * @param conversions number of conversions
     * @param cost campaign cost
     */
    public void recordCampaignMetrics(
            String tenantId, String campaignId, long impressions, long clicks, long conversions,
            double cost) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("campaignId", campaignId);
        payload.put("impressions", impressions);
        payload.put("clicks", clicks);
        payload.put("conversions", conversions);
        payload.put("cost", cost);
        payload.put("ctr", (double) clicks / impressions);
        payload.put("conversionRate", (double) conversions / clicks);
        payload.put("cac", cost / conversions);

        payload.put("tenantId", tenantId);
        try {
            eventPublisher.publish("marketing.campaign.metrics_recorded", objectMapper.writeValueAsBytes(payload));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize event payload", e);
        }
        metrics.recordTimer("marketing.campaign.roi", (long) (conversions / cost * 100));
    }
}
