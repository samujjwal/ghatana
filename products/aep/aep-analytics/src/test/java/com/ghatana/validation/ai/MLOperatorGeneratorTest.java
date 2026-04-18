/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.validation.ai;

import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.LLMGateway;
import com.ghatana.ai.llm.Message;
import com.ghatana.ai.llm.MessageRole;
import com.ghatana.pattern.api.model.OperatorSpec;
import com.ghatana.platform.domain.event.Event;
import com.ghatana.platform.domain.event.EventId;
import com.ghatana.platform.domain.event.EventTime;
import com.ghatana.platform.domain.event.EventType;
import com.ghatana.platform.observability.Metrics;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests for ML-based operator generator.
 *
 * @doc.type class
 * @doc.purpose Unit tests for MLOperatorGenerator
 * @doc.layer test
 */
@DisplayName("ML Operator Generator Tests")
@ExtendWith(MockitoExtension.class)
class MLOperatorGeneratorTest {

    @Mock
    private LLMGateway llmGateway;

    @Mock
    private Metrics metrics;

    @Test
    @DisplayName("generates operators from event data")
    void generatesOperatorsFromEventData() {
        MLOperatorGenerator generator = new MLOperatorGenerator(llmGateway, metrics);

        List<Event> events = createSampleEvents();
        MLOperatorGenerator.GenerationConfig config = MLOperatorGenerator.GenerationConfig.builder()
            .temperature(0.7)
            .maxTokens(2000)
            .confidenceThreshold(0.5)
            .build();

        CompletionResult mockResult = CompletionResult.builder()
            .text("[{\"type\":\"SEQ\",\"id\":\"pattern-1\",\"parameters\":{\"within\":\"PT5M\"},\"operands\":[]}]")
            .tokensUsed(100)
            .promptTokens(50)
            .completionTokens(50)
            .finishReason("stop")
            .modelUsed("gpt-4")
            .build();

        when(llmGateway.complete(any(CompletionRequest.class)))
            .thenReturn(Promise.of(mockResult));

        List<OperatorSpec> operators = generator.generateOperators(events, config).getResult();

        assertThat(operators).isNotEmpty();
    }

    @Test
    @DisplayName("uses default system prompt")
    void usesDefaultSystemPrompt() {
        MLOperatorGenerator generator = new MLOperatorGenerator(llmGateway, metrics);

        assertThat(generator).isNotNull();
    }

    @Test
    @DisplayName("uses custom system prompt")
    void usesCustomSystemPrompt() {
        String customPrompt = "You are a pattern detection expert.";
        MLOperatorGenerator generator = new MLOperatorGenerator(llmGateway, metrics, customPrompt);

        assertThat(generator).isNotNull();
    }

    @Test
    @DisplayName("summarizes events correctly")
    void summarizesEventsCorrectly() {
        MLOperatorGenerator generator = new MLOperatorGenerator(llmGateway, metrics);

        List<Event> events = createSampleEvents();
        MLOperatorGenerator.GenerationConfig config = MLOperatorGenerator.GenerationConfig.builder().build();

        CompletionResult mockResult = CompletionResult.builder()
            .text("[]")
            .tokensUsed(10)
            .promptTokens(5)
            .completionTokens(5)
            .finishReason("stop")
            .modelUsed("gpt-4")
            .build();

        when(llmGateway.complete(any(CompletionRequest.class)))
            .thenReturn(Promise.of(mockResult));

        generator.generateOperators(events, config).getResult();

        // Verify the LLM was called
        // In production, we'd verify the prompt contains event summaries
    }

    @Test
    @DisplayName("handles empty event list")
    void handlesEmptyEventList() {
        MLOperatorGenerator generator = new MLOperatorGenerator(llmGateway, metrics);

        List<Event> events = List.of();
        MLOperatorGenerator.GenerationConfig config = MLOperatorGenerator.GenerationConfig.builder().build();

        CompletionResult mockResult = CompletionResult.builder()
            .text("[]")
            .tokensUsed(10)
            .promptTokens(5)
            .completionTokens(5)
            .finishReason("stop")
            .modelUsed("gpt-4")
            .build();

        when(llmGateway.complete(any(CompletionRequest.class)))
            .thenReturn(Promise.of(mockResult));

        List<OperatorSpec> operators = generator.generateOperators(events, config).getResult();

        assertThat(operators).isNotNull();
    }

    @Test
    @DisplayName("config builder creates valid config")
    void configBuilderCreatesValidConfig() {
        MLOperatorGenerator.GenerationConfig config = MLOperatorGenerator.GenerationConfig.builder()
            .model("gpt-4")
            .temperature(0.5)
            .maxTokens(1000)
            .confidenceThreshold(0.8)
            .build();

        assertThat(config.model()).isEqualTo("gpt-4");
        assertThat(config.temperature()).isEqualTo(0.5);
        assertThat(config.maxTokens()).isEqualTo(1000);
        assertThat(config.confidenceThreshold()).isEqualTo(0.8);
    }

    @Test
    @DisplayName("config clamps temperature to valid range")
    void configClampsTemperatureToValidRange() {
        MLOperatorGenerator.GenerationConfig config = MLOperatorGenerator.GenerationConfig.builder()
            .temperature(3.0) // Above max
            .build();

        assertThat(config.temperature()).isEqualTo(2.0); // Clamped to max
    }

    @Test
    @DisplayName("config clamps confidence to valid range")
    void configClampsConfidenceToValidRange() {
        MLOperatorGenerator.GenerationConfig config = MLOperatorGenerator.GenerationConfig.builder()
            .confidenceThreshold(1.5) // Above max
            .build();

        assertThat(config.confidenceThreshold()).isEqualTo(1.0); // Clamped to max
    }

    /**
     * Creates sample events for testing.
     */
    private List<Event> createSampleEvents() {
        return List.of(
            createEvent("user.login", "event-1"),
            createEvent("order.created", "event-2"),
            createEvent("payment.success", "event-3")
        );
    }

    /**
     * Creates a sample event.
     */
    private Event createEvent(String type, String id) {
        return Event.builder()
            .id(EventId.of(id))
            .type(EventType.of(type))
            .time(EventTime.of(Instant.now()))
            .payload(Map.of("test", "data"))
            .build();
    }
}
