package com.ghatana.softwareorg.product.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.virtualorg.framework.event.EventPublisher;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * REST API controller for Product department events.
 *
 * <p>
 * <b>Purpose</b><br>
 * Exposes HTTP endpoints for product management operations (feature planning,
 * roadmap tracking, user feedback, experimentation).
 *
 * @doc.type class
 * @doc.purpose Product domain REST API
 * @doc.layer product
 * @doc.pattern Controller
 */
public class ProductEventController {

    private final EventPublisher eventPublisher;
    private final MetricsCollector metrics;
    private final ObjectMapper objectMapper = JsonUtils.getDefaultMapper();

    public ProductEventController(EventPublisher eventPublisher, MetricsCollector metrics) {
        this.eventPublisher = eventPublisher;
        this.metrics = metrics;
    }

    /**
     * Creates new feature request.
     *
     * @param tenantId tenant context
     * @param title feature title
     * @param description feature description
     * @param priority priority level
     * @return feature request ID
     */
    public String createFeatureRequest(
            String tenantId, String title, String description, String priority) {
        String featureId = UUID.randomUUID().toString();

        Map<String, Object> payload = new HashMap<>();
        payload.put("featureId", featureId);
        payload.put("title", title);
        payload.put("description", description);
        payload.put("priority", priority);
        payload.put("status", "REQUESTED");

        payload.put("tenantId", tenantId);
        try {
            eventPublisher.publish("product.feature.requested", objectMapper.writeValueAsBytes(payload));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize event payload", e);
        }
        metrics.incrementCounter("product.features.requested", "priority", priority);
        return featureId;
    }

    /**
     * Records user feedback.
     *
     * @param tenantId tenant context
     * @param feedbackId feedback identifier
     * @param sentiment feedback sentiment (POSITIVE, NEUTRAL, NEGATIVE)
     * @param topic feedback topic (UI, PERFORMANCE, INTEGRATION, PRICING)
     * @param message feedback message
     */
    public void recordFeedback(
            String tenantId, String feedbackId, String sentiment, String topic, String message) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("feedbackId", feedbackId);
        payload.put("sentiment", sentiment);
        payload.put("topic", topic);
        payload.put("message", message);

        payload.put("tenantId", tenantId);
        try {
            eventPublisher.publish("product.feedback.received", objectMapper.writeValueAsBytes(payload));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize event payload", e);
        }
        metrics.incrementCounter("product.feedback", "sentiment", sentiment, "topic", topic);
    }

    /**
     * Records A/B experiment result.
     *
     * @param tenantId tenant context
     * @param experimentId experiment identifier
     * @param variant variant being tested
     * @param conversionRateControl control group conversion rate
     * @param conversionRateVariant variant group conversion rate
     * @param statistically significant whether result is statistically
     * significant
     */
    public void recordExperimentResult(
            String tenantId,
            String experimentId,
            String variant,
            double conversionRateControl,
            double conversionRateVariant,
            boolean statisticallySignificant) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("experimentId", experimentId);
        payload.put("variant", variant);
        payload.put("conversionRateControl", conversionRateControl);
        payload.put("conversionRateVariant", conversionRateVariant);
        payload.put("lift", (conversionRateVariant - conversionRateControl) / conversionRateControl);
        payload.put("statisticallySignificant", statisticallySignificant);

        String eventType = statisticallySignificant ? "product.experiment.winner" : "product.experiment.inconclusive";
        payload.put("tenantId", tenantId);
        try {
            eventPublisher.publish(eventType, objectMapper.writeValueAsBytes(payload));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize event payload", e);
        }
        metrics.recordTimer("product.experiment.lift", (long) ((conversionRateVariant - conversionRateControl) / conversionRateControl * 100));
    }
}
