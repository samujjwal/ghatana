package com.ghatana.softwareorg.product.handlers;

import com.ghatana.platform.observability.MetricsCollector;

/**
 * Handles FeatureRequestCreated events from Product department.
 *
 * @doc.type class
 * @doc.purpose Product event handler
 * @doc.layer product
 * @doc.pattern EventHandler
 */
public class FeatureRequestCreatedHandler {

    private final MetricsCollector metrics;

    public FeatureRequestCreatedHandler(MetricsCollector metrics) {
        this.metrics = metrics;
    }

    public void handle(String featureId, String source) {
        metrics.incrementCounter("product.feature.requested", "source", source);
    }
}
