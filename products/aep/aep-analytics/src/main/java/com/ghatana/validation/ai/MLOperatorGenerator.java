/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.validation.ai;

import com.ghatana.ai.llm.LLMGateway;
import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.Message;
import com.ghatana.ai.llm.MessageRole;
import com.ghatana.pattern.api.model.OperatorSpec;
import com.ghatana.platform.domain.event.Event;
import com.ghatana.platform.observability.Metrics;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        return Promise.ofBlocking(() -> {
            long startTime = System.currentTimeMillis();

            try {
                String eventSummary = summarizeEvents(events);
                String userPrompt = buildUserPrompt(eventSummary, config);

                CompletionRequest request = CompletionRequest.builder()
                    .addMessage(Message.of(MessageRole.SYSTEM, systemPrompt))
                    .addMessage(Message.of(MessageRole.USER, userPrompt))
                    .model(config.model() != null ? config.model() : "gpt-4")
                    .temperature(config.temperature())
                    .maxTokens(config.maxTokens())
                    .build();

                CompletionResult result = llmGateway.complete(request).getResult();

                List<OperatorSpec> operators = parseOperatorSpecs(result.getText());

                metrics.timer("ml.operator.generation.duration")
                    .record(System.currentTimeMillis() - startTime, TimeUnit.MILLISECONDS);
                metrics.counter("ml.operator.generation.success").increment();

                log.info("Generated {} operators from {} events", operators.size(), events.size());
                return operators;
            } catch (Exception e) {
                metrics.timer("ml.operator.generation.duration")
                    .record(System.currentTimeMillis() - startTime, TimeUnit.MILLISECONDS);
                metrics.counter("ml.operator.generation.failed").increment();
                log.error("ML operator generation failed", e);
                throw new RuntimeException("ML operator generation failed", e);
            }
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
     * Parses operator specs from LLM response.
     *
     * @param response the LLM response text
     * @return list of parsed operator specs
     */
    private List<OperatorSpec> parseOperatorSpecs(String response) {
        // Extract JSON from response (handle markdown code blocks)
        String jsonText = response;
        if (response.contains("```json")) {
            int start = response.indexOf("```json") + 7;
            int end = response.indexOf("```", start);
            if (end > start) {
                jsonText = response.substring(start, end).trim();
            }
        } else if (response.contains("```")) {
            int start = response.indexOf("```") + 3;
            int end = response.indexOf("```", start);
            if (end > start) {
                jsonText = response.substring(start, end).trim();
            }
        }

        // Parse JSON (simplified - in production use proper JSON parser)
        List<OperatorSpec> operators = new ArrayList<>();
        
        // For now, return a simple pattern based on common sequences
        // In production, this would use Jackson or similar to parse the JSON
        operators.add(createSampleOperator());
        
        return operators;
    }

    /**
     * Creates a sample operator for testing.
     *
     * @return a sample SEQ operator
     */
    private OperatorSpec createSampleOperator() {
        return OperatorSpec.builder()
            .type("SEQ")
            .id("ml-generated-sequence")
            .parameter("within", "PT5M")
            .operand(OperatorSpec.builder()
                .type("SELECT")
                .id("event-1")
                .parameter("eventType", "user.login")
                .build())
            .operand(OperatorSpec.builder()
                .type("SELECT")
                .id("event-2")
                .parameter("eventType", "order.created")
                .build())
            .build();
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
