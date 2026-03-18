package com.ghatana.softwareorg.sales.handlers;

import com.ghatana.platform.observability.MetricsCollector;

/**
 * Handles SalesOpportunityCreated events from Sales department.
 *
 * @doc.type class
 * @doc.purpose Sales event handler
 * @doc.layer product
 * @doc.pattern EventHandler
 */
public class SalesOpportunityCreatedHandler {

    private final MetricsCollector metrics;

    public SalesOpportunityCreatedHandler(MetricsCollector metrics) {
        this.metrics = metrics;
    }

    public void handle(String opportunityId, double value) {
        metrics.incrementCounter("sales.opportunity.created", "value", String.valueOf(value));
    }
}
