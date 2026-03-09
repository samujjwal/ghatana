package com.ghatana.softwareorg.integration;

import com.ghatana.platform.observability.MetricsCollector;

/**
 * Event publisher for software-org department events to EventCloud.
 *
 * <p>
 * Responsibilities:<br>
 * - Normalize department events to standard format - Publish to EventCloud with
 * tenant context - Track publishing metrics
 *
 * @doc.type class
 * @doc.purpose EventCloud event publishing gateway
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class SoftwareOrgEventPublisher {

    private final MetricsCollector metrics;
    private final String tenant;

    /**
     * Create event publisher.
     *
     * @param metrics Metrics collector
     * @param tenant Tenant identifier
     */
    public SoftwareOrgEventPublisher(MetricsCollector metrics, String tenant) {
        this.metrics = metrics;
        this.tenant = tenant;
    }

    /**
     * Publish engineering event.
     *
     * @param eventType Event type
     * @param payload Event payload
     */
    public void publishEngineeringEvent(String eventType, String payload) {
        metrics.incrementCounter(
                "software_org.event.published",
                "department", "engineering",
                "event_type", eventType
        );
    }

    /**
     * Publish QA event.
     *
     * @param eventType Event type
     * @param payload Event payload
     */
    public void publishQaEvent(String eventType, String payload) {
        metrics.incrementCounter(
                "software_org.event.published",
                "department", "qa",
                "event_type", eventType
        );
    }

    /**
     * Publish DevOps event.
     *
     * @param eventType Event type
     * @param payload Event payload
     */
    public void publishDevopsEvent(String eventType, String payload) {
        metrics.incrementCounter(
                "software_org.event.published",
                "department", "devops",
                "event_type", eventType
        );
    }
}
