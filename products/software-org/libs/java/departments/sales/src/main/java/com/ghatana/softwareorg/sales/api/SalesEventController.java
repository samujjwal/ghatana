package com.ghatana.softwareorg.sales.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.virtualorg.framework.event.EventPublisher;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * REST API controller for Sales department events.
 *
 * <p>
 * <b>Purpose</b><br>
 * Exposes HTTP endpoints for sales pipeline operations (opportunity tracking,
 * quote management, contract signing, revenue recording).
 *
 * @doc.type class
 * @doc.purpose Sales domain REST API
 * @doc.layer product
 * @doc.pattern Controller
 */
public class SalesEventController {

    private final EventPublisher eventPublisher;
    private final MetricsCollector metrics;
    private final ObjectMapper objectMapper = JsonUtils.getDefaultMapper();

    public SalesEventController(EventPublisher eventPublisher, MetricsCollector metrics) {
        this.eventPublisher = eventPublisher;
        this.metrics = metrics;
    }

    /**
     * Creates new sales opportunity.
     *
     * @param tenantId tenant context
     * @param accountName account/company name
     * @param estimatedValue estimated deal value
     * @param stage sales stage (LEAD, PROSPECT, PROPOSAL, NEGOTIATION, CLOSED)
     * @return opportunity ID
     */
    public String createOpportunity(
            String tenantId, String accountName, double estimatedValue, String stage) {
        String opportunityId = UUID.randomUUID().toString();

        Map<String, Object> payload = new HashMap<>();
        payload.put("opportunityId", opportunityId);
        payload.put("accountName", accountName);
        payload.put("estimatedValue", estimatedValue);
        payload.put("stage", stage);

        payload.put("tenantId", tenantId);
        try {
            eventPublisher.publish("sales.opportunity.created", objectMapper.writeValueAsBytes(payload));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize event payload", e);
        }
        metrics.recordTimer("sales.opportunity.value", (long) estimatedValue);
        return opportunityId;
    }

    /**
     * Records quote sent to prospect.
     *
     * @param tenantId tenant context
     * @param quoteId quote identifier
     * @param opportunityId associated opportunity
     * @param quoteAmount quote amount
     */
    public void sendQuote(String tenantId, String quoteId, String opportunityId, double quoteAmount) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("quoteId", quoteId);
        payload.put("opportunityId", opportunityId);
        payload.put("quoteAmount", quoteAmount);

        payload.put("tenantId", tenantId);
        try {
            eventPublisher.publish("sales.quote.sent", objectMapper.writeValueAsBytes(payload));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize event payload", e);
        }
        metrics.incrementCounter("sales.quotes.sent", "tenant", tenantId);
    }

    /**
     * Records contract signature event.
     *
     * @param tenantId tenant context
     * @param contractId contract identifier
     * @param opportunityId associated opportunity
     * @param contractValue contract annual value (ARR)
     */
    public void signContract(
            String tenantId, String contractId, String opportunityId, double contractValue) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("contractId", contractId);
        payload.put("opportunityId", opportunityId);
        payload.put("contractValue", contractValue);
        payload.put("status", "SIGNED");

        payload.put("tenantId", tenantId);
        try {
            eventPublisher.publish("sales.contract.signed", objectMapper.writeValueAsBytes(payload));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize event payload", e);
        }
        metrics.recordTimer("sales.arr", (long) contractValue);
    }

    /**
     * Records revenue recognition.
     *
     * @param tenantId tenant context
     * @param revenueId revenue event identifier
     * @param amount revenue amount
     * @param type revenue type (NEW, RENEWAL, EXPANSION, CHURN)
     */
    public void recordRevenue(String tenantId, String revenueId, double amount, String type) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("revenueId", revenueId);
        payload.put("amount", amount);
        payload.put("type", type);

        String eventType = "CHURN".equals(type) ? "sales.revenue.lost" : "sales.revenue.recognized";
        payload.put("tenantId", tenantId);
        try {
            eventPublisher.publish(eventType, objectMapper.writeValueAsBytes(payload));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize event payload", e);
        }
        metrics.recordTimer("sales.revenue", (long) amount, "type", type);
    }
}
