/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.core.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Default implementation of stage suggestion service.
 * <p>
 * Uses rule-based heuristics to suggest stages based on event types and context.
 * This can be enhanced with ML models in the future.
 *
 * @doc.type class
 * @doc.purpose Rule-based stage suggestion service
 * @doc.layer product
 * @doc.pattern Strategy
 */
public class DefaultStageSuggestionService implements StageSuggestionService {

    private static final Logger logger = LoggerFactory.getLogger(DefaultStageSuggestionService.class);

    private final List<StageTemplate> templates;
    private final Map<String, List<StageTemplate>> templatesByType;

    public DefaultStageSuggestionService() {
        this.templates = initializeTemplates();
        this.templatesByType = templates.stream()
            .collect(Collectors.groupingBy(StageTemplate::stageType));
    }

    @Override
    public List<StageSuggestion> suggestStages(List<String> eventTypes, Map<String, Object> context) {
        List<StageSuggestion> suggestions = new ArrayList<>();

        if (eventTypes == null || eventTypes.isEmpty()) {
            return suggestions;
        }

        // Analyze event types to determine appropriate stages
        Set<String> eventCategories = categorizeEventTypes(eventTypes);

        // Suggest stages based on categories
        if (eventCategories.contains("transaction")) {
            suggestions.add(new StageSuggestion(
                "filter", "transaction-filter", "Filter out invalid transactions", 0.9,
                Map.of("minAmount", 0.01, "maxAmount", 1000000.0), List.of()
            ));
            suggestions.add(new StageSuggestion(
                "enrich", "customer-enrichment", "Enrich with customer data", 0.85,
                Map.of("enrichmentType", "customer_profile"), List.of("transaction-filter")
            ));
            suggestions.add(new StageSuggestion(
                "detect", "fraud-detection", "Detect fraudulent transactions", 0.95,
                Map.of("model", "isolation_forest", "threshold", 0.8), List.of("customer-enrichment")
            ));
        }

        if (eventCategories.contains("login") || eventCategories.contains("auth")) {
            suggestions.add(new StageSuggestion(
                "validate", "auth-validation", "Validate authentication tokens", 0.95,
                Map.of("tokenType", "jwt", "validateSignature", true), List.of()
            ));
            suggestions.add(new StageSuggestion(
                "detect", "anomaly-detection", "Detect login anomalies", 0.9,
                Map.of("window", "1h", "maxAttempts", 5), List.of("auth-validation")
            ));
        }

        if (eventCategories.contains("order")) {
            suggestions.add(new StageSuggestion(
                "validate", "order-validation", "Validate order data", 0.9,
                Map.of("checkInventory", true, "validatePricing", true), List.of()
            ));
            suggestions.add(new StageSuggestion(
                "transform", "order-enrichment", "Enrich order with product data", 0.85,
                Map.of("enrichmentType", "product_catalog"), List.of("order-validation")
            ));
        }

        if (eventCategories.contains("sensor") || eventCategories.contains("iot")) {
            suggestions.add(new StageSuggestion(
                "filter", "noise-filter", "Filter sensor noise", 0.9,
                Map.of("method", "kalman", "threshold", 0.1), List.of()
            ));
            suggestions.add(new StageSuggestion(
                "aggregate", "time-window-aggregation", "Aggregate in time windows", 0.85,
                Map.of("windowSize", "5m", "aggregation", "avg"), List.of("noise-filter")
            ));
            suggestions.add(new StageSuggestion(
                "detect", "anomaly-detection", "Detect sensor anomalies", 0.9,
                Map.of("method", "zscore", "threshold", 3.0), List.of("time-window-aggregation")
            ));
        }

        // Add general-purpose stages
        suggestions.add(new StageSuggestion(
            "log", "audit-log", "Log all events for audit trail", 1.0,
            Map.of("level", "info", "includePayload", true), List.of()
        ));
        suggestions.add(new StageSuggestion(
            "metrics", "telemetry", "Collect telemetry metrics", 0.95,
            Map.of("includeTiming", true, "includeCounts", true), List.of()
        ));

        logger.info("Generated {} stage suggestions for event types: {}", suggestions.size(), eventTypes);
        return suggestions;
    }

    @Override
    public List<StageTemplate> getStageTemplates(String stageType) {
        return templatesByType.getOrDefault(stageType, List.of());
    }

    private List<StageTemplate> initializeTemplates() {
        List<StageTemplate> templates = new ArrayList<>();

        // Filter templates
        templates.add(new StageTemplate(
            "filter-transaction", "Transaction Filter", "Filter transactions by amount and status",
            "filter",
            Map.of("minAmount", 0.01, "maxAmount", 1000000.0, "validStatus", List.of("pending", "completed")),
            List.of("transaction.created", "transaction.updated")
        ));
        templates.add(new StageTemplate(
            "filter-sensor", "Sensor Noise Filter", "Apply Kalman filter to sensor data",
            "filter",
            Map.of("method", "kalman", "threshold", 0.1),
            List.of("sensor.reading", "iot.data")
        ));

        // Validation templates
        templates.add(new StageTemplate(
            "validate-auth", "Auth Validation", "Validate JWT tokens",
            "validate",
            Map.of("tokenType", "jwt", "validateSignature", true, "checkExpiry", true),
            List.of("user.login", "auth.token")
        ));
        templates.add(new StageTemplate(
            "validate-order", "Order Validation", "Validate order constraints",
            "validate",
            Map.of("checkInventory", true, "validatePricing", true, "checkShipping", true),
            List.of("order.created", "order.updated")
        ));

        // Enrichment templates
        templates.add(new StageTemplate(
            "enrich-customer", "Customer Enrichment", "Enrich with customer profile",
            "enrich",
            Map.of("enrichmentType", "customer_profile", "includeHistory", false),
            List.of("transaction.created", "order.created")
        ));
        templates.add(new StageTemplate(
            "enrich-product", "Product Enrichment", "Enrich with product catalog",
            "enrich",
            Map.of("enrichmentType", "product_catalog", "includePricing", true),
            List.of("order.created", "cart.updated")
        ));

        // Detection templates
        templates.add(new StageTemplate(
            "detect-fraud", "Fraud Detection", "Detect fraudulent patterns",
            "detect",
            Map.of("model", "isolation_forest", "threshold", 0.8, "alertOnDetection", true),
            List.of("transaction.created", "payment.processed")
        ));
        templates.add(new StageTemplate(
            "detect-anomaly", "Anomaly Detection", "Detect statistical anomalies",
            "detect",
            Map.of("method", "zscore", "threshold", 3.0, "windowSize", "1h"),
            List.of("sensor.reading", "metric.collected")
        ));

        // Aggregation templates
        templates.add(new StageTemplate(
            "aggregate-time-window", "Time Window Aggregation", "Aggregate in time windows",
            "aggregate",
            Map.of("windowSize", "5m", "aggregation", "avg"),
            List.of("sensor.reading", "metric.collected")
        ));

        // Utility templates
        templates.add(new StageTemplate(
            "log-audit", "Audit Log", "Log events for audit",
            "log",
            Map.of("level", "info", "includePayload", true, "includeHeaders", false),
            List.of("*")
        ));
        templates.add(new StageTemplate(
            "metrics-telemetry", "Telemetry Metrics", "Collect telemetry",
            "metrics",
            Map.of("includeTiming", true, "includeCounts", true, "includeErrors", true),
            List.of("*")
        ));

        return templates;
    }

    private Set<String> categorizeEventTypes(List<String> eventTypes) {
        Set<String> categories = new java.util.HashSet<>();
        
        for (String eventType : eventTypes) {
            String lower = eventType.toLowerCase();
            
            if (lower.contains("transaction") || lower.contains("payment") || lower.contains("transfer")) {
                categories.add("transaction");
            }
            if (lower.contains("login") || lower.contains("auth") || lower.contains("logout")) {
                categories.add("login");
                categories.add("auth");
            }
            if (lower.contains("order") || lower.contains("cart") || lower.contains("checkout")) {
                categories.add("order");
            }
            if (lower.contains("sensor") || lower.contains("iot") || lower.contains("telemetry")) {
                categories.add("sensor");
                categories.add("iot");
            }
            if (lower.contains("user") || lower.contains("customer") || lower.contains("account")) {
                categories.add("user");
            }
        }
        
        return categories;
    }
}
