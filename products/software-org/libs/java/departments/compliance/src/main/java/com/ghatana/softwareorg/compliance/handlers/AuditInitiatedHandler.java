package com.ghatana.softwareorg.compliance.handlers;

import com.ghatana.platform.observability.MetricsCollector;

/**
 * Handles AuditInitiated events from Compliance department.
 *
 * @doc.type class
 * @doc.purpose Compliance event handler
 * @doc.layer product
 * @doc.pattern EventHandler
 */
public class AuditInitiatedHandler {

    private final MetricsCollector metrics;

    public AuditInitiatedHandler(MetricsCollector metrics) {
        this.metrics = metrics;
    }

    public void handle(String auditId, String scope) {
        metrics.incrementCounter("compliance.audit.initiated", "scope", scope);
    }
}
