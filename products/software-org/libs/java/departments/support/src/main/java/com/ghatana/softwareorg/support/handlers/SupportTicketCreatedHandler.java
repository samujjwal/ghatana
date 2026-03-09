package com.ghatana.softwareorg.support.handlers;

import com.ghatana.platform.observability.MetricsCollector;

/**
 * Handles SupportTicketCreated events from Support department.
 *
 * @doc.type class
 * @doc.purpose Support event handler
 * @doc.layer product
 * @doc.pattern EventHandler
 */
public class SupportTicketCreatedHandler {

    private final MetricsCollector metrics;

    public SupportTicketCreatedHandler(MetricsCollector metrics) {
        this.metrics = metrics;
    }

    public void handle(String ticketId, String priority) {
        metrics.incrementCounter("support.ticket.created", "priority", priority);
    }
}
