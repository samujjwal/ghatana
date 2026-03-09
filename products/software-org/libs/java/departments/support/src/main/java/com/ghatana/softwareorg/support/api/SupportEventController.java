package com.ghatana.softwareorg.support.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.virtualorg.framework.event.EventPublisher;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * REST API controller for Support department events.
 *
 * <p>
 * <b>Purpose</b><br>
 * Exposes HTTP endpoints for customer support operations (ticket management,
 * SLA tracking, customer satisfaction, knowledge base).
 *
 * <p>
 * <b>Endpoints</b><br>
 * - POST /api/v1/support/tickets - Create support ticket - PUT
 * /api/v1/support/tickets/{id} - Update ticket status - POST
 * /api/v1/support/feedback - Record customer feedback
 *
 * @doc.type class
 * @doc.purpose Support domain REST API
 * @doc.layer product
 * @doc.pattern Controller
 */
public class SupportEventController {

    private final EventPublisher eventPublisher;
    private final MetricsCollector metrics;
    private final ObjectMapper objectMapper = JsonUtils.getDefaultMapper();

    public SupportEventController(EventPublisher eventPublisher, MetricsCollector metrics) {
        this.eventPublisher = eventPublisher;
        this.metrics = metrics;
    }

    /**
     * Creates new support ticket.
     *
     * @param tenantId tenant context
     * @param subject ticket subject
     * @param description ticket description
     * @param priority ticket priority (CRITICAL, HIGH, MEDIUM, LOW)
     * @param customerId customer identifier
     * @return ticket ID
     */
    public String createTicket(
            String tenantId, String subject, String description, String priority, String customerId) {
        String ticketId = UUID.randomUUID().toString();

        Map<String, Object> payload = new HashMap<>();
        payload.put("ticketId", ticketId);
        payload.put("subject", subject);
        payload.put("description", description);
        payload.put("priority", priority);
        payload.put("customerId", customerId);
        payload.put("status", "OPENED");

        payload.put("tenantId", tenantId);
        try {
            eventPublisher.publish("support.ticket.opened", objectMapper.writeValueAsBytes(payload));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize event payload", e);
        }
        metrics.incrementCounter("support.tickets.created", "priority", priority);
        return ticketId;
    }

    /**
     * Updates ticket status.
     *
     * @param tenantId tenant context
     * @param ticketId ticket identifier
     * @param newStatus new status (OPENED, IN_PROGRESS, RESOLVED, CLOSED)
     */
    public void updateTicketStatus(String tenantId, String ticketId, String newStatus) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("ticketId", ticketId);
        payload.put("status", newStatus);

        payload.put("tenantId", tenantId);
        try {
            eventPublisher.publish("support.ticket.updated", objectMapper.writeValueAsBytes(payload));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize event payload", e);
        }
        metrics.incrementCounter("support.tickets.status_changed", "status", newStatus);
    }

    /**
     * Records customer satisfaction feedback.
     *
     * @param tenantId tenant context
     * @param ticketId resolved ticket identifier
     * @param satisfactionScore satisfaction score (1-5)
     * @param comment feedback comment
     */
    public void recordFeedback(
            String tenantId, String ticketId, int satisfactionScore, String comment) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("ticketId", ticketId);
        payload.put("satisfactionScore", satisfactionScore);
        payload.put("comment", comment);

        payload.put("tenantId", tenantId);
        try {
            eventPublisher.publish("support.feedback.received", objectMapper.writeValueAsBytes(payload));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize event payload", e);
        }
        metrics.recordTimer(
                "support.satisfaction_score",
                satisfactionScore,
                "score_level",
                satisfactionScore >= 4 ? "high" : "low");
    }

    /**
     * Reports SLA metric for ticket resolution.
     *
     * @param tenantId tenant context
     * @param ticketId ticket identifier
     * @param resolutionTimeMinutes time to resolution in minutes
     * @param slaMet whether SLA was met
     */
    public void recordSlaMetric(
            String tenantId, String ticketId, long resolutionTimeMinutes, boolean slaMet) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("ticketId", ticketId);
        payload.put("resolutionTimeMinutes", resolutionTimeMinutes);
        payload.put("slaMet", slaMet);

        String eventType = slaMet ? "support.sla.met" : "support.sla.breached";
        payload.put("tenantId", tenantId);
        try {
            eventPublisher.publish(eventType, objectMapper.writeValueAsBytes(payload));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize event payload", e);
        }
        metrics.incrementCounter("support.sla_metrics", "met", String.valueOf(slaMet));
    }
}
