/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.validation.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.ai.llm.ChatMessage;
import com.ghatana.ai.llm.LLMGateway;
import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.pattern.api.model.OperatorSpec;
import com.ghatana.platform.domain.event.Event;
import com.ghatana.platform.observability.Metrics;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * ML-based operator generator that uses LLM to generate pattern operators from event data.
 *
 * This service analyzes event streams and uses LLM to generate OperatorSpec trees that
 * represent patterns in the data. The generated operators can be compiled into ASTs
 * and executed by the pattern detection engine.
 *
 * @doc.type class
 * @doc.purpose Generate pattern operators using ML
 * @doc.layer core
 * @doc.pattern Service, Generator
 */
public class MLOperatorGenerator {

    private static final Logger log = LoggerFactory.getLogger(MLOperatorGenerator.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Set<String> ALLOWED_OPERATOR_TYPES = Set.of(
        "SEQ", "AND", "OR", "NOT", "WITHIN", "REPEAT", "WINDOW", "UNTIL", "SELECT", "FILTER",
        "MAP", "AGGREGATE", "JOIN", "CORRELATE"
    );

    private final LLMGateway llmGateway;
    private final Metrics metrics;
    private final String systemPrompt;
    /**
     * Creates an ML operator generator with default system prompt.
     *
     * @param llmGateway the LLM gateway for inference
     * @param metrics metrics collector
     */
    public MLOperatorGenerator(LLMGateway llmGateway, Metrics metrics) {
        this(llmGateway, metrics, buildDefaultSystemPrompt());
    }

    /**
     * Creates an ML operator generator with custom system prompt.
     *
     * @param llmGateway the LLM gateway for inference
     * @param metrics metrics collector
     * @param systemPrompt custom system prompt for the LLM
     */
    public MLOperatorGenerator(LLMGateway llmGateway, Metrics metrics, String systemPrompt) {
        this.llmGateway = llmGateway;
        this.metrics = metrics;
        this.systemPrompt = systemPrompt;
    }

    /**
     * Generates operator specifications from event data using ML.
     *
     * @param events the events to analyze
     * @param config configuration for generation
     * @return promise resolving to list of generated operator specs
     */
    public Promise<List<OperatorSpec>> generateOperators(List<Event> events, GenerationConfig config) {
        long startTime = System.currentTimeMillis();
        String eventSummary = summarizeEvents(events);
        String userPrompt = buildUserPrompt(eventSummary, config);

        CompletionRequest request = CompletionRequest.builder()
            .messages(List.of(
                ChatMessage.system(systemPrompt),
                ChatMessage.user(userPrompt)
            ))
            .model(config.model() != null ? config.model() : "gpt-4")
            .temperature(config.temperature())
            .maxTokens(config.maxTokens())
            .build();

        // Group 9 / DC-OBS-009: capture the caller MDC so correlationId and traceId flow into
        // the LLM callback regardless of which thread the Promise is resolved on.
        final Map<String, String> callerMdc = MDC.getCopyOfContextMap() != null
            ? new HashMap<>(MDC.getCopyOfContextMap())
            : Map.of();

        return llmGateway.complete(request)
            .map(result -> {
                if (!callerMdc.isEmpty()) {
                    MDC.setContextMap(callerMdc);
                }
                try {
                    List<OperatorSpec> operators = parseOperatorSpecs(result.getText());

                    metrics.timer("ml.operator.generation.duration")
                        .record(System.currentTimeMillis() - startTime, TimeUnit.MILLISECONDS);
                    metrics.counter("ml.operator.generation.success").increment();

                    log.info("Generated {} operators from {} events", operators.size(), events.size());
                    return operators;
                } finally {
                    if (!callerMdc.isEmpty()) {
                        MDC.clear();
                    }
                }
            })
            .whenException(exception -> {
                metrics.timer("ml.operator.generation.duration")
                    .record(System.currentTimeMillis() - startTime, TimeUnit.MILLISECONDS);
                metrics.counter("ml.operator.generation.failed").increment();
                log.error("ML operator generation failed", exception);
            });
    }

    /**
     * Summarizes events for LLM consumption.
     *
     * @param events the events to summarize
     * @return a text summary of the events
     */
    private String summarizeEvents(List<Event> events) {
        if (events.isEmpty()) {
            return "No events provided.";
        }

        StringBuilder summary = new StringBuilder();
        summary.append("Event Stream Summary:\n");
        summary.append("Total events: ").append(events.size()).append("\n\n");

        // Group by event type
        Map<String, List<Event>> byType = events.stream()
            .collect(Collectors.groupingBy(Event::getType));

        summary.append("Event Types:\n");
        for (Map.Entry<String, List<Event>> entry : byType.entrySet()) {
            summary.append("  - ").append(entry.getKey())
                .append(": ").append(entry.getValue().size()).append(" occurrences\n");
        }

        summary.append("\nSample Events (first 5):\n");
        events.stream().limit(5).forEach(event -> {
            summary.append("  - Type: ").append(event.getType())
                .append(", ID: ").append(event.getId())
                .append(", Time: ").append(event.getTime().getOccurrenceTime().start())
                .append("\n");
        });

        return summary.toString();
    }

    /**
     * Builds the user prompt for LLM inference.
     *
     * @param eventSummary summary of events
     * @param config generation configuration
     * @return the user prompt
     */
    private String buildUserPrompt(String eventSummary, GenerationConfig config) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(eventSummary).append("\n\n");
        prompt.append("Task: Generate pattern detection operators for this event stream.\n\n");
        prompt.append("Requirements:\n");
        prompt.append("- Return valid JSON array of OperatorSpec objects\n");
        prompt.append("- Use standard operator types: SEQ, AND, OR, NOT, WITHIN, REPEAT, WINDOW, UNTIL\n");
        prompt.append("- Each operator must have: type, id, parameters (if needed), operands (if composite)\n");
        prompt.append("- Focus on detecting meaningful patterns: sequences, correlations, anomalies\n");
        
        if (config.confidenceThreshold() > 0) {
            prompt.append("- Only include operators with confidence > ").append(config.confidenceThreshold()).append("\n");
        }

        prompt.append("\nOutput format:\n");
        prompt.append("[\n");
        prompt.append("  {\n");
        prompt.append("    \"type\": \"SEQ\",\n");
        prompt.append("    \"id\": \"pattern-1\",\n");
        prompt.append("    \"parameters\": {\"within\": \"PT5M\"},\n");
        prompt.append("    \"operands\": [\n");
        prompt.append("      {\"type\": \"SELECT\", \"id\": \"event-1\", \"parameters\": {\"eventType\": \"user.login\"}},\n");
        prompt.append("      {\"type\": \"SELECT\", \"id\": \"event-2\", \"parameters\": {\"eventType\": \"order.created\"}}\n");
        prompt.append("    ]\n");
        prompt.append("  }\n");
        prompt.append("]\n");

        return prompt.toString();
    }

    /**
     * Parses operator specs from LLM response using Jackson.
     *
     * <p>Strips optional markdown code fences, then deserialises the JSON array into
     * {@link OperatorSpec} objects. Each parsed spec is validated for a known operator
     * type and a non-blank ID before being added to the result list. Malformed entries
     * are skipped with a warning so that a single bad operator does not discard the
     * entire LLM response.
     *
     * @param response the raw LLM response text
     * @return list of valid parsed operator specs (may be empty, never null)
     */
    private List<OperatorSpec> parseOperatorSpecs(String response) {
        if (response == null || response.isBlank()) {
            log.warn("[MLOperatorGenerator] LLM returned empty response");
            metrics.counter("ml.operator.generation.parse.empty").increment();
            return List.of();
        }

        String cleaned = response.trim();
        if (cleaned.startsWith("```json")) {
            int start = cleaned.indexOf('\n') + 1;
            int end   = cleaned.lastIndexOf("```");
            cleaned   = (end > start) ? cleaned.substring(start, end).trim() : cleaned;
        } else if (cleaned.startsWith("```")) {
            int start = cleaned.indexOf('\n') + 1;
            int end   = cleaned.lastIndexOf("```");
            cleaned   = (end > start) ? cleaned.substring(start, end).trim() : cleaned;
        }

        if (!cleaned.startsWith("[")) {
            int arrayStart = cleaned.indexOf('[');
            if (arrayStart >= 0) {
                cleaned = cleaned.substring(arrayStart);
            } else {
                log.warn("[MLOperatorGenerator] LLM response does not contain a JSON array: {}",
                    cleaned.length() > 200 ? cleaned.substring(0, 200) + "..." : cleaned);
                metrics.counter("ml.operator.generation.parse.invalid").increment();
                return List.of();
            }
        }

        try {
            List<OperatorSpec> raw = MAPPER.readValue(cleaned, new TypeReference<List<OperatorSpec>>() {});
            List<OperatorSpec> valid = new ArrayList<>();
            for (int i = 0; i < raw.size(); i++) {
                OperatorSpec spec = raw.get(i);
                if (spec == null) {
                    log.warn("[MLOperatorGenerator] Null operator at index {}, skipping", i);
                    continue;
                }
                if (spec.getType() == null || spec.getType().isBlank()) {
                    log.warn("[MLOperatorGenerator] Operator at index {} has no type, skipping", i);
                    continue;
                }
                if (!ALLOWED_OPERATOR_TYPES.contains(spec.getType().toUpperCase())) {
                    log.warn("[MLOperatorGenerator] Unknown operator type '{}' at index {}, skipping",
                        spec.getType(), i);
                    continue;
                }
                if (spec.getId() == null || spec.getId().isBlank()) {
                    log.warn("[MLOperatorGenerator] Operator of type '{}' at index {} has no id, skipping",
                        spec.getType(), i);
                    continue;
                }
                valid.add(spec);
            }
            metrics.counter("ml.operator.generation.parse.success").increment();
            log.debug("[MLOperatorGenerator] Parsed {}/{} valid operators from LLM response",
                valid.size(), raw.size());
            return List.copyOf(valid);
        } catch (Exception e) {
            log.warn("[MLOperatorGenerator] Failed to parse LLM JSON response: {}", e.getMessage());
            metrics.counter("ml.operator.generation.parse.invalid").increment();
            return List.of();
        }
    }

    /**
     * Builds the default system prompt for the LLM.
     *
     * @return the default system prompt
     */
    private static String buildDefaultSystemPrompt() {
        return """
            You are an expert in pattern detection and event stream analysis.
            Your task is to generate operator specifications for detecting patterns in event data.
            
            The operator types you can use:
            - SEQ: Sequence matching (A followed by B)
            - AND: Conjunction (A and B simultaneously)
            - OR: Disjunction (A or B)
            - NOT: Negation (absence of A)
            - WITHIN: Time constraint (event within duration)
            - REPEAT: Repetition (A occurs n times)
            - WINDOW: Windowed aggregation
            - UNTIL: A continues until B occurs
            - SELECT: Event selector by type
            
            Return only valid JSON. No explanations outside the JSON.
            """;
    }

    /**
     * Configuration for ML operator generation.
     *
     * @param model the LLM model to use
     * @param temperature the sampling temperature
     * @param maxTokens maximum tokens to generate
     * @param confidenceThreshold minimum confidence for operators
     */
    public record GenerationConfig(
        String model,
        double temperature,
        int maxTokens,
        double confidenceThreshold
    ) {
        public GenerationConfig {
            temperature = Math.max(0.0, Math.min(2.0, temperature));
            confidenceThreshold = Math.max(0.0, Math.min(1.0, confidenceThreshold));
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String model = "gpt-4";
            private double temperature = 0.7;
            private int maxTokens = 2000;
            private double confidenceThreshold = 0.5;

            public Builder model(String model) { this.model = model; return this; }
            public Builder temperature(double temperature) { this.temperature = temperature; return this; }
            public Builder maxTokens(int maxTokens) { this.maxTokens = maxTokens; return this; }
            public Builder confidenceThreshold(double confidenceThreshold) { this.confidenceThreshold = confidenceThreshold; return this; }

            public GenerationConfig build() {
                return new GenerationConfig(model, temperature, maxTokens, confidenceThreshold);
            }
        }
    }
}
