package com.ghatana.softwareorg.marketing.handlers;

import com.ghatana.platform.observability.MetricsCollector;

/**
 * Handles MarketingCampaignLaunched events from Marketing department.
 *
 * @doc.type class
 * @doc.purpose Marketing event handler
 * @doc.layer product
 * @doc.pattern EventHandler
 */
public class MarketingCampaignLaunchedHandler {

    private final MetricsCollector metrics;

    public MarketingCampaignLaunchedHandler(MetricsCollector metrics) {
        this.metrics = metrics;
    }

    public void handle(String campaignId, String channel) {
        metrics.incrementCounter("marketing.campaign.launched", "channel", channel);
    }
}
