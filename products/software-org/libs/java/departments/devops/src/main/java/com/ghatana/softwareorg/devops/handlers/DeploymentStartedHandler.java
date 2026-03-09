package com.ghatana.softwareorg.devops.handlers;

import com.ghatana.platform.observability.MetricsCollector;

/**
 * Handles DeploymentStarted events from DevOps department.
 *
 * @doc.type class
 * @doc.purpose DevOps event handler
 * @doc.layer product
 * @doc.pattern EventHandler
 */
public class DeploymentStartedHandler {

    private final MetricsCollector metrics;

    public DeploymentStartedHandler(MetricsCollector metrics) {
        this.metrics = metrics;
    }

    public void handle(String deploymentId, String environment) {
        metrics.incrementCounter("deployment.started", "environment", environment);
    }
}
