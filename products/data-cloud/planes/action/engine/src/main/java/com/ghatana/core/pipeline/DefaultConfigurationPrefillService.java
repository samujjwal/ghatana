/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.core.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Default implementation of configuration prefill service.
 * <p>
 * Uses rule-based heuristics to suggest configuration values.
 * Can be enhanced with ML-based recommendation from historical pipelines.
 *
 * @doc.type class
 * @doc.purpose Rule-based configuration prefill service
 * @doc.layer product
 * @doc.pattern Strategy
 */
public class DefaultConfigurationPrefillService implements ConfigurationPrefillService {

    private static final Logger logger = LoggerFactory.getLogger(DefaultConfigurationPrefillService.class);

    // Pre-configured defaults based on common patterns
    private static final Map<String, Map<String, Object>> DEFAULT_CONFIGS = Map.of(
        "filter", Map.of(
            "removeNulls", true,
            "removeEmpty", true,
            "validateSchema", true,
            "minRecords", 1,
            "maxRecords", 1000000,
            "batchSize", 1000
        ),
        "validate", Map.of(
            "checkNulls", true,
            "checkSchema", true,
            "checkTypes", true,
            "strictMode", false,
            "failFast", false,
            "logViolations", true
        ),
        "enrich", Map.of(
            "enrichmentType", "customer_profile",
            "includeHistory", false,
            "cacheResults", true,
            "cacheTTL", 3600,
            "fallbackOnMissing", true
        ),
        "detect", Map.of(
            "model", "isolation_forest",
            "threshold", 0.8,
            "alertOnDetection", true,
            "minConfidence", 0.7,
            "maxDetectionsPerEvent", 10
        ),
        "aggregate", Map.of(
            "windowSize", "5m",
            "aggregation", "avg",
            "emitOnWindowClose", true,
            "lateDataHandling", "drop",
            "watermarkTolerance", "1m"
        ),
        "log", Map.of(
            "level", "info",
            "includePayload", true,
            "includeHeaders", false,
            "maxPayloadSize", 1024,
            "samplingRate", 1.0
        ),
        "metrics", Map.of(
            "includeTiming", true,
            "includeCounts", true,
            "includeErrors", true,
            "reportInterval", "60s",
            "percentiles", List.of(50, 95, 99)
        ),
        "transform", Map.of(
            "preserveOriginal", true,
            "validateOutput", true,
            "batchSize", 100
        ),
        "route", Map.of(
            "defaultRoute", "default",
            "fallbackRoute", "error",
            "routeByEventType", true
        )
    );

    @Override
    public Map<String, Object> suggestConfiguration(String stageType, String eventType, Map<String, Object> context) {
        logger.info("Suggesting configuration for stageType={}, eventType={}", stageType, eventType);

        Map<String, Object> config = new HashMap<>(DEFAULT_CONFIGS.getOrDefault(stageType, Map.of()));

        // Customize based on event type
        if (eventType != null) {
            customizeForEventType(config, stageType, eventType);
        }

        // Customize based on context
        if (context != null) {
            customizeForContext(config, stageType, context);
        }

        return config;
    }

    @Override
    public double getConfidence(String stageType, String eventType, String configKey) {
        String normalizedEventType = eventType != null ? eventType.toLowerCase() : "";
        String normalizedConfigKey = configKey != null ? configKey.toLowerCase() : "";

        if (normalizedEventType.contains("transaction") && normalizedConfigKey.contains("amount")) {
            return 0.9; // High confidence for transaction amount configs
        }

        if (normalizedEventType.contains("sensor") && normalizedConfigKey.contains("threshold")) {
            return 0.8; // High confidence for sensor threshold configs
        }

        if (DEFAULT_CONFIGS.containsKey(stageType) && DEFAULT_CONFIGS.get(stageType).containsKey(configKey)) {
            return 0.7; // Base confidence for default configs
        }

        return 0.5; // Low confidence for other cases
    }

    private void customizeForEventType(Map<String, Object> config, String stageType, String eventType) {
        if (eventType.contains("transaction")) {
            if (stageType.equals("filter")) {
                config.put("minAmount", 0.01);
                config.put("maxAmount", 1000000.0);
                config.put("validStatus", List.of("pending", "completed", "settled"));
            }
            if (stageType.equals("detect")) {
                config.put("model", "isolation_forest");
                config.put("threshold", 0.8);
                config.put("featureFields", List.of("amount", "location", "time"));
            }
            if (stageType.equals("aggregate")) {
                config.put("windowSize", "1h");
                config.put("aggregation", "sum");
                config.put("groupBy", List.of("customerId", "merchantId"));
            }
            if (stageType.equals("log")) {
                config.put("includePayload", false); // PII protection
                config.put("level", "warn");
            }
        }

        if (eventType.contains("login") || eventType.contains("auth")) {
            if (stageType.equals("validate")) {
                config.put("tokenType", "jwt");
                config.put("validateSignature", true);
                config.put("checkExpiry", true);
                config.put("checkIssuer", true);
            }
            if (stageType.equals("detect")) {
                config.put("model", "anomaly_detection");
                config.put("window", "1h");
                config.put("maxAttempts", 5);
                config.put("lockoutDuration", "30m");
            }
            if (stageType.equals("filter")) {
                config.put("blockKnownIPs", true);
                config.put("requireMFA", false);
            }
        }

        if (eventType.contains("sensor") || eventType.contains("iot")) {
            if (stageType.equals("filter")) {
                config.put("method", "kalman");
                config.put("threshold", 0.1);
                config.put("outlierMethod", "zscore");
            }
            if (stageType.equals("aggregate")) {
                config.put("windowSize", "5m");
                config.put("aggregation", "avg");
                config.put("downsample", true);
                config.put("downsampleFactor", 10);
            }
            if (stageType.equals("detect")) {
                config.put("method", "zscore");
                config.put("threshold", 3.0);
                config.put("windowSize", "1h");
            }
        }

        if (eventType.contains("order") || eventType.contains("cart")) {
            if (stageType.equals("validate")) {
                config.put("checkInventory", true);
                config.put("validatePricing", true);
                config.put("checkShipping", true);
            }
            if (stageType.equals("enrich")) {
                config.put("enrichmentType", "product_catalog");
                config.put("includePricing", true);
                config.put("includeInventory", true);
            }
        }
    }

    private void customizeForContext(Map<String, Object> config, String stageType, Map<String, Object> context) {
        String industry = (String) context.get("industry");
        if (industry != null) {
            if (industry.equals("finance") && stageType.equals("detect")) {
                config.put("model", "isolation_forest");
                config.put("threshold", 0.9);
            }
            if (industry.equals("healthcare") && stageType.equals("log")) {
                config.put("includePayload", false); // HIPAA compliance
                config.put("level", "warn");
            }
        }

        String sensitivity = (String) context.get("sensitivity");
        if ("high".equals(sensitivity) && stageType.equals("detect")) {
            config.put("threshold", 0.95);
        }
    }

    /**
     * Calculate the percentage of auto-configuration for a pipeline.
     *
     * @param stages list of stage specifications
     * @return percentage (0.0 to 1.0)
     */
    public double calculateAutoConfigurationPercentage(List<NaturalLanguagePipelineService.StageSpec> stages) {
        if (stages == null || stages.isEmpty()) {
            return 0.0;
        }

        int totalParams = 0;
        int autoConfiguredParams = 0;

        for (NaturalLanguagePipelineService.StageSpec stage : stages) {
            Map<String, Object> suggested = suggestConfiguration(stage.type(), "generic.event", Map.of());
            totalParams += suggested.size();
            
            // Count how many suggested params are actually present in the stage config
            for (String key : suggested.keySet()) {
                if (stage.config().containsKey(key)) {
                    autoConfiguredParams++;
                }
            }
        }

        return totalParams > 0 ? (double) autoConfiguredParams / totalParams : 0.0;
    }
}
