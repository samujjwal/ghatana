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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Default implementation of natural language pipeline service.
 * <p>
 * Uses rule-based parsing to extract pipeline intent from natural language.
 * This can be enhanced with ML models in the future.
 *
 * @doc.type class
 * @doc.purpose Rule-based natural language pipeline generator
 * @doc.layer product
 * @doc.pattern Strategy
 */
public class DefaultNaturalLanguagePipelineService implements NaturalLanguagePipelineService {

    private static final Logger logger = LoggerFactory.getLogger(DefaultNaturalLanguagePipelineService.class);

    private static final Pattern FRAUD_PATTERN = Pattern.compile("(?i)(fraud|suspicious|anomaly|detect)");
    private static final Pattern TRANSACTION_PATTERN = Pattern.compile("(?i)(transaction|payment|transfer|order)");
    private static final Pattern LOGIN_PATTERN = Pattern.compile("(?i)(login|auth|authentication|security)");
    private static final Pattern SENSOR_PATTERN = Pattern.compile("(?i)(sensor|iot|telemetry|monitoring)");
    private static final Pattern AGGREGATION_PATTERN = Pattern.compile("(?i)(aggregate|sum|count|average|window)");
    private static final Pattern FILTER_PATTERN = Pattern.compile("(?i)(filter|exclude|remove|drop)");
    private static final Pattern ENRICH_PATTERN = Pattern.compile("(?i)(enrich|add|augment|join)");

    @Override
    public PipelineSpec generatePipeline(String description, Map<String, Object> context) {
        logger.info("Generating pipeline from description: {}", description);

        String eventType = extractEventType(description, context);
        List<StageSpec> stages = generateStages(description, eventType);
        String name = generatePipelineName(description);
        String pipelineDescription = generatePipelineDescription(description);

        return new PipelineSpec(name, pipelineDescription, eventType, stages);
    }

    @Override
    public ValidationResult validateDescription(String description) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (description == null || description.trim().isEmpty()) {
            errors.add("Description cannot be empty");
        }

        if (description != null && description.length() < 10) {
            warnings.add("Description is too short; may not capture intent clearly");
        }

        if (description != null && description.length() > 500) {
            warnings.add("Description is very long; consider being more concise");
        }

        boolean valid = errors.isEmpty();
        return new ValidationResult(valid, errors, warnings);
    }

    private String extractEventType(String description, Map<String, Object> context) {
        // Check context first
        if (context != null && context.containsKey("eventType")) {
            return context.get("eventType").toString();
        }

        // Infer from description
        Matcher transactionMatcher = TRANSACTION_PATTERN.matcher(description);
        if (transactionMatcher.find()) {
            return "transaction.created";
        }

        Matcher loginMatcher = LOGIN_PATTERN.matcher(description);
        if (loginMatcher.find()) {
            return "user.login";
        }

        Matcher sensorMatcher = SENSOR_PATTERN.matcher(description);
        if (sensorMatcher.find()) {
            return "sensor.reading";
        }

        return "generic.event";
    }

    private List<StageSpec> generateStages(String description, String eventType) {
        List<StageSpec> stages = new ArrayList<>();
        int stageIndex = 0;

        // Add validation stage
        stages.add(new StageSpec(
            "step-" + stageIndex++,
            "validate",
            "Data Validation",
            Map.of("checkNulls", true, "checkSchema", true),
            List.of()
        ));

        // Add filter stage if mentioned
        if (FILTER_PATTERN.matcher(description).find()) {
            stages.add(new StageSpec(
                "step-" + stageIndex++,
                "filter",
                "Filter Invalid Data",
                Map.of("removeNulls", true),
                List.of("step-" + (stageIndex - 2))
            ));
        }

        // Add enrichment stage if mentioned or for transaction events
        if (ENRICH_PATTERN.matcher(description).find() || eventType.contains("transaction")) {
            stages.add(new StageSpec(
                "step-" + stageIndex++,
                "enrich",
                "Data Enrichment",
                Map.of("enrichmentType", "customer_profile"),
                List.of("step-" + (stageIndex - 2))
            ));
        }

        // Add aggregation stage if mentioned
        if (AGGREGATION_PATTERN.matcher(description).find()) {
            stages.add(new StageSpec(
                "step-" + stageIndex++,
                "aggregate",
                "Time Window Aggregation",
                Map.of("windowSize", "5m", "aggregation", "avg"),
                List.of("step-" + (stageIndex - 2))
            ));
        }

        // Add detection stage for fraud/anomaly
        if (FRAUD_PATTERN.matcher(description).find()) {
            stages.add(new StageSpec(
                "step-" + stageIndex++,
                "detect",
                "Pattern Detection",
                Map.of("model", "isolation_forest", "threshold", 0.8),
                stages.isEmpty() ? List.of() : List.of("step-" + (stageIndex - 2))
            ));
        }

        // Add logging stage
        stages.add(new StageSpec(
            "step-" + stageIndex++,
            "log",
            "Audit Log",
            Map.of("level", "info", "includePayload", true),
            stages.isEmpty() ? List.of() : List.of("step-" + (stageIndex - 2))
        ));

        return stages;
    }

    private String generatePipelineName(String description) {
        String normalized = description.toLowerCase().replaceAll("[^a-z0-9\\s]", "").trim();
        String[] words = normalized.split("\\s+");
        
        if (words.length == 0) {
            return "generated-pipeline";
        }

        StringBuilder name = new StringBuilder();
        for (String word : words) {
            if (word.length() > 0) {
                name.append(Character.toUpperCase(word.charAt(0)))
                     .append(word.substring(1));
            }
        }

        return name.append(" Pipeline").toString();
    }

    private String generatePipelineDescription(String description) {
        return "Auto-generated pipeline from: " + description;
    }
}
