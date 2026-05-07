/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.core.domain.nlp;

import com.ghatana.core.domain.pipeline.PipelineSpecBuilder;
import com.ghatana.core.domain.pipeline.TemplateMarketplace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Natural Language Pipeline interface for generating complete pipeline specifications.
 * Converts natural language descriptions to pipeline specs using template marketplace.
 *
 * @doc.type class
 * @doc.purpose NLP interface for generating complete pipelines from natural language
 * @doc.layer core
 * @doc.pattern Service, Interpreter
 */
public final class NlpPipelineInterface {

    private static final Logger log = LoggerFactory.getLogger(NlpPipelineInterface.class);

    private final TemplateMarketplace marketplace;
    private final Map<String, PipelineTemplate> templateRegistry;

    /**
     * Creates an NLP pipeline interface with default template marketplace.
     */
    public NlpPipelineInterface() {
        this(new TemplateMarketplace());
    }

    /**
     * Creates an NLP pipeline interface with custom template marketplace.
     *
     * @param marketplace template marketplace
     */
    public NlpPipelineInterface(TemplateMarketplace marketplace) {
        this.marketplace = marketplace;
        this.templateRegistry = initializeTemplateRegistry();
    }

    /**
     * Generates a pipeline specification from natural language description.
     *
     * @param description natural language description (e.g., "Process orders with fraud check")
     * @param pipelineName name for the pipeline
     * @param tenantId tenant identifier
     * @return pipeline generation result
     */
    public PipelineGenerationResult generatePipeline(String description, String pipelineName, String tenantId) {
        log.info("[nlp-pipeline] Generating pipeline from description: {}", description);

        // Parse the description to extract intent and parameters
        PipelineIntent intent = parseDescription(description);

        // Find matching template
        Optional<PipelineTemplate> templateOpt = findMatchingTemplate(intent);
        if (templateOpt.isEmpty()) {
            return new PipelineGenerationResult(
                null,
                false,
                "No matching template found for description: " + description,
                List.of()
            );
        }

        PipelineTemplate template = templateOpt.get();
        PipelineSpecBuilder builder = template.specBuilder();

        // Customize the builder with extracted parameters
        builder = customizeBuilder(builder, intent, pipelineName, tenantId);

        log.info("[nlp-pipeline] Generated pipeline using template: {}", template.name());

        return new PipelineGenerationResult(
            builder,
            true,
            "Pipeline generated successfully using template: " + template.name(),
            List.of(template.name())
        );
    }

    /**
     * Parses natural language description to extract intent and parameters.
     */
    private PipelineIntent parseDescription(String description) {
        String lowerDesc = description.toLowerCase();

        // Extract domain keywords
        String domain = extractDomain(lowerDesc);
        String operation = extractOperation(lowerDesc);
        List<String> features = extractFeatures(lowerDesc);
        Map<String, String> parameters = extractParameters(description);

        return new PipelineIntent(domain, operation, features, parameters, description);
    }

    /**
     * Extracts domain from description.
     */
    private String extractDomain(String description) {
        if (description.contains("order") || description.contains("payment") || description.contains("transaction")) {
            return "ecommerce";
        } else if (description.contains("click") || description.contains("session") || description.contains("user")) {
            return "analytics";
        } else if (description.contains("iot") || description.contains("device") || description.contains("sensor")) {
            return "iot";
        } else if (description.contains("audit") || description.contains("compliance") || description.contains("log")) {
            return "compliance";
        } else if (description.contains("tenant") || description.contains("multi-tenant")) {
            return "multi-tenant";
        }
        return "general";
    }

    /**
     * Extracts operation from description.
     */
    private String extractOperation(String description) {
        if (description.contains("fraud") || description.contains("detect") || description.contains("anomaly")) {
            return "detection";
        } else if (description.contains("enrich") || description.contains("transform") || description.contains("add")) {
            return "enrichment";
        } else if (description.contains("aggregate") || description.contains("sum") || description.contains("count")) {
            return "aggregation";
        } else if (description.contains("filter") || description.contains("screen")) {
            return "filtering";
        } else if (description.contains("route") || description.contains("direct")) {
            return "routing";
        }
        return "processing";
    }

    /**
     * Extracts features from description.
     */
    private List<String> extractFeatures(String description) {
        List<String> features = new ArrayList<>();

        if (description.contains("fraud")) features.add("fraud-detection");
        if (description.contains("ml") || description.contains("machine learning")) features.add("ml-scoring");
        if (description.contains("real-time") || description.contains("streaming")) features.add("real-time");
        if (description.contains("window") || description.contains("time")) features.add("windowing");
        if (description.contains("alert") || description.contains("notification")) features.add("alerting");
        if (description.contains("pii") || description.contains("mask")) features.add("pii-masking");
        if (description.contains("session")) features.add("session-enrichment");

        return features;
    }

    /**
     * Extracts named parameters from description.
     */
    private Map<String, String> extractParameters(String description) {
        Map<String, String> parameters = new HashMap<>();

        // Extract pipeline name if specified
        Pattern namePattern = Pattern.compile("(?i)name[d]?\\s+[\"']?([\\w\\-]+)[\"']?");
        Matcher nameMatcher = namePattern.matcher(description);
        if (nameMatcher.find()) {
            parameters.put("pipelineName", nameMatcher.group(1));
        }

        // Extract time window
        Pattern timePattern = Pattern.compile("(?i)(\\d+)\\s+(minute|hour|day|week)s?");
        Matcher timeMatcher = timePattern.matcher(description);
        if (timeMatcher.find()) {
            parameters.put("timeWindow", timeMatcher.group(1) + " " + timeMatcher.group(2));
        }

        return parameters;
    }

    /**
     * Finds matching template based on intent.
     */
    private Optional<PipelineTemplate> findMatchingTemplate(PipelineIntent intent) {
        // Search marketplace for templates matching intent keywords
        String searchQuery = intent.domain() + " " + intent.operation();
        for (String feature : intent.features()) {
            searchQuery += " " + feature;
        }

        List<TemplateMarketplace.TemplateListing> listings = marketplace.search(searchQuery);
        if (!listings.isEmpty()) {
            return listings.stream()
                .findFirst()
                .map(l -> new PipelineTemplate(
                    l.name(),
                    l.description(),
                    l.specBuilder()
                ));
        }

        // Fallback to direct template registry lookup
        String templateKey = intent.domain() + "-" + intent.operation();
        return Optional.ofNullable(templateRegistry.get(templateKey));
    }

    /**
     * Customizes the pipeline spec builder with extracted parameters.
     */
    private PipelineSpecBuilder customizeBuilder(PipelineSpecBuilder builder, PipelineIntent intent,
                                                   String pipelineName, String tenantId) {
        // Override pipeline name if not already set
        if (!intent.parameters().containsKey("pipelineName")) {
            builder = builder.forTenant(tenantId);
        }

        // Add description based on original natural language
        builder = builder.describedAs("Generated from: " + intent.originalDescription());

        return builder;
    }

    /**
     * Initializes the template registry with common mappings.
     */
    private Map<String, PipelineTemplate> initializeTemplateRegistry() {
        Map<String, PipelineTemplate> registry = new HashMap<>();

        // Ecommerce fraud detection
        registry.put("ecommerce-detection", new PipelineTemplate(
            "fraud-detection",
            "Real-time fraud detection for ecommerce",
            marketplace.getSpecBuilder("fraud-detection").orElse(null)
        ));

        // Analytics aggregation
        registry.put("analytics-aggregation", new PipelineTemplate(
            "windowed-aggregation",
            "Windowed aggregation for analytics",
            marketplace.getSpecBuilder("windowed-aggregation").orElse(null)
        ));

        // Compliance audit
        registry.put("compliance-processing", new PipelineTemplate(
            "audit-log-pipeline",
            "Compliance audit log processing",
            marketplace.getSpecBuilder("audit-log-pipeline").orElse(null)
        ));

        // IoT telemetry
        registry.put("iot-processing", new PipelineTemplate(
            "iot-telemetry",
            "IoT telemetry processing",
            marketplace.getSpecBuilder("iot-telemetry").orElse(null)
        ));

        return registry;
    }

    /**
     * Pipeline intent extracted from natural language.
     *
     * @param domain domain (ecommerce, analytics, iot, etc.)
     * @param operation operation (detection, enrichment, aggregation, etc.)
     * @param features list of extracted features
     * @param parameters named parameters
     * @param originalDescription original natural language description
     */
    public record PipelineIntent(
        String domain,
        String operation,
        List<String> features,
        Map<String, String> parameters,
        String originalDescription
    ) {
        public PipelineIntent {
            features = List.copyOf(features);
            parameters = Map.copyOf(parameters);
        }
    }

    /**
     * Pipeline template reference.
     *
     * @param name template name
     * @param description template description
     * @param specBuilder pipeline spec builder
     */
    public record PipelineTemplate(
        String name,
        String description,
        PipelineSpecBuilder specBuilder
    ) {}

    /**
     * Pipeline generation result.
     *
     * @param specBuilder generated pipeline spec builder
     * @param success whether generation succeeded
     * @param message result message
     * @param templatesUsed list of template names used
     */
    public record PipelineGenerationResult(
        PipelineSpecBuilder specBuilder,
        boolean success,
        String message,
        List<String> templatesUsed
    ) {
        public PipelineGenerationResult {
            templatesUsed = List.copyOf(templatesUsed);
        }
    }
}
